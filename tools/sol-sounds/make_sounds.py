#!/usr/bin/env python3
"""Sol's own sound packs (C.3): Tick, Glass, Thock, Chime, Pop and Zap, each with hit, crit, kill, pop, click and
toggle sounds, and the world's quiet sounds (snow, rainbow, leaves, ripple, sparkle), synthesised here from sine waves, noise and envelopes (nothing recorded, nothing taken from anywhere).

Writes src/main/resources/assets/cosmeticsmod/sounds/sol/<pack>/<event>.ogg and the matching entries of
assets/cosmeticsmod/sounds.json (ids cosmeticsmod:sol.<pack>.<event>). The waves are made with the standard library;
ffmpeg (with libvorbis) encodes them, as the game plays Ogg Vorbis. Run from the repository root:
python3 tools/sol-sounds/make_sounds.py
"""

import json
import math
import random
import shutil
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets/cosmeticsmod"
RATE = 44100
PACKS = ["tick", "glass", "thock", "chime", "pop", "zap"]
EVENTS = ["hit", "crit", "kill", "pop", "click", "toggle"]


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def mix(into, sound, at=0.0, gain=1.0):
    start = int(at * RATE)
    need = start + len(sound)
    if need > len(into):
        into.extend([0.0] * (need - len(into)))
    for i, s in enumerate(sound):
        into[start + i] += s * gain
    return into


def env(n, attack, decay):
    """An attack then an exponential decay, n samples long."""
    a = max(1, int(attack * RATE))
    out = []
    for i in range(n):
        if i < a:
            out.append(i / a)
        else:
            out.append(math.exp(-(i - a) / (decay * RATE)))
    return out


def tone(freq, seconds, attack=0.002, decay=0.08, glide=0.0, partials=((1.0, 1.0),), shape="sine"):
    """A tone at freq (gliding by glide octaves over its length), with partials (ratio, gain)."""
    n = int(RATE * seconds)
    e = env(n, attack, decay)
    out = []
    phase = [0.0] * len(partials)
    for i in range(n):
        t = i / n
        f = freq * (2.0 ** (glide * t))
        s = 0.0
        for k, (ratio, gain) in enumerate(partials):
            phase[k] += 2 * math.pi * f * ratio / RATE
            p = phase[k]
            if shape == "sine":
                v = math.sin(p)
            elif shape == "square":
                v = 1.0 if math.sin(p) >= 0 else -1.0
            else:  # saw
                v = 2.0 * ((p / (2 * math.pi)) % 1.0) - 1.0
            s += v * gain
        out.append(s * e[i])
    return out


def noise(seconds, attack=0.001, decay=0.02, low=0.0, seed=1):
    """A burst of noise; low (0..1) smooths it toward a thump."""
    rnd = random.Random(seed)
    n = int(RATE * seconds)
    e = env(n, attack, decay)
    out, prev = [], 0.0
    for i in range(n):
        v = rnd.uniform(-1.0, 1.0)
        prev = prev * low + v * (1.0 - low)
        out.append(prev * e[i])
    return out


def normalise(sound, peak=0.8):
    m = max(1e-9, max(abs(s) for s in sound))
    sound = [s * peak / m for s in sound]
    # A short fade at the end, so nothing clicks.
    fade = min(len(sound), int(0.004 * RATE))
    for i in range(fade):
        sound[-1 - i] *= i / fade
    return sound


# ---------------------------------------------------------------------------------------------------------- the packs

def tick(event):
    if event == "hit":
        return mix(noise(0.05, decay=0.006, seed=2), tone(3200, 0.04, decay=0.008), gain=0.5)
    if event == "crit":
        s = mix(noise(0.05, decay=0.006, seed=3), tone(4200, 0.05, decay=0.01), gain=0.6)
        return mix(s, tone(5600, 0.04, decay=0.008), at=0.03, gain=0.5)
    if event == "kill":
        s = mix(noise(0.05, decay=0.006, seed=4), tone(3000, 0.05, decay=0.01), gain=0.5)
        s = mix(s, mix(noise(0.05, decay=0.006, seed=5), tone(3800, 0.05, decay=0.01), gain=0.5), at=0.07)
        return mix(s, tone(140, 0.2, decay=0.05, glide=-0.6), at=0.0, gain=0.7)
    if event == "pop":
        return mix(noise(0.08, decay=0.012, seed=6, low=0.4), tone(1800, 0.06, decay=0.015, glide=0.5), gain=0.6)
    if event == "click":
        return mix(noise(0.03, decay=0.003, seed=7), tone(4800, 0.02, decay=0.004), gain=0.4)
    return mix(tone(2600, 0.04, decay=0.008), tone(3400, 0.04, decay=0.008), at=0.045)


def glass(event):
    ring = ((1.0, 1.0), (2.32, 0.5), (4.25, 0.25), (6.63, 0.12))
    if event == "hit":
        return tone(1900, 0.35, decay=0.08, partials=ring)
    if event == "crit":
        return mix(tone(2400, 0.4, decay=0.09, partials=ring), tone(3600, 0.3, decay=0.07, partials=ring), at=0.05, gain=0.6)
    if event == "kill":
        s = tone(1600, 0.6, decay=0.16, partials=ring)
        s = mix(s, tone(2400, 0.5, decay=0.14, partials=ring), at=0.08, gain=0.7)
        return mix(s, tone(3200, 0.5, decay=0.12, partials=ring), at=0.16, gain=0.5)
    if event == "pop":
        return mix(noise(0.2, decay=0.03, seed=8), tone(2800, 0.3, decay=0.06, partials=ring), gain=0.7)
    if event == "click":
        return tone(3000, 0.12, decay=0.025, partials=ring)
    return mix(tone(2200, 0.15, decay=0.035, partials=ring), tone(2900, 0.15, decay=0.035, partials=ring), at=0.06)


def thock(event):
    body = ((1.0, 1.0), (2.1, 0.3))
    if event == "hit":
        return mix(tone(190, 0.14, decay=0.03, glide=-0.5, partials=body), noise(0.06, decay=0.008, low=0.6, seed=9), gain=0.5)
    if event == "crit":
        s = mix(tone(240, 0.16, decay=0.035, glide=-0.6, partials=body), noise(0.06, decay=0.01, low=0.5, seed=10), gain=0.6)
        return mix(s, tone(320, 0.1, decay=0.02, glide=-0.4, partials=body), at=0.06, gain=0.6)
    if event == "kill":
        s = tone(110, 0.35, decay=0.09, glide=-0.8, partials=body)
        return mix(s, noise(0.12, decay=0.02, low=0.7, seed=11), gain=0.6)
    if event == "pop":
        return mix(tone(260, 0.12, decay=0.025, glide=0.6, partials=body), noise(0.05, decay=0.006, low=0.5, seed=12), gain=0.4)
    if event == "click":
        return mix(tone(320, 0.06, decay=0.012, glide=-0.4, partials=body), noise(0.03, decay=0.004, low=0.5, seed=13), gain=0.5)
    return mix(tone(260, 0.08, decay=0.015, glide=-0.3, partials=body), tone(340, 0.08, decay=0.015, glide=-0.3, partials=body),
               at=0.06)


def chime(event):
    bell = ((1.0, 1.0), (2.76, 0.45), (5.4, 0.2), (8.93, 0.08))
    if event == "hit":
        return tone(1320, 0.45, decay=0.12, partials=bell)
    if event == "crit":
        return mix(tone(1320, 0.5, decay=0.13, partials=bell), tone(1980, 0.45, decay=0.12, partials=bell), at=0.06, gain=0.7)
    if event == "kill":
        s = tone(880, 0.9, decay=0.25, partials=bell)
        s = mix(s, tone(1320, 0.8, decay=0.22, partials=bell), at=0.1, gain=0.7)
        return mix(s, tone(1760, 0.7, decay=0.2, partials=bell), at=0.2, gain=0.6)
    if event == "pop":
        return mix(tone(1760, 0.4, decay=0.1, partials=bell), tone(2640, 0.3, decay=0.08, partials=bell), at=0.04, gain=0.5)
    if event == "click":
        return tone(2640, 0.15, decay=0.03, partials=bell)
    return mix(tone(1568, 0.25, decay=0.06, partials=bell), tone(2093, 0.25, decay=0.06, partials=bell), at=0.07)


def pop(event):
    if event == "hit":
        return tone(420, 0.08, decay=0.02, glide=1.4)
    if event == "crit":
        return mix(tone(520, 0.09, decay=0.02, glide=1.6), tone(780, 0.08, decay=0.018, glide=1.6), at=0.05, gain=0.8)
    if event == "kill":
        s = tone(360, 0.1, decay=0.025, glide=1.5)
        s = mix(s, tone(480, 0.1, decay=0.025, glide=1.5), at=0.07)
        return mix(s, tone(640, 0.12, decay=0.03, glide=1.5), at=0.14)
    if event == "pop":
        return mix(tone(300, 0.12, decay=0.03, glide=2.0), noise(0.04, decay=0.005, seed=14), gain=0.3)
    if event == "click":
        return tone(700, 0.05, decay=0.01, glide=1.0)
    return mix(tone(500, 0.06, decay=0.012, glide=1.0), tone(650, 0.06, decay=0.012, glide=1.0), at=0.06)


def zap(event):
    buzz = ((1.0, 1.0), (1.5, 0.4))
    if event == "hit":
        return mix(tone(1400, 0.1, decay=0.03, glide=-1.5, partials=buzz, shape="saw"), noise(0.08, decay=0.015, seed=15), gain=0.4)
    if event == "crit":
        s = mix(tone(1800, 0.12, decay=0.035, glide=-1.8, partials=buzz, shape="saw"), noise(0.1, decay=0.02, seed=16), gain=0.5)
        return mix(s, tone(2400, 0.08, decay=0.02, glide=-1.5, partials=buzz, shape="square"), at=0.05, gain=0.4)
    if event == "kill":
        s = tone(1200, 0.45, decay=0.12, glide=-3.0, partials=buzz, shape="saw")
        return mix(s, noise(0.4, decay=0.08, seed=17), gain=0.5)
    if event == "pop":
        return mix(tone(900, 0.12, decay=0.03, glide=1.2, partials=buzz, shape="square"), noise(0.06, decay=0.01, seed=18), gain=0.3)
    if event == "click":
        return tone(2600, 0.04, decay=0.008, glide=-1.0, shape="square")
    return mix(tone(1500, 0.06, decay=0.012, glide=-1.0, shape="square"), tone(2000, 0.06, decay=0.012, glide=-1.0, shape="square"),
               at=0.06)


MAKERS = {"tick": tick, "glass": glass, "thock": thock, "chime": chime, "pop": pop, "zap": zap}

# The world's quiet sounds (World FX): footsteps in snow, a rainbow coming out, leaves in the wind, rain ripples, and
# the sparkle of a block of stars breaking. Ids cosmeticsmod:sol.world.<name>.
WORLD = ["snow", "rainbow", "leaves", "ripple", "sparkle"]


def crackle(seconds, grains, seed, low=0.55, decay=0.05):
    """Noise made of small grains (snow packing down): bursts at random moments under one envelope."""
    rnd = random.Random(seed)
    out = silence(seconds)
    for _ in range(grains):
        at = rnd.uniform(0.0, seconds * 0.6)
        mix(out, noise(0.03, decay=0.004, low=low, seed=rnd.randrange(1 << 30)), at=at, gain=rnd.uniform(0.3, 1.0))
    e = env(len(out), 0.01, decay)
    return [v * e[i] for i, v in enumerate(out)]


def world(name):
    bell = ((1.0, 1.0), (2.76, 0.35), (5.4, 0.12))
    if name == "snow":
        return crackle(0.22, 14, seed=31)
    if name == "rainbow":
        s = tone(1047, 1.2, attack=0.03, decay=0.35, partials=bell)
        s = mix(s, tone(1319, 1.1, attack=0.03, decay=0.32, partials=bell), at=0.12, gain=0.8)
        return mix(s, tone(1568, 1.0, attack=0.03, decay=0.3, partials=bell), at=0.24, gain=0.7)
    if name == "leaves":
        rnd = random.Random(32)
        out = silence(0.7)
        for k in range(10):
            mix(out, noise(0.12, attack=0.03, decay=0.05, low=0.35, seed=rnd.randrange(1 << 30)), at=k * 0.05, gain=0.5)
        e = env(len(out), 0.2, 0.18)
        return [v * e[i] for i, v in enumerate(out)]
    if name == "ripple":
        return mix(tone(2100, 0.14, decay=0.03, glide=-0.8), noise(0.03, decay=0.004, low=0.5, seed=33), gain=0.25)
    # sparkle
    s = silence(0.6)
    rnd = random.Random(34)
    for k in range(5):
        mix(s, tone(rnd.uniform(2600, 4200), 0.25, decay=0.06, partials=bell), at=k * 0.07, gain=0.6)
    return s


def write_wav(path, sound):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in sound))


def main():
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise SystemExit("ffmpeg (with libvorbis) is needed to encode the sounds")
    out_dir = RES / "sounds/sol"
    sounds_json = RES / "sounds.json"
    table = json.loads(sounds_json.read_text()) if sounds_json.exists() else {}
    with tempfile.TemporaryDirectory() as tmp:
        for pack in PACKS:
            (out_dir / pack).mkdir(parents=True, exist_ok=True)
            for event in EVENTS:
                sound = normalise(MAKERS[pack](event) + silence(0.02))
                wav = Path(tmp) / f"{pack}_{event}.wav"
                write_wav(wav, sound)
                ogg = out_dir / pack / f"{event}.ogg"
                subprocess.run([ffmpeg, "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "3",
                                "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(ogg)], check=True)
                table[f"sol.{pack}.{event}"] = {"sounds": [f"cosmeticsmod:sol/{pack}/{event}"]}
        (out_dir / "world").mkdir(parents=True, exist_ok=True)
        for name in WORLD:
            sound = normalise(world(name) + silence(0.02), peak=0.6)
            wav = Path(tmp) / f"world_{name}.wav"
            write_wav(wav, sound)
            ogg = out_dir / "world" / f"{name}.ogg"
            subprocess.run([ffmpeg, "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "3",
                            "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(ogg)], check=True)
            table[f"sol.world.{name}"] = {"sounds": [f"cosmeticsmod:sol/world/{name}"]}
    sounds_json.write_text(json.dumps(dict(sorted(table.items())), indent=2) + "\n")
    print("wrote", len(PACKS) * len(EVENTS) + len(WORLD), "sounds")


if __name__ == "__main__":
    main()
