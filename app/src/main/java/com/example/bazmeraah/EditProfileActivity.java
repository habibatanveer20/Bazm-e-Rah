package com.example.bazmeraah;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class EditProfileActivity extends AppCompatActivity {

    private EditText nameEditText, phoneEditText, emergencyEditText;
    private Button saveButton;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean isUrdu = false;
    private boolean isVoiceActive = true;
    private int step = 0;
    private boolean confirmStep = false;

    private String userName, userPhone, userEmergency;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        nameEditText = findViewById(R.id.edit_name);
        phoneEditText = findViewById(R.id.edit_phone);
        emergencyEditText = findViewById(R.id.edit_emergency_number);
        saveButton = findViewById(R.id.btn_save_profile);

        loadUserPrefs();
        isUrdu = getSharedPreferences("AppSettings", MODE_PRIVATE).getBoolean("language_urdu", false);

        setupTTS();
        setupSaveButton();
    }

    private void loadUserPrefs() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userName = prefs.getString("name", "");
        userPhone = prefs.getString("phone", "");
        userEmergency = prefs.getString("emergency", "");

        nameEditText.setText(userName);
        phoneEditText.setText(userPhone);
        emergencyEditText.setText(userEmergency);
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.US);
                mainHandler.postDelayed(() -> speak(
                        isUrdu ? "آپ پروفائل ایڈٹ کر رہی ہیں" : "You are editing your profile",
                        this::startVoiceEditing), 500);
            }
        });
    }

    private void setupSaveButton() {
        saveButton.setOnClickListener(v -> askSaveConfirmation());
    }

    private void startVoiceEditing() {
        step = 0;
        askNextField();
    }

    private void askNextField() {
        String msg = "";
        switch (step) {
            case 0: msg = isUrdu ? "اپنا نام بتائیں" : "Please say your name"; break;
            case 1: msg = isUrdu ? "فون نمبر بتائیں" : "Please say your phone number"; break;
            case 2: msg = isUrdu ? "ایمرجنسی نمبر بتائیں" : "Please say your emergency number"; break;
            case 3: askSaveConfirmation(); return;
        }
        speak(msg, this::startListening);
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isUrdu ? new Locale("ur", "PK") : Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) handleVoiceInput(matches.get(0));
                else repeatListening();
            }

            @Override
            public void onError(int error) { repeatListening(); }
        });

        speechRecognizer.startListening(intent);
    }

    private void handleVoiceInput(String spokenText) {
        if (spokenText == null || spokenText.isEmpty()) { repeatListening(); return; }
        spokenText = spokenText.trim();

        if (confirmStep) {
            if (spokenText.toLowerCase().contains("yes") ||
                    spokenText.contains("haan") || spokenText.contains("ہاں")) {
                confirmStep = false;
                step++;
                askNextField();
            } else if (spokenText.toLowerCase().contains("no") ||
                    spokenText.contains("nahin") || spokenText.contains("نہیں")) {
                repeatListening();
            } else {
                repeatListening();
            }
            return;
        }

        switch (step) {
            case 0:
                userName = spokenText; nameEditText.setText(userName);
                askConfirmation(isUrdu ? "آپ نے نام " + userName + " بتایا۔ درست ہے؟"
                        : "You said name " + userName + ". Correct?");
                break;
            case 1:
                userPhone = spokenText.replaceAll("[^0-9]", "");
                phoneEditText.setText(userPhone);
                askConfirmation(isUrdu ? "آپ نے فون " + userPhone + " بتایا۔ درست ہے؟"
                        : "You said phone " + userPhone + ". Correct?");
                break;
            case 2:
                userEmergency = spokenText.replaceAll("[^0-9]", "");
                emergencyEditText.setText(userEmergency);
                askConfirmation(isUrdu ? "آپ نے ایمرجنسی نمبر " + userEmergency + " بتایا۔ درست ہے؟"
                        : "You said emergency " + userEmergency + ". Correct?");
                break;
        }
    }

    private void askConfirmation(String msg) {
        confirmStep = true;
        speak(msg, this::startListening);
    }

    private void repeatListening() {
        speak(isUrdu ? "سمجھ نہیں آیا۔ دوبارہ کہیں" : "Didn't catch that. Please repeat", this::startListening);
    }

    private void askSaveConfirmation() {
        confirmStep = true;
        speak(isUrdu ? "کیا آپ پروفائل محفوظ کرنا چاہیں گی؟" : "Do you want to save your profile?", this::saveProfileDecision);
    }

    private void saveProfileDecision() {
        startListening(); // listen for yes/no to save
    }

    private void saveProfile() {
        SharedPreferences.Editor editor = getSharedPreferences("UserPrefs", MODE_PRIVATE).edit();
        editor.putString("name", userName);
        editor.putString("phone", userPhone);
        editor.putString("emergency", userEmergency);
        editor.apply();

        speak(isUrdu ? "پروفائل محفوظ ہو گئی۔" : "Profile saved successfully", this::askNextAction);
    }

    private void askNextAction() {
        speak(isUrdu ? "کیا آپ مین پیج پر جانا چاہیں گی یا کچھ اور کریں گی؟"
                        : "Do you want to go to the main page or do something else?",
                this::startListeningForFinal);
    }

    private void startListeningForFinal() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isUrdu ? new Locale("ur", "PK") : Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleFinalDecision(matches.get(0));
                } else repeatListening();
            }

            @Override
            public void onError(int error) { repeatListening(); }
        });

        speechRecognizer.startListening(intent);
    }

    private void handleFinalDecision(String spokenText) {
        if (spokenText.contains("main") || spokenText.contains("home") ||
                spokenText.contains("مین") || spokenText.contains("ہوم") ||
                spokenText.contains("exit") || spokenText.contains("بند") || spokenText.contains("ایگزٹ")) {

            // Dono case me MainActivity open
            Intent intent = new Intent(EditProfileActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // Sirf EditProfileActivity band hogi, app nahi
        } else {
            repeatListening();
        }
    }

    private void speak(String text, Runnable onDone) {
        if (tts != null && isVoiceActive) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID");
            if (onDone != null) mainHandler.postDelayed(onDone, 2000);
        } else if (onDone != null) onDone.run();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
