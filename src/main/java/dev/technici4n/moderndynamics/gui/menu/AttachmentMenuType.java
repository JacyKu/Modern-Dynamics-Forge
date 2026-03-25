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
package dev.technici4n.moderndynamics.gui.menu;

import com.google.common.util.concurrent.Runnables;
import dev.technici4n.moderndynamics.attachment.AttachmentItem;
import dev.technici4n.moderndynamics.attachment.attached.AttachedAttachment;
import dev.technici4n.moderndynamics.pipe.PipeBlockEntity;
import dev.technici4n.moderndynamics.util.ExtendedMenuProvider;
import dev.technici4n.moderndynamics.util.MdId;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.IContainerFactory;
import org.jetbrains.annotations.Nullable;

public class AttachmentMenuType<A extends AttachedAttachment, T extends AbstractContainerMenu> implements IContainerFactory<T> {
    private final AttachmentFactory<A, ? extends AttachmentItem> attachmentFactory;
    private final MenuFactory<A, T> menuFactory;
    private final MenuType<T> menuType;

    private AttachmentMenuType(AttachmentFactory<A, ? extends AttachmentItem> attachmentFactory, MenuFactory<A, T> menuFactory) {
        this.attachmentFactory = attachmentFactory;
        this.menuFactory = menuFactory;
        this.menuType = IForgeMenuType.create(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T create(int windowId, Inventory inv, FriendlyByteBuf data) {
        var world = inv.player.level();

        var bet = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(data.readResourceLocation());
        var side = data.readEnum(Direction.class);
        var pos = data.readBlockPos();
        var item = BuiltInRegistries.ITEM.byId(data.readVarInt());
        if (!(item instanceof AttachmentItem attachmentItem)) {
            throw new IllegalStateException("Server sent a non-attachment item as menu host: " + item);
        }
        var tag = data.readNbt();
        var attachment = ((AttachmentFactory<A, AttachmentItem>) attachmentFactory).createAttachment(attachmentItem, tag, Runnables.doNothing());

        return world.getBlockEntity(pos, bet).map(blockEntity -> {
            if (blockEntity instanceof PipeBlockEntity pipe) {
                return menuFactory.createMenu(windowId, inv, pipe, side, attachment);
            }
            return null;
        }).orElse(null);
    }

    public static <A extends AttachedAttachment, T extends AbstractContainerMenu, I extends AttachmentItem> MenuType<T> create(
            String id, AttachmentFactory<A, I> attachmentFactory, MenuFactory<A, T> menuFactory) {
        var type = new AttachmentMenuType<>(attachmentFactory, menuFactory);
        Registry.register(BuiltInRegistries.MENU, MdId.of(id), type.menuType);
        return type.menuType;
    }

    public static <A extends AttachedAttachment, T extends AbstractContainerMenu, I extends AttachmentItem> AttachmentMenuType<A, T> createType(
            String id, AttachmentFactory<A, I> attachmentFactory, MenuFactory<A, T> menuFactory) {
        var type = new AttachmentMenuType<>(attachmentFactory, menuFactory);
        Registry.register(BuiltInRegistries.MENU, MdId.of(id), type.menuType);
        return type;
    }

    public MenuType<T> menuType() {
        return menuType;
    }

    public interface AttachmentFactory<A extends AttachedAttachment, I extends AttachmentItem> {
        A createAttachment(I item, CompoundTag configData, Runnable setChangedCallback);
    }

    public interface MenuFactory<A extends AttachedAttachment, T extends AbstractContainerMenu> {
        T createMenu(int syncId, Inventory playerInventory, PipeBlockEntity pipe, Direction side, A attachment);
    }

    public ExtendedMenuProvider createMenu(PipeBlockEntity pipe, Direction side, A attachment) {
        return new ExtendedMenuProvider() {
            @Override
            public void writeScreenOpeningData(FriendlyByteBuf buf) {
                buf.writeResourceLocation(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(pipe.getType()));
                buf.writeEnum(side);
                buf.writeBlockPos(pipe.getBlockPos());
                buf.writeVarInt(BuiltInRegistries.ITEM.getId(attachment.getItem()));
                buf.writeNbt(attachment.writeConfigTag(new CompoundTag()));
            }

            @Override
            public Component getDisplayName() {
                return attachment.getDisplayName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return menuFactory.createMenu(syncId, inventory, pipe, side, attachment);
            }
        };
    }
}
