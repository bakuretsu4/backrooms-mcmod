package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Chooses what plays on Minecraft's music channel (so the game's Music volume slider controls it).
 *
 * <ul>
 *   <li><b>Title menu</b> (and every screen opened from it): our main theme, over and over.</li>
 *   <li><b>In the Backrooms:</b> instead of the vanilla game music, now and then one of our own
 *       pieces, with long silences in between like Minecraft's music: the theme or the dark
 *       ambient piece in most levels, the dreamy piece (or the theme) in the Poolrooms and
 *       Level 37. Level 94 has none: its siren poles play their own tune. Level 39 is meant to be
 *       quiet and huge: only the dark piece, and only every 10 to 25 minutes.</li>
 *   <li><b>Real world:</b> vanilla music as normal.</li>
 * </ul>
 *
 * <p>Minecraft asks for the "situational music" every tick and fires {@link SelectMusicEvent};
 * we answer with a {@link Music}: which sound event to play, the shortest and longest wait (in
 * ticks, 20 per second) before the next piece, and whether it may cut off music that's already
 * playing.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class BackroomsMusic {
    /** The theme loops on the menu with only a short breath between plays. */
    private static final Music MENU = new Music(ModSounds.MUSIC_MENU, 40, 200, true);
    /**
     * In the levels: 3 to 8 minutes of silence between pieces, so the music is an event and the
     * level's own sounds carry the rest. Cuts off vanilla music when you noclip in.
     */
    private static final Music LEVELS = new Music(ModSounds.MUSIC_BACKROOMS, 3600, 9600, true);
    private static final Music DREAM = new Music(ModSounds.MUSIC_DREAM, 3600, 9600, true);
    /** Level 39: very rare; the echoing silence of the town is the soundtrack. */
    private static final Music RARE = new Music(ModSounds.MUSIC_BACKROOMS, 12000, 30000, true);

    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (event.getOriginalMusic() == Musics.MENU) {
                event.setMusic(MENU);
            }
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(mc.level.dimension());
        if (floor != null) {
            // null = no music at all (anything playing stops).
            event.setMusic(switch (floor) {
                // Penance: near silence, so every creak of the chains stands out.
                case LEVEL_94, LEVEL_7, LEVEL_490 -> null;
                case POOLROOMS, LEVEL_37 -> DREAM;
                case LEVEL_39 -> RARE;
                default -> LEVELS;
            });
            return;
        }
        // Real world: vanilla music, but it may cut off the menu theme still playing from the menu
        // (vanilla game music normally waits for the current piece to end).
        Music music = event.getMusic();
        SoundInstance playing = event.getPlayingMusic();
        if (music != null && !music.replaceCurrentMusic() && playing != null
                && playing.getLocation().equals(ModSounds.MUSIC_MENU.getId())) {
            event.setMusic(new Music(music.getEvent(), music.getMinDelay(), music.getMaxDelay(), true));
        }
    }

    private BackroomsMusic() {}
}
