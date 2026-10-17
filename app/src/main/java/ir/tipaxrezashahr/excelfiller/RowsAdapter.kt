package ir.tipaxrezashahr.excelfiller

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RowsAdapter(
    private val rows: MutableList<ShipmentRow>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<RowsAdapter.RowViewHolder>() {

    class RowViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.rowName)
        val details: TextView = itemView.findViewById(R.id.rowDetails)
        val deleteButton: ImageButton = itemView.findViewById(R.id.rowDeleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val row = rows[position]
        holder.name.text = "${position + 1}. ${row.recipientName}"
        holder.details.text = "${row.city} — ${row.address} — ${row.phone}"
        holder.deleteButton.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                onDelete(adapterPos)
            }
        }
    }

    override fun getItemCount(): Int = rows.size
}
