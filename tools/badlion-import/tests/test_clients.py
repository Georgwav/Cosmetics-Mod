"""Lunar and NoRisk cosmetics to a pack (clients.py), on made-up folders shaped like the real ones."""

import json

from PIL import Image

from badlion_import import clients
from badlion_import.cli import main

from . import client_fixtures


def _import(tmp_path):
    source = client_fixtures.build(tmp_path / "Imports")
    out = tmp_path / "pack"
    assert main(["import", str(source), str(out), "--quiet"]) == 0
    return source, out


def _doc(out, ident):
    ns, name = ident.split(":")
    return json.loads((out / "assets" / ns / "cosmetics" / f"{name}.json").read_text(encoding="utf-8"))


def test_import_finds_both_clients_and_leaves_the_shop_out(tmp_path):
    source, out = _import(tmp_path)
    ids = sorted(p.stem for p in (out / "assets").rglob("cosmetics/*.json"))
    assert ids == sorted(["ashfall", "butterfly_aura_pink", "zzv4_cloak_basti_zickzack_v4_flames_cloak",
                          "zzv4_shield_bastis_shield", "zzv4_wings_zickzack_wings", "bowtie", "red_bowtie", "katana",
                          "tophat", "wm_flag", "wm_flag_ger", "cape_gamescom25"])
    report = (out / clients.REPORT).read_text(encoding="utf-8")
    assert "## Lunar" in report and "## Norisk" in report
    # Never written into the input.
    assert not (source / "pack.mcmeta").exists()
    assert json.loads((out / "pack.mcmeta").read_text())["pack"]["description"].startswith("Your own Lunar")


def test_lunar_wings_keep_their_animations_and_states(tmp_path):
    _, out = _import(tmp_path)
    doc = _doc(out, "lunar_import:zzv4_wings_zickzack_wings")
    assert doc["slot"] == "back" and doc["kind"] == "wings"
    assert doc["model"] == "lunar_import:zzv4_wings_zickzack_wings"
    assert doc["model_animations"] == "lunar_import:zzv4_wings_zickzack_wings.animation"
    # Lunar's state machine: gliding first, blended in over its ticks; otherwise main (the default 8 ticks).
    assert doc["model_states"] == [[{"animation": "elytra", "when": "q.is_elytra_flying", "blend": 0.1},
                                    {"animation": "main", "blend": 0.4}]]
    # A strip of 32 x 32 frames, 2 ticks each.
    assert doc["texture_animation"] == {"frames": 3, "frame_time": 100}
    png = Image.open(out / "assets/lunar_import/textures/cosmetic/zzv4_wings_zickzack_wings.png")
    assert png.size == (32, 96)
    geo = json.loads((out / "assets/lunar_import/cosmetic_models/zzv4_wings_zickzack_wings.geo.json").read_text())
    assert "minecraft:geometry" in geo
    anim = json.loads((out / "assets/lunar_import/cosmetic_models/zzv4_wings_zickzack_wings.animation.json").read_text())
    assert set(anim["animations"]) == {"main", "elytra"}


def test_lunar_auras_shields_and_capes(tmp_path):
    _, out = _import(tmp_path)
    aura = _doc(out, "lunar_import:ashfall")
    assert aura["slot"] == "aura"
    assert aura["bones"] == {"ash_1": {"mode": "translucent"}, "ash_2": {"mode": "translucent"}}
    assert aura["model_states"] == [[{"animation": "gui", "when": "q.is_in_gui"}, {"animation": "main"}]]
    # A colour of an aura: named after both, the model found one folder up.
    pink = _doc(out, "lunar_import:butterfly_aura_pink")
    assert pink["name"] == "Butterfly aura (Pink)" and pink["slot"] == "aura"

    shield = _doc(out, "lunar_import:zzv4_shield_bastis_shield")
    assert shield["slot"] == "shield"
    # The plate was thin along X on the arm: turned so it is thin along Z, centred on the vanilla plate.
    assert shield["model_placement"]["rotation"] == [0.0, 90.0, 0.0]
    assert shield["model_states"] == [[{"animation": "strap", "when": "q.is_first_person"}], [{"animation": "main"}]]

    cape = _doc(out, "lunar_import:zzv4_cloak_basti_zickzack_v4_flames_cloak")
    assert cape["slot"] == "cape" and cape["texture_size"] == [44, 34]
    cube = cape["parts"][0]["cubes"][0]
    assert cube["from"] == [-5.0, 0, -1] and cube["to"] == [5.0, 16, 0]
    assert cape["texture_animation"] == {"frames": 2, "frame_time": 50}


def test_norisk_models_follow_the_player_skeleton(tmp_path):
    _, out = _import(tmp_path)
    bowtie = _doc(out, "norisk_import:bowtie")
    assert bowtie["skeleton"] == "player" and bowtie["slot"] == "body"
    assert bowtie["kind"] == "accessory_body"
    assert bowtie["model_placement"] == {"scale": 1.1}
    # Its only moving animation is the shop's, which doesn't play in the world.
    assert "model_animations" not in bowtie

    katana = _doc(out, "norisk_import:katana")
    assert katana["kind"] == "accessory_right_arm"
    assert katana["model_states"] == [{"animation": "nrc.idle"}]
    # Square frames when the .mcmeta gives no size; NoRisk's frame time is in milliseconds.
    assert katana["texture_animation"] == {"frames": 4, "frame_time": 60}

    assert _doc(out, "norisk_import:tophat")["slot"] == "head"
    assert _doc(out, "norisk_import:red_bowtie")["name"] == "Red Bow Tie"
    flag = _doc(out, "norisk_import:wm_flag_ger")
    assert flag["name"] == "WM Flag (GER)" and flag["model"] == "norisk_import:wm_flag"

    cape = _doc(out, "norisk_import:cape_gamescom25")
    assert cape["texture_size"] == [22, 17]
    assert cape["texture_animation"] == {"frames": 3, "frame_time": 75}


def test_name_filter_and_rerun_into_the_same_folder(tmp_path):
    source = client_fixtures.build(tmp_path / "Imports")
    out = tmp_path / "pack"
    assert main(["import", str(source), str(out), "--quiet", "--name", "basti,katana"]) == 0
    ids = sorted(p.stem for p in (out / "assets").rglob("cosmetics/*.json"))
    assert ids == ["katana", "zzv4_cloak_basti_zickzack_v4_flames_cloak", "zzv4_shield_bastis_shield"]
    # Again with everything: the folder is reused, nothing is left over from before.
    assert main(["import", str(source), str(out), "--quiet"]) == 0
    assert len(list((out / "assets").rglob("cosmetics/*.json"))) == 12
    # A folder this tool didn't write is refused.
    other = tmp_path / "other"
    other.mkdir()
    (other / "mine.txt").write_text("x")
    assert main(["import", str(source), str(other), "--quiet"]) == 2


def test_bone_names_follow_the_parts():
    assert clients.follow_of("bipedRightArm") == "right_arm"
    assert clients.follow_of("armor_left_leg") == "left_leg"
    assert clients.follow_of("blade") is None
