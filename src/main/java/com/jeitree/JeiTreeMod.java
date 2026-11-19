package com.jeitree;

import com.jeitree.client.RecipeTreeScreen;
import com.jeitree.client.Theme;
import com.jeitree.tree.RecipeResolver;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;

@Mod(value = JeiTreeMod.MOD_ID, dist = Dist.CLIENT)
public final class JeiTreeMod {
    public static final String MOD_ID = "jeitree";

    public static final KeyMapping OPEN_TREE = new KeyMapping(
            "key.jeitree.open_tree",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_T,
            "key.categories.jeitree");

    public JeiTreeMod(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onKeyPressed);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TREE);
    }

    private void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) Theme::load);
    }

    private void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!OPEN_TREE.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            return;
        }
        Screen screen = event.getScreen();
        if (screen instanceof RecipeTreeScreen) {
            return; // the tree screen handles its own key
        }
        IJeiRuntime runtime = JeiTreePlugin.runtime();
        if (runtime == null || runtime.getIngredientListOverlay().hasKeyboardFocus()) {
            return; // don't hijack typing in JEI's search box
        }
        ItemStack hovered = findHovered(runtime, screen);
        if (hovered.isEmpty()) {
            return;
        }
        Minecraft.getInstance().setScreen(new RecipeTreeScreen(screen, hovered, new RecipeResolver(runtime)));
        event.setCanceled(true);
    }

    private static ItemStack findHovered(IJeiRuntime runtime, Screen screen) {
        if (screen instanceof IRecipesGui gui) {
            ItemStack s = toStack(gui.getIngredientUnderMouse(VanillaTypes.ITEM_STACK));
            if (!s.isEmpty()) {
                return s;
            }
        }
        IIngredientListOverlay overlay = runtime.getIngredientListOverlay();
        if (overlay.isListDisplayed()) {
            ItemStack s = toStack(overlay.getIngredientUnderMouse(VanillaTypes.ITEM_STACK));
            if (!s.isEmpty()) {
                return s;
            }
        }
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) {
                return slot.getItem().copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack toStack(Object o) {
        if (o instanceof Optional<?> opt) {
            return opt.map(JeiTreeMod::toStack).orElse(ItemStack.EMPTY);
        }
        if (o instanceof ItemStack stack && !stack.isEmpty()) {
            return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }
}
