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
import kotlin.experimental.and
import kotlin.experimental.or

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
    private var onTypeUpdate: ((String) -> Unit)? = null

    data class TrackInfo(val name: String, val instrument: String, val channel: Int)

    val tracks = mutableListOf<TrackInfo>()
    private var currentTrackIndex = 0 // To keep track of the current track index

    // new callback for playback completion to notify the UI when playback has reached the end.
    private var onPlaybackComplete: (() -> Unit)? = null

    // Function to set the playback complete callback
    fun setOnPlaybackCompleteCallback(callback: () -> Unit) {
        onPlaybackComplete = callback
    }

    // Function to set the type update callback
    fun setOnTypeUpdateCallback(callback: (String) -> Unit) {
        onTypeUpdate = callback
    }

    // Function to determine and notify the track type
    private fun determineTrackType(inputStream: InputStream) {
        try {
            val sequence = MidiSystem.getSequence(inputStream)
            var trackType = "Instrument" // Default to Instrument type

            // Check each track for MIDI events to detect type
            for (track in sequence.tracks) {
                for (i in 0 until track.size()) {
                    val event = track.get(i)
                    val message = event.message

                    if (message != null && message.message?.isNotEmpty() == true) {
                        val statusByte = message.message!![0].toInt()
                        val channel = (statusByte and 0x0F) + 1

                        if (channel == 10) {  // MIDI channel 10 is typically reserved for percussion
                            trackType = "Rhythm"
                            break
                        }
                    }
                }
            }

            // Trigger the type callback with the determined track type
            onTypeUpdate?.invoke(trackType)

        } catch (e: Exception) {
            Log.e("MIDI_PLAYER", "Error determining track type: ${e.message}")
        }
    }
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
            determineTrackType(inputStream)
        } ?: Log.e("MIDI_PLAYER", "Failed to load MIDI file from URI")
    }

    // Modify the parseInstrument function to retrieve the channel number
    private fun parseInstrumentAndChannel(inputStream: InputStream) {
        try {
            val sequence = MidiSystem.getSequence(inputStream)
            tracks.clear() // Clear previous track data

            for (track in sequence.tracks) {
                var trackName: String? = null
                var trackInstrument = "Unknown Instrument"
                var trackChannel = 1 // Default channel
                var isDrumChannel = false

                // Analyze events in each track to retrieve track names and instruments
                for (i in 0 until track.size()) {
                    val event = track.get(i)
                    val message = event.message

                    // Detect track name using MetaMessage (0x03)
                    if (message is MetaMessage && message.type == 0x03) {
                        trackName = String(message.data)
                    }

                    // Detect channel and instrument
                    if (message != null && message.message?.isNotEmpty() == true) {
                        val statusByte = message.message!![0].toInt()
                        val channel = (statusByte and 0x0F) + 1

                        // Set drum channel flag
                        if (channel == 10) {
                            isDrumChannel = true
                            trackChannel = 10
                        }

                        // Handle Program Change for instrument detection
                        if (message.status in 0xC0..0xCF && message.message!!.size > 1) {
                            val instrumentIndex = message.message!![1].toInt() and 0x7F
                            if (instrumentIndex in gmInstruments.indices) {
                                trackInstrument = gmInstruments[instrumentIndex]
                            }
                            trackChannel = channel
                        }
                    }
                }

                // Add each track's information to the list
                trackName = trackName ?: "Track ${tracks.size + 1}"
                tracks.add(TrackInfo(trackName, trackInstrument, trackChannel))
                Log.d("MIDI_PLAYER", "Track Added: Name=$trackName, Instrument=$trackInstrument, Channel=$trackChannel")
            }

            // Set the first track as default
            currentTrackIndex = 0
            updateCurrentTrack()
        } catch (e: Exception) {
            Log.e("MIDI_PLAYER", "Error parsing instrument and channel: ${e.message}")
        }
    }




    private fun updateCurrentTrack() {
        if (tracks.isNotEmpty()) {
            val currentTrack = tracks[currentTrackIndex]
            onInstrumentUpdate?.invoke(currentTrack.instrument)
            onChannelUpdate?.invoke(currentTrack.channel)
        }
    }

    fun changeTrack(index: Int) {
        if (index in tracks.indices) {
            currentTrackIndex = index
            updateCurrentTrack() // Update the UI with the new track info
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
                val totalTicks = sequence.tickLength

                // Collect all track events and assign their instruments/channels
                val allEvents = mutableListOf<Triple<Long, ByteArray, Int>>()
                for ((trackIndex, track) in sequence.tracks.withIndex()) {
                    var lastEventTick = 0L
                    val trackChannel = tracks.getOrNull(trackIndex)?.channel ?: 1 // Default to channel 1
                    for (i in 0 until track.size()) {
                        val event = track.get(i)
                        if (event.tick >= startTick) {
                            lastEventTick = event.tick
                            allEvents.add(Triple(event.tick, event.message.message, trackChannel) as Triple<Long, ByteArray, Int>)
                        }
                    }
                }
                allEvents.sortBy { it.first }

                // Play events with correct channel assignments
                var previousTick = startTick
                for ((tick, message, channel) in allEvents) {
                    if (!isPlaying) break

                    val tickDelta = tick - previousTick
                    previousTick = tick
                    currentTick = tick

                    // End playback when reaching the end of sequence
                    if (currentTick >= totalTicks) {
                        mainHandler.post { onPlaybackComplete?.invoke() }
                        break
                    }

                    // Adjust tempo based on meta message (0xFF 0x51)
                    if (message.size >= 3 && message[0].toInt() == 0xFF && message[1].toInt() == 0x51) {
                        tempo = ((message[2].toInt() and 0xFF) shl 16) or
                                ((message[3].toInt() and 0xFF) shl 8) or
                                (message[4].toInt() and 0xFF)
                        storedTempo = tempo
                    }

                    // Calculate delay for accurate playback
                    val microsecondsPerTick = tempo / ticksPerQuarterNote
                    val delayMillis = (tickDelta * microsecondsPerTick / 1000).toLong()

                    if (isPlaying && delayMillis > 0) Thread.sleep(delayMillis)
                    mainHandler.post { onTimeUpdate?.invoke(getElapsedTimeInMillis()) }

                    // Set the event to the assigned channel
                    val adjustedMessage = message.copyOf().apply { this[0] = (this[0] and 0xF0.toByte()) or (channel - 1).toByte() }
                    midiDriver.write(adjustedMessage)
                }
            } catch (e: Exception) {
                Log.e("MIDI_PLAYER", "Error during playback: ${e.message}")
            } finally {
                midiDriver.stop()
                isPlaying = false
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

    fun resetPlaybackState() {
        stopPlayback()  // Stop playback and reset variables

        // Reset variables to initial values
        isPlaying = false
        isPaused = false
        currentTick = 0L
        resumeTick = 0L
        elapsedMillis = 0L
        storedTempo = 500000  // Reset tempo to default (120 BPM)

        // Notify UI to reset displayed information (such as instrument, channel, etc.)
        onInstrumentUpdate?.invoke("Unknown Instrument")
        onChannelUpdate?.invoke(1)  // Default channel to 1
        onTypeUpdate?.invoke("Instrument")  // Default to "Instrument" type
    }

}