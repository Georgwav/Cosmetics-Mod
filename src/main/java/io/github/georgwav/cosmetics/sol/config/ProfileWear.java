package io.github.georgwav.cosmetics.sol.config;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.client.skins.MojangCapes;
import io.github.georgwav.cosmetics.client.skins.OwnSkin;
import io.github.georgwav.cosmetics.client.skins.SkinLibrary;
import io.github.georgwav.cosmetics.client.skins.SkinUploader;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * What a profile wears: its cosmetics (a saved outfit, or a list), its skin (from the launcher's skin library, sent to
 * Mojang) and its cape (one of your Mojang capes, or none). A profile puts them on when it becomes active; what you
 * wear while it's active is recorded in it. The skin and cape are sent only when they differ from what Sol last sent
 * for the signed-in account (kept with its UUID), so switching between profiles with the same skin costs nothing.
 */
public final class ProfileWear {
    /** A profile's cape when it hides your cape. */
    public static final String NO_CAPE = "none";

    private ProfileWear() {
    }

    /** The signed-in account's UUID, as text. */
    private static String account() {
        return String.valueOf(Minecraft.getInstance().getUser().getProfileId());
    }

    /** What Sol last sent for the signed-in account: the skin key, or the cape (null: nothing known for it). */
    private static String sent(boolean skin) {
        SolConfig cfg = SolConfig.get();
        if (!account().equals(cfg.wornAccount)) {
            return null;
        }
        return skin ? cfg.wornSkin : cfg.wornCape;
    }

    /** Notes what Sol sent for the signed-in account (another account's record is dropped). */
    private static void sent(String skin, String cape) {
        SolConfig cfg = SolConfig.get();
        if (!account().equals(cfg.wornAccount)) {
            cfg.wornAccount = account();
            cfg.wornSkin = null;
            cfg.wornCape = null;
        }
        if (skin != null) {
            cfg.wornSkin = skin;
        }
        if (cape != null) {
            cfg.wornCape = cape;
        }
        SolConfig.save();
    }

    /** The saved outfit you wear exactly (cosmetics and their settings), or null. */
    public static String outfitWorn() {
        CosmeticsConfig config = CosmeticsConfig.get();
        for (CosmeticsConfig.Outfit outfit : config.savedOutfits) {
            if (!outfit.cosmetics.isEmpty() && config.wearsOutfit(outfit)) {
                return outfit.name;
            }
        }
        return null;
    }

    /** The cosmetics you wear now, in order. */
    public static List<String> equipped() {
        return new ArrayList<>(CosmeticsConfig.get().localEquipped());
    }

    /**
     * Puts on a profile's cosmetics: its outfit when you still have one by that name, else its list (null: a profile
     * from before cosmetics were recorded, which leaves what you wear alone).
     */
    public static void wearCosmetics(String outfitName, List<String> equipped) {
        CosmeticsConfig config = CosmeticsConfig.get();
        CosmeticsConfig.Outfit outfit = outfitName == null ? null : config.outfit(outfitName);
        if (outfit != null) {
            config.wearOutfit(outfit);
        } else if (equipped != null) {
            List<String> wearing = config.localEquipped();
            wearing.clear();
            wearing.addAll(equipped);
        } else {
            return;
        }
        EquippedCosmetics.invalidate();
        CosmeticsConfig.save();
    }

    /** You wear this skin now (sent from the Wardrobe): the active profile records it. */
    public static void skinWorn(String key, boolean slim) {
        sent(key, null);
        SolProfiles.setSkin(key, slim);
    }

    /** You wear this cape now (or none, {@link #NO_CAPE}): the active profile records it. */
    public static void capeWorn(String id) {
        sent(null, id);
        SolProfiles.setCape(id);
    }

    /** Sends a profile's skin to your account, unless it's the one Sol last sent (or the profile has none). */
    public static void wearSkin(String key) {
        if (key == null || key.equals(sent(true))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Path db = MainFolder.database();
        if (db == null) {
            Notifications.post(Component.translatable("sol.profiles.skin_missing").getString());
            return;
        }
        String token = mc.getUser().getAccessToken();
        java.util.UUID player = mc.getUser().getProfileId();
        CompletableFuture.supplyAsync(() -> {
            try {
                for (SkinLibrary.Skin skin : SkinLibrary.load(db, player)) {
                    if (skin.key().equals(key)) {
                        return skin;
                    }
                }
                return null;
            } catch (java.io.IOException e) {
                CosmeticsMod.LOGGER.warn("[Sol] Couldn't read the launcher's skins", e);
                return null;
            }
        }, SolExecutors.IO).thenCompose(skin -> {
            if (skin == null) {
                mc.execute(() -> Notifications.post(Component.translatable("sol.profiles.skin_missing").getString()));
                return CompletableFuture.completedFuture(null);
            }
            return SkinUploader.upload(token, skin.png(), skin.slim()).thenAccept(status -> mc.execute(() -> {
                if (status == 200) {
                    int[] argb = SkinLibrary.pixels(skin.png(), 64);
                    if (argb != null) {
                        OwnSkin.set(argb, skin.slim());
                    }
                    sent(key, null);
                    Notifications.post(Component.translatable("sol.profiles.skin_worn").getString());
                } else {
                    Notifications.post(Component.translatable(status == 401 ? "cosmeticsmod.hub.skins.signed_out" : "sol.profiles.skin_failed").getString());
                }
            }));
        }).exceptionally(e -> {
            mc.execute(() -> Notifications.post(Component.translatable("sol.profiles.skin_failed").getString()));
            return null;
        });
    }

    /** Wears a profile's cape on your account (or hides it), unless that's what Sol last did (or the profile has none). */
    public static void wearCape(String id) {
        if (id == null || id.equals(sent(false))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String token = mc.getUser().getAccessToken();
        CompletableFuture<Integer> call = NO_CAPE.equals(id) ? MojangCapes.hide(token) : MojangCapes.wear(token, id);
        call.whenComplete((status, error) -> mc.execute(() -> {
            if (error == null && status != null && status == 200) {
                sent(null, id);
            } else {
                Notifications.post(Component.translatable(status != null && status == 401
                        ? "sol.wardrobe.capes.signed_out" : "sol.profiles.cape_failed").getString());
            }
        }));
    }
}
