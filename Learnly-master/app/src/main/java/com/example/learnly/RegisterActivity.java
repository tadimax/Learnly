package com.example.learnly
        ;

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
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    private FirebaseAuth mAuth;
    private EditText emailEditText;
    private EditText passwordEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        emailEditText = findViewById(R.id.editTextTextEmailAddress_register);
        passwordEditText = findViewById(R.id.editTextTextPassword_register);

        findViewById(R.id.textView_goToLogin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToLoginActivity();
            }
        });
    }

    public void registerUser(View v) {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty()) {
            emailEditText.setError("Email is required!");
            emailEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Password is required!");
            passwordEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password should be at least 6 characters!");
            passwordEditText.requestFocus();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmailAndPassword:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            Toast.makeText(RegisterActivity.this, "Registration successful for " + user.getEmail() + "!",
                                    Toast.LENGTH_SHORT).show();

                            startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                            finish();
                        } else {
                            Log.w(TAG, "createUserWithEmailAndPassword:failure", task.getException());
                            String errorMessage = "Authentication failed.";
                            if (task.getException() != null) {
                                errorMessage = task.getException().getMessage();
                            }
                            Toast.makeText(RegisterActivity.this, "Authentication failed: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void goToLoginActivity() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
