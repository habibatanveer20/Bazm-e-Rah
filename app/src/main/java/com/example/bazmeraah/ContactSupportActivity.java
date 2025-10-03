package com.example.bazmeraah;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class ContactSupportActivity extends AppCompatActivity {

    EditText etName, etContact, etMessage;
    Button btnSend;

    DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_support);

        etName = findViewById(R.id.et_name);
        etContact = findViewById(R.id.et_contact);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send_message);

        // Firebase reference
        databaseReference = FirebaseDatabase.getInstance().getReference("SupportMessages");

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String name = etName.getText().toString().trim();
        String contact = etContact.getText().toString().trim();
        String message = etMessage.getText().toString().trim();

        if(TextUtils.isEmpty(name) || TextUtils.isEmpty(contact) || TextUtils.isEmpty(message)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a HashMap to store data
        HashMap<String, String> map = new HashMap<>();
        map.put("name", name);
        map.put("contact", contact);
        map.put("message", message);

        // Push message to Firebase
        databaseReference.push().setValue(map).addOnCompleteListener(task -> {
            if(task.isSuccessful()) {
                Toast.makeText(ContactSupportActivity.this, "Message sent successfully!", Toast.LENGTH_SHORT).show();
                etMessage.setText(""); // clear message
            } else {
                Toast.makeText(ContactSupportActivity.this, "Failed to send message!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
