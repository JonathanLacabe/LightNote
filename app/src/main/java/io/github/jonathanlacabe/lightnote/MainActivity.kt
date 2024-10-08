package io.github.jonathanlacabe.lightnote

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.PopupWindow
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import io.github.jonathanlacabe.lightnote.databinding.ActivityNewBinding

class ActivityNew : ComponentActivity() {

    private lateinit var binding: ActivityNewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up File button click listener to show custom popup menu
        binding.fileButton.setOnClickListener { showFileMenu() }
    }

    private fun showFileMenu() {
        // Inflate the file_menu layout
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.file_menu, null)

        // Initialize PopupWindow with the inflated view and set width and height
        val popupWindow = PopupWindow(popupView, 300, 200, true)

        // Position the popup window just below the File button
        popupWindow.showAsDropDown(binding.fileButton, 0, 0)

        // Set up "Open..." and "Exit" click listeners in the popup window
        val openOption = popupView.findViewById<TextView>(R.id.openOption)
        val exitOption = popupView.findViewById<TextView>(R.id.exitOption)

        // Open file system for MIDI files on "Open..." click
        openOption.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/midi"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/x-midi"))
            }
            startActivityForResult(intent, REQUEST_CODE_OPEN_MIDI)
        }

        // Exit the app on "Exit" click
        exitOption.setOnClickListener {
            popupWindow.dismiss()
            finish()
        }

        // Dismiss the popup when tapping outside
        popupWindow.isOutsideTouchable = true
        popupWindow.setBackgroundDrawable(null)
    }

    companion object {
        const val REQUEST_CODE_OPEN_MIDI = 1
    }
}
