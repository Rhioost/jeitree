package com.jeitree.client;

import com.jeitree.JeiTreeMod;
import com.jeitree.tree.InventorySnapshot;
import com.jeitree.tree.RecipeOption;
import com.jeitree.tree.RecipeResolver;
import com.jeitree.tree.RecipeTree;
import com.jeitree.tree.TreeNode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RecipeTreeScreen extends Screen {
    private static final int SLOT = 18;
    private static final int FRAME = 22;   // sprite size drawn around a 18px slot
    private static final int COL_W = 54;
    private static final int ROW_H = 26;
    private static final int PANEL_W = 170;
    private static final int TOP = 46;

    /** Remembered between screens, so the choice sticks while you browse. */
    private static boolean useInventory = true;

    private final @Nullable Screen previous;
    private final RecipeResolver resolver;
    private final RecipeTree tree;

    private @Nullable InventorySnapshot lastSnapshot;
    private int tickCounter;

    private double scrollX = -16;
    private double scrollY = 0;
    private double panelScroll = 0;
    private int contentW, contentH, panelContentH;
    private boolean centered;

    private TreeNode hovered;
    private TreeNode pressed;
    private int pressedButton;
    private boolean dragged;

    public RecipeTreeScreen(@Nullable Screen previous, ItemStack target, RecipeResolver resolver) {
        super(Component.translatable("jeitree.screen.title", target.getHoverName()));
        this.previous = previous;
        this.resolver = resolver;
        this.lastSnapshot = snapshot();
        this.tree = new RecipeTree(resolver, target, 1, lastSnapshot);
    }

    /** Read-only look at the player's own main inventory (hotbar + 27 slots). Never modified, never sent anywhere. */
    private static @Nullable InventorySnapshot snapshot() {
        if (!useInventory) {
            return null;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        Inventory inv = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            stacks.add(inv.getItem(i));
        }
        return InventorySnapshot.of(stacks);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> tree.setAmount(tree.amount() - step()))
                .bounds(10, 20, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> tree.setAmount(tree.amount() + step()))
                .bounds(34, 20, 20, 20).build());
        addRenderableWidget(Button.builder(inventoryLabel(), b -> {
                    useInventory = !useInventory;
                    lastSnapshot = snapshot();
                    tree.setInventory(lastSnapshot);
                    b.setMessage(inventoryLabel());
                })
                .bounds(150, 20, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("jeitree.button.reset"), b -> {
                    tree.reset();
                    centered = false;
                })
                .bounds(width - 128, 20, 60, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width - 64, 20, 54, 20).build());
    }

    private static Component inventoryLabel() {
        return Component.translatable(useInventory ? "jeitree.button.inventory_on" : "jeitree.button.inventory_off");
    }

    private static int step() {
        return hasShiftDown() ? 16 : hasControlDown() ? 64 : 1;
    }

    @Override
    public void tick() {
        // Pick up changes to the inventory (e.g. picking items up) twice a second.
        if (++tickCounter % 10 == 0) {
            InventorySnapshot now = snapshot();
            if (!Objects.equals(now, lastSnapshot)) {
                lastSnapshot = now;
                tree.setInventory(now);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- rendering ----

    /** No vanilla blur/dim: the wallpaper (if any) is drawn at the start of render(), otherwise it is transparent. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        Theme t = Theme.get();
        t.drawBackground(g, width, height); // wallpaper if the pack has one, otherwise nothing (transparent)
        super.render(g, mx, my, partialTick); // buttons

        g.drawString(font, title, 10, 7, t.color("text"), true);
        g.drawString(font, Component.translatable("jeitree.amount", tree.amount()), 60, 26, t.color("text"), true);

        int treeRight = width - PANEL_W - 8;
        int bottom = height - 6;
        int rows = tree.layout();
        contentW = (tree.maxDepth() + 1) * COL_W;
        contentH = rows * ROW_H;
        if (!centered) {
            scrollY = tree.root().row * ROW_H - (bottom - TOP) / 2.0 + SLOT / 2.0;
            centered = true;
        }
        clampScroll();

        hovered = null;
        g.enableScissor(0, TOP, treeRight, bottom);
        drawNode(g, t, tree.root(), 20 - (int) scrollX, TOP + 6 - (int) scrollY, mx, my, treeRight, bottom);
        g.disableScissor();

        drawPanel(g, t, width - PANEL_W - 4, TOP, PANEL_W, bottom);

        if (hovered != null) {
            renderNodeTooltip(g, hovered, mx, my);
        }
    }

    private void drawNode(GuiGraphics g, Theme t, TreeNode n, int ox, int oy, int mx, int my, int treeRight, int bottom) {
        int x = ox + n.depth * COL_W;
        int y = oy + (int) (n.row * ROW_H);

        if (!n.children.isEmpty()) {
            int midX = x + SLOT + (COL_W - SLOT) / 2;
            int cy = y + SLOT / 2;
            int minY = cy;
            int maxY = cy;
            t.lineH(g, x + SLOT, midX, cy);
            for (TreeNode c : n.children) {
                int childY = oy + (int) (c.row * ROW_H) + SLOT / 2;
                t.lineH(g, midX, x + COL_W, childY);
                minY = Math.min(minY, childY);
                maxY = Math.max(maxY, childY);
            }
            t.lineV(g, midX, minY, maxY);
            t.joint(g, midX, cy);
            for (TreeNode c : n.children) {
                t.joint(g, midX, oy + (int) (c.row * ROW_H) + SLOT / 2);
            }
        }

        boolean hov = mx >= x && mx < x + SLOT && my >= y && my < y + SLOT
                && mx < treeRight && my >= TOP && my < bottom;
        if (hov) {
            hovered = n;
        }

        boolean notNeeded = n.amount <= 0;
        boolean covered = useInventory && n.parent != null && n.amount > 0 && n.have >= n.amount;
        long remaining = n.amount - n.have;

        // frame: hover > covered by inventory > base ingredient > crafted
        String frame = hov ? "node_hover" : covered ? "node_done" : n.isRaw() ? "node_raw" : "node_crafted";
        String frameColor = hov ? "hover" : covered ? "done" : n.isRaw() ? "raw" : "crafted";
        if (!t.draw(g, frame, x - 2, y - 2, FRAME, FRAME)) {
            g.fill(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, t.color(frameColor));
            g.fill(x, y, x + SLOT, y + SLOT, t.color("node_bg"));
        }

        g.renderItem(n.stack, x + 1, y + 1);
        String count = (!notNeeded && !covered && remaining > 1) ? fmt(remaining) : null;
        g.renderItemDecorations(font, n.stack, x + 1, y + 1, count);

        if (notNeeded || covered) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 190);
            g.fill(x, y, x + SLOT, y + SLOT, t.color("dim"));
            g.pose().popPose();
        }
        if (covered) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            if (!t.draw(g, "icon_check", x + SLOT - 6, y - 5, 9, 9)) {
                g.drawString(font, "\u2713", x + SLOT - 5, y - 5, t.color("have"), true);
            }
            g.pose().popPose();
        }

        for (TreeNode c : n.children) {
            drawNode(g, t, c, ox, oy, mx, my, treeRight, bottom);
        }
    }

    private void drawPanel(GuiGraphics g, Theme t, int px, int top, int pw, int bottom) {
        if (!t.draw(g, "panel", px, top, pw, bottom - top)) {
            g.fill(px, top, px + pw, bottom, t.color("panel"));
        }
        g.enableScissor(px + 2, top + 2, px + pw - 2, bottom - 2);
        int startY = top + 6 - (int) panelScroll;
        int y = startY;
        int textColor = t.color("text");

        g.drawString(font, Component.translatable("jeitree.raw"), px + 6, y, t.color("header_raw"), true);
        y += 12;
        for (RecipeTree.Total total : tree.rawTotals()) {
            g.renderItem(total.stack(), px + 6, y);
            String name = total.stack().getHoverName().getString();
            String text;
            int color;
            if (useInventory) {
                text = total.have() + "/" + total.amount() + " " + name;
                color = total.have() >= total.amount() ? t.color("have")
                        : total.have() > 0 ? t.color("partial") : t.color("missing");
            } else {
                text = total.amount() + "x " + name;
                color = textColor;
            }
            g.drawString(font, font.plainSubstrByWidth(text, pw - 34), px + 27, y + 5, color, true);
            y += SLOT;
        }

        y += 6;
        g.drawString(font, Component.translatable("jeitree.steps"), px + 6, y, t.color("header_steps"), true);
        y += 12;
        for (RecipeTree.CraftTotal c : tree.craftTotals()) {
            g.renderItem(c.stack(), px + 6, y);
            String text = c.crafts() + "x " + c.category().getString();
            g.drawString(font, font.plainSubstrByWidth(text, pw - 34), px + 27, y + 5, textColor, true);
            y += SLOT;
        }
        panelContentH = y - startY + 8;
        g.disableScissor();
    }

    private void renderNodeTooltip(GuiGraphics g, TreeNode n, int mx, int my) {
        List<Component> lines = new ArrayList<>(
                n.stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.Default.NORMAL));
        lines.add(Component.empty());
        if (n.amount <= 0) {
            lines.add(Component.translatable("jeitree.tooltip.covered").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("jeitree.tooltip.needed", n.amount).withStyle(ChatFormatting.YELLOW));
            if (useInventory && n.parent != null) {
                ChatFormatting color = n.have >= n.amount ? ChatFormatting.GREEN
                        : n.have > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                lines.add(Component.translatable("jeitree.tooltip.have", n.have).withStyle(color));
                if (n.have < n.amount) {
                    lines.add(Component.translatable("jeitree.tooltip.remaining", n.amount - n.have)
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        RecipeOption o = n.option();
        if (o != null) {
            lines.add(Component.translatable("jeitree.tooltip.recipe", n.selected + 1, n.options.size(), o.category())
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("jeitree.tooltip.raw").withStyle(ChatFormatting.GREEN));
        }
        if (!n.options.isEmpty()) {
            lines.add(Component.translatable("jeitree.tooltip.hint1").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("jeitree.tooltip.hint2").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("jeitree.tooltip.hint3").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("jeitree.tooltip.hint4").withStyle(ChatFormatting.DARK_GRAY));
        g.renderTooltip(font, lines, Optional.empty(), mx, my);
    }

    private static String fmt(long n) {
        return n >= 10000 ? (n / 1000) + "k" : Long.toString(n);
    }

    // ---- input ----

    private void clampScroll() {
        int viewW = width - PANEL_W - 12;
        int viewH = height - 6 - TOP;
        scrollX = Mth.clamp(scrollX, -20, Math.max(-20, contentW - viewW + 24));
        scrollY = Mth.clamp(scrollY, -20, Math.max(-20, contentH - viewH + 24));
    }

    private boolean inTreeArea(double mx, double my) {
        return mx < width - PANEL_W - 8 && my >= TOP && my < height - 6;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (inTreeArea(mx, my)) {
            pressed = hovered;
            pressedButton = button;
            dragged = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && pressed != null || inTreeArea(mx, my)) {
            if (Math.abs(dx) + Math.abs(dy) > 0) {
                dragged = true;
            }
            scrollX -= dx;
            scrollY -= dy;
            clampScroll();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressed != null && !dragged && pressed == hovered) {
            if (pressedButton == 0) {
                if (hasShiftDown()) {
                    tree.setRaw(pressed);
                } else {
                    tree.cycle(pressed, 1);
                }
            } else if (pressedButton == 1) {
                tree.cycle(pressed, -1);
            }
        }
        pressed = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollXAmount, double scrollYAmount) {
        if (mx >= width - PANEL_W - 4) {
            int max = Math.max(0, panelContentH - (height - 6 - TOP));
            panelScroll = Mth.clamp(panelScroll - scrollYAmount * 12, 0, max);
        } else if (hasShiftDown()) {
            scrollX -= scrollYAmount * 24;
        } else {
            scrollY -= scrollYAmount * 24;
        }
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hovered != null
                && JeiTreeMod.OPEN_TREE.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            minecraft.setScreen(new RecipeTreeScreen(this, hovered.stack, resolver));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
