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
) : RecyclerView.Adapter<PianoRollAdapter.PianoRollViewHolder>() {

    // ViewHolder class to hold each grid cell
    class PianoRollViewHolder(val cellView: View) : RecyclerView.ViewHolder(cellView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PianoRollViewHolder {
        // Create a new grid cell (View) for each visible item
        val cellView = View(parent.context).apply {
            val layoutParams = ViewGroup.LayoutParams(cellWidth, cellHeight)
            this.layoutParams = layoutParams
        }
        return PianoRollViewHolder(cellView)
    }

    override fun onBindViewHolder(holder: PianoRollViewHolder, position: Int) {
        // Calculate the row and column from the position
        val row = position / columnCount
        val col = position % columnCount

        // Adjust the checkered pattern to alternate consistently
        if ((row % 2 == 0 && col % 2 != 0) || (row % 2 != 0 && col % 2 == 0)) {
            holder.cellView.setBackgroundColor(Color.parseColor("#3A3A3A")) // Lighter gray
        } else {
            holder.cellView.setBackgroundColor(Color.parseColor("#2E2E2E")) // Darker gray
        }
    }


    override fun getItemCount(): Int {
        // Return the total number of cells (rows * columns)
        return rowCount * columnCount
    }
}
