package io.github.jonathanlacabe.lightnote

import android.util.Log

class MidiEvent(val bytes: ByteArray, val delay: Long)

class MidiParser(private val midiData: ByteArray) {
    private var tempo = 500000 // Default microseconds per quarter note
    private var ticksPerQuarterNote = 480 // Default ticks per quarter note

    fun parse(): List<MidiEvent> {
        val events = mutableListOf<MidiEvent>()
        var currentIndex = 14 // Starting after the header

        while (currentIndex < midiData.size) {
            // Check if we can read variable length delta time
            if (currentIndex >= midiData.size) break

            // Read the delta time
            val (deltaTime, bytesRead) = extractVariableLengthValue(midiData, currentIndex)
            val delay = deltaToMilliseconds(deltaTime)
            currentIndex += bytesRead

            // Check if we can read an event
            if (currentIndex >= midiData.size) break

            // Parse the MIDI event bytes
            val (eventBytes, bytesReadEvent) = parseMidiEventBytes(midiData, currentIndex)
            events.add(MidiEvent(eventBytes, delay))
            currentIndex += bytesReadEvent
        }
        return events
    }

    private fun deltaToMilliseconds(deltaTime: Int): Long {
        val microsecondsPerTick = tempo / ticksPerQuarterNote
        val millisecondsPerTick = microsecondsPerTick / 1000.0
        return (deltaTime * millisecondsPerTick).toLong()
    }

    private fun parseMidiEventBytes(data: ByteArray, startIndex: Int): Pair<ByteArray, Int> {
        if (startIndex >= data.size) return Pair(ByteArray(0), 0)

        val eventType = data[startIndex].toInt() and 0xF0
        val eventChannel = data[startIndex].toInt() and 0x0F
        val length = when (eventType) {
            0x80, 0x90 -> 3 // Note Off and Note On messages
            else -> 1 // Default length for unsupported events
        }

        val endIndex = (startIndex + length).coerceAtMost(data.size)
        val eventBytes = data.copyOfRange(startIndex, endIndex)

        // Detailed debug logging
        Log.d("MIDI_PARSER", "Parsed event type: $eventType, channel: $eventChannel, length: $length, bytes: ${eventBytes.joinToString()}")

        return Pair(eventBytes, endIndex - startIndex)
    }


    private fun extractVariableLengthValue(data: ByteArray, index: Int): Pair<Int, Int> {
        var value = 0
        var bytesRead = 0
        var currentIndex = index

        while (currentIndex < data.size) {
            val currentByte = data[currentIndex].toInt()
            value = (value shl 7) or (currentByte and 0x7F)
            bytesRead++
            currentIndex++
            if (currentByte and 0x80 == 0) break
        }
        return Pair(value, bytesRead)
    }
}
