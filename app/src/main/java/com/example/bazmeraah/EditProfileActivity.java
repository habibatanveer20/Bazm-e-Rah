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
            String lower = spokenText.toLowerCase();

            boolean isYes = lower.contains("yes") || lower.contains("haan") || lower.contains("haanji") || spokenText.contains("ہاں");
            boolean isNo = lower.contains("no") || lower.contains("nahin") || lower.contains("nahi") ||
                    lower.contains("nai") || lower.contains("na") || spokenText.contains("نہیں") ||
                    spokenText.contains("نہ") || spokenText.contains("نا");

            if (isYes) {
                confirmStep = false;
                step++;
                askNextField();
            } else if (isNo) {
                confirmStep = false; // Important: reset confirmStep before re-asking
                speak(isUrdu ? "ٹھیک ہے، دوبارہ بتائیں۔" : "Alright, please say it again.", this::startListening);
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
                    String spokenText = matches.get(0).toLowerCase();

                    if (spokenText.contains("yes") || spokenText.contains("haan") || spokenText.contains("ہاں")) {
                        saveProfile();  // ✅ yahan call karo
                    } else if (spokenText.contains("no") || spokenText.contains("نہیں")) {
                        speak(isUrdu ? "ٹھیک ہے، محفوظ نہیں کیا گیا۔" : "Okay, profile not saved.", null);
                    } else {
                        repeatListening();
                    }
                } else {
                    repeatListening();
                }
            }

            @Override
            public void onError(int error) {
                repeatListening();
            }
        });

        speechRecognizer.startListening(intent);
    }

    private void saveProfile() {
        userName = nameEditText.getText().toString().trim();
        userPhone = phoneEditText.getText().toString().trim();
        userEmergency = emergencyEditText.getText().toString().trim();

        SharedPreferences.Editor editor = getSharedPreferences("UserPrefs", MODE_PRIVATE).edit();
        editor.putString("name", userName);
        editor.putString("phone", userPhone);
        editor.putString("emergency", userEmergency);
        editor.putBoolean("isRegistered", true); // ✅ ye line zaroor add karo
        editor.apply();

        speak(isUrdu ? "پروفائل محفوظ ہو گئی۔" : "Profile saved successfully.", this::askNextAction);
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

        // ✅ Always listen in English for navigation commands
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0);
                    handleFinalDecision(spokenText);
                } else repeatListening();
            }

            @Override
            public void onError(int error) { repeatListening(); }
        });

        speechRecognizer.startListening(intent);
    }

    private void handleFinalDecision(String spokenText) {
        if (spokenText == null) { repeatListening(); return; }

        String lower = spokenText.toLowerCase();

        // ✅ Smarter matching (accounts for full phrases)
        if (lower.contains("main") || lower.contains("home") || lower.contains("go back") ||
                lower.contains("exit") || lower.contains("close") ||
                lower.contains("مین") || lower.contains("ہوم") ||
                lower.contains("باہر") || lower.contains("ایگزٹ")) {

            Intent intent = new Intent(EditProfileActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            speak(isUrdu ? "سمجھ نہیں آیا۔ دوبارہ کہیں" : "Didn't catch that. Please repeat", this::startListeningForFinal);
        }
    }

    private void speak(String text, Runnable onDone) {
        if (tts != null && isVoiceActive) {
            String utteranceId = String.valueOf(System.currentTimeMillis());

            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    // Nothing here
                }

                @Override
                public void onDone(String utteranceId) {
                    if (onDone != null) {
                        mainHandler.post(onDone); // Run after speech actually ends
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    if (onDone != null) {
                        mainHandler.post(onDone); // In case of error, continue
                    }
                }
            });

            // Speak with utterance ID so listener can detect end
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);

        } else if (onDone != null) {
            onDone.run();
        }
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