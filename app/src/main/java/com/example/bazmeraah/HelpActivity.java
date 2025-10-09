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

        // Read language preference
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        boolean isUrdu = prefs.getBoolean("language_urdu", false);

        // Setup speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(globalListener);

        requestPermissionsAndStart(isUrdu);
    }

    // 🔊 Recognition listener
    private final RecognitionListener globalListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { playBeep(); }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            isMicActive = false;
            speakMessage("I didn't catch that. Please say again.", "میں نے نہیں سنا، دوبارہ کہیں۔");
        }

        @Override
        public void onResults(Bundle results) {
            isMicActive = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                speakMessage("I didn't hear anything. Please say again.", "میں نے کچھ نہیں سنا، دوبارہ کہیں۔");
                return;
            }

            String heard = matches.get(0).toLowerCase(Locale.ROOT).trim();
            Toast.makeText(HelpActivity.this, "Heard: " + heard, Toast.LENGTH_SHORT).show();
            processCommand(heard);
        }

        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    };

    // 🎤 Ask for permissions
    private void requestPermissionsAndStart(boolean isUrdu) {
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
            initTTSAndWelcome(isUrdu);
        }
    }

    // 🗣 Initialize TTS
    private void initTTSAndWelcome(boolean isUrdu) {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                if (isUrdu) tts.setLanguage(new Locale("ur", "PK"));
                else tts.setLanguage(Locale.US);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        try { speechRecognizer.cancel(); } catch (Exception ignored) {}
                        isMicActive = false;
                    }

                    @Override public void onDone(String utteranceId) {
                        handler.postDelayed(() -> runOnUiThread(() -> {
                            if ("WELCOME_MSG".equals(utteranceId) ||
                                    "CMD".equals(utteranceId)) {
                                startListening();
                            }
                        }), 700);
                    }

                    @Override public void onError(String utteranceId) {}
                });

                speakWelcome(isUrdu);
            }
        });
    }

    private void speakWelcome(boolean isUrdu) {
        String msgEn = "You are on the Help Page. You can say: Call Emergency, Send Message, Read Tips, Contact Support, or Exit Help to go back to main page.";
        String msgUr = "آپ Help Page پر ہیں۔ آپ کہہ سکتے ہیں: Emergency Call کریں، Message بھیجیں، Tips پڑھیں، Contact Support جائیں یا Main Page پر واپس جائیں۔";
        String msg = isUrdu ? msgUr : msgEn;

        speak(msg, "WELCOME_MSG");
    }

    // 🎙 Start listening
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
                speakMessage("Microphone can't start. Please check permissions.", "مائیکروفون شروع نہیں ہو سکا، permissions چیک کریں۔");
            }
        }, 200);
    }

    // 🧠 Command Processing
    private void processCommand(String command) {

        // ✅ Step 1: Handle yes/no confirmation
        if (awaitingConfirmation) {
            if (command.contains("yes") || command.contains("yeah") || command.contains("haan")
                    || command.contains("han") || command.contains("جی ہاں") || command.contains("ہاں")) {
                awaitingConfirmation = false;
                if ("call".equals(pendingAction)) callEmergency();
            } else if (command.contains("no") || command.contains("nah") || command.contains("نہیں") || command.contains("نہ")) {
                awaitingConfirmation = false;
                speakMessage("Okay, cancelled. What do you want to do?", "ٹھیک ہے، منسوخ کر دیا گیا۔ آپ کیا کرنا چاہتے ہیں؟");
            } else {
                speakMessage("Please say yes or no.", "براہ کرم جی ہاں یا نہیں کہیں۔");
            }
            return;
        }

        // ✅ Step 2: Handle main commands
        if (command.contains("call") && command.contains("emergency")) {
            awaitingConfirmation = true;
            pendingAction = "call";
            speakMessage("Do you want to call your emergency contact? Say yes or no.",
                    "کیا آپ اپنے emergency contact کو call کرنا چاہتے ہیں؟ جی ہاں یا نہیں کہیں۔");

        } else if (command.contains("message")) {
            sendEmergencyMessage();

        } else if (command.contains("support")) {
            openSupportPage();

        } else if (command.contains("read") || command.contains("tips")) {
            readTips();

        } else if (command.contains("exit") || command.contains("main page") || command.contains("go back")) {
            speakMessage("Going back to main page.", "Main Page پر واپس جا رہے ہیں۔");
            Intent intent = new Intent(HelpActivity.this, MainActivity.class);
            startActivity(intent);
            finish();

        } else {
            speakMessage("Sorry, I didn't understand. Please say again.", "معاف کریں، میں نے سمجھا نہیں۔ دوبارہ کہیں۔");
        }
    }

    // ☎️ Emergency Call
    private void callEmergency() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String number = prefs.getString("emergency", null);

        if (number != null && !number.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED) {
                startActivity(intent);
            } else {
                speakMessage("Permission to call is not granted.", "Call کرنے کی اجازت نہیں ہے۔");
            }
        } else {
            speakMessage("No emergency contact found in your registration.", "آپ کی رجسٹریشن میں emergency contact نہیں ملا۔");
        }
    }

    // 📨 Message placeholder
    private void sendEmergencyMessage() {
        speakMessage("Sending emergency message feature is under development.", "Emergency message بھیجنے کا فیچر ابھی تیار ہو رہا ہے۔");
    }

    // 🔗 Support Page
    private void openSupportPage() {
        speakMessage("Opening support page.", "Support Page کھول رہے ہیں۔");
        startActivity(new Intent(this, ContactSupportActivity.class));
    }

    // 💡 Tips
    private void readTips() {
        String tipsEn = "Tip 1: You can call your emergency contact anytime by saying Call Emergency. Tip 2: Contact support for any help.";
        String tipsUr = "ٹپ 1: آپ کسی بھی وقت اپنے emergency contact کو call کر سکتے ہیں۔ ٹپ 2: مدد کے لیے support سے رابطہ کریں۔";
        speakMessage(tipsEn, tipsUr);
    }

    // 🗣 Helpers
    private void speakMessage(String msgEn, String msgUr) {
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        boolean isUrdu = prefs.getBoolean("language_urdu", false);
        String msg = isUrdu ? msgUr : msgEn;
        speak(msg, "CMD");
    }

    private void speak(String msg, String id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, id);
        else
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void playBeep() {
        try { toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120); } catch (Exception ignored) {}
    }

    // ⚙️ Permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) if (res != PackageManager.PERMISSION_GRANTED) allGranted = false;
            if (allGranted) {
                boolean isUrdu = getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("language_urdu", false);
                initTTSAndWelcome(isUrdu);
            } else {
                Toast.makeText(this, "All permissions are required for Help page.", Toast.LENGTH_LONG).show();
            }
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
