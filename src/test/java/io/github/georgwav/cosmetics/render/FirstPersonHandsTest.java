package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPersonHandsTest {
    /** A w x h picture, clear, with an opaque block from (x0, y0) to (x1, y1) inclusive. */
    private static int[] block(int w, int h, int x0, int y0, int x1, int y1) {
        int[] px = new int[w * h];
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                px[y * w + x] = 0xFF808080;
            }
        }
        return px;
    }

    @Test
    void flamesStartOnTheOutlineOnly() {
        // A 4 x 4 block in a 6 x 6 picture: 12 outline pixels, its middle 2 x 2 left out.
        float[] at = FirstPersonHands.outline(block(6, 6, 1, 1, 4, 4), 6, 6);
        assertEquals(24, at.length);
        for (int i = 0; i < at.length / 2; i++) {
            int x = (int) (at[i * 2] * 6), y = (int) ((1f - at[i * 2 + 1]) * 6);
            assertTrue(x >= 1 && x <= 4 && y >= 1 && y <= 4, "inside the block");
            assertTrue(x == 1 || x == 4 || y == 1 || y == 4, "on its outline, not in its middle: " + x + "," + y);
        }
    }

    @Test
    void atMostTwelveFlamesSpreadRoundTheOutline() {
        float[] at = FirstPersonHands.outline(block(16, 16, 0, 0, 15, 15), 16, 16);
        assertEquals(24, at.length);
        // Spread: not all on the top row.
        float lowest = 1f;
        for (int i = 0; i < 12; i++) {
            lowest = Math.min(lowest, at[i * 2 + 1]);
        }
        assertTrue(lowest < 0.5f);
        assertEquals(2, FirstPersonHands.outline(new int[16], 4, 4).length, "a clear picture still has one");
    }

    @Test
    void everySwingStyleStartsAndEndsWhereTheGameHoldsTheItem() {
        for (int style = 2; style < FirstPersonHands.SWING_STYLES.size(); style++) {
            for (float side : new float[]{1f, -1f}) {
                for (float p : new float[]{0f, 1f}) {
                    PoseStack pose = new PoseStack();
                    FirstPersonHands.style(pose, style, side, p);
                    assertTrue(pose.last().pose().equals(new Matrix4f(), 1e-4f),
                            FirstPersonHands.SWING_STYLES.get(style) + " at " + p + ": " + pose.last().pose());
                }
                PoseStack mid = new PoseStack();
                FirstPersonHands.style(mid, style, side, 0.3f);
                assertFalse(mid.last().pose().equals(new Matrix4f(), 1e-3f), FirstPersonHands.SWING_STYLES.get(style) + " mid-swing");
            }
        }
    }
}
