#!/usr/bin/env python3
"""
Runs the real Minecraft client with this mod, the way players run it, for any Minecraft version.

Used by CI so every supported Minecraft release is tested in-game without anyone launching it by hand:

  runtime   --node 1.21.8          every release that build's jar covers (1.21.6, 1.21.7, 1.21.8), vanilla + Fabric API
  compat    --node 1.21.8          the newest release of that build with each mod stack in compat-sets.json,
                                   then the benchmark (shaders off and on)
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

def modrinth_version(project, mc=None, loader="fabric", prerelease=False):
    """Newest version of a project for a Minecraft version; releases first unless prerelease is True."""
    params = {"loaders": json.dumps([loader])}
    if mc:
        params["game_versions"] = json.dumps([mc])
    url = f"{MODRINTH}/project/{project}/version?{urllib.parse.urlencode(params)}"
    try:
        versions = json.loads(fetch(url))
    except IOError:
        return None
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
    Downloads a mod stack plus its required dependencies. Returns ({name: version}, missing).

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
    got = {}
    for project, version in chosen.items():
        modrinth_download(version, dest_dir)
        got[names[project]] = version["version_number"]
    return got, missing


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
            write_png(png_path, *xwd_to_rgb(data))
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


def write_png(path, width, height, rows):
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + r for r in rows)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


# --------------------------------------------------------------------------------------------- one run

def run_game(inst, mod_jar, out_dir, label, extra_mods_dir=None, shaderpack=None, mode="smoke",
             timeout=1200, mods_info=None, node=None):
    game_dir = Path(out_dir) / f"{inst.mc}-{label}"
    shutil.rmtree(game_dir, ignore_errors=True)
    (game_dir / "mods").mkdir(parents=True)
    (game_dir / "saves").mkdir()
    (game_dir / "config").mkdir()
    shutil.copytree(inst.world, game_dir / "saves" / "smoke")
    shutil.copy(mod_jar, game_dir / "mods")
    shutil.copy(inst.fabric_api[1], game_dir / "mods")
    if extra_mods_dir:
        for jar in Path(extra_mods_dir).glob("*.jar"):
            shutil.copy(jar, game_dir / "mods")
    if shaderpack:
        (game_dir / "shaderpacks").mkdir()
        shutil.copy(shaderpack, game_dir / "shaderpacks")
        # Shaders start off; the mod's scenario switches them on through the Iris API mid-run.
        (game_dir / "config" / "iris.properties").write_text(
            f"shaderPack={Path(shaderpack).name}\nenableShaders=false\n")
    (game_dir / "options.txt").write_text("\n".join([
        "onboardAccessibility:false", "skipMultiplayerWarning:true", "tutorialStep:none", "joinedFirstServer:true",
        "pauseOnLostFocus:false", "renderDistance:4", "simulationDistance:5", "maxFps:60", "enableVsync:false",
        "fullscreen:false", "narrator:0", "soundCategory_master:0.0", "guiScale:2", ""]))

    jvm = [
        "-Xmx3G", "-XX:+UseG1GC",
        "-Dmixin.debug.export=true", "-Dmixin.debug.verify=true",
        "-Dcosmeticsmod.equipAll=true",
        "-Dcosmeticsmod.smokeTest=true" if mode == "smoke" else "-Dcosmeticsmod.benchmark=true",
    ]
    if mode == "benchmark":
        jvm.append("-Dcosmeticsmod.benchmark.copies=50")
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
               SDL_VIDEO_DRIVER="x11", SDL_VIDEO_FORCE_EGL="1")
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
        while proc.poll() is None:
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
                if phase not in seen_markers and (phase in ("front", "back", "shaders_on") or phase.startswith("ui_")):
                    seen_markers.add(phase)
                    time.sleep(1 if phase.startswith("ui_") else 4)  # let the phase settle
                    png = game_dir / f"screenshot-{phase}.png"
                    if display.screenshot(png):
                        shots.append(png.name)
            time.sleep(1)
        proc.wait()

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
        # Surface the end of the game log right in the CI output.
        log = game_dir / "logs" / "latest.log"
        if not log.exists():
            log = game_dir / "stdout.log"
        if log.exists():
            LOG("    --- last lines of " + log.name + " ---")
            for line in log.read_text(errors="replace").splitlines()[-40:]:
                LOG("    " + line[:300])
    return summary


def evaluate(inst, game_dir, label, mode, timed_out, seconds, shots, mods_info, node):
    problems = []
    results_path = game_dir / "cosmeticsmod-results.json"
    results = json.loads(results_path.read_text()) if results_path.exists() else None
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
    return jars[0]


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
    return 1 if failures else 0


def run_stack(args, inst, jar, mc, label, spec, mode):
    """
    Runs the game with one mod stack. Tries the newest releases first; if Fabric says those versions
    don't work together (a problem between the other mods, not this one), retries with the newest
    builds including betas, and reports n/a if that doesn't resolve either.
    """
    last_conflict = None
    for prerelease in (False, True):
        mods_dir = Path(args.cache) / "stacks" / mc / (label + ("-pre" if prerelease else ""))
        shutil.rmtree(mods_dir, ignore_errors=True)
        mods_dir.mkdir(parents=True)
        try:
            got, missing = fetch_stack(spec["mods"], mc, mods_dir, prerelease=prerelease)
            shaderpack = None
            if spec.get("shaderpack"):
                pack = modrinth_version(spec["shaderpack"], None, loader="iris")
                if pack is None:
                    missing.append(spec["shaderpack"])
                else:
                    shaderpack = modrinth_download(pack, Path(args.cache) / "shaderpacks")
                    got[spec["shaderpack"]] = pack["version_number"]
            if missing:
                return write_na(args, mc, label, got, missing)
            summary = run_game(inst, jar, args.out, label, mods_dir, shaderpack, mode=mode,
                               mods_info=got, node=args.node)
        except Exception as e:  # noqa: BLE001
            return setup_failure(args, mc, label, mode, e)
        conflict = stack_conflict(Path(args.out) / f"{mc}-{label}")
        if summary["passed"] or conflict is None:
            return summary
        last_conflict = conflict
        LOG(f"[{mc}/{label}] the other mods' versions conflict with each other; retrying with newest betas")
    return write_na(args, mc, label, got, [], reason=f"newest builds of these mods conflict with each other: {last_conflict}")


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
                  "| Minecraft | Phase | FPS | Cosmetics CPU ms/frame | Shader pack active |", "|---|---|---|---|---|"]
        for r in bench:
            for phase, p in r.get("phases", {}).items():
                lines.append(f"| {r['mc']} | {phase} | {p.get('fps', 0):.1f} | "
                             f"{p.get('cosmeticsMsPerFrame', 0):.3f} | {p.get('shaderPackInUse')} |")
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
    p = sub.add_parser("summarize")
    p.add_argument("--runs", default=str(ROOT / "ci" / "work" / "runs"))
    p.add_argument("--output", default=str(ROOT / "ci" / "work" / "compat-matrix.md"))
    args = parser.parse_args()
    return {"runtime": cmd_runtime, "compat": cmd_compat, "summarize": cmd_summarize}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
