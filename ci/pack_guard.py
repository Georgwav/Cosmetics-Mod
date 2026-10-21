#!/usr/bin/env python3
"""No knife pack in the repo, CI or the jar.

The knife packs (Delta Force, Valorant, CS2) are all rights reserved: players drop them in themselves, and CI uses a
stand-in knife the mod generates. This fails when a file in the gunpack layout (TACZ / LR Tactical: display/melee,
geo_models, animations/melee, tacz_sounds, Lua scripts, a gunpack's meta file), or under one of the packs' namespaces
(delta_wt, wtyj, cs2_wt), is tracked in git outside test fixtures, sits inside a tracked zip, or is inside a built jar.

  python3 ci/pack_guard.py [--jars JAR ...]
"""
import re
import subprocess
import sys
import zipfile

NAMESPACES = {"delta_wt", "wtyj", "cs2_wt"}
LAYOUT = re.compile(r"(^|/)(display/(melee|guns|ammo|attachments)/|geo_models/|animations/melee/|tacz_sounds/)")
META = re.compile(r"(^|/)(gunpack_info|gunpack\.meta|gunpack)\.json$")
FIXTURES = ("src/test/",)


def why(path):
    """Why a path is a pack file, or None."""
    p = path.replace("\\", "/")
    if set(p.lower().split("/")) & NAMESPACES:
        return "a knife pack's namespace"
    if LAYOUT.search(p):
        return "the gunpack layout"
    if META.search(p):
        return "a gunpack's meta file"
    if p.endswith(".lua"):
        return "a pack's Lua script"
    return None


def main(argv):
    jars = argv[argv.index("--jars") + 1:] if "--jars" in argv else []
    found = []
    tracked = subprocess.run(["git", "ls-files", "-z"], capture_output=True, check=True).stdout.decode().split("\0")
    for path in filter(None, tracked):
        if path.startswith(FIXTURES):
            continue
        reason = why(path)
        if reason:
            found.append(f"{path}: {reason}")
        elif path.endswith((".zip", ".jar")) and not path.startswith("gradle/wrapper/"):
            try:
                with zipfile.ZipFile(path) as z:
                    for name in z.namelist():
                        r = why(name)
                        if r:
                            found.append(f"{path} holds {name}: {r}")
            except (zipfile.BadZipFile, OSError) as e:
                found.append(f"{path}: can't be read as a zip ({e})")
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                r = why(name)
                if r:
                    found.append(f"{jar} holds {name}: {r}")
                elif name.startswith("solknives/"):
                    found.append(f"{jar} holds {name}: a personal build's bundled knives")
    if found:
        print("Knife pack files where they mustn't be (the packs are all rights reserved):")
        for f in found:
            print("  " + f)
        return 1
    print(f"No knife pack files ({len(tracked)} tracked files and {len(jars)} jar(s) checked).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
