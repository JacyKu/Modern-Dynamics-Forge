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
package dev.technici4n.moderndynamics.extender;

import dev.technici4n.moderndynamics.MdBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MachineExtenderBlockEntity extends MdBlockEntity {
    private boolean inApiQuery = false;
    boolean inNeighborUpdate = false;

    public MachineExtenderBlockEntity(BlockEntityType<?> bet, BlockPos pos, BlockState state) {
        super(bet, pos, state);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction direction) {
        if (inApiQuery || level == null) {
            return super.getCapability(capability, direction);
        }

        var below = level.getBlockEntity(getBlockPos().below());
        if (below == null) {
            return super.getCapability(capability, direction);
        }

        inApiQuery = true;
        try {
            var forwarded = below.getCapability(capability, direction);
            if (forwarded.isPresent()) {
                return forwarded;
            }
        } finally {
            inApiQuery = false;
        }

        return super.getCapability(capability, direction);
    }

    @Override
    public void toTag(CompoundTag tag) {
    }

    @Override
    public void fromTag(CompoundTag tag) {
    }

    @Override
    public void toClientTag(CompoundTag tag) {
    }

    @Override
    public void fromClientTag(CompoundTag tag) {
    }
}
