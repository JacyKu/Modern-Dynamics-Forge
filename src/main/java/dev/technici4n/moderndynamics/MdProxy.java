/*
 * Modern Dynamics
 * Copyright (C) 2021 shartte & Technici4n
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package dev.technici4n.moderndynamics;

import dev.technici4n.moderndynamics.util.MdId;
import dev.technici4n.moderndynamics.util.UnsidedPacketHandler;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class MdProxy {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MdId.of("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);
    private static final Map<ResourceLocation, UnsidedPacketHandler> HANDLERS = new ConcurrentHashMap<>();

    public static final MdProxy INSTANCE = createInstance();

    static {
        CHANNEL.messageBuilder(ServerboundRawPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundRawPacket::encode)
                .decoder(ServerboundRawPacket::decode)
                .consumerNetworkThread(
                        (BiConsumer<ServerboundRawPacket, Supplier<NetworkEvent.Context>>) (packet, contextSupplier) -> handleRawPacket(packet,
                                contextSupplier.get()))
                .add();
        CHANNEL.messageBuilder(ClientboundRawPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundRawPacket::encode)
                .decoder(ClientboundRawPacket::decode)
                .consumerNetworkThread(
                        (BiConsumer<ClientboundRawPacket, Supplier<NetworkEvent.Context>>) (packet, contextSupplier) -> handleRawPacket(packet,
                                contextSupplier.get()))
                .add();
    }

    private static MdProxy createInstance() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            try {
                return (MdProxy) Class.forName("dev.technici4n.moderndynamics.client.ClientProxy").getConstructor().newInstance();
            } catch (Exception exception) {
                throw new RuntimeException("Failed to instantiate Modern Dynamics client proxy.", exception);
            }
        }

        return new MdProxy();
    }

    public boolean isShiftDown() {
        return false;
    }

    public boolean isMemoryConnection() {
        return false;
    }

    protected Player getClientPlayer() {
        return null;
    }

    /**
     * Register a packet that can be received by both sides, server and client.
     */
    public void registerPacketHandler(ResourceLocation packetId, UnsidedPacketHandler unsidedHandler) {
        HANDLERS.put(packetId, unsidedHandler);
    }

    /**
     * Send a packet to the server.
     */
    public void sendPacket(ResourceLocation packetId, FriendlyByteBuf buf) {
        CHANNEL.sendToServer(new ServerboundRawPacket(packetId, copyPayload(buf)));
    }

    public void sendPacket(ServerPlayer player, ResourceLocation packetId, FriendlyByteBuf buf) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundRawPacket(packetId, copyPayload(buf)));
    }

    private static void handleRawPacket(AbstractRawPacket packet, NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            var handler = HANDLERS.get(packet.packetId);
            if (handler == null) {
                return;
            }

            Player player = context.getDirection().getReceptionSide().isServer() ? context.getSender() : INSTANCE.getClientPlayer();
            if (player == null) {
                return;
            }

            var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.payload));
            try {
                var action = handler.handlePacket(player, buf);
                if (action != null) {
                    action.run();
                }
            } catch (RuntimeException exception) {
                ModernDynamics.LOGGER.error("Failed to handle packet {} for {}", packet.packetId, player.getGameProfile().getName(), exception);
            } finally {
                buf.release();
            }
        });
        context.setPacketHandled(true);
    }

    private static byte[] copyPayload(FriendlyByteBuf buf) {
        byte[] payload = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), payload);
        return payload;
    }

    private abstract static class AbstractRawPacket {
        private final ResourceLocation packetId;
        private final byte[] payload;

        private AbstractRawPacket(ResourceLocation packetId, byte[] payload) {
            this.packetId = packetId;
            this.payload = payload;
        }

        protected ResourceLocation packetId() {
            return packetId;
        }

        protected byte[] payload() {
            return payload;
        }

        private static void encode(AbstractRawPacket packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.packetId());
            buf.writeVarInt(packet.payload().length);
            buf.writeBytes(packet.payload());
        }
    }

    private static final class ServerboundRawPacket extends AbstractRawPacket {
        private ServerboundRawPacket(ResourceLocation packetId, byte[] payload) {
            super(packetId, payload);
        }

        private static void encode(ServerboundRawPacket packet, FriendlyByteBuf buf) {
            AbstractRawPacket.encode(packet, buf);
        }

        private static ServerboundRawPacket decode(FriendlyByteBuf buf) {
            var packetId = buf.readResourceLocation();
            byte[] payload = new byte[buf.readVarInt()];
            buf.readBytes(payload);
            return new ServerboundRawPacket(packetId, payload);
        }
    }

    private static final class ClientboundRawPacket extends AbstractRawPacket {
        private ClientboundRawPacket(ResourceLocation packetId, byte[] payload) {
            super(packetId, payload);
        }

        private static void encode(ClientboundRawPacket packet, FriendlyByteBuf buf) {
            AbstractRawPacket.encode(packet, buf);
        }

        private static ClientboundRawPacket decode(FriendlyByteBuf buf) {
            var packetId = buf.readResourceLocation();
            byte[] payload = new byte[buf.readVarInt()];
            buf.readBytes(payload);
            return new ClientboundRawPacket(packetId, payload);
        }
    }
}
