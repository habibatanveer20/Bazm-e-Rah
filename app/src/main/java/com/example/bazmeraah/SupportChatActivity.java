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

import com.google.firebase.database.*;

import java.util.*;

public class SupportChatActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private TextView emptyView;
    private ProgressBar progressBar;

    private DatabaseReference supportRef;
    private Query phoneQuery;
    private ValueEventListener listener;

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean isUrdu = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean waitingForNav = false;
    private boolean isWelcomeDone = false;
    private String lastSpokenMessage = "";
    private boolean isRepeatStep = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                speak(isUrdu
                        ? "آپ مین پیج پر جا رہے ہیں"
                        : "Going to main page", () -> {

                    startActivity(new Intent(SupportChatActivity.this, MainActivity.class));
                    finish();
                });
            }
        });
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_chat);

        recyclerView = findViewById(R.id.recyclerChat);
        emptyView = findViewById(R.id.emptyView);
        progressBar = findViewById(R.id.progressBar);

        adapter = new ChatAdapter(this, messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        FirebaseDatabase db = FirebaseDatabase.getInstance("https://bazm-e-rah-default-rtdb.firebaseio.com/");
        supportRef = db.getReference("SupportMessages");

        isUrdu = getSharedPreferences("AppSettings", MODE_PRIVATE)
                .getBoolean("language_urdu", false);

        setupTTS();

        String phone = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getString("phone", null);

        if (phone == null) return;

        attachListener(phone);
    }

    // ✅ TTS + Welcome
    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {

                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.US);

                String welcome = isUrdu
                        ? "آپ سپورٹ چیٹ میں ہیں جہاں آپ ایڈمن کے جوابات دیکھ سکتے ہیں"
                        : "You are on support chat where you can see admin replies";

                speak(welcome, () -> {
                    isWelcomeDone = true;

                    // 🔥 Welcome ke baad hi messages bolo
                    if (messages != null && !messages.isEmpty()) {
                        announceMessages(messages);
                    } else {
                        // even if empty
                        announceMessages(messages);
                    }
                });
            }
        });
    }

    // ✅ Firebase listener
    private void attachListener(String phone) {

        progressBar.setVisibility(View.VISIBLE);

        phoneQuery = supportRef.orderByChild("contact").equalTo(phone);

        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                messages.clear();

                for (DataSnapshot d : snapshot.getChildren()) {

                    String from = d.child("from").getValue() != null
                            ? String.valueOf(d.child("from").getValue()) : "";

                    String msg = d.child("message").getValue() != null
                            ? String.valueOf(d.child("message").getValue()) : "";

                    long ts = 0;
                    try {
                        ts = Long.parseLong(String.valueOf(d.child("timestamp").getValue()));
                    } catch (Exception ignored) {}

                    messages.add(new ChatMessage("", "", from, msg, ts));
                }

                Collections.sort(messages, Comparator.comparingLong(a -> a.timestamp));

                adapter.updateList(messages);

                progressBar.setVisibility(View.GONE);

                // 🔥 ALWAYS SPEAK (with delay for TTS ready)
                if (isWelcomeDone) {
                    mainHandler.postDelayed(() -> announceMessages(messages), 500);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        };

        phoneQuery.addValueEventListener(listener);
    }

    // ✅ MAIN SPEAK LOGIC
    private void announceMessages(List<ChatMessage> list) {

        // ❗ CASE 1: No messages at all
        if (list == null || list.isEmpty()) {

            String speech = isUrdu
                    ? "ابھی تک آپ کا کوئی سپورٹ پیغام موجود نہیں ہے"
                    : "You have not sent any support message yet";

            lastSpokenMessage = speech.toString();

            speak(lastSpokenMessage, this::askRepeat);
            return;
        }

        ChatMessage latestUser = null;
        ChatMessage latestAdmin = null;

        for (int i = list.size() - 1; i >= 0; i--) {
            ChatMessage m = list.get(i);

            // USER = not admin
            if (latestUser == null && m.from != null && !m.from.equalsIgnoreCase("admin")) {
                latestUser = m;
            }

            // ADMIN
            if (latestAdmin == null && m.from != null && m.from.equalsIgnoreCase("admin")) {
                latestAdmin = m;
            }

            if (latestUser != null && latestAdmin != null) break;
        }

        StringBuilder speech = new StringBuilder();

        // USER MESSAGE
        if (latestUser != null && latestUser.message != null && !latestUser.message.isEmpty()) {
            speech.append(isUrdu ? "آپ کا آخری پیغام: " : "Your last message: ");
            speech.append(latestUser.message).append(". ");
        } else {
            speech.append(isUrdu
                    ? "آپ نے ابھی تک کوئی پیغام نہیں بھیجا"
                    : "You have not sent any message yet. ");
        }

        // ADMIN REPLY
        if (latestAdmin != null && latestAdmin.message != null && !latestAdmin.message.isEmpty()) {
            speech.append(isUrdu ? "ایڈمن کا جواب: " : "Admin reply: ");
            speech.append(latestAdmin.message);
        } else {
            speech.append(isUrdu
                    ? "ابھی تک ایڈمن کی طرف سے کوئی جواب نہیں آیا"
                    : "No reply from admin yet");
        }

        lastSpokenMessage = speech.toString();
        speak(lastSpokenMessage, this::askRepeat);
    }

    // ✅ Ask navigation
    private void askNavigation() {
        speak(isUrdu
                        ? "کیا آپ مین پیج، ہیلپ پیج یا ایپ بند کرنا چاہتے ہیں؟"
                        : "Do you want to go to main page, help page or exit the app?",
                this::startListening);
    }

    // ✅ Beep
    private void playBeep() {
        android.media.ToneGenerator toneGen =
                new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);
        toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200);
    }

    // ✅ Listening
    private void startListening() {

        if (waitingForNav) return;
        waitingForNav = true;

        playBeep(); // 🔊 beep

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {

            @Override
            public void onResults(Bundle results) {
                waitingForNav = false;

                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    handleNavigation(matches.get(0));
                }
            }

            @Override
            public void onError(int error) {
                waitingForNav = false;
                startListening();
            }
        });

        speechRecognizer.startListening(i);
    }

    // ✅ Handle navigation
    private void handleNavigation(String spoken) {

        if (spoken == null) return;

        String lower = spoken.toLowerCase();

        // 🔥 REPEAT FLOW
        if (isRepeatStep) {

            isRepeatStep = false;

            if (lower.contains("yes") || lower.contains("haan") || lower.contains("ہاں")) {

                // 🔁 repeat message
                speak(lastSpokenMessage, this::askRepeat);

            } else if (lower.contains("no") || lower.contains("nahin") || lower.contains("نہیں")) {

                // ➡️ go to navigation
                askNavigation();

            } else {
                startListening();
            }

            return;
        }

        // 🔥 NORMAL NAVIGATION FLOW
        if (lower.contains("exit") || lower.contains("close") || lower.contains("بند")) {
            speak(isUrdu ? "ایپ بند کی جا رہی ہے" : "Closing app", this::finishAffinity);
        }
        else if (lower.contains("main") || lower.contains("home") || lower.contains("مین")) {
            speak(isUrdu ? "مین پیج کھولا جا رہا ہے" : "Opening main page", () -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        }
        else if (lower.contains("help") || lower.contains("ہیلپ")) {
            speak(isUrdu ? "ہیلپ پیج کھولا جا رہا ہے" : "Opening help page", () -> {
                startActivity(new Intent(this, ContactSupportActivity.class));
                finish();
            });
        }
        else {
            startListening();
        }
    }


    // ✅ SAFE TTS
    private void speak(String text, Runnable onDone) {

        if (tts == null) {
            mainHandler.postDelayed(() -> speak(text, onDone), 500);
            return;
        }

        if (tts.isSpeaking()) {
            tts.stop();
        }

        String id = "utt_" + System.currentTimeMillis();

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String s) {}

            @Override
            public void onDone(String s) {
                if (onDone != null) mainHandler.post(onDone);
            }

            @Override public void onError(String s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tts != null) tts.stop();

        if (isWelcomeDone && messages != null) {
            mainHandler.postDelayed(() -> announceMessages(messages), 800);
        }
    }
    private void askRepeat() {

        isRepeatStep = true;

        speak(isUrdu
                        ? "کیا آپ پیغام دوبارہ سننا چاہتے ہیں؟ ہاں یا نہیں کہیں"
                        : "Do you want me to repeat the message? Say yes or no",
                this::startListening);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}