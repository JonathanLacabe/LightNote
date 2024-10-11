package io.github.jonathanlacabe.lightnote

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.PushbackInputStream

data class MidiEvent(val bytes: ByteArray, val delay: Long)

class MidiParser(private val midiData: ByteArray) {

    private var ticksPerQuarterNote = 480 // Default ticks per quarter note if not specified
    private var tempo = 500000 // Default microseconds per quarter note (120 BPM)

    // Function to parse MIDI data
    fun parse(): List<MidiEvent> {
        val events = mutableListOf<MidiEvent>()
        val stream = PushbackInputStream(ByteArrayInputStream(midiData))

        // Parse MIDI header
        if (!parseMidiHeader(stream)) {
            Log.e("MIDI_PARSER", "Invalid MIDI header")
            return events
        }

        // Parse track(s)
        while (stream.available() > 0) {
            if (!parseTrack(stream, events)) {
                Log.e("MIDI_PARSER", "Failed to parse track")
                break
            }
        }
        return events
    }

    // Parse the MIDI header (first 14 bytes)
    private fun parseMidiHeader(stream: PushbackInputStream): Boolean {
        val header = ByteArray(4)
        stream.read(header)
        if (!header.contentEquals("MThd".toByteArray())) return false

        // Skip header length (next 4 bytes should be 00 00 00 06)
        val headerLength = readTwoBytes(stream) shl 16 or readTwoBytes(stream)
        if (headerLength != 6) {
            Log.e("MIDI_PARSER", "Unexpected header length: $headerLength")
            return false
        }

        // Format and track count
        readTwoBytes(stream) // Format (0, 1, or 2)
        readTwoBytes(stream) // Track count

        ticksPerQuarterNote = readTwoBytes(stream)
        Log.d("MIDI_PARSER", "Parsed MIDI header, ticksPerQuarter: $ticksPerQuarterNote")
        return true
    }

    private fun parseTrack(stream: PushbackInputStream, events: MutableList<MidiEvent>): Boolean {
        val header = ByteArray(4)
        stream.read(header)
        if (!header.contentEquals("MTrk".toByteArray())) {
            Log.e("MIDI_PARSER", "Invalid track header")
            return false
        }

        val trackLength = readTwoBytes(stream) shl 16 or readTwoBytes(stream)
        Log.d("MIDI_PARSER", "Track length: $trackLength")

        var lastStatusByte = 0
        var accumulatedDelay = 0L

        while (stream.available() > 0) {
            // Read the delta time and convert to milliseconds
            val deltaTime = readVariableLengthValue(stream)
            accumulatedDelay += deltaToMilliseconds(deltaTime)

            // Read the status byte or reuse the last status byte (running status)
            val statusByte = stream.read()
            if (statusByte >= 0x80) {
                lastStatusByte = statusByte
            } else {
                stream.unread(statusByte)
            }

            // Parse supported MIDI event types
            when (lastStatusByte and 0xF0) {
                0x80 -> { // Note Off
                    val note = stream.read()
                    val velocity = stream.read()
                    events.add(
                        MidiEvent(byteArrayOf(lastStatusByte.toByte(), note.toByte(), velocity.toByte()), accumulatedDelay)
                    )
                    accumulatedDelay = 0L // Reset delay after adding the event
                    Log.d("MIDI_PARSER", "Note Off - Note: $note, Velocity: $velocity, Delay: $accumulatedDelay")
                }
                0x90 -> { // Note On
                    val note = stream.read()
                    val velocity = stream.read()
                    if (velocity > 0) {
                        events.add(
                            MidiEvent(byteArrayOf(lastStatusByte.toByte(), note.toByte(), velocity.toByte()), accumulatedDelay)
                        )
                        Log.d("MIDI_PARSER", "Note On - Note: $note, Velocity: $velocity, Delay: $accumulatedDelay")
                    } else {
                        events.add(
                            MidiEvent(byteArrayOf((lastStatusByte and 0xF0).toByte(), note.toByte(), 0), accumulatedDelay)
                        )
                        Log.d("MIDI_PARSER", "Note Off (Note On with 0 velocity) - Note: $note, Delay: $accumulatedDelay")
                    }
                    accumulatedDelay = 0L // Reset delay
                }
                0xFF -> { // Meta events
                    val metaType = stream.read()
                    val metaLength = readVariableLengthValue(stream)
                    val metaData = ByteArray(metaLength)
                    stream.read(metaData)
                    if (metaType == 0x51) { // Tempo Change
                        tempo = extractTempo(metaData)
                        Log.d("MIDI_PARSER", "Tempo Change - New Tempo: $tempo")
                    } else {
                        Log.d("MIDI_PARSER", "Skipped unsupported meta event type: ${metaType.toString(16)}")
                    }
                }
                else -> {
                    Log.d("MIDI_PARSER", "Skipped unsupported event with status byte: ${lastStatusByte.toString(16)}")
                    stream.skip(1)
                }
            }
        }
        return true
    }

    private fun extractTempo(data: ByteArray): Int {
        return (data[0].toInt() and 0xFF shl 16) or
                (data[1].toInt() and 0xFF shl 8) or
                (data[2].toInt() and 0xFF)
    }

    private fun deltaToMilliseconds(deltaTime: Int): Long {
        // Artificially increase tempo by 5x to speed up playback timing
        val microsecondsPerTick = tempo.toDouble() / ticksPerQuarterNote
        return (deltaTime * microsecondsPerTick / 5000).toLong()
    }


    private fun readVariableLengthValue(stream: PushbackInputStream): Int {
        var value = 0
        var byte: Int
        do {
            byte = stream.read()
            value = (value shl 7) or (byte and 0x7F)
        } while (byte and 0x80 != 0)
        return value
    }

    private fun readTwoBytes(stream: PushbackInputStream): Int {
        val byte1 = stream.read()
        val byte2 = stream.read()
        return (byte1 shl 8) or byte2
    }
}
