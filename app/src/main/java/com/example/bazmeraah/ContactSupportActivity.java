package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ContactSupportActivity extends AppCompatActivity {

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private TextToSpeech tts;

    private String userName = "";
    private String userPhone = "";
    private String userMessage = "";
    private int step = 0;
    private boolean confirmStep = false;
    private boolean permissionGranted = false;

    private EditText nameEditText, phoneEditText, messageEditText;
    private Handler handler = new Handler();

    private DatabaseReference supportRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_support);

        nameEditText = findViewById(R.id.et_name);
        phoneEditText = findViewById(R.id.et_contact);
        messageEditText = findViewById(R.id.et_message);

        supportRef = FirebaseDatabase.getInstance().getReference("SupportRequests");

        initTTS();
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        if (speechRecognizer != null) {
                            try { speechRecognizer.cancel(); } catch(Exception ignored) {}
                        }
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> {
                            if ("welcome".equals(utteranceId)) {
                                checkMicPermission();
                            } else if ("prompt".equals(utteranceId)) {
                                startListeningWithDelay(500);
                            }
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {}
                });

                speak("Welcome to support page. I will ask your name, phone number, and message. After confirmation, I will send it to support.", "welcome");
            }
        });
    }

    private void checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            permissionGranted = true;
            step = 0;
            confirmStep = false;
            askNext();
        }
    }

    private void askNext() {
        switch (step) {
            case 0: speak("Please tell your name.", "prompt"); break;
            case 1: speak("Please tell your phone number.", "prompt"); break;
            case 2: speak("Please tell your support message.", "prompt"); break;
            case 3:
                speak("You said your name is " + userName +
                        ", phone number is " + userPhone +
                        ", and message is " + userMessage +
                        ". Should I send this to support?", "prompt");
                confirmStep = true;
                return;
        }
        confirmStep = false;
    }

    private void startListeningWithDelay(int delayMs) {
        handler.postDelayed(this::startListening, delayMs);
    }

    private void startListening() {
        if (!permissionGranted) return;
        if (tts.isSpeaking()) { handler.postDelayed(this::startListening, 500); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech Recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); speechRecognizer.destroy(); } catch(Exception ignored) {}
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) { speak("I did not hear anything. Please try again.", "prompt"); }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) handleVoice(matches.get(0));
                else speak("I did not hear anything. Please try again.", "prompt");
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        if (speechIntent == null) {
            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        }

        speechRecognizer.startListening(speechIntent);
    }

    private void handleVoice(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) {
            speak("I did not catch that. Please try again.", "prompt");
            return;
        }

        spokenText = spokenText.toLowerCase().trim();

        if (spokenText.contains("exit") || spokenText.contains("main page")) {
            speak("Going back to main page.", "prompt");
            handler.postDelayed(() -> { startActivity(new Intent(this, MainActivity.class)); finish(); }, 1500);
            return;
        }

        if (confirmStep) {
            if (spokenText.contains("yes") || spokenText.contains("confirm")) {
                if (step == 3) sendSupportMessage(); else { step++; askNext(); }
            } else if (spokenText.contains("no")) repeatCurrentStep();
            else speak("Please say yes or no.", "prompt");
            return;
        }

        switch (step) {
            case 0: userName = spokenText; nameEditText.setText(userName); confirmStep = true;
                speak("You said your name is " + userName + ". Should I confirm?", "prompt"); break;
            case 1: userPhone = convertToDigits(spokenText);
                if(userPhone.length()<11){ speak("Phone number invalid. Try again.", "prompt"); return;}
                phoneEditText.setText(userPhone); confirmStep=true; speak("You said your phone number is "+userPhone+". Should I confirm?","prompt"); break;
            case 2: userMessage = spokenText; messageEditText.setText(userMessage); confirmStep=true;
                speak("You said your message is " + userMessage + ". Should I confirm?", "prompt"); break;
        }
    }

    private void repeatCurrentStep() {
        switch (step){
            case 0: userName=""; nameEditText.setText(""); speak("Please tell your name again.","prompt"); break;
            case 1: userPhone=""; phoneEditText.setText(""); speak("Please tell your phone number again.","prompt"); break;
            case 2: userMessage=""; messageEditText.setText(""); speak("Please tell your message again.","prompt"); break;
            case 3: step=0; userName=userPhone=userMessage=""; nameEditText.setText(""); phoneEditText.setText(""); messageEditText.setText("");
                speak("Let's start over. Please tell your name.","prompt"); break;
        }
        confirmStep=false;
    }

    private String convertToDigits(String spokenText){
        return spokenText.toLowerCase()
                .replaceAll("zero","0").replaceAll("one","1")
                .replaceAll("two","2").replaceAll("three","3")
                .replaceAll("four","4").replaceAll("five","5")
                .replaceAll("six","6").replaceAll("seven","7")
                .replaceAll("eight","8").replaceAll("nine","9")
                .replaceAll("[^0-9]","");
    }

    private void sendSupportMessage(){
        String requestId = supportRef.push().getKey();
        Map<String,String> data = new HashMap<>();
        data.put("name",userName); data.put("phone",userPhone); data.put("message",userMessage);
        supportRef.child(requestId).setValue(data);
        speak("Your support request has been sent. Thank you "+userName,"prompt");
        handler.postDelayed(() -> { startActivity(new Intent(this, MainActivity.class)); finish(); },1500);
    }

    private void speak(String text,String utteranceId){
        if(tts!=null) tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,utteranceId);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(speechRecognizer!=null) try{speechRecognizer.cancel(); speechRecognizer.destroy();} catch(Exception ignored){}
        if(tts!=null) tts.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==1 && grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            permissionGranted=true; step=0; confirmStep=false; askNext();
        } else speak("Microphone permission is required for support.","prompt");
    }
}
