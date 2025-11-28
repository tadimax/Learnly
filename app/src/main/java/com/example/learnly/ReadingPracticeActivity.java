package com.example.learnly;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class ReadingPracticeActivity extends AppCompatActivity {

    private static final String TAG = "ReadingPracticeActivity";
    private static final String APP_NAME = "ReadingPractice"; // users/<uid>/miniApps/ReadingPractice

    private static final int REQ_SPEECH = 1001;
    private static final int REQ_MIC_PERMISSION = 2001;

    // UI
    private TextView levelText, storyTitleText, statusText;
    private TextView emojiHint, wordText, hintText, phonicsLabel, readPromptText, feedbackText;
    private Button btnBackToHome, btnHint, btnHearWord, btnRecord, btnNext;
    private LinearLayout letterRow;

    // TTS
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // Firebase
    private DatabaseReference mAppSettingsRef;

    // Difficulty / word pools
    private final Random rng = new Random();
    private int tierIndex = 0; // 0=easy, 1=medium, 2=hard

    // Current word data
    private String currentWord = "CAT";
    private String currentHint = "A small pet that says meow.";
    private String currentEmoji = "🐱";

    // Last heard phrase (for debugging / display)
    private String lastHeardRaw = "";

    // Word pools: {WORD, EMOJI, HINT}
    private static final String[][] TIER_EASY = new String[][]{
            {"CAT", "🐱", "A small pet that says meow."},
            {"DOG", "🐶", "A friendly pet that barks."},
            {"SUN", "☀️", "It shines bright in the sky."},
            {"CAR", "🚗", "You can ride in it."},
            {"BUS", "🚌", "It carries many people."},
            {"FOX", "🦊", "A clever orange animal."},
            {"PIG", "🐷", "A pink farm animal."},
            {"ANT", "🐜", "A tiny insect."},
            {"BEE", "🐝", "A yellow insect that makes honey."},
            {"BED", "🛏️", "You sleep on it."}
    };

    private static final String[][] TIER_MEDIUM = new String[][]{
            {"FROG", "🐸", "It hops and says ribbit."},
            {"BIRD", "🐦", "It flies in the sky."},
            {"LION", "🦁", "The king of the jungle."},
            {"FISH", "🐟", "It swims in the water."},
            {"MOON", "🌙", "It glows in the night sky."},
            {"SHIP", "🚢", "A big boat on water."},
            {"TREE", "🌳", "A tall plant with leaves."},
            {"CAKE", "🎂", "A sweet birthday treat."},
            {"BALL", "⚽", "You can kick or throw it."},
            {"ROAD", "🛣️", "Cars drive on it."}
    };

    private static final String[][] TIER_HARD = new String[][]{
            {"APPLE", "🍎", "A red or green fruit."},
            {"TRAIN", "🚆", "A long vehicle that runs on tracks."},
            {"SMILE", "😊", "What you do when you feel happy."},
            {"HOUSE", "🏠", "A place where families live."},
            {"PLANT", "🪴", "It grows in soil with sun and water."},
            {"SHEEP", "🐑", "A soft white farm animal."},
            {"HORSE", "🐴", "A big animal you can ride."},
            {"CLOUD", "☁️", "Fluffy shapes in the sky."},
            {"HEART", "❤️", "It beats inside your body."},
            {"TIGER", "🐯", "A big cat with stripes."}
    };

    private static final String[][][] TIERS = new String[][][]{
            TIER_EASY,
            TIER_MEDIUM,
            TIER_HARD
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading_practice);

        // Bind views
        btnBackToHome  = findViewById(R.id.btnBackToHome);
        levelText      = findViewById(R.id.levelText);
        storyTitleText = findViewById(R.id.storyTitleText);
        statusText     = findViewById(R.id.statusText);

        emojiHint   = findViewById(R.id.emojiHint);
        wordText    = findViewById(R.id.wordText);
        hintText    = findViewById(R.id.hintText);
        phonicsLabel= findViewById(R.id.phonicsLabel);
        readPromptText = findViewById(R.id.readPromptText);
        feedbackText   = findViewById(R.id.feedbackText);

        btnHint     = findViewById(R.id.btnHint);
        btnHearWord = findViewById(R.id.btnHearWord);
        btnRecord   = findViewById(R.id.btnRecord);
        btnNext     = findViewById(R.id.btnNext);
        letterRow   = findViewById(R.id.letterRow);

        storyTitleText.setText("Learn to Read");

        // Home nav
        btnBackToHome.setOnClickListener(v -> {
            Intent i = new Intent(ReadingPracticeActivity.this, HomeActivity.class);
            startActivity(i);
            finish();
        });

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.US);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS language not available.", Toast.LENGTH_SHORT).show();
                } else {
                    ttsReady = true;
                    statusText.setText("TTS ready. Tap a letter to hear its sound!");
                }
            } else {
                Toast.makeText(this, "Failed to initialize TTS.", Toast.LENGTH_SHORT).show();
                statusText.setText("TTS init failed.");
            }
        });

        // Button handlers
        btnHint.setOnClickListener(v -> speakHint());
        btnHearWord.setOnClickListener(v -> speakWholeWord());
        btnRecord.setOnClickListener(v -> startSpeechRecognitionWithPermissionCheck());
        btnNext.setOnClickListener(v -> loadRandomWordFromTier());

        // --- Firebase difficulty / enabled ---
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_LONG).show();
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
            public void onDataChange(DataSnapshot snapshot) {
                String difficulty = "Easy";
                boolean isEnabled = true;

                if (snapshot.exists()) {
                    Boolean enabledFromDB = snapshot.child("enabled").getValue(Boolean.class);
                    String difficultyFromDB = snapshot.child("difficulty").getValue(String.class);

                    if (enabledFromDB != null) isEnabled = enabledFromDB;
                    if (difficultyFromDB != null) difficulty = difficultyFromDB;
                }

                if (!isEnabled) {
                    Toast.makeText(ReadingPracticeActivity.this,
                            "This app is disabled by your parent.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                mapDifficultyToTier(difficulty);
                loadRandomWordFromTier();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(ReadingPracticeActivity.this,
                        "Failed to load settings. Using Easy mode.", Toast.LENGTH_LONG).show();
                mapDifficultyToTier("Easy");
                loadRandomWordFromTier();
            }
        });
    }

    private void mapDifficultyToTier(String difficulty) {
        switch (difficulty) {
            case "Medium":
                tierIndex = 1;
                levelText.setText("Medium");
                break;
            case "Hard":
                tierIndex = 2;
                levelText.setText("Hard");
                break;
            case "Easy":
            default:
                tierIndex = 0;
                levelText.setText("Easy");
                break;
        }
    }

    // ---------- Word loading & UI ----------

    private void loadRandomWordFromTier() {
        if (tierIndex < 0 || tierIndex >= TIERS.length) {
            tierIndex = 0;
        }
        String[][] pool = TIERS[tierIndex];
        if (pool == null || pool.length == 0) {
            Toast.makeText(this, "No words configured.", Toast.LENGTH_SHORT).show();
            return;
        }

        int idx = rng.nextInt(pool.length);
        String[] entry = pool[idx];

        currentWord  = entry[0].toUpperCase(Locale.US);
        currentEmoji = entry[1];
        currentHint  = entry[2];

        emojiHint.setText(currentEmoji);
        wordText.setText(currentWord);
        hintText.setText(currentHint);
        feedbackText.setText("");
        readPromptText.setText("Try reading this word out loud:");
        statusText.setText("Tap each letter to hear the sounds.");

        buildLetterRow();

        // Speak a friendly prompt
        speakText("Can you read this word? " + spellOutWord(currentWord));
    }

    private void buildLetterRow() {
        letterRow.removeAllViews();
        char[] letters = currentWord.toCharArray();
        for (char c : letters) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(String.valueOf(c));
            b.setTextSize(24f);
            b.setPadding(dp(8), dp(8), dp(8), dp(8));
            b.setBackgroundTintList(getColorStateListCompat("#FFCC80"));
            b.setTextColor(getResources().getColor(android.R.color.white));

            final char letter = c;
            b.setOnClickListener(v -> speakPhonics(letter));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(dp(4), 0, dp(4), 0);
            b.setLayoutParams(lp);

            letterRow.addView(b);
        }
    }

    // ---------- TTS helpers ----------

    private void speakHint() {
        if (!ttsReady) return;
        String msg = currentHint + " This word is " + spellOutWord(currentWord) + ". Can you say it?";
        speakText(msg);
    }

    private void speakWholeWord() {
        if (!ttsReady) return;
        speakText("The word is " + currentWord);
    }

    private void speakPhonics(char c) {
        if (!ttsReady) return;
        String sound = phonicsForLetter(c);
        if (TextUtils.isEmpty(sound)) {
            sound = String.valueOf(c);
        }
        speakText(sound);
    }

    private void speakText(String text) {
        if (!ttsReady || TextUtils.isEmpty(text)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                    "utt-" + System.currentTimeMillis());
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utt");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    private String spellOutWord(String word) {
        if (TextUtils.isEmpty(word)) return "";
        StringBuilder sb = new StringBuilder();
        char[] arr = word.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) {
                sb.append(" - ");
            }
        }
        return sb.toString();
    }

    private String phonicsForLetter(char c) {
        char up = Character.toUpperCase(c);
        switch (up) {
            case 'A': return "a, like in apple";
            case 'B': return "b, b, b";
            case 'C': return "k, like in cat";
            case 'D': return "d, d, d";
            case 'E': return "eh, like in bed";
            case 'F': return "fff";
            case 'G': return "g, g, g";
            case 'H': return "h, h, h";
            case 'I': return "ih, like in sit";
            case 'J': return "j, j, j";
            case 'K': return "k, k, k";
            case 'L': return "l, l, l";
            case 'M': return "mmm";
            case 'N': return "nnn";
            case 'O': return "o, like in dog";
            case 'P': return "p, p, p";
            case 'Q': return "kw, like in queen";
            case 'R': return "rrr";
            case 'S': return "sss";
            case 'T': return "t, t, t";
            case 'U': return "uh, like in sun";
            case 'V': return "vvv";
            case 'W': return "w, w, w";
            case 'X': return "ks, like in fox";
            case 'Y': return "y, like in yes";
            case 'Z': return "zzz";
        }
        return null;
    }

    // ---------- Speech recognition + permission ----------

    private void startSpeechRecognitionWithPermissionCheck() {
        // Runtime mic permission
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_MIC_PERMISSION
            );
        } else {
            actuallyStartSpeechRecognition();
        }
    }

    private void actuallyStartSpeechRecognition() {
        statusText.setText("Listening… Say the word " + currentWord);
        feedbackText.setText("");

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the word " + currentWord);

        try {
            startActivityForResult(intent, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Speech recognition not available.", Toast.LENGTH_LONG).show();
            statusText.setText("Speech recognition not available on this device.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted → now actually start listening
                Toast.makeText(this, "Mic permission granted. Listening…", Toast.LENGTH_SHORT).show();
                actuallyStartSpeechRecognition();
            } else {
                Toast.makeText(this,
                        "Microphone permission is needed to practice reading.",
                        Toast.LENGTH_LONG).show();
                statusText.setText("Mic permission denied.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (results == null || results.isEmpty()) {
                    statusText.setText("I couldn't hear that.");
                    feedbackText.setText("I couldn't hear that. Let's try again!");
                    return;
                }
                lastHeardRaw = results.get(0);
                statusText.setText("Heard: " + lastHeardRaw);
                handleSpeechResult(lastHeardRaw);
            } else {
                statusText.setText("No speech result.");
            }
        }
    }

    private void handleSpeechResult(String heardRaw) {
        if (heardRaw == null) {
            feedbackText.setText("I couldn't hear that. Let's try again!");
            return;
        }

        String target = currentWord.toLowerCase(Locale.US);
        String heard  = heardRaw.trim().toLowerCase(Locale.US);

        if (TextUtils.isEmpty(heard)) {
            feedbackText.setText("I couldn't hear that. Let's try again!");
            return;
        }

        // Use just the first word from recognition
        String[] pieces = heard.split("\\s+");
        String firstWord = pieces[0];

        // Compute similarity score (0-100)
        int score = computeSimilarityScore(target, firstWord);

        StringBuilder sb = new StringBuilder();
        sb.append("I heard: \"").append(heardRaw).append("\"\n");
        sb.append("Score: ").append(score).append("%\n");

        if (firstWord.equals(target)) {
            sb.append("⭐ Amazing! That sounded just like ").append(currentWord).append("!");
            speakText("Great reading! That sounded like " + currentWord + "!");
        } else if (score >= 70) {
            sb.append("👍 Almost! You're very close to ").append(currentWord).append(".");
            speakText("Nice try! That was close to " + currentWord + ". Let's try again!");
        } else {
            sb.append("Not quite. The word is ").append(currentWord).append(". Let's try again!");
            speakText("Let's try again. The word is " + currentWord + ".");
        }

        feedbackText.setText(sb.toString());
    }

    // ---------- Simple similarity scoring (Levenshtein) ----------

    private int computeSimilarityScore(String target, String heard) {
        if (TextUtils.isEmpty(target) || TextUtils.isEmpty(heard)) {
            return 0;
        }
        int dist = levenshteinDistance(target, heard);
        int maxLen = Math.max(target.length(), heard.length());
        if (maxLen == 0) return 100;
        double similarity = 1.0 - ((double) dist / (double) maxLen);
        int score = (int) Math.round(similarity * 100.0);
        if (score < 0) score = 0;
        if (score > 100) score = 100;
        return score;
    }

    private int levenshteinDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[][] dp = new int[n + 1][m + 1];

        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;

        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = (ca == cb) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[n][m];
    }

    // ---------- Utils ----------

    private int dp(int d) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(d * density);
    }

    private android.content.res.ColorStateList getColorStateListCompat(String hexColor) {
        int color = android.graphics.Color.parseColor(hexColor);
        return android.content.res.ColorStateList.valueOf(color);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
