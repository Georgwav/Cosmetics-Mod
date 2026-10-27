"""Side by side: the video's frame and the game's nearest frame at the same moments.

    python3 compare.py video.mp4 <game folder with emote-frame-*.png> out.png 0.8,1.6,2.4
"""
import glob, os, sys
import cv2
import numpy as np
video, frames_dir, out = sys.argv[1], sys.argv[2], sys.argv[3]
times = [float(t) for t in sys.argv[4].split(",")]
shots = sorted((int(os.path.basename(f)[12:17]), f) for f in glob.glob(frames_dir + "/emote-frame-*.png"))
shots = [s for s in shots if s[0] >= 150]
cap = cv2.VideoCapture(video)
clip = []  # (milliseconds, frame), read once: short clips, and a phone's variable frame rate makes seeking unreliable
while True:
    at = cap.get(cv2.CAP_PROP_POS_MSEC)
    ok, img = cap.read()
    if not ok:
        break
    clip.append((at, img))
pairs = []
for t in times:
    v = min(clip, key=lambda c: abs(c[0] - t * 1000))[1]
    v = cv2.resize(v[v.shape[0] // 8:, :], (240, 373))
    ms, f = min(shots, key=lambda s: abs(s[0] - t * 1000))
    g = cv2.imread(f)
    h, w = g.shape[:2]
    g = cv2.resize(g[60:h, w // 2 - 150:w // 2 + 150], (240, int(240 * (h - 60) / 300)))
    g = cv2.resize(g, (240, 373))
    cell = np.hstack([v, g])
    cv2.putText(cell, f"{t:.1f}s", (8, 26), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
    pairs.append(cell)
rows = [np.hstack(pairs[i:i + 4]) for i in range(0, len(pairs), 4)]
w = rows[0].shape[1]
rows = [r if r.shape[1] == w else np.hstack([r, np.zeros((r.shape[0], w - r.shape[1], 3), np.uint8)]) for r in rows]
cv2.imwrite(out, np.vstack(rows))
