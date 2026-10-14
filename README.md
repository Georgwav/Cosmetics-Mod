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
  in Options → Controls, or in the mod's own settings (Display → Keys). Both are the same key bind, so changing
  it in one place changes it in the other; the same goes for the "Emote wheel" key.

When a resource pack has cosmetics that couldn't load (a broken file, or a model over `maxMeshTriangles`), the
Wardrobe says how many and why at the top; when an imported pack is from an older importer than the mod's fixes
need, it says to import it again.

It is a flat, minimal screen with a live preview of your player (drag it to turn and tilt it; your head looks
at the mouse) and four tabs. The settings tabs have a search field at the top: type "cape" or "elytra" and only
those settings show (a section's name finds all of it):

| Tab | What's there |
|---|---|
| Wardrobe | Every loaded cosmetic as a tile with its own picture of the cosmetic alone (no player), centred and turned three quarters towards you; the one you point at turns, and shows on you in the preview. Click a tile to put it on or take it off (what you wear is outlined). The gear in a tile's corner opens that cosmetic's own page: tint colour or rainbow (with speed), "Colouring" (how a picked colour or rainbow goes in: Automatic, the default, colours white cosmetics all over and changes the colour of coloured ones; Whole cosmetic: white becomes the colour, grey a darker shade, black stays black; Coloured parts: coloured parts take the colour with their shading, white, grey and black stay; Classic tint: the colour multiplies, as the game tints, so dark parts and opposite colours turn black), saturation (of the tint, or of the texture itself when it has no tint: 0% is black and white), hue (turns the texture's own colours around the colour wheel: red wings blue) and brightness, size, move sideways / up / forward, glow on or off, animation on or off and its speed, its style (for cosmetics with several), "Cloth cloak" for flat capes (it bends and ripples like cloth instead of swinging like a board), particles (on or off, amount, size) for auras and other cosmetics with particles, where a pet sits (left or right shoulder, on your head, or floating beside you), and a reset. Putting one on takes off the one of its kind you wore (new wings replace your wings, not your backpack) unless "Wear several of one kind" is on in Display. A cosmetic can leave some of these out. **Outfits** (in the side bar): save what you wear, with each cosmetic's colours and other settings, so the same cosmetics can make a purple outfit and a green one. Outfits show as tiles with pictures of their cosmetics; point at one to see it on you, click it to wear it (its settings come with it), and the gear opens its page to rename it, update it to what you wear now, or delete it. |
| Display | Master switch; show your own and other players' cosmetics; key binds (Open Cosmetics, Emote wheel: the same ones as in Controls, click and press a key, Escape for none, the arrow for the default); per-slot visibility; glowing parts; see-through parts; hide hats under helmets; hide capes with elytra; elytra close to your back; cape physics and cape swing strength; particles; emotes (on or off, where the camera goes while you emote, whether moving stops an emote). |
| Performance | Render distance; shadows with shaders (Iris) on or off; shadow distance. |
| Compatibility | Per-slot priority (Defer or Override) for slots another mod uses; defer to Figura avatars; detected mods; render-error counter; reload cosmetics. |

The Wardrobe's **Emotes** list plays each emote (you see it in the preview) and picks which ones are on the
emote wheel.

Players' names move up above their hats, halos and other head cosmetics, so they never overlap.

Changes apply immediately and are saved when the screen closes. "Reset settings" restores defaults but
keeps your wardrobe and how each cosmetic looks; each cosmetic's page has its own reset.

### Cosmetics

- **Bundled**:
  - `cosmeticsmod:top_hat`
  - `cosmeticsmod:halo` (glowing, slowly spins and bobs)
  - `cosmeticsmod:explorer_backpack`
  - `cosmeticsmod:feather_wings` (flapping)
  - `cosmeticsmod:glider_wings` (flap faster when moving or flying, fold when sneaking)
  - `cosmeticsmod:crown` (glowing, shimmering gems; made and animated in Blockbench as a Bedrock model)
  - `cosmeticsmod:orbit_ring` (a smooth-shaded gold ring from an OBJ mesh, circling the head)
  - `cosmeticsmod:starlight_cape` (with a glowing emblem)
  - `cosmeticsmod:ember_cape` (an animated texture)
  - `cosmeticsmod:wind_pennant` (a skeletal MD5 flag with two styles, "wave" and "calm")
  - `cosmeticsmod:knight_shield` (a shield cosmetic: shown instead of the vanilla shield while you hold one)
  - `cosmeticsmod:ember_aura`, `cosmeticsmod:frost_aura`, `cosmeticsmod:heart_aura` (particle auras); the starlight cape
    also leaves a sparkle trail while you move
  - `cosmeticsmod:pocket_slime` (a pet: sits on your left shoulder and bobs)
  - `cosmeticsmod:wristband` (on the left arm) and `cosmeticsmod:cloud_boots` (on both feet, puffing little clouds as
    you walk)
- **Add more** from any resource pack at `assets/<ns>/cosmetics/*.json`. Write cubes and meshes by hand,
  model in Blockbench (a Bedrock Entity `.geo.json` with its Bedrock / GeckoLib animations, Molang included,
  or the meshes of a `.bbmodel`), use an `.obj` from Blender, or an animated `.md5mesh` with `.md5anim`
  files. See [docs/COSMETIC_FORMAT.md](docs/COSMETIC_FORMAT.md).
- **Your own cosmetics from other clients**: the [importer](tools/badlion-import/README.md) turns the Badlion,
  Lunar and NoRisk cosmetics you own into a resource pack, for your personal use.

### Emotes

Hold **B** (the "Emote wheel" key; change it in Controls or in Display → Keys, they're the same), point at an emote on the wheel and let go, or tap B and click
one: your player plays it and the camera moves in front of you. The wheel has eight square slots, each with a
picture of its emote's best pose. It starts empty: click a **+** to pick an emote for that slot, the gear on
a slot to change it, and the **×** under the gear to empty it. **+ Page** adds another page of eight (scroll to turn pages); an empty page can be
removed. Walking, jumping, sneaking or getting hurt stops it, as do
attacking, using an item (a rocket while gliding, too) and Stop in the middle of the wheel. Only you see your emotes: nothing is sent to the server.

**Elytra emotes**: press B while gliding with an elytra and the **elytra wheel** opens instead, with its own
emotes. One plays until you land; moving doesn't stop it and the camera stays where it is. While it plays you
are drawn upright to the ground instead of lying along your flight, so you can surf through the sky on a cloud;
your elytra isn't drawn meanwhile and your cape shows instead. During other emotes an elytra stays on your posed
body, as armour does, and it sits close to your back until you spread it to glide (Display → "Elytra close to
your back").
Fill each wheel from the wheel itself, or with "Wheel +/-" and "Elytra +/-" in Wardrobe → Emotes (which puts an
emote in the first empty slot). In the picker the elytra wheel lists the emotes made for flying first.

- **Bundled**: wave, clap, cheer, dance, dab, facepalm, salute, shrug, bow, nod, head shake, spin, T-pose and sit,
  plus Cloud Rider (stand on a cloud and surf) and Cloud Sit for flying. Cloud Rider, Cloud Sit, wave, cheer,
  dance, dab, salute, spin and T-pose are made for the elytra wheel.
- **Add more** from any resource pack at `assets/<ns>/emotes/*.json`: animations exported from Blockbench
  (Bedrock animation files) and Emotecraft emote files both work. See
  [docs/COSMETIC_FORMAT.md](docs/COSMETIC_FORMAT.md#emotes). Emotes you own in another client: the importer's
  `emotes` command finds them anywhere in a folder and writes them as a pack (see
  [tools/badlion-import](tools/badlion-import/README.md#emotes-emotes-from-any-clients-files)).

### Config file (`config/cosmeticsmod.json`)

Everything on the settings screen is stored here. The file can also be edited by hand; bad values are
repaired on load.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch. |
| `showOwnCosmetics` / `showOtherPlayersCosmetics` | `true` | Whose cosmetics are drawn. |
| `slotVisible` | all `true` | Per-slot visibility (`HEAD`, `FACE`, `BODY`, `BACK`, `CAPE`, `SHIELD`, `AURA`, `PET`, `ARM`, `FEET`). |
| `particles` / `particleDensity` / `ownParticlesInFirstPerson` | `true` / `1.0` / `true` | Particle cosmetics (auras, trails) on or off, how many (0 to 2), and your own in first person. |
| `emotes` / `emoteCamera` / `emoteStopOnMove` | `true` / `FRONT` / `true` | Emotes on or off; where the camera goes while you emote (`FRONT`, `BACK`, `UNCHANGED`); whether moving stops an emote. |
| `emoteWheelSlots` / `elytraWheelSlots` | one page of `""` | The slots of the emote wheel and of the elytra wheel (opened while gliding), eight to a page: an emote id, or `""` for an empty slot. Older files' `emoteWheel` / `elytraEmoteWheel` lists are moved into the slots once. |
| `hideHeadwearWithHelmet` | `true` | Hats and face cosmetics hide while something is worn on the head. |
| `severalOfAKind` | `false` | Wear several cosmetics of one kind at once (two hats, two pairs of wings). Off: putting one on in the Wardrobe takes off the one of its kind you wore. |
| `hideCapeWithElytra` | `true` | Capes hide while an elytra is worn. |
| `elytraCloseToBack` | `true` | Your elytra sits close to your back (its lower edge 5 degrees out instead of the game's 15), easing out as you spread it to glide. |
| `capePhysics` / `capeSwing` | `true` / `1.0` | Cape sway on or off, and its strength (0 to 2). Also stops or scales a skeletal cape's own waving (for example imported capes' styles). |
| `wavyCapes` | `true` | Wavy capes, like the Wavey Capes mod: every flat cape cosmetic bends as cloth, and its lower part lags behind the top as you start, stop, jump and land, rippling through. Needs cape physics. The vanilla cape is left alone (Wavey Capes can be installed alongside for that). |
| `emissiveParts` / `translucentParts` | `true` | Draw glowing and see-through parts. |
| `maxRenderDistance` | `64` | Only players within this many blocks get cosmetics (`0` = no limit). |
| `renderInShadowPass` | `true` | Cosmetics cast shadows with Iris shaderpacks. Turn off for FPS. |
| `shadowDistance` | `32` | Only players within this many blocks cast cosmetic shadows (`0` = no limit). |
| `maxMeshTriangles` | `100000` | Most mesh triangles one cosmetic may have (1000 to 200000); wings count once per side. Bigger cosmetics aren't loaded. Applies on resource reload (F3+T). |
| `slotPriority` | `CAPE` `OVERRIDE`, the rest `DEFER` | `DEFER`: if another mod already uses the slot for that player (a vanilla or other mod's cape, a Figura avatar), ours is not drawn. `OVERRIDE`: draw ours anyway; on `CAPE` this also hides the vanilla cape for players wearing one of our capes, so a cape you put on is the one you see. |
| `deferToFigura` | `true` | Players with a Figura avatar don't get our cosmetics in `DEFER` slots. |
| `equipped` | `{"local": []}` | Who wears what: `local` is you, other keys are player UUIDs. |
| `cosmeticOptions` | `{}` | Per-cosmetic settings, keyed by cosmetic id: `color` (RGB number, white = original; left out = the cosmetic's own default colour), `rainbow`, `rainbowSpeed` (0.1 to 5), `saturation` (0 to 1, the tint, or the texture when it has no tint), `hue` (degrees, -180 to 180) and `brightness` (0 to 2) of the texture, `colorStyle` (`WHOLE`, `COLORED_PARTS` or `CLASSIC`; left out = automatic), `scale` (0.5 to 3), `turned`, `offsetX` / `offsetY` / `offsetZ` (pixels, -8 to 8, model axes), `glow`, `animate`, `animationSpeed` (0.1 to 3), `style` (left out = the cosmetic's default), `particles` (on or off), `particleAmount` (0 to 3) and `particleSize` (0.25 to 3) for cosmetics with particles, `petPosition` (`LEFT_SHOULDER`, `RIGHT_SHOULDER`, `HEAD` or `FLOATING`; left out = where the pet's creator put it) for pets; a colour or rainbow also colours their particles. Only cosmetics you changed are listed. Settings a cosmetic doesn't allow are ignored. |
| `savedOutfits` | `[]` | Your outfits, in order: `name`, `cosmetics` (ids) and `options` (each cosmetic's settings as saved, as in `cosmeticOptions`; one left out had its creator's). Outfits from older files (`outfits`, names to ids) are moved here. |

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

### Personal builds (your own cosmetics pack inside the jar)

For your own use only: bundle a resource pack, such as one the [importer](tools/badlion-import/README.md)
writes into your private `cosmetics-personal` repository, into the mod jar.

```
./gradlew build -Pcosmetics.bundleDir=../cosmetics-personal/packs/badlion-import
```

- The pack's `assets/` folder is copied into the jar, where the mod finds `assets/<ns>/cosmetics/*.json` like its
  own cosmetics. The path is relative to this repository's root, or absolute.
- The jars are named `cosmeticsmod-<v>+mc<minecraft>-personal.jar`.
- A pack file at the same path as one of the mod's own files fails the build instead of replacing it.
- Without `-Pcosmetics.bundleDir` the build is exactly the normal one. Normal CI never reads your pack; it only
  checks this mechanism with a tiny made-up pack.

**In GitHub Actions**, the manual "Personal release" workflow (`.github/workflows/personal-release.yml`) builds every
Minecraft version with the pack and publishes the `-personal` jars as a release of this repository. It refuses to
run unless this repository is private (its releases are private too), runs no tests, keeps Gradle's output out of
the log and never saves caches. It needs one secret, `PERSONAL_PACK_TOKEN`:

1. On GitHub: your avatar → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained
   tokens** → **Generate new token**.
2. Name it (for example `cosmetics-personal read`), pick an expiration, and set **Resource owner** to `Georgwav`.
3. **Repository access** → **Only select repositories** → `Georgwav/cosmetics-personal`.
4. **Permissions** → **Repository permissions** → **Contents**: **Read-only** (Metadata read-only is added
   automatically). Nothing else.
5. **Generate token** and copy it.
6. In this repository: **Settings** → **Secrets and variables** → **Actions** → **New repository secret**, name
   `PERSONAL_PACK_TOKEN`, paste the token, **Add secret**.

Then **Actions** → **Personal release** → **Run workflow** (inputs: the pack folder, default `packs/badlion-import`,
and the `cosmetics-personal` branch, default `main`). The jars appear under **Releases**.

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
| `-Dcosmeticsmod.benchmark=true` | Timed, shaders off then on. Each view runs three times: at the stress load, with every cosmetic drawn once, and with the mod switched off, so the frame rates show what cosmetics cost. Add `-Dcosmeticsmod.benchmark.copies=50` to make the stress load every cosmetic 50 times, like a crowded server. |

Locally, `python3 ci/mcci.py runtime --node 1.21.8` does what CI does. You need Xvfb, `xwd` and the jar in
`build/libs`.
