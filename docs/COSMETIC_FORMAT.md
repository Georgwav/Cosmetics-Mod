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
| `slot` | yes | `head`, `face`, `body`, `back`, `cape`, `shield`, `melee`, `aura`, `pet`, `arm` or `feet`. Decides the anchor (head, body, or the held shield) and which per-slot setting applies. A `melee` cosmetic is a knife: see [Knives](#knives). |
| `texture` | yes | Default texture for all parts. `namespace:path`; without a namespace the file's namespace is used. |
| `texture_size` | no | `[width, height]` used for UV maths. Default `[64, 64]`. |
| `parts` | yes, unless `model` is set | One or more parts. |
| `model` | no | A Blockbench / Bedrock model to use instead of (or as well as) `parts`. See [Blockbench models](#blockbench-models). |
| `bones` | no | Per-bone settings for `model`. |
| `model_animations`, `model_states`, `skeleton` | no | Blockbench / GeckoLib animations for `model`, and bones that follow the player's body parts. See [Model animations](#model-animations). |
| `animation` | no | Movement for every part that doesn't set its own. See [Animation](#animation). |
| `texture_animation` | no | Flip-book animation of `texture`, for every part drawn with it. See [Animated textures](#animated-textures). |
| `options` | no | Which settings players may change on the cosmetic's page. See [Player settings](#player-settings). |
| `default_style` | no | Which [style](#styles) shows until a player picks another. Default: the first. |
| `wings` | no | Wings that flap with what the player does. See [Wings](#wings). |
| `prop` | no | `true`: only shown while an emote that brings it plays (a cloud to ride), and not listed in the Wardrobe. See [Emotes](#emotes). |
| `pet_position` | no | For the `pet` slot: `left_shoulder` (default), `right_shoulder`, `head` or `floating`. See [Slots](#top-level). |
| `kind` | no | What the cosmetic counts as when players wear one of a kind at a time, e.g. `"hat"` and `"ears"` so a hat and ears can be worn together. Default: `"wings"` for wings, else the slot. See [Equipping](#equipping). |
| `first_person_turned` | no | `true`: a held cosmetic (a shield) made to face the other way round in first person than in third; it is turned half around its middle there. |
| `default_color` | no | Tint the cosmetic has until a player picks another: `"#RRGGBB"` or a number. Default white (the texture's own colours). |
| `icon` | no | A picture (`namespace:path.png`) for the cosmetic's Wardrobe card, shown as it is instead of a render of the model. |
| `group` | no | A name the Wardrobe lists the cosmetic under, with others that share it (a knife's finishes). |
| `melee` | for the `melee` slot | The knife's model, animations and fixes. See [Knives](#knives). |

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
| `wing` | no | `"left"` or `"right"`: this part is one of the cosmetic's [wings](#wings). |
| `wing_pivot` | no | Where that wing turns, in pixels. Default `[0, 0, 0]`. |
| `tint` | no | `false` keeps this part in its texture's own colours whatever tint, rainbow or default colour is chosen. Default `true`. Use it to colour one layer of a cosmetic and leave the rest as painted. |

## Cubes

Coordinates are in pixels (1/16 block) relative to the anchor's pivot. They use the same axes as vanilla entity
models: +Y is **down**, -Z is the player's front, +Z their back.

- **Head anchor**: the head cube spans `[-4, -8, -4]` to `[4, 0, 4]` (pivot at the neck).
- **Body anchor**: the body spans `[-4, 0, -2]` to `[4, 12, 2]`.
- **`cape` slot**: before your cubes are drawn, the anchor is moved 2 px behind the body and swings exactly like the
  vanilla cape. Model capes like the vanilla cape cube, `from [-5, 0, -1] to [5, 16, 0]`, with the vanilla 64x32 cape layout.
- **`pet` slot**: the pet sits on the wearer's left shoulder: model it standing on the origin, which is the top of
  that shoulder (body space `[6, 0, 0]`), growing upwards (-Y). `pet_position` moves it: `right_shoulder` (body
  `[-6, 0, 0]`), `head` (on top of the head, which it turns with) or `floating` (beside the left shoulder, a little
  above and behind it, bobbing). Players can move any pet on its Wardrobe page. A pet with a `shoulder` or `air`
  [style](#styles) plays `air` while floating and `shoulder` elsewhere, unless the player picks a style.
- **`arm` slot**: on the left arm (`"sides": "right"` or `"both"` to change it), the origin halfway down the arm; the
  arm spans `[-2, -6, -2]` to `[2, 6, 2]` around it.
- **`feet` slot**: on both legs (`"sides"` as above), modelled standing on the origin, the sole; the leg spans
  `[-2, -12, -2]` to `[2, 0, 2]` above it.
- **`shield` slot**: drawn instead of the vanilla shield while the player holds one (either hand, first and third
  person), in the vanilla shield model's space: its plate spans `[-6, -11, -2]` to `[6, 11, -1]` with the front facing
  -Z, and its handle `[-1, -3, -1]` to `[1, 3, 5]`. Shields in the inventory and on the ground stay vanilla.

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

- **OBJ** (`.obj`, up to 16 MiB):
  - Reads `v`, `vt`, `vn` and `f` lines. Faces can be triangles, quads or larger polygons (split into a fan
    of triangles), with 1-based or negative indices.
  - Every face corner needs a texture coordinate. OBJ texture coordinates (0 to 1, V up) are converted to
    texture pixels. They must stay within 0 to 1 (textures don't repeat).
  - `vn` normals are used for smooth shading when every face has them.
  - Materials, groups and smoothing groups are ignored: the texture and shading come from the cosmetic file.
  - `"group": "<name>"` reads only the faces under that `o` (object) or `g` (group) name, so one file can
    hold several pieces, for example a left and a right wing.
- **MD5** (`.md5mesh`, up to 8 MiB): a skinned mesh, optionally animated with `.md5anim` files. See
  [Skeletal meshes (MD5)](#skeletal-meshes-md5).
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
| `flip_v` | `false` | For `.obj` and `.md5mesh` files: the file's texture V runs the other way (for OBJ: already downwards; for MD5: upwards). |

A mirror or negative scale also turns the faces around, so they still face outwards.

### Limits and errors

- **Triangles:** one cosmetic may have up to 20 000 mesh triangles (a quad counts as two). Players can change
  this with `maxMeshTriangles` in `config/cosmeticsmod.json` (1 000 to 200 000). A cosmetic over the limit
  isn't loaded, and the log says why.
- **Errors** skip that cosmetic and log the cosmetic file plus the mesh file and line, or the face. These
  include:
  - out-of-range indices;
  - numbers that aren't finite;
  - UVs outside the texture (a texture coordinate up to 1% outside 0..1, as exporters round them, is put on its
    edge instead);
  - `uvs` or `normals` lists that don't have one entry per vertex;
  - a scale of 0;
  - files that are missing or too big.
- **Faces with no area** are dropped with a warning; the rest of the mesh loads.
- **Zero-length normals** (some exporters write `0 0 0` for corners they can't give a direction) are worked
  out from the faces, as for a mesh without normals, with a warning.
- **Sides drawn:** meshes are drawn from both sides wherever cubes are (`cutout` and `translucent`). `emissive`
  parts, like emissive cubes, show only the side the corners wind counter-clockwise on.

### Performance

Everything about a mesh is worked out once when it loads. Drawing it then costs about the same as the same
number of cube faces:
- flat meshes share a draw call with the part's cubes;
- smooth meshes use the same draw call with a per-vertex normal;
- a triangle costs as much as a quad, so prefer quads where you can.

Cosmetics without meshes draw exactly as before.

## Skeletal meshes (MD5)

An `.md5mesh` (id Software's MD5 format, version 10) holds joints and meshes whose vertices hang off weighted
joints; `.md5anim` files move the joints. Use one like any mesh file:

```json
"meshes": [
  {
    "model": "<ns>:models/cosmetic/cloak/cloak.md5mesh",
    "scale": [1, -1, 1],
    "shading": "smooth",
    "bone_animations": {
      "rect":    "<ns>:models/cosmetic/cloak/rect.md5anim",
      "curved":  "<ns>:models/cosmetic/cloak/curved.md5anim",
      "dynamic": { "file": "<ns>:models/cosmetic/cloak/dynamic.md5anim", "speed": 1.5 }
    }
  }
]
```

- **Without an animation** the mesh is drawn in its bind pose and costs exactly what an OBJ with the same
  triangles costs.
- **`bone_animation`** plays one animation: `{ "file": "...md5anim", "loop": true, "speed": 1 }`, or just the
  file's location.
- **`bone_animations`** maps names (`a-z`, `0-9`, `_`, `.`, `-`) to animations written the same way. The names
  are the cosmetic's [styles](#styles): the player picks one on the cosmetic's page, and every skeletal mesh
  in the cosmetic plays its animation of that name (or its first, if it has none by that name).
- `loop` (default `true`): `false` plays once and holds the last frame. `speed` (default `1`, up to `100`)
  multiplies the file's frame rate. Frames are blended smoothly. The cosmetic's animation setting turns the
  animation off (first frame) or changes its speed.
- **`submesh`**: an `.md5mesh` can hold several meshes (`mesh { shader "..." }` blocks). By default all are
  used; `submesh` picks one by its shader name, the last part of that name with or without its extension, or
  its index (`"0"`). Use one part per submesh to give each its own texture or mode.
- **`winding`**: `auto` (default) works out which way a closed mesh's triangles face from its volume, and takes
  open meshes (such as a flat cape) as clockwise, the id Tech convention. `cw` or `ccw` says which way the
  file's triangles turn when seen from outside, if `auto` guesses wrong (the cosmetic looks lit from inside).
- Placement keys (`mirror`, `scale`, `rotation`, `pivot`, `offset`) and `flip_v` work as for other meshes;
  units are the file's own, so `scale` turns them into pixels.
- MD5 files have no normals: `smooth` shading averages them from the faces, `flat` uses one per face.
- Joints of every `.md5anim` must match the `.md5mesh` (same names, same parents, same order). A mismatch is
  an error naming the joint.

**Performance.** All the work happens once, when the files load: animations are turned into absolute joint
transforms per frame, and the mesh is split by how its vertices are weighted.
- Triangles whose corners all follow one joint become that joint's rigid piece. Drawing it is one joint
  transform on the pose stack, then the normal vertex path.
- Only the remaining triangles (corners shared between joints) are skinned on the CPU each frame, into a
  buffer made at load time. Nothing is allocated per frame.
- Mesh triangles count towards `maxMeshTriangles` like any other mesh.

## Wings

`wings` makes a cosmetic's wings flap with what the player does: slowly while standing, faster while
walking or running, fastest while flying (creative flight or gliding with an elytra), and folded while
sneaking. Changes blend smoothly, so the wings never jump when the player starts or stops.

```json
{
  "slot": "back",
  "texture": "<ns>:textures/cosmetic/wings.png",
  "wings": {
    "axis": "y",
    "base": -25,
    "idle":     { "amplitude": 6, "speed": 0.4 },
    "moving":   { "amplitude": 18, "speed": 1.2 },
    "flying":   { "base": -10, "amplitude": 30, "speed": 2.5 },
    "sneaking": { "base": -45, "amplitude": 3, "speed": 0.3 },
    "sneak_offset": [0, 1, 0],
    "armor_offset": [0, 0, 1]
  },
  "parts": [
    { "name": "left_wing",  "wing": "left",  "wing_pivot": [1, 0, 3],  "cubes": [ ... ] },
    { "name": "right_wing", "wing": "right", "wing_pivot": [-1, 0, 3], "cubes": [ ... ] }
  ]
}
```

This is the bundled `cosmeticsmod:glider_wings`.

| Key | Default | Meaning |
|---|---|---|
| `axis` | `y` | Axis the wings turn around (model axes). |
| `base` | `0` | Resting angle in degrees for every state that doesn't set its own (sneaking defaults to 10° less). |
| `idle`, `moving`, `flying`, `sneaking` | see below | Each `{ "base": degrees, "amplitude": degrees, "speed": flaps per second }`. |
| `offset` | `[0, 0, 0]` | Pixels the whole cosmetic moves. |
| `sneak_offset` | `[0, 0, 0]` | Pixels added while sneaking (blended in). |
| `armor_offset` | `[0, 0, 0]` | Pixels added while something other than an elytra is worn in the chest slot. |
| `spread` | `0` | Pixels each wing moves outwards, sideways. |

Defaults per state: `idle` 6° at 0.35 flaps per second, `moving` 18° at 1, `flying` 32° at 2, `sneaking` 3° at 0.25.
"Moving" is reached at about sprinting speed; slower walking blends between `idle` and `moving`.

- The angle is `base + amplitude × sin(flap)`. The left wing turns by it around `axis` by the right-hand rule
  (around Y, from the back towards the player's left side); the right wing turns the opposite way, each
  around its own `wing_pivot`.
- Skeletal meshes in a cosmetic with `wings` play one loop of their animation per flap, so baked-in flapping
  speeds up and slows down too.
- Flapping follows the cosmetic's animation setting: off holds the resting angle.

## Styles

A cosmetic's styles are the names of its skeletal meshes' `bone_animations`, in the order they first appear.
With two or more, its page in game gets a **Style** setting (unless `options` has `"style": false`).
`default_style` names the one shown until a player picks another.

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
  box UV and per-face UV, and Blockbench meshes (`poly_mesh`: `positions`, `normals`, `uvs`, `normalized_uvs`, and
  `polys` as a list of polygons or `tri_list` / `quad_list`), each polygon facing the way its normals point. Texture
  size comes from the model's `texture_width` / `texture_height`.
- **Animations**: Bedrock / GeckoLib animations play with `model_animations` (see
  [Model animations](#model-animations)); the simple movements of [Animation](#animation) work too.
- **Not used**: locators.

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

### Model animations

Animate the model in Blockbench (Animate tab) and **File → Export → Export Bedrock Animations** to
`assets/<ns>/cosmetic_models/crown.animation.json`. GeckoLib animation files work as they are.

```json
{
  "slot": "back",
  "texture": "<ns>:textures/cosmetic/dragon_wings.png",
  "model": "<ns>:dragon_wings",
  "model_animations": "<ns>:dragon_wings.animation",
  "model_states": [
    { "animation": "glide", "when": "q.is_elytra_flying", "blend": 0.3 },
    { "animation": "swim", "when": "q.is_swimming", "blend": 0.4 },
    { "animation": "idle" }
  ]
}
```

| Key | Meaning |
|---|---|
| `model_animations` | The animation file, `namespace:name` for `assets/<ns>/cosmetic_models/name.json` (so `dragon_wings.animation` for `dragon_wings.animation.json`). |
| `model_states` | Which animation plays when: the first state whose `when` holds plays (no `when`: always). `blend` is how many seconds it takes to blend in from the one before. A state without `animation` leaves the bones at rest. A list of such lists is several controllers playing at once (for example one for the wings and one that hides a strap in first person). Without `model_states` the file's first animation plays. |
| `model_placement` | Moves the whole model around the anchor's pivot: `{"offset": [x, y, z], "rotation": [x, y, z], "scale": 1.5}` (pixels, degrees; `scale` may be `[x, y, z]`, negative mirrors). Scaled, then turned, then moved. Works without animations too. |
| `skeleton` | `"player"`: bones named like the player's parts follow them, with everything inside them. Names: `head`, `body`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, also with a `biped` or `armor` prefix (`bipedHead`, `armorRightArm`, as GeckoLib armor models have them). Build the model on the player as usual; a sword in `armorRightArm` then swings with the arm. |

What is supported in animation files:

- `rotation` (degrees), `position` (pixels, Bedrock axes: +Y is up) and `scale` per bone; children move with
  their parents, and rotations turn around the bone's pivot like in Blockbench.
- Keyframes by time in seconds, `pre`/`post` values, Bedrock's `lerp_mode` (`linear`, `catmullrom`, `step`) and
  GeckoLib's `easing` (`easeInOutSine` and the rest), or one value for all time.
- `loop` (`true`, `false`, `"hold_on_last_frame"`) and `animation_length`.
- Molang in any value or `when`: arithmetic, comparisons, `&& || !`, `? :`, `math.*` (`sin`, `cos`, `abs`,
  `clamp`, `lerp`, `random`, ... in degrees as in Bedrock), `v.name = ...; return ...;`, and these queries:
  `q.anim_time` (seconds into the animation), `q.life_time`, `q.is_in_gui` (Wardrobe pictures), `q.is_first_person`
  (a held shield in first person), `q.is_elytra_flying` / `q.is_gliding`, `q.is_swimming`, `q.is_sneaking`,
  `q.is_flying`, `q.is_blocking`, `q.is_moving`, `q.is_on_ground`, `q.is_sprinting`, `q.is_riding`,
  `q.ground_speed` (blocks per second) and `q.modified_distance_moved`. Other queries and functions are 0.

The cosmetic's **Animate** and **Animation speed** settings apply: off holds every animation at its start.

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

## Capes on the elytra

A player wearing a cape and an elytra has the cape's design on the elytra, as the game does with its own capes
("Show on elytra" on the cape's page, on by default). The elytra's texture is the first of:

1. `<texture>_elytra.png` beside the cape's texture (for `ns:textures/cosmetic/cape.png`,
   `ns:textures/cosmetic/cape_elytra.png`): an elytra texture in the game's 64 x 32 cape layout (the wings are
   drawn from blocks 22 to 46 across and 0 to 22 down), at any size twice as wide as high. For an animated cape, a
   vertical strip of as many frames as the cape has, shown in step with it.
2. The cape's own texture, when it is the 64 x 32 layout and its elytra part is drawn on, as the game's capes are.
3. Made from the cape's design: the cape's outside (its first cube's north face; for a cape without cubes, the
   vanilla cape's place in a 64 x 32 texture, else in the 22 blocks wide layout of other clients' capes) on the
   outside of each wing, stretched from the cape's 10 x 16 to the wing's 10 x 20 so it covers it, its inside on the
   wing's inside and the edges of both on the wing's edges. Glowing parts are drawn in (an elytra can't glow).
   Then it is cut to the shape of the game's elytra texture (a resource pack's, when one changes it), so it is
   an elytra in the cape's design, not a plate.

The elytra is recoloured and tinted as the cape is, and an animated cape's elytra plays its frames.

## Particles

Any cosmetic can give off particles: an aura, a trail behind a cape, sparkles from wings. A cosmetic in the
`aura` slot can be particles alone, with no `parts` and no `texture`:

```json
{
  "slot": "aura",
  "name": "Ember Aura",
  "particles": {
    "texture": "cosmeticsmod:ember",
    "rate": 14,
    "lifetime": [0.8, 1.6],
    "origin": [0, 2, 0],
    "area": [6, 2, 6],
    "velocity": [0, 14, 0],
    "size": [2.5, 0.5],
    "colors": ["#FF7A1A", "#FFE066"],
    "glow": true
  }
}
```

`particles` is one emitter or a list of them. The particles go into the game's particle engine, so they stay in the
world when the player moves on (a trail), work with shaders and are drawn like vanilla particles. Distances are in
pixels and directions are the player's, turned with their body: +X to their right, +Y **up**, +Z behind them.

| Key | Default | Meaning |
|---|---|---|
| `texture` / `textures` | required | Particle texture(s). `ns:name` is the file `assets/ns/textures/particle/name.png` (any resource pack, vanilla too: `minecraft:heart`, `minecraft:flame`, ...). With several, a particle steps through them over its life. White textures take the `colors` best. |
| `rate` | `10` | Particles per second (up to 200). |
| `lifetime` | `[1, 2]` | Seconds, a number or `[shortest, longest]`. |
| `origin` | `[0, 16, 0]` | Centre of the area particles start in, from the feet. |
| `area` | `[6, 12, 6]` | Half size of that area (a box). |
| `velocity` | `[0, 8, 0]` | Starting speed, pixels per second. |
| `spread` | `[2, 2, 2]` | Random extra starting speed, up to this either way. |
| `gravity` | `0` | Pull downwards, pixels per second squared (negative lifts). |
| `drag` | `0` | Share of its speed a particle loses per second (0 to 1). |
| `size` | `[2, 2]` | Size at birth and at death, pixels. |
| `alpha` | `[1, 0]` | Opacity at birth and at death (0 to 1). |
| `color` / `colors` | white | Tint(s); each particle picks one. |
| `glow` | `false` | Full bright, ignoring the light around it. |
| `spin` | `0` | Greatest turn, degrees per second, either way. |
| `when` | `always` | `always`, `moving` (a trail) or `still`. |

Players can turn particles off, set how many there are, and hide their own in first person (Display page).

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
| `saturation` | Saturation (of the tint or rainbow colour, or of the texture itself when untinted), and the texture's hue and brightness | Full strength, the texture's own colours. |
| `size` | Size | Normal size. |
| `position` | Move sideways, up, forward | Where the creator put it. |
| `glow` | Glow on or off (only shown when the cosmetic has `emissive` parts) | Always glows. |
| `animation` | Animation on or off and its speed (only shown when something moves or a texture is animated) | Always animated, at normal speed. |
| `style` | Which [style](#styles) shows (only when the cosmetic has two or more) | The `default_style`. |

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

Putting a cosmetic on in the Wardrobe takes off the one of its `kind` you wore (new wings replace your wings,
not your backpack), unless "Wear several of one kind" is on in Display (`severalOfAKind` in the config).

## Knives

A cosmetic in the `melee` slot is a knife: hold a sword and you see it in first person, with your own arms, playing
its draw, attacks and inspects (see the Punchy module's Knives settings). It isn't built from `parts`; its `melee`
block names a Bedrock model and animations made the way gunpacks for TACZ / LR Tactical make them, and the knife
renderer draws them as TACZ does. Inventory, dropped items and item frames keep the vanilla sword.

You don't usually write these files. Drop gunpacks (zips or folders, or a zip of several) into `client/melee/` in the
main folder and the client mounts each `display/melee/<id>.json` as a knife, in a resource pack it generates
("Sol Client: your knives", always on). Names, skipped knives and per-knife fixes go in `client/melee/knives.json`,
written with the defaults the first time:

```json
{
  "skip": ["delta_wt:null_annihil"],
  "knives": {
    "cs2_wt:cs2_m9emerald": { "name": "M9 Bayonet | Emerald" },
    "wtyj:desire_blade": { "name": "VCT Lock In", "hand_scale": 0.72 },
    "wtyj:eternal_karambit": { "name": "Prime Karambit", "camera": [1, -1, 4] },
    "cs2_wt:cs2_skeleton_ruby": { "name": "Skeleton Knife | Ruby", "finish_of": "cs2_wt:cs2_skeleton", "finish": "ruby" }
  }
}
```

Defaults added in later versions still apply to an older file: per knife, what the file says wins, and a default
knife or finish is turned off by putting its id in `skip`.

A name `Knife | Finish` lists the knife under `Knife` in the Wardrobe, with its other finishes. The packs' files are
only read on your computer; nothing of them is ever part of the mod.

**Sol finishes.** An entry with `finish_of` (a knife's id, in the same namespace) and `finish` is a finish Sol makes
from that knife: the same model and animations, with its texture and slot picture recoloured when the knives are
mounted. `ruby` is the one there is: the metal through a ruby ramp with a Doppler finish's light and dark patches,
the dark metal and the grip wrap near black.

**Borrowed animations.** A display may name another pack's files by that pack's namespace, for a model of your own
that plays another knife's animations: `"animation": "wtyj:melee/desire_blade"` in your pack's display plays VCT
Lock In's draws, attacks and inspects (and their sounds) on your model, when the Valorant pack is in the folder too.
The model needs the bones those animations move.

The `melee` block:

| Key | Required | Meaning |
|---|---|---|
| `model` | yes | The Bedrock geometry file, `namespace:path.json`. |
| `animations` | yes | The Bedrock animations file. The animation names the knife uses: `static_idle` (and `static_idle_1`, taking turns), `idle`, `draw`, `draw_1`, ..., `melee_1`, `melee_2` (light attacks, alternating), `melee_3` (the heavy attack, on critical hits), `inspect`, `inspect_1`, ..., and `run_start`, `run`, `run_end` or `walk*`. `*_angel` versions are a second set (Devil or Angel). |
| `sounds` | no | The namespace the animations' `sound_effects` keys play from. Default: the file's namespace. |
| `pack` | no | The gunpack it came from, shown on its Wardrobe page. |
| `hand_scale` | no | The arms' size around the fist, 0.25 to 4. Default 1. |
| `camera` | no | Moves the eye from the model's `idle_view` bone by `[x, y, z]` pixels: x to the right, y up, z back. |
| `angel` | no | For a knife with `*_angel` animations: `random` (default, picked on each draw), `devil` or `angel`. |
| `grip` | no | Moves the right arm onto the handle by `[x, z]` pixels across the arm (in the `righthand_pos` bone's frame). Default: worked out from the model, at most 2 px. |
| `transforms` | no | Third-person placement: `thirdperson_righthand` / `thirdperson_lefthand`, each with `rotation` (degrees), `translation` (pixels) and `scale`, relative to the model's `thirdperson_hand` bone. |

How the model is read:

- UVs are divided by the geometry's own `texture_width` / `texture_height`, not the picture's size. A face's
  rectangle is `uv` to `uv + uv_size` (a negative size flips it); `uv_rotation` is ignored.
- Bones named `*illuminated*` (or under one) draw at full light. `1p_effect*`, `effect*` and `glint*` are slash
  trails and glints, shown only while an animation scales them up. `*_pos` bones are where your arms go (your skin,
  right arm x -8 to -4, y 0 to 12 with the fist at +y, z -2 to 2; the left mirrored); their own cubes are never
  drawn. `camera` keys in an attack shake the view a little; `camera`, `view`, `idle_view`, `iron_view`,
  `refit_view`, `positioning`, `ground`, `fixed`, `thirdperson_hand` and `constraint` draw nothing.
- Animated rotation applies after the bone's own rotation. A catmull-rom segment starts from its first key's `pre`.

## Emotes

Emotes are separate files, at `assets/<namespace>/emotes/<name>.json` in any resource pack. Two formats work.

### Blockbench (Bedrock animations)

Animate a player model in Blockbench and use **File → Export → Export Bedrock Animations**. Every animation in
the file is an emote: a file with one animation is the emote `<namespace>:<name>`, a file with several gives
`<namespace>:<name>/<animation>` (`animation.emote.wave` gives `wave`).

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.emote.wave": {
      "animation_length": 2.4,
      "bones": {
        "right_arm": {
          "rotation": {
            "0.0": [0, 0, 0],
            "0.3": {"post": [-10, 0, 150], "lerp_mode": "catmullrom"},
            "2.1": [-10, 0, 150],
            "2.4": [0, 0, 0]
          }
        }
      }
    }
  }
}
```

- **Bones** are found by name, in any spelling (`right_arm`, `rightArm`, `RightArm`, GeckoLib's `bipedRightArm`):
  `head`, `body` (or `torso`), `right_arm`, `left_arm`, `right_leg`, `left_leg`, plus `waist` (bends the upper
  body at the hips) and `root` (or `bipedRig`; moves or turns the whole player, around the feet, or around
  `"root_pivot"`: pixels up from the feet, next to `bones`). They hang together like the Bedrock player:
  the head and arms move with the body, the body with the waist, everything with the root. Other bones are
  ignored with a warning in the log.
- **`rotation`** is in degrees, `[x, y, z]`, with the Bedrock player's signs (raising the right arm sideways is
  `+z`, forward is `-x`). **`position`** is in pixels with +Y up. **`scale`** makes a part bigger or smaller
  around its pivot (1 = as it is, 0 = gone); the root's scales every part.
- A value is a vector, or keyframes by time in seconds. A keyframe is a vector or
  `{"pre": [...], "post": [...], "lerp_mode": "linear" | "catmullrom" | "step"}`; GeckoLib's
  `{"vector": [...], "easing": "easeInOutSine"}` works too.
- Values are numbers or Molang (`"math.sin(q.anim_time * 90) * 30"`), which is worked out as the emote plays,
  30 times a second (`q.anim_time` is seconds into the emote; queries about the player are 0). Molang the mod
  doesn't understand makes the file fail, with the reason in the log.
- **`loop`**: `true` loops until you stop it, `"hold_on_last_frame"` stays on the last frame until you move,
  anything else plays once. An animation with no length is a still pose that holds.
- A part the emote doesn't move keeps its normal pose (so the head still looks around while you wave).

### Emotecraft

Emotecraft's JSON emotes (`{"name": ..., "emote": {"moves": [...]}}`) load as they are: `beginTick`, `endTick`,
`stopTick`, `isLoop` and `returnTick`, `degrees`, and per move `tick`, `easing` (all of Emotecraft's easings),
`turn`, with the parts `head`, `torso`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg` and `body` (the whole
player) and the axes `x`, `y`, `z`, `pitch`, `yaw`, `roll`. Limbs don't bend here, so `bend` and `axis` are
skipped. Before a part's first move it eases in from your normal pose, like in Emotecraft. Binary
`.emotecraft` files aren't supported.

### Flying and props

Two more keys work in both formats (in a Bedrock animation, next to `bones`; in an Emotecraft file, at the top):

- **`"elytra": true`**: the emote is made for flying, so it is on the elytra wheel until the player picks that
  wheel's emotes. Any emote can be played from the elytra wheel; there it plays until the player lands, and the
  player is drawn upright to the ground (+Y up, as when standing) instead of lying along the flight. Design an
  elytra emote as if the player were standing.
- **`"props": ["ns:name", ...]`**: cosmetics shown on the player while the emote plays, up to eight. Make them
  ordinary cosmetic files with `"prop": true`, so the Wardrobe doesn't list them. For example the bundled
  `cosmeticsmod:flying_cloud` is a `body` cosmetic modelled under the feet (the model's y = 24), with particles
  trailing behind it.

### Names and limits

The name shown is the translation `emote.<namespace>.<name>` if a language file has one, else the `name` in
the file, else the file name ("head_shake" shows "Head Shake"). An emote is at most 600 seconds long with at
most 10 000 keyframes per value; a file that breaks the rules is skipped with the reason in the log.
