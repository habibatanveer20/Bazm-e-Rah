package com.example.bazmeraah;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
    private boolean isSingleFieldEdit = false;

    private String userName, userPhone, userEmergency;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGen;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
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
                mainHandler.postDelayed(this::announceCurrentData, 500);
            }
        });
    }

    private void setupSaveButton() {
        saveButton.setOnClickListener(v -> askSaveConfirmation());
    }

    private void announceCurrentData() {
        String msg;
        if (isUrdu) {
            msg = "Aapka naam: " + userName +
                    ". Aapka phone: " + userPhone +
                    ". Emergency number: " + userEmergency +
                    ". Ab bataiye kya edit karna hai?";
        } else {
            msg = "Your name: " + userName +
                    ". Your phone: " + userPhone +
                    ". Emergency number: " + userEmergency +
                    ". What do you want to edit?";
        }

        speak(msg, this::startListeningForFieldChoice);
    }

    private void startListeningForFieldChoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                playBeep();
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleFieldChoice(matches.get(0));
                } else repeatListening();
            }

            @Override
            public void onError(int error) {
                repeatListening();
            }
        });

        speechRecognizer.startListening(intent);
    }

    private void handleFieldChoice(String spokenText) {
        String lower = spokenText.toLowerCase();

        if (lower.contains("name") || lower.contains("naam")) {
            step = 0;
            isSingleFieldEdit = true;
            askNextField();
        } else if (lower.contains("phone")) {
            step = 1;
            isSingleFieldEdit = true;
            askNextField();
        } else if (lower.contains("emergency")) {
            step = 2;
            isSingleFieldEdit = true;
            askNextField();
        } else if (lower.contains("all") || lower.contains("sab")) {
            step = 0;
            isSingleFieldEdit = false;
            askNextField();
        } else {
            repeatListening();
        }
    }

    private void askNextField() {
        String msg = "";

        switch (step) {
            case 0: msg = "Please say your name"; break;
            case 1: msg = "Please say your phone number"; break;
            case 2: msg = "Please say your emergency number"; break;
            case 3: askSaveConfirmation(); return;
        }

        speak(msg, this::startListening);
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            public void onReadyForSpeech(Bundle params) {
                playBeep();
            }
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
        if (confirmStep) {
            String lower = spokenText.toLowerCase();

            if (lower.contains("yes") || lower.contains("haan")) {

                confirmStep = false;

                if (isSingleFieldEdit) {
                    askEditMore();
                } else {
                    step++;
                    askNextField();
                }

            } else if (lower.contains("no")) {
                confirmStep = false;
                startListening();
            } else repeatListening();

            return;
        }

        switch (step) {
            case 0:
                userName = spokenText;
                nameEditText.setText(userName);
                askConfirmation("You said name " + userName + ". Correct?");
                break;

            case 1:
                userPhone = spokenText.replaceAll("[^0-9]", "");
                phoneEditText.setText(userPhone);
                askConfirmation("You said phone " + userPhone + ". Correct?");
                break;

            case 2:
                userEmergency = spokenText.replaceAll("[^0-9]", "");
                emergencyEditText.setText(userEmergency);
                askConfirmation("You said emergency " + userEmergency + ". Correct?");
                break;
        }
    }

    private void askConfirmation(String msg) {
        confirmStep = true;
        speak(msg, this::startListening);
    }

    private void askEditMore() {
        speak("Do you want to edit anything else?", this::listenEditMoreDecision);
    }

    private void listenEditMoreDecision() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                playBeep();
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0).toLowerCase();

                    if (spoken.contains("yes") || spoken.contains("haan")) {
                        resetEditingState();
                        speak("What do you want to edit?", EditProfileActivity.this::startListeningForFieldChoice);
                    } else if (spoken.contains("no")) {
                        announceFinalData();
                    } else repeatListening();
                }
            }

            @Override
            public void onError(int error) {
                repeatListening();
            }
        });

        speechRecognizer.startListening(intent);
    }

    private void announceFinalData() {
        String msg = "Your name: " + userName +
                ", phone: " + userPhone +
                ", emergency: " + userEmergency +
                ". Do you want to save this profile?";

        speak(msg, this::saveProfileDecision);
    }

    private void askSaveConfirmation() {
        speak("Do you want to save your profile?", this::saveProfileDecision);
    }

    private void saveProfileDecision() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                playBeep();
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0).toLowerCase();

                    if (spokenText.contains("yes") || spokenText.contains("haan")) {
                        saveProfile();
                    }
                    else if (spokenText.contains("no") || spokenText.contains("nahi")) {

                        speak("Okay, do you want to edit profile again or go back to main page?",
                                EditProfileActivity.this::listenAfterSaveAction);

                    }
                    else {
                        repeatListeningAfterSave();
                    }
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
        SharedPreferences.Editor editor = getSharedPreferences("UserPrefs", MODE_PRIVATE).edit();
        editor.putString("name", userName);
        editor.putString("phone", userPhone);
        editor.putString("emergency", userEmergency);
        editor.apply();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userId);

        ref.child("name").setValue(userName);
        ref.child("phone").setValue(userPhone);
        ref.child("emergency").setValue(userEmergency);

        speak("Profile saved successfully.Do you want to edit profile again or go back to main page?",  this::listenAfterSaveAction);
    }

    private void repeatListening() {
        speak("Please repeat", this::startListening);
    }

    private void speak(String text, Runnable onDone) {
        if (tts != null) {
            String id = String.valueOf(System.currentTimeMillis());

            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String s) {}
                @Override public void onDone(String s) {
                    if (onDone != null) mainHandler.post(onDone);
                }
                @Override public void onError(String s) {}
            });

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        }
    }
    private void resetEditingState() {
        confirmStep = false;
        isSingleFieldEdit = false;
        step = 0;
    }
    private void listenAfterSaveAction() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                playBeep();
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0).toLowerCase();

                    // 🔁 دوبارہ edit
                    if (spoken.contains("edit") || spoken.contains("profile")) {

                        resetEditingState(); // 🔥 important
                        speak("What do you want to edit?",
                                EditProfileActivity.this::startListeningForFieldChoice);

                    }
                    // 🏠 back to main
                    else if (spoken.contains("back") || spoken.contains("main") || spoken.contains("home")) {

                        Intent intent = new Intent(EditProfileActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();

                    } else {
                        repeatListeningAfterSave();
                    }
                }
            }

            @Override
            public void onError(int error) {
                repeatListeningAfterSave();
            }
        });

        speechRecognizer.startListening(intent);
    }
    private void repeatListeningAfterSave() {
        speak("Please say edit profile or go back to main page", this::listenAfterSaveAction);
    }
    private void playBeep() {
        try {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        } catch (Exception ignored) {}
    }

}