# Cosmetic format

A cosmetic is one JSON file at `assets/<namespace>/cosmetics/<name>.json` in the mod jar or any resource pack.
Its id is `<namespace>:<name>`, for example `cosmeticsmod:top_hat`.

Files are loaded on startup and on every resource reload (F3+T). A broken file is logged and skipped, and never
stops the game or the other cosmetics.

```json
{
  "slot": "head",
  "texture": "cosmeticsmod:textures/cosmetic/top_hat.png",
  "texture_size": [64, 32],
  "parts": [
    {
      "name": "hat",
      "mode": "cutout",
      "cubes": [
        { "from": [-6, -9.5, -6], "to": [6, -8.5, 6], "uv": [0, 0] },
        { "from": [-4, -17.5, -4], "to": [4, -9.5, 4], "uv": [0, 13] }
      ]
    }
  ]
}
```

## Top level

| Key | Required | Meaning |
|---|---|---|
| `slot` | yes | `head`, `face`, `body`, `back` or `cape`. Decides the anchor (head or body) and which per-slot priority setting applies. |
| `texture` | yes | Default texture for all parts. `namespace:path`; without a namespace the file's namespace is used. |
| `texture_size` | no | `[width, height]` used for UV maths. Default `[64, 64]`. |
| `parts` | yes, unless `model` is set | One or more parts. |
| `model` | no | A Blockbench / Bedrock model to use instead of (or as well as) `parts`. See [Blockbench models](#blockbench-models). |
| `bones` | no | Per-bone settings for `model`. |
| `animation` | no | Movement for every part that doesn't set its own. See [Animation](#animation). |

## Parts

| Key | Required | Meaning |
|---|---|---|
| `name` | no | Only used in error messages. |
| `mode` | no | `cutout` (default): opaque or alpha-tested. `translucent`: alpha-blended. `emissive`: glows, drawn with the vanilla `eyes` render type, which shaderpacks treat as emissive. |
| `texture` | no | Overrides the cosmetic's texture for this part, for example a mostly transparent glow overlay. Must use the same `texture_size`. |
| `cubes` | yes | The part's boxes. |
| `animation` | no | Movement for this part only; overrides the cosmetic's `animation`. |

## Cubes

Coordinates are in pixels (1/16 block) relative to the anchor's pivot. They use the same axes as vanilla entity
models: +Y is **down**, -Z is the player's front, +Z their back.

- **Head anchor**: the head cube spans `[-4, -8, -4]` to `[4, 0, 4]` (pivot at the neck).
- **Body anchor**: the body spans `[-4, 0, -2]` to `[4, 12, 2]`.
- **`cape` slot**: before your cubes are drawn, the anchor is moved 2 px behind the body and swings exactly like the
  vanilla cape. Model capes like the vanilla cape cube, `from [-5, 0, -1] to [5, 16, 0]`, with the vanilla 64x32 cape layout.

| Key | Required | Meaning |
|---|---|---|
| `from`, `to` | yes | Opposite corners. `to` must be >= `from` on every axis. |
| `uv` | one of `uv`/`faces` | Vanilla box UV origin `[u, v]`, the same layout as Blockbench "Java Entity" / vanilla `texOffs`. |
| `faces` | one of `uv`/`faces` | Per-face UV `{"north": [u1, v1, u2, v2], ...}` with keys `down`, `up`, `north`, `south`, `west` and `east`. A face that is left out is not drawn. |
| `inflate` | no | Grows the box on every side without changing UVs, like vanilla `CubeDeformation`. |
| `mirror` | no | Mirrors UVs on X (vanilla `mirror`). |
| `pivot` | no | Rotation origin in pixels. Default `[0, 0, 0]`. |
| `rotation` | no | Degrees around X, Y, Z, applied in vanilla order: X, then Y, then Z. |

## Blockbench models

Instead of writing cubes by hand, model the cosmetic in [Blockbench](https://www.blockbench.net/):

1. Create a **Bedrock Entity** model. Add the player as a reference (for example, start from the player
   template or import a Bedrock player model) and build the cosmetic where it should sit on the player.
2. Paint the texture in Blockbench and save it as a PNG in your resource pack, for example
   `assets/<ns>/textures/cosmetic/crown.png`.
3. **File → Export → Export Bedrock Geometry** to `assets/<ns>/cosmetic_models/crown.geo.json`. The folder
   is `cosmetic_models`, not `models`: vanilla reads everything under `models` as block and item models.
4. Add `assets/<ns>/cosmetics/crown.json`:

```json
{
  "name": "Royal Crown",
  "slot": "head",
  "texture": "<ns>:textures/cosmetic/crown.png",
  "model": "<ns>:crown",
  "bones": {
    "gems": { "mode": "emissive" }
  }
}
```

The bundled `cosmeticsmod:crown` is made exactly like this.

- **Placement**: the model keeps the position you gave it relative to the player. The head and body anchors
  both pivot at the neck (24 px up), so cosmetics follow head turns and body movement. For the `cape`
  slot, build the cape relative to the top center of the back.
- **Supported**: bones with parents, pivots and rotations, cube pivots and rotations, `inflate`, `mirror`,
  box UV and per-face UV. Texture size comes from the model's `texture_width` / `texture_height`.
- **Not used**: Bedrock animations, locators and `poly_mesh`. Use [Animation](#animation) instead.

`bones` keys are bone names (case-insensitive):

| Key | Meaning |
|---|---|
| `mode` | `cutout`, `translucent` or `emissive` for that bone's cubes. |
| `texture` | Another texture for that bone, for example a glow overlay. Same texture size. |
| `animation` | Movement for that bone. `spin` and `swing` turn around the bone's pivot unless you give a `pivot`. |

Bones are converted to the Java entity model axes: `x` and `z` stay, `y` becomes `24 - y`, rotations
`[x, y, z]` become `[-x, y, -z]`, and the `up` and `down` faces swap. This is the same relationship the
vanilla Bedrock and Java player models have, so a model lines up with the player the way it did in
Blockbench.

## Animation

`animation` is one object, or an array of them applied in order. For example, the bundled halo spins and
bobs:

```json
"animation": [
  { "type": "spin", "axis": "y", "speed": 0.15 },
  { "type": "bob", "axis": "y", "amplitude": 0.6, "speed": 0.4 }
]
```

The bundled wings flap out of step, because each wing swings the opposite way around its own pivot:

```json
{ "name": "left_wing",  "animation": { "type": "swing", "axis": "y", "pivot": [1, 0, 3],  "amplitude": 20 },  ... },
{ "name": "right_wing", "animation": { "type": "swing", "axis": "y", "pivot": [-1, 0, 3], "amplitude": -20 }, ... }
```

| Key | Required | Meaning |
|---|---|---|
| `type` | yes | `spin`: keeps turning around the axis. `bob`: moves back and forth along it. `swing`: rocks back and forth around it. |
| `axis` | no | `x`, `y` (default) or `z`, in the same model axes as cubes. |
| `pivot` | no | Rotation origin in pixels for `spin` and `swing`. Default `[0, 0, 0]` (the anchor's pivot). |
| `amplitude` | no | Degrees for `swing` (default 15), pixels for `bob` (default 0.5). |
| `speed` | no | Turns per second for `spin` (default 0.25), cycles per second for `bob` and `swing` (default 0.5). |
| `phase` | no | Offset into the cycle, 0 to 1, so parts can move out of step. |

Players can turn animation off, or change its speed, for each cosmetic on its settings page.

## Equipping

In `config/cosmeticsmod.json`:

```json
"equipped": {
  "local": ["cosmeticsmod:top_hat", "cosmeticsmod:starlight_cape"],
  "069a79f4-44e9-4726-a5be-fca90e38aaf5": ["cosmeticsmod:halo"]
}
```

`local` is whoever plays on this client. Other keys are player UUIDs.
