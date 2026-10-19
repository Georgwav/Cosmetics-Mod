package io.github.georgwav.cosmetics.mixin;

//? if >=1.21.11 {
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game's own critical hit rule (1.21.11 and later), for the knives' heavy attack. */
@Mixin(Player.class)
public interface PlayerCritAccess {
    @Invoker("canCriticalAttack")
    boolean cosmeticsmod$canCriticalAttack(Entity target);
}
//?} else {
/*final class PlayerCritAccess {
    private PlayerCritAccess() {
    }
}
*///?}
