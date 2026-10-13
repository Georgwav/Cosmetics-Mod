package io.github.georgwav.cosmetics.aura;

// One particle of a particle cosmetic, in the game's particle engine: it moves, grows or shrinks, fades,
// spins and steps through its textures as its spawn says, and glows if asked. 1.21.9 rewrote particles
// (SingleQuadParticle with the sprite given up front) and 26.1 renamed the light method.
//? if >=26.1 {
/*import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

final class AuraParticle extends SingleQuadParticle {
    private final AuraSpawner.Spawn spawn;
    private final TextureAtlasSprite[] sprites;

    AuraParticle(ClientLevel level, double x, double y, double z, AuraSpawner.Spawn spawn, TextureAtlasSprite[] sprites) {
        super(level, x, y, z, sprites[0]);
        this.spawn = spawn;
        this.sprites = sprites;
        this.xd = spawn.vx();
        this.yd = spawn.vy();
        this.zd = spawn.vz();
        this.lifetime = spawn.lifetime();
        // Vanilla's tick takes 0.04 x gravity off the vertical speed each tick.
        this.gravity = spawn.gravity() / 0.04f;
        this.friction = spawn.friction();
        this.hasPhysics = false;
        this.quadSize = spawn.startSize();
        this.rCol = ((spawn.color() >> 16) & 0xFF) / 255f;
        this.gCol = ((spawn.color() >> 8) & 0xFF) / 255f;
        this.bCol = (spawn.color() & 0xFF) / 255f;
        this.alpha = spawn.startAlpha();
        AuraParticles.live++;
    }

    @Override
    public void tick() {
        super.tick();
        float t = lifetime <= 0 ? 1f : Math.min(1f, (float) age / lifetime);
        quadSize = spawn.startSize() + (spawn.endSize() - spawn.startSize()) * t;
        alpha = spawn.startAlpha() + (spawn.endAlpha() - spawn.startAlpha()) * t;
        oRoll = roll;
        roll += spawn.spin();
        if (sprites.length > 1) {
            setSprite(sprites[Math.min(sprites.length - 1, (int) (t * sprites.length))]);
        }
    }

    @Override
    public void remove() {
        if (!removed) {
            AuraParticles.live--;
        }
        super.remove();
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return spawn.glow() ? 0xF000F0 : super.getLightCoords(partialTick);
    }
}
*///?} elif >=1.21.9 {
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

final class AuraParticle extends SingleQuadParticle {
    private final AuraSpawner.Spawn spawn;
    private final TextureAtlasSprite[] sprites;

    AuraParticle(ClientLevel level, double x, double y, double z, AuraSpawner.Spawn spawn, TextureAtlasSprite[] sprites) {
        super(level, x, y, z, sprites[0]);
        this.spawn = spawn;
        this.sprites = sprites;
        this.xd = spawn.vx();
        this.yd = spawn.vy();
        this.zd = spawn.vz();
        this.lifetime = spawn.lifetime();
        // Vanilla's tick takes 0.04 x gravity off the vertical speed each tick.
        this.gravity = spawn.gravity() / 0.04f;
        this.friction = spawn.friction();
        this.hasPhysics = false;
        this.quadSize = spawn.startSize();
        this.rCol = ((spawn.color() >> 16) & 0xFF) / 255f;
        this.gCol = ((spawn.color() >> 8) & 0xFF) / 255f;
        this.bCol = (spawn.color() & 0xFF) / 255f;
        this.alpha = spawn.startAlpha();
        AuraParticles.live++;
    }

    @Override
    public void tick() {
        super.tick();
        float t = lifetime <= 0 ? 1f : Math.min(1f, (float) age / lifetime);
        quadSize = spawn.startSize() + (spawn.endSize() - spawn.startSize()) * t;
        alpha = spawn.startAlpha() + (spawn.endAlpha() - spawn.startAlpha()) * t;
        oRoll = roll;
        roll += spawn.spin();
        if (sprites.length > 1) {
            setSprite(sprites[Math.min(sprites.length - 1, (int) (t * sprites.length))]);
        }
    }

    @Override
    public void remove() {
        if (!removed) {
            AuraParticles.live--;
        }
        super.remove();
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return spawn.glow() ? 0xF000F0 : super.getLightColor(partialTick);
    }
}
//?} else {
/*import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

final class AuraParticle extends TextureSheetParticle {
    private final AuraSpawner.Spawn spawn;
    private final TextureAtlasSprite[] sprites;

    AuraParticle(ClientLevel level, double x, double y, double z, AuraSpawner.Spawn spawn, TextureAtlasSprite[] sprites) {
        super(level, x, y, z);
        this.spawn = spawn;
        this.sprites = sprites;
        this.xd = spawn.vx();
        this.yd = spawn.vy();
        this.zd = spawn.vz();
        this.lifetime = spawn.lifetime();
        // Vanilla's tick takes 0.04 x gravity off the vertical speed each tick.
        this.gravity = spawn.gravity() / 0.04f;
        this.friction = spawn.friction();
        this.hasPhysics = false;
        this.quadSize = spawn.startSize();
        this.rCol = ((spawn.color() >> 16) & 0xFF) / 255f;
        this.gCol = ((spawn.color() >> 8) & 0xFF) / 255f;
        this.bCol = (spawn.color() & 0xFF) / 255f;
        this.alpha = spawn.startAlpha();
        AuraParticles.live++;
        setSprite(sprites[0]);
    }

    @Override
    public void tick() {
        super.tick();
        float t = lifetime <= 0 ? 1f : Math.min(1f, (float) age / lifetime);
        quadSize = spawn.startSize() + (spawn.endSize() - spawn.startSize()) * t;
        alpha = spawn.startAlpha() + (spawn.endAlpha() - spawn.startAlpha()) * t;
        oRoll = roll;
        roll += spawn.spin();
        if (sprites.length > 1) {
            setSprite(sprites[Math.min(sprites.length - 1, (int) (t * sprites.length))]);
        }
    }

    @Override
    public void remove() {
        if (!removed) {
            AuraParticles.live--;
        }
        super.remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return spawn.glow() ? 0xF000F0 : super.getLightColor(partialTick);
    }
}
*///?}
