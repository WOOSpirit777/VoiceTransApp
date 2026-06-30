package com.aibabel.voicetrans.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aibabel.voicetrans.R;
import com.aibabel.voicetrans.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 聊天消息适配器
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> messages = new ArrayList<>();
    private OnItemClickListener listener;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public interface OnItemClickListener {
        void onItemClick(ChatMessage message);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastMessage(ChatMessage message) {
        int position = messages.size() - 1;
        if (position >= 0 && messages.get(position).getId().equals(message.getId())) {
            messages.set(position, message);
            notifyItemChanged(position);
        }
    }

    public void updateMessage(String id, String sourceText, String targetText) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(id)) {
                ChatMessage msg = messages.get(i);
                if (sourceText != null) {
                    msg.setSourceText(sourceText);
                }
                if (targetText != null) {
                    msg.setTargetText(targetText);
                }
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private TextView tvSourceLang;
        private TextView tvSourceText;
        private TextView tvSourceTime;
        private TextView tvTargetLang;
        private TextView tvTargetText;
        private ImageView ivPlayAudio;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSourceLang = itemView.findViewById(R.id.tvSourceLang);
            tvSourceText = itemView.findViewById(R.id.tvSourceText);
            tvSourceTime = itemView.findViewById(R.id.tvSourceTime);
            tvTargetLang = itemView.findViewById(R.id.tvTargetLang);
            tvTargetText = itemView.findViewById(R.id.tvTargetText);
            ivPlayAudio = itemView.findViewById(R.id.ivPlayAudio);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(messages.get(position));
                }
            });
        }

        public void bind(ChatMessage message) {
            // 源语言
            tvSourceLang.setText(message.getSourceLangDisplay());
            tvSourceText.setText(message.getSourceText() != null ? message.getSourceText() : "等待识别...");
            tvSourceTime.setText(timeFormat.format(new Date(message.getTimestamp())));

            // 目标语言
            tvTargetLang.setText(message.getTargetLangDisplay());
            tvTargetText.setText(message.getTargetText() != null ? message.getTargetText() : "等待翻译...");

            // 设置文本颜色表示加载状态
            if (message.getSourceText() == null) {
                tvSourceText.setTextColor(0xFF9E9E9E);
            } else {
                tvSourceText.setTextColor(0xFF212121);
            }

            if (message.getTargetText() == null) {
                tvTargetText.setTextColor(0xFF9E9E9E);
            } else {
                tvTargetText.setTextColor(0xFF424242);
            }
        }
    }
}
