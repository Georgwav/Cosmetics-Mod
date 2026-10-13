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
  model file names). No asset contents and no cosmetic names, so it can be committed or shared.
- prints a summary: cosmetics present per type, which have models, which are animated, missing shared
  models and the cosmetics that need them, and anything that doesn't fit. Add `--verbose` to list every
  present cosmetic, `--summary <file>` to save it (not inside this repository), `--no-hash` to skip the
  SHA-1 check of every file (faster).

### `list`

```powershell
badlion-import list C:\badlion-input
badlion-import list C:\badlion-input --type CAPE --present
```

Type, id, name, and whether its texture and model are in your jars (`present`) or not (`missing`).

### `import`

Writes a resource pack of the cosmetics in your jars. Do it in two steps.

**1. Calibrate.** A few of your cosmetics in every variant that can only be judged by eye: each built-in cloak
model on one cape without a model (and both directions of its texture), and both texture directions on one
cosmetic of every type:

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
- capes, with Badlion's built-in cloak when they have no model of their own, and its `rect`, `curved` and
  `dynamic` animations as the cape's styles;
- wings: one model mirrored for the other side, flapping with your movement (skeletal wings play their own
  flap);
- hats, masks, glasses, ears, mouth and nose cosmetics, bandanas, necklaces, backpacks, tails and belts;
- animated textures (as frame strips with their timing), glow layers, see-through cosmetics, default colours,
  and whether Badlion lets the cosmetic be recoloured (chroma, saturation).

Skipped and listed in the report: pets, shields, swords, bows, sprays, auras and nametag decorations, boots and
armbands (the mod has no arm or leg anchors yet), cosmetics without a model of their own (other than capes),
and sub-models (only a cosmetic's main model converts).

#### Settings file

Every key is optional:

```json
{
  "max_texture_size": 256,
  "quantize": true,
  "cape_model": "cloak/models/cloak_1/cloak_1.md5mesh",
  "cape_style": "dynamic",
  "types": {
    "CAPE": { "flip_v_md5": true },
    "HAT":  { "flip_v_obj": true, "offset": [0, -0.5, 0] }
  }
}
```

- `cape_model`: the built-in cloak for capes without a model, as the path under Badlion's texture folder (the
  calibration names them). Default: the best UV fit `inspect` found, `corsair_cloak` when present.
- `cape_style`: the cape style shown first (`rect`, `curved` or `dynamic`).
- Per type: `flip_v_obj` and `flip_v_md5` turn the texture's V direction around for that type's OBJ and MD5
  models (the calibration's "flipped" / "V up" variants); `offset` (pixels, the mod's model axes: +Y down,
  -Z front) and `rotation` (degrees) nudge where the type sits; `shading` is `smooth` or `flat`.

## Tests

```
pip install ".[test]"
python -m pytest -q
```

The tests build small synthetic jars, definitions, AVIF files and models of their own. They never use
Badlion files.
