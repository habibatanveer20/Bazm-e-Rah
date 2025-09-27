package com.example.bazmeraah;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class HelpActivity extends AppCompatActivity {
    private TextToSpeech tts;
    private Button btnReadAloud, btnHelpBack;
    private final String HELP_TEXT =
            "Available voice commands: Memory to open memory. Settings to open settings. Help to open this page. " +
                    "Say Exit or Close to close the app.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        btnReadAloud = findViewById(R.id.btnReadAloud);
        btnHelpBack = findViewById(R.id.btnHelpBack);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
            }
        });

        btnReadAloud.setOnClickListener(v -> tts.speak(HELP_TEXT, TextToSpeech.QUEUE_FLUSH, null, "HELP_READ"));
        btnHelpBack.setOnClickListener(v -> finish());
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
