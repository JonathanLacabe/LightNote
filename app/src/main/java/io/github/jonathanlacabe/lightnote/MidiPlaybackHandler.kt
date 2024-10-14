package io.github.jonathanlacabe.lightnote

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import org.billthefarmer.mididriver.MidiDriver
import java.io.InputStream
import jp.kshoji.javax.sound.midi.MidiSystem
import jp.kshoji.javax.sound.midi.Sequence
import jp.kshoji.javax.sound.midi.MetaMessage

class MidiPlaybackHandler(private val contentResolver: ContentResolver) {

    private val midiDriver: MidiDriver = MidiDriver.getInstance()
    private var isPlaying = false
    private var playbackThread: Thread? = null
    private var currentTick = 0L // Tracks the current playback position in ticks
    private var resumeTick = 0L // Tracks the tick to resume from after a pause

    fun loadAndPlayMidiFile(uri: Uri, startTick: Long = 0L) {
        stopPlayback() // Ensure any ongoing playback is stopped
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream != null) {
            midiDriver.start()
            playMidiData(inputStream, startTick)
        } else {
            Log.e("MIDI_PLAYER", "Failed to load MIDI file from URI")
        }
    }

    private fun playMidiData(inputStream: InputStream, startTick: Long) {
        playbackThread = Thread {
            try {
                val sequence: Sequence = MidiSystem.getSequence(inputStream)
                isPlaying = true
                var tempo = 500000 // Default tempo in microseconds per quarter note (120 BPM)
                val ticksPerQuarterNote = sequence.resolution.toDouble()

                for (track in sequence.tracks) {
                    var lastEventTick = 0L
                    Log.d("MIDI_PLAYER", "Starting playback from tick: $startTick")

                    for (i in 0 until track.size()) {
                        if (!isPlaying) break

                        val event = track.get(i)

                        // Skip events before startTick to ensure accurate playback position
                        if (event.tick < startTick) continue

                        // Calculate tick difference only for events at or after startTick
                        val tickDelta = event.tick - lastEventTick
                        lastEventTick = event.tick
                        currentTick = event.tick // Update the current playback position

                        // Process tempo changes in MetaMessage type 0x51
                        if (event.message is MetaMessage) {
                            val metaMessage = event.message as MetaMessage
                            if (metaMessage.type == 0x51 && metaMessage.data.size >= 3) {
                                tempo = ((metaMessage.data[0].toInt() and 0xFF) shl 16) or
                                        ((metaMessage.data[1].toInt() and 0xFF) shl 8) or
                                        (metaMessage.data[2].toInt() and 0xFF)
                                Log.d("MIDI_PLAYER", "Tempo change detected: $tempo microseconds per quarter note")
                            }
                        }

                        // Calculate delay based on tick difference, adjusted for tempo
                        val microsecondsPerTick = tempo / ticksPerQuarterNote
                        val delayMillis = if (resumeTick == event.tick) 0L else (tickDelta * microsecondsPerTick / 1000).toLong()

                        // Avoid additional delay when resuming playback
                        if (isPlaying && delayMillis > 0) {
                            Thread.sleep(delayMillis)
                        }

                        // Send the MIDI event to MidiDriver
                        val message = event.message.message
                        midiDriver.write(message)
                        Log.d("MIDI_PLAYER", "Event sent at tick $currentTick")
                    }
                }
            } catch (e: Exception) {
                Log.e("MIDI_PLAYER", "Error during playback: ${e.message}")
            } finally {
                midiDriver.stop()
                isPlaying = false
                Log.d("MIDI_PLAYER", "Playback stopped.")
            }
        }
        playbackThread?.start()
    }

    fun pausePlayback() {
        if (isPlaying) {
            isPlaying = false
            resumeTick = currentTick // Store current tick for resuming playback
            midiDriver.stop()
            playbackThread?.interrupt() // Stop playback thread
            Log.d("MIDI_PLAYER", "Playback paused at tick $resumeTick")
        }
    }

    fun resumePlayback(uri: Uri) {
        if (!isPlaying) {
            Log.d("MIDI_PLAYER", "Resuming playback from tick $resumeTick")
            loadAndPlayMidiFile(uri, resumeTick) // Resume from stored tick
        }
    }

    fun stopPlayback() {
        if (isPlaying) {
            isPlaying = false
            currentTick = 0L // Reset current playback position
            resumeTick = 0L // Reset resume point
            midiDriver.stop()
            playbackThread?.interrupt() // Interrupt playback thread if running
            Log.d("MIDI_PLAYER", "Playback fully stopped and reset.")
        }
    }
}
