import io
import json
import math

import pytest
from PIL import Image

from badlion_import import badlion, convert, pack, textures
from badlion_import.archive import Archive, LayoutError
from badlion_import.cli import main, refuse_output

from . import fixtures

COSMETICS = "assets/blc_import/cosmetics/"


@pytest.fixture()
def input_folder(tmp_path):
    return fixtures.build(tmp_path / "input", for_import=True)


def run(input_folder, out, *extra):
    assert main(["import", str(input_folder), str(out), "--quiet", "--jobs", "1", *extra]) == 0


def cosmetic(out, name):
    matches = sorted((out / COSMETICS).glob(name))
    assert len(matches) == 1, [p.name for p in (out / COSMETICS).iterdir()]
    return json.loads(matches[0].read_text(encoding="utf-8"))


def close(a, b):
    return all(math.isclose(x, y, abs_tol=1e-6) for x, y in zip(a, b))


def same_rotation(a, b):
    """Euler angles describe one rotation several ways; compare the rotations."""
    return all(close(r1, r2) for r1, r2 in zip(convert.rotation_matrix(a), convert.rotation_matrix(b)))


# ---------------------------------------------------------------------- axes

def test_obj_axes_turn_half_around_x():
    # Y-up, front +Z: up becomes -Y (the mod's up), the front becomes -Z (the mod's front), left stays +X.
    assert close(convert.apply(convert.OBJ_TO_MOD, [0, 1, 0]), [0, -1, 0])
    assert close(convert.apply(convert.OBJ_TO_MOD, [0, 0, 1]), [0, 0, -1])
    assert close(convert.apply(convert.OBJ_TO_MOD, [1, 0, 0]), [1, 0, 0])


def test_md5_axes_turn_a_quarter_around_x():
    # Z-up, front -Y.
    assert close(convert.apply(convert.MD5_TO_MOD, [0, 0, 1]), [0, -1, 0])
    assert close(convert.apply(convert.MD5_TO_MOD, [0, -1, 0]), [0, 0, -1])
    assert close(convert.apply(convert.MD5_TO_MOD, [1, 0, 0]), [1, 0, 0])


def test_euler_round_trips_through_the_mods_rotation_order():
    for angles in ([90, 0, 0], [90, 180, 0], [180, 0, 0], [12.5, -40, 77], [0, 90, 30]):
        m = convert.rotation_matrix(angles)
        again = convert.rotation_matrix(convert.euler(m))
        assert all(close(r1, r2) for r1, r2 in zip(m, again)), angles


def test_capes_are_turned_into_the_vanilla_cape_space():
    rule = convert.RULES["CAPE"]
    p = convert.placement(badlion.Cosmetic("CAPE", 1, {}), None, True, rule, convert.TypeSettings())
    m = convert.rotation_matrix(p.rotation)
    # A cloak hanging down (MD5 -Z) hangs down (+Y), and its outside (MD5 +Y, behind the player) faces the
    # cape space's -Z, which the vanilla cape turn puts behind the body.
    assert close(convert.apply(m, [0, 0, -1]), [0, 1, 0])
    assert close(convert.apply(m, [0, 1, 0]), [0, 0, -1])


# ---------------------------------------------------------------------- pieces

def test_clean_obj_makes_indices_absolute_and_texture_coordinates_valid():
    result = convert.clean_obj(fixtures.BACKPACK_OBJ)
    lines = result.text.splitlines()
    assert "vt 1 0" in lines and "vt 0 0" in lines  # 100000 and -0.5 clamped
    assert result.clamped_uvs == 2 and result.added_uvs == 1
    faces = [l for l in lines if l.startswith("f ")]
    assert faces[0] == "f 1/1 2/2 3/3 4/4"
    assert faces[1] == "f 1/5 2/5 3/5"  # a dummy texture coordinate, added once
    assert not any(l.startswith(("mtllib", "usemtl", "o ")) for l in lines)
    assert result.triangles == 3


def test_animations_must_match_the_meshes_joints():
    kept, dropped = convert.matching_animations(fixtures.WING_MD5MESH, {
        "flapping.md5anim": fixtures.wing_md5anim(), "rect.md5anim": fixtures.md5anim()})
    assert list(kept) == ["flapping.md5anim"] and dropped == ["rect.md5anim"]


def test_frame_delay_is_ticks_when_small_and_milliseconds_when_large():
    assert textures.frame_delay_ms(2) == 100
    assert textures.frame_delay_ms(80) == 80
    assert textures.frame_delay_ms(0) is None and textures.frame_delay_ms(True) is None


def test_sequences_become_strips_and_textures_shrink():
    converted = textures.convert(fixtures.avif_sequence([100, 40, 250], size=(64, 32)), max_size=32,
                                 quantize=False)
    assert converted.frames == 3 and converted.frame_size == (32, 16)
    assert converted.frame_times == [100, 40, 250]
    image = Image.open(__import__("io").BytesIO(converted.png))
    assert image.size == (32, 48)
    # The definition's fixed delay wins when the file's own timing doesn't count.
    fixed = textures.convert(fixtures.avif_sequence([100, 40, 250]), max_size=64, quantize=True, fixed_delay=3,
                             file_timing=False)
    assert fixed.frame_times == [150, 150, 150]


def test_strips_never_get_taller_than_the_limit():
    assert textures.target_size(704, 736, 10, 4096) == (704, 736)  # 7360 px tall fits
    w, h = textures.target_size(704, 736, 15, 4096)  # 11040 px wouldn't
    assert h * 15 <= textures.MAX_STRIP_HEIGHT and abs(w / h - 704 / 736) < 0.01
    w, h = textures.target_size(704, 736, 50, 4096)
    assert h * 50 <= textures.MAX_STRIP_HEIGHT and abs(w / h - 704 / 736) < 0.01


def test_model_files_that_differ_only_in_case_stay_apart():
    planner = convert.Planner(badlion.Definitions([], {}, None, None, None), set(), lambda p: b"", convert.Settings())
    upper = planner.location_of(badlion.TEXTURE_ROOT + "wing/models/Bat.obj")
    lower = planner.location_of(badlion.TEXTURE_ROOT + "wing/models/bat.obj")
    assert upper == "blc_import:models/cosmetic/wing/bat.obj" and lower == "blc_import:models/cosmetic/wing/bat_2.obj"
    assert planner.location_of(badlion.TEXTURE_ROOT + "wing/models/Bat.obj") == upper


def test_box_cape_layout_follows_the_texture():
    assert convert.box_cape_layout((64, 32)) == (64, 32, 16)  # vanilla
    # Badlion, 32 px a block: the cape is the top 22 x 17; the six rows below aren't part of it.
    assert convert.box_cape_layout((704, 736)) == (22, 23, 16)
    assert convert.box_cape_layout((704, 735)) == (22, 23, 16)
    assert convert.box_cape_layout((352, 368)) == (22, 23, 16)
    assert convert.box_cape_layout((704, 544)) == (22, 17, 16)  # Lunar and NoRisk: the cape's rows alone
    assert convert.box_cape_layout((44, 20)) == (22, 10, 9)  # too short for 16: as long as it has rows


def test_box_faces_match_the_mods_box_uv():
    # CosmeticBaker.boxUv for the vanilla cape cube at uv [0, 0].
    assert convert.box_faces((10, 16, 1), 1, 1) == {
        "down": [1, 0, 11, 1], "up": [11, 1, 21, 0], "north": [1, 1, 11, 17],
        "south": [12, 1, 22, 17], "west": [0, 1, 1, 17], "east": [11, 1, 12, 17]}
    assert convert.box_faces((10, 22, 1), 32, 32)["north"] == [32, 32, 352, 736]


def test_capes_default_to_v_up():
    assert convert.Settings().of("CAPE").flip_v_md5 is True
    assert convert.Settings().of("HAT").flip_v_md5 is False


def test_colours_and_options():
    assert convert.color_of(-1) is None and convert.color_of(0xFF8000) == "#FF8000"
    assert convert.color_of({"r": 1.0, "g": 0.5, "b": 0.0}) == "#FF8000"
    # Colour is the player's choice on every cosmetic, whatever Badlion allowed.
    assert convert.options_of(badlion.Cosmetic("HAT", 1, {"allowChroma": True})) == {}
    assert convert.options_of(badlion.Cosmetic("WINGS", 1, {"allowChroma": False})) == {}


# ---------------------------------------------------------------------- the whole import

def test_import_writes_a_pack_of_the_owned_wearable_cosmetics(input_folder, tmp_path):
    out = tmp_path / "pack"
    run(input_folder, out, "--max-texture", "64")
    assert json.loads((out / "pack.mcmeta").read_text())["pack"]["pack_format"] == 34
    # Stamped with the conversion's format, for the mod to ask for a new import when the pack is older.
    stamp = json.loads((out / "assets/blc_import/cosmetics_import.json").read_text())
    assert stamp["format"] == pack.IMPORT_FORMAT >= 4
    names = sorted(p.name for p in (out / COSMETICS).iterdir())
    assert names == ["arm_band_1_fire_fist.json", "back_4_hiker_pack.json", "boots_2_fire_boots.json",
                     "cape_11_old_cloak.json", "cape_12_blank_model.json",
                     "cape_1_red_cape.json", "cape_2_blue_cape.json", "cape_7_flowing_cape.json",
                     "hat_1_top_hat.json", "pet_5_dragon.json", "shield_3_round_shield.json",
                     "wings_8_bat_wings.json"]
    # Capes without a model: a box like the vanilla cape. A 64 x 32 texture is the vanilla layout.
    cape = cosmetic(out, "cape_1_*.json")
    assert cape["slot"] == "cape" and cape["texture_size"] == [64, 32]
    cube = cape["parts"][0]["cubes"][0]
    assert cube["from"] == [-5, 0, -1] and cube["to"] == [5, 16, 0]
    assert cube["faces"]["north"] == [1, 1, 11, 17] and cube["faces"]["south"] == [12, 1, 22, 17]
    assert "options" not in cape  # every setting is the player's, colour included
    assert not (out / "assets/blc_import/models/cosmetic/cloak").exists()
    # Badlion's 22 x 23 layout: a vanilla-length cape from the top 22 x 17 blocks, its UVs scaled to the
    # (downscaled) texture; the rows below the cape aren't drawn.
    badlion_cape = cosmetic(out, "cape_2_*.json")
    w, h = badlion_cape["texture_size"]
    assert (w, h) == (61, 64)
    cube = badlion_cape["parts"][0]["cubes"][0]
    assert cube["to"] == [5, 16, 0]
    assert cube["faces"]["north"] == [round(w / 22, 4), round(h / 23, 4), round(11 * w / 22, 4), round(17 * h / 23, 4)]
    assert max(max(uv[1], uv[3]) for uv in cube["faces"].values()) == round(17 * h / 23, 4)
    # A cape naming a model Badlion doesn't define, or a blank one, gets the box too.
    for name in ("cape_11_*.json", "cape_12_*.json"):
        assert cosmetic(out, name)["parts"][0]["cubes"][0]["to"] == [5, 16, 0]

    # An animated cape: a strip with the file's own frame times.
    flowing = cosmetic(out, "cape_7_*.json")
    assert flowing["texture_animation"] == {"frames": 3, "frame_times": [100, 40, 250]}
    strip = Image.open(out / "assets/blc_import/textures/cosmetic/cloak/cape_7.png")
    assert strip.size == (16, 48)

    # A hat: OBJ axes, made in pixels (scale 0.0625 blocks a unit), moved half a block up (translation is in
    # blocks, in the model's Y-up axes) and sized by the cosmetic's sizing, plus its glow layer.
    hat = cosmetic(out, "hat_1_*.json")
    assert hat["slot"] == "head"
    hat_mesh = hat["parts"][0]["meshes"][0]
    assert same_rotation(hat_mesh["rotation"], [180, 0, 0])
    assert hat_mesh["scale"] == 1.2 and hat_mesh["offset"] == [0, -8, 0]
    assert hat["parts"][1]["mode"] == "emissive"
    assert hat["parts"][1]["texture"] == "blc_import:textures/cosmetic/hat/hat_1_glow.png"

    # A backpack, placed from the middle of the back (its translation, half a block down, in blocks; 0.125
    # blocks a unit is 2 pixels), see-through, colourable even though Badlion's applyChroma is off, with its own
    # colour and V flipped (flipUVs).
    back = cosmetic(out, "back_4_*.json")
    part = back["parts"][0]
    assert back["slot"] == "back" and part["mode"] == "translucent" and "tint" not in part
    assert back["default_color"] == "#FF8000"
    assert part["meshes"][0]["offset"] == [0, 14, 2] and part["meshes"][0]["scale"] == 2.0
    assert part["meshes"][0]["flip_v"] is True
    assert back["texture_size"] == [64, 32]
    obj = (out / "assets/blc_import/models/cosmetic/back/pack.obj").read_text()
    assert "100000" not in obj

    # A shield: in place of the held shield, at its arm scale (0.125 blocks a unit), facing as Badlion has it (its
    # half turn undoes its inverted model, in both views), its middle on the vanilla shield's plate.
    shield = cosmetic(out, "shield_3_*.json")
    assert shield["slot"] == "shield" and "first_person_turned" not in shield
    shield_mesh = shield["parts"][0]["meshes"][0]
    assert same_rotation(shield_mesh["rotation"], [180, 0, 0])
    assert shield_mesh["scale"] == 2.0
    m = convert.rotation_matrix(shield_mesh["rotation"])
    middle = convert.apply(m, [0, -10 * 2.0, -4 * 2.0])  # the hat box's middle, scaled
    assert close([middle[i] + shield_mesh["offset"][i] for i in range(3)], [0, 0, -1.5])

    # Boots on the feet slot (both legs, standing on the sole); armbands (from the armband folder) on an arm.
    assert cosmetic(out, "boots_2_*.json")["slot"] == "feet"
    assert cosmetic(out, "arm_band_1_*.json")["slot"] == "arm"

    # A pet on the shoulder, its animations as styles, standing on the shoulder by default.
    pet = cosmetic(out, "pet_5_*.json")
    assert pet["slot"] == "pet" and pet["default_style"] == "shoulder" and "pet_position" not in pet
    assert sorted(pet["parts"][0]["meshes"][0]["bone_animations"]) == ["air", "shoulder"]

    # Wings: one model, mirrored for the left, flapping through its own animation.
    wings = cosmetic(out, "wings_8_*.json")
    right, left = wings["parts"]
    assert (right["wing"], left["wing"]) == ("right", "left")
    assert left["meshes"][0]["mirror"] is True and "mirror" not in right["meshes"][0]
    assert right["meshes"][0]["bone_animation"].endswith("bat_wings/flapping.md5anim")
    assert right["wing_pivot"] == [0, 4, 2]
    assert wings["wings"]["base"] == 12.5 and wings["wings"]["idle"] == {"amplitude": 0}
    assert wings["wings"]["sneak_offset"] == [0.0, 2.0, 1.0] and wings["wings"]["armor_offset"] == [0, 0, 1.5]
    assert not (out / "assets/blc_import/models/cosmetic/wing/bat_wings/rect.md5anim").exists()

    # Each says what it counts as when one of a kind is worn at a time.
    assert [cosmetic(out, n)["kind"] for n in ("hat_1_*.json", "back_4_*.json", "wings_8_*.json", "pet_5_*.json",
                                                "cape_1_*.json", "shield_3_*.json", "boots_2_*.json")] == \
        ["head", "back", "wings", "pet", "cape", "shield", "feet"]

    report = (out / pack.REPORT).read_text(encoding="utf-8")
    assert "texture is not in your jars (not owned)" in report  # CAPE 9
    assert "model 'angel' is not in your jars" in report
    assert "player pets aren't supported" in report
    assert "1 animation(s) skipped" in report
    assert "model 'cloak' isn't in models.json; uses the built-in cape" in report
    assert "Capes without a model are a box like the vanilla cape" in report
    assert "| WINGS | 8 | Bat Wings | `blc_import:wings_8_bat_wings` |" in report


def test_models_json_scale_is_blocks_per_model_unit():
    def model(scale):
        return badlion.Model("HAT", "m", {"scale": scale})
    assert convert.model_scale(model(0.0625)) == 1.0  # made in pixels
    assert close([convert.model_scale(model(0.004))], [0.064])  # 2019 glasses, 126 units: 8 pixels wide
    assert convert.model_scale(model(0)) == 1.0 and convert.model_scale(None) == 1.0
    assert close([convert.model_scale(model(-0.012))], [(0.0625 - 0.012) * 16])  # backpacks: off the default
    assert convert.model_scale(model(-2.0)) == 1.0


def test_translation_is_blocks_in_the_models_own_axes():
    # Badlion's glasses: scale 0.004, translation (0, 0.1, 0.3) blocks: 1.6 px up and 4.8 px to the front.
    glasses = badlion.Model("EYES", "glasses", {"scale": 0.004, "translation": {"x": 0, "y": 0.1, "z": 0.3}})
    c = badlion.Cosmetic("EYES", 1, {"name": "Glasses", "modelName": "glasses"})
    place = convert.placement(c, glasses, False, convert.RULES["EYES"], convert.TypeSettings())
    assert close([place.scale], [0.064])
    assert close(place.offset, [0, -1.6, -4.8])
    # A rotation with no angle (w) doesn't turn it.
    crown = badlion.Model("HAT", "crown", {"scale": 0.034, "rotation": {"w": 0.0, "x": 180.0, "y": 0.0, "z": 1.0}})
    place = convert.placement(badlion.Cosmetic("HAT", 2, {"name": "Crown"}), crown, False, convert.RULES["HAT"],
                              convert.TypeSettings())
    assert same_rotation(place.rotation, [180, 0, 0])


def test_small_sizing_scales_are_blocks_per_unit():
    wings = badlion.Model("WINGS", "crystal_wings", {"scale": 0.0})
    small = badlion.Cosmetic("WINGS", 10, {"name": "Crystal", "sizingData": {"scale": 0.08}})
    big = badlion.Cosmetic("WINGS", 11, {"name": "Cupid", "sizingData": {"scale": 1.0, "offsetZ": -1.0}})
    rule, ts = convert.RULES["WINGS"], convert.TypeSettings()
    assert close([convert.placement(small, wings, False, rule, ts).scale], [1.28])
    # A factor scales wings' own default size (0.1 blocks a unit); the offset is in the player's axes: -Z is
    # forward, towards the back.
    place = convert.placement(big, wings, False, rule, ts)
    assert close([place.scale], [1.6]) and close([place.offset[2]], [rule.origin[2] - 1.0])
    # Offsets lift up the back with -Y; other types keep a pixel a unit by default.
    lifted = badlion.Cosmetic("WINGS", 12, {"name": "Butterfly", "sizingData": {"scale": 1.0, "offsetY": -3.2}})
    assert close([convert.placement(lifted, wings, False, rule, ts).offset[1]], [rule.origin[1] - 3.2])
    hat = badlion.Model("HAT", "cap", {"scale": 0.0})
    assert convert.placement(badlion.Cosmetic("HAT", 3, {"name": "Cap"}), hat, False, convert.RULES["HAT"],
                             ts).scale == 1.0


def test_wings_players_found_backwards_are_turned_whatever_their_shape(input_folder, tmp_path, monkeypatch):
    assert ("WINGS", 154) in convert.WINGS_MADE_BACKWARDS  # BastiGHG's 2 Million Golden Ruby Wings
    # The bat wings stand in for them: turned too, though their model isn't one that bends forward.
    monkeypatch.setattr(convert, "WINGS_MADE_BACKWARDS", {("WINGS", 8)})
    out = tmp_path / "pack"
    run(input_folder, out, "--name", "bat_w")
    right, left = cosmetic(out, "wings_8_*.json")["parts"]
    assert right["meshes"][0]["mirror"] is True and "mirror" not in left["meshes"][0]


def test_wings_made_facing_forward_are_turned_around():
    wings = badlion.Model("WINGS", "zickzack", {"scale": 0.0})
    rule, ts = convert.RULES["WINGS"], convert.TypeSettings()
    place = convert.placement(badlion.Cosmetic("WINGS", 1, {"name": "ZickZack"}), wings, False, rule, ts)
    # OBJ +Z is the player's front: wings bending forward into the body (ZickZack v5, Norman's fire wings) turn.
    assert convert.wing_faces_forward(place, ((-12.1, -3.54, -0.65), (-2.2, 8.75, 4.27)))
    assert convert.wing_faces_forward(place, ((-14.2, -5.4, -2.06), (-0.197, 4.49, 3.45)))
    # Flat wings, and ones bending back, stay as they are.
    assert not convert.wing_faces_forward(place, ((-9.75, -6.45, -0.19), (-1.1, 7.75, 0.19)))
    assert not convert.wing_faces_forward(place, ((-10.2, -7.2, -4.96), (0.942, 8.4, 0.652)))
    assert not convert.wing_faces_forward(place, ((-12.2, -14.5, -1.39), (0.343, 4.51, 0.171)))
    # Turned: the right wing (mirrored on X, then turned) is the original with Z mirrored after the placement.
    r = convert.rotation_matrix(convert.turned_front_to_back(place))
    mirrored = convert.multiply(r, [[-1.0, 0, 0], [0, 1.0, 0], [0, 0, 1.0]])
    want = convert.multiply([[1.0, 0, 0], [0, 1.0, 0], [0, 0, -1.0]], place.matrix)
    assert all(close(mirrored[i], want[i]) for i in range(3))


def test_shields_are_centred_and_mirrored_as_badlion_scales_them():
    goat = badlion.Model("SHIELD", "goat_shield", {"scale": 0.0, "shieldModelData": {"armScale": 0.125, "thirdPerson": {
        "rotate": {"w": 180.0, "x": 0.0, "y": 1.0, "z": 0.0}, "scale": {"x": 1.0, "y": 1.0, "z": -1.0}}}})
    bounds = ((-4, -4, 0), (4, 4, 3.4))
    place = convert.shield_placement(goat, bounds, False, convert.RULES["SHIELD"], convert.TypeSettings())
    assert place.mirror and place.scale == 2.0
    # Its half turn and its front-to-back mirror together are a mirror on the side: it faces forward, not turned.
    assert same_rotation(place.rotation, [180, 0, 0])  # the OBJ axes alone
    m = convert.rotation_matrix(place.rotation)
    middle = convert.apply(m, [-0.0 * 2.0, 0.0, 1.7 * 2.0])  # mirrored X first, then turned
    assert close([middle[i] + place.offset[i] for i in range(3)], [0, 0, -1.5])


def test_shields_face_each_view_as_badlion_turns_them():
    half = {"w": 180.0, "x": 0.0, "y": 1.0, "z": 0.0}
    rule, ts, bounds = convert.RULES["SHIELD"], convert.TypeSettings(), ((-4, -4, -0.5), (4, 4, 0.5))

    def shield(**data):
        return badlion.Model("SHIELD", "s", {"scale": 0.0, "shieldModelData": {"armScale": 0.125, **data}})

    plain = shield(firstPerson={}, thirdPerson={})
    # Inverted with a half turn in both views (the round shield, ZickZack): faces as a plain one, both views.
    round_shield = shield(inverted=True, firstPerson={"rotate": half}, thirdPerson={"rotate": half})
    facing = convert.shield_placement(plain, bounds, False, rule, ts).rotation
    assert convert.shield_placement(round_shield, bounds, False, rule, ts).rotation == facing
    assert not convert.first_person_turned(round_shield) and not convert.first_person_turned(plain)
    # A half turn in one view only: turned around in first person against third.
    assert convert.first_person_turned(shield(firstPerson={"rotate": half}, thirdPerson={}))  # Badlion's own
    dragon = shield(inverted=True, firstPerson={}, thirdPerson={"rotate": half})
    assert convert.first_person_turned(dragon)
    assert convert.shield_placement(dragon, bounds, False, rule, ts).rotation == facing
    # Inverted with no turn at all: turned around in both views.
    knight = shield(inverted=True, firstPerson={"rotate": {"w": 0.0}}, thirdPerson={"rotate": {"w": 0.0}})
    assert not convert.first_person_turned(knight)
    assert not same_rotation(convert.shield_placement(knight, bounds, False, rule, ts).rotation, facing)


def test_pets_sit_on_the_head_float_or_stay_on_the_shoulder():
    def pet(**data):
        return badlion.Cosmetic("PET", 1, {"name": "P", "petData": data})
    assert convert.pet_position(pet(onHead=True), {"shoulder": "s"}) == "head"
    assert convert.pet_position(pet(onHead=False), {"air": "a"}) == "floating"
    assert convert.pet_position(pet(onHead=False), {"air": "a", "shoulder": "s"}) is None
    assert convert.pet_position(pet(), {}) is None


def test_sizing_comes_from_the_cosmetic_else_its_model():
    model = badlion.Model("BACK", "pack", {"sizingData": {"scale": 2}})
    own = badlion.Cosmetic("BACK", 1, {"name": "B", "sizingData": {"scale": 3}})
    plain = badlion.Cosmetic("BACK", 2, {"name": "C"})
    assert convert.sizing_of(own, model) == {"scale": 3}
    assert convert.sizing_of(plain, model) == {"scale": 2}
    assert convert.sizing_of(plain, None) == {}


TOP_HALF = [y < 32 for y in range(64) for _ in range(64)]
BOTTOM_HALF = [not t for t in TOP_HALF]
# A quad whose UVs cover half the texture's height: v 0 to 0.5 in the file.
HALF_OBJ = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 0.5\nvt 0 0.5\nf 1/1 2/2 3/3 4/4\n"


def test_texture_fit_turns_v_around_when_the_other_way_lands_on_paint():
    planner = convert.Planner(None, set(), lambda path: HALF_OBJ.encode(), convert.Settings())
    # The mod reads OBJ V as pointing up, so v 0 to 0.5 is the image's bottom half.
    assert planner.fit_flip("m.obj", False, False, BOTTOM_HALF) is False
    assert planner.fit_flip("m.obj", False, False, TOP_HALF) is True
    assert planner.fit_flip("m.obj", False, True, TOP_HALF) is True  # already read the right way
    assert planner.fit_flip("m.obj", False, True, BOTTOM_HALF) is False
    # A texture painted all over can't tell; no texture information keeps the setting.
    assert planner.fit_flip("m.obj", False, False, [True] * 4096) is False
    assert planner.fit_flip("m.obj", False, True, None) is True
    planner.settings.fit_texture_direction = False
    assert planner.fit_flip("m.obj", False, False, TOP_HALF) is False


def test_texture_fit_reads_md5_v_as_pointing_down():
    half = fixtures.CAPE_MD5MESH.replace("vert 2 ( 0.5 1 )", "vert 2 ( 0.5 0.5 )").replace("vert 3 ( 0 1 )",
                                                                                           "vert 3 ( 0 0.5 )")
    planner = convert.Planner(None, set(), lambda path: half.encode(), convert.Settings())
    assert planner.fit_flip("m.md5mesh", True, False, TOP_HALF) is False
    assert planner.fit_flip("m.md5mesh", True, False, BOTTOM_HALF) is True


def test_opaque_masks_round_trip_and_come_from_strips():
    mask = [i % 3 == 0 for i in range(64 * 64)]
    assert textures.unpack_mask(textures.pack_mask(mask)) == mask
    assert textures.unpack_mask(None) is None
    strip = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    strip.paste((255, 0, 0, 255), (0, 0, 16, 4))  # frame 0: its top quarter
    strip.paste((255, 0, 0, 255), (0, 28, 16, 32))  # frame 1: its bottom quarter
    out = io.BytesIO()
    strip.save(out, format="PNG")
    found = textures.mask_from_png(out.getvalue(), 16)
    rows = [any(found[y * 64:(y + 1) * 64]) for y in range(64)]
    assert rows[0] and rows[63] and not rows[32]


def test_reimport_reuses_textures_and_removes_what_is_no_longer_selected(input_folder, tmp_path):
    out = tmp_path / "pack"
    run(input_folder, out)
    first = json.loads((out / pack.MARKER).read_text())["textures"]
    run(input_folder, out, "--types", "HAT")
    assert sorted(p.name for p in (out / COSMETICS).iterdir()) == ["hat_1_top_hat.json"]
    assert not (out / "assets/blc_import/textures/cosmetic/cloak").exists()
    again = json.loads((out / pack.MARKER).read_text())["textures"]
    hat = "blc_import:textures/cosmetic/hat/hat_1.png"
    assert again[hat] == first[hat]
    with Archive(input_folder) as archive:
        definitions = badlion.load(archive)
        planner = convert.Planner(definitions, archive.present_paths(), archive.read, convert.Settings())
        plan = planner.plan([c for c in definitions.cosmetics if c.type == "HAT"])
        written = pack.write(plan, planner, archive, out, jobs=1)
    assert written.textures_reused == 2 and written.textures_converted == 0


def test_name_picks_cosmetics_by_name_or_model(input_folder, tmp_path):
    out = tmp_path / "pack"
    run(input_folder, out, "--name", "HIKER,bat_w")  # a name and a model name, any case
    assert sorted(p.name for p in (out / COSMETICS).iterdir()) == ["back_4_hiker_pack.json",
                                                                   "wings_8_bat_wings.json"]


def test_calibration_writes_variants_to_compare(input_folder, tmp_path):
    out = tmp_path / "calibration"
    run(input_folder, out, "--calibrate")
    names = sorted(p.stem for p in (out / COSMETICS).iterdir())
    assert "calib_cape_box" in names
    assert "calib_cape_cloak_v2_v_down" in names and "calib_cape_cloak_v2_v_up" in names
    assert cosmetic(out, "calib_cape_box.json")["name"] == "Calibrate cape: box (vanilla shape)"
    assert "calib_hat_obj_as_is" in names and "calib_hat_obj_flipped" in names
    assert "calib_wings_md5_as_is" in names
    up = cosmetic(out, "calib_cape_cloak_v2_v_up.json")
    assert up["name"] == "Calibrate cape: cloak_v2, V up" and up["parts"][0]["meshes"][0]["flip_v"] is True


def test_settings_change_the_conversion(input_folder, tmp_path):
    settings = tmp_path / "settings.json"
    settings.write_text(json.dumps({"cape_model": "cloak/models/cloak_v2/cloak_v2.md5mesh", "cape_style": "rect",
                                    "types": {"HAT": {"offset": [0, -0.5, 0], "flip_v_obj": True}}}))
    out = tmp_path / "pack"
    run(input_folder, out, "--settings", str(settings), "--types", "HAT,CAPE")
    hat_mesh = cosmetic(out, "hat_1_*.json")["parts"][0]["meshes"][0]
    assert hat_mesh["offset"] == [0, -8.5, 0] and hat_mesh["flip_v"] is True
    # A cloak instead of the box: the vanilla cape's space, its animations as styles.
    cape = cosmetic(out, "cape_1_*.json")
    mesh = cape["parts"][0]["meshes"][0]
    assert mesh["model"] == "blc_import:models/cosmetic/cloak/cloak_v2/cloak_v2.md5mesh"
    assert same_rotation(mesh["rotation"], [90, 180, 0])
    assert sorted(mesh["bone_animations"]) == ["curved", "rect"] and cape["default_style"] == "rect"
    bad = tmp_path / "bad.json"
    bad.write_text(json.dumps({"types": {"HAT": {"colour": 1}}}))
    with pytest.raises(ValueError, match="not a setting"):
        convert.load_settings(bad)


def test_refuses_folders_it_did_not_write(input_folder, tmp_path):
    out = tmp_path / "somebody_elses"
    out.mkdir()
    (out / "keep.txt").write_text("mine")
    assert main(["import", str(input_folder), str(out), "--quiet"]) == 2
    assert (out / "keep.txt").read_text() == "mine"
    with pytest.raises(LayoutError, match="input folder"):
        refuse_output(input_folder / "out", input_folder)


def test_show_prints_the_numbers_of_matching_cosmetics(tmp_path, capsys):
    source = fixtures.build(tmp_path / "input", for_import=True)
    assert main(["show", str(source), "--name", "bat"]) == 0
    out = capsys.readouterr().out
    assert "=== WINGS 8  Bat Wings" in out
    assert '"wingData"' in out
    assert "bounds in the file" in out
    assert "converted: slot back" in out
    assert main(["show", str(source), "--name", "no such cosmetic"]) == 1
