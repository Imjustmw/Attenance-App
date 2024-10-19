package dev.kwasi.echoservercomplete.chatlist

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.R
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()) // Change the pattern as needed
    return dateFormat.format(Date(timestamp))
}

class ChatListAdapter : RecyclerView.Adapter<ChatListAdapter.ViewHolder>(){
    private val chatList:MutableList<ContentModel> = mutableListOf()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageView: TextView = itemView.findViewById(R.id.messageTextView)
        val timeStamp: TextView = itemView.findViewById(R.id.messageTimeStamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chatList[position]
        val messageLayout = (holder.messageView.parent as RelativeLayout)

        if (chat.senderIp == "192.168.49.1") {
            messageLayout.gravity = Gravity.END
            holder.messageView.setBackgroundResource(R.color.md_theme_secondaryContainer)
        } else {
            messageLayout.gravity = Gravity.START
            holder.messageView.setBackgroundResource(R.color.md_theme_primaryContainer)
        }


        holder.messageView.text = chat.message
        holder.timeStamp.text = formatTimestamp(chat.timestamp)

    }

    override fun getItemCount(): Int {
        return chatList.size
    }

    fun addItemToEnd(contentModel: ContentModel){
        chatList.add(contentModel)
        notifyItemInserted(chatList.size)
    }


    @SuppressLint("NotifyDataSetChanged")
    fun updateChat(newMessages: List<ContentModel>) {
        chatList.clear()
        chatList.addAll(newMessages)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearChat() {
        chatList.clear()
        notifyDataSetChanged()
    }
}