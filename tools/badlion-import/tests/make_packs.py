"""Writes packs from the synthetic fixtures for checking with the mod's own parser (CI's importer-pack job):

    python -m tests.make_packs <folder>

makes <folder>/input (made-up jars), <folder>/pack (a full import), <folder>/calibration, and <folder>/clients
(a Lunar and NoRisk import of <folder>/client-input).
"""

import sys
from pathlib import Path

from badlion_import.cli import main

from . import client_fixtures, fixtures


def make(folder: Path) -> None:
    source = fixtures.build(folder / "input", for_import=True)
    for name, extra in (("pack", []), ("calibration", ["--calibrate"])):
        if main(["import", str(source), str(folder / name), "--quiet", *extra]) != 0:
            raise SystemExit(f"import into {name} failed")
    clients = client_fixtures.build(folder / "client-input")
    if main(["import", str(clients), str(folder / "clients"), "--quiet"]) != 0:
        raise SystemExit("import into clients failed")


if __name__ == "__main__":
    make(Path(sys.argv[1]))
