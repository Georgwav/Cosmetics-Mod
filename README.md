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

- **Equip cosmetics**: put their ids in `config/cosmeticsmod.json` under `equipped` (see
  [docs/COSMETIC_FORMAT.md](docs/COSMETIC_FORMAT.md#equipping)).
- **Bundled cosmetics**: `cosmeticsmod:top_hat`, `cosmeticsmod:halo` (emissive), `cosmeticsmod:explorer_backpack`
  and `cosmeticsmod:starlight_cape` (with an emissive emblem).
- **New cosmetics**: add them from any resource pack, at `assets/<ns>/cosmetics/*.json`. See
  [docs/COSMETIC_FORMAT.md](docs/COSMETIC_FORMAT.md).

### Config (`config/cosmeticsmod.json`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch. |
| `renderInShadowPass` | `true` | Cosmetics cast shadows with Iris shaderpacks. Set to `false` to skip them in the shadow pass for FPS. |
| `slotPriority` | all `DEFER` | Per slot (`HEAD`, `FACE`, `BODY`, `BACK`, `CAPE`). `DEFER`: if another mod already uses the slot for that player (a vanilla or other mod's cape, a Figura avatar), our cosmetic is not drawn. `OVERRIDE`: draw ours anyway; on `CAPE` this also hides the vanilla cape for players wearing one of our capes. |
| `equipped` | `{"local": []}` | Who wears what. |

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
