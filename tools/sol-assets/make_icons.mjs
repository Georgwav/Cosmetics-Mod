// Writes the Sol line icons (the Sol menu prototype's SOL_ICONS, tools/sol-assets/sol_icons.js) as plain shapes
// for the game: src/main/resources/assets/cosmeticsmod/sol/icons.json. Each icon is a list of shapes on the
// 24-unit grid: paths (SVG path data), rects (with corner radius and an optional dash), circles and ellipses (with
// an optional rotation); "duo" marks the soft-fill part (drawn at 16%, 30% when active), the rest are 1.75-wide
// strokes with round caps and joins. Run from the repository root: node tools/sol-assets/make_icons.mjs
import fs from "node:fs";

const src = fs.readFileSync("tools/sol-assets/sol_icons.js", "utf8");
const SOL_ICONS = new Function(src + "\nreturn SOL_ICONS;")();
SOL_ICONS.spark = SOL_ICONS.sparkle; SOL_ICONS.box = SOL_ICONS.cube; SOL_ICONS.drop = SOL_ICONS.item;
// Sol Client modules the prototype doesn't show, drawn the same way (24-unit grid, one soft-fill part).
const d = (s) => s.replace(/<(path|rect|circle|ellipse)/g, '<$1 class="d"');
Object.assign(SOL_ICONS, {
  speaker: d('<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/>') + '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9.2a4 4 0 0 1 0 5.6"/><path d="M18.2 6.6a7.6 7.6 0 0 1 0 10.8"/>',
  server: d('<rect x="4" y="4" width="16" height="7" rx="2"/>') + '<rect x="4" y="4" width="16" height="7" rx="2"/><rect x="4" y="13" width="16" height="7" rx="2"/><path d="M7.6 7.5h.01M7.6 16.5h.01M11 7.5h5.4M11 16.5h5.4"/>',
  clock: d('<circle cx="12" cy="12" r="8.25"/>') + '<circle cx="12" cy="12" r="8.25"/><path d="M12 7.2V12l3.2 2.1"/>',
  signal: d('<circle cx="12" cy="17.6" r="1.6"/>') + '<path d="M12 17.6h.01"/><path d="M8.7 14.3a4.7 4.7 0 0 1 6.6 0"/><path d="M5.9 11.5a8.6 8.6 0 0 1 12.2 0"/><path d="M3.1 8.7a12.5 12.5 0 0 1 17.8 0"/>',
  cloud: d('<path d="M7.6 18.5h9.2a3.9 3.9 0 0 0 .5-7.77A5.4 5.4 0 0 0 6.9 9.9a4.3 4.3 0 0 0 .7 8.6z"/>') + '<path d="M7.6 18.5h9.2a3.9 3.9 0 0 0 .5-7.77A5.4 5.4 0 0 0 6.9 9.9a4.3 4.3 0 0 0 .7 8.6z"/>',
  moon: d('<path d="M19.2 14.8A7.6 7.6 0 1 1 9.2 4.8a6 6 0 0 0 10 10z"/>') + '<path d="M19.2 14.8A7.6 7.6 0 1 1 9.2 4.8a6 6 0 0 0 10 10z"/>',
  gauge: d('<path d="M4.4 16.6a8.1 8.1 0 1 1 15.2 0z"/>') + '<path d="M4.4 16.6a8.1 8.1 0 1 1 15.2 0"/><path d="M12 13.4l3.4-4"/><path d="M12 13.4h.01"/>',
});

const num = (s) => (s === undefined ? undefined : parseFloat(s));
function attrs(tag) {
  const out = {};
  for (const m of tag.matchAll(/([a-zA-Z-]+)="([^"]*)"/g)) out[m[1]] = m[2];
  return out;
}
const icons = {};
for (const [name, markup] of Object.entries(SOL_ICONS)) {
  const shapes = [];
  for (const m of markup.matchAll(/<(path|rect|circle|ellipse)\b([^>]*)\/>/g)) {
    const a = attrs(m[2]);
    const s = { type: m[1], duo: a.class === "d" };
    if (m[1] === "path") s.d = a.d;
    if (m[1] === "rect") Object.assign(s, { x: num(a.x), y: num(a.y), w: num(a.width), h: num(a.height), rx: num(a.rx) || 0 });
    if (m[1] === "circle") Object.assign(s, { cx: num(a.cx), cy: num(a.cy), r: num(a.r) });
    if (m[1] === "ellipse") Object.assign(s, { cx: num(a.cx), cy: num(a.cy), rx: num(a.rx), ry: num(a.ry) });
    if (a["stroke-dasharray"]) s.dash = a["stroke-dasharray"].split(/[ ,]+/).map(Number);
    if (a.transform) {
      const r = /rotate\(([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\)/.exec(a.transform);
      if (r) s.rotate = [Number(r[1]), Number(r[2]), Number(r[3])];
    }
    shapes.push(s);
  }
  icons[name] = shapes;
}
fs.mkdirSync("src/main/resources/assets/cosmeticsmod/sol", { recursive: true });
fs.writeFileSync("src/main/resources/assets/cosmeticsmod/sol/icons.json", JSON.stringify(icons, null, 1) + "\n");
console.log(Object.keys(icons).length + " icons");
