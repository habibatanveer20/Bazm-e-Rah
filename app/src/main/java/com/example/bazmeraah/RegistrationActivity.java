package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Locale;

public class RegistrationActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference database;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private TextToSpeech tts;

    private String userName = "";
    private String userPhone = "";
    private String userEmergency = "";
    private int step = 0;

    private EditText nameEditText, phoneEditText, emergencyEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance().getReference("users");

        nameEditText = findViewById(R.id.nameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        emergencyEditText = findViewById(R.id.emergencyEditText);

        // Already registered?
        if (getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("isRegistered", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // Check mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            startVoiceRegistration();
        }

        // Setup TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });
    }

    private void startVoiceRegistration() {
        step = 0;
        speak(" For registration, please tell your name.");
        startListening();
    }

    private void startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (speechRecognizer != null) speechRecognizer.destroy();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

            speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        handleVoice(matches.get(0));
                    } else {
                        speak("I did not hear anything. Please try again.");
                        startListening();
                    }
                }

                @Override
                public void onError(int error) {
                    speak("I did not hear anything. Please try again.");
                    startListening();
                }
            });

            speechRecognizer.startListening(speechIntent);
        } else {
            Toast.makeText(this, "Speech Recognition not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleVoice(String spokenText) {
        spokenText = spokenText.toLowerCase().trim();

        switch (step) {
            case 0: // Name
                userName = spokenText;
                nameEditText.setText(userName);
                step = 1;
                speak("You said your name is " + userName + ". Should I confirm?");
                break;

            case 1: // Confirm Name
                if (spokenText.contains("yes")) {
                    step = 2;
                    speak("Thank you. Now please tell your phone number.");
                } else {
                    step = 0;
                    speak("Okay, please tell your name again.");
                }
                break;

            case 2: // Phone
                userPhone = spokenText.replaceAll("[^0-9]", "");
                phoneEditText.setText(userPhone);
                step = 3;
                speak("You said your phone number is " + userPhone + ". Should I confirm?");
                break;

            case 3: // Confirm Phone
                if (spokenText.contains("yes")) {
                    step = 4;
                    speak("Now please tell your emergency contact number.");
                } else {
                    step = 2;
                    speak("Okay, please tell your phone number again.");
                }
                break;

            case 4: // Emergency
                userEmergency = spokenText.replaceAll("[^0-9]", "");
                emergencyEditText.setText(userEmergency);
                step = 5;
                speak("You said your emergency contact is " + userEmergency + ". Should I confirm?");
                break;

            case 5: // Confirm Emergency
                if (spokenText.contains("yes")) {
                    step = 6;
                    // Final summary + ask for registration
                    speak("Your name is " + userName + ", phone number is " + userPhone +
                            ", and emergency contact is " + userEmergency + ". Should I register?");

                    // *Important fix:* start listening for final confirmation after a short delay
                    // to avoid capturing the TTS audio itself.
                    new Handler(Looper.getMainLooper()).postDelayed(this::startListening, 1400);

                } else {
                    step = 4;
                    speak("Okay, please tell your emergency contact again.");
                }
                break;

            case 6: // Final confirmation
                if (spokenText.contains("yes") || spokenText.contains("yeah")
                        || spokenText.contains("ok") || spokenText.contains("confirm")
                        || spokenText.contains("register") || spokenText.contains("done")) {
                    registerUser();
                    return; // stop loop
                } else {
                    step = 0;
                    speak("Okay, let's start again. Please tell your name.");
                    startListening();
                }
                break;
        }

        // Only restart listening for intermediate steps (0..5)
        if (step < 6) startListening();
    }

    private void registerUser() {
        // Stop listening to avoid conflict
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        // Just save in SharedPreferences and move ahead
        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("isRegistered", true)
                .putString("name", userName)
                .putString("phone", userPhone)
                .putString("emergency", userEmergency)
                .apply();

        speak("Registration successful. Welcome " + userName);

        // Delay before moving to MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 2500);
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRegistration();
        } else {
            speak("Microphone permission is required for voice registration.");
        }
    }
}