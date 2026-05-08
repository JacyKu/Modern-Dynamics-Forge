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
package dev.technici4n.moderndynamics.compat.jade;

import dev.technici4n.moderndynamics.attachment.attached.ItemAttachedIo;
import dev.technici4n.moderndynamics.init.MdItems;
import dev.technici4n.moderndynamics.network.item.ItemHost;
import dev.technici4n.moderndynamics.pipe.PipeBlockEntity;
import dev.technici4n.moderndynamics.util.ItemVariant;
import dev.technici4n.moderndynamics.util.MdId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

public enum ItemPipeServerProvider implements IServerExtensionProvider<PipeBlockEntity, ItemStack>,
    IClientExtensionProvider<ItemStack, ItemView> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return MdId.of("item_pipe");
    }

    @Override
    public List<ViewGroup<ItemStack>> getGroups(ServerPlayer player, ServerLevel level, PipeBlockEntity pipe, boolean showDetails) {
        var itemHost = pipe.findHost(ItemHost.class);
        if (itemHost == null) {
            return List.of();
        }

        List<ViewGroup<ItemStack>> groups = new ArrayList<>();

        for (var side : Direction.values()) {
            if (pipe.getAttachment(side) instanceof ItemAttachedIo io && io.isStuffed()) {
                var group = new ViewGroup<ItemStack>(new ArrayList<>());
                group.views.addAll(variantMapToStacks(io.getStuffedItems()));
                group.id = "stuffed_" + side.getName();
                groups.add(group);
            }
        }

        var dummyGroup = new ViewGroup<ItemStack>(new ArrayList<>());
        dummyGroup.views.add(MdItems.WRENCH.getDefaultInstance());
        dummyGroup.id = "dummy";
        groups.add(dummyGroup);

        return groups;
    }

    private static Collection<ItemStack> variantMapToStacks(Map<ItemVariant, ? extends Number> map) {
        List<ItemStack> stacks = new ArrayList<>();
        for (var entry : map.entrySet()) {
            stacks.add(entry.getKey().toStack((int) Math.min(Integer.MAX_VALUE, entry.getValue().longValue())));
        }
        stacks.sort(Comparator.comparingInt(ItemStack::getCount).reversed());
        return stacks;
    }

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> serverGroups) {
        if (!(accessor.getTarget() instanceof PipeBlockEntity pipe)) {
            return List.of();
        }

        List<ClientViewGroup<ItemView>> clientGroups = new ArrayList<>();
        ItemStack hoveredAttachmentStack = ItemStack.EMPTY;

        var posInBlock = pipe.getPosInBlock(accessor.getHitResult());
        var hitAttachmentSide = pipe.hitTestAttachments(posInBlock);
        if (hitAttachmentSide != null) {
            var attachmentItem = pipe.getAttachmentItem(hitAttachmentSide);
            if (attachmentItem != null) {
                hoveredAttachmentStack = new ItemStack(attachmentItem);
                serverGroups.stream()
                        .filter(viewGroup -> viewGroup.id != null && viewGroup.id.equals("stuffed_" + hitAttachmentSide.getName()))
                        .findFirst()
                        .ifPresent(group -> {
                            var clientGroup = new ClientViewGroup<ItemView>(new ArrayList<>());
                            for (var stack : group.views) {
                                clientGroup.views.add(new ItemView(stack));
                            }
                            clientGroup.title = Component.translatable("gui.moderndynamics.tooltip.stuffed", attachmentItem.getDescription())
                                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true));
                            clientGroups.add(clientGroup);
                        });
            }
        }

        if (!clientGroups.isEmpty()) {
            return clientGroups;
        }

        for (var host : pipe.getHosts()) {
            if (host instanceof ItemHost itemHost) {
                Map<ItemVariant, Integer> items = new HashMap<>();
                for (var item : itemHost.getClientTravelingItems()) {
                    items.merge(item.variant(), item.amount(), Integer::sum);
                }

                var clientGroup = new ClientViewGroup<ItemView>(new ArrayList<>());
                for (var stack : variantMapToStacks(items)) {
                    clientGroup.views.add(new ItemView(stack));
                }
                if (!clientGroup.views.isEmpty()) {
                    clientGroups.add(clientGroup);
                }
            }
        }

        return clientGroups;
    }
}