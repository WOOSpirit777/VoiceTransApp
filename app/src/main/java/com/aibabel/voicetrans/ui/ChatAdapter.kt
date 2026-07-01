package com.aibabel.voicetrans.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private var listener: OnItemClickListener? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    interface OnItemClickListener {
        fun onItemClick(message: ChatMessage)
        fun onSourceAudioClick(message: ChatMessage)
        fun onTargetAudioClick(message: ChatMessage)
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(message: ChatMessage) {
        val position = messages.size - 1
        if (position >= 0 && messages[position].id == message.id) {
            messages[position] = message
            notifyItemChanged(position)
        }
    }

    fun updateMessage(id: String, sourceText: String?, targetText: String?) {
        for (i in messages.indices) {
            if (messages[i].id == id) {
                val msg = messages[i]
                if (sourceText != null) {
                    msg.sourceText = sourceText
                }
                if (targetText != null) {
                    msg.targetText = targetText
                }
                notifyItemChanged(i)
                break
            }
        }
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutSource: View = itemView.findViewById(R.id.layoutSource)
        private val layoutTarget: View = itemView.findViewById(R.id.layoutTarget)
        private val tvSourceLang: TextView = itemView.findViewById(R.id.tvSourceLang)
        private val tvSourceText: TextView = itemView.findViewById(R.id.tvSourceText)
        private val tvSourceTime: TextView = itemView.findViewById(R.id.tvSourceTime)
        private val ivPlaySource: ImageView = itemView.findViewById(R.id.ivPlaySource)
        private val tvTargetLang: TextView = itemView.findViewById(R.id.tvTargetLang)
        private val tvTargetText: TextView = itemView.findViewById(R.id.tvTargetText)
        private val ivPlayTarget: ImageView = itemView.findViewById(R.id.ivPlayTarget)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener!!.onItemClick(messages[position])
                }
            }

            layoutSource.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener!!.onSourceAudioClick(messages[position])
                }
            }

            layoutTarget.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener!!.onTargetAudioClick(messages[position])
                }
            }
        }

        fun bind(message: ChatMessage) {
            tvSourceLang.text = message.sourceLangDisplay
            tvSourceText.text = message.sourceText ?: "等待识别..."
            tvSourceTime.text = timeFormat.format(Date(message.timestamp))

            tvTargetLang.text = message.targetLangDisplay
            tvTargetText.text = message.targetText ?: "等待翻译..."

            ivPlaySource.visibility =
                if (message.sourceAudioData != null && message.sourceAudioData!!.isNotEmpty())
                    View.VISIBLE else View.GONE

            ivPlayTarget.visibility =
                if (message.ttsAudioData != null && message.ttsAudioData!!.isNotEmpty())
                    View.VISIBLE else View.GONE

            if (message.sourceText == null) {
                tvSourceText.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_hint))
            } else {
                tvSourceText.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
            }

            if (message.targetText == null) {
                tvTargetText.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_hint))
            } else {
                tvTargetText.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            }
        }
    }
}
