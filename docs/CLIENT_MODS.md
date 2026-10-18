# Client mods: what the client depends on and how it uses them

Hand-off notes, researched 2026-10-14. Paste this into another chat so it knows the third-party mods behind the
client and how each one is wired in. "Unverified" means it was not confirmed from a primary source. Modrinth
itself (site and API) could not be reached during the research (the egress policy blocked it), so build coverage
comes from each mod's own GitHub source (branches, tags, `gradle.properties`, `fabric.mod.json`) plus search
snippets of Modrinth/CurseForge pages. Check anything marked unverified on Modrinth before relying on it.

## 1. Summary

`cosmeticsmod` (Georgwav/Cosmetics-Mod) is a client-side Fabric mod that grew into a client, now **Sol Client**.
- **Right Shift:** the Sol quick menu.
- **The full menu:** its Mods section lists every installed mod in three groups: works with Sol, overlaps, and
  alongside.
- **Each installed mod's row:**
  - a gear for its own settings (through Mod Menu);
  - Controlling: a key button opening the game's Key Binds;
  - Screenshot Viewer: a camera button opening its screen.
- **Iris's row:** the Shaders switch.
- **Header button:** opens Mod Menu's list.
- **Wording:** this note was written for the earlier "hub", a sphere of orbs. Below, a mod's "orb" now means its row
  in the Mods section. One Stonecutter source tree
builds 10 jars: 1.21.1 (also runs 1.21), 1.21.3 (1.21.2), 1.21.4, 1.21.5, 1.21.8 (1.21.6–1.21.7), 1.21.10 (1.21.9),
1.21.11, 26.1.2 (26.1–26.1.1), 26.2 and 26.3. Fabric API is the only hard dependency.

**Dependency policy (Georg's choice):** features should mostly come from existing mods, used as dependencies and
never bundled into our jar. They are listed under `suggests` in `fabric.mod.json`, so that the launcher
(Threadrinth, a Modrinth App fork) can install them and keep them updated. A missing mod never stops the game. The
Mods section only lists installed mods. A row's gear opens the mod's own settings through Mod Menu
(`ModScreens.openSettings`). Screenshot Viewer has its own screen opener.

**Performance pack (Georg's pick):** Lithium (`lithium`), FerriteCore (`ferritecore`), ImmediatelyFast
(`immediatelyfast`), Entity Culling (`entityculling`) and ModernFix (`modernfix`) are in `suggests` too. The Mods
page offers whichever are missing through the mod browser.

**Not suggested any more:** Freelook and Chat Patches (Sol's Camera and Sol Chat do their jobs). Zoomify, Chat Heads,
Shulker Box Tooltip, Controlling and Screenshot Viewer stay: Sol has no zoom, chat faces, shulker previews, key
search or screenshot gallery of its own.

Fabric Loader does nothing with `suggests` (it is informational only). Threadrinth has to turn each mod id into a
Modrinth project, and for most of these mods the id is **not** the slug (`chat_heads` → `chat-heads`). The table
below gives the mapping.

## 2. Mods

The integration column was written for the hub: its group names (`modules`, `inventory`, `chat`, `tools`) were
positions on the old Mods sphere. In the Sol menu every installed mod is a row of the Mods section. "Gear" means
its row has a gear calling Mod Menu's `getConfigScreen(modId, menu)`. "Targets" refers to our 10 jars.

| # | Mod | Fabric mod id | Modrinth | What it gives the client | Hub integration today | Our 10 targets | Its own deps |
|---|---|---|---|---|---|---|---|
| 1 | Zoomify | `zoomify` | [zoomify](https://modrinth.com/mod/zoomify) | Zoom | modules / `zoom`; gear → Mod Menu config | All 10 (1.21.x frozen at 2.15.2) | Fabric API, YACL, Fabric Language Kotlin; Mod Menu optional |
| 5 | Mouse Tweaks | `mousetweaks` | [mouse-tweaks](https://modrinth.com/mod/mouse-tweaks) | Drag and scroll items | inventory / `mouse`; gear | All 10 (1.21.2 unverified) | Fabric API |
| 6 | AppleSkin | `appleskin` | [appleskin](https://modrinth.com/mod/appleskin) | Hunger and saturation | inventory / `apple`; gear needs Cloth Config | All 10 (1.21.2 unverified) | Fabric API; Cloth Config for its settings |
| 7 | Shulker Box Tooltip | `shulkerboxtooltip` | [shulkerboxtooltip](https://modrinth.com/mod/shulkerboxtooltip) | Shulker box contents | inventory / `box`; gear | All 10 (1.21.2 unverified) | Fabric API; Mod Menu optional |
| 8 | Inventory Profiles Next | `inventoryprofilesnext` | [inventory-profiles-next](https://modrinth.com/mod/inventory-profiles-next) | Sorting, refill, locked slots | inventory / `sort`; gear | Project lists 1.21–1.21.1, 1.21.3–1.21.11, 26.1.x, 26.2, 26.3; per-target files unverified | Fabric API, Fabric Language Kotlin, libIPN |
| 13 | Jade | `jade` | [jade](https://modrinth.com/mod/jade) | What you're looking at | tools / `target`; gear | All 10 | Fabric API |
| 15 | Chat Heads | `chat_heads` | [chat-heads](https://modrinth.com/mod/chat-heads) | Faces next to chat lines | chat / `users`; gear needs Cloth Config | All 10 | None required (not even Fabric API); Cloth Config for settings |
| 16 | No Chat Reports | `nochatreports` | [no-chat-reports](https://modrinth.com/mod/no-chat-reports) | Chat privacy | chat / `shield`; gear needs Cloth Config | All 10 (1.21.2, 1.21.8 builds unverified) | Fabric API; Cloth Config for settings |
| 17 | Screenshot Viewer | `screenshot_viewer` | [screenshot-viewer](https://modrinth.com/mod/screenshot-viewer) | Screenshot browser | **Main** sphere "Screenshots" orb (`camera`, only when installed), opens its screen directly | 1.21–1.21.4, 1.21.11, 26.1.x, 26.2; **none found for 1.21.5–1.21.10 or 26.3** | Fabric API on ≤1.21.11 builds; none on 26.x |
| 18 | Controlling | `controlling` | [controlling](https://modrinth.com/mod/controlling) | Search in Key Binds | tools / `keys`; its button opens the game's Key Binds screen | All 10 | Fabric API, Searchables |
| 19 | BetterF3 | `betterf3` | [betterf3](https://modrinth.com/mod/betterf3) | Tidier F3 screen | tools / `debug`; gear | 1.21–1.21.11 and 26.1.x, 26.2 yes (1.21.2–1.21.4 unverified); **26.3 not found** | Cloth Config (whether it is required is unverified); Fabric API likely |
| 25 | Xaero's Minimap | `xaerominimap` (unverified) | [xaeros-minimap](https://modrinth.com/mod/xaeros-minimap) | Minimap, waypoints | tools / `map`; gear (unverified that it has a Mod Menu screen) | 1.21.x, 26.1.2, 26.2 yes; 26.3 on CurseForge, Modrinth unconfirmed | Fabric API (unverified) |
| 26 | Simple Voice Chat | `voicechat` | [simple-voice-chat](https://modrinth.com/mod/simple-voice-chat) | Proximity voice chat | tools / `mic`; gear | Current builds for 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.x, 26.2.x, 26.3.x (see notes) | None required (bundles the Fabric API modules it uses); Cloth Config optional |
| – | Mod Menu | `modmenu` | [modmenu](https://modrinth.com/mod/modmenu) | Opens other mods' settings; our "Configure" entry | Mods page header button → its mod list; every gear uses it | All 10 (our CI ran 11.0.5 → 21.0.0) | Fabric API |
| – | Fabric API | `fabric-api` | [fabric-api](https://modrinth.com/mod/fabric-api) | Required | – | All 10 | – |
| – | Iris (+ Sodium) | `iris`, `sodium` | [iris](https://modrinth.com/mod/iris), [sodium](https://modrinth.com/mod/sodium) | Shaders | "Shaders" toggle orb on Main and Mods (`render`) spheres. **Neither is in `suggests`** | All 10 (our CI passes on every line) | Iris needs Sodium |

Authors and licenses (from each mod's `fabric.mod.json` unless noted): Zoomify isXander, LGPL-3.0 · Freelook
Celibistrial, AGPL-3.0 · Mouse Tweaks YaLTeR, BSD-3-Clause · AppleSkin squeek502, Unlicense · Shulker Box Tooltip
MisterPeModder, MIT · IPN blackd (Plamen Kosseff), AGPL-3.0 (GitHub repo) · Jade Snownee, CC-BY-NC-SA-4.0 · Chat
Patches OBro1961, LGPL-3.0 · Chat Heads dzwdz and Fourmisain, MPL-2.0 · No Chat Reports Aizistral, WTFPL ·
Screenshot Viewer LGatodu47, MIT · Controlling Jaredlll08, MIT · BetterF3 cominixo and TreyRuffy, MIT · Xaero's
Minimap xaero96, closed source, licence unverified · Simple Voice Chat Max Henkel, "All Rights Reserved" in
`fabric.mod.json` · Mod Menu TerraformersMC, MIT.

Stable Modrinth project ids, where the mods' build files gave them: Zoomify `w7ThoJFB`, Chat Patches `MOqt4Z5n`,
Chat Heads `Wb5oqrBJ`, Shulker Box Tooltip `2M01OLQq`, Controlling `xv94TkTM`, BetterF3 `8shC1gFX`, Simple Voice
Chat `9eGKb6K1`, Xaero's Minimap `1bokaNcj`.

## 3. Per-mod notes

- **Zoomify:** Zoom is C and secondary zoom is F6. Scroll zoom in/out is unbound. C clashes with vanilla's "Save
  Hotbar Activator". The 1.21.x builds stopped at 2.15.2 (Feb 2026), with one jar per range: 1.21–1.21.1,
  1.21.2–1.21.5, 1.21.6–1.21.8, 1.21.9–1.21.10, 1.21.11. 26.x is on 2.16.3/2.17.0. There is no public API, but
  state can be read by reflection: `dev.isxander.zoomify.Zoomify.INSTANCE.getZooming()` and
  `getZoomDivisor(float)` (it is a Kotlin object).
- **Freelook:** No longer suggested: Sol's Camera module has freelook (Left Alt). If Freelook (Celibistrial's,
  id `freelook`) or Perspective is installed anyway, its row on the Mods page decides who does it (Sol or the mod).
- **Mouse Tweaks:** Mouse only, no keys. Its git history shows ports for 1.21.6, 1.21.9, 1.21.11, 26.1, 26.2 and
  26.3, plus a 1.21.1 branch, and Modrinth lists 1.21.3–1.21.11.
- **AppleSkin:** No keys. Its settings screen exists only with Cloth Config; otherwise its factory throws, and our
  `openModSettings` catches that and shows "This mod has no settings screen". API: an `appleskin` entrypoint
  (`squeek.appleskin.api.AppleSkinApi`). `HUDOverlayEvent` subclasses have mutable `x`/`y`, which could keep
  AppleSkin's overlay on the hunger and health bars when our HUD editor moves them (test in game whether it's
  needed). The 1.21.3 build covers 1.21.3–1.21.4.
- **Shulker Box Tooltip:** It has its own settings screen (no Cloth). It works client-only, and the server can
  also install it (environment `*`). API: a `shulkerboxtooltip` entrypoint (`ShulkerBoxTooltipApi`). Release
  commits exist for 1.21.5, 1.21.6, 1.21.7, 1.21.9, 1.21.10 and 1.21.11, plus 26.1.x, 26.2.x and 26.3.x branches.
  Default preview keys (Shift and Alt) are from memory, so check them against Freelook's Left Alt.
- **Inventory Profiles Next:** The GitHub repo (blackd/Inventory-Profiles) is archived and development moved to
  Codeberg, which could not be reached. The id `inventoryprofilesnext` is confirmed in its `ModInfo.kt`. Its sort
  buttons sit on inventory screens; its hotkeys were not checked. The Modrinth fabric-1.21.11-2.2.6 page lists
  libIPN, Fabric API and Fabric Language Kotlin as required.
- **Jade:** Keys: Numpad 0 config, Numpad 1 show overlay, Numpad 2 liquids, Numpad 3/4 recipes/uses, Numpad 5
  narrate, Left Shift details (from its 26.3 source). API: a `jade` entrypoint (`snownee.jade.api.IWailaPlugin`);
  `IWailaClientRegistration` has before/after-render and tooltip-collected callbacks. Each target has its own
  branch: 1.21 (15.x), 1.21.2 (16.x, 1.21.2–1.21.3), 1.21.4, 1.21.5, 1.21.6 (1.21.6–1.21.8), 1.21.9, 1.21.11,
  26.1, 26.2, 26.3.
- **Chat Patches:** No longer suggested: Sol Chat has the timestamps, copy, stacked repeats and longer history
  Georg picked.
- **Chat Heads:** It has no Fabric API dependency. Without Cloth Config its gear opens a "Cloth Config missing"
  screen. Builds: 0.15.x for 1.21–1.21.1, 1.21.2–1.21.3, 1.21.4, 1.21.5 and 1.21.6–1.21.8; 1.3.2 for 1.21.9+,
  1.21.11, 26.1–26.2 and 26.3.
- **No Chat Reports:** It works client-only and can also go on servers. Its settings screen needs Cloth Config;
  without it Mod Menu gets no screen and we show the "no settings" notice. Builds exist for 1.21–1.21.1 (2.9.x),
  1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.9–1.21.10, 1.21.11, 26.1, 26.2 and 26.3 (2.21.0). Coverage of
  1.21.2 and 1.21.8 by the neighbouring builds is unverified. Requires Java 25 on 26.x.
- **Screenshot Viewer:** The hub reflects `io.github.lgatodu47.screenshot_viewer.screen.manage_screenshots.ManageScreenshotsScreen(Screen)`.
  That class and constructor exist on its 1.21.3, 1.21.4, 1.21.11, 26.1 and master (26.2) branches. It also has a
  Mod Menu screen. Branches: 1.21.3 (`>=1.21.2`), 1.21.4-fabric, 1.21.11-fabric, 26.1-fabric, master (`>=26.2`,
  1.3.7). A 1.21–1.21.1 build (1.3.3) is on Modrinth.
- **Controlling:** It has no Mod Menu screen (no `modmenu` entrypoint). It replaces Options → Controls → Key Binds,
  so an orb should open that vanilla screen. It requires Searchables. Its `modid=controlling` was confirmed in
  `gradle.properties`.
- **BetterF3:** The GitHub repo cominixo/BetterF3 is stale (1.18.2); the id `betterf3` comes from it and was not
  re-read from a current jar. The maintainer is now TreyRuffy. Versions: v11 (1.21–1.21.1), v14 (1.21.5), v15
  (1.21.6–1.21.8), v16 (1.21.9–1.21.10), v17 (1.21.11), v18 (26.1.x), v19.0.0 (26.2). An unofficial "BetterF3
  Fabric Patch" exists on CurseForge.
- **Xaero's Minimap:** It is closed source, so its id is from jar names and memory. The Fair-Play edition has a
  different id (`xaerominimapfair`, unverified). It has no documented public API, so keep the integration to the
  orb. Default keys (from memory, unverified): Y settings, B new waypoint, U waypoint list.
- **Simple Voice Chat:** **The server must have it too**, with its UDP port open (24454 by default). Keys: V voice
  chat menu, M mute, N disable, H hide icons. Push-to-talk is unbound, and its onboarding asks for it. Its Mod Menu
  factory shows onboarding first, then a Cloth Config screen if Cloth is installed, else its own screen. API: a
  `voicechat` entrypoint (`de.maxhenkel.voicechat.api.VoicechatPlugin`; Maven `de.maxhenkel.voicechat:voicechat-api`).
  Each 1.21.x build pins one exact version (`minecraft_dependency=1.21.8`, for example). So 1.21, 1.21.2, 1.21.3,
  1.21.6, 1.21.7 and 1.21.9 only have older Voice Chat releases (unverified), and the client and server versions
  must be compatible.
- **Mod Menu:** We reach it by reflection: `com.terraformersmc.modmenu.ModMenu.getConfigScreen(String, Screen)` and
  `com.terraformersmc.modmenu.gui.ModsScreen(Screen)`. Both exist in 11.0.3 and in current HEAD. HEAD also has
  `ModMenu.hasConfigScreen(String)`, which was also present in 11.0.3.
- **Iris:** We use `net.irisshaders.iris.api.v0.IrisApi` through method handles: `isShaderPackInUse`,
  `isRenderingShadowPass`, and `getConfig().setShadersEnabledAndApply(boolean)` for the Shaders orb. The Voxy orb
  (`voxy`, "later") also sits on both spheres but is not in `suggests`; Voxy has no builds for 1.21.1–1.21.5 or
  26.3 (COMPAT.md).

## 4. Gaps and to-do

Done since this was written: Jade is in `suggests`; Controlling has an orb (Key Binds); a mod's orb shows its
Settings button only when Mod Menu has a settings screen for it (`ModMenu.hasConfigScreen`), so the Cloth Config
case below no longer shows a dead button; the Screenshots orb only shows when Screenshot Viewer is installed.

1. ~~Add `"jade": "*"` to `suggests`.~~ Done.
2. **Iris and Sodium are not in `suggests`**, although the Shaders orb depends on Iris (Iris needs Sodium). Add
   both if the launcher should install them (Voxy is optional).
3. ~~Controlling has no orb.~~ Done: `tools` group, opens the vanilla Key Binds screen.
4. **Settings that need Cloth Config:** AppleSkin, No Chat Reports, Chat Heads and BetterF3 (Voice Chat falls back
   to its own screen). Their Settings button is now hidden without one; suggesting `cloth-config` would bring
   their settings back.
5. **Freelook's settings ignore the parent screen**, so back from them does not return to the hub. Tolerate it, or
   reopen the hub when its screen closes.
6. **Screenshot Viewer:** No build was found for 1.21.5–1.21.10 or 26.3. Its main-sphere orb now only shows when it
   is installed, like the other mods' orbs.
7. **Other coverage holes to watch:** BetterF3 has no 26.3 build. Chat Patches is alpha-only on 1.21.5–1.21.10 and
   unknown on 1.21.2–1.21.4. Voice Chat's current builds pin exact versions. IPN has no confirmed 26.3 file, and
   Xaero has no confirmed 26.3 file on Modrinth.
8. **Xaero Fair-Play edition:** if it should light the map orb too, also check `xaerominimapfair` (verify the id
   first).
9. **Transitive dependencies Threadrinth must install:** YACL and Fabric Language Kotlin (Zoomify), YACL (Chat
   Patches), libIPN and Fabric Language Kotlin (IPN), Searchables (Controlling), Sodium (Iris), and Cloth Config
   (optional, for settings screens). Modrinth's version metadata lists these, so resolving through Modrinth gets
   them; plain `suggests` does not.
10. **Mod id → Modrinth slug mapping is needed by the launcher:** `chat_heads`→`chat-heads`,
    `inventoryprofilesnext`→`inventory-profiles-next`, `mousetweaks`→`mouse-tweaks`,
    `nochatreports`→`no-chat-reports`, `screenshot_viewer`→`screenshot-viewer`, `xaerominimap`→`xaeros-minimap`,
    `voicechat`→`simple-voice-chat`. The others match. How Threadrinth resolves `suggests` is not documented here.
11. **Integration ideas for later:**
    - Read Zoomify's `getZooming()`/`getZoomDivisor()` to hide or scale our crosshair and HUD while zooming.
    - Use Jade's client render callbacks to hide its overlay while the hub or HUD editor is open.
    - Use AppleSkin's `HUDOverlayEvent` x/y to follow a moved hunger or health bar.
    - Use the Voice Chat client API for a "talking/muted" HUD module or a mute toggle on its orb.
    - Check key clashes with our defaults (Right Shift hub): Zoomify C vs vanilla, Freelook Left Alt vs Shulker Box
      Tooltip's Alt.

## 5. Built into the client (do not add mods for these)

- Fullbright, with an optional night-vision effect so it works with shaders.
- Toggle Sprint and Toggle Sneak.
- Weather changer and time changer (client-side only).
- Crosshair editor and block outline colour.
- HUD modules: Keystrokes + CPS, FPS, ping, clock, coordinates + direction + biome, armor/durability, effect timers.
- HUD editor, which also moves the vanilla HUD parts.
- Skin changer: Threadrinth's saved skins → Mojang upload.
- Main folder (`.minecraft` by default, or Threadrinth's app folder, whose saved skins the Skins page shows) and
  profiles.

## 6. Where things live in the repo

- `src/main/resources/fabric.mod.json`: `depends` (Fabric API only), `suggests` (the mod ids above) and the
  `modmenu` entrypoint.
- `src/main/java/io/github/georgwav/cosmetics/sol/ui/menu/ModsSection.java`: the Mods section.
  - `KNOWN`: each known mod's icon and how it gets on with Sol.
  - The overlap choices and the Shaders switch on Iris's row.
  - The "Mod Menu" header button.
- `src/main/java/io/github/georgwav/cosmetics/sol/ui/menu/ModScreens.java`: `openSettings`, `openKeyBinds`,
  `openScreenshots`, `openModList`.
- `src/main/java/io/github/georgwav/cosmetics/compat/Compat.java`: `IRIS`/`FIGURA`/`VOXY`/`MOD_MENU` flags, the
  shader toggle, and the `TRACKED` list used for logging.
- `src/main/java/io/github/georgwav/cosmetics/compat/iris/IrisCompat.java`: Iris API via method handles.
- `src/main/java/io/github/georgwav/cosmetics/compat/modmenu/ModMenuCompat.java`: our own "Configure" screen.
- `src/main/resources/assets/cosmeticsmod/lang/en_us.json`: `sol.mods.<modid>.note` row notes and the
  `sol.notify.no_screen` message.
- Built-ins:
  - `client/Modules.java`, `client/modules/` (Fullbright, ToggleSprint, Weather/TimeChanger, Crosshair,
    BlockOutline).
  - `client/hud/` (HUD modules, `VanillaHud`).
  - `sol/ui/screens/HudEditor.java` (snapping in `HudSnap`).
  - `client/skins/` (with `MojangCapes`), `sol/ui/wardrobe/SkinsView.java`.
  - `client/MainFolder.java`, `client/Profiles.java`.
- `COMPAT.md` (CI matrix: Sodium, Iris, Voxy, DH, Mod Menu ...), `ci/compat-sets.json`, `ci/versions.json`.
