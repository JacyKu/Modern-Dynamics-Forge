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
package dev.technici4n.moderndynamics.attachment.upgrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.technici4n.moderndynamics.ModernDynamics;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public class AttachmentUpgradesLoader extends SimplePreparableReloadListener<List<JsonObject>> {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // A bit dirty... could maybe use a better fabric API hook?
    private static final Map<ResourceManager, LoadedUpgrades> LOADED_UPGRADES = new WeakHashMap<>();

    private AttachmentUpgradesLoader() {
    }

    public static LoadedUpgrades load(ResourceManager resourceManager) {
        return parseEntries(collectEntries(resourceManager));
    }

    private static List<JsonObject> collectEntries(ResourceManager resourceManager) {
        List<JsonObject> result = new ArrayList<>();

        for (var entry : resourceManager.listResources("attachment_upgrades", s -> s.getPath().endsWith(".json")).entrySet()) {
            var resource = entry.getValue();
            try (var inputStream = resource.open();
                    var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                result.add(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | JsonParseException | IllegalStateException exception) { // getAsJsonObject can throw ISE
                ModernDynamics.LOGGER.error("Error when loading Modern Dynamics attachment upgrade with path %s".formatted(entry.getKey()),
                        exception);
            }
        }

        return result;
    }

    private static LoadedUpgrades parseEntries(List<JsonObject> array) {
        Map<Item, UpgradeType> map = new IdentityHashMap<>();
        List<Item> list = new ArrayList<>();

        for (JsonObject obj : array) {
            if (!ICondition.shouldRegisterEntry(obj)) {
                continue;
            }

            try {
                var item = GsonHelper.getAsItem(obj, "item");
                var deserialized = GSON.fromJson(obj, UpgradeType.class);
                // TODO validate

                if (!map.containsKey(item)) {
                    list.add(item);
                }
                map.put(item, deserialized);
            } catch (Exception exception) {
                ModernDynamics.LOGGER.error("Failed to read attachment upgrade entry " + obj, exception);
            }
        }

        return new LoadedUpgrades(map, list);
    }

    private static void cacheLoadedUpgrades(ResourceManager resourceManager, LoadedUpgrades upgrades) {
        LOADED_UPGRADES.put(resourceManager, upgrades);

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.getResourceManager() == resourceManager) {
            LoadedUpgrades.trySet(upgrades);
        }
    }

    private static LoadedUpgrades getOrLoadUpgrades(ResourceManager resourceManager) {
        var upgrades = LOADED_UPGRADES.get(resourceManager);
        if (upgrades == null) {
            upgrades = load(resourceManager);
            LOADED_UPGRADES.put(resourceManager, upgrades);
        }

        return upgrades;
    }

    private static void ensureLoaded(ResourceManager resourceManager) {
        LoadedUpgrades.trySet(getOrLoadUpgrades(resourceManager));
    }

    @Override
    protected List<JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return collectEntries(resourceManager);
    }

    @Override
    protected void apply(List<JsonObject> array, ResourceManager resourceManager, ProfilerFiller profiler) {
        cacheLoadedUpgrades(resourceManager, parseEntries(array));
    }

    public static void setup() {
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new AttachmentUpgradesLoader()));
        MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            var server = event.getServer();
            ensureLoaded(server.getResourceManager());
        });
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            ensureLoaded(server.getResourceManager());

            var player = event.getPlayer();
            if (player != null) {
                LoadedUpgrades.syncToClient(player);
            } else {
                for (var connectedPlayer : event.getPlayerList().getPlayers()) {
                    LoadedUpgrades.syncToClient(connectedPlayer);
                }
            }
        });
    }
}
