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
package dev.technici4n.moderndynamics.network.fluid;

import com.google.common.primitives.Ints;
import dev.technici4n.moderndynamics.Constants;
import dev.technici4n.moderndynamics.attachment.IoAttachmentType;
import dev.technici4n.moderndynamics.attachment.attached.FluidAttachedIo;
import dev.technici4n.moderndynamics.network.NetworkCache;
import dev.technici4n.moderndynamics.network.NetworkNode;
import dev.technici4n.moderndynamics.util.FluidVariant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

public class FluidCache extends NetworkCache<FluidHost, FluidCache> {
    private FluidCacheStorage fluidStorage = null;
    private int attractorBuffer = 0;
    private boolean allowNetworkIo = true;

    protected FluidCache(ServerLevel level, List<NetworkNode<FluidHost, FluidCache>> networkNodes) {
        super(level, networkNodes);
    }

    public FluidCacheStorage getOrCreateStorage() {
        combine();
        return fluidStorage;
    }

    @Override
    protected void doCombine() {
        FluidVariant fv = FluidVariant.blank();
        int amount = 0;

        for (var node : nodes) {
            var host = node.getHost();

            if (!host.getVariant().isBlank()) {
                if (fv.isBlank()) {
                    fv = host.getVariant();
                }
                amount += host.getAmount();
            }
        }

        fluidStorage = new FluidCacheStorage();
        fluidStorage.variant = fv;
        fluidStorage.amount = amount;
    }

    @Override
    protected void doSeparate() {
        nodes.sort(Comparator.comparingLong(node -> Constants.Fluids.CAPACITY));
        int remainingNodes = nodes.size();

        for (NetworkNode<FluidHost, FluidCache> node : nodes) {
            FluidHost host = node.getHost();

            int nodeAmount = (int) Math.min(Constants.Fluids.CAPACITY, fluidStorage.amount / remainingNodes);
            host.setContents(fluidStorage.variant, nodeAmount);
            fluidStorage.amount -= nodeAmount;
            remainingNodes--;
        }

        fluidStorage = null;
    }

    @Override
    public void doTick() {
        combine();

        List<ConnectedFluidStorage> targets = new ArrayList<>();
        for (var node : nodes) {
            if (node.getHost().isTicking()) {
                node.getHost().updateConnections();
                node.getHost().gatherCapabilities(targets);
            }
        }

        List<FluidAttachedIo> attractors = new ArrayList<>();
        for (var conn : targets) {
            if (conn.attachment() != null && conn.attachment().getType() == IoAttachmentType.ATTRACTOR) {
                attractors.add(conn.attachment());
            }
        }

        boolean changedVariant = false;
        allowNetworkIo = false;

        try {
            if (fluidStorage.isResourceBlank()) {
                var newVariant = findVariantForNetwork(targets, attractors);
                if (!newVariant.isBlank() && canChangeVariant()) {
                    fluidStorage.variant = newVariant;
                    changedVariant = true;
                }
            }

            if (!fluidStorage.isResourceBlank()) {
                extractFluid(targets);
                attractFluid(targets, attractors);
                distributeFluid(targets);

                if (fluidStorage.amount == 0 && canChangeVariant()) {
                    fluidStorage.variant = FluidVariant.blank();
                    changedVariant = true;
                }
            }
        } finally {
            allowNetworkIo = true;
        }

        if (changedVariant) {
            separate();
        }

        separate();
    }

    private boolean canChangeVariant() {
        for (var node : nodes) {
            if (!node.getHost().isTicking()) {
                return false;
            }
        }
        return true;
    }

    private FluidVariant findVariantForNetwork(List<ConnectedFluidStorage> targets, List<FluidAttachedIo> attractors) {
        for (var t : targets) {
            if (t.attachment() != null && t.attachment().getType() == IoAttachmentType.EXTRACTOR) {
                var toExtract = findExtractableResource(t.storage(), fv -> t.attachment().matchesFilter(fv));
                if (!toExtract.isBlank()) {
                    return toExtract;
                }
            }
        }

        if (!attractors.isEmpty()) {
            Predicate<FluidVariant> attractorFilter = fv -> {
                for (var a : attractors) {
                    if (a.matchesFilter(fv)) {
                        return true;
                    }
                }
                return false;
            };

            for (var t : targets) {
                var toExtract = findExtractableResource(t.storage(), attractorFilter);
                if (!toExtract.isBlank()) {
                    return toExtract;
                }
            }
        }

        return FluidVariant.blank();
    }

    private void extractFluid(List<ConnectedFluidStorage> targets) {
        fluidStorage.amount += transferForTargets(FluidCache::drain, targets, fluidStorage.variant,
                fluidStorage.getCapacity() - fluidStorage.amount, ConnectedFluidStorage::extractorFilteredStorage);
    }

    private void attractFluid(List<ConnectedFluidStorage> targets, List<FluidAttachedIo> attractors) {
        int attractorPower = 0;
        for (var attractor : attractors) {
            attractorPower += attractor.matchesFilter(fluidStorage.variant) ? (int) attractor.getFluidMaxIo() : 0;
        }
        int maxAttract = attractorBuffer + attractorPower;
        int attracted = transferForTargets(FluidCache::drain, targets, fluidStorage.variant,
                Math.min(fluidStorage.getCapacity() - fluidStorage.amount, maxAttract),
                ConnectedFluidStorage::storage);
        attractorBuffer = Math.min(maxAttract - attracted, FluidType.BUCKET_VOLUME);
        fluidStorage.amount += attracted;
    }

    private void distributeFluid(List<ConnectedFluidStorage> targets) {
        fluidStorage.amount -= transferForTargets(FluidCache::fill, targets, fluidStorage.variant,
                fluidStorage.amount, ConnectedFluidStorage.filterAttractors(true));
        fluidStorage.amount -= transferForTargets(FluidCache::fill, targets, fluidStorage.variant,
                fluidStorage.amount, ConnectedFluidStorage.filterAttractors(false));
    }

    private static int transferForTargets(TransferOperation operation, List<ConnectedFluidStorage> targets, FluidVariant variant, int maxAmount,
            Function<ConnectedFluidStorage, IFluidHandler> storageGetter) {
        if (maxAmount == 0) {
            return 0;
        }

        int intMaxAmount = Ints.saturatedCast(maxAmount);
        List<FluidTarget> sortableTargets = new ArrayList<>(targets.size());
        for (var target : targets) {
            var storage = storageGetter.apply(target);
            if (storage != null) {
                sortableTargets.add(new FluidTarget(storage));
            }
        }
        Collections.shuffle(sortableTargets);
        for (FluidTarget target : sortableTargets) {
            target.simulationResult = operation.transfer(target.target, variant, intMaxAmount, IFluidHandler.FluidAction.SIMULATE);
        }
        sortableTargets.sort(Comparator.comparingLong(t -> t.simulationResult));
        int transferredAmount = 0;
        for (int i = 0; i < sortableTargets.size(); ++i) {
            FluidTarget target = sortableTargets.get(i);
            int remainingTargets = sortableTargets.size() - i;
            long remainingAmount = maxAmount - transferredAmount;
            int targetMaxAmount = Ints.saturatedCast(remainingAmount / remainingTargets);

            transferredAmount += operation.transfer(target.target, variant, targetMaxAmount, IFluidHandler.FluidAction.EXECUTE);
        }
        return transferredAmount;
    }

    interface TransferOperation {
        int transfer(IFluidHandler storage, FluidVariant resource, int maxAmount, IFluidHandler.FluidAction action);
    }

    private static class FluidTarget {
        final IFluidHandler target;
        long simulationResult;

        FluidTarget(IFluidHandler target) {
            this.target = target;
        }
    }

    @Override
    public void appendDebugInfo(StringBuilder out) {
        super.appendDebugInfo(out);
        if (fluidStorage == null) {
            out.append("no item storage\n");
        } else {
            out.append("item variant = ").append(fluidStorage.variant).append("\n");
            out.append("amount = ").append(fluidStorage.amount).append("\n");
            out.append("capacity = ").append(fluidStorage.getCapacity()).append("\n");
        }
    }

    static boolean areCompatible(FluidVariant v1, FluidVariant v2) {
        return v1.isBlank() || v2.isBlank() || v1.equals(v2);
    }

    public class FluidCacheStorage implements IFluidHandler {
        private FluidVariant variant = FluidVariant.blank();
        private int amount;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tank == 0 ? variant.toStack(amount) : FluidStack.EMPTY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (tank == 0) {
                return variant.matches(stack) || (variant.isBlank() && canChangeVariant());
            } else {
                return false;
            }
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }

            if (!allowNetworkIo) {
                return 0;
            }

            if (isFluidValid(0, resource)) {
                var insertedAmount = Math.min(resource.getAmount(), getCapacity() - amount);
                if (insertedAmount > 0) {
                    if (action.execute()) {
                        if (variant.isBlank()) {
                            variant = FluidVariant.of(resource);
                            amount = insertedAmount;
                        } else {
                            amount += insertedAmount;
                        }
                        update();
                    }
                    return insertedAmount;
                }
            }

            return 0;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (!allowNetworkIo) {
                return FluidStack.EMPTY;
            }

            var extractedAmount = Math.min(maxDrain, amount);
            if (extractedAmount > 0) {
                var result = variant.toStack(extractedAmount);

                if (action.execute()) {
                    amount -= extractedAmount;
                    if (amount == 0 && canChangeVariant()) {
                        variant = FluidVariant.blank();
                    }
                    update();
                }

                return result;
            }

            return FluidStack.EMPTY;
        }

        private void update() {
            var oldVariant = nodes.get(0).getHost().getVariant();

            if (!Objects.equals(oldVariant, variant)) {
                separate();
            }
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (!allowNetworkIo || resource.isEmpty()) {
                return FluidStack.EMPTY;
            }

            if (variant.matches(resource)) {
                return drain(resource.getAmount(), action);
            }

            return FluidStack.EMPTY;
        }

        public boolean isResourceBlank() {
            return variant.isBlank();
        }

        public FluidVariant getResource() {
            return variant;
        }

        public int getAmount() {
            return amount;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? getCapacity() : 0;
        }

        public int getCapacity() {
            return (int) (nodes.size() * Constants.Fluids.CAPACITY);
        }
    }

    private FluidVariant findExtractableResource(IFluidHandler storage, Predicate<FluidVariant> filter) {
        for (int i = 0; i < storage.getTanks(); i++) {
            var fluidInTank = storage.getFluidInTank(i);
            var variant = FluidVariant.of(fluidInTank);
            if (filter.test(variant)) {
                if (!storage.drain(fluidInTank, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                    return variant;
                }
            }
        }
        return FluidVariant.blank();
    }

    private static int drain(IFluidHandler handler, FluidVariant variant, int maxAmount, IFluidHandler.FluidAction action) {
        var stack = variant.toStack(maxAmount);
        var result = handler.drain(stack, action);
        return result.getAmount();
    }

    private static int fill(IFluidHandler handler, FluidVariant variant, int maxAmount, IFluidHandler.FluidAction action) {
        return handler.fill(variant.toStack(maxAmount), action);
    }
}
