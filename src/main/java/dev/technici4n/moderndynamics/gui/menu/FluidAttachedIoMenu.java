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

import dev.technici4n.moderndynamics.Constants;
import dev.technici4n.moderndynamics.attachment.attached.FluidAttachedIo;
import dev.technici4n.moderndynamics.gui.MdPackets;
import dev.technici4n.moderndynamics.init.MdMenus;
import dev.technici4n.moderndynamics.pipe.PipeBlockEntity;
import dev.technici4n.moderndynamics.util.FluidVariant;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraftforge.fluids.FluidUtil;

public class FluidAttachedIoMenu extends AttachedIoMenu<FluidAttachedIo> {

    public FluidAttachedIoMenu(int syncId, Inventory playerInventory, PipeBlockEntity pipe, Direction side, FluidAttachedIo attachment) {
        super(MdMenus.FLUID_IO.menuType(), syncId, playerInventory, pipe, side, attachment);

        // Config slots
        var row = 0;
        var col = 0;
        for (int i = 0; i < Constants.Upgrades.MAX_FILTER; i++) {
            this.addSlot(new FluidConfigSlot(44 + col * 18, 20 + row * 18, attachment, i));
            if (++col >= 5) {
                col = 0;
                row++;
            }
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        if (slotIndex >= 0 && getSlot(slotIndex) instanceof FluidConfigSlot configSlot && configSlot.isActive()) {
            var selectedVariant = FluidUtil.getFluidContained(getCarried())
                    .map(FluidVariant::of)
                    .orElse(FluidVariant.blank());
            setFilter(configSlot.getConfigIdx(), selectedVariant, isClientSide());
        } else {
            super.clicked(slotIndex, button, actionType, player);
        }
    }

    @Override
    protected boolean trySetFilterOnShiftClick(int clickedSlot) {
        var fluidVariant = FluidUtil.getFluidContained(slots.get(clickedSlot).getItem())
                .map(FluidVariant::of)
                .filter(fv -> {
                    for (var slot : slots) {
                        if (slot instanceof FluidConfigSlot fluidConfig && fluidConfig.getFilter().equals(fv)) {
                            return false;
                        }
                    }
                    return true;
                })
                .orElse(null);
        if (fluidVariant != null) {
            for (var slot : slots) {
                if (slot instanceof FluidConfigSlot fluidConfig) {
                    if (fluidConfig.getFilter().isBlank()) {
                        setFilter(fluidConfig.getConfigIdx(), fluidVariant, isClientSide());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void setFilter(int configIdx, FluidVariant variant, boolean sendPacket) {
        if (isClientSide() && sendPacket) {
            MdPackets.sendSetFilter(containerId, configIdx, variant);
        }
        attachment.setFilter(configIdx, variant);
    }
}
