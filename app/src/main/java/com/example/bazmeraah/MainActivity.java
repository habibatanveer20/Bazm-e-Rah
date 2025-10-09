package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private static final int PERMISSION_REQUEST_CODE = 500;

    private Button btnMemory, btnSettings, btnHelp, btnWeather;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextToSpeech tts;
    private ToneGenerator toneGen;

    private boolean isMicActive = false;
    private boolean isConfirming = false;
    private Handler handler = new Handler();
    private String lastHeardCommand = null;

    private boolean isUrdu = false;
    private boolean isTTSInitialized = false; // ✅ Naya flag

    // Exit commands variations
    private final String[] exitCommands = {
            "exit", "close", "quit", "ایپ بند کرو", "app band karo","app band kar do", "exit app", "close this app"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Registration check
        if (!getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("isRegistered", false)) {
            startActivity(new Intent(this, RegistrationActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        btnMemory = findViewById(R.id.btnMemory);
        btnSettings = findViewById(R.id.btnSettings);
        btnHelp = findViewById(R.id.btnHelp);
        btnWeather = findViewById(R.id.btnWeather);

        btnMemory.setOnClickListener(v -> openMemoryPage());
        btnSettings.setOnClickListener(v -> openSettingsPage());
        btnHelp.setOnClickListener(v -> openHelpPage());
        btnWeather.setOnClickListener(v -> openWeatherPage());

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // Read saved language from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        isUrdu = prefs.getBoolean("language_urdu", false);

        // recognizer + intent
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                isUrdu ? new Locale("ur", "PK") : Locale.getDefault());

        speechRecognizer.setRecognitionListener(globalListener);

        requestPermissionsAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ Check karo ke kya theme change ho raha hai
        SharedPreferences themePrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        boolean isThemeChanging = themePrefs.getBoolean("is_theme_changing", false);

        if (isThemeChanging) {
            // ✅ Theme change ho raha hai, toh welcome message na chale
            themePrefs.edit().putBoolean("is_theme_changing", false).apply();
            // ✅ Sirf listening start karo bina welcome message ke
            if (tts != null && isTTSInitialized) {
                startListeningForCommands();
            }
        } else {
            // ✅ Normal case - welcome message bolo
            if (tts != null && !isTTSInitialized) {
                speakWelcomeMessage();
            }
        }
    }
    // Recognition listener
    private final RecognitionListener globalListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { playBeep(); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }

        @Override
        public void onError(int error) {
            isMicActive = false;

            if (isConfirming) {
                String msg = isUrdu
                        ? "براہ کرم ہاں یا نہیں کہیں۔"
                        : "Please say yes or no.";
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_ERR");
            } else {
                String msg;
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    msg = isUrdu
                            ? "میں نے کچھ نہیں سنا۔ براہ کرم کمانڈ دوبارہ کہیں۔"
                            : "I didn't catch that. Please say the command again.";
                } else {
                    msg = isUrdu
                            ? "مائیکروفون میں مسئلہ ہے۔ دوبارہ کوشش کریں۔"
                            : "Microphone error. Please try again.";
                }
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "ERROR");
            }
        }

        @Override
        public void onResults(Bundle results) {
            isMicActive = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                String msg = isUrdu
                        ? "میں نے کچھ نہیں سنا۔ براہ کرم کمانڈ دوبارہ کہیں۔"
                        : "I didn't hear anything. Please say the command again.";
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "ERROR");
                return;
            }

            String heard = matches.get(0).toLowerCase(Locale.ROOT).trim();
            Toast.makeText(MainActivity.this, "Heard: " + heard, Toast.LENGTH_SHORT).show();

            // 🔹 Direct exit command check FIRST
            for (String cmd : exitCommands) {
                if (heard.contains(cmd)) {
                    exitApp(); // فوراً exit
                    return;   // باقی logic skip
                }
            }

            // Other commands
            if (isConfirming) {
                handleConfirmation(heard);
            } else {
                handleCommandOrAskConfirmation(heard);
            }
        }

        @Override public void onPartialResults(Bundle partialResults) { }
        @Override public void onEvent(int eventType, Bundle params) { }
    };

    private void handleConfirmation(String heard) {
        if (heard.contains("yes") || heard.contains("ہاں")|| heard.contains("han")) {
            isConfirming = false;
            if (lastHeardCommand != null) {
                executeCommand(lastHeardCommand);
                lastHeardCommand = null;
            }
        } else if (heard.contains("no") || heard.contains("نہیں")|| heard.contains("nhi")|| heard.contains("nahin")) {
            isConfirming = false;
            lastHeardCommand = null;
            String msg = isUrdu
                    ? "ٹھیک ہے، براہ کرم دوبارہ کمانڈ کہیں۔"
                    : "Okay, please say your command again.";
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "RETRY_CMD");
        } else {
            String msg = isUrdu
                    ? "براہ کرم ہاں یا نہیں کہیں۔"
                    : "Please say yes or no.";
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_ERR");
        }
    }

    private void handleCommandOrAskConfirmation(String heard) {
        if (heard.equals("yes") || heard.equals("no") || heard.equals("ok") || heard.equals("okay")) {
            String msg = isUrdu
                    ? "براہ کرم پورا کمانڈ کہیں جیسے open weather یا open help۔"
                    : "Please say the full command like open weather or open help.";
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "UNKNOWN_CMD");
            return;
        }

        if (heard.contains("open") || heard.contains("memory") || heard.contains("settings") ||
                heard.contains("help") || heard.contains("weather")) {
            executeCommand(heard);
        } else {
            lastHeardCommand = heard;
            isConfirming = true;
            String msg = isUrdu
                    ? "آپ نے کہا: " + heard + ". کیا آپ یہ کھولنا چاہتے ہیں؟ ہاں یا نہیں کہیں۔"
                    : "You said " + heard + ". Do you want to open it? Say yes or no.";
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_CMD");
        }
    }

    private void requestPermissionsAndStart() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            initTTSAndWelcome();
        }
    }

    private void initTTSAndWelcome() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.getDefault());
                isTTSInitialized = true; // ✅ TTS initialized

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        isMicActive = false;
                        try { speechRecognizer.cancel(); } catch (Exception ignored) {}
                    }

                    @Override public void onDone(String utteranceId) {
                        handler.postDelayed(() -> runOnUiThread(() -> {
                            if ("WELCOME_MSG".equals(utteranceId) ||
                                    "CONFIRM_CMD".equals(utteranceId) ||
                                    "RETRY_CMD".equals(utteranceId) ||
                                    "UNKNOWN_CMD".equals(utteranceId) ||
                                    "CONFIRM_ERR".equals(utteranceId) ||
                                    "ERROR".equals(utteranceId)) {
                                startListeningForCommands();
                            }
                        }), 700);
                    }

                    @Override public void onError(String utteranceId) { }
                });

                // ✅ Welcome message sirf first time bolega
                SharedPreferences themePrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
                boolean isThemeChanging = themePrefs.getBoolean("is_theme_changing", false);

                if (!isThemeChanging) {
                    speakWelcomeMessage();
                } else {
                    // Theme change ke case mein directly listening start karo
                    themePrefs.edit().putBoolean("is_theme_changing", false).apply();
                    startListeningForCommands();
                }
            }
        });
    }

    private void speakWelcomeMessage() {
        String message;
        if (isUrdu) {
            message = "آپ مین پیج پر ہیں جہاں آپ  بزم راہ Assistant تک رسائی حاصل کر سکتے ہیں یا Weather, Memory, Settings, یا Help کھول سکتے ہیں۔ آپ کیا کھولنا چاہتے ہیں؟ یا کیا آپ ایپ بند کرنا چاہتے ہیں؟";
        } else {
            message = "You are on The main page....Where You can access bazmayraah Assistant....... or can open Weather, Memory, Settings, or Help. What do you want to open?.........or do you want to exit app";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "WELCOME_MSG");
        } else {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null);
            handler.postDelayed(this::startListeningForCommands, 1500);
        }
    }

    private void startListeningForCommands() {
        if (isMicActive) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;

        try { speechRecognizer.cancel(); } catch (Exception ignored) {}

        handler.postDelayed(() -> {
            try {
                speechRecognizer.startListening(speechRecognizerIntent);
                isMicActive = true;
            } catch (Exception e) {
                isMicActive = false;
                String msg = isUrdu
                        ? "مائیکروفون شروع نہیں ہو سکا۔ براہ کرم اجازتیں چیک کریں۔"
                        : "Microphone can't start. Please check permissions.";
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "ERROR");
            }
        }, 200);
    }

    private void executeCommand(String command) {
        if (command == null) return;

        if (command.contains("memory")) {
            tts.speak(isUrdu ? "Memory کھول رہا ہوں۔" : "Opening Memory.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_MEMORY");
            startActivity(new Intent(this, MemoryActivity.class));
        } else if (command.contains("settings") || command.contains("setting")) {
            tts.speak(isUrdu ? "Settings کھول رہا ہوں۔" : "Opening Settings.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_SETTINGS");
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (command.contains("help")) {
            tts.speak(isUrdu ? "Help کھول رہا ہوں۔" : "Opening Help.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_HELP");
            startActivity(new Intent(this, HelpActivity.class));
        } else if (command.contains("weather") || command.contains("whether")) {
            tts.speak(isUrdu ? "Weather کھول رہا ہوں۔" : "Opening Weather.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_WEATHER");
            startActivity(new Intent(this, WeatherActivity.class));
        } else {
            tts.speak("Command not recognized. Please try again.",
                    TextToSpeech.QUEUE_FLUSH, null, "UNKNOWN_CMD");
        }
    }

    private void playBeep() {
        try { toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120); } catch (Exception ignored) {}
    }

    private void openMemoryPage() { startActivity(new Intent(this, MemoryActivity.class)); }
    private void openSettingsPage() { startActivity(new Intent(this, SettingsActivity.class)); }
    private void openHelpPage() { startActivity(new Intent(this, HelpActivity.class)); }
    private void openWeatherPage() { startActivity(new Intent(this, WeatherActivity.class)); }

    private void exitApp() {
        if (tts != null) {
            tts.speak(isUrdu ? "خیال رکھیں۔ اللہ حافظ۔" : "Take care. Allah Hafiz.", TextToSpeech.QUEUE_FLUSH, null, "EXIT_MSG");

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }

                @Override public void onDone(String utteranceId) {
                    if ("EXIT_MSG".equals(utteranceId)) {
                        runOnUiThread(() -> {
                            finishAffinity();
                            System.exit(0);
                        });
                    }
                }

                @Override public void onError(String utteranceId) {
                    runOnUiThread(() -> {
                        finishAffinity();
                        System.exit(0);
                    });
                }
            });
        } else {
            finishAffinity();
            System.exit(0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) if (res != PackageManager.PERMISSION_GRANTED) allGranted = false;

            if (allGranted) initTTSAndWelcome();
            else Toast.makeText(this, "All permissions are required for full app functionality.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
        if (toneGen != null) try { toneGen.release(); } catch (Exception ignored) {}
    }

}