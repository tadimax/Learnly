/*

TODO: THIS FILE WILL BE REPLACED WITH KOTLIN AS AN ATTEMPT TO WORK ON LEARNING KOTLIN

 */



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

public class ParentalActivity extends AppCompatActivity {
    private static final String TAG = "ParentalActivity";
    private DatabaseReference user;
    private DatabaseReference db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private SwitchMaterial switchStoryTime, switchSpellingTime, switchMemoryMatch, switchColourPatterns, switchNumberFun, switchWeeklyQuiz;
    private Spinner spinnerStoryTime, spinnerSpellingTime, spinnerMemoryMatch, spinnerColourPatterns, spinnerNumberFun, spinnerWeeklyQuiz;
    private EditText editTextParentalEmail;
    private Button btnSaveParental;
    private final String[] difficultyLevels = {"Easy", "Medium", "Hard"};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parental);
        Log.d(TAG, "ParentalActivity created.");

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "User is null, cannot load settings.");
            finish();
            return;
        }


        String userId = currentUser.getUid();
        user = FirebaseDatabase.getInstance().getReference("users").child(userId);
        db = user.child("miniApps");

        initializeUI();

        setupSpinners();

        loadParentalSettings();

        btnSaveParental.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveParentalSettings();
            }
        });
    }

    //Just initializes all XML elements to Java variables
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

    // Makes the spinner adapter and applies it to all subapplications
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

    // Loads the user settings from Firebase
    private void loadParentalSettings() {
        user.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild("parentEmail")) {
                    String email = snapshot.child("parentEmail").getValue(String.class);
                    editTextParentalEmail.setText(email);
                } else {
                    editTextParentalEmail.setText(currentUser.getEmail());
                }

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

            Boolean isEnabled = appsSnapshot.child(appName).child("enabled").getValue(Boolean.class);
            appSwitch.setChecked(isEnabled != null && isEnabled);

            String difficulty = appsSnapshot.child(appName).child("difficulty").getValue(String.class);
            appSpinner.setSelection(getDifficultyPosition(difficulty));

        } else {
            appSwitch.setChecked(true);
            appSpinner.setSelection(0); // "Easy"
        }
    }

    // Saves all settings to the Firebase db
    private void saveParentalSettings() {
        String email = editTextParentalEmail.getText().toString().trim();
        user.child("parentEmail").setValue(email);

        Map<String, Object> miniAppsMap = new HashMap<>();
        miniAppsMap.put("Story Time", createAppMapFromUI(switchStoryTime, spinnerStoryTime));
        miniAppsMap.put("Spelling Time", createAppMapFromUI(switchSpellingTime, spinnerSpellingTime));
        miniAppsMap.put("Memory Match", createAppMapFromUI(switchMemoryMatch, spinnerMemoryMatch));
        miniAppsMap.put("Colour Patterns", createAppMapFromUI(switchColourPatterns, spinnerColourPatterns));
        miniAppsMap.put("Number Fun", createAppMapFromUI(switchNumberFun, spinnerNumberFun));
        miniAppsMap.put("Weekly Quiz", createAppMapFromUI(switchWeeklyQuiz, spinnerWeeklyQuiz));

        user.setValue(miniAppsMap)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Parental controls saved successfully.");
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Failed to save parental controls.", e);
                    }
                });
    }

    private Map<String, Object> createAppMapFromUI(SwitchMaterial appSwitch, Spinner appSpinner) {
        Map<String, Object> appSettings = new HashMap<>();
        appSettings.put("enabled", appSwitch.isChecked());
        appSettings.put("difficulty", appSpinner.getSelectedItem().toString());
        return appSettings;
    }

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