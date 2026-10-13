"""AVIF stills and AVIF image sequences.

Decoding is Pillow's (its wheels bundle libavif, so nothing needs compiling). The container is also read
directly, so a sequence's frame count and timing can be checked independently of the decoder.
"""

from __future__ import annotations

import io
import struct
from dataclasses import dataclass, field


@dataclass
class ContainerInfo:
    """What the ISOBMFF boxes say, without decoding any pixels."""

    brand: str
    compatible: list[str]
    sequence: bool
    frames: int | None = None
    durations_ms: list[float] = field(default_factory=list)
    width: int | None = None
    height: int | None = None


def _boxes(data: bytes, start: int, end: int):
    pos = start
    while pos + 8 <= end:
        size, kind = struct.unpack(">I4s", data[pos:pos + 8])
        header = 8
        if size == 1:
            if pos + 16 > end:
                return
            size = struct.unpack(">Q", data[pos + 8:pos + 16])[0]
            header = 16
        elif size == 0:
            size = end - pos
        if size < header or pos + size > end:
            return
        yield kind.decode("latin-1"), pos + header, pos + size
        pos += size


def _child(data: bytes, start: int, end: int, kind: str):
    for k, s, e in _boxes(data, start, end):
        if k == kind:
            return s, e
    return None


def read_container(data: bytes) -> ContainerInfo:
    ftyp = _child(data, 0, len(data), "ftyp")
    if ftyp is None:
        raise ValueError("no ftyp box: not an AVIF/ISOBMFF file")
    s, e = ftyp
    brand = data[s:s + 4].decode("latin-1")
    compatible = [data[i:i + 4].decode("latin-1") for i in range(s + 8, e - 3, 4)]
    info = ContainerInfo(brand, compatible, brand == "avis" or "avis" in compatible)

    moov = _child(data, 0, len(data), "moov")
    if moov is not None:
        # The colour track is the one whose handler is 'pict' (an alpha plane is an 'auxv' track).
        for kind, ts, te in _boxes(data, *moov):
            if kind != "trak":
                continue
            mdia = _child(data, ts, te, "mdia")
            if mdia is None:
                continue
            hdlr = _child(data, *mdia, "hdlr")
            if hdlr is None or data[hdlr[0] + 8:hdlr[0] + 12] != b"pict":
                continue
            mdhd = _child(data, *mdia, "mdhd")
            timescale = None
            if mdhd is not None:
                version = data[mdhd[0]]
                timescale = struct.unpack(">I", data[mdhd[0] + (20 if version == 1 else 12):][:4])[0]
            tkhd = _child(data, ts, te, "tkhd")
            if tkhd is not None:
                version = data[tkhd[0]]
                w, h = struct.unpack(">II", data[tkhd[1] - 8:tkhd[1]])
                info.width, info.height = w >> 16, h >> 16
            minf = _child(data, *mdia, "minf")
            stbl = _child(data, *minf, "stbl") if minf else None
            stts = _child(data, *stbl, "stts") if stbl else None
            if stts is not None and timescale:
                count = struct.unpack(">I", data[stts[0] + 4:stts[0] + 8])[0]
                durations = []
                for i in range(count):
                    samples, delta = struct.unpack(">II", data[stts[0] + 8 + i * 8:stts[0] + 16 + i * 8])
                    durations.extend([delta * 1000.0 / timescale] * samples)
                info.frames = len(durations)
                info.durations_ms = durations
            break
    return info


@dataclass
class Decoded:
    frames: list  # PIL.Image.Image, RGBA, one per frame
    durations_ms: list[float]

    @property
    def size(self) -> tuple[int, int]:
        return self.frames[0].size


def decode(data: bytes) -> Decoded:
    """Every frame as RGBA with its duration. Each frame is loaded before its duration is read: Pillow
    only fills in a frame's info once its pixels are loaded."""
    from PIL import Image

    image = Image.open(io.BytesIO(data))
    image.load()
    count = getattr(image, "n_frames", 1)
    frames = []
    durations = []
    for i in range(count):
        if i:
            image.seek(i)
            image.load()
        frames.append(image.convert("RGBA"))
        durations.append(float(image.info.get("duration") or 0))
    return Decoded(frames, durations)


def check_decoder() -> str | None:
    """None when Pillow can decode AVIF, else what's wrong and how to fix it."""
    try:
        from PIL import features
    except ImportError:
        return "Pillow is not installed: run  pip install \"pillow>=11.3\""
    if not features.check("avif"):
        import PIL

        return (f"Pillow {PIL.__version__} has no AVIF support: run  pip install --upgrade \"pillow>=11.3\"  "
                "(its wheels include AVIF on Windows, macOS and Linux)")
    return None
