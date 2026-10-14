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
    assert ids == ["imported_emotes:funky_dance", "imported_emotes:wave", "lunar_emotes:wave"]
    assert found.archive_animations_passed == 1

    wave = next(e for e in found.emotes if e.id == "lunar_emotes:wave")
    animation = wave.doc["animations"]["animation.emote.wave"]
    # Bones renamed to the mod's, numbers made numbers, GeckoLib's easing kept.
    assert sorted(animation["bones"]) == ["body", "right_arm"]
    assert animation["bones"]["right_arm"]["rotation"]["0.0"] == [0.0, 0.0, 0.0]
    assert animation["bones"]["right_arm"]["rotation"]["0.5"] == {"vector": [-10, 0, 150.0], "easing": "easeInOutSine"}
    # No length given: as long as its last keyframe.
    assert animation["animation_length"] == 1.25
    assert animation["loop"] is False
    notes = " ".join(wave.notes)
    assert "Molang" in notes and "scale" in notes and "cape_bone" in notes

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
