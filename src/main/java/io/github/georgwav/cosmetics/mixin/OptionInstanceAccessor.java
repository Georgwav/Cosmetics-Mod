package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets an option's value without its slider's limits (Fullbright's brightness past 100%). */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("value")
    void cosmeticsmod$setValue(Object value);
}
