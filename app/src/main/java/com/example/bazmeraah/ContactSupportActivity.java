package com.example.bazmeraah;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ContactSupportActivity extends AppCompatActivity {

    private EditText nameEditText, contactEditText, messageEditText;
    private Button sendButton;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean isUrdu = false;
    private boolean isVoiceActive = true;
    private int step = 0;
    private boolean confirmStep = false;

    private String userName, userContact, userMessage;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_support);

        nameEditText = findViewById(R.id.et_name);
        contactEditText = findViewById(R.id.et_contact);
        messageEditText = findViewById(R.id.et_message);
        sendButton = findViewById(R.id.btn_send_message);

        // Load Language Setting
        isUrdu = getSharedPreferences("AppSettings", MODE_PRIVATE)
                .getBoolean("language_urdu", false);

        setupTTS();
        setupSendButton();
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.US);
                mainHandler.postDelayed(() ->
                        speak(isUrdu ? "آپ کسٹمر سپورٹ سے رابطہ کر رہی ہیں" :
                                "You are contacting support", this::startVoiceFlow), 600);
            }
        });
    }

    private void setupSendButton() {
        sendButton.setOnClickListener(v -> confirmToSend());
    }

    private void startVoiceFlow() {
        step = 0;
        askNextField();
    }

    private void askNextField() {
        String msg;
        switch (step) {
            case 0:
                msg = isUrdu ? "اپنا نام بتائیں" : "Please say your name";
                break;
            case 1:
                msg = isUrdu ? "اپنا فون نمبر بتائیں" : "Please say your phone number";
                break;
            case 2:
                msg = isUrdu ? "اپنا پیغام بتائیں" : "Please say your message";
                break;
            case 3:
                confirmToSend();
                return;
            default:
                return;
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                isUrdu ? new Locale("ur", "PK") : Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty())
                    handleVoiceInput(matches.get(0));
                else repeatListening();
            }

            @Override
            public void onError(int error) {
                repeatListening();
            }
        });

        speechRecognizer.startListening(intent);
    }

    private void handleVoiceInput(String spokenText) {
        if (spokenText == null || spokenText.isEmpty()) {
            repeatListening();
            return;
        }
        spokenText = spokenText.trim();
        String lower = spokenText.toLowerCase();

        // ✅ Handle confirmation responses
        if (confirmStep) {
            if (lower.matches(".*(yes|haan|han|ha|ہاں|ہن|جی|جی ہاں).*")) {
                confirmStep = false;
                step++;
                askNextField();
            } else if (lower.matches(".*(no|nahin|nahi|نہیں|جی نہیں).*")) {
                confirmStep = false;
                repeatListening();
            } else {
                repeatListening();
            }
            return;
        }

        // ✅ Handle normal data input
        switch (step) {
            case 0:
                userName = spokenText;
                nameEditText.setText(userName);
                askConfirmation(isUrdu ?
                        "آپ نے نام " + userName + " بتایا۔ درست ہے؟" :
                        "You said your name is " + userName + ". Correct?");
                break;

            case 1:
                userContact = spokenText.replaceAll("[^0-9]", "");
                contactEditText.setText(userContact);
                askConfirmation(isUrdu ?
                        "آپ نے نمبر " + userContact + " بتایا۔ درست ہے؟" :
                        "You said your phone number is " + userContact + ". Correct?");
                break;

            case 2:
                userMessage = spokenText;
                messageEditText.setText(userMessage);
                askConfirmation(isUrdu ?
                        "آپ نے پیغام بتایا: " + userMessage + "۔ درست ہے؟" :
                        "You said message: " + userMessage + ". Correct?");
                break;
        }
    }

    private void askConfirmation(String msg) {
        confirmStep = true;
        speak(msg, this::startListening);
    }

    private void repeatListening() {
        speak(isUrdu ? "سمجھ نہیں آیا، دوبارہ کہیں" :
                "I didn't catch that, please repeat", this::startListening);
    }

    private void confirmToSend() {
        confirmStep = true;
        speak(isUrdu ? "کیا آپ یہ پیغام بھیجنا چاہتی ہیں؟ ہاں یا نہیں کہیں۔" :
                "Do you want to send this message? Please say yes or no.", this::startListeningForSendConfirmation);
    }

    private void startListeningForSendConfirmation() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                isUrdu ? new Locale("ur", "PK") : Locale.getDefault());

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String lower = matches.get(0).toLowerCase();
                    if (lower.matches(".*(yes|haan|han|ha|ہاں|جی ہاں).*")) {
                        sendMessage();
                    } else if (lower.matches(".*(no|nahin|nahi|نہیں|جی نہیں).*")) {
                        speak(isUrdu ? "ٹھیک ہے، پیغام نہیں بھیجا گیا۔" :
                                "Okay, the message was not sent.", null);
                    } else {
                        repeatListening();
                    }
                } else repeatListening();
            }

            @Override
            public void onError(int error) {
                repeatListening();
            }
        });

        speechRecognizer.startListening(intent);
    }

    private void sendMessage() {
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://bazm-e-rah-default-rtdb.firebaseio.com/");
        DatabaseReference ref = database.getReference("SupportMessages");

        HashMap<String, Object> data = new HashMap<>();
        data.put("name", userName);
        data.put("contact", userContact);
        data.put("message", userMessage);
        data.put("timestamp", System.currentTimeMillis());

        ref.push().setValue(data)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, isUrdu ?
                            "پیغام کامیابی سے بھیج دیا گیا" :
                            "Message sent successfully", Toast.LENGTH_LONG).show();

                    speak(isUrdu ?
                                    "پیغام بھیج دیا گیا۔ کیا آپ مین پیج پر جانا چاہتی ہیں یا ایپ بند کرنا چاہتی ہیں؟" :
                                    "Message sent successfully. Do you want to go to the main page or exit?",
                            this::startListeningForNextAction);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, isUrdu ?
                            "پیغام بھیجنے میں مسئلہ ہوا، دوبارہ کوشش کریں" :
                            "Failed to send message, please try again", Toast.LENGTH_LONG).show();
                });
    }

    private void startListeningForNextAction() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                isUrdu ? new Locale("ur", "PK") : Locale.getDefault());

        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String lower = matches.get(0).toLowerCase();

                    if (lower.matches(".*(main|home|مین|مین پیج|واپس|exit|ایگزٹ|بند|ختم).*")) {
                        Intent intent = new Intent(ContactSupportActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        speak(isUrdu ? "سمجھ نہیں آیا، دوبارہ کہیں" :
                                "Didn't catch that, please repeat", ContactSupportActivity.this::startListeningForNextAction);
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

    private void speak(String text, Runnable onDone) {
        if (tts != null && isVoiceActive) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID");
            if (onDone != null)
                mainHandler.postDelayed(onDone, text.length() * 80L); // Adjust timing by text length
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
