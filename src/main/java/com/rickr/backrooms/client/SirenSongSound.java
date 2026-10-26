package com.rickr.backrooms.client;

import com.rickr.backrooms.registry.ModSounds;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/**
 * The cartoon tune coming out of one Level 94 siren: a looping sound placed at the horn. Like
 * {@code client/light/FixtureHumSound} it fades towards a target volume every tick and stops
 * itself once released and silent. {@link SirenSongs} decides which sirens sing and how loud.
 *
 * <p>All sirens play the same broadcast, in step: a new siren doesn't start the song from the
 * top, it joins in where the town's song is right now ({@link SirenSongs#songPosition}). Two
 * horns playing the same tune half a bar apart would sound like a mess. To do that the song is
 * streamed ("stream": true in sounds.json) and we skip the start of the stream before it plays.
 */
final class SirenSongSound extends AbstractTickableSoundInstance {
    /** Full volume (at 1 the song is a bit too much next to the quiet hills). */
    static final float MAX_VOLUME = 0.8f;

    final BlockPos pos;
    /** 0..1 how loud we want to be (the clock's music volume). */
    float target;
    private boolean released;

    SirenSongSound(BlockPos pos, float target) {
        super(ModSounds.L94_SIREN_SONG.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.pos = pos;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        // Fades out over the sound's attenuation distance (64 blocks, see sounds.json).
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.target = target;
        this.volume = MAX_VOLUME * target;
    }

    /** Fade out and stop. */
    void release() {
        released = true;
    }

    /** True while the dusk falters drop the music to nothing for a moment: keep the channel. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        float goal = released ? 0.0f : MAX_VOLUME * target;
        // About 3 ticks to settle: dusk dropouts cut the music quickly, but never with a click.
        volume += (goal - volume) * 0.45f;
        if (released && volume < 0.005f) {
            stop();
        }
    }

    /**
     * Opens the song stream and, on the background loader thread, skips ahead to where the town's
     * song is at this moment, so this siren sings in step with the others.
     */
    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return soundBuffers.getStream(sound.getPath(), looping).thenApplyAsync(stream -> {
            try {
                AudioFormat format = stream.getFormat();
                long frames = (long) (SirenSongs.songPosition() * format.getSampleRate());
                return new SkippedStream(stream, frames * format.getFrameSize());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Util.nonCriticalIoPool());
    }

    /**
     * An audio stream whose first {@code skipBytes} bytes are thrown away. The decoder hands out
     * whole chunks, so the last skipped chunk is usually only partly used: its remainder is played
     * first.
     */
    private static final class SkippedStream implements AudioStream {
        private final AudioStream inner;
        private ByteBuffer leftover;

        SkippedStream(AudioStream inner, long skipBytes) throws IOException {
            this.inner = inner;
            while (skipBytes > 0) {
                ByteBuffer chunk = inner.read((int) Math.min(skipBytes, 1 << 16));
                if (!chunk.hasRemaining()) {
                    break; // the stream ended (only possible if it doesn't loop)
                }
                if (chunk.remaining() > skipBytes) {
                    chunk.position(chunk.position() + (int) skipBytes);
                    leftover = chunk;
                    break;
                }
                skipBytes -= chunk.remaining();
            }
        }

        @Override
        public AudioFormat getFormat() {
            return inner.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            if (leftover != null) {
                ByteBuffer rest = leftover;
                leftover = null;
                return rest;
            }
            return inner.read(size);
        }

        @Override
        public void close() throws IOException {
            inner.close();
        }
    }
}
