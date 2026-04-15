package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bazmeraah.ai.VisionEngine;
import com.example.bazmeraah.ai.CurrencyEngine;
import com.example.bazmeraah.ai.FaceEngine;
import com.example.bazmeraah.ai.FaceDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AI_Assistant extends AppCompatActivity {

    private static final int REQ_CODE_RECORD_AUDIO = 101;
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_LANGUAGE_URDU = "language_urdu";
    private static final String KEY_DARK_MODE = "dark_mode";
    private boolean isSavingNote = false;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private SharedPreferences prefs;

    private VisionEngine visionEngine;
    private CurrencyEngine currencyEngine;
    private FaceEngine faceEngine;
    private FaceDatabase faceDatabase;

    private boolean isUrdu = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);

        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        visionEngine = new VisionEngine(this);
        visionEngine.start();

        currencyEngine = new CurrencyEngine(this);
        currencyEngine.start();

        faceEngine = new FaceEngine(this);
        faceEngine.start();

        faceDatabase = new FaceDatabase(this);

        initTTS();
        initSpeechRecognizer();

        if (!hasRecordPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_CODE_RECORD_AUDIO
            );
        }

        ImageButton btnMic = findViewById(R.id.btnMic);
        View root = findViewById(R.id.main);

        root.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startListening();
                btnMic.setAlpha(1f);
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                stopListening();
                btnMic.setAlpha(0.9f);
            }

            return true;
        });
    }

    /* ================= TTS ================= */

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.US);
                speakIntro();
            }
        });
    }
    private void speakIntro() {

        String intro = isUrdu ?
                "اے آئی اسسٹنٹ تیار ہے۔ آپ پوچھ سکتی ہیں سامنے کیا ہے، رنگ کیا ہے، نوٹ کیا ہے یا یہ کون ہے۔" :
                "AI Assistant is ready. You can ask what is in front, what is the color, which currency note or who is this.";

        tts.speak(intro, TextToSpeech.QUEUE_FLUSH, null, "INTRO");
    }

    /* ================= SPEECH ================= */

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void initSpeechRecognizer() {

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                isUrdu ? new Locale("ur", "PK") : Locale.getDefault()
        );

        speechRecognizer.setRecognitionListener(listener);
    }

    private void startListening() {
        if (speechRecognizer != null && hasRecordPermission()) {
            speechRecognizer.startListening(recognizerIntent);
            isListening = true;
        }
    }

    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {

        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override

        public void onError(int error) {
            Toast.makeText(AI_Assistant.this,
                    isUrdu ? "آواز سمجھ نہیں آئی" : "Didn't catch that",
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {

            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if (matches == null || matches.isEmpty()) return;

            String spoken =
                    matches.get(0).toLowerCase(Locale.ROOT).trim();

            handleUserQuery(spoken);
        }

        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    };

    /* ================= SMART QUERY ROUTER ================= */

    private void handleUserQuery(String spoken) {

        // normalize text
        spoken = spoken.toLowerCase().trim();

        // 🔥 STEP 1: NOTE SAVE MODE
        if (isSavingNote) {

            saveNote(spoken);
            isSavingNote = false;

            tts.speak(
                    isUrdu ? "نوٹ محفوظ ہو گیا" : "Note saved",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "DONE"
            );

            return;
        }

        // 🔥 STEP 2: NOTE START (FIXED)
        if (
                (spoken.contains("note") && spoken.contains("write"))
                        || spoken.contains("write a note")
                        || spoken.contains("right note")
                        || spoken.contains("لکھو")
        ) {

            isSavingNote = true;

            tts.speak(
                    isUrdu ? "بتائیں کیا نوٹ محفوظ کرنا ہے" : "Tell me what to save",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "ASK_NOTE"
            );

            return;
        }

        // 🔥 NAVIGATION
        if (
                spoken.contains("main page")
                        || spoken.contains("go back")
                        || spoken.contains("back")
        ) {

            tts.speak("Going to main page",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "NAV");

            startActivity(new Intent(AI_Assistant.this, MainActivity.class));
            finish();
            return;
        }

        // 🔥 FACE SAVE
        if (spoken.contains("save face") || spoken.contains("save this face")) {
            String name = extractName(spoken);
            saveFace(name);
            return;
        }

        // 🔥 FACE RECOGNITION
        if (spoken.contains("who is this") || spoken.contains("kaun hai") || spoken.contains("kon hai")) {
            recognizeFace();
            return;
        }

        // 🔥 CURRENCY
        if (
                spoken.contains("currency")
                        || spoken.contains("rupee")
                        || spoken.contains("money")
                        || spoken.contains("paisa")
        ) {
            speakCurrency();
            return;
        }

        // 🔥 COLOR
        if (spoken.contains("color") || spoken.contains("rang")) {
            String result = visionEngine.detectColorOfLastObject();
            tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "COLOR");
            return;
        }

        // 🔥 DEFAULT (SAFE FIX)
        if (
                spoken.contains("what")
                        || spoken.contains("kya")
                        || spoken.contains("in front")
                        || spoken.contains("object")
        ) {
            speakAndDetect();
        } else {
            tts.speak(
                    isUrdu ? "سمجھ نہیں آیا، دوبارہ بولیں" : "I didn't understand, please try again",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "UNKNOWN"
            );
        }
    }
    private String extractName(String spoken) {

        spoken = spoken.replace("save face", "")
                .replace("save this face", "")
                .replace("save", "")
                .replace("as", "")
                .trim();

        String[] words = spoken.split(" ");
        if (words.length == 0) return null;

        return words[words.length - 1];
    }

    /* ================= FACE ================= */

    private void saveFace(String name) {

        if (name == null) {
            tts.speak(
                    isUrdu ? "نام بتائیں" : "Tell the name",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "NO_NAME"
            );
            return;
        }

        tts.speak(
                isUrdu ? "چہرہ محفوظ کیا جا رہا ہے" : "Saving face",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "WAIT"
        );

        faceEngine.saveFace(name, faceDatabase, new FaceEngine.FaceCallback() {
            @Override
            public void onSuccess(String result) {
                tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "SAVED");
            }

            @Override
            public void onError(String error) {
                tts.speak(error, TextToSpeech.QUEUE_FLUSH, null, "ERROR");
            }
        });
    }

    private void recognizeFace() {

        tts.speak(
                isUrdu ? "چیک کر رہا ہوں" : "Checking person",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "WAIT"
        );

        faceEngine.recognizeFace(faceDatabase, new FaceEngine.FaceCallback() {
            @Override
            public void onSuccess(String result) {
                tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "FOUND");
            }

            @Override
            public void onError(String error) {
                tts.speak(error, TextToSpeech.QUEUE_FLUSH, null, "ERROR");
            }
        });
    }
    /* ================= OBJECT ================= */

    private void speakAndDetect() {

        tts.speak(
                isUrdu ? "دیکھ رہا ہوں" : "Checking",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "THINK"
        );

        visionEngine.fetchSnapshotAndDetect(new VisionEngine.DetectionCallback() {

            @Override
            public void onResult(String spokenText) {
                tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "RESULT");
            }

            @Override
            public void onError() {
                tts.speak(
                        isUrdu ? "کیمرہ میں مسئلہ ہے" : "Camera error",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "ERROR"
                );
            }
        });
    }

    /* ================= CURRENCY ================= */

    private void speakCurrency() {

        tts.speak(
                isUrdu ? "کرنسی چیک کر رہا ہوں" : "Checking currency",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "WAIT"
        );

        currencyEngine.fetchSnapshotAndDetect(new CurrencyEngine.DetectionCallback() {

            @Override
            public void onResult(String result) {
                tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "CURRENCY_RESULT");
            }

            @Override
            public void onError() {
                tts.speak("Camera error",
                        TextToSpeech.QUEUE_FLUSH, null, "ERROR");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
        if (visionEngine != null) visionEngine.close();
        if (currencyEngine != null) currencyEngine.close();
        if (faceEngine != null) faceEngine.close();
    }
    private void saveNote(String noteText) {

        SharedPreferences prefs = getSharedPreferences("NotesPrefs", MODE_PRIVATE);

        Set<String> notes = prefs.getStringSet("notes", new HashSet<>());
        notes = new HashSet<>(notes);
        notes.add(noteText);

        prefs.edit().putStringSet("notes", notes).apply();
    }
}