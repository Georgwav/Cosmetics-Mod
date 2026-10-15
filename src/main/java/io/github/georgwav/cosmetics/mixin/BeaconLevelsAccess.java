package io.github.georgwav.cosmetics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A beacon's pyramid levels (the game keeps them on the client too, for the beam): World Overlays' beacon range. */
@Mixin(net.minecraft.world.level.block.entity.BeaconBlockEntity.class)
public interface BeaconLevelsAccess {
    @Accessor("levels")
    int cosmeticsmod$levels();
}
