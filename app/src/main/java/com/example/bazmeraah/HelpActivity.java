package com.example.bazmeraah;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class HelpActivity extends AppCompatActivity {

    private TextToSpeech tts;

    private Button btnReadAloud, btnCallEmergency, btnMessageEmergency, btnContactSupport;

    private final String HELP_TEXT =
            "This is the Help page. Available voice commands: " +
                    "Say 'Emergency' to call emergency contact, " +
                    "'Support' or 'Contact' to open contact support page, " +
                    "and 'Exit' or 'Close' to go back to main menu.";

    private String emergencyNumber; // fetched from SharedPreferences
    private static final int VOICE_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Buttons
        btnReadAloud = findViewById(R.id.btn_read_aloud);
        btnCallEmergency = findViewById(R.id.btn_call_emergency);
        btnMessageEmergency = findViewById(R.id.btn_message_emergency);
        btnContactSupport = findViewById(R.id.btn_contact_support);

        // Fetch emergency number
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        emergencyNumber = prefs.getString("emergency", "1234567890");

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
                speak("This is the Help page. " + HELP_TEXT);
            }
        });

        // Button Listeners
        btnReadAloud.setOnClickListener(v -> speak(HELP_TEXT));
        btnCallEmergency.setOnClickListener(v -> callEmergency());
        btnMessageEmergency.setOnClickListener(v -> messageEmergency());
        btnContactSupport.setOnClickListener(v -> openContactSupport());

        // Start voice recognition automatically
        startVoiceRecognition();
    }

    private void callEmergency() {
        speak("Calling emergency contact");
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + emergencyNumber));
        startActivity(callIntent);
    }

    private void messageEmergency() {
        speak("Opening messaging app for emergency contact");
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + emergencyNumber));
        startActivity(smsIntent);
    }

    private void openContactSupport() {
        speak("Opening contact support page");
        startActivity(new Intent(this, ContactSupportActivity.class));
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HELP_READ");
        }
    }

    // Voice recognition
    private void startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command");

        startActivityForResult(intent, VOICE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                handleVoiceCommand(results.get(0).toLowerCase());
            }
        }
    }

    private void handleVoiceCommand(String command) {
        if (command.contains("emergency")) {
            callEmergency();
        } else if (command.contains("support") || command.contains("contact")) {
            openContactSupport();
        } else if (command.contains("exit") || command.contains("close")) {
            speak("Going back to main menu");
            // Redirect to MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        } else {
            speak("Command not recognized. " + HELP_TEXT);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
