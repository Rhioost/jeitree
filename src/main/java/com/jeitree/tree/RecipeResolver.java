package com.jeitree.tree;

import com.mojang.logging.LogUtils;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Asks JEI "how do I make this item?" for every recipe category (vanilla and modded) and
 * converts the answers into simple {@link RecipeOption}s. Works with anything JEI can display.
 */
public final class RecipeResolver {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Sig(Map<ItemKey, Integer> inputs, int out) {}

    private static final Comparator<RecipeOption> ORDER = (a, b) -> {
        if (a.reversible() != b.reversible()) {
            return a.reversible() ? 1 : -1;
        }
        int c = Double.compare(a.ratio(), b.ratio());
        // non-reversible: cheapest first; reversible: compressing recipes first
        return a.reversible() ? -c : c;
    };

    private final IRecipeManager recipeManager;
    private final IFocusFactory focusFactory;
    private final Map<ItemKey, List<RecipeOption>> raw = new HashMap<>();
    private final Map<ItemKey, List<RecipeOption>> sorted = new HashMap<>();

    public RecipeResolver(IJeiRuntime runtime) {
        this.recipeManager = runtime.getRecipeManager();
        this.focusFactory = runtime.getJeiHelpers().getFocusFactory();
    }

    /** Recipes producing {@code stack}, best default first, with "reversible" (A<->B) loops flagged. */
    public List<RecipeOption> optionsFor(ItemStack stack) {
        ItemKey key = ItemKey.of(stack);
        List<RecipeOption> cached = sorted.get(key);
        if (cached != null) {
            return cached;
        }
        List<RecipeOption> list = new ArrayList<>();
        for (RecipeOption option : rawOptions(stack)) {
            boolean reversible = false;
            for (RecipeOption.Input in : option.inputs()) {
                for (RecipeOption other : rawOptions(in.stack())) {
                    if (other.consumes(key)) {
                        reversible = true;
                        break;
                    }
                }
                if (reversible) {
                    break;
                }
            }
            list.add(option.withReversible(reversible));
        }
        list.sort(ORDER);
        List<RecipeOption> result = List.copyOf(list);
        sorted.put(key, result);
        return result;
    }

    private List<RecipeOption> rawOptions(ItemStack stack) {
        ItemKey key = ItemKey.of(stack);
        List<RecipeOption> cached = raw.get(key);
        if (cached != null) {
            return cached;
        }
        List<RecipeOption> result = new ArrayList<>();
        try {
            ItemStack target = stack.copyWithCount(1);
            IFocus<ItemStack> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, target);
            IFocusGroup group = focusFactory.createFocusGroup(List.of(focus));
            Set<Sig> seen = new HashSet<>();
            List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup()
                    .limitFocus(List.of(focus))
                    .get()
                    .toList();
            for (IRecipeCategory<?> category : categories) {
                collect(category, group, target, result, seen);
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Recipe lookup failed for {}", stack, e);
        }
        List<RecipeOption> copy = List.copyOf(result);
        raw.put(key, copy);
        return copy;
    }

    private <R> void collect(IRecipeCategory<R> category, IFocusGroup group, ItemStack target,
                             List<RecipeOption> out, Set<Sig> seen) {
        List<R> recipes;
        try {
            recipes = recipeManager.createRecipeLookup(category.getRecipeType())
                    .limitFocus(group.getAllFocuses())
                    .get()
                    .toList();
        } catch (RuntimeException e) {
            return;
        }

        for (R recipe : recipes) {
            try {
                Optional<IRecipeLayoutDrawable<R>> layout =
                        recipeManager.createRecipeLayoutDrawable(category, recipe, focusFactory.getEmptyFocusGroup());
                if (layout.isEmpty()) {
                    continue;
                }
                IRecipeSlotsView view = layout.get().getRecipeSlotsView();

                int outCount = 0;
                for (IRecipeSlotView slot : view.getSlotViews(RecipeIngredientRole.OUTPUT)) {
                    for (ItemStack s : slot.getItemStacks().toList()) {
                        if (ItemStack.isSameItem(s, target)) {
                            outCount = Math.max(1, s.getCount());
                            break;
                        }
                    }
                    if (outCount > 0) {
                        break;
                    }
                }
                if (outCount <= 0) {
                    continue;
                }

                Map<ItemKey, Integer> counts = new LinkedHashMap<>();
                Map<ItemKey, ItemStack> reps = new HashMap<>();
                for (IRecipeSlotView slot : view.getSlotViews(RecipeIngredientRole.INPUT)) {
                    List<ItemStack> stacks = slot.getItemStacks().filter(s -> !s.isEmpty()).toList();
                    if (stacks.isEmpty()) {
                        continue; // fluid / other ingredient types are ignored
                    }
                    ItemStack chosen = stacks.stream()
                            .filter(s -> !ItemStack.isSameItem(s, target))
                            .findFirst()
                            .orElse(stacks.get(0));
                    ItemKey k = ItemKey.of(chosen);
                    counts.merge(k, Math.max(1, chosen.getCount()), Integer::sum);
                    reps.putIfAbsent(k, chosen.copyWithCount(1));
                }
                if (counts.isEmpty()) {
                    continue;
                }
                if (!seen.add(new Sig(counts, outCount))) {
                    continue; // e.g. smelting / blasting / smoking with identical inputs
                }

                List<RecipeOption.Input> inputs = new ArrayList<>();
                for (Map.Entry<ItemKey, Integer> e : counts.entrySet()) {
                    inputs.add(new RecipeOption.Input(reps.get(e.getKey()), e.getValue()));
                }
                out.add(new RecipeOption(category.getTitle(), outCount, List.copyOf(inputs), false));
            } catch (RuntimeException e) {
                LOGGER.debug("Skipping recipe in {}", category.getRecipeType().getUid(), e);
            }
        }
    }
}
