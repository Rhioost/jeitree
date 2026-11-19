package com.jeitree.tree;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class TreeNode {
    public final ItemStack stack; // count is always 1; use {@link #amount}
    public final ItemKey key;
    public final TreeNode parent;
    public final int depth;

    public long amount;
    public long have;
    public long crafts;
    public List<RecipeOption> options = List.of();
    /** Index into options; options.size() means "use as base ingredient". */
    public int selected;
    public final List<TreeNode> children = new ArrayList<>();
    public double row;

    TreeNode(ItemStack stack, TreeNode parent, long amount) {
        this.stack = stack;
        this.key = ItemKey.of(stack);
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
        this.amount = amount;
    }

    public boolean isRaw() {
        return selected >= options.size();
    }

    public RecipeOption option() {
        return isRaw() ? null : options.get(selected);
    }

    /** True if the option needs this item or one of its ancestors (would loop forever). */
    boolean conflicts(RecipeOption option) {
        for (RecipeOption.Input in : option.inputs()) {
            ItemKey k = in.key();
            for (TreeNode a = this; a != null; a = a.parent) {
                if (a.key.equals(k)) {
                    return true;
                }
            }
        }
        return false;
    }
}
