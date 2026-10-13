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
py -m pip install .
```

On macOS or Linux, use `python3 -m venv .venv`, `source .venv/bin/activate` and `pip install .`.

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

Comes once `inspect` has confirmed the layout on your files.

## Tests

```
pip install ".[test]"
python -m pytest -q
```

The tests build small synthetic jars, definitions, AVIF files and models of their own. They never use
Badlion files.
