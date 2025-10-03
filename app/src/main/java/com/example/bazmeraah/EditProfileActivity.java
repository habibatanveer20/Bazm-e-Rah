package com.example.bazmeraah;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class EditProfileActivity extends AppCompatActivity {

    private EditText editName, editPhone, editEmergency;
    private Button btnSave;
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Initialize views
        editName = findViewById(R.id.edit_name);
        editPhone = findViewById(R.id.edit_phone);
        editEmergency = findViewById(R.id.edit_emergency_number);
        btnSave = findViewById(R.id.btn_save_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userId = currentUser.getUid();
        usersRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);

        // Load existing data
        loadUserData();

        // Save button click
        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void loadUserData() {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String emergency = snapshot.child("emergency").getValue(String.class);

                    editName.setText(name);
                    editPhone.setText(phone);
                    editEmergency.setText(emergency);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditProfileActivity.this, "Failed to load data!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfile() {
        String name = editName.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();
        String emergency = editEmergency.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            editName.setError("Enter your name");
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            editPhone.setError("Enter your phone number");
            return;
        }
        if (TextUtils.isEmpty(emergency)) {
            editEmergency.setError("Enter emergency number");
            return;
        }

        HashMap<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("phone", phone);
        userMap.put("emergency", emergency);

        usersRef.updateChildren(userMap).addOnCompleteListener(task -> {
            if(task.isSuccessful()) {
                Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                finish(); // Go back to previous screen
            } else {
                Toast.makeText(EditProfileActivity.this, "Failed to update profile!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
