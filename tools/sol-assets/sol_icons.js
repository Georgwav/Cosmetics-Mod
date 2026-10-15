const SOL_ICONS = (() => {
  const gear = (() => {
    const n = 8, ro = 9.2, ri = 7.2, pts = [];
    for (let i = 0; i < n; i++){
      const a = i/n*Math.PI*2 - Math.PI/2, w = Math.PI/n*.52;
      [[a - w*1.15, ri], [a - w*.62, ro], [a + w*.62, ro], [a + w*1.15, ri]].forEach(([b, r]) => pts.push([12 + Math.cos(b)*r, 12 + Math.sin(b)*r]));
    }
    return "M" + pts.map(p => p.map(v => v.toFixed(2)).join(" ")).join("L") + "Z";
  })();
  const star = (cx, cy, ro, ri) => { const p = []; for (let i = 0; i < 10; i++){ const a = -Math.PI/2 + i*Math.PI/5, r = i % 2 ? ri : ro; p.push((cx + Math.cos(a)*r).toFixed(2) + " " + (cy + Math.sin(a)*r).toFixed(2)); } return "M" + p.join("L") + "Z"; };
  const rays = (cx, cy, r0, r1, n, off) => Array.from({length: n}, (_, i) => { const a = (i/n + (off || 0))*Math.PI*2; return `<path d="M${(cx + Math.cos(a)*r0).toFixed(2)} ${(cy + Math.sin(a)*r0).toFixed(2)}L${(cx + Math.cos(a)*r1).toFixed(2)} ${(cy + Math.sin(a)*r1).toFixed(2)}"/>`; }).join("");
  const d = s => s.replace(/<(path|rect|circle|ellipse)/g, '<$1 class="d"');
  return {
    // sections
    grid4: d('<rect x="13.5" y="3.5" width="7" height="7" rx="2"/>') + '<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
    hud: d('<rect x="6.5" y="7.5" width="5" height="3.5" rx="1"/>') + '<rect x="3" y="4" width="18" height="16" rx="3.5"/><rect x="6.5" y="7.5" width="5" height="3.5" rx="1"/><path d="M6.5 16.5h4"/><rect x="14" y="13.5" width="3.5" height="3" rx="1"/>',
    sun: d('<rect x="8.5" y="8.5" width="7" height="7" rx="1.8"/>') + '<rect x="8.5" y="8.5" width="7" height="7" rx="1.8"/>' + rays(12, 12, 6.8, 9.6, 8, 0),
    shirt: d('<path d="M8.2 3.8 4 6.3 2.6 10.2l3.4 1.4v8.9h12v-8.9l3.4-1.4L20 6.3l-4.2-2.5C14.9 5.2 13.6 6 12 6s-2.9-.8-3.8-2.2z"/>') + '<path d="M8.2 3.8 4 6.3 2.6 10.2l3.4 1.4v8.9h12v-8.9l3.4-1.4L20 6.3l-4.2-2.5C14.9 5.2 13.6 6 12 6s-2.9-.8-3.8-2.2z"/>',
    note: d('<circle cx="7" cy="17.5" r="2.6"/><circle cx="17" cy="15.5" r="2.6"/>') + '<circle cx="7" cy="17.5" r="2.6"/><circle cx="17" cy="15.5" r="2.6"/><path d="M9.6 17.5V6.2L19.6 4v11.5"/><path d="M9.6 9.6l10-2.2"/>',
    puzzle: d('<path d="M5 8h3.4a2.4 2.4 0 1 1 4.8 0H16.5a1 1 0 0 1 1 1v3.1a2.4 2.4 0 1 1 0 4.8V20a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/>') + '<path d="M5 8h3.4a2.4 2.4 0 1 1 4.8 0H16.5a1 1 0 0 1 1 1v3.1a2.4 2.4 0 1 1 0 4.8V20a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/>',
    gear: d(`<path d="${gear}"/>`) + `<path d="${gear}"/><circle cx="12" cy="12" r="3"/>`,
    search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.4 15.4 20.5 20.5"/>',
    // modules
    sword: d('<path d="M20 4v4.6L10.6 18l-4.6-4.6L15.4 4z"/>') + '<path d="M20 4v4.6L10.6 18l-4.6-4.6L15.4 4z"/><path d="M4.4 12.4l7.2 7.2"/><path d="M7.4 16.6 4 20"/>',
    burst: d('<circle cx="12" cy="12" r="2.6"/>') + '<circle cx="12" cy="12" r="2.6"/>' + rays(12, 12, 5.2, 9.6, 4, .125) + rays(12, 12, 5.8, 8, 4, 0),
    hitbox: '<rect x="4" y="3" width="16" height="18" rx="2.5" stroke-dasharray="2.6 2.4"/>' + d('<circle cx="12" cy="9" r="2.4"/>') + '<circle cx="12" cy="9" r="2.4"/><path d="M8.2 17.5c0-2.3 1.7-4.1 3.8-4.1s3.8 1.8 3.8 4.1"/>',
    hand: '<path d="M8 13.5V6.2a1.5 1.5 0 0 1 3 0V11"/><path d="M11 10.5V4.7a1.5 1.5 0 0 1 3 0V11"/><path d="M14 10.5V6.2a1.5 1.5 0 0 1 3 0V11"/><path d="M17 11.2a1.5 1.5 0 0 1 3 0v3.3c0 3.6-2.9 6.5-6.5 6.5h-1.3c-2.2 0-3.9-.9-5.2-2.6l-2.6-3.3a1.6 1.6 0 0 1 2.4-2.1L8 14.4"/>',
    cross: '<circle cx="12" cy="12" r="6.8"/><path d="M12 2.5v4.2M12 17.3v4.2M2.5 12h4.2M17.3 12h4.2"/>' + d('<circle cx="12" cy="12" r="1.3"/>') + '<circle cx="12" cy="12" r="1.3"/>',
    tag: d('<path d="M3.5 12.4V5a1.5 1.5 0 0 1 1.5-1.5h7.4l8.1 8.1a1.5 1.5 0 0 1 0 2.1l-6.9 6.9a1.5 1.5 0 0 1-2.1 0z"/>') + '<path d="M3.5 12.4V5a1.5 1.5 0 0 1 1.5-1.5h7.4l8.1 8.1a1.5 1.5 0 0 1 0 2.1l-6.9 6.9a1.5 1.5 0 0 1-2.1 0z"/><circle cx="8" cy="8" r="1.4"/>',
    zoom: d('<circle cx="10.5" cy="10.5" r="6.5"/>') + '<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.4 15.4 20.5 20.5"/><path d="M10.5 8v5M8 10.5h5"/>',
    eye: '<path d="M2.5 12C4.6 7.6 8 5.2 12 5.2s7.4 2.4 9.5 6.8c-2.1 4.4-5.5 6.8-9.5 6.8S4.6 16.4 2.5 12z"/>' + d('<circle cx="12" cy="12" r="3.2"/>') + '<circle cx="12" cy="12" r="3.2"/>',
    lookat: d('<rect x="10" y="3" width="11" height="8.5" rx="2.2"/>') + '<rect x="10" y="3" width="11" height="8.5" rx="2.2"/><path d="M13 6.2h5M13 8.6h3"/><circle cx="8" cy="16" r="4"/><path d="M8 10.2v2.2M8 19.6v2.2M2.2 16h2.2M11.6 16h2.2"/><circle cx="8" cy="16" r=".9"/>',
    screen: d('<rect x="3" y="4" width="18" height="12" rx="2.5"/>') + '<rect x="3" y="4" width="18" height="12" rx="2.5"/><path d="M12 16v4M8.5 20h7"/>',
    dots: d('<circle cx="17" cy="8.5" r="2.4"/>') + '<circle cx="6.5" cy="6.5" r="1.5"/><circle cx="12.5" cy="4.5" r=".9"/><circle cx="17" cy="8.5" r="2.4"/><circle cx="9" cy="13.5" r="2.4"/><circle cx="15.5" cy="16" r="1.4"/><circle cx="5.5" cy="18.5" r="1"/><circle cx="19.5" cy="19" r="1"/>',
    bulb: d('<path d="M12 3a6 6 0 0 0-3.6 10.8c.7.5 1.1 1.3 1.1 2.1v.6h5v-.6c0-.8.4-1.6 1.1-2.1A6 6 0 0 0 12 3z"/>') + '<path d="M12 3a6 6 0 0 0-3.6 10.8c.7.5 1.1 1.3 1.1 2.1v.6h5v-.6c0-.8.4-1.6 1.1-2.1A6 6 0 0 0 12 3z"/><path d="M9.5 19h5M10.5 21.5h3"/>',
    cube: d('<path d="M12 3.2 20 7.6 12 12 4 7.6z"/>') + '<path d="M12 3.2 20 7.6v8.8L12 20.8 4 16.4V7.6z"/><path d="M4 7.6 12 12l8-4.4M12 12v8.8"/>',
    item: d('<ellipse cx="12" cy="20" rx="5.5" ry="1.6"/>') + '<path d="M12 3.4 17 6.2v5.6L12 14.6 7 11.8V6.2z"/><path d="M7 6.2 12 9l5-2.8M12 9v5.6"/><ellipse cx="12" cy="20" rx="5.5" ry="1.6"/>',
    grid: d('<rect x="9.2" y="9.2" width="5.6" height="5.6"/>') + '<rect x="3.5" y="3.5" width="17" height="17" rx="2.5"/><path d="M9.2 3.5v17M14.8 3.5v17M3.5 9.2h17M3.5 14.8h17"/>',
    heart: d('<path d="M12 20.2s-7.6-4.5-7.6-10.3A4.3 4.3 0 0 1 12 7.3a4.3 4.3 0 0 1 7.6 2.6c0 5.8-7.6 10.3-7.6 10.3z"/>') + '<path d="M12 20.2s-7.6-4.5-7.6-10.3A4.3 4.3 0 0 1 12 7.3a4.3 4.3 0 0 1 7.6 2.6c0 5.8-7.6 10.3-7.6 10.3z"/>',
    bars: d('<rect x="16.4" y="4" width="3.6" height="16" rx="1.2"/>') + '<rect x="4" y="13" width="3.6" height="7" rx="1.2"/><rect x="10.2" y="8.5" width="3.6" height="11.5" rx="1.2"/><rect x="16.4" y="4" width="3.6" height="16" rx="1.2"/>',
    compass: '<circle cx="12" cy="12" r="9"/>' + d('<path d="M15.6 8.4 13.5 13.5 8.4 15.6l2.1-5.1z"/>') + '<path d="M15.6 8.4 13.5 13.5 8.4 15.6l2.1-5.1z"/>',
    shield: d('<path d="M12 3 19.5 6v5.6c0 4.5-3.2 8.2-7.5 9.4-4.3-1.2-7.5-4.9-7.5-9.4V6z"/>') + '<path d="M12 3 19.5 6v5.6c0 4.5-3.2 8.2-7.5 9.4-4.3-1.2-7.5-4.9-7.5-9.4V6z"/><path d="M12 3v18"/>',
    potion: d('<path d="M7.3 14.5h9.4l1.6 2.5a2.2 2.2 0 0 1-1.9 3.4H7.6A2.2 2.2 0 0 1 5.7 17z"/>') + '<path d="M9.5 3h5M10.5 3v5.6L5.7 16.9A2.8 2.8 0 0 0 8.1 21h7.8a2.8 2.8 0 0 0 2.4-4.1L13.5 8.6V3"/><circle cx="13.2" cy="11.6" r=".8"/>',
    key: d('<rect x="9.25" y="5" width="5.5" height="5.5" rx="1.5"/>') + '<rect x="9.25" y="5" width="5.5" height="5.5" rx="1.5"/><rect x="3" y="12.5" width="5.5" height="5.5" rx="1.5"/><rect x="9.25" y="12.5" width="5.5" height="5.5" rx="1.5"/><rect x="15.5" y="12.5" width="5.5" height="5.5" rx="1.5"/>',
    run: d('<circle cx="14.5" cy="4.6" r="1.9"/>') + '<circle cx="14.5" cy="4.6" r="1.9"/><path d="M13.6 8.3 9.8 10.6 8 13.6"/><path d="M13.6 8.3 12 13.4l3.6 2.4 1.4 4.7"/><path d="M12 13.4 9.4 17.3 5.8 18.6"/><path d="M13.6 8.3l3 2.4h3"/>',
    chat: d('<path d="M6.5 4h11A2.5 2.5 0 0 1 20 6.5V14a2.5 2.5 0 0 1-2.5 2.5H10L6 20v-3.5h.5A2.5 2.5 0 0 1 4 14V6.5A2.5 2.5 0 0 1 6.5 4z"/>') + '<path d="M6.5 4h11A2.5 2.5 0 0 1 20 6.5V14a2.5 2.5 0 0 1-2.5 2.5H10L6 20v-3.5h.5A2.5 2.5 0 0 1 4 14V6.5A2.5 2.5 0 0 1 6.5 4z"/><path d="M8 8.6h8M8 12.2h5"/>',
    chest: d('<path d="M3.5 9V7.5A3.5 3.5 0 0 1 7 4h10a3.5 3.5 0 0 1 3.5 3.5V9z"/>') + '<path d="M3.5 9V7.5A3.5 3.5 0 0 1 7 4h10a3.5 3.5 0 0 1 3.5 3.5V9"/><rect x="3.5" y="9" width="17" height="11" rx="2"/><rect x="10.5" y="7.5" width="3" height="4.2" rx=".9"/>',
    camera: '<path d="M4 8.5A2 2 0 0 1 6 6.5h2l1.6-2.3h4.8L16 6.5h2a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/>' + d('<circle cx="12" cy="13" r="3.6"/>') + '<circle cx="12" cy="13" r="3.6"/>',
    bolt: d('<path d="M13.2 2.5 5 13.6h6.6l-1 7.9L19 10.4h-6.6z"/>') + '<path d="M13.2 2.5 5 13.6h6.6l-1 7.9L19 10.4h-6.6z"/>',
    people: d('<circle cx="9" cy="8" r="3.1"/>') + '<circle cx="9" cy="8" r="3.1"/><path d="M3.4 20c0-3.3 2.5-5.6 5.6-5.6s5.6 2.3 5.6 5.6"/><circle cx="16.6" cy="9" r="2.4"/><path d="M16 14.6c2.8-.3 5 1.8 5 5.4"/>',
    star: d(`<path d="${star(12, 12.6, 9.2, 4.1)}"/>`) + `<path d="${star(12, 12.6, 9.2, 4.1)}"/>`,
    map: d('<path d="M9 4 15 6.5V20L9 17.5z"/>') + '<path d="M3.5 6.5 9 4l6 2.5L20.5 4v13.5L15 20l-6-2.5L3.5 20z"/><path d="M9 4v13.5M15 6.5V20"/>',
    mic: d('<rect x="9" y="3" width="6" height="11" rx="3"/>') + '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0"/><path d="M12 17.5v3.5M9 21h6"/>',
    // cosmetics
    ring: d('<ellipse cx="12" cy="6.5" rx="7" ry="2.4"/>') + '<ellipse cx="12" cy="6.5" rx="7" ry="2.4"/><circle cx="12" cy="15.5" r="5"/>',
    crown: d('<path d="M4 17 3 7.5l5 3.6L12 5l4 6.1 5-3.6L20 17z"/>') + '<path d="M4 17 3 7.5l5 3.6L12 5l4 6.1 5-3.6L20 17z"/><path d="M4 20.5h16"/>',
    cape: d('<path d="M7.5 4h9l2 16.5-3.5-1.6-3 2-3-2-3.5 1.6z"/>') + '<path d="M7.5 4h9l2 16.5-3.5-1.6-3 2-3-2-3.5 1.6z"/><path d="M7.5 4c1.4 1.9 7.6 1.9 9 0"/>',
    wings: d('<path d="M11.5 9.5C9 5.5 4.6 4.6 2.5 6c.5 5 3.5 9 9 9.8zM12.5 9.5C15 5.5 19.4 4.6 21.5 6c-.5 5-3.5 9-9 9.8z"/>') + '<path d="M11.5 9.5C9 5.5 4.6 4.6 2.5 6c.5 5 3.5 9 9 9.8z"/><path d="M12.5 9.5C15 5.5 19.4 4.6 21.5 6c-.5 5-3.5 9-9 9.8z"/><path d="M5 9.2c1.6.9 3.4 2.5 4.6 4.6M19 9.2c-1.6.9-3.4 2.5-4.6 4.6"/>',
    orbit: '<ellipse cx="12" cy="12" rx="9.5" ry="4" transform="rotate(-22 12 12)"/>' + d('<circle cx="12" cy="12" r="3.2"/>') + '<circle cx="12" cy="12" r="3.2"/><circle cx="19.3" cy="8.6" r="1.4"/>',
    flame: d('<path d="M12 21c-3.6 0-6-2.4-6-5.7 0-3.4 2.6-5.3 3.4-8.6 1.6 1.2 2.5 2.9 2.6 4.3 1-1 1.6-2.5 1.5-4.4 2.6 1.9 4.5 4.9 4.5 8.7 0 3.3-2.4 5.7-6 5.7z"/>') + '<path d="M12 21c-3.6 0-6-2.4-6-5.7 0-3.4 2.6-5.3 3.4-8.6 1.6 1.2 2.5 2.9 2.6 4.3 1-1 1.6-2.5 1.5-4.4 2.6 1.9 4.5 4.9 4.5 8.7 0 3.3-2.4 5.7-6 5.7z"/>',
    sparkle: d('<path d="M11 3.5c.6 4.6 3.4 7.4 8 8-4.6.6-7.4 3.4-8 8-.6-4.6-3.4-7.4-8-8 4.6-.6 7.4-3.4 8-8z"/>') + '<path d="M11 3.5c.6 4.6 3.4 7.4 8 8-4.6.6-7.4 3.4-8 8-.6-4.6-3.4-7.4-8-8 4.6-.6 7.4-3.4 8-8z"/><path d="M19 2.8v3.4M17.3 4.5h3.4"/>',
    // small ui
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
  };
})();
SOL_ICONS.spark = SOL_ICONS.sparkle; SOL_ICONS.box = SOL_ICONS.cube; SOL_ICONS.drop = SOL_ICONS.item;
