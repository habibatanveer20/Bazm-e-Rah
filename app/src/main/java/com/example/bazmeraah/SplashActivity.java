package com.example.bazmeraah;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize TTS after layout renders
        new Handler().postDelayed(() -> {
            tts = new TextToSpeech(SplashActivity.this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US); // or new Locale("ur") for Urdu
                    tts.speak("Welcome to Bazm-e-Raah", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });

            // 5-second delay then open RegistrationActivity
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(SplashActivity.this, RegistrationActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }, 5000);

        }, 50); // 50ms delay
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