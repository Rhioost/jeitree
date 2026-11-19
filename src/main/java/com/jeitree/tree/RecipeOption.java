package com.jeitree.tree;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One way of crafting an item: a category title, how many items one craft yields and the inputs. */
public record RecipeOption(Component category, int outputCount, List<Input> inputs, boolean reversible) {

    public record Input(ItemStack stack, int count) {
        public ItemKey key() {
            return ItemKey.of(stack);
        }
    }

    public int totalInputs() {
        int total = 0;
        for (Input in : inputs) {
            total += in.count();
        }
        return total;
    }

    /** Input items consumed per output item. &gt;= 1 means "compressing", &lt; 1 means "decompressing". */
    public double ratio() {
        return (double) totalInputs() / Math.max(1, outputCount);
    }

    public boolean consumes(ItemKey key) {
        for (Input in : inputs) {
            if (in.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    public RecipeOption withReversible(boolean value) {
        return new RecipeOption(category, outputCount, inputs, value);
    }
}
