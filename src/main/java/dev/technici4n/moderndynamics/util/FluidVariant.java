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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface FluidVariant extends TransferVariant<Fluid> {
    static FluidVariant blank() {
        return of(Fluids.EMPTY);
    }

    static FluidVariant of(FluidStack stack) {
        return FluidVariantImpl.of(stack);
    }

    static FluidVariant of(Fluid fluid) {
        return FluidVariantImpl.of(fluid);
    }

    boolean matches(FluidStack stack);

    default Fluid getFluid() {
        return getObject();
    }

    FluidStack toStack(int amount);

    static FluidVariant fromNbt(CompoundTag nbt) {
        return FluidVariantImpl.fromNbt(nbt);
    }

    @Override
    default void toPacket(FriendlyByteBuf buf) {
        buf.writeNbt(toNbt());
    }

    static FluidVariant fromPacket(FriendlyByteBuf buf) {
        var nbt = buf.readNbt();
        return nbt == null ? blank() : fromNbt(nbt);
    }

    default List<Component> getTooltip() {
        var tooltip = new ArrayList<Component>();
        tooltip.add(toStack(1).getDisplayName());

        var modId = BuiltInRegistries.FLUID.getKey(getFluid()).getNamespace();
        var modName = "" + ChatFormatting.BLUE + ChatFormatting.ITALIC +
                ModList.get().getModContainerById(modId).map(mc -> mc.getModInfo().getDisplayName()).orElse(modId);
        tooltip.add(Component.literal(modName));
        return tooltip;
    }
}
