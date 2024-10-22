package io.github.jonathanlacabe.lightnote

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.jonathanlacabe.lightnote.databinding.ActivityFaqBinding

class FaqActivity : AppCompatActivity() {
    //View Binding for better XML layout handling
    private lateinit var binding: ActivityFaqBinding

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        //Inflate the layout from activity_faq.xml using View Binding:
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Set up back button to return to main page:
        binding.backButton.setOnClickListener{
            finish() //Closes the F.A.Q. activity and returns to the main page
        }
    }
}
