package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.melee.KnifePacks;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The knives' pack ({@link KnifePacks}) comes with the resource pack folder's packs, so the game finds it whenever it
 * looks for packs, F3+T included. Only the client's resource pack folder; data packs are left alone.
 */
@Mixin(FolderRepositorySource.class)
public abstract class KnifePackMixin {
    @Shadow
    @Final
    private Path folder;

    @Shadow
    @Final
    private PackType packType;

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void cosmeticsmod$knives(Consumer<Pack> out, CallbackInfo ci) {
        if (packType != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = Minecraft.getInstance().getResourcePackDirectory();
        if (root != null && folder.toAbsolutePath().normalize().equals(root.toAbsolutePath().normalize())) {
            KnifePacks.load(out);
        }
    }
}
