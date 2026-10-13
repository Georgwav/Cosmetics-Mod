# Compatibility matrix

Status values:
- **CI: pass / fail**: result of the automated in-game run (see [How this is tested](#how-this-is-tested)).
- **Untested**: no result yet.
- **Your stack**: verified by hand on the maintainer's own instance. A milestone only counts as done once
  this column is filled in.

> Nothing below has been verified in-game yet. The first CI run fills in the CI column.
> The "Your stack" column needs the mod list and versions from the real instance.

| Mod | Version tested | CI | Your stack | Notes |
|---|---|---|---|---|
| Sodium | latest per MC version (from Modrinth) | Untested | Untested | Cosmetics go through vanilla vertex consumers, never wrapped, so Sodium's `VertexBufferWriter` fast path applies. No mixins into anything Sodium replaces. |
| Iris + Complementary Reimagined | latest | Untested | Untested | Vanilla entity render types only, so Iris swaps in its entity programs. Emissive parts use `eyes`, which shaderpacks treat as glowing. Shadows are on by default, with `renderInShadowPass` to turn them off. CI asserts both behaviours. |
| Iris + BSL | latest | Untested | Untested | Same checks as Complementary. |
| Voxy | latest | Untested | Untested | We never touch terrain or world rendering, so nothing interacts. |
| Distant Horizons | latest | Untested | Untested | Same as Voxy. |
| ImmediatelyFast | latest | Untested | Untested | We use the buffer source / submit collector we are given and never flush or hold consumers. |
| Entity Culling | latest | Untested | Untested | We are a render layer, so a culled player skips our layer with the rest of the model. Nothing to integrate. |
| EMF / ETF | latest | Untested | Untested | Cosmetics follow the live transforms of the model's head and body parts, so EMF animations carry them. If a model mod hides or removes those parts, the cosmetic is not drawn. |
| 3D Skin Layers | latest | Untested | Untested | Hats sit 0.5 px above the outer skin layer so the 3D layer doesn't clip through them. |
| Figura | latest (Figura, or NoFigura on newer MC) | Untested | Untested | Defaults to DEFER on every slot for players with a Figura avatar. Set a slot to OVERRIDE to draw anyway. |
| Essential | not in CI | Untested | Untested | Essential has no public API for its cosmetics. Capes that reach the vanilla cape slot are detected (cape slot DEFER). Other Essential cosmetics simply render alongside ours. |

## How the compatibility rules are met

| Rule | How |
|---|---|
| Render as a player feature layer through Minecraft's own pipeline | `CosmeticsLayer` is added to the vanilla player renderers with Fabric API's layer callback (`LivingEntityFeatureRendererRegistrationCallback`, renamed `LivingEntityRenderLayerRegistrationCallback` in 26.x). No raw GL, no global render state. |
| Cache geometry, emit through vanilla vertex consumers | JSON is baked once into flat float arrays (`BakedMesh`). Drawing pushes them into the vanilla `VertexConsumer`: from the buffer source before 1.21.9, and through `SubmitNodeCollector.submitCustomGeometry` from 1.21.9 on. No custom buffers. |
| Emissive parts | `mode: "emissive"` uses the vanilla `eyes` render type. |
| Iris shadow pass | Cosmetics are drawn in the shadow pass by default. `renderInShadowPass: false` skips them there for FPS. Iris is detected through its public API, by reflection, in an isolated class. |
| No terrain/world rendering | The mod has no hooks into terrain, chunks or the level renderer at all. |
| Respect other mods' culling | Nothing is forced to render. A skipped entity skips our layer. |
| Fabric API over mixins; no `@Overwrite`/`@Redirect` | The mod currently has **zero mixins**. The layer, cape override, resource reload and test hooks are all Fabric API events. |
| Only hard dependency is Fabric API | `fabric.mod.json` depends on `fabric-api` and nothing else. |
| Isolated compat classes | `compat/Compat` checks `FabricLoader.isModLoaded` and only then touches `compat/iris/IrisCompat` or `compat/figura/FiguraCompat`. Those use method handles resolved once, so a missing or changed mod can't cause classloading errors. |
| Never override another mod's cape/model unless enabled | `slotPriority` in the config: every slot defaults to `DEFER`. `OVERRIDE` on the cape slot hides the vanilla cape (via Fabric's `ALLOW_CAPE_RENDER`) only for players wearing one of our capes. |
| Crash containment | `RenderGuard`: an exception while drawing a cosmetic disables that cosmetic, logs it once, and the game continues. This also covers deferred drawing on 1.21.9+. F3+T re-enables. |

## How this is tested

`.github/workflows/ci.yml`, using `ci/mcci.py`:

1. **Build**: every Minecraft version in `settings.gradle.kts` is compiled from the one source tree (Stonecutter) and unit-tested.
2. **In-game, every release**: for every Minecraft release from 1.21 to 26.3 (`ci/versions.json`), CI downloads that exact game, Fabric Loader and Fabric API. It starts the **production** client (what players run) on a virtual display and loads a world. The mod's built-in scenario then checks that cosmetics render from the front and the back. CI also fails on:
   - any caught render failure;
   - any fatal mixin error from any mod (runs use `-Dmixin.debug.export=true -Dmixin.debug.verify=true`, and `.mixin.out` is uploaded);
   - any crash report.

   Screenshots of each phase are uploaded.
3. **Compat matrix** (weekly, manual, on `main`, or a commit message containing `[compat]`): the newest release of each build runs with each stack in `ci/compat-sets.json`. With shaderpacks it also checks that:
   - cosmetics are drawn in the Iris shadow pass;
   - turning `renderInShadowPass` off removes them from the shadow pass but not from the main pass.
4. **Benchmark**: 50x cosmetic load, shaders off then on. It reports FPS and the CPU time spent in cosmetics per frame.

The **Test matrix** job writes the results table into the run summary and uploads it as `compat-matrix.md`.

CI renders with Mesa's software OpenGL (llvmpipe). Absolute FPS numbers there mean nothing; compare runs with each other. A shaderpack failing to compile on llvmpipe is an environment limit, not a mod bug. That is one reason the "Your stack" column exists.
