package com.jeitree.tree;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Identity of an item + its component patch (count is ignored). */
public record ItemKey(Item item, DataComponentPatch patch) {
    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.getItem(), stack.getComponentsPatch());
    }
}
