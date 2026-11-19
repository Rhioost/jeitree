package com.jeitree.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jeitree.JeiTreeMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resource-pack driven look. A pack overrides {@code assets/jeitree/theme.json} (colours + sprite layout)
 * and the texture atlas it points to (default {@code assets/jeitree/textures/gui/theme.png}).
 * Anything missing falls back to flat, built-in drawing, so a partial or broken pack never crashes.
 */
public final class Theme {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation THEME_JSON =
            ResourceLocation.fromNamespaceAndPath(JeiTreeMod.MOD_ID, "theme.json");

    private static final ResourceLocation DEFAULT_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(JeiTreeMod.MOD_ID, "textures/gui/background.png");

    /** A rectangle inside the atlas. border &gt; 0 makes it a nine-slice that can be stretched. */
    public record Sprite(int u, int v, int w, int h, int border) {}

    private static final Map<String, Integer> DEFAULT_COLORS = Map.ofEntries(
            Map.entry("line", 0xFF8A8A8A),
            Map.entry("panel", 0x90000000),
            Map.entry("text", 0xFFFFFFFF),
            Map.entry("node_bg", 0xFF2B2B2B),
            Map.entry("raw", 0xFF55AA55),
            Map.entry("crafted", 0xFF5577DD),
            Map.entry("hover", 0xFFFFFFFF),
            Map.entry("done", 0xFFFFD34D),
            Map.entry("dim", 0xAA202020),
            Map.entry("background_tint", 0x00000000),
            Map.entry("have", 0xFF55FF55),
            Map.entry("partial", 0xFFFFFF55),
            Map.entry("missing", 0xFFFF5555),
            Map.entry("header_raw", 0xFF55FF55),
            Map.entry("header_steps", 0xFF55FFFF));

    private static volatile Theme current = new Theme(null, 0, 0, Map.of(), Map.of(), null, 0, 0);

    private final @Nullable ResourceLocation texture;
    private final int texW;
    private final int texH;
    private final Map<String, Sprite> sprites;
    private final Map<String, Integer> colors;
    private final @Nullable ResourceLocation background;
    private final int bgW;
    private final int bgH;

    private Theme(@Nullable ResourceLocation texture, int texW, int texH,
                  Map<String, Sprite> sprites, Map<String, Integer> colors,
                  @Nullable ResourceLocation background, int bgW, int bgH) {
        this.texture = texture;
        this.texW = texW;
        this.texH = texH;
        this.sprites = sprites;
        this.colors = colors;
        this.background = background;
        this.bgW = bgW;
        this.bgH = bgH;
    }

    public static Theme get() {
        return current;
    }

    public int color(String key) {
        Integer c = colors.get(key);
        if (c != null) {
            return c;
        }
        return DEFAULT_COLORS.getOrDefault(key, 0xFFFFFFFF);
    }

    public boolean hasSprite(String name) {
        return texture != null && sprites.containsKey(name);
    }

    /** Draws the named sprite stretched to w x h. Returns false (drew nothing) if the theme lacks it. */
    public boolean draw(GuiGraphics g, String name, int x, int y, int w, int h) {
        Sprite s = sprites.get(name);
        if (texture == null || s == null || w <= 0 || h <= 0) {
            return false;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int b = Math.min(s.border(), Math.min(s.w(), s.h()) / 2);
        if (b <= 0 || w < 2 * b || h < 2 * b) {
            blit(g, x, y, w, h, s.u(), s.v(), s.w(), s.h());
            return true;
        }
        int mw = s.w() - 2 * b; // source middle width
        int mh = s.h() - 2 * b;
        // corners
        blit(g, x, y, b, b, s.u(), s.v(), b, b);
        blit(g, x + w - b, y, b, b, s.u() + s.w() - b, s.v(), b, b);
        blit(g, x, y + h - b, b, b, s.u(), s.v() + s.h() - b, b, b);
        blit(g, x + w - b, y + h - b, b, b, s.u() + s.w() - b, s.v() + s.h() - b, b, b);
        // edges
        blit(g, x + b, y, w - 2 * b, b, s.u() + b, s.v(), mw, b);
        blit(g, x + b, y + h - b, w - 2 * b, b, s.u() + b, s.v() + s.h() - b, mw, b);
        blit(g, x, y + b, b, h - 2 * b, s.u(), s.v() + b, b, mh);
        blit(g, x + w - b, y + b, b, h - 2 * b, s.u() + s.w() - b, s.v() + b, b, mh);
        // centre
        blit(g, x + b, y + b, w - 2 * b, h - 2 * b, s.u() + b, s.v() + b, mw, mh);
        return true;
    }

    private void blit(GuiGraphics g, int dx, int dy, int dw, int dh, int u, int v, int uw, int vh) {
        if (dw > 0 && dh > 0 && uw > 0 && vh > 0) {
            g.blit(texture, dx, dy, dw, dh, (float) u, (float) v, uw, vh, texW, texH);
        }
    }

    // ---- lines ----

    private @Nullable Sprite sprite(String name) {
        return texture == null ? null : sprites.get(name);
    }

    /** Horizontal connector centred on y = cy. Uses the "line_h" sprite (tiled along its length) or a flat 1px line. */
    public void lineH(GuiGraphics g, int x1, int x2, int cy) {
        int a = Math.min(x1, x2);
        int b = Math.max(x1, x2);
        Sprite s = sprite("line_h");
        if (s == null) {
            g.fill(a, cy, b + 1, cy + 1, color("line"));
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int y = cy - s.h() / 2;
        for (int off = 0; off <= b - a; off += s.w()) {
            int len = Math.min(s.w(), b - a + 1 - off);
            blit(g, a + off, y, len, s.h(), s.u(), s.v(), len, s.h());
        }
    }

    /** Vertical connector centred on x = cx. Uses the "line_v" sprite (tiled along its length) or a flat 1px line. */
    public void lineV(GuiGraphics g, int cx, int y1, int y2) {
        int a = Math.min(y1, y2);
        int b = Math.max(y1, y2);
        Sprite s = sprite("line_v");
        if (s == null) {
            g.fill(cx, a, cx + 1, b + 1, color("line"));
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int x = cx - s.w() / 2;
        for (int off = 0; off <= b - a; off += s.h()) {
            int len = Math.min(s.h(), b - a + 1 - off);
            blit(g, x, a + off, s.w(), len, s.u(), s.v(), s.w(), len);
        }
    }

    /** Optional dot drawn where connectors meet ("line_joint"). Does nothing if the theme has no such sprite. */
    public void joint(GuiGraphics g, int cx, int cy) {
        Sprite s = sprite("line_joint");
        if (s == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(g, cx - s.w() / 2, cy - s.h() / 2, s.w(), s.h(), s.u(), s.v(), s.w(), s.h());
    }

    // ---- background ----

    /**
     * Full-screen wallpaper if the pack provides textures/gui/background.png (scaled to cover the screen,
     * centred, extra cropped), then the optional "background_tint" colour. With neither, draws nothing at all,
     * so the screen is fully transparent.
     */
    public void drawBackground(GuiGraphics g, int sw, int sh) {
        if (background != null && bgW > 0 && bgH > 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float screenAspect = (float) sw / sh;
            float imageAspect = (float) bgW / bgH;
            int uw;
            int vh;
            int u;
            int v;
            if (imageAspect > screenAspect) {      // image is wider: crop left/right
                vh = bgH;
                uw = Math.round(bgH * screenAspect);
                u = (bgW - uw) / 2;
                v = 0;
            } else {                                // image is taller: crop top/bottom
                uw = bgW;
                vh = Math.round(bgW / screenAspect);
                u = 0;
                v = (bgH - vh) / 2;
            }
            g.blit(background, 0, 0, sw, sh, (float) u, (float) v, uw, vh, bgW, bgH);
        }
        int tint = color("background_tint");
        if ((tint >>> 24) != 0) {
            g.fill(0, 0, sw, sh, tint);
        }
    }

    // ---- loading ----

    /** Called on every resource reload (F3+T, pack changes). */
    public static void load(ResourceManager manager) {
        try {
            Optional<Resource> res = manager.getResource(THEME_JSON);
            if (res.isEmpty()) {
                // No theme.json at all: still honour a bare background.png if a pack added one.
                ResourceLocation bg = DEFAULT_BACKGROUND;
                int[] dims = readSize(manager, bg);
                current = dims == null ? new Theme(null, 0, 0, Map.of(), Map.of(), null, 0, 0)
                        : new Theme(null, 0, 0, Map.of(), Map.of(), bg, dims[0], dims[1]);
                return;
            }
            try (BufferedReader reader = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                ResourceLocation texture = null;
                int texW = 0;
                int texH = 0;
                if (root.has("texture")) {
                    texture = ResourceLocation.tryParse(root.get("texture").getAsString());
                    if (root.has("texture_size")) {
                        var size = root.getAsJsonArray("texture_size");
                        texW = size.get(0).getAsInt();
                        texH = size.get(1).getAsInt();
                    }
                    if (texture == null || texW <= 0 || texH <= 0) {
                        LOGGER.warn("jeitree theme: invalid texture/texture_size, using flat drawing");
                        texture = null;
                    }
                }

                Map<String, Sprite> sprites = new HashMap<>();
                if (root.has("sprites")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("sprites").entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        sprites.put(e.getKey(), new Sprite(
                                o.get("u").getAsInt(), o.get("v").getAsInt(),
                                o.get("w").getAsInt(), o.get("h").getAsInt(),
                                o.has("border") ? o.get("border").getAsInt() : 0));
                    }
                }

                Map<String, Integer> colors = new HashMap<>();
                if (root.has("colors")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("colors").entrySet()) {
                        colors.put(e.getKey(), parseColor(e.getValue().getAsString()));
                    }
                }

                ResourceLocation bg = DEFAULT_BACKGROUND;
                if (root.has("background")) {
                    ResourceLocation custom = ResourceLocation.tryParse(root.get("background").getAsString());
                    if (custom != null) {
                        bg = custom;
                    }
                }
                int[] dims = readSize(manager, bg);

                current = new Theme(texture, texW, texH, Map.copyOf(sprites), Map.copyOf(colors),
                        dims == null ? null : bg, dims == null ? 0 : dims[0], dims == null ? 0 : dims[1]);
            }
        } catch (Exception e) {
            LOGGER.warn("jeitree theme failed to load, using built-in look", e);
            current = new Theme(null, 0, 0, Map.of(), Map.of(), null, 0, 0);
        }
    }

    /** Returns {width, height} of the PNG, or null if the resource does not exist / cannot be read. */
    private static int @Nullable [] readSize(ResourceManager manager, ResourceLocation id) {
        Optional<Resource> res = manager.getResource(id);
        if (res.isEmpty()) {
            return null;
        }
        try (InputStream in = res.get().open(); NativeImage image = NativeImage.read(in)) {
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            LOGGER.warn("jeitree theme: could not read background {}", id, e);
            return null;
        }
    }

    /** "#RRGGBB" or "#AARRGGBB" (the # is optional). */
    private static int parseColor(String s) {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        long value = Long.parseLong(hex, 16);
        if (hex.length() <= 6) {
            value |= 0xFF000000L;
        }
        return (int) value;
    }
}
