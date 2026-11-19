package com.jeitree.tree;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeTree {
    public static final int MAX_DEPTH = 12;
    public static final int MAX_NODES = 600;

    /** amount = total required, have = how much of it the player already carries. */
    public record Total(ItemStack stack, long amount, long have) {}
    public record CraftTotal(ItemStack stack, Component category, long crafts) {}
    private record CraftKey(ItemKey item, String category) {}

    private final RecipeResolver resolver;
    private final ItemStack target;
    private long targetAmount;
    private @Nullable InventorySnapshot inventory;
    private TreeNode root;
    private int budget;
    private int nextRow;
    private int maxDepth;

    public RecipeTree(RecipeResolver resolver, ItemStack target, long amount, @Nullable InventorySnapshot inventory) {
        this.resolver = resolver;
        this.target = target.copyWithCount(1);
        this.targetAmount = Math.max(1, amount);
        this.inventory = inventory;
        reset();
    }

    public TreeNode root() {
        return root;
    }

    public long amount() {
        return targetAmount;
    }

    public int maxDepth() {
        return maxDepth;
    }

    /** Re-resolves the whole tree with default recipe choices. */
    public void reset() {
        budget = MAX_NODES;
        root = createNode(target, null);
        expand(root);
        recalc();
    }

    public void setAmount(long amount) {
        targetAmount = Mth.clamp(amount, 1L, 999_999L);
        recalc();
    }

    /** Pass null to ignore the player's inventory entirely. */
    public void setInventory(@Nullable InventorySnapshot snapshot) {
        this.inventory = snapshot;
        recalc();
    }

    /** Cycle through recipes; the last slot in the cycle is "base ingredient". */
    public void cycle(TreeNode node, int direction) {
        int n = node.options.size();
        if (n == 0) {
            return;
        }
        node.selected = Math.floorMod(node.selected + direction, n + 1);
        rebuild(node);
    }

    public void setRaw(TreeNode node) {
        if (node.options.isEmpty() || node.isRaw()) {
            return;
        }
        node.selected = node.options.size();
        rebuild(node);
    }

    /** Assigns a row to every node and returns the number of rows. */
    public int layout() {
        nextRow = 0;
        maxDepth = 0;
        layoutNode(root);
        return nextRow;
    }

    /** Items that are still leaves of the tree (nothing crafts them), with how much the player already has. */
    public List<Total> rawTotals() {
        Map<ItemKey, long[]> sums = new LinkedHashMap<>();
        Map<ItemKey, ItemStack> reps = new LinkedHashMap<>();
        collectRaw(root, sums, reps);
        List<Total> result = new ArrayList<>();
        for (Map.Entry<ItemKey, long[]> e : sums.entrySet()) {
            result.add(new Total(reps.get(e.getKey()), e.getValue()[0], e.getValue()[1]));
        }
        return result;
    }

    /** Crafts needed, deepest steps first (so it reads in crafting order). */
    public List<CraftTotal> craftTotals() {
        Map<CraftKey, long[]> sums = new LinkedHashMap<>();
        Map<CraftKey, ItemStack> stacks = new LinkedHashMap<>();
        Map<CraftKey, Component> titles = new LinkedHashMap<>();
        collectCrafts(root, sums, stacks, titles);
        List<CraftTotal> result = new ArrayList<>();
        for (Map.Entry<CraftKey, long[]> e : sums.entrySet()) {
            result.add(new CraftTotal(stacks.get(e.getKey()), titles.get(e.getKey()), e.getValue()[0]));
        }
        return result;
    }

    // ---- structure ----

    private TreeNode createNode(ItemStack stack, @Nullable TreeNode parent) {
        TreeNode node = new TreeNode(stack.copyWithCount(1), parent, 0);
        budget--;
        if (node.depth < MAX_DEPTH && budget > 0) {
            List<RecipeOption> filtered = new ArrayList<>();
            for (RecipeOption o : resolver.optionsFor(node.stack)) {
                if (!node.conflicts(o)) {
                    filtered.add(o);
                }
            }
            node.options = List.copyOf(filtered);
        }
        node.selected = defaultSelection(node.options);
        return node;
    }

    private static int defaultSelection(List<RecipeOption> options) {
        if (options.isEmpty()) {
            return 0; // == options.size() -> raw
        }
        RecipeOption first = options.get(0);
        if (!first.reversible()) {
            return 0;
        }
        // Only A<->B style recipes exist. Craft "up" (ingots -> block) but treat "down" (block -> ingots) as raw.
        return first.ratio() >= 1.0 ? 0 : options.size();
    }

    private void expand(TreeNode node) {
        node.children.clear();
        RecipeOption o = node.option();
        if (o == null) {
            return;
        }
        for (RecipeOption.Input in : o.inputs()) {
            TreeNode child = createNode(in.stack(), node);
            node.children.add(child);
            expand(child);
        }
    }

    private void rebuild(TreeNode node) {
        node.children.clear();
        budget = Math.max(1, MAX_NODES - count(root));
        expand(node);
        recalc();
    }

    // ---- amounts ----

    /**
     * Walks the tree top-down. Each node first takes what it can from the inventory (the goal item itself
     * never does), and only the remainder has to be crafted, which shrinks the amounts of its inputs.
     */
    private void recalc() {
        InventorySnapshot.Allocator alloc = inventory == null ? null : inventory.allocator();
        root.amount = targetAmount;
        allocate(root, alloc);
    }

    private void allocate(TreeNode node, @Nullable InventorySnapshot.Allocator alloc) {
        node.have = (alloc == null || node.parent == null) ? 0 : alloc.take(node.key, node.amount);
        RecipeOption o = node.option();
        if (o == null) {
            node.crafts = 0;
            return;
        }
        long toMake = node.amount - node.have;
        node.crafts = toMake <= 0 ? 0 : ceilDiv(toMake, o.outputCount());
        for (int i = 0; i < node.children.size(); i++) {
            TreeNode child = node.children.get(i);
            child.amount = o.inputs().get(i).count() * node.crafts;
            allocate(child, alloc);
        }
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private int count(TreeNode node) {
        int n = 1;
        for (TreeNode c : node.children) {
            n += count(c);
        }
        return n;
    }

    private void layoutNode(TreeNode node) {
        maxDepth = Math.max(maxDepth, node.depth);
        if (node.children.isEmpty()) {
            node.row = nextRow++;
            return;
        }
        for (TreeNode c : node.children) {
            layoutNode(c);
        }
        node.row = (node.children.get(0).row + node.children.get(node.children.size() - 1).row) / 2.0;
    }

    private void collectRaw(TreeNode node, Map<ItemKey, long[]> sums, Map<ItemKey, ItemStack> reps) {
        if (node.children.isEmpty()) {
            if (node.amount > 0) {
                long[] s = sums.computeIfAbsent(node.key, k -> new long[2]);
                s[0] += node.amount;
                s[1] += node.have;
                reps.putIfAbsent(node.key, node.stack);
            }
            return;
        }
        for (TreeNode c : node.children) {
            collectRaw(c, sums, reps);
        }
    }

    private void collectCrafts(TreeNode node, Map<CraftKey, long[]> sums, Map<CraftKey, ItemStack> stacks,
                               Map<CraftKey, Component> titles) {
        for (TreeNode c : node.children) {
            collectCrafts(c, sums, stacks, titles);
        }
        RecipeOption o = node.option();
        if (o != null && node.crafts > 0) {
            CraftKey k = new CraftKey(node.key, o.category().getString());
            sums.computeIfAbsent(k, x -> new long[1])[0] += node.crafts;
            stacks.putIfAbsent(k, node.stack);
            titles.putIfAbsent(k, o.category());
        }
    }
}
