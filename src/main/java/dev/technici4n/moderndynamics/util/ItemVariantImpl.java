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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class ItemVariantImpl implements ItemVariant {
    private static final Map<Item, ItemVariant> NO_TAG_CACHE = new ConcurrentHashMap<>();

    static ItemVariant of(Item item) {
        Objects.requireNonNull(item, "Item may not be null.");
        return NO_TAG_CACHE.computeIfAbsent(item, i -> new ItemVariantImpl(new ItemStack(i)));
    }

    static ItemVariant of(ItemStack stack) {
        Objects.requireNonNull(stack, "Stack may not be null.");

        if (stack.isEmpty() || !stack.hasTag()) {
            return of(stack.getItem());
        }

        return new ItemVariantImpl(stack);
    }

    static ItemVariant fromNbt(CompoundTag nbt) {
        return of(ItemStack.of(nbt));
    }

    private final ItemStack stack;
    private final int hashCode;

    private ItemVariantImpl(ItemStack stack) {
        this.stack = stack.copy();
        this.stack.setCount(1);
        this.hashCode = Objects.hash(this.stack.getItem(), this.stack.getTag());
    }

    @Override
    public Item getObject() {
        return stack.getItem();
    }

    @Override
    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameTags(this.stack, stack);
    }

    @Override
    public ItemStack toStack(int count) {
        var copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    @Override
    public int getMaxStackSize() {
        return stack.getMaxStackSize();
    }

    @Override
    public boolean isBlank() {
        return stack.isEmpty();
    }

    @Override
    public CompoundTag toNbt() {
        return toStack(1).save(new CompoundTag());
    }

    @Override
    public String toString() {
        return "ItemVariant{stack=" + stack + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemVariantImpl other)) {
            return false;
        }
        return hashCode == other.hashCode && matches(other.stack);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
