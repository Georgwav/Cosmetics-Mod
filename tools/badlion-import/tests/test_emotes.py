"""The `emotes` command: player animations found anywhere in a folder, archives too, written as emotes."""

import json
import zipfile

from badlion_import import cli, emotes


def _write(path, doc):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(doc), encoding="utf-8")


WAVE = {
    "format_version": "1.8.0",
    "animations": {
        "animation.emote.wave": {
            "loop": False,
            "bones": {
                # GeckoLib's player bone names, numbers written as text, a scale and a Molang channel.
                "bipedRightArm": {"rotation": {"0.0": ["0", "0", "0"], "0.5": {"vector": [-10, 0, "150"],
                                                                               "easing": "easeInOutSine"}},
                                  "scale": [1, 1.2, 1]},
                "bipedHead": {"rotation": ["math.sin(query.anim_time * 90) * 10", 0, 0]},
                "Body": {"position": {"0.0": [0, 0, 0], "1.25": [0, -1, 0]}},
                "cape_bone": {"rotation": [10, 0, 0]},
            },
        }
    },
}

DANCE_EMOTECRAFT = {"name": "Funky Dance", "emote": {"beginTick": 0, "endTick": 40, "stopTick": 41, "isLoop": True,
                                                     "moves": [{"tick": 10, "rightArm": {"pitch": -1.2}}]}}

# A NoRisk cosmetic: its animation moves bones named like the player's, but it is the cosmetic's own.
NORISK_HAT_ANIMATION = {"format_version": "1.8.0", "animations": {"nrc.idle": {"loop": True, "bones": {
    "bipedHead": {"rotation": [0, 10, 0]}, "bipedBody": {"rotation": [0, 5, 0]}}}}}

# A wing model's flap: two player-named bones among many others.
WING_FLAP = {"animations": {"animation.wings.flap": {"bones": {
    "root": {"rotation": [0, 0, 0]}, "body": {"rotation": [5, 0, 0]}, "wing_left": {"rotation": [0, 30, 0]},
    "wing_right": {"rotation": [0, -30, 0]}, "feather1": {"rotation": [1, 0, 0]}}}}}
# A hat's tilt: one player bone only.
HAT_TILT = {"animations": {"animation.hat.tilt": {"bones": {"head": {"rotation": [5, 0, 0]}}}}}


WAVE_TEXT = json.dumps(WAVE)


def make_input(tmp_path):
    root = tmp_path / "clients"
    _write(root / "lunar" / "emotes" / "wave.animation.json", WAVE)
    _write(root / "norisk" / "cosmetics" / "HAT" / "top" / "top.norisk.json", {"id": "top", "type": "HAT"})
    _write(root / "norisk" / "cosmetics" / "HAT" / "top" / "top.animation.json", NORISK_HAT_ANIMATION)
    _write(root / "norisk" / "cosmetics" / "WINGS" / "w" / "w.animation.json", WING_FLAP)
    _write(root / "norisk" / "emotes" / "firework" / "firework1.particle.json", {"particle_effect": {}})
    _write(root / "misc" / "hat_tilt.json", HAT_TILT)
    (root / "badlion" / "emotes").mkdir(parents=True)
    (root / "badlion" / "emotes" / "floss.emotecraft").write_bytes(b"\x01\x02\x03")
    _write(root / "badlion" / "emotes" / "index.json", {"emotes": [1, 2], "version": 3})
    with zipfile.ZipFile(root / "extra.jar", "w") as z:
        z.writestr("assets/x/emotes/dance.json", json.dumps(DANCE_EMOTECRAFT))
        # A mod's mob walking: player-like bones, but not an emote.
        z.writestr("assets/mobmod/animations/goblin.animation.json", json.dumps({"animations": {"walk": {"bones": {
            "head": {"rotation": [5, 0, 0]}, "left_leg": {"rotation": [20, 0, 0]},
            "right_leg": {"rotation": [-20, 0, 0]}}}}}))
        z.writestr("assets/mobmod/animations/player_emotes.json", WAVE_TEXT)
    return root


def test_player_animations_anywhere_become_emotes_and_cosmetic_animations_dont(tmp_path):
    found = emotes.find(make_input(tmp_path))
    ids = sorted(e.id for e in found.emotes)
    # In an archive only what says "emote" counts: the mob's walk doesn't, the player emote file does.
    assert ids == ["imported_emotes:funky_dance", "imported_emotes:player_emotes", "lunar_emotes:wave"]
    assert found.archive_animations_passed == 1

    wave = next(e for e in found.emotes if e.id == "lunar_emotes:wave")
    animation = wave.doc["animations"]["animation.emote.wave"]
    # Bones renamed to the mod's, numbers made numbers, GeckoLib's easing kept.
    assert sorted(animation["bones"]) == ["body", "head", "right_arm"]
    assert animation["bones"]["right_arm"]["rotation"]["0.0"] == [0.0, 0.0, 0.0]
    assert animation["bones"]["right_arm"]["rotation"]["0.5"] == {"vector": [-10, 0, 150.0], "easing": "easeInOutSine"}
    # No length given: as long as its last keyframe.
    assert animation["animation_length"] == 1.25
    assert animation["loop"] is False
    notes = " ".join(wave.notes)
    assert "cape_bone" in notes
    # Parts made bigger or smaller stay so.
    assert animation["bones"]["right_arm"]["scale"] == [1, 1.2, 1]
    # Molang is kept for the mod to work out as the emote plays.
    assert animation["bones"]["head"]["rotation"] == ["math.sin(query.anim_time * 90) * 10", 0, 0]

    # Emotecraft emotes are copied as they are, inside archives too.
    dance = next(e for e in found.emotes if e.id == "imported_emotes:funky_dance")
    assert dance.doc == DANCE_EMOTECRAFT and dance.source == "extra.jar!/assets/x/emotes/dance.json"

    skipped = dict(found.skipped)
    assert any("cosmetic's own animation" in why for why in skipped.values())
    assert any("mostly other bones" in why for why in skipped.values())
    assert "moves only head" in skipped["misc/hat_tilt.json → animation.hat.tilt"]
    unknown = dict(found.unknown)
    assert "Emotecraft binary" in unknown["badlion/emotes/floss.emotecraft"]
    assert "keys emotes, version" in unknown["badlion/emotes/index.json"]
    # Firework particles aren't emotes, and aren't listed as unknown either.
    assert not any("particle" in src for src in unknown)


def test_bone_names_in_every_spelling():
    assert emotes.bone_of("bipedRightArm") == "right_arm"
    assert emotes.bone_of("Right_Arm") == "right_arm"
    assert emotes.bone_of("leftLeg") == "left_leg"
    assert emotes.bone_of("torso") == "body"
    assert emotes.bone_of("armorHead") is None
    assert emotes.bone_of("wing_left") is None


def test_command_writes_a_pack_and_report_and_rewrites_it(tmp_path):
    root = make_input(tmp_path)
    out = tmp_path / "pack"
    assert cli.main(["emotes", str(root), str(out), "--quiet"]) == 0
    wave = json.loads((out / "assets" / "lunar_emotes" / "emotes" / "wave.json").read_text())
    assert "animation.emote.wave" in wave["animations"]
    assert (out / "assets" / "imported_emotes" / "emotes" / "funky_dance.json").is_file()
    assert (out / "pack.mcmeta").is_file()
    report = (out / emotes.REPORT).read_text()
    assert "lunar_emotes:wave" in report and "floss.emotecraft" in report

    # Run again after an emote went away: the old file goes too.
    (root / "extra.jar").unlink()
    assert cli.main(["emotes", str(root), str(out), "--quiet"]) == 0
    assert not (out / "assets" / "imported_emotes" / "emotes" / "funky_dance.json").exists()


def test_command_refuses_a_folder_it_did_not_write(tmp_path):
    root = make_input(tmp_path)
    out = tmp_path / "someone_elses"
    out.mkdir()
    (out / "file.txt").write_text("keep me")
    assert cli.main(["emotes", str(root), str(out), "--quiet"]) == 2
    assert (out / "file.txt").read_text() == "keep me"


def test_norisk_emotes_whole_body_names_and_no_repeats_from_earlier_packs(tmp_path):
    root = tmp_path / "norisk" / "NoRiskClientV3"
    emotes_dir = root / "assets" / "noriskclient-cosmetics" / "emotes"
    spin = {"format_version": "1.8.0", "animations": {"ballett_spin": {"animation_length": 2, "bones": {
        "bipedRig": {"rotation": {"0": [0, 0, 0], "2": [0, 720, 0]}},
        "bipedRightArm": {"rotation": [0, 0, 90]}, "bipedLeftArm": {"rotation": [0, 0, -90]}}}}}
    _write(emotes_dir / "ballettspin.animation.json", spin)
    # Its model: the whole player turns 12 px up, at the hips.
    _write(emotes_dir / "ballettspin.geo.json", {"format_version": "1.12.0", "minecraft:geometry": [
        {"bones": [{"name": "bipedRig", "pivot": [0, 12, 0]}, {"name": "bipedHead", "parent": "bipedRig",
                                                                "pivot": [0, 24, 0]}]}]})
    # Two animations in one file: one emote each, named after the file and the animation.
    _write(emotes_dir / "handschlag" / "handschlag.animation.json", {"animations": {
        "nrc.emote.idle": {"bones": {"bipedRightArm": {"rotation": [-90, 0, 0]}, "bipedHead": {"rotation": [5, 0, 0]}}},
        "nrc.emote.interaction": {"bones": {"bipedRightArm": {"rotation": [-60, 0, 0]},
                                            "bipedRig2": {"rotation": [0, 180, 0]},
                                            "bipedHead": {"rotation": [5, 0, 0]}}}}})
    _write(root / "assets" / "noriskclient-cosmetics" / "shop" / "emotes" / "ballettspin.norisk.json",
           {"id": "ballettspin", "name": "Ballett Spin", "emoteFile": "ballettspin", "price": 100})
    # An earlier run's pack copied into a resource pack folder: passed over.
    out = tmp_path / "first"
    assert cli.main(["emotes", str(tmp_path / "norisk"), str(out), "--quiet"]) == 0
    copy = tmp_path / "norisk" / "resourcepacks" / "emotes-appdata"
    import shutil
    shutil.copytree(out, copy)

    found = emotes.find(tmp_path / "norisk")
    ids = sorted(e.id for e in found.emotes)
    assert ids == ["norisk_emotes:ballettspin", "norisk_emotes:handschlag_nrc_emote_idle",
                   "norisk_emotes:handschlag_nrc_emote_interaction"], "no _2 copies from the earlier pack"
    ballett = next(e for e in found.emotes if e.id == "norisk_emotes:ballettspin")
    animation = ballett.doc["animations"]["animation.emote.ballettspin"]
    assert animation["bones"]["root"]["rotation"]["2"] == [0.0, 720.0, 0.0]
    assert animation["root_pivot"] == 12.0
    assert ballett.name == "Ballett Spin"
    # The second player of an interaction isn't the player.
    handshake = next(e for e in found.emotes if e.id.endswith("interaction"))
    assert "root" not in handshake.doc["animations"]["animation.emote.handschlag_nrc_emote_interaction"]["bones"]

    out2 = tmp_path / "second"
    assert cli.main(["emotes", str(tmp_path / "norisk"), str(out2), "--quiet"]) == 0
    lang = json.loads((out2 / "assets" / "norisk_emotes" / "lang" / "en_us.json").read_text(encoding="utf-8"))
    assert lang == {"emote.norisk_emotes.ballettspin": "Ballett Spin"}
