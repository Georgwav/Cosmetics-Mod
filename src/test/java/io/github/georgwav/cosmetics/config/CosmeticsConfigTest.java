package io.github.georgwav.cosmetics.config;

import io.github.georgwav.cosmetics.model.CosmeticSlot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticsConfigTest {
    @Test
    void sanitizeRepairsAHandEditedFile() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.slotPriority = null;
        config.slotVisible = new HashMap<>();
        config.slotVisible.put(null, false);
        config.slotVisible.put(CosmeticSlot.CAPE, false);
        config.equipped = null;
        config.capeSwing = Float.NaN;
        config.maxRenderDistance = -5;
        config.shadowDistance = 100_000;

        config.sanitize();

        for (CosmeticSlot slot : CosmeticSlot.values()) {
            assertEquals(SlotPriority.DEFER, config.priority(slot));
        }
        assertFalse(config.slotVisible(CosmeticSlot.CAPE));
        assertTrue(config.slotVisible(CosmeticSlot.HEAD));
        assertEquals(1.0f, config.capeSwing);
        assertEquals(0, config.maxRenderDistance);
        assertEquals(512, config.shadowDistance);
        assertTrue(config.localEquipped().isEmpty());
    }

    @Test
    void resetKeepsTheWardrobe() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.localEquipped().add("cosmeticsmod:top_hat");
        config.enabled = false;
        config.renderInShadowPass = false;
        config.slotPriority.put(CosmeticSlot.CAPE, SlotPriority.OVERRIDE);

        config.resetSettings();

        assertTrue(config.enabled);
        assertTrue(config.renderInShadowPass);
        assertEquals(SlotPriority.DEFER, config.priority(CosmeticSlot.CAPE));
        assertEquals(List.of("cosmeticsmod:top_hat"), config.localEquipped());
    }

    @Test
    void cosmeticOptionsAreClampedAndDefaultsDropped() {
        CosmeticsConfig config = new CosmeticsConfig();
        CosmeticsConfig.CosmeticOptions hat = config.editOptions("cosmeticsmod:top_hat");
        hat.scale = 9f;
        hat.offsetY = Float.NaN;
        hat.color = 0xAB123456;
        hat.animationSpeed = -1f;
        hat.saturation = 7f;
        config.editOptions("cosmeticsmod:halo"); // untouched: nothing to keep
        config.cosmeticOptions.put(null, new CosmeticsConfig.CosmeticOptions());

        config.sanitize();

        assertEquals(1, config.cosmeticOptions.size());
        assertEquals(1.5f, config.options("cosmeticsmod:top_hat").scale);
        assertEquals(0f, config.options("cosmeticsmod:top_hat").offsetY);
        assertEquals(0x123456, config.options("cosmeticsmod:top_hat").color);
        assertEquals(0.1f, config.options("cosmeticsmod:top_hat").animationSpeed);
        assertEquals(1f, config.options("cosmeticsmod:top_hat").saturation);
        assertTrue(config.options("cosmeticsmod:halo").isDefault());
    }

    @Test
    void resetSettingsKeepsHowEachCosmeticLooks() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.editOptions("cosmeticsmod:halo").rainbow = true;

        config.resetSettings();
        assertTrue(config.options("cosmeticsmod:halo").rainbow);

        config.resetOptions("cosmeticsmod:halo");
        assertFalse(config.options("cosmeticsmod:halo").rainbow);
    }

    @Test
    void colourFollowsTheCosmeticUntilPicked() {
        CosmeticsConfig config = new CosmeticsConfig();
        assertNull(config.options("cosmeticsmod:top_hat").color);
        config.editOptions("cosmeticsmod:top_hat").color = 0xFFFFFF;
        config.editOptions("cosmeticsmod:halo").saturation = 0.5f;
        config.sanitize();
        assertEquals(0xFFFFFF, config.options("cosmeticsmod:top_hat").color, "an explicit white is kept");
        assertEquals(0.5f, config.options("cosmeticsmod:halo").saturation);
        assertEquals(2, config.cosmeticOptions.size());
    }
}
