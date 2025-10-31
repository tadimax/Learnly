package com.example.learnly;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
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

// Manages the Parental Controls screen for mini-apps and difficulty
public class ParentalActivity extends AppCompatActivity {

    // A tag for identifying log messages from this file
    private static final String TAG = "ParentalActivity";

    // Firebase variables
    private DatabaseReference mUserNodeRef; // Reference to the entire user's node
    private DatabaseReference mUserMiniAppsRef; // Reference to the /miniApps sub-node
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // UI element variables
    private SwitchMaterial switchStoryTime, switchSpellingTime, switchMemoryMatch, switchColourPatterns, switchNumberFun, switchWeeklyQuiz;
    private Spinner spinnerStoryTime, spinnerSpellingTime, spinnerMemoryMatch, spinnerColourPatterns, spinnerNumberFun, spinnerWeeklyQuiz;
    private EditText editTextParentalEmail;
    private Button btnSaveParental;

    // Difficulty levels for the spinners
    private final String[] difficultyLevels = {"Easy", "Medium", "Hard"};

    // Initializes the activity and links the layout file
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parental);
        Log.d(TAG, "ParentalActivity created.");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "User is null, cannot load settings.");
            finish(); // Close activity if no user is logged in
            return;
        }

        // Get database references
        String userId = currentUser.getUid();
        mUserNodeRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        mUserMiniAppsRef = mUserNodeRef.child("miniApps");

        // Link all UI elements
        initializeUI();

        // Set up all 6 spinners
        setupSpinners();

        // Load the settings from Firebase
        loadParentalSettings();

        // Set listener for the save button
        btnSaveParental.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveParentalSettings();
            }
        });
    }

    // Links all 14 UI elements from the XML to the Java variables
    private void initializeUI() {
        btnSaveParental = findViewById(R.id.btnSaveParental);
        editTextParentalEmail = findViewById(R.id.editTextParentalEmail);

        switchStoryTime = findViewById(R.id.switchStoryTime);
        switchSpellingTime = findViewById(R.id.switchSpellingTime);
        switchMemoryMatch = findViewById(R.id.switchMemoryMatch);
        switchColourPatterns = findViewById(R.id.switchColourPatterns);
        switchNumberFun = findViewById(R.id.switchNumberFun);
        switchWeeklyQuiz = findViewById(R.id.switchWeeklyQuiz);

        spinnerStoryTime = findViewById(R.id.spinnerStoryTime);
        spinnerSpellingTime = findViewById(R.id.spinnerSpellingTime);
        spinnerMemoryMatch = findViewById(R.id.spinnerMemoryMatch);
        spinnerColourPatterns = findViewById(R.id.spinnerColourPatterns);
        spinnerNumberFun = findViewById(R.id.spinnerNumberFun);
        spinnerWeeklyQuiz = findViewById(R.id.spinnerWeeklyQuiz);
    }

    // Creates and applies the adapter for all 6 spinners
    private void setupSpinners() {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, difficultyLevels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerStoryTime.setAdapter(spinnerAdapter);
        spinnerSpellingTime.setAdapter(spinnerAdapter);
        spinnerMemoryMatch.setAdapter(spinnerAdapter);
        spinnerColourPatterns.setAdapter(spinnerAdapter);
        spinnerNumberFun.setAdapter(spinnerAdapter);
        spinnerWeeklyQuiz.setAdapter(spinnerAdapter);
    }

    // Loads all settings from the user's node in Firebase
    private void loadParentalSettings() {
        mUserNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // 1. Load the email
                if (snapshot.hasChild("parentEmail")) {
                    String email = snapshot.child("parentEmail").getValue(String.class);
                    editTextParentalEmail.setText(email);
                } else {
                    // Pre-fill with their auth email if not set
                    editTextParentalEmail.setText(currentUser.getEmail());
                }

                // 2. Load the mini-app settings
                DataSnapshot appsSnapshot = snapshot.child("miniApps");
                loadAppSetting(appsSnapshot, "Story Time", switchStoryTime, spinnerStoryTime);
                loadAppSetting(appsSnapshot, "Spelling Time", switchSpellingTime, spinnerSpellingTime);
                loadAppSetting(appsSnapshot, "Memory Match", switchMemoryMatch, spinnerMemoryMatch);
                loadAppSetting(appsSnapshot, "Colour Patterns", switchColourPatterns, spinnerColourPatterns);
                loadAppSetting(appsSnapshot, "Number Fun", switchNumberFun, spinnerNumberFun);
                loadAppSetting(appsSnapshot, "Weekly Quiz", switchWeeklyQuiz, spinnerWeeklyQuiz);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load parental settings.", error.toException());
            }
        });
    }

    // Helper function to load the settings for a single app
    private void loadAppSetting(DataSnapshot appsSnapshot, String appName, SwitchMaterial appSwitch, Spinner appSpinner) {
        if (appsSnapshot.hasChild(appName)) {
            // Read "enabled" flag, default to true if not found
            Boolean isEnabled = appsSnapshot.child(appName).child("enabled").getValue(Boolean.class);
            appSwitch.setChecked(isEnabled != null && isEnabled);

            // Read "difficulty" string, default to "Easy"
            String difficulty = appsSnapshot.child(appName).child("difficulty").getValue(String.class);
            appSpinner.setSelection(getDifficultyPosition(difficulty));

        } else {
            // Default settings if app node doesn't exist
            appSwitch.setChecked(true);
            appSpinner.setSelection(0); // "Easy"
        }
    }

    // Saves all settings back to the Firebase database
    private void saveParentalSettings() {
        // 1. Save the email
        String email = editTextParentalEmail.getText().toString().trim();
        mUserNodeRef.child("parentEmail").setValue(email);

        // 2. Save all the mini-app settings
        Map<String, Object> miniAppsMap = new HashMap<>();
        miniAppsMap.put("Story Time", createAppMapFromUI(switchStoryTime, spinnerStoryTime));
        miniAppsMap.put("Spelling Time", createAppMapFromUI(switchSpellingTime, spinnerSpellingTime));
        miniAppsMap.put("Memory Match", createAppMapFromUI(switchMemoryMatch, spinnerMemoryMatch));
        miniAppsMap.put("Colour Patterns", createAppMapFromUI(switchColourPatterns, spinnerColourPatterns));
        miniAppsMap.put("Number Fun", createAppMapFromUI(switchNumberFun, spinnerNumberFun));
        miniAppsMap.put("Weekly Quiz", createAppMapFromUI(switchWeeklyQuiz, spinnerWeeklyQuiz));

        mUserMiniAppsRef.setValue(miniAppsMap)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Parental controls saved successfully.");
                        finish(); // Close the activity after saving
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Failed to save parental controls.", e);
                    }
                });
    }

    // Helper function to create a Map object from the UI elements
    private Map<String, Object> createAppMapFromUI(SwitchMaterial appSwitch, Spinner appSpinner) {
        Map<String, Object> appSettings = new HashMap<>();
        appSettings.put("enabled", appSwitch.isChecked());
        appSettings.put("difficulty", appSpinner.getSelectedItem().toString());
        return appSettings;
    }

    // Helper function to find the spinner index (0, 1, or 2) from a difficulty string
    private int getDifficultyPosition(String difficulty) {
        if (difficulty == null) return 0;
        switch (difficulty) {
            case "Medium":
                return 1;
            case "Hard":
                return 2;
            case "Easy":
            default:
                return 0;
        }
    }
}