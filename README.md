# JEI Recipe Trees (NeoForge 1.21.1)

EMI-style recipe trees for JEI. Client-side only; needs JEI 19.x.

## Use
Hover an item in the JEI list, the JEI recipe screen, or your inventory and press **T** (rebindable).

In the tree screen:
- Left-click a node: next recipe for that item (last step = "base ingredient")
- Right-click: previous recipe, Shift+click: base ingredient
- Press T over a node: re-root the tree on that item
- Drag / mouse wheel (Shift = horizontal) to pan, wheel over the right panel to scroll it
- `-` / `+` change the amount (Shift = 16, Ctrl = 64)
- **Inventory: On/Off** button: items you already carry are subtracted from the plan.
  Covered nodes get a check mark and are dimmed; the side panel shows `have/need` for raw materials.
  (Read-only: it only looks at your main inventory, never changes it, nothing is sent anywhere.)

## Themes (resource packs)
The look is controlled by a resource pack. Override these two files:

    assets/jeitree/theme.json                 colours + where each sprite sits in the atlas
    assets/jeitree/textures/gui/theme.png     the texture atlas (default 128x128)

`theme.json`:

    {
      "texture": "jeitree:textures/gui/theme.png",
      "texture_size": [128, 128],
      "sprites": { "node_raw": {"u":0,"v":0,"w":22,"h":22}, ... },
      "colors":  { "line": "#FFD4FF6E", "text": "#E8FFC0", ... }
    }

Sprites (all optional; a missing one falls back to flat drawing in the matching colour):
- `node_raw`, `node_crafted`, `node_hover`, `node_done` : 22x22 frames drawn around each 18px slot
- `panel` : right-hand panel background. Add `"border": N` to make it a stretchable nine-slice
- `icon_check` : small mark on nodes fully covered by your inventory (drawn 9x9)
- `line_h` / `line_v` : the connector lines. Each is tiled along its length, so a 4x3 `line_h` becomes a 3px-thick
  line (a dashed or patterned strip works too). The thickness is simply the sprite's height (`line_h`) or width (`line_v`)
- `line_joint` : optional dot drawn where connectors meet, centred on the junction

Colours (`#RRGGBB` or `#AARRGGBB`): `line panel text node_bg raw crafted hover done dim
have partial missing header_raw header_steps background_tint`.
(`line` is only used when the theme has no `line_h` / `line_v` sprite.)

### Background
Drop a wallpaper at `assets/jeitree/textures/gui/background.png` (any size, not tiled). It is scaled to cover the
whole screen, centred, with any overflow cropped. It replaces the vanilla blurred/dimmed menu background.
- No `background.png` in any pack: the screen is fully transparent (you see the game behind it, no blur or dim).
- `background_tint` (default `#00000000`, i.e. none) is drawn over the wallpaper, or over the bare game view
  if there is no wallpaper, handy for darkening a bright image: e.g. `"#40000000"`.
- Want smooth scaling instead of pixelated? Add `background.png.mcmeta` next to it containing
  `{"texture": {"blur": true}}`.
- To use another file name, set `"background": "mynamespace:textures/gui/other.png"` in `theme.json`.

Use a different atlas size or file name by changing `texture` / `texture_size`.
Press F3+T in game to reload after editing a pack.

`jeitree-lime-theme.zip` is a ready-made example pack (lime on black, glowing 3px lines, wallpaper); copy it into `resourcepacks/`.

## Build
This repo has no Gradle wrapper. Copy `gradlew`, `gradlew.bat` and `gradle/` from the
NeoForge 1.21.1 MDK (github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle), then:

    gradlew.bat build        # jar in build/libs
    gradlew.bat runClient    # dev client with JEI

## Limits
Items only (fluids ignored), tag ingredients use the first alternative. Inventory matching is by item
(plain items match any stack of that item; potions/enchanted books and other component items match exactly).
Armor, offhand, ender chests and nearby containers are not counted.
