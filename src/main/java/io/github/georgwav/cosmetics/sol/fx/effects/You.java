package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.FxColour;
import io.github.georgwav.cosmetics.sol.fx.FxDraw;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxKind;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxOut;
import io.github.georgwav.cosmetics.sol.fx.FxPool;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxText;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.SkinPixels;
import io.github.georgwav.cosmetics.ui.TilePictures;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * You (C.4): what you (and the players near you) do, made a little louder, only on your screen.
 * <ul>
 * <li>Naruto run: while you sprint your arms sweep back and you lean 25 degrees into it. On your model in third person
 * (and on the copy of you the settings page shows); first person stays the game's.</li>
 * <li>Chat bubbles: what a player says pops up in a pixel bubble over their head for 4 s.</li>
 * <li>Eating effects: crumbs in the food's own colours, or drips in the potion's, burst at the mouth.</li>
 * </ul>
 */
public final class You extends FxModule {
    private static final List<String> ONE = List.of("one");

    public final Setting.Toggle naruto = add(new Setting.Toggle("naruto_on", true).group("naruto"));
    public final Setting.Slider narutoLean = add(new Setting.Slider("naruto_lean", 25, 10, 40, 5, v -> Math.round(v) + "°")
            .group("naruto"));
    public final FxSettings bubbles = fx("chat_bubble", ONE, new FxSettings.Spec().toggle(true).noSpeed().noAmount().noGlow()
            .firstPerson());
    public final Setting.Choice bubbleWhose = add(new Setting.Choice("chat_bubble_whose", "everyone", List.of("everyone", "others"))
            .group("chat_bubble"));
    public final Setting.Toggle eating = add(new Setting.Toggle("eating_on", true).group("eating"));
    public final Setting.Slider eatingAmount = add(new Setting.Slider("eating_amount", 100, 50, 200, 10, v -> Math.round(v) + "%")
            .group("eating"));
    public final Setting.Slider eatingSize = add(new Setting.Slider("eating_size", 100, 50, 200, 10, v -> Math.round(v) + "%")
            .group("eating"));
    public final Setting.Choice eatingWhose = add(new Setting.Choice("eating_whose", "everyone", List.of("everyone", "yours"))
            .group("eating"));
    public final Setting.Toggle eatingFirst = add(new Setting.Toggle("eating_first_person", false).group("eating"));

    /** Your lean into the run, this tick and the last (0 standing up .. 1 full). */
    private float lean, leanWas;

    public You() {
        super("you", Category.VIEW, "person");
        narutoLean.shownWhen(naruto::get);
        bubbleWhose.shownWhen(bubbles.on::get);
        for (Setting<?> s : List.of(eatingAmount, eatingSize, eatingWhose, eatingFirst)) {
            s.shownWhen(eating::get);
        }
        FxEvents.EAT.add(new FxEvents.Eat() {
            @Override
            public boolean listening() {
                return enabled() && eating.get();
            }

            @Override
            public void ate(LivingEntity who, ItemStack food) {
                Minecraft mc = Minecraft.getInstance();
                boolean yours = who == mc.player;
                if (!yours && eatingWhose.get().equals("yours")) {
                    return;
                }
                if (yours && mc.options.getCameraType().isFirstPerson() && !eatingFirst.get()) {
                    return;
                }
                var look = who.getLookAngle();
                double x = who.getX() + look.x * 0.3, y = who.getEyeY() - 0.18 + look.y * 0.2, z = who.getZ() + look.z * 0.3;
                burst(FxWorld.POOL, x, y, z, look.x, look.z, food, 0f);
            }
        });
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register(
                (message, signed, sender, params, at) -> said(signed != null ? signed.sender() : null,
                        signed != null ? signed.signedContent() : null, message));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                said(null, null, message);
            }
        });
    }

    // ------------------------------------------------------------------ Naruto run

    @Override
    protected void tick(Minecraft mc) {
        Player you = mc.player;
        leanWas = lean;
        boolean running = naruto.get() && you != null && you.isSprinting() && !you.isFallFlying() && !you.isSwimming()
                && !you.isPassenger() && !you.getAbilities().flying;
        float target = running ? 1f : 0f;
        // Into the lean in three ticks, out of it in four.
        lean += (target - lean) * (running ? 0.5f : 0.4f);
        if (Math.abs(target - lean) < 0.02f) {
            lean = target;
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        lean = leanWas = 0f;
    }

    /** How far you lean into the run now (0..1), for the checks. */
    public float lean() {
        return lean;
    }

    /**
     * Poses the model of entity {@code id} for the Naruto run when it's you running (the game's PlayerModel, right
     * after it posed it). Returns whether it changed anything.
     */
    public static boolean pose(int id, ModelPart head, ModelPart body, ModelPart leftArm, ModelPart rightArm, ModelPart leftLeg,
                               ModelPart rightLeg) {
        You m = io.github.georgwav.cosmetics.client.Modules.YOU;
        Minecraft mc = Minecraft.getInstance();
        if (m == null || !m.enabled() || mc.player == null || id != mc.player.getId()) {
            return false;
        }
        float k = m.leanWas + (m.lean - m.leanWas) * FrameInfo.partialTick;
        if (k <= 0.001f) {
            return false;
        }
        PoseSnapshot.narutoRun(head, body, leftArm, rightArm, leftLeg, rightLeg, (float) Math.toRadians(m.narutoLean.get()) * k, k);
        return true;
    }

    // ------------------------------------------------------------------ chat bubbles

    /** "<Name> text", "[Rank] Name: text" or "Name » text": a player's line as servers write it. */
    private static final Pattern LINE = Pattern.compile(
            "^(?:\\[[^\\]]{1,24}\\]\\s*)*<?([A-Za-z0-9_]{2,16})>?\\s*(?::|»|>>|>)?\\s+(.+)$");
    /** The newest bubble of each player (an older one still fading stays hidden). */
    private static final Int2IntOpenHashMap NEWEST = new Int2IntOpenHashMap();
    private static int bubbleSeq;

    /** A chat line arrived: from {@code sender} saying {@code text}, or a line to read the player and text from. */
    private void said(UUID sender, String text, Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || !bubbles.on() || mc.level == null || mc.player == null) {
            return;
        }
        Player who = sender != null ? mc.level.getPlayerByUUID(sender) : null;
        if (who == null || text == null) {
            Matcher m = LINE.matcher(line.getString().strip());
            if (!m.matches()) {
                return;
            }
            String name = m.group(1);
            for (Player p : mc.level.players()) {
                if (p.getName().getString().equalsIgnoreCase(name)) {
                    who = p;
                    text = m.group(2);
                    break;
                }
            }
        }
        if (who == null || text == null || text.isBlank() || who.distanceToSqr(mc.player) > 48 * 48) {
            return;
        }
        if (who == mc.player && bubbleWhose.get().equals("others")) {
            return;
        }
        bubble(FxWorld.POOL, who.getId(), who.getX(), who.getY() + who.getBbHeight(), who.getZ(), text, 0f);
    }

    /** The bubble's text in lines that fit: at most four, the last cut short with dots. */
    static String wrap(String text) {
        String[] words = text.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT).split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            // A word too long for a line is cut into pieces that fit.
            while (FxText.width(w) > BUBBLE_WIDTH) {
                int cut = w.length() - 1;
                while (cut > 1 && FxText.width(w.substring(0, cut)) > BUBBLE_WIDTH) {
                    cut--;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(w.substring(0, cut));
                w = w.substring(cut);
            }
            String next = line.isEmpty() ? w : line + " " + w;
            if (FxText.width(next) <= BUBBLE_WIDTH) {
                line.setLength(0);
                line.append(next);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(w);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.size() > BUBBLE_LINES) {
            List<String> kept = new ArrayList<>(lines.subList(0, BUBBLE_LINES));
            String last = kept.get(BUBBLE_LINES - 1);
            while (!last.isEmpty() && FxText.width(last + "...") > BUBBLE_WIDTH) {
                last = last.substring(0, last.length() - 1);
            }
            kept.set(BUBBLE_LINES - 1, last.strip() + "...");
            lines = kept;
        }
        return String.join("\n", lines);
    }

    private void bubble(FxPool pool, int entity, double x, double y, double z, String text, float delay) {
        Fx f = start(pool, bubbles, BUBBLE, x, y, z, 4f);
        bubbles.apply(f, 0);
        f.entity = entity;
        f.text = wrap(text);
        f.i0 = ++bubbleSeq;
        if (entity != 0) {
            NEWEST.put(entity, f.i0);
        }
        FxPool.delay(f, delay);
    }

    // ------------------------------------------------------------------ eating

    /** Each food's three main colours, by item. */
    private static final Map<String, int[]> FOOD_COLOURS = new HashMap<>();

    /** A food's colours: its picture's three commonest (dark outlines left out), or a crumb brown when it has none. */
    static int[] foodColours(ItemStack food) {
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(food.getItem()));
        int[] c = FOOD_COLOURS.get(id);
        if (c == null) {
            c = new int[]{0xFFC08A4C, 0xFF9A6A34, 0xFFE0B070};
            try {
                int colon = id.indexOf(':');
                String ns = colon < 0 ? "minecraft" : id.substring(0, colon), path = colon < 0 ? id : id.substring(colon + 1);
                var px = TilePictures.read(ns + ":textures/item/" + path + ".png");
                if (px != null) {
                    c = commonest(px.argb(), c);
                }
            } catch (RuntimeException e) {
                // No picture: the crumb brown.
            }
            FOOD_COLOURS.put(id, c);
        }
        return c;
    }

    /** The three commonest opaque colours of a picture (in 4-bit steps, dark outlines left out), or {@code fallback}. */
    static int[] commonest(int[] argb, int[] fallback) {
        Map<Integer, int[]> bins = new HashMap<>();
        for (int c : argb) {
            if ((c >>> 24) < 128 || Colors.luma(c) < 0.12f) {
                continue;
            }
            int key = (c >> 4 & 0xF) | (c >> 8 & 0xF0) | (c >> 12 & 0xF00);
            int[] e = bins.computeIfAbsent(key, k -> new int[4]);
            e[0]++;
            e[1] += c >> 16 & 0xFF;
            e[2] += c >> 8 & 0xFF;
            e[3] += c & 0xFF;
        }
        if (bins.isEmpty()) {
            return fallback;
        }
        List<int[]> sorted = new ArrayList<>(bins.values());
        sorted.sort((a, b) -> Integer.compare(b[0], a[0]));
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            int[] e = sorted.get(Math.min(i, sorted.size() - 1));
            out[i] = 0xFF000000 | (e[1] / e[0]) << 16 | (e[2] / e[0]) << 8 | (e[3] / e[0]);
        }
        return out;
    }

    /** Whether it's drunk (a potion's drips) rather than eaten (crumbs), and the drink's colour (0: its picture's). */
    static int drinkColour(ItemStack s) {
        var potion = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return potion != null ? 0xFF000000 | potion.getColor() : 0;
    }

    private static boolean drunk(ItemStack s) {
        return String.valueOf(s.getUseAnimation()).toLowerCase(Locale.ROOT).equals("drink");
    }

    /** One burst at the mouth (x, y, z), the face turned along (lookX, lookZ). */
    private void burst(FxPool pool, double x, double y, double z, double lookX, double lookZ, ItemStack food, float delay) {
        boolean drink = drunk(food);
        int potion = drink ? drinkColour(food) : 0;
        int[] colours = potion != 0 ? new int[]{potion, Colors.mixRgb(potion, 0xFFFFFFFF, 0.35f), Colors.mixRgb(potion, 0xFF000000, 0.3f)}
                : foodColours(food);
        burst(pool, x, y, z, lookX, lookZ, colours, drink, delay);
    }

    private void burst(FxPool pool, double x, double y, double z, double lookX, double lookZ, int[] colours, boolean drink, float delay) {
        Fx f = pool == FxWorld.POOL ? FxWorld.spawn(EAT, x, y, z, 0.8f) : pool.spawn(EAT, x, y, z, 0.8f, 2);
        f.base = 0.8f;
        FxColour.fixed(f, colours[0]);
        f.palette[0] = colours[0];
        f.palette[1] = colours[1];
        f.palette[2] = colours[2];
        f.style = drink ? 1 : 0;
        f.tx = lookX;
        f.tz = lookZ;
        f.amount = (float) (eatingAmount.get() / 100.0);
        f.size = (float) (eatingSize.get() / 100.0);
        f.c = ground(pool, x, z, y) - (float) y;
        f.seed = (int) (System.nanoTime() >>> 6) ^ (int) (x * 131) ^ (int) (z * 71);
        FxPool.delay(f, delay);
    }

    // ------------------------------------------------------------------ Try it

    /** The copy in the Naruto run stands in for the dummy while it shows. */
    @Override
    public boolean hidesDummy() {
        return naruto.get();
    }

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        float longest = 0f;
        double a = Math.toRadians(dummy.yaw);
        double fx = -Math.sin(a), fz = Math.cos(a);
        if (naruto.get()) {
            // Side on, so the lean and the arms swept back show.
            PoseSnapshot.Pose p = new PoseSnapshot.Pose();
            p.bodyYaw = dummy.yaw + 90f;
            p.headYaw = dummy.yaw + 90f;
            p.walk = 2.2f;
            p.walkSpeed = 1f;
            p.lean = (float) Math.toRadians(narutoLean.get());
            PoseSnapshot copy = new PoseSnapshot();
            copy.capture(SkinPixels.current(), p, false);
            Fx f = pool == FxWorld.POOL ? FxWorld.spawn(RUN, dummy.x, dummy.y, dummy.z, 2f) : pool.spawn(RUN, dummy.x, dummy.y, dummy.z, 2f, 2);
            f.base = 2f;
            f.copy = copy;
            f.snap = false;
            longest = 2f;
        }
        if (bubbles.on()) {
            float h = dummy.body != null ? dummy.body.height : 1.8f;
            bubble(pool, 0, dummy.x, dummy.y + h, dummy.z, "Hi! GG, that was close", 0.1f);
            longest = Math.max(longest, 0.1f + bubbles.life(4f));
        }
        if (eating.get()) {
            float eye = (dummy.body != null ? dummy.body.height : 1.8f) * 0.9f;
            int[] apple = foodColours(new ItemStack(net.minecraft.world.item.Items.APPLE));
            for (int k = 0; k < 3; k++) {
                burst(pool, dummy.x + fx * 0.3, dummy.y + eye - 0.18, dummy.z + fz * 0.3, fx, fz, apple, false, 0.15f + k * 0.2f);
            }
            int potion = 0xFFF82423;
            burst(pool, dummy.x + fx * 0.3, dummy.y + eye - 0.18, dummy.z + fz * 0.3, fx, fz,
                    new int[]{potion, Colors.mixRgb(potion, 0xFFFFFFFF, 0.35f), Colors.mixRgb(potion, 0xFF000000, 0.3f)}, true, 0.85f);
            longest = Math.max(longest, 1.65f);
        }
        return longest > 0f ? longest : 0.6f;
    }

    // ------------------------------------------------------------------ the kinds

    private static final float T = FxDraw.TEXEL;
    /** A bubble's lines at most this wide (font pixels), and this many. */
    static final int BUBBLE_WIDTH = 84, BUBBLE_LINES = 4;
    private static final float FONT = 0.025f;
    private static final float[] R = new float[3], U = new float[3];
    private static final int INK = 0xFF14131C;

    /** The copy of you in the Naruto run (Try it), standing where the dummy was. */
    static final FxKind RUN = new FxKind("you_naruto", 2) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (fx.copy == null || out.glowPass()) {
                return;
            }
            FxDraw.origin(out, fx);
            float in = Ramp.steps(Math.min(1f, age / 0.2f), 3, true);
            float left = Ramp.steps(Math.min(1f, (fx.end - now) / 0.25f), 3, true);
            fx.copy.draw(out, 0f, 0f, 0f, 1f, 0f, null, Colors.withAlpha(0xFFFFFFFF, Math.min(in, left)), PoseSnapshot.WHOLE, 0f);
        }
    };

    /** A chat bubble: Sol's dark slab with a rim in the effect's colour, the words in pixel type, a tail down to the head. */
    static final FxKind BUBBLE = new FxKind("you_bubble", 16) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (fx.text == null) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            double x = fx.x, y = fx.y, z = fx.z;
            if (fx.entity != 0) {
                if (NEWEST.get(fx.entity) != fx.i0 || mc.level == null) {
                    return;
                }
                Entity e = mc.level.getEntity(fx.entity);
                if (e == null) {
                    return;
                }
                if (e == mc.player && mc.options.getCameraType().isFirstPerson()) {
                    You m = io.github.georgwav.cosmetics.client.Modules.YOU;
                    if (m == null || !m.bubbles.inFirstPerson()) {
                        return;
                    }
                }
                float pt = FrameInfo.partialTick;
                x = e.xo + (e.getX() - e.xo) * pt;
                y = e.yo + (e.getY() - e.yo) * pt + e.getBbHeight();
                z = e.zo + (e.getZ() - e.zo) * pt;
            }
            float t = fx.time(age);
            float a = Ramp.steps(Math.min(1f, (4f - t) / 0.4f), 3, fx.snap) * fx.early(now);
            if (a <= 0f) {
                return;
            }
            // Pops in: 1.25 times, then its size, in three steps.
            float pop = Ramp.pop(t, 0.15f, fx.snap);
            float px = FONT * fx.size * pop;
            out.origin(x, y, z);
            out.right(R);
            out.up(U);
            String text = fx.text;
            int lines = 1, widest = 0, start = 0;
            for (int i = 0; i <= text.length(); i++) {
                if (i == text.length() || text.charAt(i) == '\n') {
                    widest = Math.max(widest, FxText.width(text, start, i));
                    if (i < text.length()) {
                        lines++;
                    }
                    start = i + 1;
                }
            }
            int pad = 3, lineH = 8;
            float w = (widest + pad * 2) * px, h = (lines * lineH - 2 + pad * 2) * px;
            // Its bottom (the tail's tip) half a block over the head, above a name tag.
            float bottom = 0.62f, cy = bottom + 3 * px + h / 2f;
            if (out.glowPass()) {
                // A soft light of the rim's colour round it.
                float gk = FxBits.glowK(fx);
                if (gk > 0f) {
                    Vfx.billboard(out, FxSoft.GLOW, U[0] * cy, U[1] * cy, U[2] * cy, w * 0.85f, h * 1.1f, 0f,
                            Vfx.with(fx.tone(Ramp.MAIN, 0, now), 0.35f * a * gk));
                }
                return;
            }
            out.use(FxSprites.atlas());
            float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
            int rim = Colors.withAlpha(fx.tone(Ramp.MAIN, 0, now), a);
            int fill = Colors.withAlpha(INK, a * 0.88f);
            rect(out, 0f, cy, w / 2f + px, h / 2f + px, uv, rim);
            rect(out, 0f, cy, w / 2f, h / 2f, uv, fill);
            // The tail: three rows narrowing to a point.
            for (int row = 0; row < 3; row++) {
                float hw = (2.5f - row) * px;
                rect(out, 0f, bottom + (2.5f - row) * px, hw, px / 2f, uv, rim);
            }
            // The words, centred line by line.
            int textColour = Colors.withAlpha(0xFFFFFFFF, a);
            float top = cy + h / 2f - pad * px - PixelFontCap.HALF * px;
            start = 0;
            int line = 0;
            for (int i = 0; i <= text.length(); i++) {
                if (i == text.length() || text.charAt(i) == '\n') {
                    float ly = top - line * lineH * px;
                    FxText.drawOn(out, text, start, i, U[0] * ly, U[1] * ly, U[2] * ly, R, U, px, textColour, 0);
                    line++;
                    start = i + 1;
                }
            }
        }

        private void rect(FxOut out, float cx, float cy, float hw, float hh, float[] uv, int argb) {
            float x = U[0] * cy, y = U[1] * cy, z = U[2] * cy;
            float rx = R[0] * hw, ry = R[1] * hw, rz = R[2] * hw, ux = U[0] * hh, uy = U[1] * hh, uz = U[2] * hh;
            out.vertex(x - rx - ux, y - ry - uy, z - rz - uz, uv[0], uv[3], argb);
            out.vertex(x - rx + ux, y - ry + uy, z - rz + uz, uv[0], uv[1], argb);
            out.vertex(x + rx + ux, y + ry + uy, z + rz + uz, uv[2], uv[1], argb);
            out.vertex(x + rx - ux, y + ry - uy, z + rz - uz, uv[2], uv[3], argb);
        }
    };

    /** Half the capitals' height (font pixels): a line's middle from its top. */
    private static final class PixelFontCap {
        static final float HALF = io.github.georgwav.cosmetics.sol.ui.kit.PixelFont.CAP / 2f;
    }

    /**
     * A burst at the mouth: crumbs (style 0), little cubes of the food's colours tumbling forward and bouncing to rest on
     * the ground, or drips (style 1) of the potion, glossy drops falling and glowing faintly, a sparkle as it goes down.
     */
    static final FxKind EAT = new FxKind("you_eat", 24) {
        private final int[] ramp = new int[Ramp.TONES];

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            float a = (float) Math.pow(Math.min(1f, (0.95f - t) / 0.35f), 1.2f) * fx.early(now);
            if (a <= 0f) {
                return;
            }
            Vfx.origin(out, fx);
            boolean glow = out.glowPass();
            boolean drip = fx.style == 1;
            float gk = FxBits.glowK(fx), s = fx.size;
            int n = Math.max(3, Math.round((drip ? 7 : 10) * fx.amount));
            float lx = (float) fx.tx, lz = (float) fx.tz;
            for (int i = 0; i < n; i++) {
                float h1 = Vfx.h(fx.seed, i), h2 = Vfx.s(fx.seed, i + 20), h3 = Vfx.h(fx.seed, i + 40);
                float forward = drip ? 0.2f + 0.3f * h1 : 0.9f + 1.3f * h1;
                float sideways = h2 * (drip ? 0.35f : 1f);
                float up = drip ? -0.1f : 0.8f + 1.4f * h3;
                float vx = lx * forward - lz * sideways, vz = lz * forward + lx * sideways;
                float x = Vfx.fly(vx, 2.5f, 0f, t), z = Vfx.fly(vz, 2.5f, 0f, t);
                float y = Math.max(fx.c + 0.03f, Vfx.fly(up, 1f, -11f, t));
                int c = fx.palette[i % 3];
                if (drip) {
                    float vy = Vfx.speed(up, 1f, -11f, t);
                    float r = (0.03f + 0.02f * h3) * s;
                    if (glow) {
                        Vfx.glow(out, x, y, z, r * 4f, Vfx.with(c, 0.35f * a * gk));
                    } else if (y > fx.c + 0.04f) {
                        // Stretched along its fall.
                        Vfx.billboard(out, FxSoft.ORB, x, y, z, r, r * (1f + Math.min(1.5f, -vy * 0.25f)), 0f, Vfx.with(Vfx.white(c, 0.15f), 0.9f * a));
                    } else {
                        Vfx.decal(out, FxSoft.DOT_SOFT, x, fx.c + 0.01f, z, r * 2.5f, 0f, Vfx.with(c, 0.6f * a));
                    }
                    if (glow && i == 0 && t < 0.3f) {
                        Vfx.glint(out, 0f, 0f, 0f, 0.25f * s, t * 3f, Vfx.with(Vfx.white(c, 0.5f), (1f - t / 0.3f) * a * gk));
                    }
                } else if (!glow) {
                    float side = (0.035f + 0.035f * h3) * s;
                    Ramp.of(c, ramp);
                    FxDraw.cube(out, x, y + side / 2f, z, side, ramp, a);
                }
            }
        }
    };
}
