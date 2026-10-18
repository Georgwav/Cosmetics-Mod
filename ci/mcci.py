#!/usr/bin/env python3
"""
Runs the real Minecraft client with this mod, the way players run it, for any Minecraft version.

Used by CI so every supported Minecraft release is tested in-game without anyone launching it by hand:

  runtime   --node 1.21.8          every release that build's jar covers (1.21.6, 1.21.7, 1.21.8), vanilla + Fabric API
  compat    --node 1.21.8          the newest release of that build with each mod stack in compat-sets.json,
                                   then the benchmark (shaders off and on)
  bench-ab  --node 1.21.11 --base A.jar --head B.jar
                                   A/B benchmark: the same scene (Sodium + Iris + Complementary, every
                                   cosmetic 50 times) with two builds of the mod, alternating for several
                                   rounds, reported as medians and ranges per phase
  summarize --runs <dir>           markdown table of every run (GitHub job summary + compat-matrix.md)

For each run it downloads Minecraft, Fabric Loader and Fabric API from the official sources, generates a
small flat world with the matching dedicated server, starts the client on a virtual display (Xvfb, Mesa
software OpenGL), joins the world and lets the mod's built-in scenario (DevScenario) check that the
cosmetics actually render, render in the Iris shadow pass, and respect the shadow-pass option. It then
checks the log for mixin failures, crashes and errors from this mod, and screenshots each phase.

Standard library only.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid
import zipfile
import zlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_META = "https://meta.fabricmc.net/v2"
FABRIC_MAVEN = "https://maven.fabricmc.net/"
MODRINTH = "https://api.modrinth.com/v2"
RESOURCES = "https://resources.download.minecraft.net"
HEADERS = {"User-Agent": "Georgwav/Cosmetics-Mod CI (+https://github.com/Georgwav/Cosmetics-Mod)"}
# Modrinth project ids we never download: Fabric API comes from Fabric's maven, matched to the exact version.
SKIP_PROJECTS = {"P7dR8mSH"}

# Fatal mixin failures (from any mod). Optional mixins that skip themselves only log warnings and are fine.
MIXIN_PROBLEM = re.compile(
    r"Mixin apply (?:for mod \S+ )?failed|InvalidInjectionException|InvalidMixinException|"
    r"MixinTransformerError|Critical injection failure|MixinApplyError|Mixin prepare for mod \S+ failed")
# Startup failures the game never recovers from (26.2+ tries OpenGL, then Vulkan).
STARTUP_FATAL = re.compile(r"Failed to create backend \w+")
OUR_ERROR = re.compile(r"(?:/ERROR|ERROR\]).*CosmeticsMod|CosmeticsMod.*(?:/ERROR|ERROR\])")
# A line of logs/cosmeticsmod.log (ci/log4j2-ci.xml): time, thread, level, our logger, message.
OUR_LINE = re.compile(r"^\[\d\d:\d\d:\d\d\] \[(?P<thread>[^\]]*)/(?P<level>[A-Z]+)\] \(CosmeticsMod\): (?P<msg>.*)$")
# Warnings a CI run is expected to log, each at most once; any other warning or error of ours fails the run.
EXPECTED_ONCE = [re.compile(p) for p in (
    r"^Dev scenario without -D\S*equipAll=true",  # the Sol scenario wears only what it puts on
)]
# Identical lines of ours allowed per run: more means something logs every frame or tick.
REPEAT_LIMIT = 2
LOG = print


# --------------------------------------------------------------------------------------------- downloads

def fetch(url, dest=None, sha1=None, retries=5):
    """Download url (to dest if given, verified against sha1 if given). Returns bytes when dest is None."""
    if dest is not None:
        dest = Path(dest)
        if dest.exists() and (sha1 is None or _sha1(dest) == sha1):
            return dest
        dest.parent.mkdir(parents=True, exist_ok=True)
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()
            if sha1 is not None and hashlib.sha1(data).hexdigest() != sha1:
                raise IOError(f"sha1 mismatch for {url}")
            if dest is None:
                return data
            tmp = dest.with_suffix(dest.suffix + ".part")
            tmp.write_bytes(data)
            tmp.replace(dest)
            return dest
        except Exception as e:  # noqa: BLE001 - network errors of every kind are retried
            last = e
            time.sleep(2 ** attempt)
    raise IOError(f"could not download {url}: {last}")


def fetch_json(url, cache=None):
    if cache is not None and Path(cache).exists():
        return json.loads(Path(cache).read_text())
    data = json.loads(fetch(url))
    if cache is not None:
        Path(cache).parent.mkdir(parents=True, exist_ok=True)
        Path(cache).write_text(json.dumps(data))
    return data


def _sha1(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def maven_path(coordinate):
    parts = coordinate.split(":")
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = f"-{parts[3]}" if len(parts) > 3 else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{classifier}.jar"


def rules_allow(rules):
    """Mojang library/argument rules for a linux x86_64 machine with no special features."""
    if not rules:
        return True
    allowed = False
    for rule in rules:
        os_rule = rule.get("os", {})
        if rule.get("features"):
            matches = False
        else:
            matches = (os_rule.get("name") in (None, "linux")
                       and os_rule.get("arch") in (None, "x86", "x86_64", "amd64"))
        if matches:
            allowed = rule["action"] == "allow"
    return allowed


# --------------------------------------------------------------------------------------------- Minecraft

class Instance:
    """Everything needed to launch one Minecraft version (shared by all runs of that version)."""

    def __init__(self, mc, java, cache):
        self.mc = mc
        self.java = java
        self.cache = Path(cache)
        self.dir = self.cache / "versions" / mc
        self.dir.mkdir(parents=True, exist_ok=True)

        manifest = fetch_json(MOJANG_MANIFEST, self.cache / "version_manifest_v2.json")
        entry = next((v for v in manifest["versions"] if v["id"] == mc), None)
        if entry is None:
            manifest = fetch_json(MOJANG_MANIFEST)  # cached manifest may predate this release
            entry = next(v for v in manifest["versions"] if v["id"] == mc)
        self.version = fetch_json(entry["url"], self.dir / f"{mc}.json")

        loader = read_root_property("deps.fabric_loader")
        self.fabric = fetch_json(f"{FABRIC_META}/versions/loader/{mc}/{loader}/profile/json",
                                 self.dir / f"fabric-{loader}.json")
        self.fabric_api = latest_fabric_api(mc, self.cache)
        self.classpath = self._libraries()
        self.assets_dir, self.asset_index = self._assets()
        self.world = self._world()

    def _libraries(self):
        jars = {}
        lib_dir = self.cache / "libraries"
        jobs = []
        # Fabric first so its copies win over vanilla ones (e.g. ASM).
        for lib in self.fabric["libraries"]:
            path = maven_path(lib["name"])
            key = ":".join(lib["name"].split(":")[:2] + lib["name"].split(":")[3:])
            if key not in jars:
                jars[key] = lib_dir / path
                jobs.append((lib.get("url", FABRIC_MAVEN).rstrip("/") + "/" + path, lib_dir / path, lib.get("sha1")))
        for lib in self.version["libraries"]:
            if not rules_allow(lib.get("rules")):
                continue
            artifact = lib.get("downloads", {}).get("artifact")
            if not artifact:
                continue
            parts = lib["name"].split(":")
            key = ":".join(parts[:2] + parts[3:])
            if key in jars:
                continue
            jars[key] = lib_dir / artifact["path"]
            jobs.append((artifact["url"], lib_dir / artifact["path"], artifact.get("sha1")))
        client = self.version["downloads"]["client"]
        client_jar = self.dir / f"{self.mc}-client.jar"
        jobs.append((client["url"], client_jar, client.get("sha1")))
        with ThreadPoolExecutor(8) as pool:
            list(pool.map(lambda j: fetch(*j), jobs))
        return [str(p) for p in jars.values()] + [str(client_jar)]

    def _assets(self):
        index_info = self.version["assetIndex"]
        assets_dir = self.cache / "assets"
        index_path = assets_dir / "indexes" / f"{index_info['id']}.json"
        fetch(index_info["url"], index_path, index_info.get("sha1"))
        objects = json.loads(index_path.read_text())["objects"]
        jobs = []
        for name, obj in objects.items():
            if name.endswith(".ogg"):
                continue  # sounds are most of the download and not needed to test rendering
            h = obj["hash"]
            jobs.append((f"{RESOURCES}/{h[:2]}/{h}", assets_dir / "objects" / h[:2] / h, h))
        with ThreadPoolExecutor(16) as pool:
            list(pool.map(lambda j: fetch(*j), jobs))
        return assets_dir, index_info["id"]

    def _world(self):
        """A small flat, peaceful world made by the matching dedicated server (works for every version)."""
        world = self.dir / "world"
        if (world / "level.dat").exists():
            return world
        server_dir = self.dir / "server"
        shutil.rmtree(server_dir, ignore_errors=True)
        server_dir.mkdir(parents=True)
        server = self.version["downloads"]["server"]
        fetch(server["url"], server_dir / "server.jar", server.get("sha1"))
        (server_dir / "eula.txt").write_text("eula=true\n")
        (server_dir / "server.properties").write_text("\n".join([
            "level-name=world", "level-type=minecraft\\:flat", "online-mode=false", "difficulty=peaceful",
            "spawn-monsters=false", "generate-structures=false", "view-distance=4", "simulation-distance=4",
            "server-port=25599", "spawn-protection=0", "enable-query=false", "enable-rcon=false", ""]))
        LOG(f"[{self.mc}] generating world with the dedicated server")
        proc = subprocess.Popen([self.java, "-Xmx2G", "-jar", "server.jar", "nogui"], cwd=server_dir,
                                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        deadline = time.time() + 600
        output = []
        for line in proc.stdout:
            output.append(line)
            if "Done (" in line:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
                break
            if time.time() > deadline:
                break
        try:
            proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            proc.kill()
        if not (server_dir / "world" / "level.dat").exists():
            raise RuntimeError(f"[{self.mc}] server did not create a world:\n{''.join(output[-40:])}")
        shutil.move(str(server_dir / "world"), str(world))
        shutil.rmtree(server_dir, ignore_errors=True)
        return world


def latest_fabric_api(mc, cache):
    meta = Path(cache) / "fabric-api-metadata.xml"
    if not meta.exists() or time.time() - meta.stat().st_mtime > 3600:
        meta.parent.mkdir(parents=True, exist_ok=True)
        meta.write_bytes(fetch(FABRIC_MAVEN + "net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"))
    versions = re.findall(r"<version>([^<]+)</version>", meta.read_text())
    matching = [v for v in versions if v.endswith("+" + mc)]
    if not matching:
        raise RuntimeError(f"no Fabric API release for Minecraft {mc}")

    def key(v):
        return tuple(int(x) for x in re.findall(r"\d+", v.split("+")[0]))

    best = max(matching, key=key)
    path = f"net/fabricmc/fabric-api/fabric-api/{best}/fabric-api-{best}.jar"
    jar = fetch(FABRIC_MAVEN + path, Path(cache) / "fabric-api" / f"fabric-api-{best}.jar")
    return best, jar


def read_root_property(key):
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip()
    raise KeyError(key)


# --------------------------------------------------------------------------------------------- Modrinth

def modrinth_versions(project, mc=None, loader="fabric"):
    """All versions of a project for a Minecraft version, newest first (empty if none or unreachable)."""
    params = {"loaders": json.dumps([loader])}
    if mc:
        params["game_versions"] = json.dumps([mc])
    url = f"{MODRINTH}/project/{project}/version?{urllib.parse.urlencode(params)}"
    try:
        return json.loads(fetch(url))
    except IOError:
        return []


def modrinth_version(project, mc=None, loader="fabric", prerelease=False):
    """Newest version of a project for a Minecraft version; releases first unless prerelease is True."""
    versions = modrinth_versions(project, mc, loader)
    if not versions:
        return None
    if prerelease:
        return versions[0]
    releases = [v for v in versions if v.get("version_type") == "release"]
    return (releases or versions)[0]


def modrinth_version_by_id(version_id):
    try:
        return json.loads(fetch(f"{MODRINTH}/version/{version_id}"))
    except IOError:
        return None


def modrinth_download(version, dest_dir):
    files = version["files"]
    file = next((f for f in files if f.get("primary")), files[0])
    dest = Path(dest_dir) / file["filename"]
    fetch(file["url"], dest, file.get("hashes", {}).get("sha1"))
    return dest


def fetch_stack(mods, mc, dest_dir, prerelease=False):
    """
    Downloads a mod stack plus its required dependencies. Returns (stack, missing) where stack maps each
    downloaded jar's file name to {"name", "version"} (the Modrinth version object).

    Versions are resolved first and downloaded at the end, so a dependency that a mod pins to an exact
    version (Modrinth "version_id", e.g. Iris pinning its Sodium) wins over the newest one.
    """
    chosen, names, missing = {}, {}, []
    pins = {}
    queue = list(mods)
    while queue:
        spec = queue.pop(0)
        version, name = None, spec
        for name in spec.split("|"):
            version = modrinth_version(name, mc, prerelease=prerelease)
            if version:
                break
        if version is None:
            missing.append(spec)
            continue
        project = version["project_id"]
        if project in chosen or project in SKIP_PROJECTS:
            continue
        chosen[project] = version
        names[project] = name
        for dep in version.get("dependencies", []):
            dep_project = dep.get("project_id")
            if dep.get("dependency_type") != "required" or dep_project in SKIP_PROJECTS:
                continue
            if dep.get("version_id"):
                pinned = modrinth_version_by_id(dep["version_id"])
                if pinned and mc in pinned.get("game_versions", [mc]):
                    pins[pinned["project_id"]] = pinned
                    dep_project = pinned["project_id"]
            if dep_project and dep_project not in chosen:
                queue.append(dep_project)
    for project, pinned in pins.items():
        if project not in SKIP_PROJECTS:
            chosen[project] = pinned
            names.setdefault(project, project)
    stack = {}
    for project, version in chosen.items():
        stack[modrinth_download(version, dest_dir).name] = {"name": names[project], "version": version}
    return stack, missing


def stack_info(stack):
    """{name: version number} of a downloaded stack, for reports."""
    return {entry["name"]: entry["version"]["version_number"] for entry in stack.values()}


# Fabric Loader's suggested fix when mods need other versions of each other, e.g.
# "Replace mod 'Sodium' (sodium) 0.9.2+mc26.1.2 with version 0.0.0 or any version between 0.9.1- (inclusive) ..."
LOADER_REPLACE = re.compile(r"Replace mod '[^']*' \(([^)\s]+)\) (\S+) with (.+)")
# A version the loader asks for: "0.9.1" (that version) or "0.6.x" (any 0.6 build).
WANTED_VERSION = re.compile(r"(\d+(?:\.\d+)+)(\.x)?")


def jar_mod_id(jar):
    try:
        with zipfile.ZipFile(jar) as z:
            return json.loads(z.read("fabric.mod.json").decode("utf-8", "replace")).get("id")
    except (KeyError, OSError, ValueError, zipfile.BadZipFile):
        return None


def contains_version(text, number, prefix=False):
    """
    True if `number` (e.g. 0.9.1) appears in `text` as a whole version, not inside 10.9.12. With
    `prefix`, any version starting with it matches (0.6 matches 0.6.13 but not 0.61).
    """
    tail = r"\.\d" if prefix else r"(?![\d])(?!\.\d)"
    return re.search(r"(?<![\d.])" + re.escape(number) + tail, text) is not None


def apply_loader_fix(log_text, mods_dir, stack, mc, tried):
    """
    Follows Fabric Loader's "Replace mod ..." suggestions: swaps each named mod for the newest build of
    the same Modrinth project that has the version the loader asks for (or, if no build matches, the
    next older build). Returns a description of the swaps, or None if there was nothing to try.
    """
    swaps = []
    # The same suggestion appears in both logs; follow it once per mod.
    suggestions = {mod_id: (current, wanted) for mod_id, current, wanted in LOADER_REPLACE.findall(log_text)}
    for mod_id, (current, wanted) in suggestions.items():
        jar_name = next((name for name in stack if jar_mod_id(Path(mods_dir) / name) == mod_id), None)
        if jar_name is None:
            continue  # not one of the downloaded mods (e.g. Fabric API or this mod): nothing to swap
        entry = stack[jar_name]
        version = entry["version"]
        tried.add(version["id"])
        builds = modrinth_versions(version["project_id"], mc)
        wanted_versions = [(n, bool(x)) for n, x in WANTED_VERSION.findall(wanted) if n != "0.0.0"]
        matching = [b for b in builds if b["id"] not in tried
                    and any(contains_version(b["version_number"], n, x) for n, x in wanted_versions)]
        matching.sort(key=lambda b: b.get("version_type") != "release")  # stable: newest release first
        current_index = next((i for i, b in enumerate(builds) if b["id"] == version["id"]), -1)
        older = [b for b in builds[current_index + 1:] if b["id"] not in tried]
        replacement = (matching or older or [None])[0]
        if replacement is None:
            continue
        tried.add(replacement["id"])
        (Path(mods_dir) / jar_name).unlink(missing_ok=True)
        new_jar = modrinth_download(replacement, mods_dir)
        del stack[jar_name]
        stack[new_jar.name] = {"name": entry["name"], "version": replacement}
        swaps.append(f"{mod_id} {current} -> {replacement['version_number']}")
    return ", ".join(swaps) or None


INCOMPATIBLE_STACK = "Incompatible mods found"


# --------------------------------------------------------------------------------------------- display

class Display:
    """One Xvfb server for the whole script, reused by every run."""
    _shared = None

    @classmethod
    def get(cls):
        if cls._shared is None or cls._shared.proc.poll() is not None:
            cls._shared = cls()
        return cls._shared

    def __init__(self, number=99, size="1280x720"):
        self.name = f":{number}"
        self.size = size
        self.proc = subprocess.Popen(["Xvfb", self.name, "-screen", "0", f"{size}x24", "-nolisten", "tcp"],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)

    def screenshot(self, png_path):
        """Grabs the whole virtual screen with xwd and converts it to PNG (no extra tools needed)."""
        try:
            data = subprocess.run(["xwd", "-root", "-silent", "-display", self.name],
                                  capture_output=True, timeout=30, check=True).stdout
            width, height, rows = xwd_to_rgb(data)
            write_png(png_path, width, height, rows)
            if os.environ.get("MCCI_LOG_SCREENSHOTS"):
                log_screenshot(Path(png_path).stem, width, height, rows)
            return True
        except Exception as e:  # noqa: BLE001 - screenshots are best effort
            LOG(f"screenshot failed: {e}")
            return False

    def close(self):
        self.proc.terminate()


def xwd_to_rgb(data):
    h = struct.unpack(">25I", data[:100])
    header_size, width, height = h[0], h[4], h[5]
    byte_order, bits_per_pixel, bytes_per_line = h[7], h[11], h[12]
    red_mask, green_mask, blue_mask, ncolors = h[14], h[15], h[16], h[19]
    offset = header_size + ncolors * 12
    if bits_per_pixel != 32:
        raise ValueError(f"unsupported xwd depth {bits_per_pixel}")
    rows = []
    if byte_order == 0 and (red_mask, green_mask, blue_mask) == (0xFF0000, 0xFF00, 0xFF):
        # The usual Xvfb layout: bytes B, G, R, X per pixel. Reorder with slices (fast).
        for y in range(height):
            base = offset + y * bytes_per_line
            px = data[base:base + width * 4]
            row = bytearray(width * 3)
            row[0::3], row[1::3], row[2::3] = px[2::4], px[1::4], px[0::4]
            rows.append(bytes(row))
        return width, height, rows
    fmt = "<I" if byte_order == 0 else ">I"
    shifts = [(m & -m).bit_length() - 1 for m in (red_mask, green_mask, blue_mask)]
    for y in range(height):
        row = bytearray()
        base = offset + y * bytes_per_line
        for x in range(width):
            (p,) = struct.unpack_from(fmt, data, base + x * 4)
            row += bytes(((p & red_mask) >> shifts[0], (p & green_mask) >> shifts[1], (p & blue_mask) >> shifts[2]))
        rows.append(bytes(row))
    return width, height, rows


def png_bytes(width, height, rows):
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + r for r in rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_png(path, width, height, rows):
    Path(path).write_bytes(png_bytes(width, height, rows))


def log_screenshot(name, width, height, rows):
    """
    Prints a small copy of a screenshot into the log as base64 PNG, for when the uploaded artifacts can't
    be downloaded: settings pages scaled to a quarter, in-world shots cropped around the player at half size.
    """
    import base64
    if name.startswith("screenshot-ui"):
        step, x0, y0, x1, y1 = 4, 0, 0, width, height
    elif name.startswith("screenshot-shield"):
        step, x0, y0, x1, y1 = 2, 0, 0, width, height  # first person: the shield is low on the left
    elif name.startswith(("screenshot-elytra_stand", "screenshot-elytra_angle", "screenshot-elytra_cape",
                          "screenshot-emote_elytra")):
        # Zoomed close-ups of the elytra on the back, shoulders to feet (the lower part of the screen): full size,
        # so a gap of a pixel shows.
        crop_w, crop_h = min(480, width), min(400, height)
        x0, y0 = (width - crop_w) // 2, height - crop_h
        step, x1, y1 = 1, x0 + crop_w, height
    else:
        size = min(512, width, height)
        x0, y0 = (width - size) // 2, max(0, (height - size) // 2 - 40)
        step, x1, y1 = 2, x0 + size, y0 + size
    small = []
    for y in range(y0, y1, step):
        src = rows[y][x0 * 3:x1 * 3]
        row = bytearray(len(range(0, len(src) // 3, step)) * 3)
        row[0::3], row[1::3], row[2::3] = src[0::3 * step], src[1::3 * step], src[2::3 * step]
        small.append(bytes(row))
    data = png_bytes(len(small[0]) // 3, len(small), small)
    LOG(f"MCCI_SCREENSHOT {name} " + base64.b64encode(data).decode("ascii"))


# --------------------------------------------------------------------------------------------- one run

def run_game(inst, mod_jar, out_dir, label, extra_mods_dir=None, shaderpack=None, mode="smoke",
             timeout=1200, mods_info=None, node=None, extra_env=None):
    game_dir = Path(out_dir) / f"{inst.mc}-{label}"
    shutil.rmtree(game_dir, ignore_errors=True)
    (game_dir / "mods").mkdir(parents=True)
    (game_dir / "saves").mkdir()
    (game_dir / "config").mkdir()
    shutil.copytree(inst.world, game_dir / "saves" / "smoke")
    if mod_jar:  # None for a control run without this mod
        shutil.copy(mod_jar, game_dir / "mods")
    shutil.copy(inst.fabric_api[1], game_dir / "mods")
    if extra_mods_dir:
        for jar in Path(extra_mods_dir).glob("*.jar"):
            shutil.copy(jar, game_dir / "mods")
    # A launcher folder with saved skins (the unit tests' database), chosen as the client's main folder: the
    # Skins page reads it.
    launcher = game_dir / "launcher"
    launcher.mkdir()
    shutil.copy(ROOT / "src" / "test" / "resources" / "skins" / "app.db", launcher / "app.db")
    (game_dir / "config" / "cosmeticsmod-client.json").write_text(json.dumps({"mainFolder": str(launcher.resolve())}))
    if shaderpack:
        (game_dir / "shaderpacks").mkdir()
        shutil.copy(shaderpack, game_dir / "shaderpacks")
        # Shaders start off; the mod's scenario switches them on through the Iris API mid-run.
        (game_dir / "config" / "iris.properties").write_text(
            f"shaderPack={Path(shaderpack).name}\nenableShaders=false\n")
    # The benchmark compares frame rates, so nothing may cap them: no frame limit (260 = unlimited), and no
    # "inactivity" limit, which drops the game to 30 FPS after a minute without input (from 1.21.2).
    fps = ["maxFps:260", 'inactivityFpsLimit:"minimized"'] if mode == "benchmark" else ["maxFps:60"]
    # MCCI_GRAPHICS=fast|fancy|fabulous: the Sol light pass has to work under each (Fabulous composites the world
    # through its own post chain first). 1.21.x stores it as a number; 26.x as a name.
    graphics = os.environ.get("MCCI_GRAPHICS", "")
    extra = []
    if graphics:
        extra.append("graphicsMode:" + ({"fast": "0", "fancy": "1", "fabulous": "2"}[graphics]
                                        if not inst.mc.startswith("26.") else '"' + graphics + '"'))
    (game_dir / "options.txt").write_text("\n".join([
        "onboardAccessibility:false", "skipMultiplayerWarning:true", "tutorialStep:none", "joinedFirstServer:true",
        "pauseOnLostFocus:false", "renderDistance:4", "simulationDistance:5", *fps, "enableVsync:false",
        "fullscreen:false", "narrator:0", "soundCategory_master:0.0", "guiScale:2", *extra, ""]))

    jvm = [
        "-Xmx3G", "-XX:+UseG1GC",
        # The game's own logging plus logs/cosmeticsmod.log with only our logger, for the log gate.
        "-Dlog4j.configurationFile=" + str(ROOT / "ci" / "log4j2-ci.xml"),
        "-Dmixin.debug.export=true", "-Dmixin.debug.verify=true",
        "-Dcosmeticsmod.equipAll=" + ("false" if mode == "sol" else "true"),
        "-Dcosmeticsmod.benchmark=true" if mode == "benchmark" else
        ("-Dcosmeticsmod.solScenario=" + SOL_SCENARIO if mode == "sol" else "-Dcosmeticsmod.smokeTest=true"),
    ]
    if mode != "sol" and SOL_SCENARIO_IN_SMOKE:
        jvm.append("-Dcosmeticsmod.solScenario=" + SOL_SCENARIO_IN_SMOKE)
    if mode == "sol":
        # Local look runs: compare light-pass strengths in one run, or judge at another quality.
        for env, prop in (("MCCI_SOL_TUNE", "solTune"), ("MCCI_SOL_QUALITY", "solQuality"), ("MCCI_SOL_SHOTS", "solShots"), ("MCCI_SOL_THEMES", "solThemes"), ("MCCI_SOL_UI", "solUi"), ("MCCI_SOL_EVENTS", "solEvents")):
            if os.environ.get(env):
                jvm.append("-Dcosmeticsmod.%s=%s" % (prop, os.environ[env]))
    if mode == "benchmark":
        jvm.append("-Dcosmeticsmod.benchmark.copies=50")
    # The Sol scenario's music shots: a mock Cider (REST, Socket.IO, Apple lyrics, a cover) and LRCLIB on Cider's port.
    mock = None
    if mode == "sol":
        import mock_cider
        try:
            mock = mock_cider.start(10767)
            jvm += ["-Dcosmeticsmod.mockCider=true", "-Dcosmeticsmod.lrclib=http://127.0.0.1:10767",
                    "-Dcosmeticsmod.fakeSpectrum=true"]
        except OSError as e:
            LOG(f"[{inst.mc}/{label}] mock Cider not started: {e}")
    if shaderpack:
        jvm.append("-Dcosmeticsmod.expectShaders=true")
    for arg in inst.fabric.get("arguments", {}).get("jvm", []):
        if isinstance(arg, str):
            jvm.append(arg)
    game = [
        "--username", "CosmeticsCI", "--version", inst.mc, "--gameDir", str(game_dir),
        "--assetsDir", str(inst.assets_dir), "--assetIndex", inst.asset_index,
        "--uuid", uuid.uuid3(uuid.NAMESPACE_DNS, "cosmetics-ci").hex, "--accessToken", "0",
        "--userType", "msa", "--versionType", "release", "--width", "1280", "--height", "720",
        "--quickPlaySingleplayer", "smoke",
    ]
    cmd = [inst.java, *jvm, "-cp", os.pathsep.join(inst.classpath), inst.fabric["mainClass"], *game]

    display = Display.get()
    env = dict(os.environ, DISPLAY=display.name, LIBGL_ALWAYS_SOFTWARE="1",
               # 26.3+ uses SDL3, whose GLX path finds no matching visual on Xvfb; Mesa's EGL works.
               # Versions using GLFW ignore these.
               SDL_VIDEO_DRIVER="x11", SDL_VIDEO_FORCE_EGL="1", **(extra_env or {}))
    log_path = game_dir / "stdout.log"
    LOG(f"[{inst.mc}/{label}] launching ({mode})")
    started = time.time()
    shots = []
    hang_reason = None
    with open(log_path, "w") as log_file:
        proc = subprocess.Popen(cmd, cwd=game_dir, stdout=log_file, stderr=subprocess.STDOUT, env=env)
        seen_markers = set()
        timed_out = False
        last_size, last_change = 0, time.time()
        joined_at = None
        while proc.poll() is None:
            if mode == "control":
                if joined_at is None and "joined the game" in log_path.read_text(errors="replace"):
                    joined_at = time.time()
                if joined_at is not None and time.time() - joined_at > CONTROL_SURVIVE_SECONDS:
                    proc.kill()
                    break
            if time.time() - started > timeout:
                timed_out = True
                proc.kill()
                break
            # Fail fast instead of waiting for the timeout: the game blocks on an error dialog when no
            # render backend can start, and a silent log for minutes means it hung.
            size = log_path.stat().st_size
            if size != last_size:
                last_size, last_change = size, time.time()
                text = log_path.read_text(errors="replace")
                if len(STARTUP_FATAL.findall(text)) >= 2 or "Could not create any" in text:
                    time.sleep(10)
                    hang_reason = "no render backend could start (see log)"
            elif time.time() - last_change > 300:
                hang_reason = "game hung: no log output for 5 minutes"
            if hang_reason:
                proc.kill()
                break
            for marker in game_dir.glob("cosmeticsmod-phase-*"):
                phase = marker.name[len("cosmeticsmod-phase-"):]
                if phase not in seen_markers and (phase.startswith("sol_") or phase in ("front", "back", "shield_on", "shield_off", "shield_cosmetic", "emote", "emote_elytra", "elytra_stand_on", "elytra_angle_on", "elytra_stand_off", "elytra_angle_off", "elytra_cape", "elytra_emote", "shaders_on") or phase.startswith("ui_")):
                    seen_markers.add(phase)
                    time.sleep(1 if phase.startswith(("ui_", "sol_")) else 4)  # let the phase settle (Sol phases settle in-game first)
                    png = game_dir / f"screenshot-{phase}.png"
                    if display.screenshot(png):
                        shots.append(png.name)
            time.sleep(1)
        proc.wait()
    if mock:
        mock.shutdown()
        mock.server_close()

    if mode == "control":
        crash = crash_summary(game_dir)
        survived = joined_at is not None and not crash and not timed_out
        LOG(f"[{inst.mc}/{label}] control run without this mod: "
            + ("ran fine in the world" if survived else "crashed or never reached the world"))
        for line in crash[:30]:
            LOG("    " + line[:300])
        return {"survived": survived, "crash": crash}

    summary = evaluate(inst, game_dir, label, mode, timed_out, time.time() - started, shots, mods_info, node)
    if hang_reason:
        summary["passed"] = False
        summary["problems"].insert(0, hang_reason)
    (game_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    status = "PASS" if summary["passed"] else "FAIL"
    LOG(f"[{inst.mc}/{label}] {status} in {summary['seconds']:.0f}s"
        + ("" if summary["passed"] else ": " + "; ".join(summary["problems"])))
    for phase, stats in summary["phases"].items():
        LOG(f"    {phase}: " + ", ".join(f"{k}={v:.3g}" if isinstance(v, float) else f"{k}={v}"
                                       for k, v in stats.items()))
    if not summary["passed"]:
        crash = crash_summary(game_dir)
        if crash:
            LOG("    --- crash report ---")
            for line in crash:
                LOG("    " + line[:300])
        # Surface the end of the game log right in the CI output.
        log = game_dir / "logs" / "latest.log"
        if not log.exists():
            log = game_dir / "stdout.log"
        if log.exists():
            LOG("    --- last lines of " + log.name + " ---")
            for line in log.read_text(errors="replace").splitlines()[-40:]:
                LOG("    " + line[:300])
    return summary


CONTROL_SURVIVE_SECONDS = 40
# The Sol scenario: "full" or "reduced" for a run of its own (mode "sol"); set through --sol.
SOL_SCENARIO = "full"
SOL_SCENARIO_IN_SMOKE = ""


def crash_summary(game_dir, max_lines=40):
    """The description, exception and "Caused by" chain of the first crash report, or [] if none."""
    reports = sorted((game_dir / "crash-reports").glob("*.txt")) if (game_dir / "crash-reports").exists() else []
    if not reports:
        return []
    lines = reports[0].read_text(errors="replace").splitlines()
    start = next((i for i, l in enumerate(lines) if l.startswith("Description:")), 0)
    head = []
    for line in lines[start:]:
        if line.startswith("A detailed walkthrough"):
            break
        head.append(line)
    out = head[:max_lines // 2]
    for i, line in enumerate(head):
        if line.startswith("Caused by:") and i >= max_lines // 2:
            out.extend(head[i:i + 4])
    return out[:max_lines]


def our_log_problems(game_dir):
    """The log gate, on our own log file: unexpected warnings and errors, expected ones more than once, and any
    identical line more than REPEAT_LIMIT times (per-frame or per-tick logging)."""
    path = game_dir / "logs" / "cosmeticsmod.log"
    if not path.exists():
        return ["no logs/cosmeticsmod.log: the CI logging config (ci/log4j2-ci.xml) wasn't used"]
    counts = {}
    unexpected = []
    for line in path.read_text(errors="replace").splitlines():
        m = OUR_LINE.match(line)
        if not m:
            continue  # a stack trace line under the last message
        msg = m.group("msg").strip()
        counts[msg] = counts.get(msg, 0) + 1
        if m.group("level") in ("WARN", "ERROR") and "SMOKE TEST FAILED" not in msg \
                and not any(p.search(msg) for p in EXPECTED_ONCE):
            unexpected.append(f"{m.group('level')} {msg[:200]}")
    problems = []
    if unexpected:
        unique = list(dict.fromkeys(unexpected))
        problems.append(f"{len(unique)} unexpected warning/error line(s) from this mod, e.g. {unique[0]}"
                        + (f"; also {unique[1]}" if len(unique) > 1 else ""))
    twice = [m for m, n in counts.items() if any(p.search(m) for p in EXPECTED_ONCE) and n > 1]
    if twice:
        problems.append(f"an expected-once warning came {counts[twice[0]]} times: {twice[0][:200]}")
    repeated = sorted(((n, m) for m, n in counts.items() if n > REPEAT_LIMIT), reverse=True)
    if repeated:
        n, m = repeated[0]
        problems.append(f"{len(repeated)} line(s) of ours repeated more than {REPEAT_LIMIT} times, e.g. {n}x {m[:200]}")
    return problems


def evaluate(inst, game_dir, label, mode, timed_out, seconds, shots, mods_info, node):
    problems = []
    results_path = game_dir / ("cosmeticsmod-sol-results.json" if mode == "sol" else "cosmeticsmod-results.json")
    results = json.loads(results_path.read_text()) if results_path.exists() else None
    if results is not None and mode == "sol":
        results["failures"] = results.get("problems", [])
    if timed_out:
        problems.append("timed out")
    if results is None:
        problems.append("the game never finished the scenario (no cosmeticsmod-results.json): crash, hang or never reached the world")
    elif not results.get("passed"):
        problems.extend(results.get("failures", []))

    text = ""
    for log in (game_dir / "logs" / "latest.log", game_dir / "stdout.log"):
        if log.exists():
            text += log.read_text(errors="replace")
    mixin = sorted({line.strip()[:240] for line in text.splitlines() if MIXIN_PROBLEM.search(line)})
    if mixin:
        problems.append(f"{len(mixin)} mixin problem line(s), e.g. {mixin[0]}")
    ours = sorted({line.strip()[:240] for line in text.splitlines()
                   if OUR_ERROR.search(line) and "SMOKE TEST FAILED" not in line})
    if ours:
        problems.append(f"{len(ours)} error line(s) from this mod, e.g. {ours[0]}")
    problems.extend(our_log_problems(game_dir))
    crashes = sorted((game_dir / "crash-reports").glob("*.txt")) if (game_dir / "crash-reports").exists() else []
    if crashes:
        first = crashes[0].read_text(errors="replace").splitlines()
        description = next((l for l in first if l.startswith("Description:")), crashes[0].name)
        problems.append(f"crash report: {description}")

    return {
        "mc": inst.mc,
        "node": node,
        "label": label,
        "mode": mode,
        "fabricApi": inst.fabric_api[0],
        "mods": mods_info or {},
        "passed": not problems,
        "problems": problems,
        "seconds": seconds,
        "phases": (results or {}).get("phases", {}),
        "screenshots": shots,
    }


# --------------------------------------------------------------------------------------------- commands

def java_for(major):
    home = os.environ.get(f"JAVA_HOME_{major}_X64")
    if home:
        return str(Path(home) / "bin" / "java")
    return shutil.which("java") or "java"


def mod_jar_for(node, jars_dir):
    jars = [j for j in Path(jars_dir).glob(f"*+mc{node}.jar") if not j.name.endswith("-sources.jar")]
    if not jars:
        raise FileNotFoundError(f"no jar for build {node} in {jars_dir}")
    # The newest, should an older build's jar (another name) still be there.
    return max(jars, key=lambda j: j.stat().st_mtime)


def node_info(node):
    return json.loads((ROOT / "ci" / "versions.json").read_text())[node]


def cmd_runtime(args):
    info = node_info(args.node)
    try:
        jar = mod_jar_for(args.node, args.jars)
    except FileNotFoundError as e:
        for mc in info["test"]:
            setup_failure(args, mc, "vanilla", "smoke", f"build failed, no jar ({e})")
        return 1
    failures = 0
    for mc in info["test"]:
        try:
            inst = Instance(mc, java_for(info["java"]), args.cache)
            summary = run_game(inst, jar, args.out, "vanilla", node=args.node)
        except Exception as e:  # noqa: BLE001 - report setup failures as a failed run, keep testing others
            summary = setup_failure(args, mc, "vanilla", "smoke", e)
        failures += not summary["passed"]
    return 1 if failures else 0


def cmd_look(args):
    """One release with the Sol scenario only, screenshots of every theme; for looking at the sky locally."""
    global SOL_SCENARIO
    SOL_SCENARIO = args.sol
    info = node_info(args.node)
    mc = args.release or info["test"][-1]
    jar = mod_jar_for(args.node, args.jars)
    inst = Instance(mc, java_for(info["java"]), args.cache)
    summary = run_game(inst, jar, args.out, "sol", mode="sol", node=args.node, timeout=args.timeout)
    return 0 if summary["passed"] else 1


def cmd_compat(args):
    info = node_info(args.node)
    mc = info["test"][-1]
    try:
        jar = mod_jar_for(args.node, args.jars)
    except FileNotFoundError as e:
        setup_failure(args, mc, "compat", "smoke", f"build failed, no jar ({e})")
        return 1
    sets = json.loads((ROOT / "ci" / "compat-sets.json").read_text())
    sets.pop("_comment", None)
    selected = args.sets.split(",") if args.sets else list(sets)
    inst = Instance(mc, java_for(info["java"]), args.cache)
    failures = 0
    for name in selected:
        summary = run_stack(args, inst, jar, mc, name, sets[name], "smoke")
        failures += not summary["passed"]
    if args.benchmark:
        run_stack(args, inst, jar, mc, "benchmark", sets["sodium-iris-complementary"], "benchmark")
        # Sol's cost next to Voxy's far terrain (n/a where Voxy has no build for this version).
        run_stack(args, inst, jar, mc, "benchmark-voxy", sets["voxy"], "benchmark")
    return 1 if failures else 0


def run_stack(args, inst, jar, mc, label, spec, mode, resolved=None):
    """
    Runs the game with one mod stack, starting from the newest releases. If Fabric says those versions
    don't work together (a problem between the other mods, not this one), follows the loader's
    suggested version swaps, then tries the newest builds including betas, and reports n/a if nothing
    resolves. When the run passes and `resolved` is a dict, the working setup is stored in it so later
    runs can reuse it without resolving again.
    """
    last_conflict, stack = None, {}
    for prerelease in (False, True):
        mods_dir = Path(args.cache) / "stacks" / mc / (label + ("-pre" if prerelease else ""))
        shutil.rmtree(mods_dir, ignore_errors=True)
        mods_dir.mkdir(parents=True)
        tried = set()
        try:
            stack, missing = fetch_stack(spec["mods"], mc, mods_dir, prerelease=prerelease)
            shaderpack, pack_info = None, {}
            if spec.get("shaderpack"):
                pack = modrinth_version(spec["shaderpack"], None, loader="iris")
                if pack is None:
                    missing.append(spec["shaderpack"])
                else:
                    shaderpack = modrinth_download(pack, Path(args.cache) / "shaderpacks")
                    pack_info = {spec["shaderpack"]: pack["version_number"]}
            if missing:
                return write_na(args, mc, label, {**stack_info(stack), **pack_info}, missing)
            for _ in range(MAX_LOADER_FIXES + 1):
                summary = run_game(inst, jar, args.out, label, mods_dir, shaderpack, mode=mode,
                                   mods_info={**stack_info(stack), **pack_info}, node=args.node,
                                   extra_env=spec.get("env"))
                game_dir = Path(args.out) / f"{mc}-{label}"
                conflict = stack_conflict(game_dir)
                if summary["passed"]:
                    if resolved is not None:
                        resolved.update(mods_dir=mods_dir, shaderpack=shaderpack,
                                        mods_info={**stack_info(stack), **pack_info})
                    return summary
                if conflict is None:
                    return check_without_this_mod(args, inst, mc, label, mods_dir, shaderpack, summary, spec.get("env"))
                last_conflict = conflict
                fix = apply_loader_fix(read_logs(game_dir), mods_dir, stack, mc, tried)
                if fix is None:
                    break
                LOG(f"[{mc}/{label}] the other mods need different versions of each other; retrying with {fix}")
        except Exception as e:  # noqa: BLE001
            return setup_failure(args, mc, label, mode, e)
        if not prerelease:
            LOG(f"[{mc}/{label}] no working combination of releases; retrying with the newest betas")
    return write_na(args, mc, label, stack_info(stack), [],
                    reason=f"no published builds of these mods work together: {last_conflict}")


MAX_LOADER_FIXES = 4


def check_without_this_mod(args, inst, mc, label, mods_dir, shaderpack, summary, extra_env=None):
    """
    A stack failed for a reason other than a version conflict. Runs the same stack without this mod:
    if it crashes there too, the failure belongs to the other mods or to CI's software OpenGL, and the
    stack is reported as n/a with the reason. Otherwise this mod is involved and the failure stands.
    """
    control = run_game(inst, None, args.out, label + "-without-cosmeticsmod", mods_dir, shaderpack,
                       mode="control", timeout=420, node=args.node, extra_env=extra_env)
    game_dir = Path(args.out) / f"{mc}-{label}"
    if control["survived"]:
        summary["problems"].append("the same stack without this mod runs fine, so this mod is involved")
    else:
        reason = next((l for l in control["crash"] if l.startswith("Description:")), "never reached the world")
        detail = next((l.strip() for l in control["crash"][1:] if l.strip()), "")
        summary.update(passed=True, na=True)
        summary["problems"].insert(0, f"fails without this mod too ({reason} {detail})"[:300])
        LOG(f"[{mc}/{label}] n/a: the stack fails without this mod as well, so this is not a Cosmetics Mod problem")
    (game_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    return summary


def read_logs(game_dir):
    return "\n".join(log.read_text(errors="replace")
                     for log in (game_dir / "logs" / "latest.log", game_dir / "stdout.log") if log.exists())


def stack_conflict(game_dir):
    """The loader's incompatibility message if the stack failed between other mods, else None."""
    for log in (game_dir / "logs" / "latest.log", game_dir / "stdout.log"):
        if not log.exists():
            continue
        text = log.read_text(errors="replace")
        if INCOMPATIBLE_STACK not in text:
            continue
        details = [line.strip() for line in text.splitlines() if line.strip().startswith("- ")]
        if any("cosmeticsmod" in line.lower() or "cosmetics mod" in line.lower() for line in details):
            return None  # this mod is part of the problem: a real failure
        return details[0][:200] if details else INCOMPATIBLE_STACK
    return None


def write_na(args, mc, label, got, missing, reason=None):
    game_dir = Path(args.out) / f"{mc}-{label}"
    game_dir.mkdir(parents=True, exist_ok=True)
    reason = reason or f"no build for {mc}: {', '.join(missing)}"
    summary = {"mc": mc, "node": args.node, "label": label, "mode": "smoke", "mods": got, "passed": True,
               "na": True, "problems": [reason], "phases": {}, "screenshots": []}
    (game_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    LOG(f"[{mc}/{label}] n/a: {reason}")
    return summary


def setup_failure(args, mc, label, mode, error):
    LOG(f"[{mc}/{label}] FAIL during setup: {error}")
    game_dir = Path(args.out) / f"{mc}-{label}"
    game_dir.mkdir(parents=True, exist_ok=True)
    summary = {"mc": mc, "node": args.node, "label": label, "mode": mode, "mods": {}, "passed": False,
               "problems": [f"setup failed: {error}"], "phases": {}, "screenshots": []}
    (game_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    return summary


# A cosmetic definition inside a mod jar.
COSMETIC_FILE = re.compile(r"assets/[^/]+/cosmetics/[^/]+\.json$")
# Benchmark numbers compared between the two builds: (key in the phase results, label, lower is better).
BENCH_METRICS = [("cosmeticsMsPerFrame", "Cosmetics CPU ms/frame", True), ("fps", "FPS", False),
                 ("verticesPerFrame", "Vertices/frame", True)]

# The benchmark draws each view at the stress load, at a normal load and with the mod off (see DevScenario).
COST_VIEWS = [("Shaders off", "vanilla_back", "vanilla_back_normal", "vanilla_back_off"),
              ("Shaders on", "shaders_on", "shaders_on_normal", "shaders_on_off")]


def cost_lines(rounds, copies):
    """
    Markdown table of what cosmetics cost: each view's frame rate with the mod off, at a normal load (every
    cosmetic once) and at the stress load, from benchmark runs of one build (`rounds`: each run's phases).
    """
    lines = []
    for view, stress, normal, off in COST_VIEWS:
        def values(phase, key):
            return [r[phase][key] for r in rounds if key in r.get(phase, {})]
        off_fps = values(off, "fps")
        if not off_fps:
            continue
        base = median(off_fps)
        for label, phase in (("off", off), ("normal load (each cosmetic once)", normal),
                             (f"stress load (each cosmetic {copies}x)", stress)):
            fps = values(phase, "fps")
            if not fps:
                continue
            ms = values(phase, "cosmeticsMsPerFrame")
            verts = values(phase, "verticesPerFrame")
            change = (median(fps) - base) / base * 100 if base else 0.0
            lines.append(f"| {view} | {label} | {median(fps):.1f} [{min(fps):.1f} to {max(fps):.1f}] | "
                         + ("" if phase == off else f"{change:+.1f}%") + " | "
                         + (f"{median(ms):.3f}" if ms and phase != off else "") + " | "
                         + (f"{median(verts):,.0f}" if verts and phase != off else "") + " |")
    if not lines:
        return []
    return ["| View | Cosmetics | FPS, median [min to max] | FPS vs off | Cosmetics CPU ms/frame | Vertices/frame |",
            "|---|---|---|---|---|---|"] + lines


def same_cosmetics(jar, reference, dest):
    """
    A copy of `jar` without the cosmetics that `reference` doesn't bundle, so both builds draw exactly the
    same scene (for example, a new example cosmetic is left out of the comparison). Returns the removed
    entries.
    """
    with zipfile.ZipFile(reference) as z:
        keep = {name for name in z.namelist() if COSMETIC_FILE.match(name)}
    removed = []
    Path(dest).parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar) as src, zipfile.ZipFile(dest, "w") as out:
        for item in src.infolist():
            if COSMETIC_FILE.match(item.filename) and item.filename not in keep:
                removed.append(item.filename)
                continue
            out.writestr(item, src.read(item.filename))
    return removed


def median(values):
    ordered = sorted(values)
    middle = len(ordered) // 2
    return ordered[middle] if len(ordered) % 2 else (ordered[middle - 1] + ordered[middle]) / 2


def cmd_bench_ab(args):
    """
    Runs the benchmark with the base and head builds in alternating order (A B, B A, A B, ...) so drift
    on the runner (thermal, noisy neighbours) hits both the same, then compares medians per phase.
    """
    info = node_info(args.node)
    mc = info["test"][-1]
    work = Path(args.out)
    work.mkdir(parents=True, exist_ok=True)
    head = work / "jars" / "head" / Path(args.head).name
    removed = same_cosmetics(args.head, args.base, head)
    LOG(f"[{mc}/bench-ab] head jar without cosmetics the base doesn't have: "
        + (", ".join(removed) if removed else "nothing removed"))
    jars = {"base": Path(args.base), "head": head}
    sets = json.loads((ROOT / "ci" / "compat-sets.json").read_text())
    spec = sets["sodium-iris-complementary"]
    inst = Instance(mc, java_for(info["java"]), args.cache)
    phases = {"base": [], "head": []}
    setup = {}
    for r in range(args.rounds):
        for which in (("base", "head") if r % 2 == 0 else ("head", "base")):
            label = f"bench-{which}-{r + 1}"
            if not setup:
                summary = run_stack(args, inst, jars[which], mc, label, spec, "benchmark", resolved=setup)
            else:
                summary = run_game(inst, jars[which], args.out, label, setup["mods_dir"], setup["shaderpack"],
                                   mode="benchmark", mods_info=setup["mods_info"], node=args.node,
                                   extra_env=spec.get("env"))
            if not summary["passed"] or summary.get("na"):
                LOG(f"[{mc}/bench-ab] {label} did not complete: {'; '.join(summary.get('problems', []))}")
                return 1
            phases[which].append(summary["phases"])

    lines = [f"## Benchmark A/B, Minecraft {mc}", "",
             f"Same scene with both builds: Sodium + Iris + Complementary, every cosmetic drawn 50 times, "
             f"{args.rounds} runs each in alternating order. Software rendering, so compare the two "
             f"columns, not the absolute numbers. Median [min to max].", ""]
    if removed:
        lines += ["Left out of the head build so both draw the same: " + ", ".join(f"`{r}`" for r in removed), ""]
    lines += [f"| Phase | Metric | Base ({Path(args.base).name}) | Head ({Path(args.head).name}) | Change | Ranges overlap |",
              "|---|---|---|---|---|---|"]
    result = {"mc": mc, "node": args.node, "rounds": args.rounds, "removed": removed, "phases": {}}
    for phase in phases["base"][0]:
        for key, name, _ in BENCH_METRICS:
            a = [p[phase][key] for p in phases["base"] if key in p.get(phase, {})]
            b = [p[phase][key] for p in phases["head"] if key in p.get(phase, {})]
            if not a or not b:
                continue
            ma, mb = median(a), median(b)
            change = (mb - ma) / ma * 100 if ma else 0.0
            overlap = min(b) <= max(a) and min(a) <= max(b)
            lines.append(f"| {phase} | {name} | {ma:.4g} [{min(a):.4g} to {max(a):.4g}] | "
                         f"{mb:.4g} [{min(b):.4g} to {max(b):.4g}] | {change:+.1f}% | {'yes' if overlap else 'no'} |")
            result["phases"].setdefault(phase, {})[key] = {"base": a, "head": b, "changePercent": change,
                                                           "rangesOverlap": overlap}
    cost = cost_lines(phases["head"], max(r.get("copies", 0) for r in phases["head"][0].values()))
    if cost:
        lines += ["", "### What cosmetics cost, head build", "",
                  "The same view with the mod switched off, every cosmetic drawn once, and the stress load. "
                  "Software rendering: the percentages are what to read, and they overstate the cost on a real GPU, "
                  "where drawing the vertices isn't done by the CPU.", ""] + cost
    text = "\n".join(lines) + "\n"
    (work / "bench-ab.md").write_text(text)
    (work / "bench-ab.json").write_text(json.dumps(result, indent=2))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write(text)
    print(text)
    return 0


def cmd_summarize(args):
    runs = [json.loads(p.read_text()) for p in sorted(Path(args.runs).rglob("summary.json"))]

    def vkey(v):
        return tuple(int(x) for x in re.findall(r"\d+", v))

    runs.sort(key=lambda r: (vkey(r["mc"]), r["label"]))
    lines = ["# In-game test results", "",
             "Real Minecraft client, production mode (players' setup), virtual display with Mesa software OpenGL.", "",
             "| Minecraft | Setup | Result | Mods | Notes |", "|---|---|---|---|---|"]
    for r in runs:
        if r.get("mode") == "benchmark":
            continue
        result = "n/a" if r.get("na") else ("pass" if r["passed"] else "**FAIL**")
        mods = ", ".join(f"{k} {v}" for k, v in r.get("mods", {}).items()) or "Fabric API only"
        notes = "; ".join(r.get("problems", []))
        phases = r.get("phases", {})
        if "shaders_on" in phases:
            notes = (notes + "; " if notes else "") + \
                f"shadow-pass meshes: {phases['shaders_on'].get('shadowPassMeshes')}"
        lines.append(f"| {r['mc']} | {r['label']} | {result} | {mods} | {notes.replace('|', '/')} |")
    bench = [r for r in runs if r.get("mode") == "benchmark"]
    if bench:
        lines += ["", "## Benchmark (50x cosmetic load, software rendering: compare runs, not absolute FPS)", "",
                  "`_normal` phases draw every cosmetic once, `_off` phases have the mod switched off.", "",
                  "| Minecraft | Phase | FPS | Cosmetics CPU ms/frame | Vertices/frame | Shader pack active |",
                  "|---|---|---|---|---|---|"]
        for r in bench:
            for phase, p in r.get("phases", {}).items():
                lines.append(f"| {r['mc']} | {phase} | {p.get('fps', 0):.1f} | "
                             f"{p.get('cosmeticsMsPerFrame', 0):.3f} | {p.get('verticesPerFrame', 0):,.0f} | "
                             f"{p.get('shaderPackInUse')} |")
        sol = [(r, phase, p) for r in bench for phase, p in r.get("phases", {}).items() if "solMsPerFrame" in p]
        if sol:
            parts = ["sky", "sun", "particles", "light", "flare", "sol_ui", "hud"]
            lines += ["", "## Sol's cost (CPU ms per frame; budgets: Atmosphere 0.3, sun + flare 0.03, Sol UI 0.2)", "",
                      "With software rendering (llvmpipe, as in CI) a draw call also does the graphics card's work, so "
                      "these times are the renderer's as much as Sol's: compare runs, and check the budgets on a real "
                      "graphics card.", "",
                      "| Minecraft | Setup | Phase | FPS | Frame p50 / p99 ms | " + " | ".join(parts) + " |",
                      "|---|---|---|---|---|" + "---|" * len(parts)]
            for r, phase, p in sol:
                ms = p["solMsPerFrame"]
                lines.append(f"| {r['mc']} | {r['label']} | {phase} | {p.get('fps', 0):.1f} | "
                             f"{p.get('frameMsP50', 0):.1f} / {p.get('frameMsP99', 0):.1f} | "
                             + " | ".join(f"{ms.get(k, 0):.3f}" for k in parts) + " |")
    text = "\n".join(lines) + "\n"
    Path(args.output).write_text(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write(text)
    print(text)
    return 0  # a report; the runs themselves already failed their jobs


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("runtime", "compat"):
        p = sub.add_parser(name)
        p.add_argument("--node", required=True, help="build (Stonecutter version) to test, e.g. 1.21.8")
        p.add_argument("--jars", default=str(ROOT / "build" / "libs"))
        p.add_argument("--cache", default=str(ROOT / "ci" / "work" / "cache"))
        p.add_argument("--out", default=str(ROOT / "ci" / "work" / "runs"))
        if name == "compat":
            p.add_argument("--sets", default="", help="comma-separated stacks from compat-sets.json (default: all)")
            p.add_argument("--benchmark", action="store_true", help="also run the benchmark (shaders off/on)")
    p = sub.add_parser("look")
    p.add_argument("--node", required=True, help="build (Stonecutter version), e.g. 1.21.11")
    p.add_argument("--release", default="", help="Minecraft release to run (default: the build's newest)")
    p.add_argument("--sol", default="full", help="Sol scenario: full, reduced or look")
    p.add_argument("--timeout", type=int, default=1500)
    p.add_argument("--jars", default=str(ROOT / "build" / "libs"))
    p.add_argument("--cache", default=str(ROOT / "ci" / "work" / "cache"))
    p.add_argument("--out", default=str(ROOT / "ci" / "work" / "runs"))
    p = sub.add_parser("bench-ab")
    p.add_argument("--node", required=True, help="build (Stonecutter version) both jars are for, e.g. 1.21.11")
    p.add_argument("--base", required=True, help="jar to compare against, e.g. built from main")
    p.add_argument("--head", required=True, help="jar under test")
    p.add_argument("--rounds", type=int, default=4, help="benchmark runs per jar")
    p.add_argument("--cache", default=str(ROOT / "ci" / "work" / "cache"))
    p.add_argument("--out", default=str(ROOT / "ci" / "work" / "bench"))
    p = sub.add_parser("summarize")
    p.add_argument("--runs", default=str(ROOT / "ci" / "work" / "runs"))
    p.add_argument("--output", default=str(ROOT / "ci" / "work" / "compat-matrix.md"))
    args = parser.parse_args()
    return {"runtime": cmd_runtime, "compat": cmd_compat, "look": cmd_look, "bench-ab": cmd_bench_ab,
            "summarize": cmd_summarize}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
