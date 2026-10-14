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
            assertEquals(slot == CosmeticSlot.CAPE ? SlotPriority.OVERRIDE : SlotPriority.DEFER, config.priority(slot));
        }
        assertFalse(config.slotVisible(CosmeticSlot.CAPE));
        assertTrue(config.slotVisible(CosmeticSlot.HEAD));
        assertEquals(1.0f, config.capeSwing);
        assertEquals(0, config.maxRenderDistance);
        assertEquals(512, config.shadowDistance);
        assertTrue(config.localEquipped().isEmpty());
    }

    @Test
    void oldFilesSwitchCapesToOverrideOnceAndKeepLaterChoices() {
        CosmeticsConfig old = new CosmeticsConfig();
        old.configVersion = 0;
        old.slotPriority.put(CosmeticSlot.CAPE, SlotPriority.DEFER);
        old.sanitize();
        assertEquals(SlotPriority.OVERRIDE, old.priority(CosmeticSlot.CAPE));
        assertEquals(CosmeticsConfig.CONFIG_VERSION, old.configVersion);

        // Someone who sets it back to DEFER afterwards keeps that.
        old.slotPriority.put(CosmeticSlot.CAPE, SlotPriority.DEFER);
        old.sanitize();
        assertEquals(SlotPriority.DEFER, old.priority(CosmeticSlot.CAPE));
    }

    @Test
    void outfitsSaveAndPutOnExactlyWhatWasWorn() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.localEquipped().addAll(List.of("a:hat", "a:cape"));
        String first = config.saveOutfit("Outfit");
        assertEquals("Outfit 1", first);
        assertTrue(config.wearsOutfit(first));

        config.localEquipped().clear();
        config.localEquipped().add("a:wings");
        assertEquals("Outfit 2", config.saveOutfit("Outfit"));
        assertFalse(config.wearsOutfit(first));

        config.wearOutfit(first);
        assertEquals(List.of("a:hat", "a:cape"), config.localEquipped());
        assertTrue(config.wearsOutfit(first));
        // Changing what you wear doesn't change the saved outfit.
        config.localEquipped().add("a:wings");
        assertEquals(List.of("a:hat", "a:cape"), config.outfits.get(first));

        config.outfits.remove(first);
        assertEquals("Outfit 1", config.saveOutfit("Outfit"));
    }

    @Test
    void wearingOneOfAKindTakesOffTheOtherOfThatKind() {
        java.util.Map<String, String> kinds = java.util.Map.of("a:wings1", "wings", "a:wings2", "wings",
                "a:backpack", "back", "a:hat", "head");
        List<String> wearing = new java.util.ArrayList<>(List.of("a:wings1", "a:backpack", "a:gone"));
        CosmeticsConfig.wear(wearing, "a:wings2", true, kinds::get, false);
        assertEquals(List.of("a:backpack", "a:gone", "a:wings2"), wearing, "new wings replace the old, not a backpack");
        CosmeticsConfig.wear(wearing, "a:wings1", true, kinds::get, true);
        assertEquals(List.of("a:backpack", "a:gone", "a:wings2", "a:wings1"), wearing, "several allowed");
        CosmeticsConfig.wear(wearing, "a:wings2", false, kinds::get, false);
        assertEquals(List.of("a:backpack", "a:gone", "a:wings1"), wearing);
        CosmeticsConfig.wear(wearing, "a:hat", true, kinds::get, false);
        CosmeticsConfig.wear(wearing, "a:hat", true, kinds::get, false);
        assertEquals(List.of("a:backpack", "a:gone", "a:wings1", "a:hat"), wearing, "putting on twice keeps one");
    }

    @Test
    void sanitizeRepairsOutfits() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.outfits = null;
        config.sanitize();
        assertTrue(config.outfits.isEmpty());
        config.outfits.put(" ", List.of("a:hat"));
        config.outfits.put("Mine", new java.util.ArrayList<>(java.util.Arrays.asList("a:hat", null)));
        config.sanitize();
        assertEquals(java.util.Set.of("Mine"), config.outfits.keySet());
        assertEquals(List.of("a:hat"), config.outfits.get("Mine"));
    }

    @Test
    void resetKeepsTheWardrobe() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.localEquipped().add("cosmeticsmod:top_hat");
        config.enabled = false;
        config.renderInShadowPass = false;
        config.slotPriority.put(CosmeticSlot.CAPE, SlotPriority.DEFER);
        config.slotPriority.put(CosmeticSlot.HEAD, SlotPriority.OVERRIDE);

        config.resetSettings();

        assertTrue(config.enabled);
        assertTrue(config.renderInShadowPass);
        assertEquals(SlotPriority.OVERRIDE, config.priority(CosmeticSlot.CAPE));
        assertEquals(SlotPriority.DEFER, config.priority(CosmeticSlot.HEAD));
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
        assertEquals(CosmeticsConfig.MAX_SCALE, config.options("cosmeticsmod:top_hat").scale);
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
