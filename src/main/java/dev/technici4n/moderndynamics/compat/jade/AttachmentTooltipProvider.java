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

import dev.technici4n.moderndynamics.pipe.PipeBlockEntity;
import dev.technici4n.moderndynamics.util.MdId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

public enum AttachmentTooltipProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return MdId.of("attachment_tooltip");
    }

    @Override
    public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
        var attachmentStack = getHoveredAttachmentStack(accessor);
        if (!attachmentStack.isEmpty()) {
            return IElementHelper.get().item(attachmentStack);
        }
        return currentIcon;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var attachmentStack = getHoveredAttachmentStack(accessor);
        if (attachmentStack.isEmpty()) {
            return;
        }

        tooltip.remove(Identifiers.CORE_OBJECT_NAME);
        tooltip.add(0, IThemeHelper.get().title(attachmentStack.getHoverName()), Identifiers.CORE_OBJECT_NAME);
    }

    private static ItemStack getHoveredAttachmentStack(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PipeBlockEntity pipe)) {
            return ItemStack.EMPTY;
        }

        var hitSide = pipe.hitTestAttachments(pipe.getPosInBlock(accessor.getHitResult()));
        if (hitSide == null) {
            return ItemStack.EMPTY;
        }

        var attachmentItem = pipe.getAttachmentItem(hitSide);
        return attachmentItem == null ? ItemStack.EMPTY : new ItemStack(attachmentItem);
    }
}