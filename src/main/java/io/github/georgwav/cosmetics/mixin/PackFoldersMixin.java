package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Resource pack folders: a folder inside resourcepacks that isn't a pack itself (no pack.mcmeta) is a group, and the
 * packs in it (two levels deep at most) show up in the pack screen with the rest. Only the resource pack folder;
 * worlds' data packs are left as the game finds them.
 */
@Mixin(FolderRepositorySource.class)
public abstract class PackFoldersMixin {
    @Unique
    private static final ThreadLocal<Integer> cosmeticsmod$depth = ThreadLocal.withInitial(() -> 0);

    @Inject(method = "discoverPacks", at = @At("TAIL"), require = 0)
    private static void cosmeticsmod$folders(Path folder, DirectoryValidator validator, BiConsumer<Path, Pack.ResourcesSupplier> out,
                                             CallbackInfo ci) throws IOException {
        int depth = cosmeticsmod$depth.get();
        if (depth >= 2 || !io.github.georgwav.cosmetics.sol.config.SolConfig.get().packFolders) {
            return;
        }
        Path root = Minecraft.getInstance().getResourcePackDirectory();
        if (depth == 0 && (root == null || !folder.toAbsolutePath().normalize().equals(root.toAbsolutePath().normalize()))) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path sub : entries) {
                if (Files.isDirectory(sub) && !Files.exists(sub.resolve("pack.mcmeta")) && !sub.getFileName().toString().startsWith(".")) {
                    cosmeticsmod$depth.set(depth + 1);
                    try {
                        FolderRepositorySource.discoverPacks(sub, validator, out);
                    } finally {
                        cosmeticsmod$depth.set(depth);
                    }
                }
            }
        }
    }
}
