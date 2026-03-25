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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

final class FluidVariantImpl implements FluidVariant {
    private static final FluidVariant BLANK = new FluidVariantImpl(FluidStack.EMPTY);
    private static final Map<Fluid, FluidVariant> NO_TAG_CACHE = new ConcurrentHashMap<>();

    private static Fluid normalizeFluid(Fluid fluid) {
        if (fluid != Fluids.EMPTY && fluid instanceof FlowingFluid flowingFluid && !fluid.isSource(fluid.defaultFluidState())) {
            return flowingFluid.getSource();
        }

        if (fluid != Fluids.EMPTY && !fluid.isSource(fluid.defaultFluidState()) && !(fluid instanceof FlowingFluid)) {
            ResourceLocation id = Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid));
            throw new IllegalArgumentException("Cannot convert flowing fluid %s (%s) into a still fluid.".formatted(id, fluid));
        }

        return fluid;
    }

    static FluidVariant of(Fluid fluid) {
        Objects.requireNonNull(fluid, "Fluid may not be null.");
        fluid = normalizeFluid(fluid);
        if (fluid == Fluids.EMPTY) {
            return BLANK;
        }

        return NO_TAG_CACHE.computeIfAbsent(fluid, f -> new FluidVariantImpl(new FluidStack(f, 1)));
    }

    static FluidVariant of(FluidStack stack) {
        Objects.requireNonNull(stack, "Stack may not be null.");

        if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY || !stack.hasTag()) {
            return of(stack.getFluid());
        }

        return new FluidVariantImpl(stack);
    }

    static FluidVariant fromNbt(CompoundTag nbt) {
        return of(FluidStack.loadFluidStackFromNBT(nbt));
    }

    private final FluidStack stack;
    private final int hashCode;

    private FluidVariantImpl(FluidStack stack) {
        if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
            this.stack = FluidStack.EMPTY;
        } else {
            this.stack = stack.copy();
            this.stack.setAmount(1);
        }
        this.hashCode = Objects.hash(this.stack.getFluid(), this.stack.getTag());
    }

    @Override
    public Fluid getObject() {
        return stack.getFluid();
    }

    @Override
    public boolean matches(FluidStack stack) {
        return this.stack.getFluid() == stack.getFluid() && Objects.equals(this.stack.getTag(), stack.getTag());
    }

    @Override
    public FluidStack toStack(int amount) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }

        var copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    @Override
    public boolean isBlank() {
        return stack.isEmpty();
    }

    @Override
    public CompoundTag toNbt() {
        return toStack(1).writeToNBT(new CompoundTag());
    }

    @Override
    public String toString() {
        return "FluidVariant{stack=" + stack + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FluidVariantImpl other)) {
            return false;
        }
        return hashCode == other.hashCode && matches(other.stack);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
