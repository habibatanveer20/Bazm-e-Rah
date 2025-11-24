package com.example.bazmeraah;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class VerifyOTPActivity extends AppCompatActivity {

    private static final String TAG = "VerifyOTPActivity";

    private TextView infoText;
    private EditText otpEdit;
    private Button verifyBtn, resendBtn;
    private ProgressBar progressBar;

    private TextToSpeech tts;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private FirebaseAuth mAuth;
    private String phoneNumber;
    private boolean existingUser = false;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otpactivity);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        infoText = findViewById(R.id.infoText);
        otpEdit = findViewById(R.id.otpEdit);
        verifyBtn = findViewById(R.id.verifyBtn);
        resendBtn = findViewById(R.id.resendBtn);
        progressBar = findViewById(R.id.progressBar);

        Intent i = getIntent();
        phoneNumber = i != null ? i.getStringExtra("phone") : null;
        existingUser = i != null && i.getBooleanExtra("existing_user", false);

        // Normalize phone if needed (optional)
        if (phoneNumber != null) phoneNumber = normalizePhone(phoneNumber);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
                announceForUser(
                        isUrdu() ?
                                "ہم نے آپ کے موبائل نمبر پر او ٹی پی بھیجا ہے۔ اگر خود بخود آئے گا تو آپ لاگ ان ہو جائیں گے۔" :
                                "We sent an OTP to your number. If auto-retrieved, you will be logged in."
                );
            }
        });

        infoText.setText(getScreenText());
        infoText.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);

        setupCallbacks();

        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            startPhoneVerification(phoneNumber, null);
        } else {
            speak(isUrdu() ? "فون نمبر دستیاب نہیں۔" : "Phone number not available.");
        }

        verifyBtn.setOnClickListener(v -> {
            String code = otpEdit.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                speak(isUrdu() ? "براہ کرم او ٹی پی درج کریں۔" : "Please enter OTP.");
                return;
            }
            progressBar.setVisibility(android.view.View.VISIBLE);
            verifyManualCode(code);
        });

        resendBtn.setOnClickListener(v -> {
            speak(isUrdu() ? "او ٹی پی دوبارہ بھیج رہا ہوں۔" : "Resending OTP.");
            startPhoneVerification(phoneNumber, resendToken);
        });
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        phone = phone.replaceAll("\\s+", "");
        if (phone.startsWith("0") && phone.length() == 11) {
            return "+92" + phone.substring(1);
        } else if (phone.startsWith("92") && phone.length() == 12) {
            return "+" + phone;
        } else {
            return phone;
        }
    }

    private boolean isUrdu() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        return prefs.getBoolean("language_urdu", false);
    }

    private String getScreenText() {
        if (phoneNumber == null) phoneNumber = "";
        if (isUrdu()) {
            return "او ٹی پی " + phoneNumber + " پر بھیجا گیا ہے۔ اگر SMS خود بخود ملا تو آپ لاگ ان ہو جائیں گے۔";
        }
        return "OTP sent to " + phoneNumber + ". If SMS auto-retrieves you will be logged in.";
    }

    private void setupCallbacks() {
        callbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                Log.d(TAG, "onVerificationCompleted (auto)");
                speak(isUrdu() ? "او ٹی پی خود بخود موصول ہو گیا۔" : "OTP auto-retrieved.");
                // When auto retrieved, verificationId might be null but credential contains everything
                signIn(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Log.e(TAG, "onVerificationFailed: " + e.getMessage(), e);
                progressBar.setVisibility(android.view.View.GONE);

                // Friendly message for common problems
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("quota") || msg.contains("exceeded")) {
                    speak(isUrdu() ? "او ٹی پی سروس کی حد پوری ہو گئی ہے۔ بعد میں کوشش کریں۔" : "SMS quota exceeded. Try later.");
                } else {
                    speak(isUrdu() ? "ویریفکیشن ناکام۔ فون نمبر چیک کریں۔" : "Verification failed. Check the phone number.");
                }
            }

            @Override
            public void onCodeSent(@NonNull String verId,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                Log.d(TAG, "onCodeSent: " + verId);
                verificationId = verId;
                resendToken = token;
                progressBar.setVisibility(android.view.View.GONE);
                speak(isUrdu() ? "او ٹی پی بھیج دیا گیا۔" : "OTP sent.");
            }
        };
    }

    private void startPhoneVerification(String number, PhoneAuthProvider.ForceResendingToken token) {
        try {
            progressBar.setVisibility(android.view.View.VISIBLE);

            PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(mAuth)
                    .setPhoneNumber(number)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(callbacks);

            if (token != null) builder.setForceResendingToken(token);

            PhoneAuthProvider.verifyPhoneNumber(builder.build());

        } catch (Exception ex) {
            Log.e(TAG, "startPhoneVerification error", ex);
            progressBar.setVisibility(android.view.View.GONE);
            speak(isUrdu() ? "او ٹی پی بھیجنے میں مسئلہ آیا۔" : "Error starting verification.");
        }
    }

    private void verifyManualCode(String code) {
        if (verificationId == null) {
            progressBar.setVisibility(android.view.View.GONE);
            speak(isUrdu() ? "او ٹی پی دستیاب نہیں۔ دوبارہ بھیجیں۔" : "OTP not received.");
            return;
        }
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signIn(credential);
    }

    private void signIn(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(android.view.View.GONE);

                    if (!task.isSuccessful()) {
                        Log.e(TAG, "signIn failed", task.getException());
                        speak(isUrdu() ? "او ٹی پی غلط۔ دوبارہ کوشش کریں۔" : "Incorrect OTP. Try again.");
                        return;
                    }

                    FirebaseUser firebaseUser = task.getResult().getUser();
                    if (firebaseUser == null) {
                        speak(isUrdu() ? "لاگ ان میں مسئلہ آیا۔" : "Sign-in problem occurred.");
                        return;
                    }

                    String uid = firebaseUser.getUid();
                    DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(uid);

                    userRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                User u = snapshot.getValue(User.class);
                                saveAndGo(u);
                            } else {
                                // fallback: try phones -> uid mapping
                                FirebaseDatabase.getInstance().getReference("phones")
                                        .child(phoneNumber)
                                        .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot s2) {
                                                if (s2.exists()) {
                                                    String mappedUid = s2.getValue(String.class);
                                                    FirebaseDatabase.getInstance()
                                                            .getReference("Users")
                                                            .child(mappedUid)
                                                            .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                                                                @Override
                                                                public void onDataChange(@NonNull DataSnapshot s3) {
                                                                    if (s3.exists()) {
                                                                        User u = s3.getValue(User.class);
                                                                        saveAndGo(u);
                                                                    } else {
                                                                        saveAndGo(new User("", phoneNumber, ""));
                                                                    }
                                                                }

                                                                @Override
                                                                public void onCancelled(@NonNull DatabaseError error) {
                                                                    saveAndGo(new User("", phoneNumber, ""));
                                                                }
                                                            });
                                                } else {
                                                    saveAndGo(new User("", phoneNumber, ""));
                                                }
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                                saveAndGo(new User("", phoneNumber, ""));
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            saveAndGo(new User("", phoneNumber, ""));
                        }
                    });
                });
    }

    private void saveAndGo(User u) {
        SharedPreferences.Editor ed = getSharedPreferences("UserPrefs", MODE_PRIVATE).edit();

        ed.putBoolean("isRegistered", true);
        ed.putString("name", u != null && u.name != null ? u.name : "");
        ed.putString("phone", u != null && u.phone != null ? u.phone : phoneNumber);
        ed.putString("emergency", u != null && u.emergency != null ? u.emergency : "");
        ed.apply();

        speak(isUrdu() ? "آپ کامیابی سے لاگ ان ہو گئے ہیں۔" : "Logged in successfully.");

        mainHandler.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 1400);
    }

    private void speak(String text) {
        if (tts != null && text != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "otp");
        }
    }

    private void announceForUser(String text) {
        speak(text);
        if (infoText != null) {
            infoText.setText(text);
            infoText.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }
}
