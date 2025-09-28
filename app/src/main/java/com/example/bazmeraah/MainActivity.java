package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.media.ToneGenerator;
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

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 300;

    private Button btnMemory, btnSettings, btnHelp, btnWeather;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextToSpeech tts;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        // Manual navigation buttons
        btnMemory.setOnClickListener(v -> openMemoryPage());
        btnSettings.setOnClickListener(v -> openSettingsPage());
        btnHelp.setOnClickListener(v -> openHelpPage());
        btnWeather.setOnClickListener(v -> openWeatherPage());

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        checkLocationPermission();
        checkCameraPermission();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
                speakWelcomeMessage();
            }
        });
    }

    private void speakWelcomeMessage() {
        String message = "You have arrived at the main page. Here you can access Weather, Memory, Settings, AI Assistant, and Help. What assistance do you need?";
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "WELCOME_MSG");

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) {
                if (utteranceId.equals("WELCOME_MSG")) runOnUiThread(() -> startMicIfPermissionGranted());
            }
            @Override public void onError(String utteranceId) { }
        });
    }

    private void startMicIfPermissionGranted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            listenForCommand();
        }
    }

    private void listenForCommand() {
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { playBeep(); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override
            public void onError(int error) { retryListening(); }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String command = matches.get(0).toLowerCase(Locale.ROOT);
                    askConfirmation(command);
                } else retryListening();
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        speechRecognizer.startListening(speechRecognizerIntent);
    }

    private void askConfirmation(String command) {
        tts.speak("You said: " + command + ". Is that correct?", TextToSpeech.QUEUE_FLUSH, null, "CONFIRM_CMD");
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) {
                if (utteranceId.equals("CONFIRM_CMD")) runOnUiThread(() -> listenForConfirmation(command));
            }
            @Override public void onError(String utteranceId) { }
        });
    }

    private void listenForConfirmation(String command) {
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { playBeep(); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) { retryListening(); }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String response = matches.get(0).toLowerCase(Locale.ROOT);
                    if (response.contains("yes")) executeCommand(command);
                    else {
                        tts.speak("Okay, please repeat your command.", TextToSpeech.QUEUE_FLUSH, null, "RETRY_CMD");
                        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                            @Override public void onStart(String utteranceId) { }
                            @Override public void onDone(String utteranceId) { if (utteranceId.equals("RETRY_CMD")) listenForCommand(); }
                            @Override public void onError(String utteranceId) { }
                        });
                    }
                } else retryListening();
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        speechRecognizer.startListening(speechRecognizerIntent);
    }

    private void executeCommand(String command) {
        if (command.contains("memory")) openMemoryPage();
        else if (command.contains("settings")) openSettingsPage();
        else if (command.contains("help")) openHelpPage();
        else if (command.contains("weather")) openWeatherPage();
        else if (command.contains("exit") || command.contains("close") || command.contains("quit")) exitApp();

        // After executing, start listening for next command
        listenForCommand();
    }

    private void retryListening() {
        new android.os.Handler().postDelayed(this::listenForCommand, 1000);
    }

    private void playBeep() {
        ToneGenerator toneGen = new ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
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

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                Toast.makeText(this, "Location permission denied! Some features may not work.", Toast.LENGTH_LONG).show();
        }
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                Toast.makeText(this, "Camera permission denied! Some features may not work.", Toast.LENGTH_LONG).show();
        }
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                startMicIfPermissionGranted();
            else
                Toast.makeText(this, "Microphone permission denied! Cannot use voice commands.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
    }
}
