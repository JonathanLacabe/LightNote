package io.github.jonathanlacabe.lightnote

import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.view.Gravity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jonathanlacabe.lightnote.databinding.ActivityNewBinding
import jp.kshoji.javax.sound.midi.MidiSystem
import org.billthefarmer.mididriver.MidiDriver

class NewActivity : ComponentActivity(){
    private lateinit var binding: ActivityNewBinding
    private var currentFileName: String = "default" //Value will only appear in exceptions.
    private var isPlaying = false // Initialize isPlaying to track playback state
    private lateinit var midiPlaybackHandler: MidiPlaybackHandler
    private var midiFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        binding = ActivityNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateFileNameTextView(currentFileName)

        midiPlaybackHandler = MidiPlaybackHandler(contentResolver)
        Log.d("MIDI_DRIVER", "MIDI Driver initialized")

        setupPianoKeys()
        setupPianoRoll()

        //Synchronized vertical scrolling between the piano keys and the piano roll grid:
        binding.pianoKeysScrollView.setOnScrollChangeListener{ _, _, scrollY, _, _ ->
            //Scroll the RecyclerView in sync with the piano keys
            binding.pianoRollRecyclerView.scrollBy(0, scrollY)
        }

/* Since RecyclerView itself doesn't have an OnScrollChangeListener like ScrollView,
    the synchronization is set up in reverse using the LayoutManager of the RecyclerView.*/
        binding.pianoRollRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener(){
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int){
                // Sync the scroll of piano keys ScrollView
                binding.pianoKeysScrollView.scrollBy(0, dy)
            }
        })

        //Check if a MIDI file URI was passed from MainActivity
        intent.getStringExtra("MIDI_FILE_URI")?.let{ uriString ->
            val uri = Uri.parse(uriString)
            midiFileUri = uri

            //Load the MIDI file and update the duration
            midiPlaybackHandler.getMidiDuration(uri)?.let{ durationMillis ->
                binding.durationValue.text = formatDuration(durationMillis)
            }

            //Update the file name for the UI
            var fileName: String? =null
            contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()){
                    fileName = cursor.getString(0)?.substringBeforeLast('.') ?: "Unknown File"
                }
            }
            updateFileNameTextView(fileName ?: "Unknown File")
        }

        //Check if a MIDI file resource ID was passed from MainActivity
        intent.getIntExtra("MIDI_FILE_RESOURCE_ID", -1).takeIf{ it != -1 }?.let{ resourceId ->
            //Load the pre-imported MIDI file from raw resources
            val uri = Uri.parse("android.resource://${packageName}/$resourceId")
            midiFileUri = uri
            loadMidiFile(uri)//Load and display the pre-imported MIDI file
        }

        //upper space TextView show a toast with the full name on hold-down
        binding.fileNameTextView.setOnLongClickListener{
            Toast.makeText(this, currentFileName, Toast.LENGTH_SHORT).show()
            true
        }

        binding.makeLightsButton.setOnClickListener{
            //Toast.makeText(this, "For Arduino functionality, see Help -> Github.", Toast.LENGTH_SHORT).show()
            Toast.makeText(this, "Thank you for using the app!", Toast.LENGTH_SHORT).show()
        }

        //File
        binding.fileButton.setOnClickListener{ showFileMenu() }

        //View
        binding.viewButton.setOnClickListener{ showViewMenu() }

        //Help
        binding.helpButton.setOnClickListener{ showHelpMenu() }

        //Play button functionality:
        binding.playButton.setOnClickListener{
            if (!isPlaying){
                isPlaying = true
                midiFileUri?.let{ uri ->
                    midiPlaybackHandler.resumePlayback(uri)//Resume from last paused tick, or start if not paused.
                }
            }
        }

        //Pause button functionality:
        binding.pauseButton.setOnClickListener{
            if (isPlaying){
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
        }

        //Rewind button functionality:
        binding.rewindButton.setOnClickListener{
            isPlaying=true//Set to playing as rewind restarts playback
            midiFileUri?.let{ uri ->
                midiPlaybackHandler.stopPlayback() //Stop current playback and reset
                midiPlaybackHandler.loadAndPlayMidiFile(uri)//Restart from the beginning
            }
        }

        //Panic button functionality:
        binding.panicButton.setOnClickListener{
            Log.d("PanicButton", "Panic button clicked, calling rewind twice with delay")

            //First call to rewind
            binding.rewindButton.performClick()
            Toast.makeText(this, "Panic Button: Errors fixed!", Toast.LENGTH_SHORT).show()

            //Second call to rewind after 0.3 seconds - otherwise, it itself will cause errors
            binding.rewindButton.postDelayed({
                binding.rewindButton.performClick()
            }, 300)
        }

        //Duration button long-click - shows toast, and pause:
        binding.durationValue.setOnLongClickListener{
            if (isPlaying){
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
            Toast.makeText(this, "Time Duration", Toast.LENGTH_SHORT).show()
            true
        }

        //time update callback:
        midiPlaybackHandler.setOnTimeUpdateCallback{ elapsedMillis ->
            val elapsedSeconds = (elapsedMillis / 1000).toInt()
            val elapsedTimeFormatted = String.format("%02d:%02d:%02d", elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60)
            runOnUiThread{
                binding.timeValue.text = elapsedTimeFormatted
            }
        }

        //Instrument update callback:
        midiPlaybackHandler.setOnInstrumentUpdateCallback{ instrumentName ->
            runOnUiThread{
                Log.d("MIDI_PLAYER", "Instrument update callback fired. Instrument: $instrumentName")

                //Always keep the track name as "Track" and only update the instrument. I think I made this change everywhere, but just to be sure.
                if (binding.trackName.text != "Track"){
                    binding.trackName.text = "Track"
                }

                //Set instrument text only if it is different, to prevent unnecessary resets:
                if (binding.instrument.text != instrumentName){
                    binding.instrument.text = instrumentName
                }
            }
        }

        //Instrument button long-click to show toast and pause:
        binding.instrument.setOnLongClickListener{
            if (isPlaying){
                isPlaying=false
                midiPlaybackHandler.pausePlayback()
            }
            Toast.makeText(this, binding.instrument.text, Toast.LENGTH_SHORT).show()
            true
        }

        //Channel update callback:
        midiPlaybackHandler.setOnChannelUpdateCallback{ channelNumber ->
            runOnUiThread{
                Log.d("MIDI_PLAYER", "Channel update callback fired. Channel: $channelNumber")
                binding.channel.text = channelNumber.toString()
            }
        }

        //Channel button long-click: toast and channel number.
        binding.channel.setOnLongClickListener{
            if (isPlaying){
                isPlaying= false
                midiPlaybackHandler.pausePlayback()
            }
            Toast.makeText(this, "Channel: ${binding.channel.text}", Toast.LENGTH_SHORT).show()
            true
        }


        binding.type.setOnLongClickListener{
            if (isPlaying){
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
            Toast.makeText(this, binding.type.text, Toast.LENGTH_SHORT).show()
            true
        }

        //type update callback:
        midiPlaybackHandler.setOnTypeUpdateCallback{ trackType ->
            runOnUiThread{
                binding.type.text = trackType
            }
        }

        //Playback Completion Callback:
        midiPlaybackHandler.setOnPlaybackCompleteCallback{
            runOnUiThread{
                binding.timeValue.text = binding.durationValue.text
            }
        }

        //Track button functionality (change from long to nrmal click):
        findViewById<TextView>(R.id.trackName).setOnClickListener {
            //Pause the playback before showing the track selection menu:
            if (isPlaying) {
                midiPlaybackHandler.pausePlayback()
            }
            showTrackSelectionMenu() //Show track selection menu
        }

    }

    private fun setupPianoRoll(){
        val pianoRollRecyclerView = findViewById<RecyclerView>(R.id.pianoRollRecyclerView)

        //Convert dp to pixels:
        val displayMetrics = resources.displayMetrics
        val cellWidthPx = (45 * displayMetrics.density).toInt()  // 45dp to pixels
        val cellHeightWithMarginPx = (45 * displayMetrics.density).toInt() + (3 * displayMetrics.density).toInt() //45dp height + margin (because the piano keys have borders that need to be accounted for)

        // GridLayoutManager that scrolls horizontally and handles column chunking
        val gridLayoutManager = GridLayoutManager(this, 72, GridLayoutManager.HORIZONTAL, false)
        pianoRollRecyclerView.layoutManager= gridLayoutManager

        // Adapter for the piano roll grid with chunking
        val pianoRollAdapter = PianoRollAdapter(
            rowCount = 72,                       //72 rows to match the number of keys.
            columnCount = 72,                    //In v2: minimum of 1728 columns off the bat.
            cellWidth = cellWidthPx,             //Each cell is 45dp wide.
            cellHeight = cellHeightWithMarginPx  //Each cell is 45dp tall (plus a little).
        )
        pianoRollRecyclerView.adapter = pianoRollAdapter

        //Sync vertical scrolling between piano keys and the grid -
        val pianoKeysScrollView = findViewById<ScrollView>(R.id.pianoKeysScrollView)
        val verticalScrollView = findViewById<ScrollView>(R.id.verticalScrollView)

        //Sync vertical scrolling between the two views:
        verticalScrollView.setOnScrollChangeListener{ _, _, scrollY, _, _ ->
            pianoKeysScrollView.scrollTo(0, scrollY)
            pianoRollRecyclerView.scrollBy(0, scrollY)
        }

        pianoKeysScrollView.isHorizontalScrollBarEnabled = false//Disable horizontal scrolling for piano keys - should NEVER happen.
    }


    //Pressing on the note makes a little piano sound correct to the note. Quality of life, really.
    private fun setupPianoKeys(){
        val pianoKeysScrollView = findViewById<ScrollView>(R.id.pianoKeysScrollView)

        //Set onClickListeners for each key (C2 to C8, 36-108) by using their ids.
        ///Testing proved that the lowest note playable was C2, and the highest note was C8, so I worked off of that.
        val pianoKeys = listOf(
            R.id.key_C2, R.id.key_CSharp2, R.id.key_D2, R.id.key_DSharp2, R.id.key_E2, R.id.key_F2,
            R.id.key_FSharp2, R.id.key_G2, R.id.key_GSharp2, R.id.key_A2, R.id.key_ASharp2, R.id.key_B2,
            R.id.key_C3, R.id.key_CSharp3, R.id.key_D3, R.id.key_DSharp3, R.id.key_E3, R.id.key_F3,
            R.id.key_FSharp3, R.id.key_G3, R.id.key_GSharp3, R.id.key_A3, R.id.key_ASharp3, R.id.key_B3,
            R.id.key_C4, R.id.key_CSharp4, R.id.key_D4, R.id.key_DSharp4, R.id.key_E4, R.id.key_F4,
            R.id.key_FSharp4, R.id.key_G4, R.id.key_GSharp4, R.id.key_A4, R.id.key_ASharp4, R.id.key_B4,
            R.id.key_C5, R.id.key_CSharp5, R.id.key_D5, R.id.key_DSharp5, R.id.key_E5, R.id.key_F5,
            R.id.key_FSharp5, R.id.key_G5, R.id.key_GSharp5, R.id.key_A5, R.id.key_ASharp5, R.id.key_B5,
            R.id.key_C6, R.id.key_CSharp6, R.id.key_D6, R.id.key_DSharp6, R.id.key_E6, R.id.key_F6,
            R.id.key_FSharp6, R.id.key_G6, R.id.key_GSharp6, R.id.key_A6, R.id.key_ASharp6, R.id.key_B6,
            R.id.key_C7, R.id.key_CSharp7, R.id.key_D7, R.id.key_DSharp7, R.id.key_E7, R.id.key_F7,
            R.id.key_FSharp7, R.id.key_G7, R.id.key_GSharp7, R.id.key_A7, R.id.key_ASharp7, R.id.key_B7, R.id.key_C8
        )

        //Assign onClickListeners to each key:
        for (keyId in pianoKeys){
            val keyView = findViewById<TextView>(keyId)
            keyView.setOnClickListener{
                val note = keyView.text.toString() //Get the note from the correct TextView in activity_new
                if(isPlaying){
                    isPlaying = false
                    midiPlaybackHandler.pausePlayback()
                }
                playPianoSound(note)//Play sound of tapped key
            }
        }

        //Scroll to center on C5 after the view has been laid out:
        pianoKeysScrollView.post{
            val totalHeight = pianoKeysScrollView.getChildAt(0).height
            val positionToScroll = totalHeight * (C8 - C5) / (C8 - C2)
            pianoKeysScrollView.scrollTo(0, positionToScroll)
        }
    }

    //Play the piano note sound using midi:
    private fun playPianoSound(note: String) {
        val midiNote = noteToMidiNumber(note)
        if(isPlaying){
            isPlaying = false
            midiPlaybackHandler.pausePlayback()
        }

        //Send a "note-on" command using MidiDriver:
        val midiDriver = MidiDriver.getInstance()
        midiDriver.start()

        //Send the "note-on" message with reduced velocity (0x40 for softer volume, was too loud before)
        val noteOnMessage = byteArrayOf(0x90.toByte(), midiNote.toByte(), 0x40.toByte())
        midiDriver.write(noteOnMessage)

        //Stop the note after a short delay (for simulation purposes)
        binding.pianoKeysScrollView.postDelayed({
            val noteOffMessage = byteArrayOf(0x80.toByte(), midiNote.toByte(), 0x00.toByte())//0x00 velocity for "note-off"
            midiDriver.write(noteOffMessage)
            midiDriver.stop()
        }, 500)//Stops the note after 500ms - CHANGE FOR TESTING
    }

    //Map note names (e.g., "C4", "D#5") to midi note numbers:
    private fun noteToMidiNumber(note: String): Int{
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val noteName = note.substring(0, note.length - 1)
        val octave = note.last().toString().toInt()
        val noteIndex = noteNames.indexOf(noteName)
        return (octave+1)*12+noteIndex
    }

    private fun updateFileNameTextView(fileName: String){
        //Update the TextView with the new file name:
        currentFileName = fileName.substringBeforeLast(".")//Remove extension.
        binding.fileNameTextView.text = currentFileName

        //enable marquee scrolling if the text is too long:
        binding.fileNameTextView.isSelected = true
    }

    private fun showFileMenu(){
        val fileButton = findViewById<TextView>(R.id.fileButton)
        //Inflate file_menu layout:
        val popupView = layoutInflater.inflate(R.layout.file_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.showAsDropDown(fileButton, -24, 0)//xoff experimentally set: change if anything else looks better

        //popup window background, outside tap handling
        popupWindow.isOutsideTouchable = true

        //Position the popup window just below the File button
        popupWindow.showAsDropDown(binding.fileButton, 0, 8)//yoff experimentally set: change if anything else looks better

        //"Open..." and "Exit" click listeners in the popup window
        val openOption = popupView.findViewById<TextView>(R.id.openOption)
        val exitOption = popupView.findViewById<TextView>(R.id.exitOption)

        openOption.setOnClickListener{
            if(isPlaying){//Pause - "Reactive Feedback system", where if you type anything it pauses.
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
            popupWindow.dismiss()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
                type = "audio/midi"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/x-midi"))
            }
            startActivityForResult(intent, REQUEST_CODE_OPEN_MIDI)
        }

        exitOption.setOnClickListener{
            if(isPlaying){//Pause, of course.
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
            popupWindow.dismiss()
            finish()
        }
    }

    private fun showViewMenu(){
        val viewButton = findViewById<TextView>(R.id.viewButton)
        //Inflate view_menu layout:
        val popupView = layoutInflater.inflate(R.layout.view_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.showAsDropDown(viewButton, -37, 0)//Experimentally set, changes if needed.

        //Click "Piano Roll Editor". In v2, staff editor will be enabled.
        val pianoRollEditor = popupView.findViewById<TextView>(R.id.pianoRollEditor)
        pianoRollEditor.setOnClickListener {
            if (isPlaying) {
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }

            pianoRollEditor.alpha = 0.5f
            pianoRollEditor.postDelayed({ pianoRollEditor.alpha = 1f }, 100)
            popupWindow.dismiss()
        }

        //The Staff Editor option is grayed out until v2
    }

    private fun showHelpMenu(){
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

        //Open tutorial sequence (V2V2V2V2V2V2 [[[[[[[)
        tutorialOption.setOnClickListener {
            if (isPlaying) {
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
            popupWindow.dismiss()
            startTutorialSequence()
        }

        //Open GitHub documentation link
        githubOption.setOnClickListener {
            if (isPlaying) {
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
            popupWindow.dismiss()
            val githubUri = Uri.parse("https://github.com/JonathanLacabe/LightNote/blob/master/README.md")
            val githubIntent = Intent(Intent.ACTION_VIEW, githubUri)
            startActivity(githubIntent)
        }

        //Open YouTube playlist link
        youtubeOption.setOnClickListener {
            if (isPlaying) {
                isPlaying = false
                midiPlaybackHandler.pausePlayback()
            }
            popupWindow.dismiss()
            val youtubeUri = Uri.parse("https://www.youtube.com/playlist?list=PLS8kBPvyvuR9t8YuetX402X1M1pYZUqTB")
            val youtubeIntent = Intent(Intent.ACTION_VIEW, youtubeUri)
            startActivity(youtubeIntent)
        }
    }

    /*Extract the title of the midi file imported by user. Without ContentResolver, the straight mri will be produced.
      Differences in URI structure across devices can cause issues - "Unknown File" occurs in exception cases.
      This function tries two methods to get the title - the first works sometimes, the second catches it every
      other time, like an exception but better.*/
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_MIDI && resultCode == RESULT_OK){
            data?.data?.let{ uri ->
                //Reset playback state to clear old data and set defaults:
                midiPlaybackHandler.resetPlaybackState()

                //Assign selected file URI to midiFileUri:
                midiFileUri = uri

                //Update duration and display:
                midiPlaybackHandler.getMidiDuration(uri)?.let { durationMillis ->
                    binding.durationValue.text = formatDuration(durationMillis)
                }
                var fileName: String? = null

                //1. Attempt to retrieve display name using ContentResolver: Should work, but not always!
                contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()){
                        fileName=cursor.getString(0)?.substringBeforeLast('.') //Removes extension, if found
                    }
                }

                //2. Fallback: If display name is still null, try getting filename from URI path. This solves the issue! Party!
                if(fileName == null){
                    fileName = uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown File"
                }

                //Update the displayed file name.
                updateFileNameTextView(fileName!!)
            }
        }
    }

    private fun loadMidiFile(uri: Uri){
        midiPlaybackHandler.getMidiDuration(uri)?.let{ durationMillis ->
            binding.durationValue.text = formatDuration(durationMillis)
        }
        updateFileNameTextView("Debussy - Arabesque 1")//Set the preloaded file name. Credit given to creator in credits [[[[.
    }

    private fun formatDuration(durationMillis: Long): String{
        val totalSeconds = (durationMillis+500) / 1000//Round to nearest second
        val hours = totalSeconds/3600
        val minutes = (totalSeconds%3600) / 60
        val seconds = totalSeconds%60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds) //perfectly formatted, as all things should be
    }

    //Selection menu for tracks
    private fun showTrackSelectionMenu(){
        val trackNames = midiPlaybackHandler.tracks.map{ it.name }.toTypedArray()

        //Create custom ArrayAdapter with a custom layout:
        val adapter=object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, trackNames){
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View{
                val view = super.getView(position, convertView, parent)

                //Apply the custom style to each item:
                view.setBackgroundColor(resources.getColor(R.color.dialog_list_background))//custom background color
                (view as TextView).setTextColor(resources.getColor(R.color.dialog_list_text))//custom text color
                return view
            }
        }

        AlertDialog.Builder(this, R.style.LightDialogTheme)
            .setTitle("Select Track")
            .setAdapter(adapter){ _, which ->
                midiPlaybackHandler.changeTrack(which)//This will trigger instrument and channel updates via callback.

                //binding.pianoKeysScrollView.postDelayed({
                    if(isPlaying){
                        midiFileUri?.let{ uri ->
                            midiPlaybackHandler.resumePlayback(uri) //delay done for testing
                        }
                    }
                //}, 200)
            }
            .setCancelable(true)
            .create()
            .show()
    }



    override fun onDestroy(){
        super.onDestroy()
        midiPlaybackHandler.stopPlayback()
    }

    private fun startTutorialSequence(){
        //v2: tutorial sequence logic, implement the tutorial popups or overlays here
    }

    companion object{
        const val REQUEST_CODE_OPEN_MIDI = 1

        //midi note numbers used in the piano key and roll functions:
        const val C2 = 36
        const val C5 = 72
        const val C8 = 108
    }
}
