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
 *       pieces, with long silences in between like Minecraft's music. Every level has a piece of
 *       its own (and now and then the main theme instead, see sounds.json). Level 94 has none: its
 *       siren poles play their own tune. Level 39 is meant to be quiet and huge, so its music
 *       comes only every 10 to 25 minutes; Level 7's sea is loud and Level 490 is meant to be
 *       near-silent (every creak of the chains should stand out), so both every 5 to 12 minutes.
 *       Level 227 has its own piece ("megaflora"), every 4 to 10 minutes.</li>
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
    /*
     * One Music per level: which sound event, then the shortest and longest silence before the
     * next piece (in ticks: 3600 = 3 minutes, 9600 = 8 minutes). "true" = it may cut off whatever
     * is playing when you arrive (for example vanilla music when you noclip in).
     */
    private static final Music LEVEL_0 = new Music(ModSounds.MUSIC_LEVEL0, 3600, 9600, true);
    private static final Music LEVEL_1 = new Music(ModSounds.MUSIC_LEVEL1, 3600, 9600, true);
    private static final Music LEVEL_2 = new Music(ModSounds.MUSIC_LEVEL2, 3600, 9600, true);
    private static final Music LEVEL_4 = new Music(ModSounds.MUSIC_LEVEL4, 3600, 9600, true);
    private static final Music POOLROOMS = new Music(ModSounds.MUSIC_POOLROOMS, 3600, 9600, true);
    private static final Music LEVEL_37 = new Music(ModSounds.MUSIC_LEVEL37, 3600, 9600, true);
    /** Level 7: the sea is loud, so music only every 5 to 12 minutes. */
    private static final Music LEVEL_7 = new Music(ModSounds.MUSIC_LEVEL7, 6000, 14000, true);
    /** Level 39: very rare (10 to 25 minutes); the echoing silence of the town is the soundtrack. */
    private static final Music LEVEL_39 = new Music(ModSounds.MUSIC_LEVEL39, 12000, 30000, true);
    /** Level 490: near silence is part of the place (the chains, the rain), so only every 5 to 12 minutes. */
    private static final Music LEVEL_490 = new Music(ModSounds.MUSIC_LEVEL490, 6000, 14000, true);
    /** Level 369: a little more often than most (every 2.5 to 7 minutes): it wants you to stay. */
    private static final Music LEVEL_369 = new Music(ModSounds.MUSIC_LEVEL369, 3000, 8400, true);
    private static final Music LEVEL_9 = new Music(ModSounds.MUSIC_LEVEL9, 3600, 9600, true);
    /**
     * Level 227: its own piece (wonder turning into dread), every 4 to 10 minutes; the forest and
     * the tree's groans carry the silences.
     */
    private static final Music MEGAFLORA = new Music(com.rickr.backrooms.registry.Level227Atmos.MUSIC_MEGAFLORA, 4800, 12000, true);

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
                case LEVEL_0 -> LEVEL_0;
                case LEVEL_1 -> LEVEL_1;
                case LEVEL_2 -> LEVEL_2;
                case LEVEL_4 -> LEVEL_4;
                case POOLROOMS -> POOLROOMS;
                case LEVEL_37 -> LEVEL_37;
                case LEVEL_7 -> LEVEL_7;
                case LEVEL_39 -> LEVEL_39;
                case LEVEL_94 -> null; // its siren poles play their own tune
                case LEVEL_490 -> LEVEL_490;
                case LEVEL_369 -> LEVEL_369;
                case LEVEL_9 -> LEVEL_9;
                case LEVEL_227 -> MEGAFLORA;
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
