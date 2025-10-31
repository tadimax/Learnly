package com.example.learnly;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

// Manages the Parental Controls screen for mini-apps and difficulty
public class ParentalActivity extends AppCompatActivity {

    // A tag for identifying log messages from this file
    private static final String TAG = "ParentalActivity";

    // Initializes the activity and links the layout file
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parental);
        Log.d(TAG, "ParentalActivity created.");

    }
}