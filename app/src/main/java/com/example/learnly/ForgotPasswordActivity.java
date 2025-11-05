package com.example.learnly;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

// Manages the password reset logic
public class ForgotPasswordActivity extends AppCompatActivity {

    // A tag for identifying log messages from this file
    private static final String TAG = "ForgotPasswordActivity";

    // Firebase and UI variables
    private FirebaseAuth mAuth;
    private EditText emailEditText;

    // Initializes the activity, links UI elements, and sets listeners
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();
        emailEditText = findViewById(R.id.editTextTextEmailAddress_reset);

        // Listener for the Reset button
        findViewById(R.id.button_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPassword(v);
            }
        });

        // Listener for the "Back to Login" button
        findViewById(R.id.button_back_to_login).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToMainActivity();
            }
        });
    }

    // Validates email and sends a password reset link
    public void resetPassword(View view) {
        String email = emailEditText.getText().toString().trim();

        // Check if email is empty
        if (email.isEmpty()) {
            emailEditText.setError("Email is required!");
            emailEditText.requestFocus();
            return; // Stop the function
        }

        // Send the reset email via Firebase
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // On success, show message and go to login
                            Log.d(TAG, "Password reset email sent.");
                            Toast.makeText(ForgotPasswordActivity.this, "Password reset email sent!", Toast.LENGTH_LONG).show();
                            goToMainActivity();
                        } else {
                            // On failure, show error
                            Log.w(TAG, "sendPasswordResetEmail:failure", task.getException());
                            Toast.makeText(ForgotPasswordActivity.this, "Failed to send reset email. Check if the email is correct.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // Navigates back to the main login screen and clears other activities
    private void goToMainActivity() {
        Intent intent = new Intent(ForgotPasswordActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}