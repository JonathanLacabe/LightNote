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
    private var isRunning = false
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
    private var selectedInstrumentText: String? = null
    private var selectedChannelText: Int? = null

    // Define a variable to store the elapsed time in milliseconds
    private var elapsedMillis = 0L
    private var lastStartTime = 0L // Timestamp of last resume

    // Callback function to update elapsed time on the UI
    private var onTimeUpdate: ((Long) -> Unit)? = null

    // New callback for channel updates
    private var onChannelUpdate: ((Int) -> Unit)? = null
    private var onTypeUpdate: ((String) -> Unit)? = null

    // Store instruments for each channel (channel -> instrument number)
    private val channelInstruments = mutableMapOf<Int, Int>()

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

    // Modify the parseInstrumentAndChannel function to store the instrument for each channel
    private fun parseInstrumentAndChannel(inputStream: InputStream) {
        try {
            val sequence = MidiSystem.getSequence(inputStream)
            tracks.clear() // Clear previous track data
            channelInstruments.clear() // Clear previous instrument data for channels

            // Loop through each track in the sequence
            for (track in sequence.tracks) {
                var trackName: String? = null
                val instruments = mutableSetOf<String>()
                var detectedChannel: Int? = null
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
                            detectedChannel = 10
                        }

                        // Handle Program Change for instrument detection
                        if (message.status in 0xC0..0xCF && message.message!!.size > 1) {
                            val instrumentIndex = message.message!![1].toInt() and 0x7F
                            if (instrumentIndex in gmInstruments.indices) {
                                instruments.add(gmInstruments[instrumentIndex])
                            }
                            if (detectedChannel == null) detectedChannel = channel

                            // Store the instrument for this channel
                            channelInstruments[channel] = instrumentIndex
                        }
                    }
                }

                // Avoid adding an "Unknown Track" if trackName remains unset but instruments are valid
                if (instruments.isNotEmpty() || trackName != null) {
                    trackName = trackName ?: "Track ${tracks.size + 1}"
                    val trackInstrument = if (isDrumChannel) "Drums" else instruments.firstOrNull() ?: "Unknown Instrument"
                    val trackChannel = detectedChannel ?: 1
                    tracks.add(TrackInfo(trackName, trackInstrument, trackChannel))
                    Log.d("MIDI_PLAYER", "Track Added: Name=$trackName, Instrument=$trackInstrument, Channel=$trackChannel")
                }
            }

            // Set the current track and update the UI
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
            val selectedTrack = tracks[currentTrackIndex]

            // Store the selected track's instrument and channel for later use
            selectedInstrumentText = selectedTrack.instrument
            selectedChannelText = selectedTrack.channel

            Log.d("MIDI_PLAYER", "Track changed to: ${selectedTrack.name}, Instrument: ${selectedTrack.instrument}, Channel: ${selectedTrack.channel}")

            // Update the UI immediately
            updateCurrentTrack() // This will invoke the callbacks to update the instrument and channel in the UI
        } else {
            Log.e("MIDI_PLAYER", "Invalid track index: $index")
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
                var tempo: Long = 500000L // Initial tempo: 120 BPM
                var microsecondsPerTick = tempo / ticksPerQuarterNote
                val totalTicks = sequence.tickLength

                Log.d("MIDI_PLAYER", "Total ticks: $totalTicks, Ticks per quarter note: $ticksPerQuarterNote")
                Log.d("MIDI_PLAYER", "Initial Tempo: $tempo microseconds per quarter note (120 BPM)")

                // Track and apply tempo changes
                val tempoChanges = mutableListOf<Pair<Long, Long>>()
                for (track in sequence.tracks) {
                    for (i in 0 until track.size()) {
                        val event = track.get(i)
                        if (event.message is MetaMessage) {
                            val metaMessage = event.message as MetaMessage
                            if (metaMessage.type == 0x51 && metaMessage.data.size >= 3) {
                                val newTempo = ((metaMessage.data[0].toLong() and 0xFF) shl 16) or
                                        ((metaMessage.data[1].toLong() and 0xFF) shl 8) or
                                        (metaMessage.data[2].toLong() and 0xFF)
                                tempoChanges.add(Pair(event.tick, newTempo))
                            }
                        }
                    }
                }
                tempoChanges.sortBy { it.first }

                val allEvents = mutableListOf<Triple<Long, ByteArray, Int>>()
                for ((trackIndex, track) in sequence.tracks.withIndex()) {
                    for (i in 0 until track.size()) {
                        val event = track.get(i)
                        val message = event.message.message
                        if (event.tick >= startTick && message != null) {
                            val trackChannel = tracks.getOrNull(trackIndex)?.channel ?: 1
                            allEvents.add(Triple(event.tick, message, trackChannel))
                        }
                    }
                }
                allEvents.sortBy { it.first }

                var previousTick = startTick
                var tempoIndex = 0
                val activeNotes = mutableMapOf<Byte, Long>()

                for ((tick, message, channel) in allEvents) {
                    if (!isPlaying) break

                    // Apply tempo changes as needed
                    while (tempoIndex < tempoChanges.size && tempoChanges[tempoIndex].first <= tick) {
                        val (changeTick, newTempo) = tempoChanges[tempoIndex]
                        tempo = newTempo
                        microsecondsPerTick = tempo / ticksPerQuarterNote
                        tempoIndex++
                    }

                    val tickDelta = tick - previousTick
                    previousTick = tick
                    currentTick = tick

                    val delayMicroseconds = (tickDelta * microsecondsPerTick).toLong()
                    val delayMillis = delayMicroseconds / 1000
                    if (delayMillis > 0 && isPlaying) {
                        Thread.sleep(delayMillis)
                    }

                    // Override Program Change (0xC0) messages for the selected track
                    if (message[0].toInt() and 0xF0 == 0xC0) {
                        // Always use the instrument assigned to the selected track's channel
                        if (channel == tracks[currentTrackIndex].channel) {
                            val newProgramChangeMessage = byteArrayOf(
                                (0xC0 or (channel - 1)).toByte(),
                                channelInstruments[channel]!!.toByte() // Override with selected instrument
                            )
                            Log.d("MIDI_PLAYER", "Overriding Program Change: Channel=$channel, Instrument=${gmInstruments[channelInstruments[channel]!!]}")
                            midiDriver.write(newProgramChangeMessage)
                        }
                    } else {
                        // Handle note-on and note-off events
                        if (message[0].toInt() and 0xF0 == 0x90 && message[2].toInt() > 0) {
                            activeNotes[message[1]] = tick
                        } else if (message[0].toInt() and 0xF0 == 0x80 || (message[0].toInt() and 0xF0 == 0x90 && message[2].toInt() == 0)) {
                            val startTick = activeNotes.remove(message[1])
                            if (startTick != null) {
                                val noteDurationTicks = tick - startTick
                                val noteDurationMillis = (noteDurationTicks * microsecondsPerTick / 1000)
                            }
                        }

                        // Write MIDI message to driver, adjusting for the correct channel
                        if (tick >= totalTicks) break

                        mainHandler.post { onTimeUpdate?.invoke(getElapsedTimeInMillis()) }

                        if (message[0].toInt() and 0xF0 == 0x90 && (message[0].toInt() and 0x0F) + 1 == 10) {
                            midiDriver.write(message) // Channel 10 (Drums)
                        } else {
                            val adjustedMessage = message.copyOf().apply { this[0] = (this[0] and 0xF0.toByte()) or (channel - 1).toByte() }
                            midiDriver.write(adjustedMessage)
                        }
                    }
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

            // Reload and resume playback from where it was paused
            contentResolver.openInputStream(uri)?.let { inputStream ->
                // Load and start playing the MIDI file from the resumeTick
                loadAndPlayMidiFile(uri, resumeTick)

                // After playback has started, restore the correct instruments
                // This ensures that Program Change messages are sent after playback resumes
                Handler(Looper.getMainLooper()).postDelayed({
                    for ((channel, instrument) in channelInstruments) {
                        // Send the Program Change message to set the correct instrument
                        val programChangeMessage = byteArrayOf((0xC0 or (channel - 1)).toByte(), instrument.toByte())
                        midiDriver.write(programChangeMessage)
                        Log.d("MIDI_PLAYER", "Restored Instrument: Channel=$channel, Instrument=${gmInstruments[instrument]}")
                    }

                    // Update the instrument and channel text in the UI from the last selected track
                    selectedInstrumentText?.let { instrumentName ->
                        onInstrumentUpdate?.invoke(instrumentName)  // Update UI for instrument
                        Log.d("MIDI_PLAYER", "UI Updated: Instrument=$instrumentName")
                    }

                    selectedChannelText?.let { channelNumber ->
                        onChannelUpdate?.invoke(channelNumber)  // Update UI for channel
                        Log.d("MIDI_PLAYER", "UI Updated: Channel=$channelNumber")
                    }

                }, 100)  // Small delay to ensure playback is underway before restoring instruments and updating UI text
            } ?: Log.e("MIDI_PLAYER", "Failed to open MIDI file for resuming playback")

            isPlaying = true // Set playing state to true after starting playback
        }
    }







    fun stopPlayback() {
        if (isPlaying || isPaused) {
            isPlaying = false
            isRunning = false
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