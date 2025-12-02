package com.example.bazmeraah;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
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
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SupportChatActivity extends AppCompatActivity {

    private static final String TAG = "SupportChat";

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private TextView emptyView;
    private ProgressBar progressBar;

    private DatabaseReference supportRef;
    private Query phoneQuery;
    private ValueEventListener listener;

    // TTS & voice
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean isUrdu = false;
    private boolean isVoiceActive = true;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AtomicBoolean waitingForNavResponse = new AtomicBoolean(false);
    private String currentNormalizedPhone = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_chat);

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

        Log.d(TAG, "Loaded phone from prefs (raw): " + myPhone);

        if (myPhone == null || myPhone.trim().isEmpty()) {
            emptyView.setText("No phone found. Please send a support message first.");
            emptyView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            return;
        }

        // normalize to 03... zero format so it matches SupportMessages.contact
        String normalized = toZeroFormat(myPhone);
        currentNormalizedPhone = normalized;
        Log.d(TAG, "Normalized phone to zero-format: " + normalized);

        // load language preference
        isUrdu = getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("language_urdu", false);
        setupTTS();

        attachListener(normalized);
    }

    /**
     * Convert various phone formats to 0xxxxxxxxxx (03...) format when possible.
     */
    private String toZeroFormat(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("\\s+", "").replaceAll("[^0-9\\+]", "");
        if (phone.isEmpty()) return null;

        if (phone.startsWith("+92") && phone.length() >= 4) {
            String rest = phone.substring(3);
            if (rest.startsWith("0")) rest = rest.substring(1);
            return "0" + rest;
        }

        if (phone.startsWith("92") && phone.length() >= 3) {
            String rest = phone.substring(2);
            if (rest.startsWith("0")) rest = rest.substring(1);
            return "0" + rest;
        }

        if (phone.length() == 10 && phone.startsWith("3")) {
            return "0" + phone;
        }

        if (phone.startsWith("0") && phone.length() == 11) {
            return phone;
        }

        return phone;
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.US);
            }
        });
    }

    private void attachListener(String phone) {
        try {
            progressBar.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);

            if (phone == null || phone.trim().isEmpty()) {
                progressBar.setVisibility(View.GONE);
                emptyView.setText("No valid phone to query.");
                emptyView.setVisibility(View.VISIBLE);
                return;
            }

            Log.d(TAG, "Attaching listener for contact = " + phone);
            phoneQuery = supportRef.orderByChild("contact").equalTo(phone);

            listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    messages.clear();
                    Log.d(TAG, "onDataChange exists=" + snapshot.exists() + " children=" + snapshot.getChildrenCount());

                    if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
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
                        recyclerView.post(() -> recyclerView.smoothScrollToPosition(messages.size() - 1));
                    }

                    // announce latest admin reply if any (and not announced before)
                    announceAdminReplyIfAny(messages, currentNormalizedPhone);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "DB error: " + error.getMessage());
                    Toast.makeText(SupportChatActivity.this,
                            "DB error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            };

            phoneQuery.addValueEventListener(listener);

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Log.e(TAG, "attachListener exception: " + e.getMessage(), e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Find the newest message that likely is an admin reply and announce it via TTS if not announced before.
     * We consider a message as "reply" if its 'from' equals "admin" OR its msg exists and timestamp is newest.
     * We store last announced timestamp in SharedPreferences key: lastReplyTs_{phone}
     */
    private void announceAdminReplyIfAny(List<ChatMessage> list, String phone) {
        if (list == null || list.isEmpty() || phone == null) return;

        // find latest message (we already sorted ascending, so last is newest)
        ChatMessage latest = list.get(list.size() - 1);
        if (latest == null) return;

        long latestTs = latest.timestamp;
        if (latestTs <= 0) return; // no timestamp, skip

        // load last announced ts
        String key = "lastReplyTs_" + phone;
        long lastAnnounced = getSharedPreferences("UserPrefs", MODE_PRIVATE).getLong(key, 0);

        // Only consider if this message is from admin or reply text exists and is newer than stored
        boolean looksLikeAdmin = false;
        if (latest.from != null && latest.from.equalsIgnoreCase("admin")) looksLikeAdmin = true;
        if (latest.message != null && latest.message.trim().length() > 0 && looksLikeAdmin) {
            // ok
        } else if (latest.message != null && latest.message.trim().length() > 0 && !looksLikeAdmin) {
            // there might be cases where reply stored in 'reply' field mapped to message — still announce
            // we'll still allow announcing if timestamp is newer than lastAnnounced
            // (this is permissive; adjust if you want stricter detection)
        } else {
            // nothing to announce
            return;
        }

        if (latestTs > lastAnnounced) {
            // Update stored before speaking to avoid duplicates if activity pauses/resumes
            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().putLong(key, latestTs).apply();

            String toSpeak = isUrdu
                    ? "ایڈمن سے پیغام آیا: " + latest.message
                    : "You have a reply from admin: " + latest.message;

            speak(toSpeak, () -> {
                // after reading reply, ask navigation question
                String navQ = isUrdu ? "کیا آپ مین پیج پر جانا چاہتے ہیں یا ہیلپ پیج؟" :
                        "Do you want to go to main page or help page?";
                speak(navQ, this::startListeningForNavigation);
            });
        }
    }

    private void speak(String text, Runnable onDone) {
        if (tts == null) {
            if (onDone != null) onDone.run();
            return;
        }

        String utt = "UTT_" + System.currentTimeMillis();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utt);
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String s) { }

            @Override
            public void onDone(String s) {
                if (onDone != null) mainHandler.post(onDone);
            }

            @Override
            public void onError(String s) {
                if (onDone != null) mainHandler.post(onDone);
            }
        });
    }

    private void startListeningForNavigation() {
        // avoid starting if already waiting
        if (waitingForNavResponse.get()) return;
        waitingForNavResponse.set(true);

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // fallback: show toast and don't block
            Toast.makeText(this, isUrdu ? "وائس ریکاگنیشن دستیاب نہیں" : "Speech recognition not available", Toast.LENGTH_SHORT).show();
            waitingForNavResponse.set(false);
            return;
        }

        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        android.content.Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isUrdu ? new Locale("ur", "PK") : Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                waitingForNavResponse.set(false);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleNavigationResult(matches.get(0));
                } else {
                    speak(isUrdu ? "سمجھ نہیں آیا، دوبارہ کہیں" : "Didn't catch that, please repeat", SupportChatActivity.this::startListeningForNavigation);
                }
            }

            @Override
            public void onError(int error) {
                waitingForNavResponse.set(false);
                speak(isUrdu ? "سمجھ نہیں آیا، دوبارہ کہیں" : "Didn't catch that, please repeat", SupportChatActivity.this::startListeningForNavigation);
            }
        });

        speechRecognizer.startListening(intent);
    }

    private void handleNavigationResult(String spoken) {
        if (spoken == null) {
            speak(isUrdu ? "سمجھ نہیں آیا، دوبارہ کہیں" : "Didn't catch that, please repeat", this::startListeningForNavigation);
            return;
        }
        String lower = spoken.toLowerCase();
        Log.d(TAG, "Navigation result: " + lower);

        // match keywords for main/home
        if (lower.contains("main") || lower.contains("home") || lower.contains("مین") || lower.contains("واپس")) {
            speak(isUrdu ? "آپ مین پیج پر جا رہے ہیں" : "Navigating to main page", () -> {
                startActivity(new Intent(SupportChatActivity.this, MainActivity.class));
                finish();
            });
            return;
        }

        // match keywords for help page (ContactSupportActivity)
        if (lower.contains("help") || lower.contains("help page") || lower.contains("ہیلپ") || lower.contains("مدد")) {
            speak(isUrdu ? "آپ ہیلپ پیج پر جا رہے ہیں" : "Opening help page", () -> {
                Intent i = new Intent(SupportChatActivity.this, ContactSupportActivity.class);
                startActivity(i);
                finish();
            });
            return;
        }

        // fallback: try again
        speak(isUrdu ? "سمجھ نہیں آیا، دوبارہ کہیں" : "Didn't catch that, please repeat", this::startListeningForNavigation);
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

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }

        mainHandler.removeCallbacksAndMessages(null);
    }
}
