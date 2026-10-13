import hashlib
import json
import zipfile

import pytest

from badlion_import import avif, badlion, inspection
from badlion_import.archive import Archive, LayoutError
from badlion_import.cli import main

from . import fixtures


@pytest.fixture()
def input_folder(tmp_path):
    return fixtures.build(tmp_path / "input")


def snapshot(folder):
    return {p.name: hashlib.sha1(p.read_bytes()).hexdigest() for p in sorted(folder.iterdir())}


def test_archive_reads_entries_by_absolute_path(input_folder):
    with Archive(input_folder) as archive:
        assert set(archive.jars) == {0, 5}
        assert archive.has(badlion.COSMETICS_JSON)
        assert not archive.has(fixtures.T + "cloak/cloak_9.avif"), "trimmed entries are simply not present"
        assert archive.verify(badlion.COSMETICS_JSON) == "ok"
        assert archive.locate(fixtures.T + "hat/hat_1.avif")[0] == 0


def test_refuses_the_live_install(tmp_path):
    folder = fixtures.build(tmp_path / ".minecraft" / "copy")
    with pytest.raises(LayoutError, match=".minecraft"):
        Archive(folder)


def test_avif_sequences_decode_with_their_timing():
    data = fixtures.avif_sequence([100, 40, 250])
    info = avif.read_container(data)
    assert info.sequence and info.brand == "avis"
    assert info.frames == 3
    assert [round(d) for d in info.durations_ms] == [100, 40, 250]
    decoded = avif.decode(data)
    assert len(decoded.frames) == 3
    assert decoded.durations_ms == [100, 40, 250]
    assert not avif.read_container(fixtures.avif_still()).sequence


def test_inspect_confirms_the_layout_and_finds_the_cape_model(input_folder):
    with Archive(input_folder) as archive:
        result = inspection.run(archive)
        layout = inspection.layout_markdown(result, archive)
    status = {c.claim: c.status for c in result.checks}
    assert status["each file is {hash, size, absolutePath}"] == inspection.CONFIRMED
    assert status["jars are plain zips with stored (uncompressed) entries"] == inspection.CONFIRMED
    assert status["hash is the SHA-1 of the file (used for incremental re-imports)"] == inspection.CONFIRMED
    assert status["definitions are all in one jar (usually data5)"] == inspection.CONFIRMED
    assert status["example.json is a commented schema example"] == inspection.CONFIRMED
    assert status["animated textures are AVIF image sequences (ftyp avis) with frame timing"] == inspection.CONFIRMED
    assert status["cosmetic fields are the expected ones"] == inspection.PARTLY, "mysteryField is reported"
    assert result.cape_model.endswith("cloak_v2/cloak_v2.md5mesh"), "the UVs that match the painted half win"
    assert "WINGS/angel" in result.missing_models
    assert result.missing_models["WINGS/angel"] == ["WINGS:6"]
    assert any("<folder>_<n>_extra.avif" in line for line in result.layout_details), "unexpected files are listed as patterns"

    present = {s.cosmetic.key: s for s in result.states}
    assert present["CAPE:1"].present and present["CAPE:1"].model == "default cloak"
    assert present["CAPE:7"].present and present["CAPE:7"].cosmetic.animated
    assert not present["CAPE:9"].present
    assert present["WINGS:1"].model == "default wing"
    assert not present["WINGS:6"].present, "its model was trimmed away"
    assert present["HAT:1"].model == "own"

    # The numbers that place each model, for checking the conversion.
    row = next(line for line in layout.splitlines() if line.startswith("| `HAT/tophat` |"))
    assert '{"x":0,"y":1,"z":0}' in row

    # Structure only: no cosmetic names in LAYOUT.md.
    for name in ("Red Cape", "Flowing Cape", "Angel", "Top Hat"):
        assert name not in layout


def test_inspect_command_writes_layout_and_never_touches_the_input(input_folder, tmp_path, capsys):
    before = snapshot(input_folder)
    layout = tmp_path / "LAYOUT.md"
    assert main(["inspect", str(input_folder), "--layout", str(layout), "--quiet", "--verbose"]) == 0
    assert snapshot(input_folder) == before
    out = capsys.readouterr().out
    assert "CAPE" in out and "Needs the default wing model" in out and "WINGS/angel" in out
    assert layout.read_text(encoding="utf-8").startswith("# Badlion data layout")


def test_outputs_inside_the_input_are_refused(input_folder):
    assert main(["inspect", str(input_folder), "--layout", str(input_folder / "LAYOUT.md"), "--quiet"]) == 2


def test_list(input_folder, capsys):
    assert main(["list", str(input_folder)]) == 0
    out = capsys.readouterr().out
    assert "Red Cape" in out and "Not Owned Cape" in out
    lines = {line.split()[0] + line.split()[1]: line for line in out.splitlines()[1:]}
    assert "missing" in lines["CAPE9"]
    assert "present" in lines["HAT1"]
    main(["list", str(input_folder), "--present", "--type", "cape"])
    out = capsys.readouterr().out
    assert "Not Owned" not in out and "Top Hat" not in out


def test_layout_differences_are_reported(tmp_path):
    folder = fixtures.build(tmp_path / "input")
    manifest = json.loads((folder / "assets.json").read_text())
    manifest["extra"] = 1
    (folder / "assets.json").write_text(json.dumps(manifest))
    with zipfile.ZipFile(folder / "data0.jar", "a", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("unlisted/file.txt", "hello " * 50)
    with Archive(folder) as archive:
        result = inspection.run(archive)
    status = {c.claim: c.status for c in result.checks}
    assert status["assets.json is {id, version, buildType, chunkData: [{chunkId, files: [...]}]}"] == inspection.PARTLY
    assert status["jars are plain zips with stored (uncompressed) entries"] == inspection.PARTLY
    assert status["entries are stored under their absolutePath"] == inspection.PARTLY


def test_bad_manifest_is_a_clear_error(tmp_path):
    folder = tmp_path / "input"
    folder.mkdir()
    (folder / "assets.json").write_text('{"chunks": []}')
    with pytest.raises(LayoutError, match="chunkData"):
        Archive(folder)


def test_cosmetic_ids():
    c = badlion.Cosmetic("WINGS", 6, {"name": "Angel"})
    assert badlion.cosmetic_id(c) == "wings_6_angel"
    assert badlion.cosmetic_id(badlion.Cosmetic("CAPE", 863, {"name": "Über Cape #2!"})) == "cape_863_ber_cape_2"


def test_strip_comments_keeps_strings():
    text = '{"a": "http://x//y", /* c */ "b": [1, 2,], // end\n}'
    assert json.loads(badlion.strip_comments(text)) == {"a": "http://x//y", "b": [1, 2]}
