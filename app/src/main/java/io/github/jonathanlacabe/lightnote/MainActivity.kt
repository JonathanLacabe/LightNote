package io.github.jonathanlacabe.lightnote


import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.jonathanlacabe.lightnote.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    // View Binding for better XML layout handling
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout from activity_main.xml using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Now you can access views in your XML using binding.<view_id>
        binding.newButton.setOnClickListener {
            // Handle button clicks here
        }
    }
}
