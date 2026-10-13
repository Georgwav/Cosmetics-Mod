"""AVIF textures to the PNGs the mod reads.

A still becomes one PNG. A sequence becomes a vertical strip (frame 0 on top), which the mod's
`texture_animation` steps through by moving V, with one frame time per frame. Textures are scaled down to a
maximum size so a large collection stays a reasonable size on disk and in video memory.
"""

from __future__ import annotations

import io
import math
from dataclasses import dataclass

from . import avif

# The tallest strip written; GPUs handle 16384, this leaves headroom for older ones.
MAX_STRIP_HEIGHT = 8192
# Frame times the mod accepts, in milliseconds.
MIN_FRAME_MS = 1
MAX_FRAME_MS = 600_000
# Used when a sequence carries no timing and the definition gives none.
DEFAULT_FRAME_MS = 50
# Where a texture is painted (not see-through), on a MASK_GRID x MASK_GRID grid: what the importer checks a
# model's UVs against. Frames sampled for it, at most.
MASK_GRID = 64
MASK_FRAMES = 8


@dataclass
class Converted:
    png: bytes
    frame_size: tuple[int, int]  # one frame, after scaling
    frames: int
    frame_times: list[int]  # per frame in ms; empty for a still
    source_size: tuple[int, int]
    opaque: list[bool] | None = None  # where any frame is painted, MASK_GRID x MASK_GRID, row by row from the top


def opaque_mask(frames) -> list[bool]:
    """Where any of the frames (PIL images) is painted, on a MASK_GRID x MASK_GRID grid."""
    from PIL import Image

    mask = [False] * (MASK_GRID * MASK_GRID)
    step = max(1, len(frames) // MASK_FRAMES)
    for frame in frames[::step][:MASK_FRAMES]:
        alpha = frame.convert("RGBA").getchannel("A").resize((MASK_GRID, MASK_GRID), Image.BOX)
        for i, a in enumerate(alpha.tobytes()):
            if a > 16:
                mask[i] = True
    return mask


def mask_from_png(data: bytes, frame_height: int) -> list[bool]:
    """opaque_mask of a strip this tool wrote (frames stacked, each frame_height tall)."""
    from PIL import Image

    image = Image.open(io.BytesIO(data)).convert("RGBA")
    w, h = image.size
    frame_height = max(1, min(frame_height, h))
    frames = [image.crop((0, y, w, y + frame_height)) for y in range(0, h - frame_height + 1, frame_height)]
    return opaque_mask(frames or [image])


def pack_mask(mask: list[bool]) -> str:
    return format(int("".join("1" if m else "0" for m in mask) or "0", 2), f"0{len(mask) // 4}x")


def unpack_mask(text: str) -> list[bool] | None:
    try:
        bits = bin(int(text, 16))[2:].zfill(MASK_GRID * MASK_GRID)
    except (TypeError, ValueError):
        return None
    return [b == "1" for b in bits] if len(bits) == MASK_GRID * MASK_GRID else None


def frame_delay_ms(value) -> int | None:
    """Badlion's fixed `frameDelay`. Small values are game ticks (50 ms each), larger ones milliseconds."""
    if not isinstance(value, (int, float)) or isinstance(value, bool) or value <= 0:
        return None
    ms = value * 50 if value < 10 else value
    return int(min(MAX_FRAME_MS, max(MIN_FRAME_MS, round(ms))))


def target_size(width: int, height: int, frames: int, max_size: int) -> tuple[int, int]:
    """One frame's size after scaling: the longer side at most `max_size`, the strip at most MAX_STRIP_HEIGHT."""
    scale = min(1.0, max_size / max(width, height))
    w, h = max(1, int(math.floor(width * scale + 0.5))), max(1, int(math.floor(height * scale + 0.5)))
    if frames * h > MAX_STRIP_HEIGHT:
        h = max(1, MAX_STRIP_HEIGHT // frames)
        w = max(1, int(math.floor(width * h / height + 0.5)))
    return w, h


def convert(data: bytes, *, max_size: int, quantize: bool, fixed_delay=None, file_timing: bool = True) -> Converted:
    """Decodes an AVIF still or sequence and returns the PNG to write.

    `fixed_delay` is the definition's frameDelay; it is used unless `file_timing` says the file's own frame
    durations count (Badlion's customFrameDelay), or the file has no usable durations.
    """
    from PIL import Image

    decoded = avif.decode(data)
    frames = decoded.frames
    source = frames[0].size
    w, h = target_size(source[0], source[1], len(frames), max_size)
    if (w, h) != source:
        frames = [f.resize((w, h), Image.LANCZOS) for f in frames]
    # Every frame the same size as the first (a malformed sequence could differ).
    frames = [f if f.size == (w, h) else f.resize((w, h), Image.LANCZOS) for f in frames]
    opaque = opaque_mask(frames)

    times: list[int] = []
    if len(frames) > 1:
        fixed = frame_delay_ms(fixed_delay)
        from_file = [int(round(d)) for d in decoded.durations_ms]
        usable_file = len(from_file) == len(frames) and all(d > 0 for d in from_file)
        if (file_timing or fixed is None) and usable_file:
            times = from_file
        else:
            times = [fixed or DEFAULT_FRAME_MS] * len(frames)
        times = [min(MAX_FRAME_MS, max(MIN_FRAME_MS, t)) for t in times]
        image = Image.new("RGBA", (w, h * len(frames)))
        for i, frame in enumerate(frames):
            image.paste(frame, (0, i * h))
    else:
        image = frames[0]

    if quantize:
        image = image.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    out = io.BytesIO()
    image.save(out, format="PNG", optimize=False, compress_level=9)
    return Converted(out.getvalue(), (w, h), len(frames), times, source, opaque)
