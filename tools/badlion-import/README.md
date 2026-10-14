# Badlion importer

Turns the Badlion cosmetics you own into a resource pack for this mod, **for your personal use**. It reads
your own trimmed copies of Badlion's data jars and writes the pack into your private `cosmetics-personal`
repository. Converted cosmetics never go into this repository, its builds or its CI.

- Python 3.10 or newer and Pillow 11.3 or newer. Pillow's wheels include AVIF decoding (stills and image
  sequences) on Windows, macOS and Linux, so nothing needs compiling.
- **Read-only on its input.** It reads `assets.json` and the zips' directories, then opens only the entries
  it needs. It never extracts archives and never writes into the input folder. It refuses to read from a
  `.minecraft` folder (the live install) and refuses to write converted content into this repository.

## Setup (Windows, PowerShell)

```powershell
cd <your clone of Cosmetics-Mod>\tools\badlion-import
py -m venv .venv
.\.venv\Scripts\Activate.ps1
py -m pip install -e .
```

On macOS or Linux, use `python3 -m venv .venv`, `source .venv/bin/activate` and `pip install -e .`.

`-e` (editable) runs the tool from this folder, so a `git pull` updates it with no reinstall, and `inspect` writes
`LAYOUT.md` here. Without `-e`, `LAYOUT.md` goes to the folder you run it from.

## Input

One folder holding your trimmed copies of `data0.jar` … `data9.jar` and `assets.json`, for example
`C:\badlion-input\`. Keep it **outside** this repository.

## Commands

### `inspect`: check the layout first

```powershell
badlion-import inspect C:\badlion-input
```

It checks every rule in the expected layout against your files (archives, definitions, texture and model
paths, AVIF sequences and their timing, shared and missing models, which built-in cloak model capes
without a `modelName` use) and:
- writes `tools/badlion-import/LAYOUT.md`: structure only (key names, JSON types, path patterns, counts,
  model file names, model sizes) and the numbers that place each model and cosmetic (`models.json` scale,
  translation, rotation and sizing; each cosmetic's sizing, wing and pet data, by type and id). No asset
  contents and no cosmetic names, so it can be shared.
- prints a summary: cosmetics present per type, which have models, which are animated, missing shared
  models and the cosmetics that need them, and anything that doesn't fit. Add `--verbose` to list every
  present cosmetic, `--summary <file>` to save it (not inside this repository), `--no-hash` to skip the
  SHA-1 check of every file (faster).

### `survey`: any other client's files

```powershell
badlion-import survey C:\Users\Pc\Downloads\Imports --out C:\badlion-packs\SURVEY.md
```

Looks through a folder of another client's cosmetic files (Lunar, NoRisk, ...), and into the archives in it, and
writes `SURVEY.md` for planning an importer: path patterns, file kinds and sizes, the keys and value types of the
JSON files (with short id-like values such as types), image sizes, model counts and bounds, and the first bytes of
files it can't read. Structure only: no pixels, no vertices, no names or other text, so it can be shared.

### `list`

```powershell
badlion-import list C:\badlion-input
badlion-import list C:\badlion-input --type CAPE --present
```

Type, id, name, and whether its texture and model are in your jars (`present`) or not (`missing`).

### `show`: the numbers behind a cosmetic

When a converted cosmetic looks wrong (too small, turned the wrong way, in the wrong place), this prints what
Badlion says about it and what the conversion made of it, to paste into an issue or a chat:

```powershell
badlion-import show C:\badlion-input --name "zickzack v5"
badlion-import show C:\badlion-input --types SHIELD --limit 6
badlion-import show C:\badlion-input --only WINGS:53
```

For each cosmetic: its definition (sizing, wing, pet and shield data), its `models.json` entry, the model file's
size and texture area, its animations, and the slot, scale, rotation and offset the conversion uses, with the
model's box around its anchor in game. Numbers only: no textures or models are printed.

### `import`

Writes a resource pack of the cosmetics in your jars. Do it in two steps.

**1. Calibrate.** A few of your cosmetics in every variant that can only be judged by eye: one cape without a
model as the box (the default) and on each built-in cloak model (in both directions of its texture), and both
texture directions on one cosmetic of every type:

```powershell
badlion-import import C:\badlion-input C:\badlion-packs\calibration --calibrate
```

Use the pack (below), open the Wardrobe and look at the cosmetics named "Calibrate …". Note which variant
looks like it does in Badlion for each type, and put that in a settings file (below).

**2. Import everything:**

```powershell
badlion-import import C:\badlion-input C:\badlion-packs\badlion --settings C:\badlion-packs\settings.json
```

| Option | Meaning |
|---|---|
| `--calibrate` | Only the calibration variants. |
| `--types CAPE,WINGS` | Only these types. |
| `--only CAPE:12,HAT:3` | Only these cosmetics (type and Badlion id, as `list` prints them). |
| `--name zickzack,basti` | Only cosmetics whose name or model name contains one of these words (any case), for example every cosmetic of one creator. Works with `--calibrate` too. |
| `--limit 5` | At most this many per type, for a quick test. |
| `--settings file.json` | Conversion settings (below). |
| `--max-texture 256` | Longest side of a texture frame, in pixels. Badlion's capes are 704×736 with up to 15 frames, so the default keeps a large collection to a sensible size. |
| `--no-quantize` | Keep full colour. By default textures are stored with 256 colours, which makes files about 3× smaller. |
| `--jobs 8` | Textures converted in parallel (default: one per CPU core). |

Running it again into the same folder only re-encodes textures that changed, and removes cosmetics from the
earlier run that this one doesn't write. It refuses a folder that isn't empty and wasn't written by it.

**Using the pack.** Put the output folder (or a link to it) in `.minecraft\resourcepacks` and enable it in
Options → Resource Packs. Or bundle it into a personal build with `-Pcosmetics.bundleDir`. Cosmetics appear in
the Wardrobe under their Badlion names, with ids like `blc_import:cape_12_red_cape`.

`import-report.md` in the pack lists what was converted, what was skipped and why, cosmetics over the mod's
triangle limit (raise `maxMeshTriangles` in `config/cosmeticsmod.json` for those), and the placement values the
conversion used.

**What converts:**
- capes: those without a model of their own as a box like the vanilla cape, swinging with the vanilla cape
  physics, 10 x 16 x 1. Badlion's cape textures are 22 x 23 blocks (704 x 736 or 352 x 368): the vanilla box
  layout of that cape in the top 22 x 17, and six rows below it that aren't part of the cape. Lunar and NoRisk
  cape textures are the 22 x 17 alone. Capes with a model use it, with its `rect`, `curved` and `dynamic`
  animations as the cape's styles;
- wings: one model mirrored for the other side, flapping with your movement (skeletal wings play their own
  flap). Without a size of their own they're 0.1 blocks a model unit (Badlion only sizes its biggest wing models,
  down from that). A wing model whose middle sits forward of the back (bending into the body: made facing the other
  way, as six of Badlion's curved wings are) is turned around front to back (listed in the report's notes);
- hats, masks, glasses, ears, mouth and nose cosmetics, bandanas, necklaces, backpacks, tails and belts;
- shields: shown instead of the vanilla shield while you hold one, at the size Badlion holds them
  (`shieldModelData.armScale`), facing as Badlion shows them (each view's `rotate` and `scale` after the half turn
  of an `inverted` model; a shield Badlion turns around in one view only gets `first_person_turned`), and
  centred on the vanilla shield's plate (a shield that still faces the wrong way has **Turn around** on its
  Wardrobe page);
- boots (one model on both feet) and armbands (on the left arm);
- pets: on your left shoulder, on your head for pets Badlion puts there, and floating beside you for pets
  that only have an `air` animation. Their `shoulder` and `air` animations are styles; the mod plays `air`
  while a pet floats and `shoulder` elsewhere, and you can move any pet in its Wardrobe page;
- animated textures (as frame strips with their timing), glow layers, see-through cosmetics and default colours;
  every cosmetic can be recoloured, whatever Badlion allowed;
- what each counts as when you wear one of a kind at a time (Badlion's type: a hat, ears, a mask, ...), so
  putting on new wings takes off the old ones but not your backpack;
- each model's texture direction: when the model's texture coordinates, read the other way up, land clearly
  more on painted pixels than on see-through ones, the import turns them around (listed in the report's
  notes). This is what makes a model show as a few scattered pixels when it's wrong.
- sizing (`sizingData`): the cosmetic's own, else its model's in `models.json`. Its offsets are pixels in the
  player's axes (+Y down, -Z forward).
- size and position as Badlion has them: `models.json`'s `scale` is blocks per model unit (0.0625 for a model
  made in pixels, 0.004 for one made 16 times finer; 0 is pixels, or 0.1 for wings), its `translation` is in blocks along the
  model's own axes, and its `rotation` turns by `w` degrees around the axis `x, y, z`.

Skipped and listed in the report: player pets, swords, bows, sprays, auras and nametag decorations, cosmetics
without a model of their own (other than capes), and sub-models (only a cosmetic's main model converts).

### `import` for Lunar and NoRisk cosmetics

Point `import` at a folder of Lunar and NoRisk cosmetic files (the ones you own) instead of Badlion jars, and it
converts those. It tells them apart by their files, so one folder can hold both:

```powershell
badlion-import import C:\Users\Pc\Downloads\Imports C:\badlion-packs\lunar-norisk
```

Write it to its own pack folder (not the Badlion one) and turn both packs on. `--name basti,katana` converts
only cosmetics whose name or path contains one of the words; `--max-texture` and `--no-quantize` work as for
Badlion. The report (`import-report.md`) lists every cosmetic with its slot and kind, and anything skipped.

- **Lunar**: every `<name>.gek.json` is a cosmetic. Its model (`.geo.json`), animations (`.anim.json`) and
  texture (`.webp`) are found by the names it gives, in its folder or the one above (`butterfly_aura/textures/
  pink.gek.json` uses the aura's model one folder up). Its `state_machine` becomes the cosmetic's
  `model_states`, so wings glide while you glide and an aura shows its menu pose in the Wardrobe pictures.
  Wings go on the back, auras (by their names) around the player, shields in your hand; anything else is worn
  where it was modelled. A folder with a texture and no `.gek.json` is a cape. Animated textures (`.mcmeta`
  with frame sizes, frame times in ticks) keep animating.
- **NoRisk**: every `<name>.norisk.json` under `cosmetics/` is a cosmetic (the shop's files are left out):
  capes from their `.png` strips (frame times in milliseconds); hats, accessories and the rest from their
  `.geo.json`, which is built on the player's skeleton, so a sword in the right arm swings with your arm
  (`"skeleton": "player"`). The `nrc.idle` animation plays (the shop's preview doesn't); `defaultSettings`
  `scale` is kept. `variants/` are cosmetics of their own, and so is every texture in a `textures/` folder
  (one per country flag).

Their names come from the folders (Lunar) or the descriptors (NoRisk). They show up in the Wardrobe next to your
other cosmetics.

### `emotes`: emotes from any client's files

Emotes aren't kept with the cosmetics, and each client keeps them somewhere else. `emotes` looks through a whole
folder, inside every `.jar` and `.zip` too, for the emote formats the mod plays, and writes them as a pack for the
emote wheel:

```powershell
badlion-import emotes C:\Users\Pc\.lunarclient C:\badlion-packs\emotes-lunar
badlion-import emotes C:\Users\Pc\AppData\Roaming\.minecraft C:\badlion-packs\emotes-minecraft
```

- **Bedrock / GeckoLib animations** (`{"animations": ...}`, what Blockbench exports and Lunar and NoRisk build
  with) that move a player: bones named `head`, `body`, `right_arm`, ... in any spelling, GeckoLib's
  `bipedHead`, `bipedRightArm`, ... too, and NoRisk's `bipedRig` (the whole player, turning where the model next
  to the animation has it). Each animation is one emote, named after its file. Bones are renamed to the mod's;
  other bones are left out (noted in the report). Molang and `scale` (parts made bigger or smaller) are kept: the
  mod plays them.
- **NoRisk's names**: its shop files (`shop/emotes/*.norisk.json`) give the emotes their names in the game. Animations of cosmetics (a
  folder with the cosmetic's `.geo.json`, `.gek.json` or `.norisk.json`) and of models that move one player bone
  or mostly other bones aren't emotes. Inside archives (mostly mods, whose mobs have bones named like a player's
  too) only animations whose path or name says "emote" are taken.
- **Emotecraft** JSON emotes, as they are.

Emotes get the client in their id when the path says which (`lunar_emotes:`, `norisk_emotes:`,
`badlion_emotes:`, else `imported_emotes:`). The report (`emote-report.md`) lists every emote, what was left out
and why, and every **emote-like file it couldn't read** (a path with "emote" in it, Emotecraft's binary
`.emotecraft`): send that list to get those supported. Running it again on the same pack folder replaces its
emotes. Packs this tool wrote are passed over wherever they are (an earlier one copied into `resourcepacks`), so
emotes aren't found twice. Copy the pack into `resourcepacks` and turn it on; the emotes appear in Wardrobe → Emotes.

#### Settings file

Every key is optional:

```json
{
  "max_texture_size": 256,
  "quantize": true,
  "cape_model": "cloak/models/cloak_1/cloak_1.md5mesh",
  "cape_style": "dynamic",
  "fit_texture_direction": true,
  "types": {
    "CAPE": { "flip_v_md5": true },
    "HAT":  { "flip_v_obj": true, "offset": [0, -0.5, 0] }
  }
}
```

- `cape_model`: what capes without a model are: `box` (the default), or a built-in cloak as the path under
  Badlion's texture folder, e.g. `cloak/models/cloak_v2/cloak_v2.md5mesh` (the calibration names them).
- `cape_style`: the style shown first for capes on a cloak model (`rect`, `curved` or `dynamic`).
- `fit_texture_direction`: `false` keeps every model's texture direction as the type's settings say, instead
  of checking it against the texture.
- Per type: `flip_v_obj` and `flip_v_md5` turn the texture's V direction around for that type's OBJ and MD5
  models (the calibration's "flipped" / "V up" variants); `offset` (pixels, the mod's model axes: +Y down,
  -Z front) and `rotation` (degrees) nudge where the type sits; `shading` is `smooth` or `flat`.

## Tests

```
pip install ".[test]"
python -m pytest -q
```

The tests build small synthetic jars, definitions, AVIF files and models of their own, and made-up Lunar and
NoRisk folders. They never use any client's real files.
