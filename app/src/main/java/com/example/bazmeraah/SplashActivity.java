package com.example.bazmeraah;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SplashActivity extends BaseActivity {

    private TextToSpeech tts;
    private boolean ttsStarted = false;
    private static final int SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 🔹 Read saved language from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        boolean isUrdu = prefs.getBoolean("language_urdu", false);

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && !ttsStarted) {
                if (isUrdu) {
                    tts.setLanguage(new Locale("ur", "PK"));
                } else {
                    tts.setLanguage(Locale.US);
                }
                ttsStarted = true;

                // 🔹 Speak according to language
                String welcomeMsg = isUrdu
                        ? "بزم راہ میں خوش آمدید"
                        : "Welcome to BazmayRaah";

                tts.speak(welcomeMsg, TextToSpeech.QUEUE_FLUSH, null, "SplashWelcome");

                // Add silent delay
                tts.playSilentUtterance(SPLASH_DELAY_MS, TextToSpeech.QUEUE_ADD, "SplashDelay");

                // Move to next screen after delay
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
