package io.github.jonathanlacabe.lightnote

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class PianoRollAdapter(
    private val rowCount: Int,
    private val columnCount: Int,
    private val cellWidth: Int,
    private val cellHeight: Int
):RecyclerView.Adapter<PianoRollAdapter.PianoRollViewHolder>(){

    //ViewHolder class to hold each grid box:
    class PianoRollViewHolder(val cellView: View) : RecyclerView.ViewHolder(cellView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PianoRollViewHolder{
        //Create new piano roll box (View) for each visible item:
        val cellView = View(parent.context).apply {
            val layoutParams = ViewGroup.LayoutParams(cellWidth, cellHeight)
            this.layoutParams = layoutParams
        }
        return PianoRollViewHolder(cellView)
    }

    override fun onBindViewHolder(holder: PianoRollViewHolder, position: Int){
        //Calculate the row and column from the position:
        val row = position/columnCount
        val col = position%columnCount

        //Checkered pattern in - Absolute work of genius. Hardest code of the entire app to write.
        if((row % 2 == 0 && col % 2 != 0) || (row % 2 != 0 && col % 2 == 0)) {
            holder.cellView.setBackgroundColor(Color.parseColor("#3A3A3A"))//Lighter Gray
        }else{
            holder.cellView.setBackgroundColor(Color.parseColor("#2E2E2E"))//Darker gray
        }
    }

    override fun getItemCount(): Int {
        //Return the total number of boxes (row * col)
        return rowCount*columnCount
    }
}
