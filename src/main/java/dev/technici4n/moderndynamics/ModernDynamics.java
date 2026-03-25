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

import dev.technici4n.moderndynamics.attachment.upgrade.AttachmentUpgradesLoader;
import dev.technici4n.moderndynamics.client.ModernDynamicsClient;
import dev.technici4n.moderndynamics.gui.MdPackets;
import dev.technici4n.moderndynamics.init.MdAttachments;
import dev.technici4n.moderndynamics.init.MdBlockEntities;
import dev.technici4n.moderndynamics.init.MdBlocks;
import dev.technici4n.moderndynamics.init.MdItems;
import dev.technici4n.moderndynamics.init.MdMenus;
import dev.technici4n.moderndynamics.network.NetworkManager;
import dev.technici4n.moderndynamics.network.TickHelper;
import dev.technici4n.moderndynamics.network.item.SimulatedInsertionTargets;
import dev.technici4n.moderndynamics.util.MdId;
import dev.technici4n.moderndynamics.util.MdItemGroup;
import dev.technici4n.moderndynamics.util.WrenchHelper;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MdId.MOD_ID)
public class ModernDynamics {
    public static final Logger LOGGER = LogManager.getLogger("Modern Dynamics");

    public ModernDynamics() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::register);

        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            NetworkManager.onServerStopped();
            SimulatedInsertionTargets.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                TickHelper.onEndTick();
                NetworkManager.onEndTick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(WrenchHelper::handleUseBlock);
        AttachmentUpgradesLoader.setup();

        if (FMLEnvironment.dist.isClient()) {
            ModernDynamicsClient.init(modBus);
        }

        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_ITEM_VARIANT, MdPackets.SET_ITEM_VARIANT_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_FLUID_VARIANT, MdPackets.SET_FLUID_VARIANT_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_FILTER_MODE, MdPackets.SET_FILTER_MODE_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_FILTER_DAMAGE, MdPackets.SET_FILTER_DAMAGE_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_FILTER_NBT, MdPackets.SET_FILTER_NBT_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_FILTER_MOD, MdPackets.SET_FILTER_MOD_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_FILTER_SIMILAR, MdPackets.SET_FILTER_SIMILAR_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_ROUTING_MODE, MdPackets.SET_ROUTING_MODE_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_OVERSENDING_MODE, MdPackets.SET_OVERSENDING_MODE_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_REDSTONE_MODE, MdPackets.SET_REDSTONE_MODE_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_MAX_ITEMS_IN_INVENTORY, MdPackets.SET_MAX_ITEMS_IN_INVENTORY_HANDLER);
        MdProxy.INSTANCE.registerPacketHandler(MdPackets.SET_MAX_ITEMS_EXTRACTED, MdPackets.SET_MAX_ITEMS_EXTRACTED_HANDLER);

        LOGGER.info("Successfully loaded Modern Dynamics!");
    }

    private void register(RegisterEvent event) {
        var registryKey = event.getRegistryKey();
        if (registryKey == Registries.BLOCK) {
            MdBlocks.init();
        } else if (registryKey == Registries.ITEM) {
            MdItems.init();
            MdAttachments.init();
        } else if (registryKey == Registries.BLOCK_ENTITY_TYPE) {
            MdBlockEntities.init();
        } else if (registryKey == Registries.MENU) {
            MdMenus.init();
        } else if (registryKey == Registries.CREATIVE_MODE_TAB) {
            MdItemGroup.init();
        }
    }
}
