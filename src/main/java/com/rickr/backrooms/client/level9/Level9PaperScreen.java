package com.rickr.backrooms.client.level9;

import com.rickr.backrooms.Backrooms;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * A Level 9 paper seen close up: a missing-person poster, a Neighborhood Watch flyer (or a
 * Watch notice board, which is a printed copy of one) or a child's drawing. It works like the
 * Level 39 survey notes ({@code client/anomaly/level39/SurveyNoteScreen}): the sheet is a 256 x
 * 256 GUI texture drawn in the middle of the screen with the words on top. Click or press Escape
 * to put it down. The game keeps running behind it.
 *
 * <p>The words are wrapped into lines once, when the screen opens, not every frame.
 */
public class Level9PaperScreen extends Screen {
    private static final int SIZE = 256;

    // Poster sheet (textures/gui/l9_missing_poster.png): "MISSING" is printed at the top.
    private static final ResourceLocation POSTER = Backrooms.id("textures/gui/l9_missing_poster.png");
    /** Photo atlas: 4 x 2 photos of 64 x 64, then the photocopy grain (row 2, column 0). */
    private static final ResourceLocation PHOTOS = Backrooms.id("textures/gui/l9_poster_photos.png");
    private static final int PHOTO_ATLAS_W = 256;
    private static final int PHOTO_ATLAS_H = 192;
    private static final int PHOTO_CELL = 64;
    /** The photo's frame in the sheet (the sheet itself is x 34-222, "MISSING" at the top). */
    private static final int PHOTO_X = 92;
    private static final int PHOTO_Y = 52;
    private static final int PHOTO_SIZE = 72;
    private static final int POSTER_NAME_Y = 131;
    private static final int POSTER_TEXT_X = 44;
    private static final int POSTER_TEXT_Y = 145;
    private static final int POSTER_TEXT_W = 168;
    private static final int POSTER_TEXT_BOTTOM = 247;
    /** Photocopier toner: nearly black, a little warm. */
    private static final int TONER = 0xFF1C1A18;

    // Flyer sheet (textures/gui/l9_watch_flyer.png): the eye and NEIGHBORHOOD WATCH header and the footer are printed.
    private static final ResourceLocation FLYER = Backrooms.id("textures/gui/l9_watch_flyer.png");
    private static final int FLYER_TITLE_Y = 70;
    private static final int FLYER_TEXT_X = 48;
    private static final int FLYER_TEXT_Y = 88;
    private static final int FLYER_TEXT_W = 160;
    private static final int FLYER_TEXT_BOTTOM = 220;
    /** The title is printed in the Watch's red. */
    private static final int WATCH_RED = 0xFF8E1B1B;

    private static final int LINE = 10;

    private enum Kind { POSTER, FLYER, DRAWING }

    private final Kind kind;
    /** Poster: the photo number (-1 = the player's own face). Drawing: the drawing number. */
    private final int index;
    private final Component heading;
    private final Component body;
    /** The drawing's picture (drawings only). */
    @javax.annotation.Nullable
    private final ResourceLocation drawingTexture;
    private List<FormattedCharSequence> lines = List.of();

    private Level9PaperScreen(Kind kind, int index, Component heading, Component body,
                              @javax.annotation.Nullable ResourceLocation drawingTexture) {
        super(heading);
        this.kind = kind;
        this.index = index;
        this.heading = heading;
        this.body = body;
        this.drawingTexture = drawingTexture;
    }

    /** Missing-person poster {@code person}; {@code ownName} = the player's own name and face are on it. */
    public static Level9PaperScreen poster(int person, boolean ownName) {
        if (ownName) {
            Minecraft mc = Minecraft.getInstance();
            String name = mc.player != null ? mc.player.getGameProfile().getName() : "you";
            return new Level9PaperScreen(Kind.POSTER, -1, Component.literal(name.toUpperCase(java.util.Locale.ROOT)),
                    Component.translatable("poster.backrooms.l9.self.text"), null);
        }
        return new Level9PaperScreen(Kind.POSTER, person, Component.translatable("poster.backrooms.l9." + person + ".name"),
                Component.translatable("poster.backrooms.l9." + person + ".text"), null);
    }

    /** A flyer or notice text: {@code key}.title and {@code key}.text in the language file. */
    public static Level9PaperScreen flyer(String key) {
        return new Level9PaperScreen(Kind.FLYER, 0, Component.translatable(key + ".title"), Component.translatable(key + ".text"), null);
    }

    /** Child's drawing {@code drawing} (the picture and its crayon words are all in the texture). */
    public static Level9PaperScreen drawing(int drawing) {
        return new Level9PaperScreen(Kind.DRAWING, drawing, Component.translatable("block.backrooms.kids_drawing"), Component.empty(),
                Backrooms.id("textures/gui/l9_kids_drawing_" + drawing + ".png"));
    }

    @Override
    protected void init() {
        super.init();
        int wrap = kind == Kind.POSTER ? POSTER_TEXT_W : FLYER_TEXT_W;
        lines = kind == Kind.DRAWING ? List.of() : font.split(body, wrap);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = (width - SIZE) / 2;
        int y = Math.max(0, (height - SIZE) / 2);
        switch (kind) {
            case POSTER -> renderPoster(graphics, x, y);
            case FLYER -> renderFlyer(graphics, x, y);
            case DRAWING -> graphics.blit(drawingTexture, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        }
    }

    private void renderPoster(GuiGraphics graphics, int x, int y) {
        graphics.blit(POSTER, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        int px = x + PHOTO_X;
        int py = y + PHOTO_Y;
        if (index < 0 && minecraft != null && minecraft.player != null) {
            // Your own face, photocopied: grey and a little dark.
            graphics.setColor(0.62f, 0.62f, 0.6f, 1.0f);
            PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin(), px, py, PHOTO_SIZE);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            int cell = Math.max(0, index);
            graphics.blit(PHOTOS, px, py, PHOTO_SIZE, PHOTO_SIZE, (cell % 4) * PHOTO_CELL, (cell / 4) * PHOTO_CELL,
                    PHOTO_CELL, PHOTO_CELL, PHOTO_ATLAS_W, PHOTO_ATLAS_H);
        }
        // The photocopier's grain and smudges over the photo. The grain is mostly see-through, and
        // GUI drawing has blending off by default, so turn it on or the faint pixels come out black.
        RenderSystem.enableBlend();
        graphics.blit(PHOTOS, px, py, PHOTO_SIZE, PHOTO_SIZE, 0, 2 * PHOTO_CELL, PHOTO_CELL, PHOTO_CELL, PHOTO_ATLAS_W, PHOTO_ATLAS_H);
        RenderSystem.disableBlend();
        graphics.drawString(font, heading, x + (SIZE - font.width(heading)) / 2, y + POSTER_NAME_Y, TONER, false);
        drawLines(graphics, x + POSTER_TEXT_X, y + POSTER_TEXT_Y, y + POSTER_TEXT_BOTTOM, TONER);
    }

    private void renderFlyer(GuiGraphics graphics, int x, int y) {
        graphics.blit(FLYER, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        graphics.drawString(font, heading, x + (SIZE - font.width(heading)) / 2, y + FLYER_TITLE_Y, WATCH_RED, false);
        drawLines(graphics, x + FLYER_TEXT_X, y + FLYER_TEXT_Y, y + FLYER_TEXT_BOTTOM, TONER);
    }

    private void drawLines(GuiGraphics graphics, int x, int top, int bottom, int colour) {
        int ty = top;
        for (FormattedCharSequence line : lines) {
            if (ty + LINE > bottom) {
                break;
            }
            graphics.drawString(font, line, x, ty, colour, false);
            ty += LINE;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
