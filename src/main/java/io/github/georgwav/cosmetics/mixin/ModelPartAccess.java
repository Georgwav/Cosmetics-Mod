package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** A model part's own cubes: bent arms and legs draw them themselves (see BentLimbs). */
@Mixin(ModelPart.class)
public interface ModelPartAccess {
    @Accessor("cubes")
    List<ModelPart.Cube> cosmeticsmod$cubes();
}
