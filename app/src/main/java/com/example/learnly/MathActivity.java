package com.example.learnly;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;


public class MathActivity extends AppCompatActivity {

    private static final String TAG = "MathActivity";
    private static final String APP_NAME = "Number Fun";
    private DatabaseReference mAppSettingsRef;

    private TextView textViewMathQuestion, textViewMathFeedback, textViewFingerTrick;
    private EditText editTextMathAnswer;
    private Button btnSubmitAnswer, btnNextQuestion, btnBackToHub; // Added btnBackToHub

    private List<MathQuestion> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private MathQuestion currentQuestion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_math);

        // Checks for parental controls and sets the difficulty to give correct questions
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No user logged in, closing activity.");
            finish();
            return;
        }
        String userId = user.getUid();
        mAppSettingsRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userId)
                .child("miniApps")
                .child(APP_NAME);

        mAppSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String difficulty = "Easy";
                boolean isEnabled = true;

                if (snapshot.exists()) {
                    Boolean enabledFromDB = snapshot.child("enabled").getValue(Boolean.class);
                    String difficultyFromDB = snapshot.child("difficulty").getValue(String.class);

                    if (enabledFromDB != null) { isEnabled = enabledFromDB; }
                    if (difficultyFromDB != null) { difficulty = difficultyFromDB; }
                }

                if (isEnabled) {
                    Log.d(TAG, "Starting " + APP_NAME + " with difficulty: " + difficulty);
                    initializeApp(difficulty);
                } else {
                    Log.w(TAG, APP_NAME + " is disabled by parent.");
                    Toast.makeText(MathActivity.this, "This app is disabled by your parent.", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load app settings, using default.", error.toException());
                initializeApp("Easy"); // Fail-safe: load easy mode
            }
        });

    }

    // Sets up the UI and game logic after settings are loaded
    private void initializeApp(String difficulty) {
        // Link UI elements
        textViewMathQuestion = findViewById(R.id.textViewMathQuestion);
        textViewMathFeedback = findViewById(R.id.textViewMathFeedback);
        textViewFingerTrick = findViewById(R.id.textViewFingerTrick);
        editTextMathAnswer = findViewById(R.id.editTextMathAnswer);
        btnSubmitAnswer = findViewById(R.id.btnSubmitAnswer);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        btnBackToHub = findViewById(R.id.btnBackToHub); // Added this line

        // Load the questions based on difficulty
        loadQuestions(difficulty);

        // Display the first question
        displayQuestion();

        // Set listeners
        btnSubmitAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAnswer();
            }
        });

        btnNextQuestion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToNextQuestion();
            }
        });

        // Added listener for the back button
        btnBackToHub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // finish() closes this activity and returns to HomeActivity
                finish();
            }
        });
    }

    // Loads the specific questions depending on the difficulty that was passed through, plus a hint, and the answer
    private void loadQuestions(String difficulty) {
        questions.clear();
        switch (difficulty) {
            case "Hard":
                questions.add(new MathQuestion("7 x 6 =", 42, "Try 7 x 5 first, then add 7 more."));
                questions.add(new MathQuestion("45 / 9 =", 5, "How many times does 9 go into 45?"));
                questions.add(new MathQuestion("12 x 11 =", 132, "For 11s, split the '12' -> 1__2 and add 1+2 in the middle!"));
                questions.add(new MathQuestion("8 x 8 =", 64, "This is a square number!"));
                questions.add(new MathQuestion("100 / 4 =", 25, "Think of 4 quarters in a dollar."));
                break;
            case "Medium":
                questions.add(new MathQuestion("15 + 8 =", 23, "You can do 15 + 10, then take away 2."));
                questions.add(new MathQuestion("30 - 12 =", 18, "Try 30 - 10 first, then take away 2 more."));
                questions.add(new MathQuestion("5 x 3 =", 15, "Count by 5s three times: 5, 10, 15."));
                questions.add(new MathQuestion("10 + 17 =", 27, "Add the tens (10+10), then add the ones (7)."));
                questions.add(new MathQuestion("25 - 9 =", 16, "25 - 10 is 15. Since you only took 9, add 1 back."));
                break;
            case "Easy":
            default:
                questions.add(new MathQuestion("2 + 2 =", 4, "Hold up 2 fingers on each hand. Count them!"));
                questions.add(new MathQuestion("5 - 1 =", 4, "Hold up 5 fingers. Put one down."));
                questions.add(new MathQuestion("3 + 4 =", 7, "Start with 4, and count up 3 more: 5, 6, 7."));
                questions.add(new MathQuestion("8 - 3 =", 5, "Start at 8 and count back 3: 7, 6, 5."));
                questions.add(new MathQuestion("1 + 2 =", 3, "Hold up 1 finger, then 2 more."));
                break;
        }
    }

    // Shows the current question on the screen
    private void displayQuestion() {
        if (currentQuestionIndex < questions.size()) {
            currentQuestion = questions.get(currentQuestionIndex);

            textViewMathQuestion.setText(currentQuestion.getQuestionText());
            textViewFingerTrick.setText(currentQuestion.getFingerTrick());

            // Reset UI for the new question
            textViewMathFeedback.setText("Good Luck!");
            editTextMathAnswer.setText("");
            btnSubmitAnswer.setVisibility(View.VISIBLE);
            btnNextQuestion.setVisibility(View.GONE);
        } else {
            // No more questions
            textViewMathQuestion.setText("You're done!");
            editTextMathAnswer.setVisibility(View.GONE);
            btnSubmitAnswer.setVisibility(View.GONE);
            btnNextQuestion.setVisibility(View.GONE);
            textViewFingerTrick.setVisibility(View.GONE);
            textViewMathFeedback.setText("Great job! Quiz complete!");
        }
    }

    // Checks the user's answer
    private void checkAnswer() {
        String answerString = editTextMathAnswer.getText().toString();

        if (answerString.isEmpty()) {
            textViewMathFeedback.setText("Please type an answer.");
            return;
        }

        int userAnswer = Integer.parseInt(answerString);

        if (userAnswer == currentQuestion.getAnswer()) {
            textViewMathFeedback.setText("Correct! Great job!");
            btnSubmitAnswer.setVisibility(View.GONE);
            btnNextQuestion.setVisibility(View.VISIBLE);
        } else {
            textViewMathFeedback.setText("Not quite. Try again! Or check the hint.");
        }
    }

    // Moves to the next question
    private void goToNextQuestion() {
        currentQuestionIndex++;
        displayQuestion();
    }
}