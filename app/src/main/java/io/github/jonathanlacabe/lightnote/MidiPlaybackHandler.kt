package io.github.jonathanlacabe.lightnote

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import org.billthefarmer.mididriver.MidiDriver
import java.io.InputStream
import jp.kshoji.javax.sound.midi.MidiSystem
import jp.kshoji.javax.sound.midi.Sequence

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

                for (track in sequence.tracks) {
                    for (event in track.ticks) {
                        if (!isPlaying) break
                        val message = event.message
                        midiDriver.write(message) // Send the event to MidiDriver

                        // Timing Control
                        val delayMillis = event.tickLength * 2 // Adjust as necessary
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
