package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class HelpActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 501;

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextToSpeech tts;
    private ToneGenerator toneGen;
    private Handler handler = new Handler();

    private boolean isMicActive = false;
    private boolean awaitingConfirmation = false;
    private String pendingAction = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

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

    // recognition listener
    private final RecognitionListener globalListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { playBeep(); }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            isMicActive = false;
            speak("I didn't catch that. Please say again.");
        }

        @Override
        public void onResults(Bundle results) {
            isMicActive = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                speak("I didn't hear anything. Please say again.");
                return;
            }

            String heard = matches.get(0).toLowerCase(Locale.ROOT).trim();
            Toast.makeText(HelpActivity.this, "Heard: " + heard, Toast.LENGTH_SHORT).show();
            processCommand(heard);
        }

        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    };

    // ask for permissions
    private void requestPermissionsAndStart() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.CALL_PHONE);

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            initTTSAndWelcome();
        }
    }

    // init tts
    private void initTTSAndWelcome() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        try { speechRecognizer.cancel(); } catch (Exception ignored) {}
                        isMicActive = false;
                    }

                    @Override public void onDone(String utteranceId) {
                        handler.postDelayed(() -> runOnUiThread(() -> {
                            if ("WELCOME_MSG".equals(utteranceId) ||
                                    "ERROR".equals(utteranceId) ||
                                    "CMD".equals(utteranceId)) {
                                startListening();
                            }
                        }), 700);
                    }

                    @Override public void onError(String utteranceId) {}
                });

                speakWelcome();
            }
        });
    }

    private void speakWelcome() {
        String msg = "You are on the Help Page. You can say: Call Emergency, Send Message,  or want to know Bamyrah Tips, Or for any query and for need more help you can directly go to ,Contact Support, regarding bazmyrah or Exit Help to go back to main page.";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "WELCOME_MSG");
        } else {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
            handler.postDelayed(this::startListening, 1500);
        }
    }

    // start listening
    private void startListening() {
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
                speak("Microphone can't start. Please check permissions.");
            }
        }, 200);
    }

    // process commands
    private void processCommand(String command) {
        if (awaitingConfirmation) {
            if (command.contains("yes")) {
                awaitingConfirmation = false;
                if ("call".equals(pendingAction)) callEmergency();
            } else if (command.contains("no")) {
                awaitingConfirmation = false;
                speak("Okay, cancelled. What do you want to do?");
            } else {
                speak("Please say yes or no.");
            }
            return;
        }

        if (command.contains("call") && command.contains("emergency")) {
            awaitingConfirmation = true;
            pendingAction = "call";
            speak("Do you want to call your emergency contact? Say yes or no.");

        } else if (command.contains("message")) {
            sendEmergencyMessage();

        } else if (command.contains("support") || command.contains("spot") || command.contains("sport")) {
            openSupportPage();

        } else if (command.contains("read")) {
            readTips();

        } else if (command.contains("exit") || command.contains("main page") || command.contains("go back")) {
            speak("Going back to main page.");
            Intent intent = new Intent(HelpActivity.this, MainActivity.class);
            startActivity(intent);
            finish();

        } else {
            speak("Sorry, I didn't understand. Please say again.");
        }
    }

    // actions
    private void callEmergency() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String number = prefs.getString("emergency_contact", null);

        if (number != null) {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED) {
                startActivity(intent);
            } else {
                speak("Permission to call is not granted.");
            }
        } else {
            speak("No emergency contact found in your registration.");
        }
    }

    private void sendEmergencyMessage() {
        speak("Sending emergency message feature is under development.");
        // later add SMS sending here
    }

    private void openSupportPage() {
        speak("Opening support page.");
        Intent intent = new Intent(this, ContactSupportActivity.class);
        startActivity(intent);
    }

    private void readTips() {
        String tips = "Tip 1. You can call your emergency contact anytime by saying Call Emergency. Tip 2. You can also contact support if you need help.";
        speak(tips);
    }

    // helpers
    private void speak(String msg) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "CMD");
        } else {
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private void playBeep() {
        try { toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120); } catch (Exception ignored) {}
    }

    // permissions result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) if (res != PackageManager.PERMISSION_GRANTED) allGranted = false;
            if (allGranted) initTTSAndWelcome();
            else Toast.makeText(this, "All permissions are required for Help page.", Toast.LENGTH_LONG).show();
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
