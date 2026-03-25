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
package dev.technici4n.moderndynamics.client.attachment;

import dev.technici4n.moderndynamics.attachment.upgrade.AttachmentUpgradesLoader;
import dev.technici4n.moderndynamics.attachment.upgrade.LoadedUpgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

public final class ClientAttachmentUpgrades {
    private ClientAttachmentUpgrades() {
    }

    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<LoadedUpgrades>() {
            @Override
            protected LoadedUpgrades prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return AttachmentUpgradesLoader.load(resourceManager);
            }

            @Override
            protected void apply(LoadedUpgrades upgrades, ResourceManager resourceManager, ProfilerFiller profiler) {
                LoadedUpgrades.trySet(upgrades);
            }
        });
    }

    public static LoadedUpgrades ensureLoaded() {
        var upgrades = LoadedUpgrades.get();
        if (!upgrades.list.isEmpty()) {
            return upgrades;
        }

        var minecraft = Minecraft.getInstance();
        var resourceManager = minecraft.getResourceManager();
        upgrades = AttachmentUpgradesLoader.load(resourceManager);
        LoadedUpgrades.trySet(upgrades);

        return upgrades;
    }
}
