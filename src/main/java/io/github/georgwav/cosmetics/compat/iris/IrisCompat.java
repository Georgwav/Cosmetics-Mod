package io.github.georgwav.cosmetics.compat.iris;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Talks to Iris' public API ({@code net.irisshaders.iris.api.v0.IrisApi}).
 *
 * <p>Only {@link io.github.georgwav.cosmetics.compat.Compat} touches this class, and only after
 * {@code FabricLoader.isModLoaded("iris")} returned true, so it is never loaded without Iris.
 * The API is resolved through method handles once at startup rather than compiled against, so one
 * source tree works with every Iris release for every Minecraft version, and an Iris API change
 * degrades to "no shadow-pass detection" with a log line instead of a crash.
 */
public final class IrisCompat {
    private static final MethodHandle IS_SHADOW_PASS;
    private static final MethodHandle IS_SHADER_PACK_IN_USE;
    private static final MethodHandle SET_SHADERS_ENABLED;

    static {
        MethodHandle shadowPass = null;
        MethodHandle packInUse = null;
        MethodHandle setEnabled = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = lookup.findStatic(apiClass, "getInstance", MethodType.methodType(apiClass)).invoke();
            shadowPass = lookup.findVirtual(apiClass, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(api);
            packInUse = lookup.findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(api);
            try {
                Class<?> configClass = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
                Object config = lookup.findVirtual(apiClass, "getConfig", MethodType.methodType(configClass)).invoke(api);
                setEnabled = lookup.findVirtual(configClass, "setShadersEnabledAndApply",
                        MethodType.methodType(void.class, boolean.class)).bindTo(config);
            } catch (ReflectiveOperationException | LinkageError e) {
                CosmeticsMod.LOGGER.debug("Iris config API not available; benchmark cannot toggle shaders", e);
            }
            CosmeticsMod.LOGGER.info("Iris detected: cosmetics follow the Iris shadow pass setting");
        } catch (Throwable e) {
            CosmeticsMod.LOGGER.warn("Iris is installed but its API could not be resolved; "
                    + "cosmetics will always render in the shadow pass", e);
        }
        IS_SHADOW_PASS = shadowPass;
        IS_SHADER_PACK_IN_USE = packInUse;
        SET_SHADERS_ENABLED = setEnabled;
    }

    private IrisCompat() {
    }

    public static boolean isRenderingShadowPass() {
        if (IS_SHADOW_PASS == null) {
            return false;
        }
        try {
            return (boolean) IS_SHADOW_PASS.invokeExact();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isShaderPackInUse() {
        if (IS_SHADER_PACK_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IS_SHADER_PACK_IN_USE.invokeExact();
        } catch (Throwable e) {
            return false;
        }
    }

    /** @return false if the Iris config API is not available */
    public static boolean setShadersEnabled(boolean enabled) {
        if (SET_SHADERS_ENABLED == null) {
            return false;
        }
        try {
            SET_SHADERS_ENABLED.invokeExact(enabled);
            return true;
        } catch (Throwable e) {
            CosmeticsMod.LOGGER.warn("Could not toggle Iris shaders", e);
            return false;
        }
    }
}
