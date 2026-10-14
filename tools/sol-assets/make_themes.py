import json, os, sys
OUT = sys.argv[1]
def rgba(s):
    # "rgba(r,g,b,a)" -> "#aarrggbb"
    v = s[s.index('(')+1:s.index(')')].split(',')
    r,g,b = (int(x) for x in v[:3]); a = round(float(v[3])*255)
    return "#%02x%02x%02x%02x" % (a,r,g,b)
def em(arr): return [{"type":a[0],"count":a[1],"colors":a[2],"size":a[3]} for a in arr]
def sky(t,m,h,g,warm=0.35): return {"top":t,"middle":m,"horizon":h,"glow":g,"sunWarmth":warm}
def fog(c,d,**k): o={"color":c,"density":d}; o.update(k); return o
T = []
def theme(id, order, name, line, ui, skyv, fogv, light, show, kf, **rest):
    o = {"name":name,"line":line,"order":order,"ui":ui,"sky":skyv,"fog":fogv,"light":light,"show":show}
    o.update(rest); o["keyframes"]=kf; T.append((id,o))
def split(s):
    v = s[s.index('(')+1:s.index(')')].split(',')
    return "#%02x%02x%02x" % tuple(int(x) for x in v[:3]), float(v[3])
def ui(a,p,r):
    pc, pa = split(p); rc, ra = split(r)
    return {"accent":a,"panel":pc,"panelAlpha":pa,"rim":rc,"rimAlpha":ra}

theme("sakura_dusk",1,"Sakura Dusk","Violet dusk over a coral horizon, blossoms falling.",
  ui("#f59ac0","rgba(52,30,56,.52)","rgba(255,190,215,.55)"),
  sky("#4a3866","#5d3f68","#a3606c","#fa9588",0.45), fog("#cf7b72",.35), {"tint":"#ffc4cc","strength":0.16},
  {"phase":"dusk","azimuth":90},
  {"day":{"sky":sky("#6f86c9","#9aa6d8","#f2b8c4","#ffd0d0",0.25),"fog":fog("#e8bcc4",.3),"light":{"tint":"#fff0f2","strength":0.08},"clouds":{"tint":"#fff2f4"}},
   "dawn":{"sky":sky("#5a4a7c","#8a6a98","#e8a0a8","#ffb8b0",0.4),"fog":fog("#dca0a4",.38)},
   "night":{"sky":sky("#120c22","#1e1430","#3a2440","#6a3a52",0.1),"fog":fog("#2a1a2e",.4),"light":{"tint":"#c8b0ff","strength":0.12},"clouds":{"tint":"#3a2a44"},"particleScale":0.6}},
  sun={"size":.026,"core":"#ffe1cf","halo":"#ff9b86","haloRadius":.3,"haloAlpha":.55,"x":.8,"y":.61},
  stars={"count":24,"palette":"warm","milkyWay":0.35},
  clouds={"tint":"#ffb4a8","litBySun":True}, wind=[0.35,0.0,0.12],
  particles=em([["petal",70,["#f7a8c4","#ffc9da","#ee93b4"],[3,6]],["blossom",22,["#f9b3cb","#ffd3e2"],[4,7]],["lens",3,["#f7a8c4"],[34,52]]]))

theme("frost_morning",2,"Frost Morning","Pale blue mist that swallows the world; snow in the air.",
  ui("#8fdcd0","rgba(30,44,58,.48)","rgba(220,240,255,.6)"),
  sky("#9fb5cc","#a8bcd2","#d7e0ea","#eef3f8",0.2), fog("#d3dde9",.85,voxyThinning=0.5), {"tint":"#e6f0ff","strength":0.14},
  {"phase":"day","azimuth":-90},
  {"dawn":{"sky":sky("#8f9cc0","#a8acd0","#e6d0dc","#f8e4ea",0.3),"fog":fog("#d4d0e2",.84)},
   "dusk":{"sky":sky("#8a8fb8","#a49cc4","#e0c8d8","#f4dce4",0.3),"fog":fog("#c8c4dc",.82)},
   "night":{"sky":sky("#0e1626","#18243a","#2e3e58","#4a5e7c",0.05),"fog":fog("#1e2a3c",.8),"light":{"tint":"#b8d0ff","strength":0.14},"clouds":{"tint":"#2c3850"}}},
  sun={"size":.02,"core":"#ffffff","halo":"#ffffff","haloRadius":.2,"haloAlpha":.35,"rays":10,"coreRays":6,"x":.28,"y":.3},
  moon={"ring":True,"dogs":True,"halo":"#d8ecff"},
  stars={"count":40,"palette":"natural","tint":"#dff0ff","milkyWay":0.3},
  clouds={"tint":"#f2f6fb","litBySun":True}, wind=[0.15,0.0,0.05],
  particles=em([["snow",120,["#ffffff","#eaf3ff"],[1.4,3.2]],["flake",14,["#ffffff"],[4,7]],["dust",18,["#ffffff"],[1,2]]]))

theme("ember_solstice",3,"Ember Solstice","A red sky, a haloed sun, sparks rising off the sand.",
  ui("#ffb347","rgba(60,22,8,.52)","rgba(255,200,120,.6)"),
  sky("#5e0f06","#7a1a07","#c2461a","#fe8a2c",0.35), fog("#f08a3a",.55,bands=4), {"tint":"#ffcf9a","strength":0.22},
  {"phase":"dusk","azimuth":90},
  {"day":{"sky":sky("#a8461e","#c8622a","#f0a058","#ffc27a",0.3),"fog":fog("#f4a868",.45),"light":{"tint":"#ffe0b8","strength":0.14},"clouds":{"tint":"#ffc89a"}},
   "dawn":{"sky":sky("#4a1410","#7a2a14","#d06a2a","#ffa04a",0.38)},
   "night":{"sky":sky("#160403","#260806","#4a1408","#8a2e10",0.1),"fog":fog("#3a1408",.55),"light":{"tint":"#ff9a6a","strength":0.16},"clouds":{"tint":"#4a1a0e"},"particleScale":0.8}},
  sun={"size":.042,"core":"#fff3c4","halo":"#ffb347","haloRadius":.36,"haloAlpha":.85,"rays":14,"coreRays":9,"x":.5,"y":.585},
  stars={"count":40,"palette":"warm","milkyWay":0.3},
  clouds={"tint":"#ff9a5c","litBySun":True}, wind=[0.4,0.0,0.1],
  particles=em([["ember",70,["#ffb24a","#ff7a2a","#ffd27a"],[1.4,3.2]],["dust",22,["#ffd9a0"],[1,2]]]))

theme("aurora",4,"Aurora","Deep teal night, green and violet ribbons, slow snow.",
  ui("#5effc1","rgba(8,30,36,.52)","rgba(120,255,210,.5)"),
  sky("#04070f","#08182a","#0f3342","#1f6a6a",0.1), fog("#0e2b36",.5), {"tint":"#9fffe0","strength":0.12},
  {"phase":"night","azimuth":180},
  {"day":{"sky":sky("#5a8ca8","#86b4c4","#cfe6e8","#e6f6f2",0.2),"fog":fog("#b8d4d8",.45),"light":{"tint":"#eafff8","strength":0.06},"clouds":{"tint":"#eef8f8"},"particleScale":0.5},
   "dawn":{"sky":sky("#1e3450","#3a5a74","#8ab4b8","#c8e4d8",0.3),"fog":fog("#4a6a74",.5)},
   "dusk":{"sky":sky("#142238","#2a4a60","#6a9aa0","#a8d8c8",0.3),"fog":fog("#3a5a64",.5)}},
  moon={"size":.02,"color":"#e8f0ff","halo":"#9fd9ff","haloRadius":.12,"haloAlpha":.3,"ring":True,"x":.17,"y":.2},
  stars={"count":150,"palette":"teal","milkyWay":0.55},
  aurora=["#4dffb0","#7a6bff"],
  clouds={"tint":"#16303a","litBySun":True}, wind=[0.12,0.0,0.04],
  particles=em([["snow",50,["#dff6ff"],[1.1,2.4]],["dust",20,["#7affd0"],[1,2]]]))

theme("nebula",5,"Nebula","Violet space close enough to touch, star dust and shooting stars.",
  ui("#c78bff","rgba(30,14,52,.52)","rgba(210,170,255,.55)"),
  sky("#07031a","#1d0b3d","#55245e","#ff7ab8",0.1), fog("#3a1747",.45), {"tint":"#d8b8ff","strength":0.14},
  {"phase":"night","azimuth":180},
  {"day":{"sky":sky("#6a5aa8","#9a84c4","#e2b4d4","#ffcce0",0.2),"fog":fog("#c8a8d0",.4),"light":{"tint":"#f4e8ff","strength":0.06},"clouds":{"tint":"#f4e4f4"},"particleScale":0.5},
   "dawn":{"sky":sky("#2a1a5a","#5a3a7c","#c87aa0","#ffa0c0",0.3),"fog":fog("#6a3a6a",.45)},
   "dusk":{"sky":sky("#1a0e40","#4a2468","#b05a90","#ff8ab8",0.3),"fog":fog("#5a2a5c",.45)}},
  stars={"count":210,"palette":"nebula","milkyWay":0.7,"shooting":True,"meteorNights":5},
  nebula=[{"x":.28,"y":.22,"r":.36,"color":"#7b2cff"},{"x":.72,"y":.32,"r":.3,"color":"#ff4fa3"},{"x":.55,"y":.12,"r":.24,"color":"#3b7bff"}],
  planets={"enabled":True,"style":"nebula"},
  clouds={"tint":"#2a1440","litBySun":True}, wind=[0.1,0.0,0.05],
  particles=em([["dust",70,["#ffd6f5","#c9b6ff","#ffffff"],[1,2.4]],["sparkle",12,["#ffffff"],[3,6]]]))

theme("firefly_meadow",6,"Firefly Meadow","Warm green dusk; fireflies blink low over the grass.",
  ui("#d6f26a","rgba(22,34,26,.52)","rgba(220,255,140,.5)"),
  sky("#18243a","#33484f","#c48c5a","#f2b36b",0.4), fog("#6d775a",.4), {"tint":"#ffe2a8","strength":0.16},
  {"phase":"dusk","azimuth":90},
  {"day":{"sky":sky("#5a8ac0","#86aac8","#e0d4a8","#f6e6b8",0.25),"fog":fog("#b8c49a",.35),"light":{"tint":"#fff6dc","strength":0.08},"clouds":{"tint":"#fff6e0"},"particleScale":0.2},
   "dawn":{"sky":sky("#24344a","#4a6068","#d4a070","#f8c888",0.38)},
   "night":{"sky":sky("#070c16","#0e1820","#1e2a26","#3a4a30",0.05),"fog":fog("#141c16",.45),"light":{"tint":"#d8ffb0","strength":0.1},"clouds":{"tint":"#1a221c"},"particleScale":1.2}},
  sun={"size":.026,"core":"#ffe2a8","halo":"#ffb35c","haloRadius":.24,"haloAlpha":.5,"x":.86,"y":.63},
  stars={"count":30,"palette":"warm","milkyWay":0.4},
  clouds={"tint":"#f2b88a","litBySun":True}, wind=[0.08,0.0,0.03],
  particles=em([["firefly",46,["#e9ff7a","#fff3a0"],[1.4,2.8]],["dust",14,["#fff0b0"],[1,2]]]))

theme("monsoon",7,"Monsoon","Slate clouds, heavy rain, lightning that lights the whole sky.",
  ui("#7fc4ff","rgba(20,28,36,.55)","rgba(160,210,255,.5)"),
  sky("#1d232b","#2f3842","#56626e","#7b8794",0.05), fog("#4f5a65",.7), {"tint":"#b8c8d8","strength":0.14},
  {"phase":"day","azimuth":0},
  {"dawn":{"sky":sky("#1a1e28","#2c323e","#5a5a66","#8a7e84",0.15)},
   "dusk":{"sky":sky("#181c24","#282e38","#4e5260","#7a7480",0.15)},
   "night":{"sky":sky("#06080c","#0c1016","#161c24","#28323e",0.0),"fog":fog("#12161c",.7),"light":{"tint":"#90a8c8","strength":0.12},"clouds":{"tint":"#14181e"}}},
  sun=None,
  stars={"count":20,"palette":"natural","milkyWay":0.2,"through":"cloudGaps"},
  lightning=True,
  clouds={"tint":"#4a5460","litBySun":False}, wind=[0.6,0.0,0.25],
  particles=em([["rain",150,["#b8c8d8"],[1,1.6]]]))

theme("maple_afternoon",8,"Maple Afternoon","Low gold sun, cool blue sky, leaves tumbling past.",
  ui("#ff8a3d","rgba(40,28,20,.52)","rgba(255,190,120,.55)"),
  sky("#4f76a3","#7d9bbb","#f0c48f","#ffd29a",0.4), fog("#e2b98d",.45), {"tint":"#ffd8a4","strength":0.18},
  {"phase":"dusk","azimuth":90},
  {"day":{"sky":sky("#4a7ab8","#78a0cc","#d8e2e8","#f2ead8",0.25),"fog":fog("#d8d4c4",.35),"light":{"tint":"#fff2dc","strength":0.08},"clouds":{"tint":"#fffaf0"}},
   "dawn":{"sky":sky("#46628c","#7890b0","#e8b890","#ffc898",0.38)},
   "night":{"sky":sky("#0a1020","#141e32","#2a2e3a","#4a3a34",0.05),"fog":fog("#1e1e24",.45),"light":{"tint":"#c8c0ff","strength":0.1},"clouds":{"tint":"#22242c"},"particleScale":0.6}},
  sun={"size":.032,"core":"#fff2cc","halo":"#ffcf7a","haloRadius":.26,"haloAlpha":.6,"x":.2,"y":.58},
  stars={"count":40,"palette":"natural","milkyWay":0.4},
  planets={"enabled":True,"style":"natural","daytime":["venus","saturn"]},
  clouds={"tint":"#ffd2a0","litBySun":True}, wind=[0.55,0.0,0.2],
  particles=em([["leaf",38,["#d9572b","#e8902e","#b8411f","#f2b13a"],[4,8]],["dust",14,["#fff0c8"],[1,2]]]))

theme("eclipse",9,"Eclipse","A black sun inside a burning ring; ash drifting down.",
  ui("#ff5a3c","rgba(30,8,8,.55)","rgba(255,120,90,.55)"),
  sky("#040405","#12070a","#5e130f","#ff4b2b",0.3), fog("#3a0d0b",.5), {"tint":"#ff9a7a","strength":0.18},
  {"phase":"day","azimuth":0},
  {"dawn":{"sky":sky("#060406","#160a0c","#6a1a12","#ff5a34",0.32)},
   "dusk":{"sky":sky("#060406","#160a0c","#6a1a12","#ff5a34",0.32)},
   "night":{"sky":sky("#020203","#080405","#2a0a08","#7a2014",0.1),"fog":fog("#1e0706",.5),"light":{"tint":"#ff8060","strength":0.14}}},
  sun={"size":.055,"core":"#000000","halo":"#ff6a3a","haloRadius":.3,"haloAlpha":.7,"rays":0,"coreRays":0,"plasma":False,"sunDogs":False,"pillar":False,"shimmer":False,"x":.5,"y":.34},
  eclipse={"x":.5,"y":.34,"size":.055,"corona":"#ff6a3a"},
  stars={"count":60,"palette":"warm","milkyWay":0.3},
  clouds={"tint":"#2a0e0c","litBySun":False}, wind=[0.2,0.0,0.08],
  particles=em([["ash",70,["#8a8380","#b5aca8","#5f5856"],[1.4,3.2]],["ember",12,["#ff7a3a"],[1.2,2.4]]]))

theme("deep_field",10,"Deep Field","Black and white space: a spiral galaxy turning overhead, star dust drifting through.",
  ui("#ffffff","rgba(6,6,8,.64)","rgba(255,255,255,.55)"),
  sky("#000000","#060608","#15151a","#e4e4ea",0.05), fog("#0b0b0e",.5), {"tint":"#eef0f6","strength":0.06},
  {"phase":"night","azimuth":180},
  {"day":{"sky":sky("#3a3c44","#5a5c66","#a8aab2","#d8d8de",0.1),"fog":fog("#8a8c94",.45),"light":{"tint":"#ffffff","strength":0.0},"clouds":{"tint":"#e0e0e4"},"particleScale":0.4},
   "dawn":{"sky":sky("#101014","#24242c","#6a6a74","#b0b0ba",0.15),"fog":fog("#3a3a42",.5)},
   "dusk":{"sky":sky("#0c0c10","#1e1e26","#5a5a64","#a0a0aa",0.15),"fog":fog("#303038",.5)}},
  stars={"count":280,"palette":"mono","milkyWay":0.8,"shooting":True,"meteorNights":6},
  galaxy={"x":.66,"y":.26,"r":.34,"tilt":.42},
  planets={"enabled":True,"style":"mono"},
  clouds={"tint":"#18181c","litBySun":False}, wind=[0.1,0.0,0.04],
  particles=em([["dust",80,["#ffffff","#d0d0d8","#9a9aa3"],[1,2.4]],["sparkle",14,["#ffffff"],[3,6]]]))

theme("silver_night",11,"Silver Night","A huge square moon, silver haze, pollen drifting in its light.",
  ui("#b9c8ff","rgba(16,22,44,.52)","rgba(200,215,255,.5)"),
  sky("#070b1c","#121c3a","#33436a","#8ea3d6",0.1), fog("#26314f",.5), {"tint":"#c8d4ff","strength":0.16},
  {"phase":"night","azimuth":-90},
  {"day":{"sky":sky("#6a84c0","#94a8d4","#d4dcef","#eef2fb",0.2),"fog":fog("#c0cae0",.42),"light":{"tint":"#f2f4ff","strength":0.06},"clouds":{"tint":"#f4f6ff"},"particleScale":0.5},
   "dawn":{"sky":sky("#1e2a50","#3a4a78","#8a98c0","#c8d0ea",0.3),"fog":fog("#4a5678",.48)},
   "dusk":{"sky":sky("#161e40","#2e3a66","#7684b0","#b8c4e6",0.3),"fog":fog("#3e4a6c",.48)}},
  moon={"size":.045,"color":"#eef3ff","halo":"#b9c8ff","haloRadius":.32,"haloAlpha":.45,"ring":True,"dogs":True,"x":.7,"y":.28},
  stars={"count":120,"palette":"silver","tint":"#dfe6ff","milkyWay":0.45},
  clouds={"tint":"#28324e","litBySun":True}, wind=[0.12,0.0,0.05],
  particles=em([["dust",60,["#dfe7ff","#b9c8ff"],[1,2.4]],["sparkle",8,["#ffffff"],[3,5]]]))

theme("grace",12,"Grace","A gold rune of linked rings burns above a bank of lit cloud, deep in dark green fog.",
  ui("#e8c26a","rgba(8,20,17,.62)","rgba(240,205,120,.5)"),
  sky("#040d0b","#0b2a28","#14403d","#215049",0.1), fog("#10302d",.78), {"tint":"#b8e0c8","strength":0.12},
  {"phase":"night","azimuth":180},
  {"day":{"sky":sky("#2c5a54","#4a7a70","#86aa98","#a8c8b4",0.15),"fog":fog("#4a7068",.72),"light":{"tint":"#e8f8ee","strength":0.06},"clouds":{"tint":"#a8c4b8"}},
   "dawn":{"sky":sky("#0c2420","#1e443e","#4a6e5c","#c09060",0.3),"fog":fog("#244a44",.76)},
   "dusk":{"sky":sky("#081c18","#183c36","#3e6050","#b07e50",0.3),"fog":fog("#1c403a",.76)}},
  stars={"count":12,"palette":"gold","tint":"#ffe0a0","milkyWay":0.0,"deepSky":False,"through":"fog"},
  planets={"enabled":False},
  sigil={"x":.56,"y":.305,"r":.104,"color":"#ffd27a","halo":"#e47a26"},
  mist={"color":"#285c56","warm":"#e6a256","bands":7,"alpha":1.2},
  clouds={"tint":"#1e3e38","litBySun":False}, wind=[0.15,0.0,0.06],
  particles=em([["ember",56,["#ffcf6a","#ffb347","#fff0b8"],[1.1,2.3]],["dust",40,["#7fc4ab","#5a9a85"],[1,2]],["ash",10,["#1c2e28","#26392f"],[2,3.5]]]))

theme("empty",13,"Empty","Vanilla sky, Atmosphere off, neutral colours. Set it up yourself.",
  ui("#9aa3b5","rgba(16,18,24,.62)","rgba(255,255,255,.4)"),
  sky("#5f8fe8","#78a7ff","#b9d1ff","#d4e3ff",0.3), fog("#c0d8ff",.2), {"tint":"#ffffff","strength":0.0},
  {"phase":"day","azimuth":0}, {},
  sun={"size":.03,"core":"#fff3a8","halo":"#fff2c0","haloRadius":.2,"haloAlpha":.3,"x":.74,"y":.24},
  stars={"count":0,"milkyWay":0.0,"deepSky":False,"shooting":False,"comets":False},
  planets={"enabled":False},
  flags={"vanillaSun":True,"noRidges":True},
  particles=[])

os.makedirs(OUT, exist_ok=True)
for id,o in T:
    if o.get("sun", 1) is None: o["sun"] = None
    with open(os.path.join(OUT, id + ".json"), "w") as f:
        json.dump(o, f, indent=2); f.write("\n")
print(len(T), "themes")
