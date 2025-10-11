package com.example.learnly;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    private FirebaseAuth mAuth;
    private TextView settingsTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        Log.d(TAG, "instance:received");
        settingsTextView = findViewById(R.id.settingsTextView);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Log.d(TAG, "user:received");
            settingsTextView.setText(user.getEmail() + " Settings");
        } else {
            Log.d(TAG, "user:not received");
            Intent i = new Intent(SettingsActivity.this, MainActivity.class);
            startActivity(i);
        }

    }

    public void logout(View view) {
        mAuth.signOut();
        Log.d(TAG, "user:logged out");
        Intent i = new Intent(SettingsActivity.this, MainActivity.class);
        startActivity(i);
    }

    public void changePassword(View view) {
        mAuth.signOut(); //signed out
        Log.d(TAG, "user:logged out and sent to ForgotPasswordActivity");
        Intent i = new Intent(SettingsActivity.this, ForgotPasswordActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
