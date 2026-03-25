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
package dev.technici4n.moderndynamics.client;

import dev.technici4n.moderndynamics.MdProxy;
import dev.technici4n.moderndynamics.attachment.RenderedAttachment;
import dev.technici4n.moderndynamics.client.attachment.ClientAttachmentUpgrades;
import dev.technici4n.moderndynamics.client.attachment.SetAttachmentUpgradesPacket;
import dev.technici4n.moderndynamics.client.ber.PipeBlockEntityRenderer;
import dev.technici4n.moderndynamics.client.model.PipeModelLoader;
import dev.technici4n.moderndynamics.client.screen.FluidAttachedIoScreen;
import dev.technici4n.moderndynamics.client.screen.ItemAttachedIoScreen;
import dev.technici4n.moderndynamics.gui.MdPackets;
import dev.technici4n.moderndynamics.init.MdBlocks;
import dev.technici4n.moderndynamics.init.MdMenus;
import dev.technici4n.moderndynamics.network.item.sync.ClientTravelingItemSmoothing;
import dev.technici4n.moderndynamics.pipe.PipeBlock;
import dev.technici4n.moderndynamics.pipe.PipeBlockEntity;
import dev.technici4n.moderndynamics.util.MdId;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ModernDynamicsClient {
    public static void init(IEventBus modBus) {
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_ATTACHMENT_UPGRADES, SetAttachmentUpgradesPacket.HANDLER);

        modBus.addListener(ModernDynamicsClient::onClientSetup);
        modBus.addListener(ModernDynamicsClient::registerGeometryLoaders);
        modBus.addListener(ModernDynamicsClient::registerRenderers);
        modBus.addListener(ModernDynamicsClient::registerClientReloadListeners);

        MinecraftForge.EVENT_BUS.addListener(ModernDynamicsClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ModernDynamicsClient::renderPipeAttachmentOutline);
    }

    private static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        var modelMap = new HashMap<String, ResourceLocation>();
        for (var id : RenderedAttachment.getAttachmentIds()) {
            modelMap.put(id, MdId.of("attachment/" + id));
        }

        event.register(PipeModelLoader.ID.getPath(), new PipeModelLoader(modelMap));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (PipeBlock pipeBlock : MdBlocks.ALL_PIPES) {
                ItemBlockRenderTypes.setRenderLayer(pipeBlock, RenderType.cutout());
            }

            MenuScreens.register(MdMenus.ITEM_IO.menuType(), ItemAttachedIoScreen::new);
            MenuScreens.register(MdMenus.FLUID_IO.menuType(), FluidAttachedIoScreen::new);
        });
    }

    private static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        ClientAttachmentUpgrades.registerReloadListeners(event);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (PipeBlock pipeBlock : MdBlocks.ALL_PIPES) {
            var blockEntityType = pipeBlock.getBlockEntityTypeNullable();
            if (blockEntityType != null) {
                event.registerBlockEntityRenderer(blockEntityType, PipeBlockEntityRenderer::new);
            }
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !Minecraft.getInstance().isPaused()) {
            ClientTravelingItemSmoothing.onUnpausedTick();
        }
    }

    /**
     * Highlights only the pipe attachment when it's under the mouse cursor to indicate it has special interactions.
     */
    private static void renderPipeAttachmentOutline(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        var blockHitResult = event.getTarget();
        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        var pos = blockHitResult.getBlockPos();
        var blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof PipeBlock) {
            var be = level.getBlockEntity(pos);
            if (be instanceof PipeBlockEntity pipe) {
                var hitResult = Minecraft.getInstance().hitResult;
                if (hitResult == null) {
                    return;
                }

                var hitPosInBlock = hitResult.getLocation();
                hitPosInBlock = hitPosInBlock.subtract(pos.getX(), pos.getY(), pos.getZ());

                var hitSide = pipe.hitTestAttachments(hitPosInBlock);
                if (hitSide != null) {
                    // Forge 1.20.1 does not expose the helper used by the NeoForge branch here.
                    // Keep the hit-test path alive; a custom lines renderer can be restored later.
                }
            }
        }
    }
}
