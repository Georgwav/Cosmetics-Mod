package io.github.georgwav.cosmetics.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where the game's own HUD parts sit: each moved by an offset from its usual place (GUI pixels), set in the HUD
 * editor. From 1.21.6 Fabric's HUD elements draw each part moved (see {@link Hud#register()}); before 1.21.6 the
 * parts stay where the game puts them.
 */
public final class VanillaHud {
    /** A part of the game's HUD and where the game draws it on a screen of w x h GUI pixels: {x, y, width, height}. */
    public enum Part {
        HOTBAR("hotbar") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f - 91f, h - 29f, 182f, 29f};
            }
        },
        HEALTH("health") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f - 91f, h - 39f, 81f, 9f};
            }
        },
        ARMOR("armor") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f - 91f, h - 49f, 81f, 9f};
            }
        },
        FOOD("food") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f + 10f, h - 39f, 81f, 9f};
            }
        },
        AIR("air") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f + 10f, h - 49f, 81f, 9f};
            }
        },
        HELD_ITEM("held_item") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f - 70f, h - 61f, 140f, 12f};
            }
        },
        ACTION_BAR("action_bar") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f - 91f, h - 76f, 182f, 12f};
            }
        },
        EFFECTS("effects") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w - 151f, 1f, 150f, 51f};
            }
        },
        BOSS_BAR("boss_bar") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w / 2f - 91f, 2f, 182f, 18f};
            }
        },
        SCOREBOARD("scoreboard") {
            @Override
            public float[] box(int w, int h) {
                return new float[]{w - 102f, h / 2f - 50f, 100f, 100f};
            }
        };

        public final String id;

        Part(String id) {
            this.id = id;
        }

        /** Where the game draws it unmoved. */
        public abstract float[] box(int w, int h);
    }

    private static final Map<Part, float[]> OFFSETS = new EnumMap<>(Part.class);

    private VanillaHud() {
    }

    /** Whether this Minecraft version lets the client move the game's HUD (Fabric's HUD elements, from 1.21.6). */
    public static boolean supported() {
        //? if >=1.21.6 {
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    public static float dx(Part part) {
        float[] o = OFFSETS.get(part);
        return o == null ? 0f : o[0];
    }

    public static float dy(Part part) {
        float[] o = OFFSETS.get(part);
        return o == null ? 0f : o[1];
    }

    public static void move(Part part, float dx, float dy) {
        if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) {
            OFFSETS.remove(part);
        } else {
            OFFSETS.put(part, new float[]{Math.round(dx), Math.round(dy)});
        }
    }

    public static void reset() {
        OFFSETS.clear();
    }

    public static boolean moved() {
        return !OFFSETS.isEmpty();
    }

    /** The parts moved, as {part: [dx, dy]}. */
    public static JsonObject save() {
        JsonObject json = new JsonObject();
        for (Map.Entry<Part, float[]> e : OFFSETS.entrySet()) {
            JsonArray offset = new JsonArray();
            offset.add(e.getValue()[0]);
            offset.add(e.getValue()[1]);
            json.add(e.getKey().id, offset);
        }
        return json;
    }

    public static void load(JsonObject json) {
        OFFSETS.clear();
        for (Part part : Part.values()) {
            JsonElement v = json.get(part.id);
            if (v != null && v.isJsonArray() && v.getAsJsonArray().size() == 2) {
                move(part, v.getAsJsonArray().get(0).getAsFloat(), v.getAsJsonArray().get(1).getAsFloat());
            }
        }
    }
}
