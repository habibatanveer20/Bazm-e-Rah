package com.example.bazmeraah;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SupportChatActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private TextView emptyView;
    private ProgressBar progressBar;

    private DatabaseReference supportRef;
    private Query phoneQuery;
    private ValueEventListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_chat); // provided below

        recyclerView = findViewById(R.id.recyclerChat);
        emptyView = findViewById(R.id.emptyView);
        progressBar = findViewById(R.id.progressBar);

        adapter = new ChatAdapter(this, messages);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(false); // oldest at top, newest at bottom
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(adapter);

        FirebaseDatabase database = FirebaseDatabase.getInstance("https://bazm-e-rah-default-rtdb.firebaseio.com/");
        supportRef = database.getReference("SupportMessages");

        String myPhone = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getString("phone", null);

        if (myPhone == null || myPhone.trim().isEmpty()) {
            emptyView.setText("No phone found. Please send a support message first.");
            emptyView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            return;
        }

        attachListener(myPhone);
    }

    private void attachListener(String phone) {
        try {
            progressBar.setVisibility(View.VISIBLE);
            phoneQuery = supportRef.orderByChild("contact").equalTo(phone);

            listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    messages.clear();
                    if (!snapshot.exists()) {
                        emptyView.setText("No messages available.");
                        emptyView.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        adapter.updateList(messages);
                        return;
                    }

                    for (DataSnapshot child : snapshot.getChildren()) {
                        String key = child.getKey();
                        String contact = child.child("contact").getValue() != null ?
                                String.valueOf(child.child("contact").getValue()) : null;

                        String from = child.child("from").getValue() != null ?
                                String.valueOf(child.child("from").getValue()) : null;

                        // message might be in "message" or older "reply" field
                        String msg = null;
                        if (child.child("message").getValue() != null) {
                            msg = String.valueOf(child.child("message").getValue());
                        } else if (child.child("reply").getValue() != null) {
                            msg = String.valueOf(child.child("reply").getValue());
                        }

                        long ts = 0;
                        if (child.child("timestamp").getValue() != null) {
                            try {
                                ts = Long.parseLong(String.valueOf(child.child("timestamp").getValue()));
                            } catch (Exception e) {
                                // sometimes timestamp stored as text; attempt trimming then parse
                                try {
                                    ts = Long.parseLong(String.valueOf(child.child("timestamp").getValue()).trim());
                                } catch (Exception ex) {
                                    ts = 0;
                                }
                            }
                        }

                        ChatMessage m = new ChatMessage(key, contact, from, msg, ts);
                        messages.add(m);
                    }

                    // sort ascending by timestamp (older -> newer)
                    Collections.sort(messages, new Comparator<ChatMessage>() {
                        @Override
                        public int compare(ChatMessage o1, ChatMessage o2) {
                            // fallback to 0 if equal
                            long a = o1.timestamp;
                            long b = o2.timestamp;
                            return Long.compare(a, b);
                        }
                    });

                    adapter.updateList(messages);
                    progressBar.setVisibility(View.GONE);
                    emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);

                    // scroll to bottom (latest) after layout
                    if (!messages.isEmpty()) {
                        recyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                recyclerView.smoothScrollToPosition(messages.size() - 1);
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SupportChatActivity.this,
                            "DB error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            };

            phoneQuery.addValueEventListener(listener);

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (listener != null) {
                if (phoneQuery != null) phoneQuery.removeEventListener(listener);
                else if (supportRef != null) supportRef.removeEventListener(listener);
            }
        } catch (Exception ignored) {}
    }
}
