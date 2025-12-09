package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Locale;

public class AI_Assistant extends AppCompatActivity {

    private static final int REQ_CODE_RECORD_AUDIO = 101;
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_LANGUAGE_URDU = "language_urdu";
    private static final String KEY_DARK_MODE = "dark_mode";

    private TextToSpeech tts;
    private boolean introSpoken = false;
    private boolean isTTSReady = false;
    private boolean isSpeakingIntro = false; // jab TTS intro bol raha ho
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private SharedPreferences prefs;

    // Added fields for post-stop prompt handling
    private boolean awaitingChoice = false; // jab hum user se "continue or main page" expect kar rahe hon
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // prefs aur theme apply karna BEFORE setContentView
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(darkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_ai_assistant);

        // insets handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton btnMic = findViewById(R.id.btnMic);
        View root = findViewById(R.id.main);

        initTTS();
        initSpeechRecognizer();

        // agar permission pehle se nahin hai to request karein
        if (!hasRecordPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
        }

        // Touch-and-hold anywhere on screen to enable mic
        root.setOnTouchListener(new View.OnTouchListener() {
            private Handler localHandler = new Handler();
            private boolean downStarted = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    downStarted = true;

                    // Agar TTS abhi intro bol raha ho to wait kar dein -- warna mic start karo
                    if (isSpeakingIntro) {
                        Log.d("AI_Assistant", "User pressed while intro speaking — will start mic after intro");
                        // jab intro done ho toh startMicListening; safety: 300ms buffer
                        handler.postDelayed(() -> {
                            if (!isListening) startMicListening();
                        }, 500);
                    } else {
                        // seedha mic start karo (agar intro pehle bol chuka ho ya nahi chahiye)
                        startMicListening();
                    }

                    // Visual feedback: change alpha of button
                    btnMic.setAlpha(1f);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    downStarted = false;
                    stopMicListening();
                    btnMic.setAlpha(0.9f);
                }
                // return true taake touch consume ho aur onClick etc na chale
                return true;
            }
        });

        // optional: agar koi specific button press bhi chahiye to
        btnMic.setOnTouchListener((v, event) -> {
            // same as root touch: let root handle it; but we override to give same feel
            return false; // let root's listener still receive events
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Speak intro ONCE when activity visible (user wanted "sab se pehle intro")
        handler.postDelayed(() -> {
            if (!introSpoken) {
                speakIntro();
            }
        }, 400);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
                if (isUrdu) {
                    Locale urLocale = new Locale("ur", "PK");
                    int res = tts.setLanguage(urLocale);
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w("AI_Assistant", "Urdu TTS not available, falling back to default");
                        tts.setLanguage(Locale.US);
                    }
                } else {
                    tts.setLanguage(Locale.US);
                }
                tts.setSpeechRate(0.95f);
                isTTSReady = true;
                Log.d("AI_Assistant", "TTS initialized and ready");

                // Utterance listener to track intro done and to resume listening when needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            if ("INTRO_UTT".equals(utteranceId)) {
                                isSpeakingIntro = true;
                            }
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            if ("INTRO_UTT".equals(utteranceId)) {
                                isSpeakingIntro = false;
                                introSpoken = true;
                                Log.d("AI_Assistant", "INTRO_UTT done");
                            }

                            // If prompt finished and we expect to start listening, schedule it
                            if ("POST_STOP_PROMPT".equals(utteranceId) || "WELCOME_PROMPT".equals(utteranceId)) {
                                handler.postDelayed(() -> startMicListening(), 600);
                            }
                        }

                        @Override
                        public void onError(String utteranceId) {
                            if ("INTRO_UTT".equals(utteranceId)) {
                                isSpeakingIntro = false;
                                introSpoken = true;
                            }
                        }
                    });
                }
            } else {
                isTTSReady = false;
                Log.e("AI_Assistant", "TTS init failed");
            }
        });
    }

    private void speakIntro() {
        if (!isTTSReady || tts == null) {
            Log.d("AI_Assistant", "speakIntro: TTS not ready yet, retrying in 300ms");
            handler.postDelayed(this::speakIntro, 300);
            return;
        }

        boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
        String intro;
        if (isUrdu) {
            intro = "آپ بزمِ راہ AI اسسٹنٹ پر ہیں۔ اسکرین کو کہیں بھی دباکر رکھیں تاکہ مائیک آن رہے۔ چھوڑتے ہی مائیک بند ہو جائے گا۔";
        } else {
            intro = "You are on Bazm-e-Raah AI Assistant. Press and hold the screen anywhere to keep the microphone on. Release to stop.";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            isSpeakingIntro = true;
            tts.speak(intro, TextToSpeech.QUEUE_FLUSH, null, "INTRO_UTT");
        } else {
            // older devices: speak and set flags with delay fallback
            tts.speak(intro, TextToSpeech.QUEUE_FLUSH, null);
            introSpoken = true;
            handler.postDelayed(() -> isSpeakingIntro = false, 1200);
        }
        Log.d("AI_Assistant", "speakIntro: queued intro");
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            } catch (Exception e) {
                Log.w("AI_Assistant", "SpeechRecognizer.createSpeechRecognizer failed", e);
                speechRecognizer = null;
            }

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
            if (isUrdu) recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("ur", "PK"));
            else recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

            if (speechRecognizer != null) {
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        Log.d("AI_Assistant", "Ready for speech");
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        Log.d("AI_Assistant", "Beginning of speech");
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) { }

                    @Override
                    public void onBufferReceived(byte[] buffer) { }

                    @Override
                    public void onEndOfSpeech() {
                        Log.d("AI_Assistant", "End of speech");
                    }

                    @Override
                    public void onError(int error) {
                        Log.w("AI_Assistant", "Recognizer error: " + error);
                        isListening = false;
                        awaitingChoice = false;
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String text = matches.get(0);
                            Log.d("AI_Assistant", "Final recognized: " + text);

                            if (awaitingChoice) {
                                handlePostMicChoice(text);
                                return;
                            }

                            // Normal behaviour: speak back what was heard (localized)
                            boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
                            String reply = isUrdu ? "آپ نے کہا: " + text : "You said: " + text;
                            if (tts != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                                    tts.speak(reply, TextToSpeech.QUEUE_ADD, null, "HEARD_UTT");
                                else
                                    tts.speak(reply, TextToSpeech.QUEUE_ADD, null);
                            }
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        // ignore partials for now
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) { }
                });
            }
        } else {
            speechRecognizer = null;
            Log.w("AI_Assistant", "Speech recognition not available on this device");
        }
    }

    private void startMicListening() {
        if (!hasRecordPermission()) {
            Toast.makeText(this, "Microphone permission required. Please allow and try again.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
            return;
        }

        if (speechRecognizer == null) {
            Log.w("AI_Assistant", "SpeechRecognizer not initialized");
            boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
            String noMic = isUrdu ? "مائیک سروس دستیاب نہیں ہے۔" : "Microphone service not available.";
            if (tts != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    tts.speak(noMic, TextToSpeech.QUEUE_ADD, null, "NO_MIC");
                else
                    tts.speak(noMic, TextToSpeech.QUEUE_ADD, null);
            }
            return;
        }

        if (isListening) {
            Log.d("AI_Assistant", "startMicListening: already listening");
            return;
        }

        // cancel any awaitingChoice mode
        awaitingChoice = false;

        // feedback to user
        boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
        String micOnMsg = isUrdu ? "مائیک آن۔ براہِ کرم بولنا شروع کریں۔" : "Microphone on. Please start speaking.";
        if (tts != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                tts.speak(micOnMsg, TextToSpeech.QUEUE_FLUSH, null, "MIC_ON");
            else
                tts.speak(micOnMsg, TextToSpeech.QUEUE_FLUSH, null);
        }

        // delay so TTS not captured
        handler.postDelayed(() -> {
            try {
                // ensure recognizerIntent defaults
                if (recognizerIntent == null) {
                    initSpeechRecognizer(); // will reinit recognizerIntent and listener
                } else {
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                }

                speechRecognizer.startListening(recognizerIntent);
                isListening = true;
                Log.d("AI_Assistant", "SpeechRecognizer started listening");
            } catch (Exception e) {
                Log.e("AI_Assistant", "startListening failed: " + e.getMessage(), e);
                Toast.makeText(this, "Mic start failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, 800);
    }

    private void stopMicListening() {
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception e) {
                Log.e("AI_Assistant", "stopListening exception: " + e.getMessage());
            }
        }
        isListening = false;

        boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
        String micOffMsg = isUrdu ? "مائیک بند۔" : "Microphone off.";
        if (tts != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                tts.speak(micOffMsg, TextToSpeech.QUEUE_FLUSH, null, "MIC_OFF");
            else
                tts.speak(micOffMsg, TextToSpeech.QUEUE_FLUSH, null);
        }

        // prompt after small delay
        handler.postDelayed(this::promptAfterStop, 700);
    }

    // Prompt the user after mic stop, then listen briefly for their choice
    private void promptAfterStop() {
        boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
        String prompt = isUrdu ?
                "کیا آپ مزید یہ اسسٹنٹ استعمال کرنا چاہیں گے یا مین پیج پر جانا چاہیں گے؟ جواب دیں: مزید یا مین پیج۔" :
                "Do you want to continue using the assistant or go to the main page? Say: continue or main page.";

        if (tts != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                tts.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "POST_STOP_PROMPT");
            else
                tts.speak(prompt, TextToSpeech.QUEUE_FLUSH, null);
        }

        // after prompt, listen briefly
        handler.postDelayed(() -> {
            try {
                awaitingChoice = true;

                // single best result
                if (recognizerIntent != null) {
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                }

                if (speechRecognizer != null) {
                    speechRecognizer.startListening(recognizerIntent);
                    isListening = true;
                }

                // fallback timeout (6s)
                handler.postDelayed(() -> {
                    if (awaitingChoice) {
                        awaitingChoice = false;
                        String reply = isUrdu ? "ٹھیک ہے، آپ اسی اسسٹنٹ کو استعمال جاری رکھ سکتے ہیں۔" :
                                "Alright — you can continue using the assistant.";
                        if (tts != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                                tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "KEEP_USING_TIMEOUT");
                            else
                                tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null);
                        }
                        // restore defaults
                        if (recognizerIntent != null) {
                            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                        }
                        try { if (speechRecognizer != null) speechRecognizer.stopListening(); } catch (Exception ignored) {}
                    }
                }, 6000);

            } catch (Exception e) {
                Log.e("AI_Assistant", "promptAfterStop startListening failed: " + e.getMessage());
                awaitingChoice = false;
                if (recognizerIntent != null) {
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                }
            }
        }, 900);
    }

    // Parse user's choice and act (go to main page or continue)
    private void handlePostMicChoice(String text) {
        awaitingChoice = false;
        isListening = false;

        if (text == null) text = "";

        String lower = text.toLowerCase(Locale.ROOT).trim();
        Log.d("AI_Assistant", "User choice: " + lower);

        boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);

        boolean goMain = lower.contains("main") || lower.contains("home") ||
                lower.contains("main page") || lower.contains("home page") ||
                lower.contains("menu") || lower.contains("jao") || lower.contains("jayen") ||
                lower.contains("مین") || lower.contains("مین پیج") || lower.contains("گھر");

        boolean keepUsing = lower.contains("continue") || lower.contains("more") ||
                lower.contains("aur") || lower.contains("mazid") ||
                lower.contains("مزید") || lower.contains("جاری") ||
                lower.contains("yes") || lower.contains("haan") || lower.contains("ہاں");

        if (goMain && !keepUsing) {
            String reply = isUrdu ? "ٹھیک ہے، مین پیج پر لے جا رہا ہوں۔" : "Okay, taking you to the main page.";
            if (tts != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "GO_MAIN_UTT");
                else
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null);
            }

            handler.postDelayed(() -> {
                try {
                    Intent i = new Intent(AI_Assistant.this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                    finish();
                } catch (Exception e) {
                    Log.e("AI_Assistant", "Failed to open MainActivity: " + e.getMessage());
                }
            }, 900);
        } else {
            String reply = isUrdu ? "ٹھیک ہے، آپ اسی اسسٹنٹ کو استعمال جاری رکھ سکتے ہیں۔" :
                    "Alright — you can continue using the assistant.";
            if (tts != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "KEEP_USING_UTT");
                else
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null);
            }
        }

        // restore recognizerIntent defaults
        if (recognizerIntent != null) {
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    // Permission result handling
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
                Toast.makeText(this, isUrdu ? "مائیکروفون کی اجازت دی گئی" : "Microphone permission granted", Toast.LENGTH_SHORT).show();
            } else {
                boolean isUrdu = prefs.getBoolean(KEY_LANGUAGE_URDU, false);
                Toast.makeText(this, isUrdu ? "مائیکروفون کی اجازت ضروری ہے۔ ایپ محدود کام کرے گی۔" :
                        "Microphone permission required. App may have limited functionality.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Helpers: call ye methods from settings UI jab user language/theme change kare
    private void saveLanguagePref(boolean urduOn) {
        prefs.edit().putBoolean(KEY_LANGUAGE_URDU, urduOn).apply();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; isTTSReady = false; isSpeakingIntro = false; }
        initTTS();

        try { if (speechRecognizer != null) { speechRecognizer.cancel(); speechRecognizer.destroy(); speechRecognizer = null; } } catch (Exception ignored) {}
        initSpeechRecognizer();
    }

    private void saveThemePref(boolean darkMode) {
        prefs.edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
        AppCompatDelegate.setDefaultNightMode(darkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        recreate();
    }
}
