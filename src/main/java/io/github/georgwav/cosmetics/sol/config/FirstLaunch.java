package io.github.georgwav.cosmetics.sol.config;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Modules;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;

/**
 * The first start of Sol Client: the four starting profiles (from the prototype), the player's current modules kept
 * as "Deep Field", and Deep Field switched on with Atmosphere.
 */
public final class FirstLaunch {
    private FirstLaunch() {
    }

    public static void run(Minecraft mc) {
        JsonObject current = Modules.toJson();
        make("Deep Field", "deep_field", null, current, true);
        make("Red PvP", "ember_solstice", "#ff4d4d", current, false);
        make("Survival", "sakura_dusk", null, current, true);
        make("Building", "frost_morning", null, current, false);
        SolConfig.get().activeProfile = "Deep Field";
        SolConfig.save();
        SolProfiles.switchTo("Deep Field");
        CosmeticsMod.LOGGER.info("[Sol] First launch: made the starting profiles");
    }

    private static void make(String name, String theme, String accent, JsonObject modules, boolean atmosphere) {
        if (Files.isRegularFile(SolProfiles.folder().resolve(name + ".json"))) {
            return;
        }
        JsonObject p = Migrations.migrateProfile(new JsonObject());
        JsonObject mods = modules.deepCopy();
        JsonObject atm = mods.has("atmosphere") && mods.get("atmosphere").isJsonObject() ? mods.getAsJsonObject("atmosphere")
                : new JsonObject();
        atm.addProperty("enabled", atmosphere);
        mods.add("atmosphere", atm);
        p.add("modules", mods);
        JsonObject t = p.getAsJsonObject("theme");
        t.addProperty("id", theme);
        if (accent != null) {
            t.addProperty("accent", accent);
        }
        SolProfiles.write(name, p);
    }
}
