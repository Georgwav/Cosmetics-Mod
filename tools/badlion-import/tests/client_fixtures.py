"""Made-up Lunar and NoRisk cosmetic folders, shaped like the real ones (see the survey), for the tests."""

from __future__ import annotations

import io
import json
from pathlib import Path

from PIL import Image


def _image(path: Path, size, frames: int = 1, fmt: str = "PNG", color=(200, 40, 40, 255)) -> None:
    w, h = size
    image = Image.new("RGBA", (w, h * frames), (0, 0, 0, 0))
    for i in range(frames):
        for x in range(w):
            for y in range(h):
                if (x + y + i) % 3:
                    image.putpixel((x, i * h + y), color)
    path.parent.mkdir(parents=True, exist_ok=True)
    out = io.BytesIO()
    image.save(out, format=fmt)
    path.write_bytes(out.getvalue())


def _json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1), encoding="utf-8")


def _geo(bones, width=64, height=64) -> dict:
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.unknown", "texture_width": width, "texture_height": height,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


def _cube(origin, size, uv=(0, 0)) -> dict:
    return {"origin": list(origin), "size": list(size), "uv": list(uv)}


def _face_cube(origin, size) -> dict:
    faces = {f: {"uv": [0, 0], "uv_size": [4, 4]} for f in ("north", "east", "south", "west", "up", "down")}
    return {"origin": list(origin), "size": list(size), "uv": faces}


WINGS_ANIM = {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
    "main": {"loop": True, "bones": {
        "zickzack_wings": {"position": {"vector": [0, 1, 0]}, "scale": {"vector": [0.9, 0.9, 0.9]}},
        "right_wing": {"rotation": {"vector": [0, 0, "math.sin(q.anim_time * 180) * 20"]}},
        "left_wing": {"rotation": {"vector": [0, 0, "-math.sin(q.anim_time * 180) * 20"]}}}},
    "elytra": {"loop": True, "bones": {"right_wing": {"rotation": {"vector": [0, "-14.68", 0]}}}},
}}

STATE_MACHINE = {"name": "molang", "controllers": [{
    "name": "main_controller", "default_transition_ticks": "8",
    "states": [{"anim": "elytra", "plays_when": "q.is_elytra_flying", "transition_easing": "easeInOutSine",
                "transition_ticks": "2"},
               {"anim": "main", "plays_when": "1", "transition_easing": "easeInOutSine"}]}]}


def build_lunar(root: Path) -> Path:
    lunar = root / "Lunar"
    # Wings: a pet in Lunar's terms, on the shoulder bone, with an animated texture.
    wings = lunar / "zzv4 wings"
    _json(wings / "zickzack_wings.gek.json", {
        "type": "pet", "attached_bone": "SHOULDER",
        "model": "lunar:cosmetics/models/gek/pets/zickzack_wings/zickzack_wings.geo.json",
        "texture": "lunar:cosmetics/models/gek/pets/zickzack_wings/zickzack_wings.png",
        "animation": "lunar:cosmetics/models/gek/pets/zickzack_wings/zickzack_wings.anim.json",
        "animated_texture": True, "state_machine": STATE_MACHINE})
    _json(wings / "zickzack_wings.geo.json", _geo([
        {"name": "zickzack_wings", "pivot": [0, 21.25, 2]},
        {"name": "right_wing", "parent": "zickzack_wings", "pivot": [-2.2, 21.25, 2],
         "cubes": [_face_cube([-15, 8, 2], [13, 28, 0])]},
        {"name": "left_wing", "parent": "zickzack_wings", "pivot": [2.2, 21.25, 2],
         "cubes": [_face_cube([2, 8, 2], [13, 28, 0])]}]))
    _json(wings / "zickzack_wings.anim.json", WINGS_ANIM)
    _image(wings / "zickzack_wings.webp", (32, 32), frames=3, fmt="WEBP")
    _json(wings / "zickzack_wings.webp.mcmeta", {"animation": {"frametime": 2, "width": 32, "height": 32}})

    # An aura around the player, see-through, with a GUI state.
    aura = lunar / "ashfall"
    _json(aura / "ashfall.gek.json", {
        "type": "pet", "attached_bone": "SHOULDER", "model": "lunar:cosmetics/ashfall/ashfall.geo.json",
        "texture": "lunar:cosmetics/ashfall/ashfall.png", "animation": "lunar:cosmetics/ashfall/ashfall.anim.json",
        "is_translucent": True,
        "state_machine": {"name": "molang", "controllers": [{"name": "main_controller", "states": [
            {"anim": "gui", "plays_when": "q.is_in_gui"}, {"anim": "main", "plays_when": "1"}]}]}})
    _json(aura / "ashfall.geo.json", _geo([
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "ash_1", "parent": "root", "pivot": [-10, 30, 0], "cubes": [_cube([-11, 29, 0], [2, 2, 0])]},
        {"name": "ash_2", "parent": "root", "pivot": [10, 5, 0], "cubes": [_cube([9, 4, 0], [2, 2, 0])]}],
        32, 32))
    _json(aura / "ashfall.anim.json", {"format_version": "1.8.0", "animations": {
        "main": {"loop": True, "animation_length": 2, "bones": {"ash_1": {"position": {
            "0.0": [0, 0, 0], "2.0": [0, -20, 0]}}}},
        "gui": {"loop": True, "bones": {"root": {"scale": 0.6}}}}})
    _image(aura / "ashfall.webp", (32, 32), fmt="WEBP")

    # A colour of an aura: its descriptor in textures/, the model one folder up.
    butterfly = lunar / "butterfly_aura"
    _json(butterfly / "butterfly_aura.geo.json", _geo([
        {"name": "fly", "pivot": [0, 20, 0], "cubes": [_cube([-1, 30, -1], [2, 1, 2])]}], 32, 32))
    _json(butterfly / "textures" / "pink.gek.json", {
        "type": "pet", "attached_bone": "SHOULDER", "model": "lunar:cosmetics/butterfly_aura/butterfly_aura.geo.json",
        "texture": "lunar:cosmetics/butterfly_aura/textures/pink.png"})
    _image(butterfly / "textures" / "pink.webp", (32, 32), fmt="WEBP", color=(240, 120, 200, 255))

    # A shield held in the right arm: the plate is thin along X.
    shield = lunar / "zzv4 shield"
    _json(shield / "bastis_shield.gek.json", {
        "type": "wristwear", "attached_bone": "NONE", "show_during_emote": True,
        "model": "lunar:cosmetics/models/gek/shields/bastis_shield/bastis_shield.geo.json",
        "texture": "lunar:cosmetics/models/gek/shields/bastis_shield/bastis_shield.png",
        "animation": "lunar:cosmetics/models/gek/shields/bastis_shield/bastis_shield.anim.json",
        "item_transformation": {
            "lunar:cosmetics/models/gek/shields/bastis_shield/bastis_shield_block.display.json": "query.is_blocking",
            "lunar:cosmetics/models/gek/shields/bastis_shield/bastis_shield_held.display.json": ""},
        "compat": "suits_full_coverage", "render_first_person": "SINGLE_ARM", "item_render_type": "SHIELD",
        "state_machine": {"name": "molang", "controllers": [
            {"name": "strap", "states": [{"anim": "strap", "plays_when": "q.is_first_person"}]},
            {"name": "main", "states": [{"anim": "main", "plays_when": "1"}]}]}})
    _json(shield / "bastis_shield.geo.json", _geo([
        {"name": "Body", "pivot": [0, 24, 0]},
        {"name": "bipedRightArm", "parent": "Body", "pivot": [-5, 22, 0]},
        {"name": "armorRightArm", "parent": "bipedRightArm", "pivot": [-5, 22, 0]},
        {"name": "shield", "parent": "armorRightArm", "pivot": [-9, 14, 0],
         "cubes": [_face_cube([-9.5, 4, -6], [1, 18, 12])]},
        {"name": "strap", "parent": "shield", "pivot": [-8, 14, 0], "cubes": [_face_cube([-8.5, 12, -1], [0.5, 4, 2])]}],
        128, 128))
    _json(shield / "bastis_shield.anim.json", {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
        "main": {"loop": True}, "strap": {"loop": True, "bones": {"strap": {"scale": {"vector": [0, 0, 0]}}}}}})
    _image(shield / "bastis_shield.webp", (32, 32), frames=2, fmt="WEBP")
    _json(shield / "bastis_shield.webp.mcmeta", {"animation": {"frametime": 2, "width": 32, "height": 32}})
    _json(shield / "bastis_shield_held.display.json", {"texture_size": [128, 128], "display": {
        "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [-15, 9.25, 0]}}})

    # A cape: a texture in a folder of its own (frames of 22 x 17 blocks).
    cloak = lunar / "zzv4 cloak"
    _image(cloak / "basti_zickzack_v4_flames_cloak.webp", (44, 34), frames=2, fmt="WEBP")
    _json(cloak / "basti_zickzack_v4_flames_cloak.webp.mcmeta", {"animation": {"frametime": 1, "width": 44, "height": 34}})
    return lunar


def _armor_skeleton(extra) -> list[dict]:
    return [
        {"name": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0]},
        {"name": "bipedBody", "pivot": [0, 24, 0]},
        {"name": "armorBody", "parent": "bipedBody", "pivot": [0, 24, 0]},
        {"name": "bipedRightArm", "pivot": [-5, 22, 0]},
        {"name": "armorRightArm", "parent": "bipedRightArm", "pivot": [-5, 22, 0]},
        {"name": "bipedLeftArm", "pivot": [5, 22, 0]},
        {"name": "armorLeftArm", "parent": "bipedLeftArm", "pivot": [5, 22, 0]},
        {"name": "bipedRightLeg", "pivot": [-1.9, 12, 0]},
        {"name": "armorRightLeg", "parent": "bipedRightLeg", "pivot": [-1.9, 12, 0]},
        {"name": "bipedLeftLeg", "pivot": [1.9, 12, 0]},
        {"name": "armorLeftLeg", "parent": "bipedLeftLeg", "pivot": [1.9, 12, 0]},
        *extra]


def _norisk_descriptor(name: str, ctype: str, path: str, scale: float = 1.0, **extra) -> dict:
    return {"id": "0123456789abcdef", "name": name, "creator": "someone", "price": 300, "type": ctype, "path": path,
            "defaultSettings": {"color": None, "scale": scale, "previewScale": 2.9,
                                "offset": {"x": 0, "y": 0, "z": 0}, "previewOffset": {"x": 0, "y": -1, "z": 0},
                                "adjustRotationWhenSwimmingOrFlying": True, "adjustPositionWhenSkeaking": True,
                                "sneakingPositionOffset": {"x": 0, "y": 0, "z": 0}, "renderMode": "ORIGINAL",
                                "hash": None},
            "layers": [], "features": [], "compatibleCosmetics": [], "rarity": "COMMON", "isExclusive": False,
            **extra}


def build_norisk(root: Path) -> Path:
    nrc = root / "noriskclient-cosmetics"
    cosmetics = nrc / "cosmetics"
    # An animated cape: a strip of 22 x 17 frames, frame time in milliseconds.
    cape = cosmetics / "cape" / "gamescom25"
    _json(cape / "gamescom25.norisk.json", _norisk_descriptor("Gamescom 2025", "CAPE", "gamescom25", 0.9575))
    _image(cape / "gamescom25.png", (22, 17), frames=3)
    _json(cape / "gamescom25.png.mcmeta", {"nrc": {"capeType": "nrc"},
                                           "animation": {"frametime": 75, "width": 22, "height": 17}})

    # A bow tie on the body, larger than modelled; its only animation is the shop's.
    bowtie = cosmetics / "accessories" / "bowtie"
    _json(bowtie / "bowtie.norisk.json", _norisk_descriptor("Bow Tie", "ACCESSORIES", "bowtie", 1.1,
                                                            supportedBones=["armorBody"]))
    _json(bowtie / "bowtie.geo.json", _geo(_armor_skeleton([
        {"name": "model", "parent": "armorBody", "pivot": [0, 22, -2.5],
         "cubes": [_cube([-1, 21, -3], [2, 2, 1], (32, 0)), _face_cube([-3, 21, -3], [2, 2, 0.5])]}])))
    _json(bowtie / "bowtie.animation.json", {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
        "nrc.idle": {"loop": True}, "nrc.shop": {"loop": True, "bones": {"model": {"rotation": {"vector": [-47.5, 0, 0]}}}}}})
    _image(bowtie / "bowtie.png", (64, 64))
    red = bowtie / "variants" / "red_bowtie"
    _json(red / "red_bowtie.norisk.json", _norisk_descriptor("Red Bow Tie", "ACCESSORIES", "red_bowtie"))
    _json(red / "red_bowtie.geo.json", json.loads((bowtie / "bowtie.geo.json").read_text()))
    _image(red / "red_bowtie.png", (64, 64), color=(250, 0, 0, 255))

    # A sword in the right hand that swings, with an animated texture (square frames, no size in the .mcmeta).
    katana = cosmetics / "accessories" / "katana"
    _json(katana / "katana.norisk.json", _norisk_descriptor("Katana", "ACCESSORIES", "katana"))
    _json(katana / "katana.geo.json", _geo(_armor_skeleton([
        {"name": "blade", "parent": "armorRightArm", "pivot": [-6, 12, 0],
         "cubes": [_cube([-7, 2, -1], [1, 14, 2], (0, 0))]}])))
    _json(katana / "katana.animation.json", {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
        "nrc.idle": {"loop": True, "animation_length": 2,
                     "bones": {"blade": {"rotation": {"0.0": {"vector": [0, 0, 0]},
                                                      "1.0": {"vector": [0, 10, 0], "easing": "easeInOutSine"},
                                                      "2.0": {"vector": [0, 0, 0]}}}}}}})
    _image(katana / "katana.png", (16, 16), frames=4)
    _json(katana / "katana.png.mcmeta", {"animation": {"frametime": 60}})

    # A hat, and a flag with a texture per country.
    hat = cosmetics / "hat" / "tophat"
    _json(hat / "tophat.norisk.json", _norisk_descriptor("Top Hat", "HAT", "tophat"))
    _json(hat / "tophat.geo.json", _geo(_armor_skeleton([
        {"name": "hat", "parent": "armorHead", "pivot": [0, 32, 0], "cubes": [_cube([-3, 32, -3], [6, 5, 6])]}])))
    _image(hat / "tophat.png", (64, 64), color=(30, 30, 30, 255))
    flag = cosmetics / "accessories" / "wm_flag"
    _json(flag / "wm_flag.norisk.json", _norisk_descriptor("WM Flag", "ACCESSORIES", "wm_flag"))
    _json(flag / "wm_flag.geo.json", _geo(_armor_skeleton([
        {"name": "flag", "parent": "armorBody", "pivot": [0, 24, 3], "cubes": [_cube([-3, 20, 3], [6, 4, 0])]}]),
        128, 128))
    _image(flag / "wm_flag.png", (128, 128))
    _image(flag / "textures" / "ger.png", (128, 128), color=(0, 0, 0, 255))

    # The shop's own files are no cosmetics.
    _json(nrc / "shop" / "ranks" / "30_days_nrc_plus.norisk.json",
          {"id": "x", "price": 800, "rarity": "COMMON", "durationInDays": 30, "iconPath": "textures/x.png"})
    _image(nrc / "shop" / "assets" / "f1.png", (64, 64))
    return nrc


def build(folder: Path) -> Path:
    """Both clients' folders side by side, as in the user's Imports folder."""
    folder.mkdir(parents=True, exist_ok=True)
    build_lunar(folder)
    build_norisk(folder)
    return folder
