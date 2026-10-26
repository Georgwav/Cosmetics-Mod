# Video to emote

Turns a video of someone dancing (facing the camera, whole body in view) into a Bedrock emote for the client, the way
the bundled Freestyle emote was made.

1. **Track the body** in every frame with MediaPipe's pose landmarker (Python 3.10+):

   ```
   python3 -m venv venv && venv/bin/pip install mediapipe opencv-python-headless
   curl -LO https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task
   venv/bin/python track.py dance.mp4 pose_landmarker_heavy.task pose.json
   ```

2. **Make the emote**, giving the video's length from `ffprobe` (a phone's variable frame rate makes the frame count
   unreliable) and how loud its music should be:

   ```
   ffprobe -v error -show_entries format=duration -of csv=p=0 dance.mp4
   python3 to_emote.py pose.json ../../src/main/resources/assets/cosmeticsmod/emotes/<name>.json <name> <length> 0.35
   ```

   Add `emote.cosmeticsmod.<name>` to `en_us.json` and the name to `Emotes.BUNDLED_ORDER`, and count it in
   `BundledEmotesTest` and `DevScenario.BUNDLED_EMOTES`.

3. **Look at it in the game** next to the video: build the jar, film the emote, and pair the frames.

   ```
   MCCI_WINDOW=960x540 MCCI_SOL_ONLY=none \
   MCCI_JVM_EXTRA="-Dcosmeticsmod.emoteFrames=cosmeticsmod:<name> -Dcosmeticsmod.exitWhenDone=false" \
     python3 ci/mcci.py look --node 26.3 --out /tmp/emote
   venv/bin/python compare.py dance.mp4 /tmp/emote/26.3-sol side-by-side.png 1,2,3,4,5,6,7,8
   ```

## How the pose is read

- **Positions** across the picture come from the tracker's 2D points. Its 3D guess tilts heads and twists torsos that
  are square, so only its depth is used, at 60%: from the front, which is how others see you, the arms then sit where
  the video has them.
- **The hips** turn the whole player (`root`). **The torso** bends and twists at the `waist`, relative to the hips.
  **The head** turns relative to the torso. The dancer's typical torso and head pose counts as upright.
- **Arms and legs** don't bend in Minecraft. A leg points from hip to ankle. An arm points from the shoulder to a point
  on the forearm, nearer the wrist, so a hand folded in to the chest doesn't send a straight arm past the face. Each
  limb takes the shortest swing from hanging down, so it never rolls about its own length.
- **Keyframes** every second frame (15 a second), Catmull-Rom between, as the client plays them.

## The music

The emote names the sound `emote_music:<name>`. Songs from videos are almost never ours to share, so they stay out of
this repository and its releases, like the knife packs. Make a resource pack for yourself instead:

```
mkdir -p music/assets/emote_music/sounds
ffmpeg -i dance.mp4 -vn -c:a libvorbis -q:a 5 music/assets/emote_music/sounds/<name>.ogg
```

with `music/assets/emote_music/sounds.json`:

```json
{"<name>": {"sounds": [{"name": "emote_music:<name>", "stream": true}]}}
```

and `music/pack.mcmeta` (this one loads from 1.21 to 26.x):

```json
{"pack": {"description": "Emote music", "pack_format": 34, "supported_formats": [34, 999], "min_format": 34, "max_format": 999}}
```

Zip it and turn it on in Options › Resource Packs, or put it inside your personal jar with the importer's `bundle`
command or `-Pcosmetics.bundleDir` (see the main README's personal builds).
