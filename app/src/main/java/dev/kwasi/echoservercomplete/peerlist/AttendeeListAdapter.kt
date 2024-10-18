package dev.kwasi.echoservercomplete.peerlist

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.R

class AttendeeListAdapter(private val iFaceImpl: AttendeeListAdapterInterface) : RecyclerView.Adapter<AttendeeListAdapter.ViewHolder>() {
    private val attendeesList: MutableList<String> = mutableListOf() // Change to hold student IDs or names

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.attendeeTV)
        val askQuestionButton: Button = itemView.findViewById(R.id.attendeeBTN)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.attendee_item, parent, false) // Inflate your item layout for attendees
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val studentId = attendeesList[position]

        holder.titleTextView.text = studentId // Set the student ID or name to the TextView

        holder.askQuestionButton.setOnClickListener {
            iFaceImpl.onAttendeeClicked(studentId) // Trigger the interface function with the clicked attendee's ID
        }
    }

    override fun getItemCount(): Int {
        return attendeesList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newAttendeesList: Collection<String>) {
        attendeesList.clear()
        attendeesList.addAll(newAttendeesList)
        Log.d("AttendeeListAdapter", "Updated list: $attendeesList")
        notifyDataSetChanged()
    }
}

// Define an interface to handle clicks on attendees
interface AttendeeListAdapterInterface {
    fun onAttendeeClicked(studentId: String)
}