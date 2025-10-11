package com.example.learnly;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextView welcomeTextView;
    private Button settingsButton, storyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        welcomeTextView = findViewById(R.id.welcomeTextView);
        settingsButton = findViewById(R.id.settingsButton);
        storyButton = findViewById(R.id.storyButton);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            welcomeTextView.setText("Welcome, " + user.getEmail() + "!");
        } else {
            Intent i = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(i);
        }

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
                startActivity(intent);

            }
        });

        storyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, StoryTimeActivity.class);
                startActivity(intent);

            }
        });
    }
}
