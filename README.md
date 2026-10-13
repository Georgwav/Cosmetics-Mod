# Cosmetics Mod

Client-side Fabric mod that draws player cosmetics (hats, halos, backpacks, capes, ...) as a normal part of the
player model. It is built to coexist with any client mod: Sodium, Iris and shaderpacks, Voxy, Distant Horizons,
ImmediatelyFast, Entity Culling, EMF/ETF, 3D Skin Layers, Figura and Essential. See [COMPAT.md](COMPAT.md).

One source tree builds a jar for every Minecraft release from **1.21 to 26.3**:

| Jar (`build/libs/`) | Minecraft | Java |
|---|---|---|
| `cosmeticsmod-<v>+mc1.21.1.jar` | 1.21, 1.21.1 | 21 |
| `cosmeticsmod-<v>+mc1.21.3.jar` | 1.21.2, 1.21.3 | 21 |
| `cosmeticsmod-<v>+mc1.21.4.jar` | 1.21.4 | 21 |
| `cosmeticsmod-<v>+mc1.21.5.jar` | 1.21.5 | 21 |
| `cosmeticsmod-<v>+mc1.21.8.jar` | 1.21.6, 1.21.7, 1.21.8 | 21 |
| `cosmeticsmod-<v>+mc1.21.10.jar` | 1.21.9, 1.21.10 | 21 |
| `cosmeticsmod-<v>+mc1.21.11.jar` | 1.21.11 | 21 |
| `cosmeticsmod-<v>+mc26.1.2.jar` | 26.1, 26.1.1, 26.1.2 | 25 |
| `cosmeticsmod-<v>+mc26.2.jar` | 26.2 | 25 |
| `cosmeticsmod-<v>+mc26.3.jar` | 26.3 | 25 |

The only required dependency is Fabric API.

## Using it

### Settings screen

There are three ways to open it:
- **Mod Menu**: the "Configure" button, when Mod Menu is installed.
- **Chat**: type `/cosmetics`.
- **Key**: the "Open Cosmetics" key. It is unbound by default so it can't clash with another mod; bind it
  in Options → Controls.

It is a flat, minimal screen with a live preview of your player and four tabs:

| Tab | What's there |
|---|---|
| Wardrobe | Every loaded cosmetic, grouped by slot. Toggle what you wear and watch the preview. The `>` at the end of a row opens that cosmetic's own page: tint colour or rainbow (with speed), size, move sideways / up / forward, glow on or off, animation on or off and its speed, and a reset. |
| Display | Master switch; show your own and other players' cosmetics; per-slot visibility; glowing parts; see-through parts; hide hats under helmets; hide capes with elytra; cape physics and cape swing strength. |
| Performance | Render distance; shadows with shaders (Iris) on or off; shadow distance. |
| Compatibility | Per-slot priority (Defer or Override) for slots another mod uses; defer to Figura avatars; detected mods; render-error counter; reload cosmetics. |

Changes apply immediately and are saved when the screen closes. "Reset settings" restores defaults but
keeps your wardrobe and how each cosmetic looks; each cosmetic's page has its own reset.

### Cosmetics

- **Bundled**:
  - `cosmeticsmod:top_hat`
  - `cosmeticsmod:halo` (glowing, slowly spins and bobs)
  - `cosmeticsmod:explorer_backpack`
  - `cosmeticsmod:feather_wings` (flapping)
  - `cosmeticsmod:starlight_cape` (with a glowing emblem)
- **Add more** from any resource pack at `assets/<ns>/cosmetics/*.json`. See
  [docs/COSMETIC_FORMAT.md](docs/COSMETIC_FORMAT.md).

### Config file (`config/cosmeticsmod.json`)

Everything on the settings screen is stored here. The file can also be edited by hand; bad values are
repaired on load.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch. |
| `showOwnCosmetics` / `showOtherPlayersCosmetics` | `true` | Whose cosmetics are drawn. |
| `slotVisible` | all `true` | Per-slot visibility (`HEAD`, `FACE`, `BODY`, `BACK`, `CAPE`). |
| `hideHeadwearWithHelmet` | `true` | Hats and face cosmetics hide while something is worn on the head. |
| `hideCapeWithElytra` | `true` | Capes hide while an elytra is worn. |
| `capePhysics` / `capeSwing` | `true` / `1.0` | Cape sway on or off, and its strength (0 to 2). |
| `emissiveParts` / `translucentParts` | `true` | Draw glowing and see-through parts. |
| `maxRenderDistance` | `64` | Only players within this many blocks get cosmetics (`0` = no limit). |
| `renderInShadowPass` | `true` | Cosmetics cast shadows with Iris shaderpacks. Turn off for FPS. |
| `shadowDistance` | `32` | Only players within this many blocks cast cosmetic shadows (`0` = no limit). |
| `slotPriority` | all `DEFER` | `DEFER`: if another mod already uses the slot for that player (a vanilla or other mod's cape, a Figura avatar), ours is not drawn. `OVERRIDE`: draw ours anyway; on `CAPE` this also hides the vanilla cape for players wearing one of our capes. |
| `deferToFigura` | `true` | Players with a Figura avatar don't get our cosmetics in `DEFER` slots. |
| `equipped` | `{"local": []}` | Who wears what: `local` is you, other keys are player UUIDs. |
| `cosmeticOptions` | `{}` | Per-cosmetic settings, keyed by cosmetic id: `color` (RGB number, white = original), `rainbow`, `rainbowSpeed` (0.1 to 5), `scale` (0.5 to 1.5), `offsetX` / `offsetY` / `offsetZ` (pixels, -8 to 8, model axes), `glow`, `animate`, `animationSpeed` (0.1 to 3). Only cosmetics you changed are listed. |

## Building

```
./gradlew build            # all Minecraft versions; jars land in build/libs/
./gradlew :1.21.8:build    # one version
```

- **Gradle needs JDK 25.** 26.x requires it; 1.21.x builds still target Java 21.
- **Fabric Loom variants:** the remapping Loom (`net.fabricmc.fabric-loom-remap`) builds the obfuscated 1.21.x
  versions, and plain Loom (`net.fabricmc.fabric-loom`) builds 26.x. Both use Mojang names.
- **Version-specific code:** it lives inline as [Stonecutter](https://stonecutter.kikugie.dev) comments, for example
  `//? if >=1.21.9 {`.
  - The committed sources are written for **1.21.11**, so that is the version your IDE sees.
  - To work on another version, run the Stonecutter "Set active project" task for it, and switch back to 1.21.11
    before committing.

### Porting to a new Minecraft version

1. Add the version to `settings.gradle.kts`, `versions/<v>/gradle.properties` and `ci/versions.json`.
2. Build and fix compile errors with Stonecutter conditions.
3. To see an API exactly as that version has it, run
   `./gradlew :<v>:dumpApi -PdumpClasses=<class>,<class>`, or add a line to `ci/api-dump.txt` and push a commit
   whose message contains `[api-dump]`.

## Testing

`.github/workflows/ci.yml` starts the real game on every supported Minecraft release, so no version has to be
tested by hand. Details are in [COMPAT.md](COMPAT.md#how-this-is-tested).

To run the same scenario yourself, start the game with these JVM arguments:

| JVM argument | Effect |
|---|---|
| `-Dcosmeticsmod.equipAll=true` | Every player wears every cosmetic. |
| `-Dcosmeticsmod.smokeTest=true` | After joining a world: third person front and back, then (with Iris and `-Dcosmeticsmod.expectShaders=true`) shaders on and the shadow-pass option off. It writes `cosmeticsmod-results.json`, logs PASSED/FAILED and quits. |
| `-Dcosmeticsmod.benchmark=true` | Same phases, timed, shaders off then on. Add `-Dcosmeticsmod.benchmark.copies=50` to draw every cosmetic 50 times and simulate a crowded server. |

Locally, `python3 ci/mcci.py runtime --node 1.21.8` does what CI does. You need Xvfb, `xwd` and the jar in
`build/libs`.
