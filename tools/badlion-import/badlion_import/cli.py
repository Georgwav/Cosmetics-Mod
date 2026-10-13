"""Command line: `list`, `inspect` (and, once the layout is confirmed, `import`)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import __version__, avif, badlion, inspection
from .archive import Archive, LayoutError

TOOL_DIR = Path(__file__).resolve().parent.parent
DEFAULT_LAYOUT = TOOL_DIR / "LAYOUT.md"


def _repo_root() -> Path | None:
    """This mod's repository, if the tool runs from a clone of it."""
    for parent in TOOL_DIR.parents:
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
    layout = Path(args.layout) if args.layout else DEFAULT_LAYOUT
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
    p.add_argument("--layout", help=f"where to write LAYOUT.md (default {DEFAULT_LAYOUT})")
    p.add_argument("--summary", help="also write the summary to this file (not inside this repository)")
    p.add_argument("--no-hash", action="store_true", help="don't verify file hashes (faster)")
    p.add_argument("--verbose", action="store_true", help="list every present cosmetic in the summary")
    p.add_argument("--quiet", action="store_true", help="no progress messages")
    p.set_defaults(func=cmd_inspect)

    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except LayoutError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
