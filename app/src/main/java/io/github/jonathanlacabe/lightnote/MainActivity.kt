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
    }
}
