package io.github.jonathanlacabe.lightnote

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import org.billthefarmer.mididriver.MidiDriver
import java.io.InputStream
import jp.kshoji.javax.sound.midi.MidiSystem
import jp.kshoji.javax.sound.midi.Sequence
import jp.kshoji.javax.sound.midi.MetaMessage
import android.os.Handler
import android.os.Looper

class MidiPlaybackHandler(private val contentResolver: ContentResolver) {

    private val midiDriver: MidiDriver = MidiDriver.getInstance()
    private var isPlaying = false
    private var playbackThread: Thread? = null
    private var currentTick = 0L
    private var resumeTick = 0L
    private var isPaused = false
    private var storedTempo = 500000 // Default tempo (120 BPM), saved between pauses

    // Define a variable to store the elapsed time in milliseconds
    private var elapsedMillis = 0L
    private var lastStartTime = 0L // Timestamp of last resume

    // Callback function to update elapsed time on the UI
    private var onTimeUpdate: ((Long) -> Unit)? = null

    fun loadAndPlayMidiFile(uri: Uri, startTick: Long = 0L) {
        stopPlayback() // Safely stop any ongoing playback
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream != null) {
            midiDriver.start()
            playMidiData(inputStream, startTick)
        } else {
            Log.e("MIDI_PLAYER", "Failed to load MIDI file from URI")
        }
    }

    private fun playMidiData(inputStream: InputStream, startTick: Long) {
        val mainHandler = Handler(Looper.getMainLooper())
        playbackThread = Thread {
            try {
                val sequence: Sequence = MidiSystem.getSequence(inputStream)
                isPlaying = true
                isPaused = false
                elapsedMillis = if (startTick == 0L) 0L else elapsedMillis
                lastStartTime = System.currentTimeMillis()

                val ticksPerQuarterNote = sequence.resolution.toDouble()
                var tempo = storedTempo

                for (track in sequence.tracks) {
                    var lastEventTick = 0L
                    Log.d("MIDI_PLAYER", "Starting playback from tick: $startTick")

                    for (i in 0 until track.size()) {
                        if (!isPlaying) break

                        val event = track.get(i)
                        if (event.tick < startTick) continue

                        val tickDelta = event.tick - lastEventTick
                        lastEventTick = event.tick
                        currentTick = event.tick

                        // Handle tempo change
                        if (event.message is MetaMessage) {
                            val metaMessage = event.message as MetaMessage
                            if (metaMessage.type == 0x51 && metaMessage.data.size >= 3) {
                                tempo = ((metaMessage.data[0].toInt() and 0xFF) shl 16) or
                                        ((metaMessage.data[1].toInt() and 0xFF) shl 8) or
                                        (metaMessage.data[2].toInt() and 0xFF)
                                storedTempo = tempo
                                Log.d("MIDI_PLAYER", "Tempo change detected: $tempo microseconds per quarter note")
                            }
                        }

                        // Calculate the delay in milliseconds based on tick difference and tempo
                        val microsecondsPerTick = tempo / ticksPerQuarterNote
                        val delayMillis = if (resumeTick == event.tick) 0L else (tickDelta * microsecondsPerTick / 1000).toLong()

                        // Perform the delay and then update the elapsed time
                        if (isPlaying && delayMillis > 0) {
                            Thread.sleep(delayMillis)
                        }

                        // Update elapsed time on the UI thread every 1000 milliseconds
                        mainHandler.post {
                            onTimeUpdate?.invoke(getElapsedTimeInMillis())
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




    fun getMidiDuration(uri: Uri): Long? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val sequence = MidiSystem.getSequence(inputStream)
                val tickLength = sequence.tickLength
                val resolution = sequence.resolution.toDouble()
                val tempo = 500000 // Default tempo in microseconds per quarter note
                val secondsPerTick = (tempo / resolution) / 1_000_000.0
                (tickLength * secondsPerTick * 1000).toLong()
            }
        } catch (e: Exception) {
            Log.e("MIDI_PLAYER", "Error calculating MIDI duration: ${e.message}")
            null
        }
    }

    fun pausePlayback() {
        if (isPlaying) {
            isPlaying = false
            elapsedMillis += System.currentTimeMillis() - lastStartTime
            isPaused = true
            resumeTick = currentTick // Store current tick for resuming playback
            midiDriver.stop()
            playbackThread?.interrupt() // Stop playback thread
            Log.d("MIDI_PLAYER", "Playback paused at tick $resumeTick with tempo $storedTempo")
        }
    }

    fun resumePlayback(uri: Uri) {
        if (!isPlaying) {
            Log.d("MIDI_PLAYER", "Resuming playback from tick $resumeTick with stored tempo $storedTempo")
            isPaused = false
            lastStartTime = System.currentTimeMillis()
            loadAndPlayMidiFile(uri, resumeTick) // Resume from stored tick and tempo
        }
    }

    fun stopPlayback() {
        if (isPlaying || isPaused) {
            isPlaying = false
            isPaused = false
            currentTick = 0L // Reset current playback position
            resumeTick = 0L // Reset resume point
            elapsedMillis = 0L //for the time counter, this is reset.
            midiDriver.stop()
            playbackThread?.interrupt() // Interrupt playback thread if running
            Log.d("MIDI_PLAYER", "Playback fully stopped and reset.")
        }
    }

    // Set the callback function to be called with elapsed time
    fun setOnTimeUpdateCallback(callback: (Long) -> Unit) {
        onTimeUpdate = callback
    }

    // Helper function to retrieve total elapsed time in milliseconds
    fun getElapsedTimeInMillis(): Long {
        return if (isPlaying) {
            elapsedMillis + (System.currentTimeMillis() - lastStartTime)
        } else {
            elapsedMillis
        }
    }
}
