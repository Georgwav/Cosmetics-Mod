"""The `bundle` command: packs inside a copy of the mod's jar."""

import json
import zipfile

from badlion_import import bundle, cli


def _write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else json.dumps(data).encode("utf-8"))


def _mod_jar(path, mod_id="cosmeticsmod"):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": mod_id}))
        z.writestr("io/github/georgwav/cosmetics/CosmeticsMod.class", b"\xca\xfe\xba\xbe")
        z.writestr("assets/cosmeticsmod/lang/en_us.json", json.dumps({"key.cosmeticsmod.open": "Open Cosmetics"}))
    return path


def _packs(tmp_path):
    badlion = tmp_path / "packs" / "badlion"
    _write(badlion / "pack.mcmeta", {"pack": {"pack_format": 34}})
    _write(badlion / "import-report.md", b"report")
    _write(badlion / "assets" / "blc_import" / "cosmetics" / "cape_1.json", {"id": "cape_1"})
    _write(badlion / "assets" / "blc_import" / "textures" / "cape_1.png", b"\x89PNG one")
    _write(badlion / "assets" / "shared_emotes" / "lang" / "en_us.json", {"emote.a": "A"})
    emotes = tmp_path / "packs" / "emotes"
    _write(emotes / "pack.mcmeta", {"pack": {"pack_format": 34}})
    _write(emotes / "assets" / "norisk_emotes" / "emotes" / "spin.json", {"animations": {}})
    _write(emotes / "assets" / "shared_emotes" / "lang" / "en_us.json", {"emote.b": "B"})
    # The same file in both: taken from the later pack.
    _write(emotes / "assets" / "blc_import" / "textures" / "cape_1.png", b"\x89PNG two")
    return badlion, emotes


def _contents(jar):
    with zipfile.ZipFile(jar) as z:
        return {name: z.read(name) for name in z.namelist()}


def test_packs_go_into_a_personal_copy_of_the_jar(tmp_path):
    jar = _mod_jar(tmp_path / "Downloads" / "cosmeticsmod-1.0+mc26.2.jar")
    badlion, emotes = _packs(tmp_path)
    assert cli.main(["bundle", str(jar), str(badlion), str(emotes)]) == 0

    personal = tmp_path / "Downloads" / "cosmeticsmod-1.0+mc26.2-personal.jar"
    files = _contents(personal)
    # The mod's own files, untouched; only the packs' assets, not their reports or pack.mcmeta.
    assert files["io/github/georgwav/cosmetics/CosmeticsMod.class"] == b"\xca\xfe\xba\xbe"
    assert json.loads(files["fabric.mod.json"])["id"] == "cosmeticsmod"
    assert json.loads(files["assets/blc_import/cosmetics/cape_1.json"]) == {"id": "cape_1"}
    assert "assets/norisk_emotes/emotes/spin.json" in files
    assert not any(name.endswith(("pack.mcmeta", "import-report.md")) for name in files)
    # Later packs win; translations are merged.
    assert files["assets/blc_import/textures/cape_1.png"] == b"\x89PNG two"
    assert json.loads(files["assets/shared_emotes/lang/en_us.json"]) == {"emote.a": "A", "emote.b": "B"}
    stamp = json.loads(files[bundle.STAMP])
    assert stamp["packs"] == ["badlion", "emotes"] and "assets/norisk_emotes/emotes/spin.json" in stamp["files"]
    # The jar it was made from is left as it was.
    assert bundle.STAMP not in _contents(jar)


def test_bundling_again_replaces_the_earlier_bundle(tmp_path):
    jar = _mod_jar(tmp_path / "mods-source" / "cosmeticsmod-1.0+mc26.2.jar")
    badlion, emotes = _packs(tmp_path)
    assert cli.main(["bundle", str(jar), str(badlion), str(emotes)]) == 0
    personal = bundle.default_output(jar)
    # Into the personal jar again, with only the emotes now: the Badlion cosmetics go, the mod stays.
    assert cli.main(["bundle", str(personal), str(emotes)]) == 0
    files = _contents(personal)
    assert "assets/blc_import/cosmetics/cape_1.json" not in files
    assert "assets/norisk_emotes/emotes/spin.json" in files
    assert json.loads(files["assets/shared_emotes/lang/en_us.json"]) == {"emote.b": "B"}
    assert "assets/cosmeticsmod/lang/en_us.json" in files
    assert json.loads(files[bundle.STAMP])["packs"] == ["emotes"]


def test_refuses_what_would_break_the_mod(tmp_path, capsys):
    jar = _mod_jar(tmp_path / "cosmeticsmod.jar")
    badlion, _ = _packs(tmp_path)
    # A pack file where the mod has its own.
    clash = tmp_path / "packs" / "clash"
    _write(clash / "assets" / "cosmeticsmod" / "lang" / "en_us.json", {"key.cosmeticsmod.open": "Hacked"})
    assert cli.main(["bundle", str(jar), str(badlion), str(clash)]) == 2
    assert "where the mod has its own" in capsys.readouterr().err
    assert not bundle.default_output(jar).exists()
    # Another mod's jar, or a folder that isn't a pack.
    other = _mod_jar(tmp_path / "other.jar", mod_id="sodium")
    assert cli.main(["bundle", str(other), str(badlion)]) == 2
    assert cli.main(["bundle", str(jar), str(tmp_path / "packs")]) == 2
    # Over the mod's own jar.
    assert cli.main(["bundle", str(jar), str(badlion), "--out", str(jar)]) == 2


def test_into_a_mods_folder_warns_about_a_second_copy(tmp_path, capsys):
    mods = tmp_path / ".minecraft" / "mods"
    jar = _mod_jar(mods / "cosmeticsmod-1.0+mc26.2.jar")
    _mod_jar(mods / "sodium.jar", mod_id="sodium")
    badlion, _ = _packs(tmp_path)
    assert cli.main(["bundle", str(jar), str(badlion), "--out", str(mods)]) == 0
    assert (mods / "cosmeticsmod-1.0+mc26.2-personal.jar").is_file()
    err = capsys.readouterr().err
    assert "cosmeticsmod-1.0+mc26.2.jar is the mod too" in err and "sodium" not in err


def test_says_where_a_mistyped_path_probably_is(tmp_path, capsys):
    # The download unzipped into a folder of its own.
    jar = _mod_jar(tmp_path / "Downloads" / "jars" / "cosmeticsmod-1.0+mc26.2.jar")
    badlion, emotes = _packs(tmp_path)
    assert cli.main(["bundle", str(tmp_path / "Downloads" / jar.name), str(badlion)]) == 2
    assert f"did you mean {jar}" in capsys.readouterr().err
    # A pack folder by another name: the packs beside it are listed.
    assert cli.main(["bundle", str(jar), str(badlion), str(tmp_path / "packs" / "emotes-norisk")]) == 2
    assert "folders in" in (err := capsys.readouterr().err) and "badlion, emotes" in err
