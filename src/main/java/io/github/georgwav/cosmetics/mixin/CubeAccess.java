package io.github.georgwav.cosmetics.mixin;

// A cube's faces, private before 1.21.2 (public after): bent arms and legs draw them themselves (see BentLimbs).
//? if <1.21.2 {
/*import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.Cube.class)
public interface CubeAccess {
    @Accessor("polygons")
    ModelPart.Polygon[] cosmeticsmod$polygons();
}
*///?}
