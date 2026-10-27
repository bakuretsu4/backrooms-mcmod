package com.rickr.backrooms.client.anomaly.level39;

import com.rickr.backrooms.Backrooms;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * One page of an abandoned M.E.G. survey camp's notes (Level 39), shown on its water-stained
 * clipboard so it reads like a found document instead of a chat line. Click or press Escape to
 * put it down. The texts are {@code message.backrooms.l39_survey_note.N} in the language file.
 */
public class SurveyNoteScreen extends Screen {
    private static final ResourceLocation PAGE = Backrooms.id("textures/gui/l39_survey_note.png");
    private static final int SIZE = 256;
    /** The writable part of the sheet in the texture (below the typed letterhead). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 76;
    private static final int TEXT_W = 176;
    private static final int TEXT_BOTTOM = 238;
    private static final int LINE = 10;
    /** Faded typewriter ink. */
    private static final int INK = 0xFF2C303A;

    private final int note;

    public SurveyNoteScreen(int note) {
        super(Component.translatable("block.backrooms.l39_survey_note"));
        this.note = note;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = (width - SIZE) / 2;
        int y = Math.max(0, (height - SIZE) / 2);
        graphics.blit(PAGE, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        List<FormattedCharSequence> lines = font.split(Component.translatable("message.backrooms.l39_survey_note." + note), TEXT_W);
        int ty = y + TEXT_Y;
        for (FormattedCharSequence line : lines) {
            if (ty + LINE > y + TEXT_BOTTOM) {
                break;
            }
            graphics.drawString(font, line, x + TEXT_X, ty, INK, false);
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
