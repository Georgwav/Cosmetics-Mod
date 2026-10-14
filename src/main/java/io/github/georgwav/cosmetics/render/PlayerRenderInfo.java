package io.github.georgwav.cosmetics.render;

import java.util.UUID;

/**
 * What the render layer knows about the player being drawn, in a form that doesn't depend on the
 * Minecraft version. Filled in by the version-specific layer; one instance is reused on the render
 * thread, so nothing is allocated per frame.
 */
public final class PlayerRenderInfo {
    public UUID uuid;
    /** The player on this client. */
    public boolean local;
    public boolean invisible;
    /** A vanilla cape (Mojang cape, or another mod's cape delivered through the vanilla skin) will be drawn. */
    public boolean vanillaCapeVisible;
    /** Wearing an elytra: capes are hidden like vanilla does. */
    public boolean elytra;
    /** Something is worn in the head slot (helmet, pumpkin, skull...). */
    public boolean helmet;
    /** Squared distance to the local player, in blocks. */
    public double distanceSq;
    /** Vanilla cape sway, in degrees; the same inputs the vanilla cape uses. */
    public float capeFlap;
    public float capeLean;
    public float capeLean2;
    /** Horizontal speed in blocks per tick, for wings. */
    public float moveSpeed;
    public boolean sneaking;
    /** Flying in creative or gliding with an elytra. */
    public boolean flying;
    /** Something other than an elytra is worn in the chest slot. */
    public boolean chestArmor;
    /** Gliding with an elytra. */
    public boolean gliding;
    public boolean swimming;
    /** Holding a shield up. */
    public boolean blocking;
    public boolean onGround;
    public boolean sprinting;
    /** Sitting on something (a horse, a boat, ...). */
    public boolean riding;
    /** Distance walked, in blocks (the walk animation's position). */
    public float walked;

    public void reset() {
        uuid = null;
        local = false;
        invisible = false;
        vanillaCapeVisible = false;
        elytra = false;
        helmet = false;
        distanceSq = 0;
        capeFlap = 0;
        capeLean = 0;
        capeLean2 = 0;
        moveSpeed = 0;
        sneaking = false;
        flying = false;
        chestArmor = false;
        gliding = false;
        swimming = false;
        blocking = false;
        onGround = false;
        sprinting = false;
        riding = false;
        walked = 0;
    }
}
