package io.github.jonathanlacabe.lightnote

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import io.github.jonathanlacabe.lightnote.databinding.ActivityNewBinding
import org.billthefarmer.mididriver.MidiDriver

class NewActivity : ComponentActivity() {

    private lateinit var binding: ActivityNewBinding
    private var currentFileName: String = "default" //Value will only appear in exceptions.
    private var isPlaying = false // Initialize isPlaying to track playback state

    // Declare MidiDriver and a ByteArray for the MIDI file data
    private lateinit var midiDriver: MidiDriver
    private var midiData: ByteArray? = null

    private var playbackThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateFileNameTextView(currentFileName)

        midiDriver = MidiDriver.getInstance() // Initialize the MIDI driver
        midiDriver.start() // Start the MIDI driver

        Log.d("MIDI_DRIVER", "MIDI Driver initialized")

        //upper space TextView show a toast with the full name on hold-down
        binding.fileNameTextView.setOnLongClickListener {
            Toast.makeText(this, currentFileName, Toast.LENGTH_SHORT).show()
            true
        }

        //File button
        binding.fileButton.setOnClickListener { showFileMenu() }

        //View button
        binding.viewButton.setOnClickListener { showViewMenu() }

        //Help button
        binding.helpButton.setOnClickListener { showHelpMenu() }

        // Play button functionality
        binding.playButton.setOnClickListener {
            if (!isPlaying && midiData != null) {
                isPlaying = true
                playMidiData() // Call playMidiData to start playback
            }
        }

        // Pause button functionality
        binding.pauseButton.setOnClickListener {
            if (isPlaying) {
                isPlaying = false // Stop playback, will pause since thread checks isPlaying
            }
        }

        // Rewind button functionality
        binding.rewindButton.setOnClickListener {
            if (isPlaying) {
                isPlaying = false // Stop current playback
            }
            // Restart playback from the beginning
            if (midiData != null) {
                playMidiData()
            }
        }


    }

    private fun updateFileNameTextView(fileName: String) {
        // Update the TextView with the new file name
        currentFileName = fileName.substringBeforeLast(".") // Remove extension
        binding.fileNameTextView.text = currentFileName

        // Enable marquee scrolling if the text is too long
        binding.fileNameTextView.isSelected = true
    }

    private fun showFileMenu() {
        val fileButton = findViewById<TextView>(R.id.fileButton)
        // Inflate the file_menu layout
        val popupView = layoutInflater.inflate(R.layout.file_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.showAsDropDown(fileButton, -24, 0)


        // Set popup window background and outside touch handling
        popupWindow.isOutsideTouchable = true

        // Position the popup window just below the File button
        popupWindow.showAsDropDown(binding.fileButton, 0, 8)

        // Set up "Open..." and "Exit" click listeners in the popup window
        val openOption = popupView.findViewById<TextView>(R.id.openOption)
        val exitOption = popupView.findViewById<TextView>(R.id.exitOption)

        openOption.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/midi"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/x-midi"))
            }
            startActivityForResult(intent, REQUEST_CODE_OPEN_MIDI)
        }

        exitOption.setOnClickListener {
            popupWindow.dismiss()
            finish()
        }
    }

    private fun showViewMenu() {
        val viewButton = findViewById<TextView>(R.id.viewButton)
        // Inflate the view_menu layout
        val popupView = layoutInflater.inflate(R.layout.view_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.showAsDropDown(viewButton, -37, 0)

        // Set up "Piano Roll Editor" click behavior (appears clickable but does nothing)
        val pianoRollEditor = popupView.findViewById<TextView>(R.id.pianoRollEditor)
        pianoRollEditor.setOnClickListener {
            // Show some visual feedback when tapped
            pianoRollEditor.alpha = 0.5f
            pianoRollEditor.postDelayed({ pianoRollEditor.alpha = 1f }, 100)
            popupWindow.dismiss() // Just dismiss the popup; no actual function needed
        }

        //The Staff Editor option is grayed out until v2
    }

    private fun showHelpMenu() {
        val helpButton = findViewById<TextView>(R.id.helpButton)
        val popupView = layoutInflater.inflate(R.layout.help_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.showAsDropDown(helpButton, -30, 3)
        popupWindow.isOutsideTouchable = true

        val tutorialOption = popupView.findViewById<TextView>(R.id.tutorialOption)
        val githubOption = popupView.findViewById<TextView>(R.id.githubOption)
        val youtubeOption = popupView.findViewById<TextView>(R.id.youtubeOption)

        // Open tutorial sequence
        tutorialOption.setOnClickListener {
            popupWindow.dismiss()
            startTutorialSequence()
        }

        // Open GitHub documentation link
        githubOption.setOnClickListener {
            popupWindow.dismiss()
            val githubUri = Uri.parse("https://github.com/JonathanLacabe/LightNote/blob/master/README.md")
            val githubIntent = Intent(Intent.ACTION_VIEW, githubUri)
            startActivity(githubIntent)
        }

        // Open YouTube playlist link
        youtubeOption.setOnClickListener {
            popupWindow.dismiss()
            val youtubeUri = Uri.parse("https://www.youtube.com/playlist?list=PLS8kBPvyvuR9t8YuetX402X1M1pYZUqTB")
            val youtubeIntent = Intent(Intent.ACTION_VIEW, youtubeUri)
            startActivity(youtubeIntent)
        }
    }

    //Extract the title of the midi file imported by user. Without ContentResolver, the straight mri will be produced.
    //Differences in URI structure across devices can cause issues - "Unknown File" occurs in exception cases.
    //This function tries two methods to get the title.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_MIDI && resultCode == RESULT_OK) {
            data?.data?.let { uri ->

                //scrapes midi info
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    midiData = inputStream.readBytes() // Load MIDI file into midiData
                }

                var fileName: String? = null

                // 1. Attempt to retrieve display name using ContentResolver
                contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        fileName = cursor.getString(0)?.substringBeforeLast('.') // Removes extension if found
                    }
                }

                // 2. Fallback: If display name is still null, try getting filename from URI path
                if (fileName == null) {
                    fileName = uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown File"
                }

                // Update the displayed file name
                updateFileNameTextView(fileName!!)
            }
        }
    }


    // Helper function to get column name more reliably
    private fun Cursor.getColumnIndexOpenableColumnName(columnName: String): Int {
        return this.getColumnIndex(columnName).takeIf { it != -1 } ?: this.getColumnIndex("name")
    }

    private fun playMidiData() {
        if (midiData == null) {
            Log.e("MIDI_DRIVER", "No MIDI data loaded")
            return
        }
        playbackThread = Thread {
            midiData?.let { data ->
                val midiParser = MidiParser(data)
                val events = try {
                    midiParser.parse() // Parse MIDI events safely
                } catch (e: Exception) {
                    Log.e("MIDI_DRIVER", "Error parsing MIDI data: ${e.message}")
                    return@Thread
                }

                isPlaying = true
                for (event in events) {
                    if (!isPlaying) break // Stop if paused

                    try {
                        midiDriver.write(event.bytes)
                        Log.d("MIDI_DRIVER", "MIDI event sent successfully.")
                    } catch (e: Exception) {
                        Log.e("MIDI_DRIVER", "Error sending MIDI event: ${e.message}")
                    }

                    // Use `event.delay` with error handling
                    try {
                        Thread.sleep(event.delay)
                    } catch (e: InterruptedException) {
                        Log.e("MIDI_DRIVER", "Playback interrupted: ${e.message}")
                    }
                }
                isPlaying = false
            }
        }
        playbackThread?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        midiDriver.stop()
        midiDriver.stop() // Stop the MIDI driver
    }


    private fun startTutorialSequence() {
        // Placeholder for tutorial sequence logic
        // Implement the tutorial popups or overlays here
    }

    companion object {
        const val REQUEST_CODE_OPEN_MIDI = 1
    }
}
