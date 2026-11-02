package com.rickr.backrooms.client.menu;

import com.rickr.backrooms.Backrooms;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Gives Minecraft's own Options screen and pause menu the look of our menus (after the play-test:
 * "just change the look"): a camcorder's on-screen menu. Their buttons stay vanilla's (so they
 * work exactly as before); only how Minecraft's OWN buttons are drawn changes:
 * <ul>
 *   <li>behind them, in a world: the game, with scanlines and a blinking "PAUSE" in the corner;
 *       from the title screen: our footage background ({@link FootageBackground});</li>
 *   <li>the title in the VHS font;</li>
 *   <li>vanilla buttons as menu rows: a faint frame, the label in light grey, and when the mouse is
 *       on one (or it has keyboard focus) the dark bar, yellow edge and play-arrow of {@link VhsButton};</li>
 *   <li>vanilla sliders as in our settings ({@link VhsSlider}): label, a thin track, a marker.</li>
 * </ul>
 *
 * <p>Other mods put their own buttons and panels on these screens (Essential, for example, draws
 * icons and its own fonts on its buttons). So we never take over the whole screen: Minecraft draws
 * it as usual, every other mod's widgets and overlays included, and we only swap the look of the
 * widgets we are sure are Minecraft's own (a vanilla class with a real vanilla label). How:
 * <ol>
 *   <li>Just before the screen draws, those vanilla widgets are hidden for this one frame.</li>
 *   <li>Right after Minecraft draws the screen's background, we draw our background touches and
 *       our versions of the hidden widgets (so everything else still draws on top, as normal).</li>
 *   <li>Right after the screen has drawn, the widgets are shown again (so clicks and keys work).</li>
 * </ol>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class VanillaRestyle {
    private static final float TEXT = 1.0f;
    /** Was each button lit last frame (to blip only as it lights up)? */
    private static final Map<AbstractWidget, Boolean> LIT = new WeakHashMap<>();
    /** The vanilla widgets hidden for the frame being drawn (shown again after it). Reused every frame. */
    private static final List<AbstractWidget> HIDDEN = new ArrayList<>();
    /** The mouse position for this frame (the background event doesn't carry it). */
    private static int mouseX;
    private static int mouseY;
    private static Field sliderValue;

    private VanillaRestyle() {}

    private static boolean restyled(Screen screen) {
        return screen instanceof PauseScreen pause && pause.showsPauseMenu() || screen != null && screen.getClass() == OptionsScreen.class;
    }

    /**
     * Is this one of Minecraft's own widgets, safe to redraw in our style? Its class must be
     * Minecraft's (not another mod's subclass, which may draw icons or special text), and its label
     * a real translated line from the game's language files (other mods sometimes put placeholder
     * text on a vanilla button and draw the real look themselves). Anything else is left alone.
     */
    private static boolean vanilla(AbstractWidget w) {
        if (!w.getClass().getName().startsWith("net.minecraft.")) {
            return false;
        }
        return w.getMessage().getContents() instanceof TranslatableContents t && Language.getInstance().has(t.getKey());
    }

    /** Before the screen draws: hide the vanilla widgets we will draw ourselves (and the title). */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        // Normally the last frame already showed them again; this is only a safety net so a vanilla
        // button can never stay invisible.
        for (AbstractWidget w : HIDDEN) {
            w.visible = true;
        }
        HIDDEN.clear();
        Screen screen = event.getScreen();
        if (!restyled(screen)) {
            return;
        }
        mouseX = event.getMouseX();
        mouseY = event.getMouseY();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget w) || !w.visible) {
                continue;
            }
            boolean title = w instanceof StringWidget s && s.getMessage().getString().equals(screen.getTitle().getString());
            boolean ours = w instanceof AbstractSliderButton || w instanceof AbstractButton && w.getWidth() > 24;
            if (title || ours && vanilla(w)) {
                w.visible = false;
                HIDDEN.add(w);
            }
        }
    }

    /** Just after Minecraft drew the screen's background: our touches, then our versions of the hidden widgets. */
    @SubscribeEvent
    public static void onBackground(ScreenEvent.BackgroundRendered event) {
        Screen screen = event.getScreen();
        if (!restyled(screen)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        background(g, screen, mc);
        // The title, at the top in the middle.
        Component title = VhsText.literal(screen.getTitle().getString().toUpperCase(java.util.Locale.ROOT));
        float scale = 1.5f;
        int tw = VhsText.width(mc.font, title, scale);
        VhsText.draw(g, mc.font, title, (screen.width - tw) / 2.0f, 14, scale, VhsText.WHITE, true);
        for (AbstractWidget w : HIDDEN) {
            if (w instanceof AbstractSliderButton slider) {
                slider(g, slider, mouseX, mouseY, mc);
            } else if (w instanceof AbstractButton button) {
                button(g, button, mouseX, mouseY, mc);
            }
        }
    }

    /** After the screen drew: show the hidden widgets again, so they take clicks and keys as normal. */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        for (AbstractWidget w : HIDDEN) {
            w.visible = true;
        }
        HIDDEN.clear();
    }

    /** In a world: the game behind (Minecraft already dimmed it), scanlines, PAUSE blinking. Else the footage background. */
    private static void background(GuiGraphics g, Screen screen, Minecraft mc) {
        if (mc.level == null) {
            FootageBackground.render(g, screen.width, screen.height);
            g.fill(0, 0, screen.width, screen.height, 0x66000000);
            return;
        }
        for (int y = 0; y < screen.height; y += 3) {
            g.fill(0, y, screen.width, y + 1, 0x22000000);
        }
        // Viewfinder corners and a blinking PAUSE, like the camcorder's own pause.
        int m = 10;
        VhsText.brackets(g, m, m, screen.width - m, screen.height - m, 14, VhsText.withAlpha(0xF2F2E8, 0.55f));
        if ((System.currentTimeMillis() / 600) % 2 == 0) {
            int y = screen.height - m - 17; // bottom left: the pause menu's settings button is top left
            g.fill(m + 8, y, m + 11, y + 9, VhsText.WHITE);
            g.fill(m + 13, y, m + 16, y + 9, VhsText.WHITE);
            VhsText.draw(g, mc.font, VhsText.translatable("menu.backrooms.osd.pause"), m + 21, y + 1, 1.0f, VhsText.WHITE, true);
        }
    }

    private static boolean lit(AbstractWidget w, int mouseX, int mouseY) {
        boolean lit = w.active && (w.isMouseOver(mouseX, mouseY) || w.isFocused());
        Boolean was = LIT.put(w, lit);
        if (lit && was != null && !was) {
            VhsButton.blip(Minecraft.getInstance());
        }
        return lit;
    }

    private static void button(GuiGraphics g, AbstractButton b, int mouseX, int mouseY, Minecraft mc) {
        boolean lit = lit(b, mouseX, mouseY);
        int x = b.getX();
        int y = b.getY();
        int w = b.getWidth();
        int h = b.getHeight();
        if (lit) {
            VhsButton.drawHighlight(g, x, y, w, h, TEXT, 1.0f);
        } else {
            // A faint frame, so the rows read as buttons.
            int edge = VhsText.withAlpha(0xF2F2E8, b.active ? 0.16f : 0.07f);
            g.fill(x, y, x + w, y + 1, edge);
            g.fill(x, y + h - 1, x + w, y + h, edge);
            g.fill(x, y, x + 1, y + h, edge);
            g.fill(x + w - 1, y, x + w, y + h, edge);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x40000000);
        }
        Component label = VhsText.literal(b.getMessage().getString());
        int colour = !b.active ? VhsText.DISABLED : lit ? VhsText.YELLOW : VhsText.IDLE;
        int lw = VhsText.width(mc.font, label, TEXT);
        float tx = x + Math.max(VhsButton.textInset(TEXT), (w - lw) / 2.0f);
        float ty = y + (h - mc.font.lineHeight * TEXT) / 2.0f + 1;
        g.enableScissor(x + 1, y, x + w - 1, y + h);
        VhsText.draw(g, mc.font, label, tx, ty, TEXT, colour, true);
        g.disableScissor();
    }

    private static void slider(GuiGraphics g, AbstractSliderButton s, int mouseX, int mouseY, Minecraft mc) {
        boolean lit = lit(s, mouseX, mouseY);
        int x = s.getX();
        int y = s.getY();
        int w = s.getWidth();
        int h = s.getHeight();
        if (lit) {
            VhsButton.drawHighlight(g, x, y, w, h, TEXT, 1.0f);
        } else {
            g.fill(x, y, x + w, y + h, 0x40000000);
        }
        int colour = !s.active ? VhsText.DISABLED : lit ? VhsText.YELLOW : VhsText.IDLE;
        Component label = VhsText.literal(s.getMessage().getString());
        VhsText.draw(g, mc.font, label, x + VhsButton.textInset(TEXT), y + 3, TEXT, colour, true);
        // The track, filled to the value, with a marker (vanilla's mouse mapping is across the whole row).
        double value = value(s);
        int x0 = x + 4;
        int x1 = x + w - 4;
        int ty = y + h - 4;
        int fx = x0 + (int) Math.round(value * (x1 - x0));
        g.fill(x0, ty, x1, ty + 1, VhsText.withAlpha(0xF2F2E8, 0.2f));
        g.fill(x0, ty, fx, ty + 1, VhsText.withAlpha(lit ? VhsText.YELLOW : 0xF2F2E8, 0.7f));
        g.fill(fx - 1, ty - 2, fx + 1, ty + 3, colour);
    }

    /** A vanilla slider's value 0..1 (a protected field; read by reflection, the names are Mojang's at run time). */
    private static double value(AbstractSliderButton s) {
        try {
            if (sliderValue == null) {
                sliderValue = AbstractSliderButton.class.getDeclaredField("value");
                sliderValue.setAccessible(true);
            }
            return sliderValue.getDouble(s);
        } catch (ReflectiveOperationException e) {
            return 0.5;
        }
    }
}
