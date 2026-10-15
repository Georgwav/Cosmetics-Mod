package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.util.LogOnce;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Hypixel's Mod API: when Hypixel says hello, Sol asks for the location event, and from then on knows where you are
 * (a lobby, or a game and its mode). Auto GG listens only in games, and "Play again" queues the mode you just
 * played. Nothing is sent anywhere else, and nothing at all off Hypixel (only Hypixel says hello). If another mod
 * already handles the Mod API, Sol's part stays out of its way.
 */
public final class HypixelModApi {
    /** Raw packet bytes, read and written by {@link HypixelPackets}. */
    public record Raw(Type<Raw> kind, byte[] data) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return kind;
        }
    }

    private static final CustomPacketPayload.Type<Raw> HELLO = type("hello");
    private static final CustomPacketPayload.Type<Raw> LOCATION = type("location");
    private static final CustomPacketPayload.Type<Raw> REGISTER = type("register");

    private static volatile HypixelPackets.Location location;
    private static volatile boolean ready;

    private HypixelModApi() {
    }

    private static CustomPacketPayload.Type<Raw> type(String path) {
        //? if >=1.21.11 {
        return new CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("hypixel", path));
        //?} else {
        /*return new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hypixel", path));
        *///?}
    }

    private static StreamCodec<FriendlyByteBuf, Raw> codec(CustomPacketPayload.Type<Raw> kind) {
        return StreamCodec.of((buf, raw) -> buf.writeBytes(raw.data()), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new Raw(kind, data);
        });
    }

    /** At startup: the packet types and the receivers. */
    public static void register() {
        try {
            //? if >=26.1 {
            /*PayloadTypeRegistry.clientboundPlay().register(HELLO, codec(HELLO));
            PayloadTypeRegistry.clientboundPlay().register(LOCATION, codec(LOCATION));
            PayloadTypeRegistry.serverboundPlay().register(REGISTER, codec(REGISTER));
            *///?} else {
            PayloadTypeRegistry.playS2C().register(HELLO, codec(HELLO));
            PayloadTypeRegistry.playS2C().register(LOCATION, codec(LOCATION));
            PayloadTypeRegistry.playC2S().register(REGISTER, codec(REGISTER));
            //?}
        } catch (IllegalArgumentException e) {
            // Another mod speaks the Mod API already.
            LogOnce.info("hypixel-mod-api", "Another mod handles Hypixel's Mod API; Sol leaves it to that one");
            return;
        }
        ClientPlayNetworking.registerGlobalReceiver(HELLO, (raw, context) -> {
            try {
                ClientPlayNetworking.send(new Raw(REGISTER, HypixelPackets.registerLocation()));
                ready = true;
            } catch (RuntimeException e) {
                LogOnce.info("hypixel-register", "Couldn't ask Hypixel for the location event: {}", e.toString());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(LOCATION, (raw, context) -> {
            HypixelPackets.Location l = HypixelPackets.location(raw.data());
            if (l != null) {
                location = l;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            location = null;
            ready = false;
        });
        CosmeticsMod.LOGGER.debug("[Sol] Hypixel Mod API ready");
    }

    /** Where Hypixel last said you are, or null (not on Hypixel, or it hasn't said yet). */
    public static HypixelPackets.Location location() {
        return location;
    }

    /** Whether Hypixel said hello on this connection (the Mod API is there). */
    public static boolean connected() {
        return ready;
    }
}
