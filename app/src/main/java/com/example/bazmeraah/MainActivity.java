package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

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

        // recognizer + intent
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(globalListener);

        requestPermissionsAndStart();
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

            // Handle confirmation-specific transient errors differently
            if (isConfirming) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Did not get a clear "yes/no" — ask again for yes/no and KEEP confirmation state
                    tts.speak("I didn't hear yes or no. Please say yes or no.",
                            TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_ERR");
                    // onDone of CONFIRM_ERR will restart listening (no reset of isConfirming or lastHeardCommand)
                } else {
                    // Other errors — reset confirmation and ask for full command again
                    isConfirming = false;
                    lastHeardCommand = null;
                    tts.speak("I could not confirm. Please say the full command like open weather or open memory.",
                            TextToSpeech.QUEUE_FLUSH, null, "RETRY_CMD");
                }
            } else {
                // Normal (non-confirmation) errors
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    tts.speak("I didn't catch that. Please say the command again.",
                            TextToSpeech.QUEUE_FLUSH, null, "ERROR");
                } else {
                    tts.speak("Microphone error. Please try again.",
                            TextToSpeech.QUEUE_FLUSH, null, "ERROR");
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            isMicActive = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                // No results returned
                if (isConfirming) {
                    // keep confirmation and ask for yes/no again
                    tts.speak("I didn't hear yes or no. Please say yes or no.",
                            TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_ERR");
                } else {
                    tts.speak("I didn't hear anything. Please say the command again.",
                            TextToSpeech.QUEUE_FLUSH, null, "ERROR");
                }
                return;
            }

            String heard = matches.get(0).toLowerCase(Locale.ROOT).trim();
            Toast.makeText(MainActivity.this, "Heard: " + heard, Toast.LENGTH_SHORT).show();

            if (isConfirming) {
                // Expecting yes/no only
                if (heard.contains("yes")) {
                    isConfirming = false;
                    if (lastHeardCommand != null) {
                        executeCommand(lastHeardCommand);
                        lastHeardCommand = null;
                    } else {
                        // Somehow command lost — ask for full command
                        tts.speak("Sorry I lost the command. Please say the full command like open weather.",
                                TextToSpeech.QUEUE_FLUSH, null, "UNKNOWN_CMD");
                    }
                } else if (heard.contains("no")) {
                    isConfirming = false;
                    lastHeardCommand = null;
                    tts.speak("Okay, please say your command again.",
                            TextToSpeech.QUEUE_FLUSH, null, "RETRY_CMD");
                } else {
                    // Not yes/no — ask again for yes/no (do not reset lastHeardCommand)
                    tts.speak("Please say yes or no.",
                            TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_ERR");
                }
            } else {
                // Normal command stage
                // If user accidentally says short filler words, ask for full command
                if (heard.equals("yes") || heard.equals("no") || heard.equals("ok") || heard.equals("okay")) {
                    tts.speak("Please say the full command like open weather or open help.",
                            TextToSpeech.QUEUE_FLUSH, null, "UNKNOWN_CMD");
                    return;
                }

                // If user explicitly used "open", execute immediately
                if (heard.contains("open")) {
                    executeCommand(heard);
                } else {
                    // Ambiguous — ask confirmation (yes/no) but KEEP lastHeardCommand
                    lastHeardCommand = heard;
                    isConfirming = true;
                    tts.speak("You said " + heard + ". Do you want to open it? Say yes or no.",
                            TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_CMD");
                }
            }
        }

        @Override public void onPartialResults(Bundle partialResults) { }
        @Override public void onEvent(int eventType, Bundle params) { }
    };

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
                tts.setLanguage(Locale.getDefault());

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        // stop any listening while TTS speaking
                        isMicActive = false;
                        try { speechRecognizer.cancel(); } catch (Exception ignored) {}
                    }

                    @Override public void onDone(String utteranceId) {
                        // longer delay to avoid echo (700ms)
                        handler.postDelayed(() -> runOnUiThread(() -> {
                            // safe equals to avoid NPE
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

                speakWelcomeMessage();
            }
        });
    }

    private void speakWelcomeMessage() {
        String message = "You are on The main page. Where You can access bazmyraah Assistant or can open Weather, Memory, Settings, or Help. What do you want to open?";
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
                tts.speak("Microphone can't start. Please check permissions.",
                        TextToSpeech.QUEUE_FLUSH, null, "ERROR");
            }
        }, 200);
    }

    private void executeCommand(String command) {
        if (command == null) return;

        if (command.contains("memory")) {
            tts.speak("Opening Memory.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_MEMORY");
            startActivity(new Intent(this, MemoryActivity.class));
        } else if (command.contains("settings") || command.contains("setting")) {
            tts.speak("Opening Settings.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_SETTINGS");
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (command.contains("help")) {
            tts.speak("Opening Help.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_HELP");
            startActivity(new Intent(this, HelpActivity.class));
        } else if (command.contains("weather") || command.contains("whether")) {
            tts.speak("Opening Weather.", TextToSpeech.QUEUE_FLUSH, null, "OPEN_WEATHER");
            startActivity(new Intent(this, WeatherActivity.class));
        } else if (command.contains("exit") || command.contains("close") || command.contains("quit")) {
            exitApp();
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
        Toast.makeText(this, "Exiting app...", Toast.LENGTH_SHORT).show();
        finishAffinity();
        System.exit(0);
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
    @Override
    protected void onResume() {
        super.onResume();

        if (tts != null) {
            speakWelcomeMessage();
        }
    }

}
