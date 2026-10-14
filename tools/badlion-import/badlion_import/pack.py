"""Writes an import plan out as a resource pack: pack.mcmeta, the cosmetics, their textures and models, and a
report. The pack works as it is (Options → Resource Packs) or bundled into a personal build.

Re-running into the same folder only re-encodes textures whose source changed (or whose size settings did), and
removes files from earlier runs that this run no longer writes.
"""

from __future__ import annotations

import json
import os
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

from . import __version__, badlion, convert, textures
from .archive import Archive, LayoutError

MARKER = ".blc-import-cache.json"
REPORT = "import-report.md"
DEFAULT_MAX_TRIANGLES = 100_000  # the mod's default maxMeshTriangles
# The conversion's format, stamped into the pack (assets/<namespace>/cosmetics_import.json): the mod asks for a new
# import when a pack's is older than its fixes need (CosmeticLoader.IMPORT_FORMATS). 2: capes the vanilla length,
# wings sized and placed as Badlion has them.
IMPORT_FORMAT = 2
IMPORT_STAMP = "cosmetics_import.json"

PACK_MCMETA = {
    "pack": {
        "description": "Your own Badlion cosmetics, for Cosmetics Mod (personal use only)",
        "pack_format": 34,
        "supported_formats": {"min_inclusive": 34, "max_inclusive": 999},
        "min_format": 34,
        "max_format": 999,
    }
}


@dataclass
class Written:
    cosmetics: list[convert.Planned] = field(default_factory=list)
    failed: list[tuple[convert.Planned, str]] = field(default_factory=list)
    textures_converted: int = 0
    textures_reused: int = 0


def check_output(output: Path) -> None:
    """The output is a fresh folder, or one this tool wrote before (so removing stale files is safe)."""
    if output.exists() and not output.is_dir():
        raise LayoutError(f"{output} exists and is not a folder")
    if output.is_dir() and any(output.iterdir()) and not (output / MARKER).is_file():
        raise LayoutError(f"{output} is not empty and wasn't written by this tool; choose an empty or new folder")


def _source_key(archive: Archive, path: str, job: convert.TextureJob, settings: convert.Settings) -> str:
    _, info = archive.locate(path)
    return json.dumps([path, info.CRC, info.file_size, settings.max_texture_size, settings.quantize,
                       job.fixed_delay, job.file_timing], sort_keys=True, default=str)


def _convert(args):
    data, max_size, quantize, fixed_delay, file_timing = args
    return textures.convert(data, max_size=max_size, quantize=quantize, fixed_delay=fixed_delay,
                            file_timing=file_timing)


def write(plan: convert.Plan, planner: convert.Planner, archive: Archive, output: Path, *, jobs: int | None = None,
          progress=lambda message: None) -> Written:
    check_output(output)
    output.mkdir(parents=True, exist_ok=True)
    cache_path = output / MARKER
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.is_file() else {}
    cached = cache.get("textures", {})
    settings = planner.settings
    written_files: set[Path] = set()
    result = Written()

    # Textures: one per location, shared by every cosmetic (and calibration variant) that uses it.
    texture_jobs: dict[str, convert.TextureJob] = {}
    for p in plan.planned:
        for job in p.textures:
            texture_jobs.setdefault(job.location, job)
    infos: dict[str, textures.Converted] = {}
    todo = []
    for location, job in texture_jobs.items():
        target = output / convert.location_to_path(location)
        key = _source_key(archive, job.source, job, settings)
        entry = cached.get(location)
        if entry and entry.get("key") == key and target.is_file():
            opaque = textures.unpack_mask(entry.get("opaque"))
            if opaque is None:  # written by an older version: read it from the PNG
                opaque = textures.mask_from_png(target.read_bytes(), entry["frame_size"][1])
                entry = {**entry, "opaque": textures.pack_mask(opaque)}
                cached[location] = entry
            infos[location] = textures.Converted(b"", tuple(entry["frame_size"]), entry["frames"],
                                                 entry["frame_times"], tuple(entry["source_size"]), opaque)
            result.textures_reused += 1
            written_files.add(target)
        else:
            todo.append((location, job, key, target))
    new_cache = {loc: cached[loc] for loc in infos if loc in cached}
    if todo:
        progress(f"converting {len(todo)} texture(s), reusing {result.textures_reused}")
        workers = max(1, jobs if jobs else (os.cpu_count() or 1))
        pool = ProcessPoolExecutor(max_workers=workers) if workers > 1 else None
        done = 0
        try:
            # In batches, so only a few source files are in memory at a time.
            batch_size = max(8, workers * 4)
            for start in range(0, len(todo), batch_size):
                batch = todo[start:start + batch_size]
                work = [(archive.read(job.source), settings.max_texture_size, settings.quantize, job.fixed_delay,
                         job.file_timing) for _, job, _, _ in batch]
                converted = pool.map(_convert, work) if pool else map(_convert, work)
                for (location, job, key, target), info in zip(batch, converted):
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(info.png)
                    written_files.add(target)
                    infos[location] = info
                    new_cache[location] = {"key": key, "frame_size": list(info.frame_size), "frames": info.frames,
                                           "frame_times": info.frame_times, "source_size": list(info.source_size),
                                           "opaque": textures.pack_mask(info.opaque)}
                    result.textures_converted += 1
                done += len(batch)
                if len(todo) > batch_size:
                    progress(f"{done} of {len(todo)} textures converted")
        except LayoutError:
            raise
        except Exception as e:  # noqa: BLE001 - a decoder error names no file; say which batch
            raise LayoutError(f"converting textures failed near {batch[0][1].source}: {e}") from e
        finally:
            if pool is not None:
                pool.shutdown()

    # Models: written once each.
    models: dict[str, convert.ModelFile] = {}
    for p in plan.planned:
        for m in p.models:
            models.setdefault(m.location, m)
    for location, m in models.items():
        target = output / convert.location_to_path(location)
        target.parent.mkdir(parents=True, exist_ok=True)
        if m.source.endswith(".obj"):
            target.write_text(planner.text(m.source), encoding="utf-8", newline="\n")
        else:
            target.write_bytes(archive.read(m.source))
        written_files.add(target)

    # Cosmetics.
    for p in plan.planned:
        try:
            doc = p.build(infos)
        except (KeyError, ValueError, TypeError) as e:
            result.failed.append((p, f"{type(e).__name__}: {e}"))
            continue
        target = output / "assets" / convert.NAMESPACE / "cosmetics" / f"{p.id}.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
        written_files.add(target)
        result.cosmetics.append(p)

    mcmeta = output / "pack.mcmeta"
    mcmeta.write_text(json.dumps(PACK_MCMETA, indent=2) + "\n", encoding="utf-8")
    written_files.add(mcmeta)
    stamp = output / "assets" / convert.NAMESPACE / IMPORT_STAMP
    stamp.parent.mkdir(parents=True, exist_ok=True)
    stamp.write_text(json.dumps({"format": IMPORT_FORMAT, "importer": __version__}) + "\n", encoding="utf-8")
    written_files.add(stamp)

    # Files an earlier run wrote that this one didn't.
    ours = output / "assets" / convert.NAMESPACE
    if ours.is_dir():
        for path in sorted(ours.rglob("*"), reverse=True):
            if path.is_file() and path not in written_files:
                path.unlink()
            elif path.is_dir() and not any(path.iterdir()):
                path.rmdir()

    cache_path.write_text(json.dumps({"version": __version__, "textures": new_cache}, indent=1), encoding="utf-8")
    return result


# ---------------------------------------------------------------------- report

def _range(values) -> str:
    values = [v for v in values if isinstance(v, (int, float)) and not isinstance(v, bool)]
    if not values:
        return "none"
    common = Counter(values).most_common(3)
    return (f"{len(values)} values, {min(values):g} to {max(values):g}; most common "
            + ", ".join(f"{v:g} ×{n}" for v, n in common))


def report(plan: convert.Plan, written: Written, planner: convert.Planner, settings: convert.Settings,
           calibrate: bool, max_triangles: int = DEFAULT_MAX_TRIANGLES) -> str:
    lines = [f"# Badlion import{' (calibration)' if calibrate else ''}", "",
             f"Written by `badlion-import import` {__version__}. Personal use only: keep this pack private.", ""]
    by_type = Counter(p.cosmetic.type for p in written.cosmetics)
    skipped_by_type = Counter(s.cosmetic.type for s in plan.skipped)
    lines += ["## Converted", "", "| Type | Slot | Converted | Skipped |", "|---|---|---|---|"]
    for t in sorted(set(by_type) | set(skipped_by_type)):
        slot = convert.RULES[t].slot if t in convert.RULES else "-"
        lines.append(f"| {t} | {slot} | {by_type.get(t, 0)} | {skipped_by_type.get(t, 0)} |")
    lines += ["", f"Textures: {written.textures_converted} converted, {written.textures_reused} unchanged since the "
                  f"last run (max {settings.max_texture_size} px, {'256 colours' if settings.quantize else 'full colour'})."]
    if planner.flipped_by_fit:
        lines.append("Texture direction turned around to fit the texture (see Notes): "
                     + ", ".join(f"{t} {n}" for t, n in sorted(planner.flipped_by_fit.items())) + ".")
    if planner.obj_stats["clamped_uvs"] or planner.obj_stats["added_uvs"]:
        lines.append(f"OBJ fixes: {planner.obj_stats['clamped_uvs']} texture coordinates outside 0..1 clamped, "
                     f"{planner.obj_stats['added_uvs']} faces without texture coordinates given one.")
    if planner.cape_model == convert.BOX_CAPE:
        lines.append("Capes without a model are a box like the vanilla cape, 10 x 16 x 1, textured from the top "
                     "22 x 17 blocks of the texture (Badlion's 22 x 23 textures have six rows below the cape).")
    else:
        lines.append(f"Capes without a model use `{planner.cape_model.replace(badlion.TEXTURE_ROOT, '')}`.")

    heavy = [p for p in written.cosmetics if p.triangles > max_triangles]
    if heavy:
        lines += ["", "## Over the mod's triangle limit", "",
                  f"These have more than {max_triangles} triangles. Raise `maxMeshTriangles` in "
                  "`config/cosmeticsmod.json` (up to 200000) to load them.", ""]
        lines += [f"- {p.cosmetic.type} {p.cosmetic.id} {p.cosmetic.name}: {p.triangles}" for p in heavy]

    if written.failed:
        lines += ["", "## Failed", ""] + [f"- {p.cosmetic.type} {p.cosmetic.id} {p.cosmetic.name}: {why}"
                                          for p, why in written.failed]

    reasons = defaultdict(list)
    for s in plan.skipped:
        reasons[s.reason].append(s)
    if reasons:
        lines += ["", "## Skipped", ""]
        for reason, items in sorted(reasons.items(), key=lambda kv: -len(kv[1])):
            types = Counter(s.cosmetic.type for s in items)
            lines.append(f"- **{reason}**: {len(items)} ({', '.join(f'{t} {n}' for t, n in types.most_common())})")

    notes = [(p, n) for p in written.cosmetics for n in p.notes]
    if notes:
        lines += ["", "## Notes", ""] + [f"- {p.cosmetic.type} {p.cosmetic.id} {p.cosmetic.name}: {n}"
                                         for p, n in notes]

    # Numbers that decide placement and timing, for checking the conversion's assumptions.
    d = planner.d
    used_models = [p for p in d.models.values() if p.users]
    lines += ["", "## Values used for placement", ""]
    lines.append(f"- models.json scale (0 = default 1): {_range([m.raw.get('scale') for m in used_models])}")
    for axis in "xyz":
        lines.append(f"- models.json translation.{axis}: "
                     f"{_range([(m.raw.get('translation') or {}).get(axis) for m in used_models if isinstance(m.raw.get('translation'), dict)])}")
    for group, keys in (("sizingData", ("scale", "offsetX", "offsetY", "offsetZ")),
                        ("wingData", ("flapBase", "xOffset", "yOffset", "zOffset", "sneakOffset", "sneakZOffset",
                                      "armorOffset")),
                        ("animationData", ("frameDelay", "frames"))):
        for key in keys:
            values = [(c.raw.get(group) or {}).get(key) for c in d.cosmetics if isinstance(c.raw.get(group), dict)]
            lines.append(f"- {group}.{key}: {_range(values)}")

    lines += ["", "## Cosmetics", "", "| Type | Badlion id | Name | Id in game |", "|---|---|---|---|"]
    for p in sorted(written.cosmetics, key=lambda p: (p.cosmetic.type, str(p.cosmetic.id), p.id)):
        lines.append(f"| {p.cosmetic.type} | {p.cosmetic.id} | {p.name.replace('|', '/')} | "
                     f"`{convert.NAMESPACE}:{p.id}` |")
    return "\n".join(lines) + "\n"
