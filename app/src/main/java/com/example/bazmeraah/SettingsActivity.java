package com.example.bazmeraah;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.ArrayList;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private Switch switchLanguage, switchVoice, switchTheme;
    private Button btnEditProfile;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;

    private boolean isVoiceEnabled;
    private boolean isEnglish = true;
    private boolean waitingForEditConfirmation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        switchLanguage = findViewById(R.id.switchlanguage);
        switchVoice = findViewById(R.id.switchvoice);
        switchTheme = findViewById(R.id.switchtheme);
        btnEditProfile = findViewById(R.id.btnedit_profile);

        loadSettings();
        initializeTTS();
        initializeSpeechRecognizer();

        // ---- Switch Listeners ----
        switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("language_urdu", isChecked).apply();
            isEnglish = !isChecked;
            speak(isChecked ? "زبان اردو میں تبدیل ہو گئی۔ اب میں اردو میں بات کروں گی۔"
                    : "Language switched to English. From now on, I will speak in English.", this::startListening);
        });

        switchVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("voice_enabled", isChecked).apply();
            isVoiceEnabled = isChecked;
            speak(isChecked ? "Voice assistant turned on." : "Voice assistant turned off.", this::startListening);
        });

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isDarkMode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
            recreate();
        });

        btnEditProfile.setOnClickListener(v -> {
            speak(getTextMsg("confirm_edit"), () -> {
                waitingForEditConfirmation = true;
                if (!isFinishing()) startListening();
            });
        });
    }

    private void loadSettings() {
        boolean isUrdu = prefs.getBoolean("language_urdu", false);
        isEnglish = !isUrdu;
        isVoiceEnabled = prefs.getBoolean("voice_enabled", true);
        boolean darkMode = prefs.getBoolean("isDarkMode", false);

        switchLanguage.setChecked(isUrdu);
        switchVoice.setChecked(isVoiceEnabled);
        switchTheme.setChecked(darkMode);
    }

    private void initializeTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isEnglish ? Locale.ENGLISH : new Locale("ur", "PK"));
                speak(getCurrentStatusSummary(), this::startListening);
            }
        });
    }

    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                speak(getTextMsg("not_understood"), SettingsActivity.this::startListening);
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleCommand(matches.get(0).toLowerCase());
                } else {
                    speak(getTextMsg("not_understood"), SettingsActivity.this::startListening);
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private String getCurrentStatusSummary() {
        boolean isUrdu = prefs.getBoolean("language_urdu", false);
        boolean darkMode = prefs.getBoolean("isDarkMode", false);
        boolean voiceOn = prefs.getBoolean("voice_enabled", true);

        String lang = isUrdu ? (isEnglish ? "Urdu" : "اردو") : (isEnglish ? "English" : "انگلش");
        String theme = darkMode ? (isEnglish ? "Dark" : "ڈارک") : (isEnglish ? "Light" : "لائٹ");
        String voice = voiceOn ? (isEnglish ? "enabled" : "آن ہے") : (isEnglish ? "disabled" : "آف ہے");

        if (isEnglish) {
            return "You are on the settings page. "
                    + "Current language is " + lang + ", theme is " + theme + ", and voice is " + voice + ". "
                    + "You can say: change language, change theme, change voice, or edit profile. What would you like to do?";
        } else {
            return "آپ سیٹنگز پیج پر ہیں۔ "
                    + "موجودہ زبان " + lang + " ہے، تھیم " + theme + " ہے، اور وائس " + voice + " ہے۔ "
                    + "آپ کہہ سکتی ہیں: زبان تبدیل کرو، تھیم تبدیل کرو، وائس بدل دو، یا پروفائل ایڈٹ کرو۔ آپ کیا کرنا چاہیں گی؟";
        }
    }

    private void handleCommand(String command) {
        command = command.toLowerCase();

        // --- Confirmation for button click ---
        if (waitingForEditConfirmation) {
            waitingForEditConfirmation = false;
            if (command.contains("yes") || command.contains("haan")) {
                openEditProfile();
            } else {
                speak(getTextMsg("ask_more"), this::startListening);
            }
            return;
        }

        // --- Direct voice commands ---
        if (command.contains("dark")) {
            switchTheme.setChecked(true);
            speak(getTextMsg("dark_on"), this::askMore);
        } else if (command.contains("light")) {
            switchTheme.setChecked(false);
            speak(getTextMsg("light_on"), this::askMore);
        } else if (command.contains("urdu")) {
            switchLanguage.setChecked(true);
            speak(getTextMsg("lang_urdu"), this::askMore);
        } else if (command.contains("english")) {
            switchLanguage.setChecked(false);
            speak(getTextMsg("lang_english"), this::askMore);
        } else if (command.contains("voice on") || command.contains("enable voice")) {
            switchVoice.setChecked(true);
            speak(getTextMsg("voice_on"), this::askMore);
        } else if (command.contains("voice off") || command.contains("disable voice")) {
            switchVoice.setChecked(false);
            speak(getTextMsg("voice_off"), this::askMore);
        } else if (command.contains("edit") || command.contains("profile")) {
            openEditProfile();
        } else if (command.contains("exit") || command.contains("back") || command.contains("main") || command.contains("no")) {
            speak(getTextMsg("exiting"), () -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        } else {
            speak(getTextMsg("not_understood"), this::startListening);
        }
    }

    private void openEditProfile() {
        speak(getTextMsg("opening_edit"), () -> {
            if (!isFinishing()) {
                new Handler().postDelayed(() -> {
                    startActivity(new Intent(SettingsActivity.this, EditProfileActivity.class));
                    if (!isFinishing()) finish();
                }, 300);
            }
        });
    }

    private void askMore() {
        speak(getTextMsg("ask_more"), this::startListening);
    }

    private void startListening() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isEnglish ? Locale.ENGLISH : new Locale("ur", "PK"));
            speechRecognizer.startListening(intent);
        }
    }

    private void speak(String text, Runnable onDone) {
        if (tts != null && isVoiceEnabled) {
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) {
                    if (onDone != null)
                        runOnUiThread(() -> new Handler().postDelayed(onDone, 500));
                }
                @Override public void onError(String utteranceId) {}
            });
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID");
        } else if (onDone != null) onDone.run();
    }

    private void speak(String text) { speak(text, null); }

    private String getTextMsg(String key) {
        switch (key) {
            case "language_switched": return isEnglish ? "Language switched." : "زبان تبدیل ہو گئی۔";
            case "voice_enabled": return isEnglish ? "Voice setting changed." : "وائس سیٹنگ تبدیل ہو گئی۔";
            case "theme_changed": return isEnglish ? "Theme changed successfully." : "تھیم کامیابی سے تبدیل ہو گیا۔";
            case "dark_on": return isEnglish ? "Dark theme enabled." : "ڈارک تھیم فعال ہو گیا۔";
            case "light_on": return isEnglish ? "Light theme enabled." : "لائٹ تھیم فعال ہو گیا۔";
            case "lang_urdu": return "زبان اردو میں تبدیل کر دی گئی۔ اب میں اردو میں بات کروں گی۔";
            case "lang_english": return "Language switched to English. From now on, I will speak in English.";
            case "voice_on": return isEnglish ? "Voice assistant turned on." : "وائس اسسٹنٹ آن کر دیا گیا۔";
            case "voice_off": return isEnglish ? "Voice assistant turned off." : "وائس اسسٹنٹ بند کر دیا گیا۔";
            case "opening_edit": return isEnglish ? "Opening edit profile." : "پروفائل ایڈٹ کھولی جا رہی ہے۔";
            case "confirm_edit": return isEnglish ? "Do you want to edit your profile?" : "کیا آپ پروفائل ایڈٹ کرنا چاہیں گی؟";
            case "ask_more": return isEnglish ? "Do you want to do anything else on this page?" : "کیا آپ اس پیج پر کچھ اور کرنا چاہیں گی؟";
            case "ask_next_action": return isEnglish ? "Do you want to do anything else here or go back to the main page?"
                    : "کیا آپ یہاں کوئی اور کام کرنا چاہیں گی یا مین پیج پر جانا چاہیں گی؟";
            case "exiting": return isEnglish ? "Returning to main page." : "مین پیج پر واپس جا رہی ہوں۔";
            case "not_understood": return isEnglish ? "Sorry, I didn't understand. Please repeat." : "معذرت، سمجھ نہیں آیا۔ دوبارہ کہیں۔";
            default: return "";
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}
