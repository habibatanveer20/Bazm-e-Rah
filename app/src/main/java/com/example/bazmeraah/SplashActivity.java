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

        // 🔊 Initialize Text-to-Speech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US); // tum Urdu bhi set kar sakti ho: new Locale("ur")
                tts.speak("Welcome to Bzam-e-Raah", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        // ⏳ Delay for 3 seconds then open Registration page
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, RegistrationActivity.class);
            startActivity(intent);
            finish();
        }, 5000);
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
