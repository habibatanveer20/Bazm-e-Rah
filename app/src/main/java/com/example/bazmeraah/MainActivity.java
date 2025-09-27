package com.example.bazmeraah;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.widget.ImageButton;
import android.widget.TextView;
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

    private ImageButton btnMic;
    private TextView tvTranscript;
    private TextView btnMemory, btnSettings, btnHelp, btnWeather;  // ✅ FIXED

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is already registered, otherwise go to RegistrationActivity
        if (!getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("isRegistered", false)) {
            startActivity(new Intent(this, RegistrationActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        btnMic = findViewById(R.id.btnMic);
        tvTranscript = findViewById(R.id.tvTranscript);
        btnMemory = findViewById(R.id.btnMemory);
        btnSettings = findViewById(R.id.btnSettings);
        btnHelp = findViewById(R.id.btnHelp);
        btnWeather = findViewById(R.id.btnWeather);

        // --- Speech Recognizer Setup ---
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvTranscript.setText("Listening...");
            }

            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }

            @Override
            public void onError(int error) {
                tvTranscript.setText("Could not recognize. Try again.");
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String command = matches.get(0).toLowerCase(Locale.ROOT);
                    tvTranscript.setText(command);

                    // --- Voice Commands ---
                    if (command.contains("memory")) {
                        openMemoryPage();
                    } else if (command.contains("settings")) {
                        openSettingsPage();
                    } else if (command.contains("help")) {
                        openHelpPage();
                    } else if (command.contains("weather")) {
                        openWeatherPage();
                    } else if (command.contains("exit") || command.contains("close") || command.contains("quit")) {
                        exitApp();
                    } else {
                        Toast.makeText(MainActivity.this, "Unknown command: " + command, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        // --- Mic button click ---
        btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        AUDIO_PERMISSION_REQUEST_CODE);
                Toast.makeText(this, "Requesting microphone permission...", Toast.LENGTH_SHORT).show();
                return;
            }
            tvTranscript.setText("Listening...");
            speechRecognizer.startListening(speechRecognizerIntent);
        });

        // --- Buttons click (manual navigation) ---
        btnMemory.setOnClickListener(v -> openMemoryPage());
        btnSettings.setOnClickListener(v -> openSettingsPage());
        btnHelp.setOnClickListener(v -> openHelpPage());
        btnWeather.setOnClickListener(v -> openWeatherPage());

        // --- Check Permissions ---
        checkLocationPermission();
        checkCameraPermission();
    }

    private void openMemoryPage() {
        startActivity(new Intent(MainActivity.this, MemoryActivity.class));
    }

    private void openSettingsPage() {
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    private void openHelpPage() {
        startActivity(new Intent(MainActivity.this, HelpActivity.class));
    }

    private void openWeatherPage() {
        startActivity(new Intent(MainActivity.this, WeatherActivity.class));
    }

    private void exitApp() {
        Toast.makeText(this, "Exiting app...", Toast.LENGTH_SHORT).show();
        finishAffinity();
        System.exit(0);
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission denied! Some features may not work.", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this , "Camera permission denied! Some features may not work.", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted! Press mic again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission denied! Cannot use voice.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
