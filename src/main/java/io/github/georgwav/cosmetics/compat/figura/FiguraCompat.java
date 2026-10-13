package io.github.georgwav.cosmetics.compat.figura;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Asks Figura whether a player currently has an avatar. Figura avatars can hide or replace the whole
 * vanilla player model, so by default every slot defers to Figura for players that have one (each slot
 * can be switched to OVERRIDE in the config).
 *
 * <p>Only loaded when {@code FabricLoader.isModLoaded("figura")}. Figura has no stable public Java API,
 * so the lookup is reflective and resolved once; if Figura changes its internals we log once and stop
 * deferring rather than crash.
 */
public final class FiguraCompat {
    private static final MethodHandle GET_AVATAR;
    /** Getter for the avatar's model data, if Figura exposes it; an avatar without data is just a placeholder. */
    private static final MethodHandle AVATAR_DATA;

    static {
        MethodHandle getAvatar = null;
        MethodHandle avatarData = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> manager = Class.forName("org.figuramc.figura.avatar.AvatarManager");
            Class<?> avatar = Class.forName("org.figuramc.figura.avatar.Avatar");
            getAvatar = lookup.findStatic(manager, "getAvatarForPlayer", MethodType.methodType(avatar, UUID.class))
                    .asType(MethodType.methodType(Object.class, UUID.class));
            try {
                avatarData = lookup.unreflectGetter(avatar.getField("nbt"))
                        .asType(MethodType.methodType(Object.class, Object.class));
            } catch (ReflectiveOperationException e) {
                // Older/newer Figura without that field: any avatar object counts.
            }
            CosmeticsMod.LOGGER.info("Figura detected: slots set to DEFER yield to players' Figura avatars");
        } catch (Throwable e) {
            CosmeticsMod.LOGGER.warn("Figura is installed but its avatar lookup could not be resolved; "
                    + "cosmetics will not defer to Figura avatars", e);
        }
        GET_AVATAR = getAvatar;
        AVATAR_DATA = avatarData;
    }

    private FiguraCompat() {
    }

    public static boolean hasAvatar(UUID player) {
        if (GET_AVATAR == null) {
            return false;
        }
        try {
            Object avatar = GET_AVATAR.invokeExact(player);
            if (avatar == null) {
                return false;
            }
            return AVATAR_DATA == null || AVATAR_DATA.invokeExact(avatar) != null;
        } catch (Throwable e) {
            return false;
        }
    }
}
