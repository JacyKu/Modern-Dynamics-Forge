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
package dev.technici4n.moderndynamics.network;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks adjacent block entity capabilities and schedules a host update if they invalidate.
 */
public final class HostAdjacentCaps<C> {
    private final NodeHost host;
    private final Capability<C> capability;
    @SuppressWarnings("unchecked")
    private final LazyOptional<C>[] capCaches = new LazyOptional[6];

    public HostAdjacentCaps(NodeHost host, Capability<C> capability) {
        this.host = host;
        this.capability = capability;
    }

    public void invalidateCaches() {
        for (int i = 0; i < capCaches.length; i++) {
            capCaches[i] = null;
        }
    }

    @Nullable
    public C getCapability(Direction dir) {
        int side = dir.get3DDataValue();
        var cached = capCaches[side];
        if (cached == null || !cached.isPresent()) {
            cached = resolve(dir);
            capCaches[side] = cached;
            final var observed = cached;
            cached.addListener(ignored -> {
                if (capCaches[side] == observed) {
                    capCaches[side] = null;
                }
                if (!host.getPipe().isRemoved()) {
                    host.scheduleUpdate();
                }
            });
        }
        return cached.orElse(null);
    }

    private LazyOptional<C> resolve(Direction dir) {
        var level = host.getPipe().getLevel();
        if (level == null) {
            return LazyOptional.empty();
        }

        var adjacentPos = host.getPipe().getBlockPos().relative(dir);
        var adjacentBe = level.getBlockEntity(adjacentPos);
        if (adjacentBe == null) {
            return LazyOptional.empty();
        }

        return adjacentBe.getCapability(capability, dir.getOpposite());
    }
}
