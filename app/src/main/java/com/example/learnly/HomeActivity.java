package com.example.learnly;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.Log;

import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Manages the main home screen, displays mini-apps, and handles navigation
public class HomeActivity extends AppCompatActivity {

    // A tag for identifying log messages from this file
    private static final String TAG = "HomeActivity";

    // Firebase authentication and database variables
    private FirebaseAuth mAuth;
    private DatabaseReference mUserDatabaseRef;

    // UI element variables
    private TextView welcomeTextView;
    private Button settingsButton, storyButton, spellingButton, memoryMatchButton, colourPatternsButton, numberFunButton, weeklyQuizButton;

    // Initializes the activity, links UI elements, and loads user data
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        welcomeTextView = findViewById(R.id.welcomeTextView);

        // Find all buttons by their IDs from activity_home.xml
        settingsButton = findViewById(R.id.settingsButton);
        storyButton = findViewById(R.id.storyButton);
        spellingButton = findViewById(R.id.button2);
        memoryMatchButton = findViewById(R.id.button3);
        colourPatternsButton = findViewById(R.id.button4);
        numberFunButton = findViewById(R.id.button5);
        weeklyQuizButton = findViewById(R.id.button6);


        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            welcomeTextView.setText("Hello");

            String userId = user.getUid();
            mUserDatabaseRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

            // This will now load settings AND update button states
            loadUserSettings();

        } else {
            Intent i = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(i);
            finish();
        }

        // --- Set Click Listeners For All Buttons ---

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPinCheckLogic();
            }
        });

        storyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, StoryTimeActivity.class);
                startActivity(intent);
            }
        });

        spellingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Start SpellingTimeActivity
                // Intent intent = new Intent(HomeActivity.this, SpellingTimeActivity.class);
                // startActivity(intent);
                Log.d(TAG, "Spelling Button Clicked");
            }
        });

        memoryMatchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Start MemoryMatchActivity
                // Intent intent = new Intent(HomeActivity.this, MemoryMatchActivity.class);
                // startActivity(intent);
                Log.d(TAG, "Memory Match Button Clicked");
            }
        });

        colourPatternsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Start ColourPatternsActivity
                // Intent intent = new Intent(HomeActivity.this, ColourPatternsActivity.class);
                // startActivity(intent);
                Log.d(TAG, "Colour Patterns Button Clicked");
            }
        });

        numberFunButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Start NumberFunActivity
                // Intent intent = new Intent(HomeActivity.this, NumberFunActivity.class);
                // startActivity(intent);
                Log.d(TAG, "Number Fun Button Clicked");
            }
        });

        weeklyQuizButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Start WeeklyQuizActivity
                // Intent intent = new Intent(HomeActivity.this, WeeklyQuizActivity.class);
                // startActivity(intent);
                Log.d(TAG, "Weekly Quiz Button Clicked");
            }
        });
    }

    // Fetches user settings from Firebase and updates the UI (Welcome Text AND Button States)
    private void loadUserSettings() {
        // Use addValueEventListener to get live updates if settings change
        mUserDatabaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                // 1. Load Child Name for Welcome Message
                if (snapshot.hasChild("childName")) {
                    String childName = snapshot.child("childName").getValue(String.class);
                    if (childName != null && !childName.trim().isEmpty()) {
                        welcomeTextView.setText("Hello, " + childName);
                    } else {
                        welcomeTextView.setText("Hello");
                    }
                } else {
                    welcomeTextView.setText("Hello");
                }

                // 2. Load Mini-App Settings to Enable/Disable Buttons
                DataSnapshot appsSnapshot = snapshot.child("miniApps");

                // Check each app. Default to 'true' (enabled) if no setting is found.
                // (isEnabled == null || isEnabled) means "Enable if the setting doesn't exist OR if it's set to true"

                Boolean storyEnabled = appsSnapshot.child("Story Time").child("enabled").getValue(Boolean.class);
                storyButton.setEnabled(storyEnabled == null || storyEnabled);

                Boolean spellingEnabled = appsSnapshot.child("Spelling Time").child("enabled").getValue(Boolean.class);
                spellingButton.setEnabled(spellingEnabled == null || spellingEnabled);

                Boolean matchEnabled = appsSnapshot.child("Memory Match").child("enabled").getValue(Boolean.class);
                memoryMatchButton.setEnabled(matchEnabled == null || matchEnabled);

                Boolean colourEnabled = appsSnapshot.child("Colour Patterns").child("enabled").getValue(Boolean.class);
                colourPatternsButton.setEnabled(colourEnabled == null || colourEnabled);

                Boolean numberEnabled = appsSnapshot.child("Number Fun").child("enabled").getValue(Boolean.class);
                numberFunButton.setEnabled(numberEnabled == null || numberEnabled);

                Boolean quizEnabled = appsSnapshot.child("Weekly Quiz").child("enabled").getValue(Boolean.class);
                weeklyQuizButton.setEnabled(quizEnabled == null || quizEnabled);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to load user settings.", error.toException());
                // In case of error, just leave buttons enabled by default
            }
        });
    }

    // Checks if a PIN exists and shows the correct dialog
    private void showPinCheckLogic() {
        SharedPreferences prefs = getEncryptedPrefs();
        if (prefs == null) {
            Log.e(TAG, "Security error. Cannot open settings.");
            return;
        }
        String savedPin = prefs.getString("parent_pin", null);

        if (savedPin == null) {
            showCreatePinDialog(prefs);
        } else {
            showEnterPinDialog(prefs, savedPin);
        }
    }

    // Displays a dialog to enter an existing PIN
    private void showEnterPinDialog(final SharedPreferences prefs, final String correctPin) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter PIN");
        builder.setMessage("Please enter your 4-digit parent PIN to access settings.");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("4-digit PIN");
        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String enteredPin = input.getText().toString();
                if (enteredPin.equals(correctPin)) {
                    launchSettingsActivity();
                } else {
                    Log.w(TAG, "Incorrect PIN entered.");
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    // Displays a dialog to create a new PIN, forcing completion
    private void showCreatePinDialog(final SharedPreferences prefs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create a 4-Digit PIN");
        builder.setMessage("This PIN will be required to access parent settings.");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 8, 48, 8);

        final EditText inputPin = new EditText(this);
        inputPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        inputPin.setHint("Enter 4-digit PIN");
        layout.addView(inputPin);

        final EditText confirmPin = new EditText(this);
        confirmPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirmPin.setHint("Confirm PIN");
        layout.addView(confirmPin);

        builder.setView(layout);

        builder.setPositiveButton("Create", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String pin1 = inputPin.getText().toString();
                String pin2 = confirmPin.getText().toString();

                if (pin1.length() != 4) {
                    Log.w(TAG, "PIN must be 4 digits.");
                    showCreatePinDialog(prefs);
                } else if (!pin1.equals(pin2)) {
                    Log.w(TAG, "PINs do not match.");
                    showCreatePinDialog(prefs);
                } else {
                    prefs.edit().putString("parent_pin", pin1).apply();
                    Log.d(TAG, "PIN Created!");
                    launchSettingsActivity();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                showCreatePinDialog(prefs);
                Log.w(TAG, "User must create a PIN to continue.");
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    // Accesses the secure, on-device storage for the PIN
    private SharedPreferences getEncryptedPrefs() {
        try {
            String mainKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            return EncryptedSharedPreferences.create(
                    "parent_secure_prefs", mainKeyAlias, this, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create encrypted preferences", e);
            return null;
        }
    }

    // Starts the SettingsActivity
    private void launchSettingsActivity() {
        Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
        startActivity(intent);
    }
}