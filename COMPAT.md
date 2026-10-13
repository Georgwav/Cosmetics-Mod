# Compatibility matrix

Status values:
- **CI: pass / fail / n/a**: result of the automated in-game run (see [How this is tested](#how-this-is-tested)).
  n/a means the other mod has no build for that Minecraft version, or it cannot run in CI's software
  renderer even without this mod.
- **Your stack**: verified by hand on the maintainer's own instance. A milestone only counts as done once
  this column is filled in.

> Results below are from CI run 9 on main (2026-10-13), newest release of each version line, newest builds
> of each mod from Modrinth (exact versions are in that run's "Test matrix" summary). The "Your stack"
> column still needs the mod list and versions from the real instance.

## Summary

| Mod | CI | Your stack | Notes |
|---|---|---|---|
| Sodium | pass (all 10 version lines) | Untested | Cosmetics go through vanilla vertex consumers, never wrapped, so Sodium's `VertexBufferWriter` fast path applies. No mixins into anything Sodium replaces. |
| Iris + Complementary Reimagined | pass (all 10) | Untested | Vanilla entity render types only, so Iris swaps in its entity programs. Emissive parts use `eyes`, which shaderpacks treat as glowing. CI checks on every version that cosmetics are drawn in the shadow pass, and that `renderInShadowPass: false` removes them from it (and only from it). |
| Iris + BSL | pass (all 10) | Untested | Same checks as Complementary. |
| Voxy | pass (all 5 versions with a Voxy build) | Untested | Passes on 1.21.8, 1.21.10, 1.21.11, 26.1.2 and 26.2 (Voxy 0.2.5-alpha to 0.2.19-beta). There is no Voxy build for 1.21.1–1.21.5 or 26.3. Voxy needs OpenGL 4.6, so CI has Mesa's software renderer report it for this stack. We never touch terrain or world rendering. |
| Distant Horizons | pass (all 10) | Untested | Same as Voxy: no terrain hooks. |
| ImmediatelyFast | pass (all 10, perf stack) | Untested | We use the buffer source / submit collector we are given and never flush or hold consumers. |
| Entity Culling | pass (all 10, perf stack) | Untested | We are a render layer, so a culled player skips our layer with the rest of the model. Nothing to integrate. |
| EMF / ETF | pass (all 10) | Untested | Cosmetics follow the live transforms of the model's head and body parts, so EMF animations carry them. If a model mod hides or removes those parts, the cosmetic is not drawn. |
| 3D Skin Layers | pass (all 10) | Untested | Hats sit 0.5 px above the outer skin layer so the 3D layer doesn't clip through them. |
| Figura | pass (all 10; NoFigura where Figura has no build) | Untested | Defaults to DEFER on every slot for players with a Figura avatar. Set a slot to OVERRIDE to draw anyway. |
| Mod Menu | pass (all 10, Mod Menu 11.0.5 to 21.0.0) | Untested | Optional entry point to the settings screen; also `/cosmetics` and an unbound key. CI opens Mod Menu's list and the screen Mod Menu gets for this mod's "Configure" button, and checks that it draws. |
| Essential | not in CI | Untested | Essential has no public API for its cosmetics. Capes that reach the vanilla cape slot are detected; set the cape slot to DEFER to keep them over ours. Other Essential cosmetics simply render alongside ours. |

## By Minecraft version

Every cell is a real game run: world loaded, cosmetics drawn front and back, all four settings tabs
opened. Shader stacks also check the Iris shadow pass on and off.

| Minecraft | Vanilla (every release in range) | Sodium + Iris + Complementary | Sodium + Iris + BSL | Sodium + ImmediatelyFast + Entity Culling | Voxy | Distant Horizons | EMF + ETF + 3D Skin Layers | Figura |
|---|---|---|---|---|---|---|---|---|
| 1.21.1 (1.21–1.21.1) | pass | pass | pass | pass | n/a (no build) | pass | pass | pass |
| 1.21.3 (1.21.2–1.21.3) | pass | pass | pass | pass | n/a (no build) | pass | pass | pass |
| 1.21.4 | pass | pass | pass | pass | n/a (no build) | pass | pass | pass |
| 1.21.5 | pass | pass | pass | pass | n/a (no build) | pass | pass | pass |
| 1.21.8 (1.21.6–1.21.8) | pass | pass | pass | pass | pass | pass | pass | pass |
| 1.21.10 (1.21.9–1.21.10) | pass | pass | pass | pass | pass | pass | pass | pass |
| 1.21.11 | pass | pass | pass | pass | pass | pass | pass | pass |
| 26.1.2 (26.1–26.1.2) | pass | pass | pass | pass | pass | pass | pass | pass |
| 26.2 | pass | pass | pass | pass | pass | pass | pass | pass |
| 26.3 | pass | pass | pass | pass | n/a (no build) | pass | pass | pass |

Benchmark (50x cosmetic load, CPU time spent in cosmetics per frame, software rendering): 0.15–0.44 ms
with shaders off and 0.72–2.1 ms with Complementary on, across all versions. At normal 1x load that is
roughly 0.003–0.009 ms and 0.014–0.042 ms per frame.

## How the compatibility rules are met

| Rule | How |
|---|---|
| Render as a player feature layer through Minecraft's own pipeline | `CosmeticsLayer` is added to the vanilla player renderers with Fabric API's layer callback (`LivingEntityFeatureRendererRegistrationCallback`, renamed `LivingEntityRenderLayerRegistrationCallback` in 26.x). No raw GL, no global render state. |
| Cache geometry, emit through vanilla vertex consumers | JSON is baked once into flat float arrays (`BakedMesh`). Drawing pushes them into the vanilla `VertexConsumer`: from the buffer source before 1.21.9, and through `SubmitNodeCollector.submitCustomGeometry` from 1.21.9 on. No custom buffers. |
| Emissive parts | `mode: "emissive"` uses the vanilla `eyes` render type. |
| Free-form meshes | Inline, OBJ and Blockbench meshes are baked at load into the same float layout as cubes. Flat-shaded meshes share the cubes' buffer and draw call; smooth-shaded ones are written right after them into the same vertex consumer with per-vertex normals. Same render types and consumers, so Sodium, Iris (including the shadow pass) and ImmediatelyFast see nothing new. |
| Animated textures | A vertical-strip frame is a V offset written with the vertices; a frame file is just another vanilla entity render type for its texture. No texture uploads, no custom atlas. |
| Skeletal (MD5) meshes | Split at load into rigid pieces per joint and a CPU-skinned rest. Rigid pieces are drawn with their joint's transform pushed on the pose stack; skinned vertices are written from a buffer made at load. Same render types and vertex consumers as every other layer, so the Iris shadow pass and batching treat them the same. |
| Iris shadow pass | Cosmetics are drawn in the shadow pass by default. `renderInShadowPass: false` skips them there for FPS. Iris is detected through its public API, by reflection, in an isolated class. |
| No terrain/world rendering | The mod has no hooks into terrain, chunks or the level renderer at all. |
| Respect other mods' culling | Nothing is forced to render. A skipped entity skips our layer. |
| Fabric API over mixins; no `@Overwrite`/`@Redirect` | The layer, cape override, resource reload and test hooks are all Fabric API events. Shield cosmetics need two small `@Inject` mixins per version (Fabric API has no hook for held items): one notes whose hand item the game is resolving (`ItemModelResolver.appendItemLayers` from 1.21.4, `ItemInHandRenderer.renderItem` and, on 1.21.2–1.21.3, `ItemInHandLayer.renderArmWithItem` before), one swaps in the cosmetic where the vanilla shield would be drawn (`ShieldSpecialRenderer`, `BlockEntityWithoutLevelRenderer.renderByItem` before 1.21.4). Both only act for players wearing a shield cosmetic; everything else passes through untouched. |
| Only hard dependency is Fabric API | `fabric.mod.json` depends on `fabric-api` and nothing else. |
| Isolated compat classes | `compat/Compat` checks `FabricLoader.isModLoaded` and only then touches `compat/iris/IrisCompat` or `compat/figura/FiguraCompat`. Those use method handles resolved once, so a missing or changed mod can't cause classloading errors. |
| Never override another mod's model unless enabled | `slotPriority` in the config: every slot except the cape defaults to `DEFER`. The cape slot defaults to `OVERRIDE`, which hides the vanilla cape (via Fabric's `ALLOW_CAPE_RENDER`) only for players wearing one of our capes; set it to `DEFER` to keep vanilla and other mods' capes instead. |
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
