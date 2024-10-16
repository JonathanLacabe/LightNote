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

    // Constants for GM instrument names
    private val gmInstruments = listOf(
        "Acoustic Grand Piano",
        "Bright Acoustic Piano",
        "Electric Grand Piano",
        "Honky-tonk Piano",
        "Rhodes Piano",
        "Chorused Piano",
        "Harpsichord",
        "Clavinet",
        "Celesta",
        "Glockenspiel",
        "Music Box",
        "Vibraphone",
        "Marimba",
        "Xylophone",
        "Tubular Bells",
        "Dulcimer",
        "Hammond Organ",
        "Percussive Organ",
        "Rock Organ",
        "Church Organ",
        "Reed Organ",
        "Accordion",
        "Harmonica",
        "Tango Accordion",
        "Acoustic Nylon Guitar",
        "Acoustic Steel Guitar",
        "Electric Jazz Guitar",
        "Electric Clean Guitar",
        "Electric Muted Guitar",
        "Overdriven Guitar",
        "Distortion Guitar",
        "Guitar Harmonics",
        "Acoustic Bass",
        "Fingered Electric Bass",
        "Plucked Electric Bass",
        "Fretless Bass",
        "Slap Bass 1",
        "Slap Bass 2",
        "Synth Bass 1",
        "Synth Bass 2",
        "Violin",
        "Viola",
        "Cello",
        "Contrabass",
        "Tremolo Strings",
        "Pizzicato Strings",
        "Orchestral Harp",
        "Timpani",
        "String Ensemble 1",
        "String Ensemble 2",
        "Synth Strings 1",
        "Synth Strings 2",
        "Choir 'Aah'",
        "Choir 'Ooh'",
        "Synth Voice",
        "Orchestral Hit",
        "Trumpet",
        "Trombone",
        "Tuba",
        "Muted Trumpet",
        "French Horn",
        "Brass Section",
        "Synth Brass 1",
        "Synth Brass 2",
        "Soprano Sax",
        "Alto Sax",
        "Tenor Sax",
        "Baritone Sax",
        "Oboe",
        "English Horn",
        "Bassoon",
        "Clarinet",
        "Piccolo",
        "Flute",
        "Recorder",
        "Pan Flute",
        "Bottle Blow",
        "Shakuhachi",
        "Whistle",
        "Ocarina",
        "Square Wave Lead",
        "Sawtooth Wave Lead",
        "Calliope Lead",
        "Chiff Lead",
        "Charang Lead",
        "Voice Lead",
        "Fifths Lead",
        "Bass Lead",
        "New Age Pad",
        "Warm Pad",
        "Polysynth Pad",
        "Choir Pad",
        "Bowed Pad",
        "Metallic Pad",
        "Halo Pad",
        "Sweep Pad",
        "Rain (FX)",
        "Soundtrack (FX)",
        "Crystal (FX)",
        "Atmosphere (FX)",
        "Brightness (FX)",
        "Goblins (FX)",
        "Echoes (FX)",
        "Sci-Fi (FX)",
        "Sitar",
        "Banjo",
        "Shamisen",
        "Koto",
        "Kalimba",
        "Bagpipe",
        "Fiddle",
        "Shanai",
        "Tinkle Bell",
        "Agogo",
        "Steel Drums",
        "Woodblock",
        "Taiko Drum",
        "Melodic Tom",
        "Synth Drum",
        "Reverse Cymbal",
        "Guitar Fret Noise",
        "Breath Noise",
        "Seashore",
        "Bird Tweet",
        "Telephone Ring",
        "Helicopter",
        "Applause",
        "Gun Shot"
    )

    private var onInstrumentUpdate: ((String) -> Unit)? = null

    // Define a variable to store the elapsed time in milliseconds
    private var elapsedMillis = 0L
    private var lastStartTime = 0L // Timestamp of last resume

    // Callback function to update elapsed time on the UI
    private var onTimeUpdate: ((Long) -> Unit)? = null

    // New callback for channel updates
    private var onChannelUpdate: ((Int) -> Unit)? = null

    // Function to set the channel update callback
    fun setOnChannelUpdateCallback(callback: (Int) -> Unit) {
        onChannelUpdate = callback
    }

    // Function to load and play a MIDI file and retrieve instrument info
    fun loadAndPlayMidiFile(uri: Uri, startTick: Long = 0L) {
        stopPlayback() // Stop any ongoing playback
        contentResolver.openInputStream(uri)?.use { inputStream ->
            midiDriver.start()
            parseInstrumentAndChannel(contentResolver.openInputStream(uri)!!) // Call new function
            playMidiData(contentResolver.openInputStream(uri)!!, startTick)
        } ?: Log.e("MIDI_PLAYER", "Failed to load MIDI file from URI")
    }

    // Modify the parseInstrument function to retrieve the channel number
    private fun parseInstrumentAndChannel(inputStream: InputStream) {
        try {
            val sequence = MidiSystem.getSequence(inputStream)
            val instruments = mutableSetOf<String>()
            var channel: Int? = null

            for (track in sequence.tracks) {
                for (i in 0 until track.size()) {
                    val event = track.get(i)
                    val message = event.message

                    // Extract instrument information
                    if (message != null && message.status in 0xC0..0xCF && (message.message?.size ?: 0) > 1) {
                        val instrumentIndex = message.message!![1].toInt() and 0x7F
                        if (instrumentIndex in gmInstruments.indices) {
                            instruments.add(gmInstruments[instrumentIndex])
                        }
                    }

                    // Extract channel information (only need first occurrence)
                    if (channel == null && message != null && message.message?.isNotEmpty() == true) {
                        val statusByte = message.message!![0].toInt()
                        if (statusByte in 0x80..0xEF) { // Only for channel-specific messages
                            channel = (statusByte and 0x0F) + 1
                        }
                    }
                }
            }

            // Update the UI with instrument and channel information
            onInstrumentUpdate?.invoke(instruments.firstOrNull() ?: "Unknown Instrument")
            onChannelUpdate?.invoke(channel ?: 1) // Default to channel 1 if not found

        } catch (e: Exception) {
            Log.e("MIDI_PLAYER", "Error parsing instrument and channel: ${e.message}")
        }
    }

    // Set the instrument update callback
    fun setOnInstrumentUpdateCallback(callback: (String) -> Unit) {
        onInstrumentUpdate = callback
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
