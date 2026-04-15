package com.example.bazmeraah;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Locale;
import android.media.AudioManager;
import android.media.ToneGenerator;


public class MemoryActivity extends AppCompatActivity {
    private ToneGenerator toneGen;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;

    private boolean isUrdu = false;
    private boolean isListening = false;

    BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        isUrdu = prefs.getBoolean("language_urdu", false);

        bottomNav = findViewById(R.id.bottom_nav);

        // ✅ DEFAULT PEOPLE (BUT SILENT)
        loadFragment(new peopleFragment(false));
        bottomNav.setSelectedItemId(R.id.nav_people);

        initSpeech();
        initTTS();
    }

    // ================= TTS =================
    private void initTTS() {

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {

                tts.setLanguage(isUrdu ? new Locale("ur","PK") : Locale.ENGLISH);

                speakIntro();
            }
        });
    }

    private void speakIntro() {

        String msg = isUrdu ?
                "آپ میموری پیج پر ہیں۔ یہاں سے محفوظ لوگ یا نوٹس دیکھ سکتے ہیں۔ آپ کیا دیکھنا چاہتے ہیں یا مین پیج پر جانا چاہتے ہیں؟"
                :
                "You are on memory page. You can check saved people or notes. What do you want or go back to main page.";

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "INTRO");

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {

            @Override public void onStart(String id) {}

            @Override
            public void onDone(String id) {

                if ("INTRO".equals(id)) {
                    runOnUiThread(() -> startListening());
                }
            }

            @Override public void onError(String id) {}
        });
    }

    // ================= SPEECH =================
    private void initSpeech() {

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { isListening = false; }
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onError(int error) {

                isListening = false;

                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {

                    speakRepeat();
                }
            }

            @Override
            public void onResults(Bundle results) {

                isListening = false;

                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (list == null || list.isEmpty()) {
                    speakRepeat();
                    return;
                }

                String cmd = list.get(0).toLowerCase();

                // ✅ PEOPLE
                if (cmd.contains("people") || cmd.contains("پیپل")) {

                    stopMemory();

                    loadFragment(new peopleFragment(true)); // 🔥 NOW TTS START

                }

                // ✅ NOTES
                else if (cmd.contains("notes") || cmd.contains("نوٹس")|| cmd.contains("no") || cmd.contains("note")) {

                    stopMemory();

                    loadFragment(new notesFragment()); // notes handle itself
                }

                // ✅ MAIN PAGE
                else if (cmd.contains("main") || cmd.contains("home") || cmd.contains("واپس")) {

                    stopMemory();

                    startActivity(new Intent(MemoryActivity.this, MainActivity.class));
                    finish();
                }

                else {
                    startListening(); // retry
                }
            }
        });
    }

    private void startListening() {

        if (isListening) return;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                isUrdu ? new Locale("ur","PK") : Locale.ENGLISH);
        playBeep(); // 🔥 beep before mic
        speechRecognizer.startListening(intent);
        isListening = true;
    }

    // ================= STOP ONLY MEMORY =================
    private void stopMemory() {

        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
        } catch (Exception ignored) {}

        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {}
    }

    // ================= FRAGMENT =================
    private void loadFragment(Fragment f) {

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }

    @Override
    protected void onDestroy() {
        stopMemory();
        super.onDestroy();
    }
    private void speakRepeat() {

        String msg = isUrdu ?
                "میں نہیں سن سکا، دوبارہ کہیں۔"
                :
                "I didn't catch that, please repeat";

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "REPEAT");

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {

            @Override public void onStart(String id) {}

            @Override
            public void onDone(String id) {
                if ("REPEAT".equals(id)) {
                    runOnUiThread(() -> startListening());
                }
            }

            @Override public void onError(String id) {}
        });
    }
    private void playBeep() {
        try {
            if (toneGen != null) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            }
        } catch (Exception ignored) {}
    }
}