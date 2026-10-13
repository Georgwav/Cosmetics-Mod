package io.github.georgwav.cosmetics.config;

import io.github.georgwav.cosmetics.model.CosmeticSlot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
