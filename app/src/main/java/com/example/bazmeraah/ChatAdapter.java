package com.example.bazmeraah;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Date;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ADMIN = 1;

    private List<ChatMessage> items;
    private final Context context; // <--- store context

    public ChatAdapter(Context context, List<ChatMessage> items) {
        this.context = context;
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage m = items.get(position);
        if (m.from != null && m.from.equalsIgnoreCase("admin")) {
            return TYPE_ADMIN;
        } else {
            return TYPE_USER;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADMIN) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_chat_item_admin, parent, false);
            return new AdminHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_chat_item_user, parent, false);
            return new UserHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = items.get(position);
        String text = m.message != null ? m.message : "";

        if (holder instanceof AdminHolder) {
            ((AdminHolder) holder).txtMessage.setText(text);
            ((AdminHolder) holder).txtTime.setText(formatTs(m.timestamp));
        } else {
            ((UserHolder) holder).txtMessage.setText(text);
            ((UserHolder) holder).txtTime.setText(formatTs(m.timestamp));
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public void updateList(List<ChatMessage> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    // Use Activity/Context to get time format - avoid null context
    private String formatTs(long ts) {
        try {
            if (ts <= 0) return "";
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
            Date d = new Date(ts);
            return timeFormat.format(d);
        } catch (Exception e) {
            // fallback simple formatting
            try {
                if (ts <= 0) return "";
                Date d = new Date(ts);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a");
                return sdf.format(d);
            } catch (Exception ex) {
                return "";
            }
        }
    }

    static class UserHolder extends RecyclerView.ViewHolder {
        TextView txtMessage, txtTime;
        UserHolder(View v) {
            super(v);
            txtMessage = v.findViewById(R.id.txtUserMessage);
            txtTime = v.findViewById(R.id.txtUserTime);
        }
    }

    static class AdminHolder extends RecyclerView.ViewHolder {
        TextView txtMessage, txtTime;
        AdminHolder(View v) {
            super(v);
            txtMessage = v.findViewById(R.id.txtAdminMessage);
            txtTime = v.findViewById(R.id.txtAdminTime);
        }
    }
}
