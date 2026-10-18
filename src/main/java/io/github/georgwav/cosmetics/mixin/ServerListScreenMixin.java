package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.ServerListUpgrade;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Servers' folders on the server list screen (ServerListUpgrade): a chip per folder at the top left ("All" first),
 * the one shown greyed; clicking one shows that folder's servers.
 */
@Mixin(JoinMultiplayerScreen.class)
abstract class ServerListScreenMixin extends Screen {
    @Shadow
    protected ServerSelectionList serverSelectionList;
    @Unique
    private final List<Button> cosmeticsmod$chips = new ArrayList<>();

    protected ServerListScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    public abstract net.minecraft.client.multiplayer.ServerList getServers();

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$chips(CallbackInfo ci) {
        cosmeticsmod$chips.clear();
        if (!ServerListUpgrade.on() || getServers() == null) {
            return;
        }
        List<String> folders = ServerListUpgrade.folders(getServers());
        if (folders.isEmpty()) {
            return;
        }
        ServerListUpgrade.show(ServerListUpgrade.filter(), getServers());
        // Left of the centred title, so they never cover it.
        int room = width / 2 - font.width(title) / 2 - 10, x = 6;
        List<String> names = new ArrayList<>();
        names.add(null);
        names.addAll(folders);
        for (String folder : names) {
            Component label = folder == null ? Component.translatable("sol.servers.all") : Component.literal(folder);
            int w = font.width(label) + 10;
            if (x + w > room) {
                break;
            }
            Button chip = addRenderableWidget(Button.builder(label, b -> {
                ServerListUpgrade.show(folder, getServers());
                serverSelectionList.updateOnlineServers(getServers());
                cosmeticsmod$mark();
            }).bounds(x, 6, w, 14).build());
            cosmeticsmod$chips.add(chip);
            x += w + 3;
        }
        cosmeticsmod$mark();
    }

    @Unique
    private void cosmeticsmod$mark() {
        String shown = ServerListUpgrade.filter();
        for (Button chip : cosmeticsmod$chips) {
            String name = chip.getMessage().getString();
            boolean all = chip == cosmeticsmod$chips.get(0);
            chip.active = all ? shown != null : !name.equalsIgnoreCase(shown);
        }
    }
}
