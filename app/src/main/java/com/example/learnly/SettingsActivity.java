package com.example.learnly;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.content.Intent;
import android.widget.EditText;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private FirebaseAuth mAuth;
    private DatabaseReference db;
    private FirebaseUser currentUser;
    private TextView settingsTextView;
    private EditText editTextChildName;
    private SwitchMaterial switchWeeklyReport;
    private Button btnManageApps;
    private Button btnChangePin;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        settingsTextView = findViewById(R.id.settingsTextView);
        editTextChildName = findViewById(R.id.editTextChildName);
        switchWeeklyReport = findViewById(R.id.switchWeeklyReport);
        btnManageApps = findViewById(R.id.btnManageApps);
        btnChangePin = findViewById(R.id.btnChangePin);
        btnSave = findViewById(R.id.btnSave);

        if (currentUser != null) {
            Log.d(TAG, "User logged in: " + currentUser.getEmail());
            settingsTextView.setText(currentUser.getEmail() + " Settings");

            String userId = currentUser.getUid();
            db = FirebaseDatabase.getInstance().getReference("users").child(userId);

            // loadUserSettings(); // <-- REMOVED FROM HERE

        } else {
            Log.d(TAG, "User not logged in, redirecting to MainActivity.");
            Intent i = new Intent(SettingsActivity.this, MainActivity.class);
            startActivity(i);
            finish();
        }

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserSettings();
            }
        });

        btnManageApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Opening ParentalActivity...");
                // Launch the new ParentalActivity
                Intent i = new Intent(SettingsActivity.this, ParentalActivity.class);
                startActivity(i);
            }
        });

        btnChangePin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEnterOldPinDialog();
            }
        });
    }

    // Refreshes data every time the activity is shown
    @Override
    protected void onResume() {
        super.onResume();
        // Load or refresh settings every time the screen becomes visible
        if (currentUser != null) {
            loadUserSettings();
        }
    }

    // Gets user data from Firebase
    private void loadUserSettings() {
        db.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    if (snapshot.hasChild("childName")) {
                        String childName = snapshot.child("childName").getValue(String.class);
                        editTextChildName.setText(childName);
                    }
                    if (snapshot.hasChild("weeklyReportEnabled")) {
                        Boolean isEnabled = snapshot.child("weeklyReportEnabled").getValue(Boolean.class);
                        switchWeeklyReport.setChecked(isEnabled != null && isEnabled);
                    }
                } else {
                    Log.d(TAG, "No user settings found in database.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to load user settings.", error.toException());
            }
        });
    }

    // Saves the current settings to RealTime Firebase DB
    private void saveUserSettings() {
        String childName = editTextChildName.getText().toString().trim();
        boolean weeklyReportEnabled = switchWeeklyReport.isChecked();

        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("childName", childName);
        userUpdates.put("weeklyReportEnabled", weeklyReportEnabled);

        if (currentUser.getEmail() != null) {
            userUpdates.put("parentEmail", currentUser.getEmail());
        }

        db.updateChildren(userUpdates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "User settings saved to database.");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Failed to save user settings.", e);
                    }
                });
    }

    // Signs the user out of Firebase Auth and returns to the main screen
    public void logout(View view) {
        mAuth.signOut();
        Log.d(TAG, "user:logged out");
        Intent i = new Intent(SettingsActivity.this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // Sends a password reset email using Firebase Auth
// Signs the user out and sends them to the ForgotPasswordActivity
    public void changePassword(View view) {
        mAuth.signOut();
        Log.d(TAG, "User signed out, redirecting to ForgotPasswordActivity.");
        Intent i = new Intent(SettingsActivity.this, ForgotPasswordActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // Displays a dialog to verify the user's current (old) PIN
    private void showEnterOldPinDialog() {
        final SharedPreferences prefs = getEncryptedPrefs();
        if (prefs == null) {
            Log.e(TAG, "Security error. Cannot change PIN.");
            return;
        }

        final String correctPin = prefs.getString("parent_pin", null);
        if (correctPin == null) {
            Log.e(TAG, "Cannot change PIN, no PIN is currently set.");
            showCreateNewPinDialog(prefs);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Old PIN");
        builder.setMessage("Please enter your current 4-digit PIN to make changes.");

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
                    showCreateNewPinDialog(prefs);
                } else {
                    Log.w(TAG, "Incorrect PIN entered for PIN change.");
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

    // Displays a dialog to create and confirm a new PIN
    private void showCreateNewPinDialog(final SharedPreferences prefs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create a New 4-Digit PIN");
        builder.setMessage("Please enter and confirm your new PIN.");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 8, 48, 8);

        final EditText inputPin = new EditText(this);
        inputPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        inputPin.setHint("Enter new 4-digit PIN");
        layout.addView(inputPin);

        final EditText confirmPin = new EditText(this);
        confirmPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirmPin.setHint("Confirm new PIN");
        layout.addView(confirmPin);

        builder.setView(layout);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String pin1 = inputPin.getText().toString();
                String pin2 = confirmPin.getText().toString();

                if (pin1.length() != 4) {
                    Log.w(TAG, "New PIN is not 4 digits.");
                    showCreateNewPinDialog(prefs);
                } else if (!pin1.equals(pin2)) {
                    Log.w(TAG, "New PINs do not match.");
                    showCreateNewPinDialog(prefs);
                } else {
                    // This is the part that was missing
                    prefs.edit().putString("parent_pin", pin1).apply();
                    Log.d(TAG, "PIN successfully changed.");
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
}