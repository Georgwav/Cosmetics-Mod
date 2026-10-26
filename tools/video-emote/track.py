"""Runs MediaPipe's heavy pose landmarker over every frame of a video; writes each frame's picture and 3D landmarks.

    python3 track.py video.mp4 pose_landmarker_heavy.task pose.json
"""
import json, sys
import cv2
import mediapipe as mp
from mediapipe.tasks import python as mpt
from mediapipe.tasks.python import vision

video, model, out = sys.argv[1], sys.argv[2], sys.argv[3]
opts = vision.PoseLandmarkerOptions(
    base_options=mpt.BaseOptions(model_asset_path=model),
    running_mode=vision.RunningMode.VIDEO, num_poses=1,
    min_pose_detection_confidence=0.5, min_pose_presence_confidence=0.5, min_tracking_confidence=0.5)
cap = cv2.VideoCapture(video)
fps = cap.get(cv2.CAP_PROP_FPS)
frames = []
with vision.PoseLandmarker.create_from_options(opts) as lm:
    i = 0
    while True:
        ok, bgr = cap.read()
        if not ok:
            break
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        img = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
        r = lm.detect_for_video(img, int(i * 1000 / fps))
        if r.pose_world_landmarks:
            w = [[p.x, p.y, p.z, p.visibility] for p in r.pose_world_landmarks[0]]
            n = [[p.x, p.y, p.z, p.visibility] for p in r.pose_landmarks[0]]
            frames.append({"i": i, "world": w, "image": n})
        else:
            frames.append({"i": i, "world": None, "image": None})
        i += 1
json.dump({"fps": fps, "width": int(cap.get(3)), "height": int(cap.get(4)), "frames": frames}, open(out, "w"))
print(len(frames), "frames,", sum(f["world"] is not None for f in frames), "with a pose, fps", fps)
