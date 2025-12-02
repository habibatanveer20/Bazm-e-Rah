package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class RegistrationActivity extends AppCompatActivity {

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private TextToSpeech tts;

    private String userName = "";
    private String userPhone = "";
    private String userEmergency = "";
    private int step = 0;
    private boolean confirmStep = false;
    private boolean permissionGranted = false;

    private EditText nameEditText, phoneEditText, emergencyEditText;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private boolean isUrdu = false;
    private boolean isTtsActive = false;

    private ToneGenerator toneGenerator;

    private FirebaseAuth auth;

    // --- NEW: reference to phones node
    private DatabaseReference phonesRef;

    // prevent mic restarting when we decide to redirect
    private boolean disableMic = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();

        // --- NEW: init phonesRef
        phonesRef = FirebaseDatabase.getInstance().getReference("phones");

        nameEditText = findViewById(R.id.nameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        emergencyEditText = findViewById(R.id.emergencyEditText);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        if (getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("isRegistered", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        isUrdu = prefs.getBoolean("language_urdu", false);

        setupTTS();
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur", "PK") : Locale.US);

                step = -1;
                mainHandler.postDelayed(() -> {
                    speakWithGuaranteedDelay(isUrdu
                            ? "آواز کے ذریعے رجسٹریشن میں خوش آمدید۔ آواز کی رجسٹریشن کے لیے مائیک کی اجازت ضروری ہے، براہِ کرم کسی کی مدد لیں۔"
                            : "Welcome to voice registration. For voice registration you have to give permission to mic, please take someone help for this", 3500);
                }, 1000);
            }
        });
    }

    private void speakWithGuaranteedDelay(String text, int delayMillis) {
        if (tts != null) {
            tts.stop();
        }

        mainHandler.removeCallbacksAndMessages(null);
        isTtsActive = true;

        String utteranceId = "UTT_" + System.currentTimeMillis();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
            }

            @Override
            public void onDone(String s) {
                if (s.equals(utteranceId)) {
                    mainHandler.post(() -> {
                        isTtsActive = false;
                        onTtsComplete();
                    });
                }
            }

            @Override
            public void onError(String s) {
                mainHandler.post(() -> {
                    isTtsActive = false;
                    onTtsComplete();
                });
            }
        });
    }

    private void onTtsComplete() {
        // if we've disabled mic because we're redirecting, do not start listening again
        if (disableMic) {
            Log.d("RegDebug", "onTtsComplete: mic disabled, not starting listener.");
            return;
        }

        switch (step) {
            case -1:
                checkMicPermission();
                break;
            case 0:
            case 1:
            case 2:
            case 3:
                startListeningWithSafety();
                break;
        }
    }

    private void checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            permissionGranted = true;
            step = 0;
            askNextQuestion();
        }
    }

    private void askNextQuestion() {
        String msg;
        int delayTime = 4000;

        switch (step) {
            case 0:
                msg = isUrdu ? "براہ کرم اپنا نام بتائیں" : "Please tell your name";
                break;
            case 1:
                msg = isUrdu ? "براہ کرم اپنا فون نمبر بتائیں" : "Please tell your phone number";
                break;
            case 2:
                msg = isUrdu ? "براہ کرم ایمرجنسی نمبر بتائیں" : "Please tell your emergency contact";
                break;
            case 3:
                msg = isUrdu
                        ? "آپ کا نام " + userName + " ہے، فون " + userPhone + " ہے، ایمرجنسی " + userEmergency + " ہے۔ کیا رجسٹر کروں؟"
                        : "Name: " + userName + ", Phone: " + userPhone + ", Emergency: " + userEmergency + ". Should I register?";
                confirmStep = true;
                delayTime = 6000;
                break;
            default:
                msg = "";
        }

        speakWithGuaranteedDelay(msg, delayTime);
    }

    private void startListeningWithSafety() {
        if (isTtsActive) {
            mainHandler.postDelayed(this::startListeningWithSafety, 200);
            return;
        }

        if (!permissionGranted) return;
        // also do not start listening if disableMic set
        if (disableMic) {
            Log.d("RegDebug", "startListeningWithSafety: disabled, skipping start");
            return;
        }
        mainHandler.postDelayed(this::actuallyStartListening, 500);
    }

    private void actuallyStartListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech Recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SimpleRecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleVoiceInput(matches.get(0));
                } else {
                    retryListening();
                }
            }

            @Override
            public void onError(int error) {
                retryListening();
            }
        });

        if (speechIntent == null) {
            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isUrdu ? "ur-PK" : Locale.getDefault());
            speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        }

        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
            speechRecognizer.startListening(speechIntent);
        } catch (Exception e) {
            retryListening();
        }
    }

    private void stopSpeechRecognition() {
        try {
            if (speechRecognizer != null) {
                try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
                try { speechRecognizer.cancel(); } catch (Exception ignored) {}
                try { speechRecognizer.destroy(); } catch (Exception ignored) {}
                speechRecognizer = null;
            }
            // NOTE: do NOT reset permissionGranted here — we rely on permission flag remaining true
            // permissionGranted = false; // <-- removed intentionally to avoid blocking subsequent restarts
        } catch (Exception e) {
            Log.w("RegDebug", "stopSpeechRecognition error: " + e.getMessage());
        }
    }

    private void handleVoiceInput(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) {
            retryListening();
            return;
        }

        spokenText = spokenText.toLowerCase().trim();

        if (confirmStep) {
            processConfirmation(spokenText);
        } else {
            processUserInput(spokenText);
        }
    }

    private void processConfirmation(String spokenText) {
        if (spokenText.contains("yes") || spokenText.contains("yaas") || spokenText.contains("yass") || spokenText.contains("haan") || spokenText.contains("ہاں")) {
            if (step == 3) {
                // --- CHANGED: call the phone-checking flow instead of directly register
                checkPhoneThenRegister();
            } else {
                confirmStep = false;
                step++;
                askNextQuestion();
            }
        } else if (spokenText.contains("no") || spokenText.contains("nahin") || spokenText.contains("نہیں")) {
            resetCurrentStep();
        } else {
            retryListening();
        }
    }

    private void processUserInput(String spokenText) {
        switch (step) {
            case 0:
                userName = spokenText;
                nameEditText.setText(userName);
                askConfirmation(isUrdu ? "آپ نے نام " + userName + " بتایا۔ درست ہے؟" : "You said name " + userName + ". Correct?");
                break;
            case 1:
                userPhone = convertToDigits(spokenText);
                if (userPhone.length() < 11) {
                    speakWithGuaranteedDelay(isUrdu ? "فون نمبر درست نہیں۔ دوبارہ کہیں" : "Invalid phone. Try again", 4000);
                    return;
                }
                phoneEditText.setText(userPhone);
                askConfirmation(isUrdu ? "آپ نے فون " + userPhone + " بتایا۔ درست ہے؟" : "You said phone " + userPhone + ". Correct?");
                break;
            case 2:
                userEmergency = convertToDigits(spokenText);
                if (userEmergency.length() < 11) {
                    speakWithGuaranteedDelay(isUrdu ? "ایمرجنسی نمبر درست نہیں۔ دوبارہ کہیں" : "Invalid emergency number. Try again", 4000);
                    return;
                }
                emergencyEditText.setText(userEmergency);
                askConfirmation(isUrdu ? "آپ نے ایمرجنسی نمبر " + userEmergency + " بتایا۔ درست ہے؟" : "You said emergency " + userEmergency + ". Correct?");
                break;
        }
    }

    private void askConfirmation(String message) {
        confirmStep = true;
        speakWithGuaranteedDelay(message, 5000);
    }

    private void retryListening() {
        speakWithGuaranteedDelay(isUrdu ? "سنی نہیں۔ دوبارہ کوشش کریں" : "Didn't hear. Please try again", 3000);
    }

    private void resetCurrentStep() {
        confirmStep = false;
        switch (step) {
            case 0:
                userName = "";
                nameEditText.setText("");
                break;
            case 1:
                userPhone = "";
                phoneEditText.setText("");
                break;
            case 2:
                userEmergency = "";
                emergencyEditText.setText("");
                break;
            case 3:
                step = 0;
                userName = userPhone = userEmergency = "";
                nameEditText.setText("");
                phoneEditText.setText("");
                emergencyEditText.setText("");
                break;
        }
        askNextQuestion();
    }

    private String convertToDigits(String spokenText) {
        return spokenText.toLowerCase()
                .replaceAll("zero", "0").replaceAll("صفر", "0")
                .replaceAll("one", "1").replaceAll("ایک", "1")
                .replaceAll("two", "2").replaceAll("دو", "2")
                .replaceAll("three", "3").replaceAll("تین", "3")
                .replaceAll("four", "4").replaceAll("چار", "4")
                .replaceAll("five", "5").replaceAll("پانچ", "5")
                .replaceAll("six", "6").replaceAll("چھ", "6")
                .replaceAll("seven", "7").replaceAll("سات", "7")
                .replaceAll("eight", "8").replaceAll("آٹھ", "8")
                .replaceAll("nine", "9").replaceAll("نو", "9")
                .replaceAll("[^0-9]", "");
    }

    // --- IMPROVED: normalize phone to +92 format (robust)
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        // remove spaces and non-digit/plus
        phone = phone.replaceAll("\\s+", "").replaceAll("[^0-9\\+]", "");
        // already +92XXXXXXXXX (13 chars: + + 12 digits?) e.g. +923XXXXXXXXX => length 13
        if (phone.startsWith("+92") && phone.length() >= 12) {
            // ensure it's +92 followed by 10 digits
            return phone;
        }
        // starts with 0 and 11 digits: 03XXXXXXXXX
        if (phone.startsWith("0") && phone.length() == 11) {
            return "+92" + phone.substring(1);
        }
        // starts with 92 and 12 digits: 923XXXXXXXXX
        if (phone.startsWith("92") && phone.length() == 12) {
            return "+" + phone;
        }
        // if user supplied 3XXXXXXXXX (without leading 0) and length 10
        if (phone.length() == 10 && phone.startsWith("3")) {
            return "+92" + phone;
        }
        // fallback: return as-is
        return phone;
    }

    // --- NEW: check phones node first; if exists -> go to VerifyOTPActivity, else continue to auth-check and create user
    private void checkPhoneThenRegister() {
        // --- IMPORTANT: disable mic immediately to prevent beep/auto-restart while checking
        disableMic = true;
        stopSpeechRecognition();

        final String raw = userPhone != null ? userPhone.trim() : "";
        final String normalized = normalizePhone(raw);

        // quick local update to show normalized in UI
        userPhone = normalized;
        phoneEditText.setText(userPhone);

        final ArrayList<String> candidates = new ArrayList<>();
        if (normalized != null && !normalized.isEmpty()) candidates.add(normalized);        // +92...
        if (normalized != null && normalized.startsWith("+")) candidates.add(normalized.substring(1)); // 92...
        if (normalized != null && normalized.startsWith("+92") && normalized.length() >= 4) {
            candidates.add("0" + normalized.substring(3)); // 03...
        }
        if (raw != null && !raw.isEmpty() && !candidates.contains(raw)) candidates.add(raw); // raw spoken
        if (raw != null && raw.startsWith("92") && !raw.startsWith("+")) candidates.add("+" + raw);

        Log.d("RegDebug", "Candidates to check: " + candidates.toString());
        speakWithGuaranteedDelay(isUrdu ? "فون فارمیٹس چیک کر رہا ہوں" : "Checking phone formats", 700);

        tryCheckCandidates(candidates, 0);
    }

    private void tryCheckCandidates(final ArrayList<String> candidates, final int index) {
        if (index >= candidates.size()) {
            // none matched -> verify with FirebaseAuth if an account exists for this constructed email.
            Log.d("RegDebug", "No match found in phones node. Checking FirebaseAuth for existing email.");
            checkAuthEmailBeforeCreate(normalizePhone(userPhone));
            return;
        }

        final String key = candidates.get(index);
        if (key == null || key.trim().isEmpty()) {
            tryCheckCandidates(candidates, index + 1);
            return;
        }

        Log.d("RegDebug", "Checking phones/" + key);
        phonesRef.child(key).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d("RegDebug", "Found existing phone key: " + key);
                    // disable mic and stop recognition before speaking and redirecting
                    disableMic = true;
                    stopSpeechRecognition();

                    String msg = isUrdu ? "آپ کا یہ نمبر پہلے سے موجود ہے۔ میں آپ کو automatic verification پر لے جا رہا ہوں۔"
                            : "This phone already exists. Redirecting to automatic verification.";
                    speakWithGuaranteedDelay(msg, 1000);

                    mainHandler.postDelayed(() -> {
                        Intent i = new Intent(RegistrationActivity.this, VerifyOTPActivity.class);
                        i.putExtra("phone", key);
                        i.putExtra("existing_user", true);
                        startActivity(i);
                    }, 1300);

                } else {
                    Log.d("RegDebug", "Not found: " + key + " — trying next.");
                    tryCheckCandidates(candidates, index + 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("RegDebug", "DB read error for " + key + " -> " + error.getMessage());
                // re-enable mic so user can try again after hearing the error
                disableMic = false;
                speakWithGuaranteedDelay(isUrdu ? "سرور پر مسئلہ ہے، دوبارہ کوشش کریں۔" : "Server error. Please try again.", 2000);
            }
        });
    }

    // --- NEW: check FirebaseAuth for email existence before attempting create
    private void checkAuthEmailBeforeCreate(final String normalizedPhone) {
        final String normalized = normalizePhone(normalizedPhone);
        final String email = normalized + "@bazmeraah.com";

        Log.d("RegDebug", "Checking FirebaseAuth for email: " + email);
        speakWithGuaranteedDelay(isUrdu ? "سرور پر رجسٹریشن چیک کر رہا ہوں" : "Checking registration on server", 800);

        auth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        boolean isExisting = false;
                        if (task.getResult() != null && task.getResult().getSignInMethods() != null) {
                            isExisting = !task.getResult().getSignInMethods().isEmpty();
                        }

                        if (isExisting) {
                            // account exists in FirebaseAuth -> redirect to OTP verification
                            Log.d("RegDebug", "FirebaseAuth: email already exists for " + email);
                            disableMic = true;
                            stopSpeechRecognition();

                            String msg = isUrdu ? "یہ نمبر پہلے سے رجسٹرڈ ہے۔ توثیق کے لئے لے جا رہا ہوں۔" :
                                    "This number is already registered. Redirecting to verification.";
                            speakWithGuaranteedDelay(msg, 1000);

                            mainHandler.postDelayed(() -> {
                                Intent i = new Intent(RegistrationActivity.this, VerifyOTPActivity.class);
                                i.putExtra("phone", normalized);
                                i.putExtra("existing_user", true);
                                startActivity(i);
                            }, 1300);

                        } else {
                            // no existing auth account -> safe to create
                            Log.d("RegDebug", "FirebaseAuth: no account found for " + email + " — creating new user.");
                            // keep mic disabled while creating/redirecting to avoid interruptions
                            disableMic = true;
                            actuallyCreateUser(normalized);
                        }
                    } else {
                        Log.e("RegDebug", "fetchSignInMethodsForEmail failed: " + (task.getException()!=null?task.getException().getMessage():"unknown"));
                        // re-enable mic so user can try again after hearing the error
                        disableMic = false;
                        speakWithGuaranteedDelay(isUrdu ? "سرور پر مسئلہ ہے۔ دوبارہ کوشش کریں۔" : "Server error. Please try again.", 2000);
                    }
                });
    }

    // --- RENAMED original registerUser logic into actuallyCreateUser to keep flow clear
    private void actuallyCreateUser(String normalizedPhone) {

        // temporary email
        String email = normalizedPhone + "@bazmeraah.com";
        String password = "12345678";

        Log.d("RegDebug", "Attempt create user with email: " + email);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser firebaseUser = auth.getCurrentUser();
                        if (firebaseUser == null) {
                            // re-enable mic on failure to let user try again
                            disableMic = false;
                            speakWithGuaranteedDelay(isUrdu ? "رجسٹریشن میں مسئلہ آیا" : "Registration failed", 3000);
                            return;
                        }
                        String uid = firebaseUser.getUid();

                        FirebaseDatabase.getInstance().getReference("Users")
                                .child(uid)
                                .setValue(new User(userName, normalizedPhone, userEmergency))
                                .addOnCompleteListener(task1 -> {

                                    // write phones/{phone} = uid for future checks
                                    phonesRef.child(normalizedPhone).setValue(uid);

                                    getSharedPreferences("UserPrefs", MODE_PRIVATE).edit()
                                            .putString("uid", uid)
                                            .putBoolean("isRegistered", true)
                                            .putString("name", userName)
                                            .putString("phone", normalizedPhone)
                                            .putString("emergency", userEmergency)
                                            .apply();

                                    speakWithGuaranteedDelay(isUrdu ?
                                                    "رجسٹریشن کامیاب۔ خوش آمدید " + userName :
                                                    "Registration successful. Welcome " + userName,
                                            3000);

                                    mainHandler.postDelayed(() -> {
                                        startActivity(new Intent(this, MainActivity.class));
                                        finish();
                                    }, 3500);
                                });

                    } else {
                        // improved handling
                        Exception ex = task.getException();
                        Log.e("RegDebug", "createUser failed: ", ex);

                        boolean treatAsExisting = false;
                        if (ex != null) {
                            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                            if (msg.contains("already") || msg.contains("in use") || msg.contains("exist")) {
                                treatAsExisting = true;
                            }
                        }

                        if (treatAsExisting) {
                            // disable mic and stop recognition before speaking and redirecting
                            disableMic = true;
                            stopSpeechRecognition();

                            String speakMsg = isUrdu ? "یہ نمبر پہلے ہی موجود ہے۔ میں آپ کو verification پر لے جا رہا ہوں۔" :
                                    "This number is already registered. Redirecting to verification.";
                            speakWithGuaranteedDelay(speakMsg, 1000);
                            mainHandler.postDelayed(() -> {
                                Intent i = new Intent(RegistrationActivity.this, VerifyOTPActivity.class);
                                i.putExtra("phone", normalizedPhone);
                                i.putExtra("existing_user", true);
                                startActivity(i);
                            }, 1300);
                        } else {
                            // generic failure (network, invalid email, etc.) -> re-enable mic so user can retry
                            disableMic = false;
                            speakWithGuaranteedDelay(isUrdu ? "رجسٹریشن میں مسئلہ آیا" : "Registration failed", 3000);
                        }
                    }

                });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSpeechRecognition();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (toneGenerator != null) toneGenerator.release();
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true;
            step = 0;
            askNextQuestion();
        } else {
            speakWithGuaranteedDelay(isUrdu ? "مائیک کی اجازت ضروری ہے" : "Mic permission required", 3000);
        }
    }
}
