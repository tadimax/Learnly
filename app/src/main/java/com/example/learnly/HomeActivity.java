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
    private Button settingsButton, storyButton;

    // Initializes the activity, links UI elements, and loads user data
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
            welcomeTextView.setText("Hello");

            String userId = user.getUid();
            mUserDatabaseRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

            loadUserSettings();

        } else {
            Intent i = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(i);
            finish();
        }

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
    }

    // Fetches user settings from Firebase and updates the welcome text
    private void loadUserSettings() {
        mUserDatabaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChild("childName")) {
                    String childName = snapshot.child("childName").getValue(String.class);

                    if (childName != null && !childName.trim().isEmpty()) {
                        welcomeTextView.setText("Hello, " + childName);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to load user settings.", error.toException());
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