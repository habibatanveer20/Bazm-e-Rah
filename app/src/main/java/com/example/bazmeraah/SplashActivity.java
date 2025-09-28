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
        // 🚀 Set content view immediately to show splash background fast
        setContentView(R.layout.activity_splash);
        // ⏳ Small delay to let layout render before initializing TTS
        new Handler().postDelayed(() -> {
            // 🔊 Initialize Text-to-Speech after layout is rendered
            tts = new TextToSpeech(SplashActivity.this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US); // ya new Locale("ur") Urdu
                    tts.speak("Welcome to Bazm-e-Raah", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
            // ⏳ Delay for 5 seconds then open Registration page
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(SplashActivity.this, RegistrationActivity.class);
                // Clear back stack so back button doesn't return to splash
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }, 5000);
        }, 50); // 50ms delay to ensure layout renders before TTS
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