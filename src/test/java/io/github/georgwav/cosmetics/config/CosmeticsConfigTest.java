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
    @SuppressWarnings("deprecation")
    void emoteWheelsAreSlotsThatStartEmpty() {
        CosmeticsConfig fresh = new CosmeticsConfig();
        fresh.sanitize();
        assertEquals(CosmeticsConfig.WHEEL_SLOTS, fresh.emoteWheelSlots.size());
        assertTrue(fresh.emoteWheelSlots.stream().allMatch(String::isEmpty), "a new wheel has nothing on it");

        // A wheel from before slots keeps its emotes, in order, then empty slots to fill the page.
        CosmeticsConfig old = new CosmeticsConfig();
        old.configVersion = 2;
        old.emoteWheel = new java.util.ArrayList<>(List.of("a:wave", "a:clap", "a:wave"));
        old.sanitize();
        assertEquals(List.of("a:wave", "a:clap", "", "", "", "", "", ""), old.emoteWheelSlots);
        assertTrue(old.emoteWheel.isEmpty());

        // Hand-edited: holes and a part page are made whole; too many pages are cut.
        CosmeticsConfig edited = new CosmeticsConfig();
        edited.configVersion = CosmeticsConfig.CONFIG_VERSION;
        edited.emoteWheelSlots = new java.util.ArrayList<>(java.util.Arrays.asList("a:x", null, "a:y",
                "", "", "", "", "", "a:z"));
        edited.sanitize();
        assertEquals(2 * CosmeticsConfig.WHEEL_SLOTS, edited.emoteWheelSlots.size());
        assertEquals("", edited.emoteWheelSlots.get(1));
        assertEquals("a:z", edited.emoteWheelSlots.get(8));
        edited.emoteWheelSlots = new java.util.ArrayList<>(java.util.Collections.nCopies(
                (CosmeticsConfig.MAX_WHEEL_PAGES + 3) * CosmeticsConfig.WHEEL_SLOTS, ""));
        edited.sanitize();
        assertEquals(CosmeticsConfig.MAX_WHEEL_PAGES * CosmeticsConfig.WHEEL_SLOTS, edited.emoteWheelSlots.size());
    }

    @Test
    void oldFilesGetTheHigherTriangleLimitUnlessTheyChoseTheirOwn() {
        CosmeticsConfig old = new CosmeticsConfig();
        old.configVersion = 3;
        old.maxMeshTriangles = 20_000;
        old.sanitize();
        assertEquals(new CosmeticsConfig().maxMeshTriangles, old.maxMeshTriangles);
        CosmeticsConfig chosen = new CosmeticsConfig();
        chosen.configVersion = 3;
        chosen.maxMeshTriangles = 30_000;
        chosen.sanitize();
        assertEquals(30_000, chosen.maxMeshTriangles);
        // Once migrated, 20,000 is a choice like any other.
        chosen.maxMeshTriangles = 20_000;
        chosen.sanitize();
        assertEquals(20_000, chosen.maxMeshTriangles);
    }

    @Test
    void outfitsSaveAndPutOnExactlyWhatWasWorn() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.localEquipped().addAll(List.of("a:hat", "a:cape"));
        CosmeticsConfig.Outfit first = config.saveOutfit("Outfit");
        assertEquals("Outfit 1", first.name);
        assertTrue(config.wearsOutfit(first));

        config.localEquipped().clear();
        config.localEquipped().add("a:wings");
        assertEquals("Outfit 2", config.saveOutfit("Outfit").name);
        assertFalse(config.wearsOutfit(first));

        config.wearOutfit(first);
        assertEquals(List.of("a:hat", "a:cape"), config.localEquipped());
        assertTrue(config.wearsOutfit(first));
        // Changing what you wear doesn't change the saved outfit.
        config.localEquipped().add("a:wings");
        assertEquals(List.of("a:hat", "a:cape"), config.outfit("Outfit 1").cosmetics);

        config.savedOutfits.remove(first);
        assertEquals("Outfit 1", config.saveOutfit("Outfit").name);
    }

    @Test
    void outfitsKeepTheirCosmeticsSettings() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.localEquipped().addAll(List.of("a:hat", "a:wings"));
        config.editOptions("a:hat").color = 0x8000FF;
        CosmeticsConfig.Outfit purple = config.saveOutfit("Outfit");
        assertTrue(config.renameOutfit(purple, "  Purple "));
        assertEquals("Purple", purple.name);

        // The same cosmetics, green: another outfit.
        config.editOptions("a:hat").color = 0x00FF00;
        config.editOptions("a:wings").scale = 1.5f;
        CosmeticsConfig.Outfit green = config.saveOutfit("Outfit");
        assertTrue(config.renameOutfit(green, "Green"));
        assertFalse(config.renameOutfit(green, "Purple"), "another outfit's name");
        assertFalse(config.renameOutfit(green, " "), "no name");
        assertEquals("Green", green.name);
        assertFalse(config.wearsOutfit(purple), "same cosmetics, other settings");
        assertTrue(config.wearsOutfit(green));

        config.wearOutfit(purple);
        assertEquals(0x8000FF, config.options("a:hat").color);
        assertEquals(1.0f, config.options("a:wings").scale, "it had its creator's settings in the purple outfit");
        assertTrue(config.wearsOutfit(purple));
        // The outfit keeps its own copy: changing the settings worn doesn't change it.
        config.editOptions("a:hat").color = 0xFF0000;
        assertEquals(0x8000FF, purple.optionsFor("a:hat").color);
        assertFalse(config.wearsOutfit(purple));

        // Shown on you without wearing it: its settings win while shown, yours are untouched.
        config.showOutfitSettings(green);
        assertEquals(0x00FF00, config.options("a:hat").color);
        assertEquals(1.5f, config.options("a:wings").scale);
        config.showOutfitSettings(null);
        assertEquals(0xFF0000, config.options("a:hat").color);

        config.updateOutfit(purple);
        assertEquals(0xFF0000, purple.optionsFor("a:hat").color);
    }

    @Test
    void outfitsFromOlderFilesKeepTheirCosmetics() {
        CosmeticsConfig config = new CosmeticsConfig();
        config.outfits.put("Old", new java.util.ArrayList<>(List.of("a:hat", "a:cape")));
        config.sanitize();
        assertTrue(config.outfits.isEmpty());
        assertEquals(List.of("a:hat", "a:cape"), config.outfit("Old").cosmetics);
        assertTrue(config.outfit("Old").options.isEmpty());
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
        config.savedOutfits = null;
        config.outfits = null;
        config.sanitize();
        assertTrue(config.savedOutfits.isEmpty());
        CosmeticsConfig.Outfit blank = new CosmeticsConfig.Outfit();
        blank.name = " ";
        CosmeticsConfig.Outfit mine = new CosmeticsConfig.Outfit();
        mine.name = "Mine";
        mine.cosmetics = new java.util.ArrayList<>(java.util.Arrays.asList("a:hat", null, "a:hat"));
        mine.options = null;
        CosmeticsConfig.Outfit twin = new CosmeticsConfig.Outfit();
        twin.name = "Mine ";
        config.savedOutfits.addAll(java.util.Arrays.asList(blank, null, mine, twin));
        config.sanitize();
        assertEquals(List.of(mine), config.savedOutfits, "blank, missing and repeated names go");
        assertEquals(List.of("a:hat"), mine.cosmetics);
        assertTrue(mine.options.isEmpty());
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
