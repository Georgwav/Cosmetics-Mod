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
| `texture_animation` | no | Flip-book animation of `texture`, for every part drawn with it. See [Animated textures](#animated-textures). |
| `options` | no | Which settings players may change on the cosmetic's page. See [Player settings](#player-settings). |
| `default_color` | no | Tint the cosmetic has until a player picks another: `"#RRGGBB"` or a number. Default white (the texture's own colours). |

## Parts

| Key | Required | Meaning |
|---|---|---|
| `name` | no | Only used in error messages. |
| `mode` | no | `cutout` (default): opaque or alpha-tested. `translucent`: alpha-blended. `emissive`: glows, drawn with the vanilla `eyes` render type, which shaderpacks treat as emissive. |
| `texture` | no | Overrides the cosmetic's texture for this part, for example a mostly transparent glow overlay. Must use the same `texture_size`. |
| `cubes` | one of `cubes`/`meshes` | The part's boxes. |
| `meshes` | one of `cubes`/`meshes` | The part's free-form meshes. See [Meshes](#meshes). A part can have both. |
| `animation` | no | Movement for this part only; overrides the cosmetic's `animation`. |
| `texture_animation` | no | Flip-book animation of this part's texture; overrides the cosmetic's. See [Animated textures](#animated-textures). |
| `tint` | no | `false` keeps this part in its texture's own colours whatever tint, rainbow or default colour is chosen. Default `true`. Use it to colour one layer of a cosmetic and leave the rest as painted. |

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

## Meshes

Besides boxes, a part can have free-form meshes: triangles and quads, written inline or loaded from an
`.obj` file or a Blockbench `.bbmodel` project. They use the same space as cubes (pixels, +Y down, -Z
front, relative to the anchor's pivot) and work with everything cubes do: `mode`, per-part `texture`,
`animation`, tint, the size and position settings, and every slot.

```json
{
  "slot": "head",
  "name": "Orbit Ring",
  "texture": "cosmeticsmod:textures/cosmetic/orbit_ring.png",
  "texture_size": [32, 16],
  "animation": { "type": "spin", "axis": "y", "speed": 0.12 },
  "parts": [
    {
      "name": "ring",
      "meshes": [
        {
          "model": "cosmeticsmod:models/cosmetic/orbit_ring.obj",
          "shading": "smooth",
          "rotation": [12, 0, -10],
          "offset": [0, -6.5, 0]
        }
      ]
    }
  ]
}
```

This is the bundled `cosmeticsmod:orbit_ring`.

### Inline meshes

```json
"meshes": [
  {
    "vertices": [[-2, -10, 0], [2, -10, 0], [2, -14, 0], [-2, -14, 0], [0, -16, 0]],
    "uvs":      [[0, 8], [4, 8], [4, 4], [0, 4], [2, 2]],
    "faces":    [[0, 1, 2, 3], [3, 2, 4]]
  }
]
```

| Key | Required | Meaning |
|---|---|---|
| `vertices` | yes | `[x, y, z]` in pixels. |
| `uvs` | yes | `[u, v]` in texture pixels (as `texture_size` counts them), one per vertex. |
| `faces` | yes | Triangles `[a, b, c]` or quads `[a, b, c, d]` of 0-based vertex indices. List the corners **counter-clockwise as seen from outside**, like the corners of a cube face. |
| `normals` | no | `[x, y, z]` per vertex, for `"shading": "smooth"`. |

A vertex with a different UV on two faces (a texture seam) is written twice.

### Mesh files

`"model"` names a file in any resource pack by its full location, for example
`"cosmeticsmod:models/cosmetic/orbit_ring.obj"` for `assets/cosmeticsmod/models/cosmetic/orbit_ring.obj`. Use
either `model` or inline geometry in one mesh, not both.

- **OBJ** (`.obj`, up to 4 MiB):
  - Reads `v`, `vt`, `vn` and `f` lines. Faces can be triangles, quads or larger polygons (split into a fan
    of triangles), with 1-based or negative indices.
  - Every face corner needs a texture coordinate. OBJ texture coordinates (0 to 1, V up) are converted to
    texture pixels. They must stay within 0 to 1 (textures don't repeat).
  - `vn` normals are used for smooth shading when every face has them.
  - Materials, groups and smoothing groups are ignored: the texture and shading come from the cosmetic file.
- **Blockbench project** (`.bbmodel`, up to 16 MiB):
  - Reads the project's **mesh** elements, including their rotation and their groups' rotations. Cube
    elements are skipped with a warning; export those as a [Bedrock model](#blockbench-models) instead.
  - Build the mesh where it sits on the player (feet at y = 0), as for Bedrock models. It is converted the
    way Blockbench's own Java entity export converts: `x` becomes `-x` and `y` becomes `24 - y`.
  - UVs are scaled from the project's UV resolution to `texture_size`. Faces without a texture are skipped,
    as Blockbench hides them. The texture is the cosmetic's own: save Blockbench's texture as a PNG in your
    pack and point `texture` at it.

**From Blender** (or another Y-up tool), export OBJ with UV coordinates and normals, keeping the default
axes (forward -Z, up Y). Then flip Y with a negative scale and move the model onto the anchor. If 1 Blender
unit is 1 block and the model sits on a player standing at the origin, the neck is 1.5 blocks (24 px) up:

```json
{ "model": "<ns>:models/cosmetic/wings.obj", "scale": [16, -16, 16], "offset": [0, 24, 0], "shading": "smooth" }
```

### Placement and shading

These apply to inline and file meshes, in this order: `mirror`, `scale`, `rotation` around `pivot`, then `offset`.

| Key | Default | Meaning |
|---|---|---|
| `mirror` | `false` | Mirror on X. |
| `scale` | `1` | One number, or `[x, y, z]`. Negative values mirror on that axis. |
| `pivot` | `[0, 0, 0]` | Rotation origin in pixels. |
| `rotation` | `[0, 0, 0]` | Degrees around X, Y, Z, applied X, then Y, then Z (like cubes). |
| `offset` | `[0, 0, 0]` | Moves the mesh, in pixels. |
| `shading` | `flat` | `flat`: one normal per face, like cubes. `smooth`: normals per vertex, from the file's normals if it has them, otherwise averaged from the faces around each vertex (larger faces count more). Smooth shading shows on `cutout` and `translucent` parts; `emissive` parts aren't lit, so it changes nothing there. |

A mirror or negative scale also turns the faces around, so they still face outwards.

### Limits and errors

- **Triangles:** one cosmetic may have up to 20 000 mesh triangles (a quad counts as two). Players can change
  this with `maxMeshTriangles` in `config/cosmeticsmod.json` (1 000 to 200 000). A cosmetic over the limit
  isn't loaded, and the log says why.
- **Errors** skip that cosmetic and log the cosmetic file plus the mesh file and line, or the face. These
  include:
  - out-of-range indices;
  - numbers that aren't finite;
  - UVs outside the texture;
  - `uvs` or `normals` lists that don't have one entry per vertex;
  - a scale of 0;
  - zero-length normals;
  - files that are missing or too big.
- **Faces with no area** are dropped with a warning; the rest of the mesh loads.
- **Sides drawn:** meshes are drawn from both sides wherever cubes are (`cutout` and `translucent`). `emissive`
  parts, like emissive cubes, show only the side the corners wind counter-clockwise on.

### Performance

Everything about a mesh is worked out once when it loads. Drawing it then costs about the same as the same
number of cube faces:
- flat meshes share a draw call with the part's cubes;
- smooth meshes use the same draw call with a per-vertex normal;
- a triangle costs as much as a quad, so prefer quads where you can.

Cosmetics without meshes draw exactly as before.

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
- **Not used**: Bedrock animations, locators and `poly_mesh`. Use [Animation](#animation) instead, and
  [Meshes](#meshes) for free-form shapes.

`bones` keys are bone names (case-insensitive):

| Key | Meaning |
|---|---|
| `mode` | `cutout`, `translucent` or `emissive` for that bone's cubes. |
| `texture` | Another texture for that bone, for example a glow overlay. Same texture size. |
| `animation` | Movement for that bone. `spin` and `swing` turn around the bone's pivot unless you give a `pivot`. |

Bones are converted to the Java entity model axes the same way Blockbench converts between its
Bedrock and Java entity formats:
- `x` and `z` stay, and `y` becomes `24 - y`;
- rotations stay as they are;
- per-face UVs move to the matching faces (Bedrock's `up`/`down` are our top/bottom, and its `east`/`west`
  are our west/east).

So a model lines up with the player the way it did in Blockbench, and the vanilla Bedrock player model
converts to exactly the Java one.

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

## Animated textures

A texture can flip through frames like a flip-book. The geometry stays as it is; each frame only moves
the texture coordinates or swaps the texture, so it costs nothing extra to draw.

**All frames in one image**, stacked top to bottom. `texture_size` describes one frame, and the image is
`frames` times as tall. A 64x32 cape with 4 frames is a 64x128 PNG:

```json
{
  "slot": "cape",
  "texture": "<ns>:textures/cosmetic/ember_cape.png",
  "texture_size": [64, 32],
  "texture_animation": { "frames": 4, "frame_times": [120, 80, 80, 120] },
  "parts": [ { "cubes": [ { "from": [-5, 0, -1], "to": [5, 16, 0], "uv": [0, 0] } ] } ]
}
```

**One file per frame**, all the same size:

```json
"texture_animation": {
  "textures": ["<ns>:textures/cosmetic/flag_0.png", "<ns>:textures/cosmetic/flag_1.png"],
  "frame_time": 100
}
```

| Key | Required | Meaning |
|---|---|---|
| `frames` | yes, unless `textures` is set | Number of frames, 1 to 1024. With `textures` it may be left out; if given it must match. |
| `textures` | no | One texture per frame, shown instead of the part's `texture`. Without it, the frames are a vertical strip in the part's texture. |
| `frame_time` | no | How long every frame shows, in milliseconds. Default `100`. |
| `frame_times` | no | How long each frame shows, in milliseconds, one entry per frame. Overrides `frame_time`. |

- The cosmetic's `texture_animation` applies to every part drawn with the cosmetic's own `texture`, including
  the bones of a [Blockbench model](#blockbench-models). Parts with their own `texture` (such as a glow overlay)
  stay still unless they set their own `texture_animation`.
- Texture animation follows the cosmetic's animation settings: off shows the first frame, and the speed
  setting speeds it up or slows it down.
- Frame times are whole milliseconds, 1 to 600 000.

## Player settings

Each cosmetic has its own settings page in game. By default it offers everything; `options` turns settings
off for one cosmetic. A setting that is off is hidden on the page and ignored when drawing (even if
`config/cosmeticsmod.json` still has a value for it), so the cosmetic looks the way its creator set it:

```json
"options": { "rainbow": false, "saturation": false, "size": false }
```

| Key | Setting | When off |
|---|---|---|
| `tint` | Tint colour | The `default_color` is used. |
| `rainbow` | Rainbow and its speed | No rainbow. |
| `saturation` | How strong the tint or rainbow colour is (0 to 100 %) | Full strength. |
| `size` | Size | Normal size. |
| `position` | Move sideways, up, forward | Where the creator put it. |
| `glow` | Glow on or off (only shown when the cosmetic has `emissive` parts) | Always glows. |
| `animation` | Animation on or off and its speed (only shown when something moves or a texture is animated) | Always animated, at normal speed. |

Options left out stay allowed. A coloured cosmetic with a fixed look except for one layer:

```json
{
  "slot": "back",
  "texture": "<ns>:textures/cosmetic/banner.png",
  "default_color": "#3366CC",
  "options": { "rainbow": false },
  "parts": [
    { "name": "pole", "tint": false, "cubes": [ ... ] },
    { "name": "cloth", "cubes": [ ... ] }
  ]
}
```

## Equipping

In `config/cosmeticsmod.json`:

```json
"equipped": {
  "local": ["cosmeticsmod:top_hat", "cosmeticsmod:starlight_cape"],
  "069a79f4-44e9-4726-a5be-fca90e38aaf5": ["cosmeticsmod:halo"]
}
```

`local` is whoever plays on this client. Other keys are player UUIDs.
