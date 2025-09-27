package com.example.bazmeraah;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SettingsActivity extends AppCompatActivity {

    Switch switchDarkMode, switchTTS, switchVoiceCmd;
    Button btnEmergency;

    private static final String PREFS = "BazmERaahPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchDarkMode = findViewById(R.id.switch_darkmode);
        switchTTS = findViewById(R.id.switch_tts);
        switchVoiceCmd = findViewById(R.id.switch_voicecmd);
        btnEmergency = findViewById(R.id.btn_emergency);

        // Load saved prefs
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        boolean isDarkMode = prefs.getBoolean("dark_mode", true);
        boolean isTTS = prefs.getBoolean("tts", false);
        boolean isVoiceCmd = prefs.getBoolean("voice_cmd", false);

        switchDarkMode.setChecked(isDarkMode);
        switchTTS.setChecked(isTTS);
        switchVoiceCmd.setChecked(isVoiceCmd);

        // Apply theme immediately
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // Dark mode toggle
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            Toast.makeText(this, "Dark Mode: " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            AppCompatDelegate.setDefaultNightMode(isChecked ?
                    AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        // TTS toggle
        switchTTS.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("tts", isChecked).apply();
            Toast.makeText(this, "Voice Feedback: " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        // Voice Command toggle
        switchVoiceCmd.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("voice_cmd", isChecked).apply();
            Toast.makeText(this, "Voice Commands: " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        // Emergency contact button (fetch from Firebase and call)
        btnEmergency.setOnClickListener(v -> {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid);

            ref.get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String emergencyNumber = snapshot.child("emergency").getValue(String.class);

                    if (emergencyNumber != null && !emergencyNumber.isEmpty()) {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Emergency Contact")
                                .setMessage("Do you want to Call or Send SMS?")
                                .setPositiveButton("Call", (dialog, which) -> {
                                    Intent intent = new Intent(Intent.ACTION_CALL);
                                    intent.setData(Uri.parse("tel:" + emergencyNumber));
                                    startActivity(intent);
                                })
                                .setNegativeButton("SMS", (dialog, which) -> {
                                    Intent smsIntent = new Intent(Intent.ACTION_VIEW,
                                            Uri.fromParts("sms", emergencyNumber, null));
                                    smsIntent.putExtra("sms_body",
                                            "This is an emergency alert from Bazm-e-Raah!");
                                    startActivity(smsIntent);
                                })
                                .show();
                    } else {
                        Toast.makeText(this, "No Emergency Number Found", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

    }
}
