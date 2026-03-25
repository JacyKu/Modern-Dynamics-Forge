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
package dev.technici4n.moderndynamics.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.ApiStatus;

/**
 * An immutable count-less ItemStack.
 */
@ApiStatus.NonExtendable
public interface ItemVariant extends TransferVariant<Item> {
    static ItemVariant blank() {
        return of(Items.AIR);
    }

    static ItemVariant of(ItemStack stack) {
        return ItemVariantImpl.of(stack);
    }

    static ItemVariant of(ItemLike item) {
        return ItemVariantImpl.of(item.asItem());
    }

    boolean matches(ItemStack stack);

    default Item getItem() {
        return getObject();
    }

    default ItemStack toStack() {
        return toStack(1);
    }

    ItemStack toStack(int count);

    int getMaxStackSize();

    static ItemVariant fromNbt(CompoundTag nbt) {
        return ItemVariantImpl.fromNbt(nbt);
    }

    @Override
    default void toPacket(FriendlyByteBuf buf) {
        buf.writeNbt(toNbt());
    }

    static ItemVariant fromPacket(FriendlyByteBuf buf) {
        var nbt = buf.readNbt();
        return nbt == null ? blank() : fromNbt(nbt);
    }
}
