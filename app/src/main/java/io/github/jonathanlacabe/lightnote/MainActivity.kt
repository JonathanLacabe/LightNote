package io.github.jonathanlacabe.lightnote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.jonathanlacabe.lightnote.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle New button click to navigate to NewActivity
        binding.newButton.setOnClickListener {
            val intent = Intent(this, NewActivity::class.java)
            startActivity(intent)
        }

        // Handle F.A.Q. button click to navigate to FaqActivity
        binding.faqButton.setOnClickListener {
            val intent = Intent(this, FaqActivity::class.java)
            startActivity(intent)
        }

        // Handle Open button click to open the file manager
        binding.openButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/midi"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/x-midi"))
            }
            startActivityForResult(intent, REQUEST_CODE_OPEN_MIDI)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_MIDI && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val intent = Intent(this, NewActivity::class.java).apply {
                    putExtra("MIDI_FILE_URI", uri.toString())
                }
                startActivity(intent)
            }
        }
    }

    companion object {
        const val REQUEST_CODE_OPEN_MIDI = 1
    }
}
