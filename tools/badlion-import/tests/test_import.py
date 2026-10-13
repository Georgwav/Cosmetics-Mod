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
    assert convert.box_cape_layout((704, 736)) == (22, 23, 22)  # Badlion, 32 px a block
    assert convert.box_cape_layout((704, 735)) == (22, 23, 22)
    assert convert.box_cape_layout((352, 368)) == (22, 23, 22)


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
    assert convert.options_of(badlion.Cosmetic("HAT", 1, {"allowChroma": True})) == {"saturation": False}
    assert convert.options_of(badlion.Cosmetic("HAT", 1, {"allowSaturation": True})) == {
        "tint": False, "rainbow": False}


# ---------------------------------------------------------------------- the whole import

def test_import_writes_a_pack_of_the_owned_wearable_cosmetics(input_folder, tmp_path):
    out = tmp_path / "pack"
    run(input_folder, out, "--max-texture", "64")
    assert json.loads((out / "pack.mcmeta").read_text())["pack"]["pack_format"] == 34
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
    assert cape["options"] == {"saturation": False}  # chroma allowed, saturation not
    assert not (out / "assets/blc_import/models/cosmetic/cloak").exists()
    # Badlion's 22 x 23 layout is a 22 long cape, its UVs scaled to the (downscaled) texture.
    long = cosmetic(out, "cape_2_*.json")
    w, h = long["texture_size"]
    assert (w, h) == (61, 64)
    cube = long["parts"][0]["cubes"][0]
    assert cube["to"] == [5, 22, 0]
    assert cube["faces"]["north"] == [round(w / 22, 4), round(h / 23, 4), round(11 * w / 22, 4), h]
    # A cape naming a model Badlion doesn't define, or a blank one, gets the box too.
    for name in ("cape_11_*.json", "cape_12_*.json"):
        assert cosmetic(out, name)["parts"][0]["cubes"][0]["to"] == [5, 16, 0]

    # An animated cape: a strip with the file's own frame times.
    flowing = cosmetic(out, "cape_7_*.json")
    assert flowing["texture_animation"] == {"frames": 3, "frame_times": [100, 40, 250]}
    strip = Image.open(out / "assets/blc_import/textures/cosmetic/cloak/cape_7.png")
    assert strip.size == (16, 48)

    # A hat: OBJ axes, the model's translation and the cosmetic's sizing, plus its glow layer.
    hat = cosmetic(out, "hat_1_*.json")
    assert hat["slot"] == "head"
    hat_mesh = hat["parts"][0]["meshes"][0]
    assert same_rotation(hat_mesh["rotation"], [180, 0, 0])
    assert hat_mesh["scale"] == 1.2 and hat_mesh["offset"] == [0, -1, 0]
    assert hat["parts"][1]["mode"] == "emissive"
    assert hat["parts"][1]["texture"] == "blc_import:textures/cosmetic/hat/hat_1_glow.png"

    # A backpack from the feet up, see-through, untinted, with its own colour and V flipped (flipUVs).
    back = cosmetic(out, "back_4_*.json")
    part = back["parts"][0]
    assert back["slot"] == "back" and part["mode"] == "translucent" and part["tint"] is False
    assert back["default_color"] == "#FF8000"
    assert part["meshes"][0]["offset"] == [0, 25, 0] and part["meshes"][0]["scale"] == 2.0
    assert part["meshes"][0]["flip_v"] is True
    assert back["texture_size"] == [64, 32]
    obj = (out / "assets/blc_import/models/cosmetic/back/pack.obj").read_text()
    assert "100000" not in obj

    # A shield: in place of the held shield, OBJ axes, centred on the vanilla shield's plate, the model's scale.
    shield = cosmetic(out, "shield_3_*.json")
    assert shield["slot"] == "shield"
    shield_mesh = shield["parts"][0]["meshes"][0]
    assert same_rotation(shield_mesh["rotation"], [180, 0, 0])
    assert shield_mesh["scale"] == 1.5 and shield_mesh["offset"] == [0, 0, -1.5]

    # Boots on the feet slot (both legs, standing on the sole); armbands (from the armband folder) on an arm.
    assert cosmetic(out, "boots_2_*.json")["slot"] == "feet"
    assert cosmetic(out, "arm_band_1_*.json")["slot"] == "arm"

    # A pet on the shoulder, its animations as styles, standing on the shoulder by default.
    pet = cosmetic(out, "pet_5_*.json")
    assert pet["slot"] == "pet" and pet["default_style"] == "shoulder"
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

    report = (out / pack.REPORT).read_text(encoding="utf-8")
    assert "texture is not in your jars (not owned)" in report  # CAPE 9
    assert "model 'angel' is not in your jars" in report
    assert "player pets aren't supported" in report
    assert "1 animation(s) skipped" in report
    assert "model 'cloak' isn't in models.json; uses the built-in cape" in report
    assert "Capes without a model are a box like the vanilla cape" in report
    assert "| WINGS | 8 | Bat Wings | `blc_import:wings_8_bat_wings` |" in report


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
    assert hat_mesh["offset"] == [0, -1.5, 0] and hat_mesh["flip_v"] is True
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
