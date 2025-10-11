package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
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

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RegistrationActivity extends AppCompatActivity {

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private TextToSpeech tts;

    private String userName = "";
    private String userPhone = "";
    private String userEmergency = "";
    private int step = 0;
    private boolean confirmStep = false;
    private boolean permissionGranted = false;

    private EditText nameEditText, phoneEditText, emergencyEditText;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private boolean isUrdu = false;
    private boolean isTtsActive = false;

    // ✅ Added: Beep tone generator
    private ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        nameEditText = findViewById(R.id.nameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        emergencyEditText = findViewById(R.id.emergencyEditText);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        if (getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("isRegistered", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        isUrdu = prefs.getBoolean("language_urdu", false);

        setupTTS();
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.US);

                step = -1;
                mainHandler.postDelayed(() -> {
                    speakWithGuaranteedDelay(isUrdu
                            ? "آواز کے ذریعے رجسٹریشن میں خوش آمدید"
                            : "Welcome to voice registration", 3500);
                }, 1000);
            }
        });
    }

    private void speakWithGuaranteedDelay(String text, int delayMillis) {
        if (tts != null) {
            tts.stop();
        }

        mainHandler.removeCallbacksAndMessages(null);
        isTtsActive = true;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);

        executor.schedule(() -> {
            isTtsActive = false;
            mainHandler.post(this::onTtsComplete);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void onTtsComplete() {
        switch (step) {
            case -1:
                checkMicPermission();
                break;
            case 0:
            case 1:
            case 2:
            case 3:
                startListeningWithSafety();
                break;
        }
    }

    private void checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            permissionGranted = true;
            step = 0;
            askNextQuestion();
        }
    }

    private void askNextQuestion() {
        String msg;
        int delayTime = 4000;

        switch (step) {
            case 0:
                msg = isUrdu ? "براہ کرم اپنا نام بتائیں" : "Please tell your name";
                break;
            case 1:
                msg = isUrdu ? "براہ کرم اپنا فون نمبر بتائیں" : "Please tell your phone number";
                break;
            case 2:
                msg = isUrdu ? "براہ کرم ایمرجنسی نمبر بتائیں" : "Please tell your emergency contact";
                break;
            case 3:
                msg = isUrdu
                        ? "آپ کا نام " + userName + " ہے، فون " + userPhone + " ہے، ایمرجنسی " + userEmergency + " ہے۔ کیا رجسٹر کروں؟"
                        : "Name: " + userName + ", Phone: " + userPhone + ", Emergency: " + userEmergency + ". Should I register?";
                confirmStep = true;
                delayTime = 6000;
                break;
            default:
                msg = "";
        }

        speakWithGuaranteedDelay(msg, delayTime);
    }

    private void startListeningWithSafety() {
        if (isTtsActive) {
            mainHandler.postDelayed(this::startListeningWithSafety, 200);
            return;
        }

        if (!permissionGranted) return;
        mainHandler.postDelayed(this::actuallyStartListening, 500);
    }

    private void actuallyStartListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech Recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleVoiceInput(matches.get(0));
                } else {
                    retryListening();
                }
            }

            @Override
            public void onError(int error) {
                retryListening();
            }
        });

        if (speechIntent == null) {
            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isUrdu ? "ur-PK" : Locale.getDefault());
            speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        }

        try {
            // ✅ Beep when mic starts listening
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
            speechRecognizer.startListening(speechIntent);
        } catch (Exception e) {
            retryListening();
        }
    }

    private void handleVoiceInput(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) {
            retryListening();
            return;
        }

        spokenText = spokenText.toLowerCase().trim();

        if (confirmStep) {
            processConfirmation(spokenText);
        } else {
            processUserInput(spokenText);
        }
    }

    private void processConfirmation(String spokenText) {
        if (spokenText.contains("yes") || spokenText.contains("haan") || spokenText.contains("ہاں")) {
            if (step == 3) {
                registerUser();
            } else {
                confirmStep = false;
                step++;
                askNextQuestion();
            }
        } else if (spokenText.contains("no") || spokenText.contains("nahin") || spokenText.contains("نہیں")) {
            resetCurrentStep();
        } else {
            retryListening();
        }
    }

    private void processUserInput(String spokenText) {
        switch (step) {
            case 0:
                userName = spokenText;
                nameEditText.setText(userName);
                askConfirmation(isUrdu ? "آپ نے نام " + userName + " بتایا۔ درست ہے؟" : "You said name " + userName + ". Correct?");
                break;
            case 1:
                userPhone = convertToDigits(spokenText);
                if (userPhone.length() < 11) {
                    speakWithGuaranteedDelay(isUrdu ? "فون نمبر درست نہیں۔ دوبارہ کہیں" : "Invalid phone. Try again", 4000);
                    return;
                }
                phoneEditText.setText(userPhone);
                askConfirmation(isUrdu ? "آپ نے فون " + userPhone + " بتایا۔ درست ہے؟" : "You said phone " + userPhone + ". Correct?");
                break;
            case 2:
                userEmergency = convertToDigits(spokenText);
                if (userEmergency.length() < 11) {
                    speakWithGuaranteedDelay(isUrdu ? "ایمرجنسی نمبر درست نہیں۔ دوبارہ کہیں" : "Invalid emergency number. Try again", 4000);
                    return;
                }
                emergencyEditText.setText(userEmergency);
                askConfirmation(isUrdu ? "آپ نے ایمرجنسی نمبر " + userEmergency + " بتایا۔ درست ہے؟" : "You said emergency " + userEmergency + ". Correct?");
                break;
        }
    }

    private void askConfirmation(String message) {
        confirmStep = true;
        speakWithGuaranteedDelay(message, 5000);
    }

    private void retryListening() {
        speakWithGuaranteedDelay(isUrdu ? "سنی نہیں۔ دوبارہ کوشش کریں" : "Didn't hear. Please try again", 3000);
    }

    private void resetCurrentStep() {
        confirmStep = false;
        switch (step) {
            case 0: userName = ""; nameEditText.setText(""); break;
            case 1: userPhone = ""; phoneEditText.setText(""); break;
            case 2: userEmergency = ""; emergencyEditText.setText(""); break;
            case 3:
                step = 0;
                userName = userPhone = userEmergency = "";
                nameEditText.setText("");
                phoneEditText.setText("");
                emergencyEditText.setText("");
                break;
        }
        askNextQuestion();
    }

    private String convertToDigits(String spokenText) {
        return spokenText.toLowerCase()
                .replaceAll("zero", "0").replaceAll("صفر", "0")
                .replaceAll("one", "1").replaceAll("ایک", "1")
                .replaceAll("two", "2").replaceAll("دو", "2")
                .replaceAll("three", "3").replaceAll("تین", "3")
                .replaceAll("four", "4").replaceAll("چار", "4")
                .replaceAll("five", "5").replaceAll("پانچ", "5")
                .replaceAll("six", "6").replaceAll("چھ", "6")
                .replaceAll("seven", "7").replaceAll("سات", "7")
                .replaceAll("eight", "8").replaceAll("آٹھ", "8")
                .replaceAll("nine", "9").replaceAll("نو", "9")
                .replaceAll("[^0-9]", "");
    }

    private void registerUser() {
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                .putBoolean("isRegistered", true)
                .putString("name", userName)
                .putString("phone", userPhone)
                .putString("emergency", userEmergency)
                .apply();

        speakWithGuaranteedDelay(isUrdu ? "رجسٹریشن کامیاب۔ خوش آمدید " + userName : "Registration successful. Welcome " + userName, 4000);

        mainHandler.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 5000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (toneGenerator != null) toneGenerator.release();
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true;
            step = 0;
            askNextQuestion();
        } else {
            speakWithGuaranteedDelay(isUrdu ? "مائیک کی اجازت ضروری ہے" : "Mic permission required", 3000);
        }
    }
}
