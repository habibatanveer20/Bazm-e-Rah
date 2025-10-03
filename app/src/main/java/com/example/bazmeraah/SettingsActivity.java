package com.example.bazmeraah;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchLanguage, switchVoice, switchTheme;
    private Button btnEditProfile;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings); // XML file

        // Initialize views
        switchLanguage = findViewById(R.id.switchlanguage);
        switchVoice = findViewById(R.id.switchvoice);
        switchTheme = findViewById(R.id.switchtheme);
        btnEditProfile = findViewById(R.id.btn_editprofile);

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
                if (switchVoice.isChecked()) {
                    speak("Settings page opened. You can change language, voice assistant, or theme.");
                }
            }
        });

        // Load saved preferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        switchLanguage.setChecked(prefs.getBoolean("language", false));
        switchVoice.setChecked(prefs.getBoolean("voice", true));
        switchTheme.setChecked(prefs.getBoolean("theme", false));

        // Apply theme immediately
        applyTheme(switchTheme.isChecked());

        // Switch listeners
        switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreference("language", isChecked);
            if (switchVoice.isChecked()) {
                speak("Language switched to " + (isChecked ? "English" : "Urdu"));
            }
        });

        switchVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreference("voice", isChecked);
            if (isChecked) {
                speak("Voice assistant enabled");
            }
        });

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreference("theme", isChecked);
            applyTheme(isChecked);
            if (switchVoice.isChecked()) {
                speak("Theme " + (isChecked ? "dark mode" : "light mode"));
            }
        });

        btnEditProfile.setOnClickListener(v -> {
            if (switchVoice.isChecked()) {
                speak("Edit profile feature clicked");
            }
        });
    }

    private void savePreference(String key, boolean value) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SETTINGS_TTS");
        }
    }

    private void applyTheme(boolean darkMode) {
        if (darkMode) {
            getWindow().getDecorView().setBackgroundColor(Color.parseColor("#2A2727"));
        } else {
            getWindow().getDecorView().setBackgroundColor(Color.parseColor("#FFFFFF"));
        }
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
