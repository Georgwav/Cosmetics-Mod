"""Builds docs/gallery.html: every UI shot, the Styles shots with their apply times, and every effect's contact sheet
with its settings. The pictures are CI's own, on the ci-screenshots branch (a commit with [shots] in its message
publishes them); the page only links to them, so it stays small.

Usage: python3 tools/gallery/make_gallery.py FX_RESULTS.json STYLES_RESULTS.json
  FX_RESULTS.json      cosmeticsmod-fx-results.json from a full effects run (the cases, their settings)
  STYLES_RESULTS.json  cosmeticsmod-styles-results.json from a Styles run (the apply paths and times)
Both are in a run's game folder (ci/work/runs/<node>-sol/) or on the ci-screenshots branch under 1.21.11/.
"""
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = "https://raw.githubusercontent.com/Georgwav/Cosmetics-Mod/ci-screenshots"
NODES = ["1.21.11", "1.21.1", "26.3"]
SWING = ["exhibition", "sigma", "swong", "spin", "stab", "slide", "push"]
SHOUT = {"hud": "HUD", "ui": "UI", "fx": "FX", "f5": "F5", "pvp": "PvP", "xp": "XP"}


def ui_shots():
    """The UI shots in the order the scenario takes them (with the swing styles the full runs add)."""
    src = (ROOT / "src/main/java/io/github/georgwav/cosmetics/devtools/SolScenario.java").read_text()
    body = src[src.index("UI_SHOTS = {"):]
    body = body[:body.index("};")]
    shots = re.findall(r'"([a-z0-9_]+)"', body)
    i = shots.index("hands_view") + 1
    return shots[:i] + ["hands_style_" + s for s in SWING] + shots[i:]


def lang():
    return json.loads((ROOT / "src/main/resources/assets/cosmeticsmod/lang/en_us.json").read_text())


def label(name):
    """A shot's name as words: menu_drawer_crosshair__your -> Menu · drawer · crosshair (your)."""
    main, _, extra = name.partition("__")
    words = " ".join(SHOUT.get(w, w) for w in main.split("_") if w)
    words = words[:1].upper() + words[1:]
    return words + (f" ({extra.replace('_', ' ')})" if extra else "")


def card(src, title, note="", wide=False):
    cls = "card wide" if wide else "card"
    n = f'<p class="note">{html.escape(note)}</p>' if note else ""
    return (f'<figure class="{cls}"><a href="{src}" target="_blank" rel="noopener">'
            f'<img loading="lazy" src="{src}" alt="" data-src="{src}" onerror="this.closest(\'figure\').classList.add(\'missing\')"></a>'
            f'<figcaption><b>{html.escape(title)}</b>{n}</figcaption></figure>')


def main():
    fx = json.loads(Path(sys.argv[1]).read_text())
    styles = json.loads(Path(sys.argv[2]).read_text())
    tr = lang()
    shots = ui_shots()
    style_shots = [s for s in shots if s.startswith("styles_") or s.startswith("menu_styles")]
    menu_shots = [s for s in shots if s not in style_shots]

    def ui_src(node, s):
        return f"{RAW}/{node}/{node}-sol/screenshot-ui_{s}.png"

    parts = []
    parts.append('<section id="ui"><h2>The UI shots</h2>'
                 '<p class="lead">Every screen and state the scenario stages, one shot each, at 1280×720 on CI\'s software '
                 'renderer. Pick the release above; each shot opens full size.</p><div class="grid" data-shots>')
    for s in menu_shots:
        parts.append(card(ui_src(NODES[0], s), label(s)))
    parts.append("</div></section>")

    rows = []
    for a in styles.get("applies", []):
        rows.append(f"<tr><td>{html.escape(label(a['shot']))}</td><td>{html.escape(a['path'])}</td>"
                    f"<td class=\"num\">{a['ms']} ms</td><td class=\"num\">{a.get('mixFiles', '')}</td></tr>")
    parts.append('<section id="styles"><h2>Sol Styles</h2>'
                 '<p class="lead">The Styles shots, made with the synthetic test packs (tools/styles-packs), and how long each '
                 'change took to apply: a live write swaps the textures in place; a reload rebuilds the base pack.</p>'
                 f'<dl class="facts"><div><dt>Packs scanned</dt><dd>{styles.get("packs", "?")} in {styles.get("scanMs", "?")} ms</dd></div>'
                 f'<div><dt>Styles tab opening</dt><dd>{int(styles.get("menuOpenMicros", 0)) / 1000:.1f} ms</dd></div>'
                 f'<div><dt>Files in the mix</dt><dd>{styles.get("mixFiles", "?")}</dd></div></dl>'
                 '<table><thead><tr><th>Change</th><th>Path</th><th>Time</th><th>Mix files</th></tr></thead><tbody>'
                 + "".join(rows) + '</tbody></table><div class="grid" data-shots>')
    for s in style_shots:
        parts.append(card(ui_src(NODES[0], s), label(s)))
    parts.append("</div></section>")

    by_module = {}
    for c in fx.get("cases", []):
        by_module.setdefault(c["module"], []).append(c)
    parts.append('<section id="effects"><h2>The effects</h2>'
                 f'<p class="lead">Every effect in every style on 1.21.11, played on a copy of you with its clock frozen at six '
                 f'moments of its life, on {html.escape(fx.get("preset_a", "").replace("_", " ").title())}. Each passed CI\'s checks: it shows, it\'s gone a '
                 'second after it ends, its colour is the preset\'s accent within ΔE 12 on two presets where it takes the '
                 'preset\'s colour, and a grey one never shows through a wall.</p>')
    for module, cases in by_module.items():
        name = tr.get(f"cosmeticsmod.module.{module}", module.replace("_", " ").title())
        parts.append(f'<h3>{html.escape(name)} <span class="count">{len(cases)}</span></h3><div class="sheets">')
        for c in cases:
            checks = []
            if c.get("colour"):
                checks.append("colour checked")
            if c.get("wall"):
                checks.append("never through walls")
            if c.get("hud"):
                checks.append("on the HUD")
            settings = c.get("settings", "defaults")
            note = f"{'Defaults' if settings == 'defaults' else settings} — life {c.get('life', 0):.1f} s" + (f" — {', '.join(checks)}" if checks else "")
            parts.append(card(f"{RAW}/1.21.11/1.21.11-sol/screenshot-fxsheet_{c['name']}.png", label(c["name"]), note, wide=True))
        parts.append("</div>")
    parts.append("</section>")

    page = TEMPLATE.replace("{{BODY}}", "\n".join(parts)).replace("{{COUNT_UI}}", str(len(menu_shots))) \
        .replace("{{COUNT_STYLES}}", str(len(style_shots))).replace("{{COUNT_FX}}", str(len(fx.get("cases", []))))
    out = ROOT / "docs/gallery.html"
    out.write_text(page)
    print(f"wrote {out}: {len(menu_shots)} UI shots, {len(style_shots)} Styles shots, {len(fx.get('cases', []))} effects")


TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sol Client gallery</title>
<style>
:root { --bg: #0e0f14; --panel: #171922; --line: #2a2d3a; --text: #e9e7e2; --dim: #9a98a6; --accent: #ffb347; }
@media (prefers-color-scheme: light) {
  :root { --bg: #f6f4ef; --panel: #ffffff; --line: #e1ddd3; --text: #1d1c22; --dim: #676472; --accent: #b4610a; }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
header { position: sticky; top: 0; z-index: 2; background: var(--bg); border-bottom: 1px solid var(--line); padding: 12px 16px; }
header .row { max-width: 1400px; margin: 0 auto; display: flex; flex-wrap: wrap; gap: 12px 24px; align-items: center; }
h1 { font-size: 18px; margin: 0; letter-spacing: .02em; }
nav a { color: var(--dim); text-decoration: none; margin-right: 16px; }
nav a:hover { color: var(--accent); }
label { color: var(--dim); }
select { background: var(--panel); color: var(--text); border: 1px solid var(--line); border-radius: 6px; padding: 4px 8px; font: inherit; }
main { max-width: 1400px; margin: 0 auto; padding: 8px 16px 64px; }
h2 { font-size: 22px; margin: 40px 0 6px; }
h3 { font-size: 16px; margin: 28px 0 10px; color: var(--accent); }
.count { color: var(--dim); font-weight: 400; font-size: 13px; }
.lead { color: var(--dim); max-width: 860px; margin: 0 0 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.sheets { display: grid; gap: 14px; }
.card { margin: 0; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.card img { display: block; width: 100%; height: auto; aspect-ratio: 16 / 9; object-fit: cover; background: var(--line); image-rendering: pixelated; }
.card.wide img { aspect-ratio: 32 / 3; object-fit: contain; }
figcaption { padding: 8px 10px; font-size: 13px; }
.note { margin: 2px 0 0; color: var(--dim); }
.missing img { visibility: hidden; }
.missing a { position: relative; display: block; }
.missing a::after { content: "Not taken on this release yet"; position: absolute; inset: 0; display: grid; place-items: center; color: var(--dim); font-size: 13px; }
.facts { display: flex; flex-wrap: wrap; gap: 12px 32px; margin: 0 0 14px; }
.facts dt { color: var(--dim); font-size: 13px; }
.facts dd { margin: 0; font-size: 18px; }
table { border-collapse: collapse; margin: 0 0 18px; width: 100%; max-width: 860px; font-size: 14px; }
th, td { text-align: left; padding: 6px 10px; border-bottom: 1px solid var(--line); }
th { color: var(--dim); font-weight: 500; }
.num { text-align: right; font-variant-numeric: tabular-nums; }
@media (max-width: 640px) { .grid { grid-template-columns: 1fr; } .card.wide img { aspect-ratio: 16 / 3; object-fit: cover; } }
</style>
</head>
<body>
<header><div class="row">
<h1>Sol Client gallery</h1>
<nav><a href="#ui">UI ({{COUNT_UI}})</a><a href="#styles">Styles ({{COUNT_STYLES}})</a><a href="#effects">Effects ({{COUNT_FX}})</a></nav>
<label>Release <select id="node"><option>1.21.11</option><option>1.21.1</option><option>26.3</option></select></label>
</div></header>
<main>
{{BODY}}
</main>
<script>
// The UI and Styles shots of the release picked (the effects' sheets are 1.21.11's).
document.getElementById('node').addEventListener('change', e => {
  const node = e.target.value;
  document.querySelectorAll('[data-shots] img').forEach(img => {
    const src = img.dataset.src.replace(/ci-screenshots\\/[^/]+\\/[^/]+-sol\\//, 'ci-screenshots/' + node + '/' + node + '-sol/');
    img.closest('figure').classList.remove('missing');
    img.src = src;
    img.closest('a').href = src;
  });
});
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
