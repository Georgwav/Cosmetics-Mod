"""Command line: `list`, `inspect` and `import`."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import __version__, avif, badlion, convert, inspection, pack
from .archive import Archive, LayoutError

TOOL_DIR = Path(__file__).resolve().parent.parent


def default_layout() -> Path:
    """tools/badlion-import/LAYOUT.md when the tool runs from its source folder (an editable install); a plain
    `pip install .` copies it into site-packages, so then the current folder."""
    if (TOOL_DIR / "pyproject.toml").is_file():
        return TOOL_DIR / "LAYOUT.md"
    return Path.cwd() / "LAYOUT.md"


def _repo_root() -> Path | None:
    """This mod's repository: the clone the tool runs from, or the one the current folder is in."""
    for start in (TOOL_DIR, Path.cwd().resolve()):
        for parent in (start, *start.parents):
            if (parent / "settings.gradle.kts").is_file() and (parent / ".git").exists():
                return parent
    return None


def refuse_output(path: Path, input_folder: Path, allow_in_repo: bool = False) -> None:
    """Outputs never go into the input folder, and (except LAYOUT.md) never into this mod's repository."""
    path = path.resolve()
    if path == input_folder.resolve() or input_folder.resolve() in path.parents:
        raise LayoutError(f"{path} is inside the input folder, which is never written to")
    repo = _repo_root()
    if repo is not None and not allow_in_repo and (path == repo or repo in path.parents):
        raise LayoutError(f"{path} is inside the Cosmetics-Mod repository. Converted Badlion content belongs in "
                          "your private cosmetics-personal repository only.")


def _progress(quiet: bool):
    def report(message: str) -> None:
        if not quiet:
            print(f"... {message}", file=sys.stderr, flush=True)
    return report


def cmd_list(args) -> int:
    with Archive(Path(args.input)) as archive:
        present = archive.present_paths()
        definitions = badlion.load(archive)
        result = inspection.Result()
        # Presence only needs the cape model when there are capes without a model.
        result.cape_model = next((p for p in sorted(present) if p.startswith(badlion.TEXTURE_ROOT + "cloak/models/")
                                  and p.endswith((".md5mesh", ".obj"))), None)
        inspection._states(definitions, present, result)
        print(f"{'Type':<12}{'Id':>7}  {'Name':<34}{'Status':<9} Model")
        for st in sorted(result.states, key=lambda s: (s.cosmetic.type, _sort_id(s.cosmetic.id))):
            c = st.cosmetic
            if args.type and c.type.lower() != args.type.lower():
                continue
            if args.present and not st.present:
                continue
            status = "present" if st.present else "missing"
            model = st.model_name or st.model
            print(f"{c.type:<12}{str(c.id):>7}  {c.name[:33]:<34}{status:<9} {model}"
                  + (f"  ({'; '.join(st.problems)})" if st.problems and not st.present else ""))
    return 0


def _sort_id(value):
    return (0, value) if isinstance(value, int) else (1, str(value))


def cmd_inspect(args) -> int:
    problem = avif.check_decoder()
    if problem:
        print(problem, file=sys.stderr)
        return 2
    input_folder = Path(args.input)
    layout = Path(args.layout) if args.layout else default_layout()
    refuse_output(layout, input_folder, allow_in_repo=True)
    if args.summary:
        refuse_output(Path(args.summary), input_folder)
    with Archive(input_folder) as archive:
        result = inspection.run(archive, verify_hashes=not args.no_hash, progress=_progress(args.quiet))
        layout.write_text(inspection.layout_markdown(result, archive), encoding="utf-8")
    summary = inspection.summary_text(result, verbose=args.verbose)
    print(summary)
    if args.summary:
        Path(args.summary).write_text(summary, encoding="utf-8")
    print(f"Wrote {layout}")
    return 0


def _select(cosmetics, args) -> list:
    """The cosmetics the options ask for: --types, --only TYPE:ID,..., --name, --limit per type."""
    types = {t.strip().upper() for t in args.types.split(",")} if args.types else None
    names = [n.strip().lower() for n in args.name.split(",") if n.strip()] if args.name else None
    only = set()
    if args.only:
        for item in args.only.split(","):
            t, _, i = item.strip().partition(":")
            only.add((t.upper(), i))
    chosen = []
    for c in cosmetics:
        if types and c.type not in types:
            continue
        if only and (c.type, str(c.id)) not in only:
            continue
        if names and not any(n in c.name.lower() or n in (c.model_name or "").lower() for n in names):
            continue
        chosen.append(c)
    return chosen


def cmd_import(args) -> int:
    problem = avif.check_decoder()
    if problem:
        print(problem, file=sys.stderr)
        return 2
    input_folder = Path(args.input)
    output = Path(args.output)
    refuse_output(output, input_folder)
    settings = convert.load_settings(Path(args.settings) if args.settings else None)
    if args.max_texture:
        settings.max_texture_size = args.max_texture
    if args.no_quantize:
        settings.quantize = False
    progress = _progress(args.quiet)
    pack.check_output(output)
    with Archive(input_folder) as archive:
        present = archive.present_paths()
        definitions = badlion.load(archive)
        planner = convert.Planner(definitions, present, archive.read, settings)
        cosmetics = _select(definitions.cosmetics, args)
        progress("planning")
        if args.calibrate:
            plan = convert.calibration_plan(planner, cosmetics)
        else:
            plan = planner.plan(cosmetics)
            if args.limit:
                kept, per_type = [], {}
                for p in plan.planned:
                    per_type[p.cosmetic.type] = per_type.get(p.cosmetic.type, 0) + 1
                    if per_type[p.cosmetic.type] <= args.limit:
                        kept.append(p)
                plan.planned = kept
        written = pack.write(plan, planner, archive, output, jobs=args.jobs, progress=progress)
        text = pack.report(plan, written, planner, settings, args.calibrate)
        (output / pack.REPORT).write_text(text, encoding="utf-8")
    print(f"Wrote {len(written.cosmetics)} cosmetic(s) to {output}"
          + (f", {len(written.failed)} failed" if written.failed else "")
          + f"; {len(plan.skipped)} skipped. Details: {output / pack.REPORT}")
    return 1 if written.failed else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="badlion-import", description=__doc__)
    parser.add_argument("--version", action="version", version=__version__)
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("list", help="every cosmetic in the input: type, id, name, present or missing")
    p.add_argument("input", help="folder with your trimmed data0.jar ... data9.jar and assets.json")
    p.add_argument("--type", help="only this type, e.g. CAPE")
    p.add_argument("--present", action="store_true", help="only cosmetics whose files are in your jars")
    p.set_defaults(func=cmd_list)

    p = sub.add_parser("inspect", help="check the input against the expected layout and write LAYOUT.md")
    p.add_argument("input", help="folder with your trimmed data0.jar ... data9.jar and assets.json")
    p.add_argument("--layout", help="where to write LAYOUT.md (default: tools/badlion-import, or the current "
                                     "folder if the tool was installed without -e)")
    p.add_argument("--summary", help="also write the summary to this file (not inside this repository)")
    p.add_argument("--no-hash", action="store_true", help="don't verify file hashes (faster)")
    p.add_argument("--verbose", action="store_true", help="list every present cosmetic in the summary")
    p.add_argument("--quiet", action="store_true", help="no progress messages")
    p.set_defaults(func=cmd_inspect)

    p = sub.add_parser("import", help="convert the cosmetics in your jars into a resource pack for the mod")
    p.add_argument("input", help="folder with your trimmed data0.jar ... data9.jar and assets.json")
    p.add_argument("output", help="pack folder to write (new, or one this tool wrote before); not in this repository")
    p.add_argument("--calibrate", action="store_true",
                   help="write a few cosmetics in every variant to compare in game, instead of everything")
    p.add_argument("--types", help="only these types, e.g. CAPE,WINGS,HAT")
    p.add_argument("--only", help="only these cosmetics, as TYPE:ID, e.g. CAPE:12,HAT:3")
    p.add_argument("--name", help="only cosmetics whose name or model name contains one of these, e.g. "
                                  "zickzack,basti (any case)")
    p.add_argument("--limit", type=int, help="at most this many cosmetics per type (for a quick test)")
    p.add_argument("--settings", help="JSON file with conversion settings (see the README)")
    p.add_argument("--max-texture", type=int, help="longest side of a texture frame in pixels (default 256)")
    p.add_argument("--no-quantize", action="store_true", help="keep full colour (bigger files)")
    p.add_argument("--jobs", type=int, help="textures converted in parallel (default: one per CPU)")
    p.add_argument("--quiet", action="store_true", help="no progress messages")
    p.set_defaults(func=cmd_import)

    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except LayoutError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
