package com.example.bazmeraah;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private boolean ttsStarted = false; // Ensure TTS runs only once
    private static final int SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && !ttsStarted) {
                tts.setLanguage(Locale.US); // or new Locale("ur") for Urdu
                ttsStarted = true;

                // Speak welcome message
                tts.speak("Welcome to BazmayRaah", TextToSpeech.QUEUE_FLUSH, null, "SplashWelcome");

                // Add silent utterance to wait for splash duration
                tts.playSilentUtterance(SPLASH_DELAY_MS, TextToSpeech.QUEUE_ADD, "SplashDelay");

                // Listener to move to next activity after delay
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) { }

                    @Override
                    public void onDone(String utteranceId) {
                        if ("SplashDelay".equals(utteranceId)) {
                            runOnUiThread(() -> {
                                Intent intent = new Intent(SplashActivity.this, RegistrationActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                        }
                    }

                    @Override
                    public void onError(String utteranceId) { }
                });
            }
        });
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
