package com.jeitree.tree;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only counts of what the player is carrying. A node whose item has no custom components
 * matches any stack of that item; a node with components (potions, enchanted books...) matches exactly.
 */
public final class InventorySnapshot {
    private final Map<Item, Integer> byItem;
    private final Map<ItemKey, Integer> exact;

    private InventorySnapshot(Map<Item, Integer> byItem, Map<ItemKey, Integer> exact) {
        this.byItem = byItem;
        this.exact = exact;
    }

    public static InventorySnapshot of(Iterable<ItemStack> stacks) {
        Map<Item, Integer> byItem = new HashMap<>();
        Map<ItemKey, Integer> exact = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            byItem.merge(stack.getItem(), stack.getCount(), Integer::sum);
            exact.merge(ItemKey.of(stack), stack.getCount(), Integer::sum);
        }
        return new InventorySnapshot(byItem, exact);
    }

    /** A mutable copy used while walking the tree so one item is never counted twice. */
    public Allocator allocator() {
        return new Allocator(new HashMap<>(byItem), new HashMap<>(exact));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InventorySnapshot other && byItem.equals(other.byItem) && exact.equals(other.exact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byItem, exact);
    }

    public static final class Allocator {
        private final Map<Item, Integer> byItem;
        private final Map<ItemKey, Integer> exact;

        private Allocator(Map<Item, Integer> byItem, Map<ItemKey, Integer> exact) {
            this.byItem = byItem;
            this.exact = exact;
        }

        /** Takes up to {@code wanted} of the item out of the remaining pool and returns how many were taken. */
        public long take(ItemKey key, long wanted) {
            if (wanted <= 0) {
                return 0;
            }
            if (key.patch().isEmpty()) {
                int available = byItem.getOrDefault(key.item(), 0);
                long taken = Math.min(available, wanted);
                if (taken > 0) {
                    byItem.put(key.item(), (int) (available - taken));
                }
                return taken;
            }
            int available = exact.getOrDefault(key, 0);
            long taken = Math.min(available, wanted);
            if (taken > 0) {
                exact.put(key, (int) (available - taken));
            }
            return taken;
        }
    }
}
