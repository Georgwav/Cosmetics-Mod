package io.github.georgwav.cosmetics.mixin;

// World FX › Chunk rise (ChunkRise): each chunk section's place, as the game hands it to its draw, moved down while the
// section rises. Up to 1.21.4 the section offset uniform (camera-relative floats), on 1.21.6 to 1.21.10 the offset of
// its per-draw transform (likewise), from 1.21.11 its section info (its block origin, whole numbers). Not on 1.21.5.
//? if >=26.3 {
/*import io.github.georgwav.cosmetics.render.ChunkRise;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
abstract class ChunkRiseMixin {
    @ModifyArg(method = "extractSectionDrawGroups", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData$ChunkSectionInfo;<init>(IIIF)V"))
    private int cosmeticsmod$rise(int x, int y, int z, float visibility) {
        return y + ChunkRise.offsetBlocks(x, y, z);
    }
}
*///?} elif >=1.21.11 {
import io.github.georgwav.cosmetics.render.ChunkRise;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
abstract class ChunkRiseMixin {
    @ModifyArg(method = "prepareChunkRenders", require = 0, index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniforms$ChunkSectionInfo;<init>(Lorg/joml/Matrix4fc;IIIFII)V"))
    private int cosmeticsmod$rise(org.joml.Matrix4fc view, int x, int y, int z, float visibility, int atlasWidth, int atlasHeight) {
        return y + ChunkRise.offsetBlocks(x, y, z);
    }
}
//?} elif >=1.21.6 {
/*import io.github.georgwav.cosmetics.render.ChunkRise;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
abstract class ChunkRiseMixin {
    @ModifyArg(method = "prepareChunkRenders", require = 0, index = 1, at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;<init>(FFF)V", ordinal = 0))
    private float cosmeticsmod$rise(float x, float y, float z) {
        return y + ChunkRise.offsetRelative(x, y, z);
    }
}
*///?} elif >=1.21.5 {
/*final class ChunkRiseMixin {
    private ChunkRiseMixin() {
    }
}
*///?} else {
/*import io.github.georgwav.cosmetics.render.ChunkRise;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
abstract class ChunkRiseMixin {
    @ModifyArg(method = "renderSectionLayer", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/Uniform;set(FFF)V", ordinal = 0))
    private float cosmeticsmod$rise(float x, float y, float z) {
        return y + ChunkRise.offsetRelative(x, y, z);
    }
}
*///?}
