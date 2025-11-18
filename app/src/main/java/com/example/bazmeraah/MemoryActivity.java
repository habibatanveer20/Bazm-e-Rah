package com.example.bazmeraah;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Locale;

public class MemoryActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;

    private boolean isEnglish = true;
    private boolean isVoiceEnabled = true;

    BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);

        prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        bottomNav = findViewById(R.id.bottom_nav);

        isEnglish = !prefs.getBoolean("language_urdu", false);
        isVoiceEnabled = prefs.getBoolean("voice_enabled", true);

        loadFragment(new PlaceFragment());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_people) {
                loadFragment(new peopleFragment());
            } else if (id == R.id.nav_notes) {
                loadFragment(new notesFragment());
            } else {
                loadFragment(new PlaceFragment());
            }
            return true;
        });

        initializeTTS();
        initializeSpeechRecognizer();
    }

    // ------------------------- TTS -------------------------
    private void initializeTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isEnglish ? Locale.ENGLISH : new Locale("ur", "PK"));
                speak(getGreeting(), this::askWhatToDo);
            }
        });
    }

    private void speak(String text, Runnable onDone) {
        if (tts == null || !isVoiceEnabled) {
            if (onDone != null) onDone.run();
            return;
        }

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onError(String utteranceId) {}
            @Override
            public void onDone(String utteranceId) {
                if (onDone != null)
                    runOnUiThread(() -> new Handler().postDelayed(onDone, 400));
            }
        });

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID");
    }

    private void speak(String text) { speak(text, null); }

    // ------------------------- GREETING & ASKING -------------------------
    private String getGreeting() {
        return isEnglish ?
                "Welcome to your memory page." :
                "میموری پیج میں خوش آمدید۔";
    }

    private void askWhatToDo() {
        String msg = isEnglish ?
                "What would you like to open? People, Places or Notes?" :
                "آپ کیا کھولنا چاہیں گی؟ پیپل، پلیس یا نوٹس؟";
        speak(msg, this::startListening);
    }

    private void askFurtherAssistance() {
        String msg = isEnglish ?
                "Do you need any further assistance?" :
                "کیا آپ کو مزید مدد چاہیے؟";
        speak(msg, this::startListening);
    }

    // ------------------------- STT -------------------------
    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onError(int error) {
                speak(getNotUnderstood(), MemoryActivity.this::startListening);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (list != null && !list.isEmpty()) {
                    handleCommand(list.get(0).toLowerCase());
                } else {
                    speak(getNotUnderstood(), MemoryActivity.this::startListening);
                }
            }
        });
    }

    private void startListening() {
        if (!isVoiceEnabled) return;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                isEnglish ? Locale.ENGLISH : new Locale("ur", "PK"));

        speechRecognizer.startListening(intent);
    }

    // ------------------------- COMMAND LOGIC -------------------------
    private void handleCommand(String c) {

        // ------- PEOPLE -------
        if (c.contains("people") || c.contains("پیپل") || c.contains("لوگ")) {
            bottomNav.setSelectedItemId(R.id.nav_people);
            loadFragment(new peopleFragment());
            speak(msgOpen("people"), this::askFurtherAssistance);
            return;
        }

        // ------- PLACES -------
        if (c.contains("place") || c.contains("پلیس") || c.contains("جگہ")) {
            bottomNav.setSelectedItemId(R.id.nav_places);
            loadFragment(new PlaceFragment());
            speak(msgOpen("place"), this::askFurtherAssistance);
            return;
        }

        // ------- NOTES -------
        if (c.contains("notes") || c.contains("نوٹس")) {
            bottomNav.setSelectedItemId(R.id.nav_notes);
            loadFragment(new notesFragment());
            speak(msgOpen("notes"), this::askFurtherAssistance);
            return;
        }

        // ------- YES -------
        if (c.contains("yes") || c.contains("haan") || c.contains("ہاں")) {
            askWhatToDo();
            return;
        }

        // ------- NO -------
        if (c.contains("no") || c.contains("nahi") || c.contains("نہیں")) {
            speak(isEnglish ? "Alright, I’m here if you need anything." :
                    "ٹھیک ہے، اگر ضرورت ہو تو میں موجود ہوں۔");
            return;
        }

        // ------- BACK -------
        if (c.contains("back") || c.contains("home") || c.contains("main")
                || c.contains("واپس")) {

            speak(isEnglish ? "Returning to main page." :
                    "مین پیج پر جا رہی ہوں۔", () -> {
                startActivity(new Intent(MemoryActivity.this, MainActivity.class));
                finish();
            });
            return;
        }

        // ------- EXIT -------
        if (c.contains("exit") || c.contains("بند") || c.contains("ایگزٹ")) {
            speak(isEnglish ? "Closing the app." : "ایپ بند کر رہی ہوں۔",
                    () -> finishAffinity());
            return;
        }

        speak(getNotUnderstood(), this::startListening);
    }

    private String msgOpen(String type) {
        if (type.equals("people"))
            return isEnglish ? "Opening people." : "پیپل کھول رہی ہوں۔";

        if (type.equals("place"))
            return isEnglish ? "Opening places." : "پلیس کھول رہی ہوں۔";

        return isEnglish ? "Opening notes." : "نوٹس کھول رہی ہوں۔";
    }

    private String getNotUnderstood() {
        return isEnglish ?
                "Sorry, I didn't understand. Please repeat." :
                "معذرت، سمجھ نہیں آیا۔ دوبارہ کہیں۔";
    }

    // ------------------------- Fragment Loader -------------------------
    private void loadFragment(Fragment f) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop(); tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
