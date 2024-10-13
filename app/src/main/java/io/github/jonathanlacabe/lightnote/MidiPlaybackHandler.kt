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

    fun loadAndPlayMidiFile(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream != null) {
            midiDriver.start()
            playMidiData(inputStream)
        } else {
            Log.e("MIDI_PLAYER", "Failed to load MIDI file from URI")
        }
    }

    private fun playMidiData(inputStream: InputStream) {
        playbackThread = Thread {
            try {
                val sequence: Sequence = MidiSystem.getSequence(inputStream)
                isPlaying = true

                var tempo = 500000 // Default microseconds per quarter note for 120 BPM
                val ticksPerQuarterNote = sequence.resolution.toDouble()
                val microsecondsPerTickFactor = tempo / ticksPerQuarterNote

                for (track in sequence.tracks) {
                    var lastEventTick = 0L

                    for (i in 0 until track.size()) {
                        if (!isPlaying) break

                        val event = track.get(i)
                        val tickDelta = event.tick - lastEventTick
                        lastEventTick = event.tick

                        // Check for tempo change (Meta message with type 0x51)
                        if (event.message is MetaMessage) {
                            val metaMessage = event.message as MetaMessage
                            if (metaMessage.type == 0x51 && metaMessage.data.size >= 3) { // 0x51 indicates a tempo change event
                                tempo = ((metaMessage.data[0].toInt() and 0xFF) shl 16) or
                                        ((metaMessage.data[1].toInt() and 0xFF) shl 8) or
                                        (metaMessage.data[2].toInt() and 0xFF)
                                Log.d("MIDI_PLAYER", "Tempo change detected: $tempo microseconds per quarter note")
                            }
                        }

                        val message = event.message.message // MIDI event message bytes
                        midiDriver.write(message) // Send the event to MidiDriver
                        if (message != null) {
                            Log.d("MIDI_PLAYER", "MIDI event sent: ${message.joinToString()}")
                        }

                        // Calculate accurate delay using the current tempo
                        val microsecondsPerTick = tempo / ticksPerQuarterNote
                        val delayMillis = (tickDelta * microsecondsPerTick / 1000).toLong()
                        Thread.sleep(delayMillis)
                    }
                }
            } catch (e: Exception) {
                Log.e("MIDI_PLAYER", "Error playing MIDI file: ${e.message}")
            } finally {
                midiDriver.stop()
                isPlaying = false
            }
        }
        playbackThread?.start()
    }



    fun stopPlayback() {
        isPlaying = false
        midiDriver.stop()
        playbackThread?.interrupt()
    }
}
