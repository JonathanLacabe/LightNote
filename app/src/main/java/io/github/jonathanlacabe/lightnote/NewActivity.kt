package io.github.jonathanlacabe.lightnote

import android.os.Bundle
import androidx.activity.ComponentActivity

class NewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new)
    }
}
