package com.example.bazmeraah;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
    private ToneGenerator toneGen;

    private String userName, userContact, userMessage;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_support);
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
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
                        speak(isUrdu ? " آپ کسٹمر سپورٹ سے رابطہ کر رہی ہیں۔ براہِ کرم اپنا نام، فون نمبر اور اپنا مسئلہ تفصیل سے بتائیں۔ ہمارا عملہ پیغام موصول ہونے کے بعد آپ سے رابطہ کرے گا۔" :
                                "You are contacting customer support. Please give your name, phone number and describe your issue. Our team will contact you after reviewing the message.", this::startVoiceFlow), 600);
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
            public void onReadyForSpeech(Bundle params) {
                playBeep();
            }
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
            if (lower.matches(".*(yes|yaas| yass| yas|haan|han|ha|ہاں|ہن|جی|جی ہاں).*")) {
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
                    if (lower.matches(".*(yes|yaas|yas|haan|han|ha|ہاں|جی ہاں).*")) {

                        // 🔥 FORMAT NUMBER BEFORE SENDING
                        userContact = normalizePhone(userContact);
                        contactEditText.setText(userContact);

                        sendMessage();
                    }else if (lower.matches(".*(no|nahin|nahi|نہیں|جی نہیں).*")) {
                        speak(isUrdu ? "ٹھیک ہے، پیغام نہیں بھیجا گیا۔" :
                                "Okay, the message was not sent.", null);
                    } else {
                        repeatListening();
                    }
                } else repeatListening();
            }
            @Override
            public void onReadyForSpeech(Bundle params) {
                playBeep();
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
        String formattedPhone = normalizePhone(userContact); // 🔥 ADD THIS
        data.put("contact", formattedPhone);
        data.put("message", userMessage);
        data.put("timestamp", System.currentTimeMillis());
        data.put("status", "open");
        data.put("reply", "");
        data.put("replyBy", "");
        data.put("replyTimestamp", 0);

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
            public void onReadyForSpeech(Bundle params) {
                playBeep();
            }
            @Override
            public void onError(int error) {
                repeatListening();
            }
        });

        speechRecognizer.startListening(intent);
    }

    private boolean isSpeaking = false;

    private void speak(String text, Runnable onDone) {
        if (tts != null && isVoiceActive) {
            // Agar TTS already bol raha hai, thoda wait karke dobara try karo
            if (isSpeaking) {
                mainHandler.postDelayed(() -> speak(text, onDone), 600);
                return;
            }

            String utteranceId = String.valueOf(System.currentTimeMillis());
            isSpeaking = true;

            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    isSpeaking = true;
                }

                @Override
                public void onDone(String utteranceId) {
                    isSpeaking = false;
                    // Thoda pause rakha jaaye taake TTS aur mic overlap na kare
                    if (onDone != null) {
                        mainHandler.postDelayed(onDone, 500);
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    isSpeaking = false;
                    if (onDone != null) {
                        mainHandler.postDelayed(onDone, 500);
                    }
                }
            });

            // TTS start
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else if (onDone != null) {
            onDone.run();
        }
    }
    private void playBeep() {
        try {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (Exception ignored) {}
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
    private String normalizePhone(String phone) {

        if (phone == null) return "";

        phone = phone.replaceAll("\\s+", "").replaceAll("[^0-9\\+]", "");

        if (phone.startsWith("+92")) return phone;

        if (phone.startsWith("0") && phone.length() == 11) {
            return "+92" + phone.substring(1);
        }

        if (phone.startsWith("92")) {
            return "+" + phone;
        }

        if (phone.length() == 10 && phone.startsWith("3")) {
            return "+92" + phone;
        }

        return phone;
    }
}