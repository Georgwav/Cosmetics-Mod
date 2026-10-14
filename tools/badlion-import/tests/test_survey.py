"""The `survey` command on a made-up folder of another client's files."""

import io
import json
import zipfile

from PIL import Image

from badlion_import import survey
from badlion_import.cli import main

from . import fixtures


def _png(size) -> bytes:
    out = io.BytesIO()
    Image.new("RGBA", size, (10, 20, 30, 255)).save(out, format="PNG")
    return out.getvalue()


def _folder(tmp_path):
    root = tmp_path / "Imports"
    (root / "lunar" / "cosmetics").mkdir(parents=True)
    (root / "lunar" / "cosmetics" / "index.json").write_text(json.dumps({
        "cosmetics": [{"id": 101, "name": "Secret Cape Name", "type": "CLOAK", "scale": 1.5},
                      {"id": 102, "name": "Other", "type": "HAT", "scale": 0.5}]}))
    (root / "lunar" / "cosmetics" / "cape_101.png").write_bytes(_png((64, 32)))
    (root / "lunar" / "cosmetics" / "blob.lcp").write_bytes(b"\x89LCP\x00\x01" + b"\x00" * 50)
    jar = io.BytesIO()
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr("assets/norisk/cosmetics/hat_7.json", json.dumps({"model": "hat_7.obj", "offset": [0, 1, 2]}))
        z.writestr("assets/norisk/models/hat_7.obj", fixtures.HAT_OBJ)
        z.writestr("assets/norisk/textures/hat_7.png", _png((16, 16)))
    (root / "norisk.jar").write_bytes(jar.getvalue())
    return root


def test_survey_describes_structure_but_not_contents(tmp_path):
    root = _folder(tmp_path)
    result = survey.run(root)
    text = survey.markdown(result, root.name, "test")
    assert result.files == 7  # 4 loose files (one a jar) and 3 inside it
    assert "| `lunar/cosmetics/cape_<n>.png` | 1 |" in text
    assert "| `norisk.jar!/assets/norisk/cosmetics/hat_<n>.json` | 1 |" in text
    # JSON: keys, types, number ranges and id-like values, but not free text like names.
    assert "`type`: string ×2; e.g. `CLOAK` ×1, `HAT` ×1" in text
    assert "`scale`: number ×2; 0.5 to 1.5" in text
    assert "Secret Cape Name" not in text
    # Images by size, models by counts and bounds, unknown files by their first bytes.
    assert "64×32 ×1" in text and "16×16 ×1" in text
    assert "obj: 4 vertices, 2 triangles" in text
    assert "`.lcp` | `89 4c 43 50 00 01 00 00` ×1" in text


def test_survey_command_writes_survey_md_and_leaves_the_folder_alone(tmp_path):
    root = _folder(tmp_path)
    before = sorted(p.name for p in root.rglob("*"))
    out = tmp_path / "SURVEY.md"
    assert main(["survey", str(root), "--out", str(out), "--quiet"]) == 0
    assert out.read_text(encoding="utf-8").startswith("# Survey of `Imports`")
    assert sorted(p.name for p in root.rglob("*")) == before
    # Never into the folder it reads.
    assert main(["survey", str(root), "--out", str(root / "SURVEY.md"), "--quiet"]) == 2


def test_patterns_fold_numbers_and_ids():
    assert survey.pattern("cloak/12/cloak_12.png") == "cloak/<n>/cloak_<n>.png"
    assert survey.pattern("a/0f1e2d3c4b5a6978/x.json") == "a/<hex>/x.json"
