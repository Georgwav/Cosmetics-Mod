package io.github.georgwav.cosmetics.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The food data's exhaustion, for Sol UI's exhaustion line. The server doesn't send it to the client; AppleSkin does
 * (into this same field), so without AppleSkin it stays 0 and the line isn't drawn.
 */
@Mixin(FoodData.class)
public interface FoodDataMixin {
    @Accessor("exhaustionLevel")
    float cosmeticsmod$exhaustion();
}
