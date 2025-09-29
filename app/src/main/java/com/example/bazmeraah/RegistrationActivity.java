package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class RegistrationActivity extends AppCompatActivity {

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private TextToSpeech tts;

    private String userName = "";
    private String userPhone = "";
    private String userEmergency = "";
    private int step = 0; // 0=name, 1=phone, 2=emergency, 3=confirm
    private boolean confirmStep = false;

    private EditText nameEditText, phoneEditText, emergencyEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        nameEditText = findViewById(R.id.nameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        emergencyEditText = findViewById(R.id.emergencyEditText);

        // Already registered?
        if (getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("isRegistered", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // Setup TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);

                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) { }

                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> new Handler().postDelayed(() -> startListening(), 800));
                    }

                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(() -> new Handler().postDelayed(() -> startListening(), 800));
                    }
                });
            }
        });

        // Check mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            startVoiceRegistration();
        }
    }

    private void startVoiceRegistration() {
        step = 0;
        confirmStep = false;
        askNext();
    }

    private void askNext() {
        switch (step) {
            case 0:
                speakAndListen("Please tell your name.");
                break;
            case 1:
                speakAndListen("Please tell your phone number.");
                break;
            case 2:
                speakAndListen("Please tell your emergency contact number.");
                break;
            case 3:
                speakAndListen("You said your name is " + userName +
                        ", phone number is " + userPhone +
                        ", and emergency contact is " + userEmergency +
                        ". Should I register?");
                confirmStep = true;
                return;
        }
        confirmStep = false;
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech Recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
                @Override
                public void onResults(Bundle results) {
                    try {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            handleVoice(matches.get(0));
                        } else {
                            speakAndListen("I did not hear anything. Please try again.");
                        }
                    } catch (Exception e) {
                        speakAndListen("Sorry, there was an error understanding you. Please try again.");
                    }
                }

                @Override
                public void onError(int error) {
                    speakAndListen("I did not hear anything. Please try again.");
                }
            });
        }

        if (speechIntent == null) {
            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        }

        speechRecognizer.startListening(speechIntent);
    }

    private void handleVoice(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) {
            speakAndListen("I did not catch that. Please try again.");
            return;
        }

        spokenText = spokenText.toLowerCase().trim();

        if (confirmStep) {
            if (spokenText.contains("yes") || spokenText.contains("confirm")) {
                if (step == 3) {
                    registerUser();
                } else {
                    step++;
                    askNext();
                }
            } else if (spokenText.contains("no")) {
                switch (step) {
                    case 0:
                        userName = "";
                        nameEditText.setText("");
                        speakAndListen("Okay, please tell your name again.");
                        break;
                    case 1:
                        userPhone = "";
                        phoneEditText.setText("");
                        speakAndListen("Okay, please tell your phone number again.");
                        break;
                    case 2:
                        userEmergency = "";
                        emergencyEditText.setText("");
                        speakAndListen("Okay, please tell your emergency contact again.");
                        break;
                    case 3:
                        step = 0;
                        userName = userPhone = userEmergency = "";
                        nameEditText.setText("");
                        phoneEditText.setText("");
                        emergencyEditText.setText("");
                        speakAndListen("Okay, let's try again from the beginning. Please tell your name.");
                        break;
                }
                confirmStep = false;
            } else {
                speakAndListen("Please say yes or no.");
            }
            return;
        }

        switch (step) {
            case 0:
                userName = spokenText;
                nameEditText.setText(userName);
                confirmStep = true;
                speakAndListen("You said your name is " + userName + ". Should I confirm?");
                break;
            case 1:
                userPhone = convertToDigits(spokenText);
                phoneEditText.setText(userPhone);
                confirmStep = true;
                speakAndListen("You said your phone number is " + userPhone + ". Should I confirm?");
                break;
            case 2:
                userEmergency = convertToDigits(spokenText);
                emergencyEditText.setText(userEmergency);
                confirmStep = true;
                speakAndListen("You said your emergency contact is " + userEmergency + ". Should I confirm?");
                break;
        }
    }

    // ✅ Helper to convert words → digits
    private String convertToDigits(String spokenText) {
        spokenText = spokenText.toLowerCase()
                .replaceAll("zero", "0")
                .replaceAll("one", "1")
                .replaceAll("two", "2")
                .replaceAll("three", "3")
                .replaceAll("four", "4")
                .replaceAll("five", "5")
                .replaceAll("six", "6")
                .replaceAll("seven", "7")
                .replaceAll("eight", "8")
                .replaceAll("nine", "9");

        return spokenText.replaceAll("[^0-9]", "");
    }

    private void registerUser() {
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                .putBoolean("isRegistered", true)
                .putString("name", userName)
                .putString("phone", userPhone)
                .putString("emergency", userEmergency)
                .apply();

        //speakAndListen("Registration successful. Welcome " + userName);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void speakAndListen(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRegistration();
        } else {
            speakAndListen("Microphone permission is required for voice registration.");
        }
    }
}