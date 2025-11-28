package com.example.learnly;

import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class SpellingGameActivity extends AppCompatActivity {

    private static final String TAG = "SpellingGameActivity";
    private static final String APP_NAME = "Spelling Time";

    // UI
    private TextView levelText, promptText, resultText, emojiHint;
    private LinearLayout answerRow;
    private GridLayout lettersGrid;
    private Button btnBackspace, btnHint, btnReset, btnNext;

    // Game state
    // tierIndex: 0 = easy (3 letters), 1 = medium (4 letters), 2 = hard (5 letters)
    private int tierIndex = 0;
    private String difficultyLabel = "Easy";

    private String currentWord = "";
    private ArrayList<Character> bank;
    private ArrayList<Character> slots;

    // TTS
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // Firebase
    private DatabaseReference mAppSettingsRef;

    private final Random rng = new Random();

    // ---------------- Word pools (30 per tier) ----------------
    // Tier 0: EASY (3-letter words)
    private static final String[][] LEVEL_TIER_1 = new String[][]{
            {"CAT", "A small pet that says meow."},
            {"DOG", "A friendly pet that barks."},
            {"SUN", "It shines bright in the sky."},
            {"CAR", "You can ride in it."},
            {"BUS", "It carries many people."},
            {"ANT", "A tiny insect that crawls."},
            {"HAT", "You wear it on your head."},
            {"PEN", "You use it to write."},
            {"MAP", "It shows you where things are."},
            {"CUP", "You drink from it."},
            {"BOX", "You can put things inside it."},
            {"BED", "You sleep on it."},
            {"PIG", "A pink farm animal that says oink."},
            {"RUG", "A soft mat on the floor."},
            {"BAT", "It flies and comes out at night."},
            {"FOX", "A clever orange animal."},
            {"JAR", "You can keep snacks inside it."},
            {"NUT", "A small food squirrels love."},
            {"LOG", "A big piece of a tree."},
            {"EGG", "A baby chick comes from this."},
            {"BUG", "A tiny creature that crawls or flies."},
            {"BEE", "A yellow insect that makes honey."},
            {"RAT", "A small furry animal with a tail."},
            {"OWL", "A night bird that says hoo."},
            {"TOY", "Something fun to play with."},
            {"KEY", "You use it to unlock things."},
            {"LIP", "Part of your mouth."},
            {"EAR", "You use it to hear."},
            {"BOW", "A pretty ribbon tie."},
            {"ICE", "Cold water that is frozen."}
    };

    // Tier 1: MEDIUM (4-letter words)
    private static final String[][] LEVEL_TIER_2 = new String[][]{
            {"FROG",  "It hops and says ribbit."},
            {"BIRD",  "It flies in the sky."},
            {"LION",  "The king of the jungle."},
            {"STAR",  "Shines at night in the sky."},
            {"FISH",  "It swims in the water."},
            {"BEAR",  "A big furry animal."},
            {"DUCK",  "It quacks and swims."},
            {"WORM",  "A tiny underground creature."},
            {"GOAT",  "A farm animal with horns."},
            {"DEER",  "A gentle animal with antlers."},
            {"SHIP",  "A boat that sails on water."},
            {"TREE",  "A tall plant with leaves."},
            {"MILK",  "A white drink from cows."},
            {"CAKE",  "A sweet treat for birthdays."},
            {"BALL",  "You can throw or kick it."},
            {"BOOK",  "You read stories from it."},
            {"DESK",  "You sit at it to learn."},
            {"SOAP",  "You clean your hands with it."},
            {"MOON",  "It glows in the night sky."},
            {"WOLF",  "A wild animal that howls."},
            {"SAND",  "Tiny grains found at the beach."},
            {"SNOW",  "Cold white ice that falls from the sky."},
            {"LEAF",  "A part of a tree that is green."},
            {"FIRE",  "It is hot and bright."},
            {"CORN",  "A yellow vegetable on a cob."},
            {"HAND",  "You use it to hold things."},
            {"NOSE",  "You smell with it."},
            {"RAIN",  "Water drops that fall from clouds."},
            {"ROAD",  "Cars drive on it."},
            {"ROPE",  "A long strong string."}
    };

    // Tier 2: HARD (5-letter words)
    private static final String[][] LEVEL_TIER_3 = new String[][]{
            {"APPLE",  "A red or green fruit."},
            {"HOUSE",  "A place where families live."},
            {"SMILE",  "What you do when you feel happy."},
            {"TRAIN",  "A long vehicle that runs on tracks."},
            {"PLANT",  "It grows in soil with sun and water."},
            {"CHAIR",  "You sit on it."},
            {"BREAD",  "You eat it in sandwiches."},
            {"SHEEP",  "A soft white farm animal."},
            {"WATER",  "You drink it every day."},
            {"LIGHT",  "It brightens a room."},
            {"HORSE",  "A big animal you can ride."},
            {"CLOUD",  "Fluffy shapes in the sky."},
            {"MOUSE",  "A tiny animal or a computer tool."},
            {"BRUSH",  "You use it to clean or paint."},
            {"HEART",  "It beats inside your body."},
            {"SNAKE",  "A long slithering reptile."},
            {"SHIRT",  "You wear it on your body."},
            {"FRUIT",  "Sweet foods that grow on trees or plants."},
            {"BLOOM",  "When a flower opens."},
            {"SPOON",  "You eat soup with it."},
            {"BRICK",  "Used to build strong walls."},
            {"PLANE",  "It flies in the sky."},
            {"EARTH",  "The planet we live on."},
            {"BUNNY",  "A rabbit with long ears."},
            {"CROWN",  "A king or queen wears it."},
            {"SWEET",  "Another word for candy or sugar."},
            {"TIGER",  "A big cat with stripes."},
            {"MAGIC",  "Something amazing and mysterious."},
            {"PIZZA",  "A yummy food with cheese and sauce."},
            {"GRASS",  "Green blades that grow on the ground."}
    };

    private static final String[][][] LEVEL_POOLS = new String[][][]{
            LEVEL_TIER_1,
            LEVEL_TIER_2,
            LEVEL_TIER_3
    };

    private static final int MAX_GRID_COLS = 7;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spelling_game);

        // --- Bind views ---
        levelText   = findViewById(R.id.levelText);
        promptText  = findViewById(R.id.promptText);
        resultText  = findViewById(R.id.resultText);
        emojiHint   = findViewById(R.id.emojiHint);
        answerRow   = findViewById(R.id.answerRow);
        lettersGrid = findViewById(R.id.lettersGrid);
        btnBackspace= findViewById(R.id.btnBackspace);
        btnHint     = findViewById(R.id.btnHint);
        btnReset    = findViewById(R.id.btnReset);
        btnNext     = findViewById(R.id.btnNext);

        // --- Back to Home button (top-left) ---
        Button btnBackToHome = findViewById(R.id.btnBackToHome);
        if (btnBackToHome != null) {
            btnBackToHome.setOnClickListener(v -> finish());
        }

        // --- Init TTS ---
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.US);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(SpellingGameActivity.this,
                            "TTS language not available.", Toast.LENGTH_SHORT).show();
                } else {
                    ttsReady = true;
                }
            } else {
                Toast.makeText(SpellingGameActivity.this,
                        "Failed to initialize TTS.", Toast.LENGTH_SHORT).show();
            }
        });

        // --- Button handlers ---
        btnBackspace.setOnClickListener(v -> backspaceOne());
        btnHint.setOnClickListener(v -> revealOneLetter());
        btnReset.setOnClickListener(v -> resetCurrent());
        btnNext.setOnClickListener(v -> nextWordSameTier());

        // --- Parental controls / difficulty from Firebase ---
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

                    if (enabledFromDB != null)  isEnabled = enabledFromDB;
                    if (difficultyFromDB != null) difficulty = difficultyFromDB;
                }

                if (isEnabled) {
                    Log.d(TAG, "Starting " + APP_NAME + " with difficulty: " + difficulty);
                    initializeApp(difficulty);
                } else {
                    Log.w(TAG, APP_NAME + " is disabled by parent.");
                    Toast.makeText(SpellingGameActivity.this,
                            "This app is disabled by your parent.", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load app settings, using default.", error.toException());
                initializeApp("Easy"); // Fail-safe
            }
        });
    }

    /**
     * Map parental difficulty → exact tier.
     * Easy  -> tier 0 (3-letter words)
     * Medium-> tier 1 (4-letter words)
     * Hard  -> tier 2 (5-letter words)
     */
    private void initializeApp(String difficultyRaw) {
        String diff = (difficultyRaw == null)
                ? "easy"
                : difficultyRaw.trim().toLowerCase(Locale.US);

        if (diff.contains("medium")) {
            tierIndex = 1;
            difficultyLabel = "Medium: 4-letter words";
        } else if (diff.contains("hard")) {
            tierIndex = 2;
            difficultyLabel = "Hard: 5-letter words";
        } else {
            tierIndex = 0;
            difficultyLabel = "Easy: 3-letter words";
        }

        loadNewWordInCurrentTier();
    }

    // ---------- Level / word lifecycle ----------

    private void loadNewWordInCurrentTier() {
        if (tierIndex < 0 || tierIndex >= LEVEL_POOLS.length) {
            tierIndex = 0;
        }

        String[][] pool = LEVEL_POOLS[tierIndex];
        if (pool == null || pool.length == 0) {
            Toast.makeText(this, "No words configured for this difficulty.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Pick a random word from this tier
        int choice = rng.nextInt(pool.length);
        String word   = pool[choice][0];
        String prompt = pool[choice][1];

        currentWord = word.toUpperCase(Locale.US);
        bank  = toBank(currentWord);
        slots = emptySlots(currentWord.length());

        // UI
        levelText.setText(difficultyLabel);
        promptText.setText(prompt);
        if (emojiHint != null) {
            emojiHint.setText(getHintEmoji(currentWord));
            animateEmojiHint();
        }
        resultText.setText("");
        btnNext.setEnabled(false);

        buildAnswerRow();
        buildLettersGrid();
        speakPrompt();
    }

    /**
     * Next button: only get another random word in the SAME tier.
     */
    private void nextWordSameTier() {
        if (!isSpelledCorrect()) {
            Toast.makeText(this, "Finish this word first!", Toast.LENGTH_SHORT).show();
            return;
        }
        loadNewWordInCurrentTier();
    }

    private void resetCurrent() {
        bank  = toBank(currentWord);
        slots = emptySlots(currentWord.length());
        resultText.setText("");
        btnNext.setEnabled(false);
        buildAnswerRow();
        buildLettersGrid();
        speakPrompt();
    }

    // ---------- UI builders ----------

    private void buildAnswerRow() {
        answerRow.removeAllViews();
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            TextView slotView = new TextView(this);
            slotView.setText(slots.get(i) == null ? "_" : String.valueOf(slots.get(i)));
            slotView.setTextSize(28);
            slotView.setTypeface(Typeface.DEFAULT_BOLD);
            slotView.setPadding(dp(8), dp(8), dp(8), dp(8));
            slotView.setMinWidth(dp(34));
            slotView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            slotView.setBackgroundResource(android.R.drawable.editbox_background_normal);

            // Tap a filled slot to clear it and put letter back to bank
            slotView.setOnClickListener(v -> {
                Character c = slots.get(idx);
                if (c != null) {
                    slots.set(idx, null);
                    bank.add(c);
                    Collections.shuffle(bank);
                    buildAnswerRow();
                    buildLettersGrid();
                    resultText.setText("");
                    btnNext.setEnabled(false);
                }
            });

            answerRow.addView(slotView);
        }
    }

    private void buildLettersGrid() {
        lettersGrid.removeAllViews();
        int n = bank.size();
        if (n <= 0) return;

        int cols = Math.min(MAX_GRID_COLS, Math.max(3, (int) Math.ceil(Math.sqrt(n))));
        lettersGrid.setColumnCount(cols);

        for (int i = 0; i < bank.size(); i++) {
            final Character letter = bank.get(i);
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(letter.toString());
            b.setTextSize(22);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setLayoutParams(lp);

            b.setOnClickListener(v -> {
                // NEW: speak the letter out loud when tapped
                if (letter != null) {
                    speakLetter(letter.charValue());
                }
                // Existing behavior: place the letter into the next empty slot
                placeLetter(letter);
            });

            lettersGrid.addView(b);
        }
    }

    // ---------- Game mechanics ----------

    private void placeLetter(Character c) {
        int idx = firstEmptySlot();
        if (idx == -1) {
            Toast.makeText(this, "All slots filled. Use backspace to remove.", Toast.LENGTH_SHORT).show();
            return;
        }
        int removeAt = bank.indexOf(c);
        if (removeAt == -1) return;
        bank.remove(removeAt);

        slots.set(idx, c);
        buildAnswerRow();
        buildLettersGrid();

        checkAutoWin();
    }

    private void backspaceOne() {
        int idx = lastFilledSlot();
        if (idx == -1) {
            Toast.makeText(this, "Nothing to remove.", Toast.LENGTH_SHORT).show();
            return;
        }
        Character c = slots.get(idx);
        slots.set(idx, null);
        if (c != null) bank.add(c);
        Collections.shuffle(bank);
        buildAnswerRow();
        buildLettersGrid();
        resultText.setText("");
        btnNext.setEnabled(false);
    }

    private void revealOneLetter() {
        char[] target = currentWord.toCharArray();

        // First fix a wrong letter
        for (int i = 0; i < target.length; i++) {
            Character s = slots.get(i);
            if (s != null && s.charValue() != target[i]) {
                bank.add(s);
                boolean found = removeFromBank(target[i]);
                slots.set(i, Character.valueOf(target[i]));
                if (!found) removeExtraFromSlots(target[i], i);
                Collections.shuffle(bank);
                buildAnswerRow();
                buildLettersGrid();
                speakHintLetter(target[i]);
                checkAutoWin();
                return;
            }
        }
        // Otherwise fill first empty slot
        for (int i = 0; i < target.length; i++) {
            Character s = slots.get(i);
            if (s == null) {
                boolean found = removeFromBank(target[i]);
                slots.set(i, Character.valueOf(target[i]));
                if (!found) removeExtraFromSlots(target[i], i);
                Collections.shuffle(bank);
                buildAnswerRow();
                buildLettersGrid();
                speakHintLetter(target[i]);
                checkAutoWin();
                return;
            }
        }

        Toast.makeText(this, "Nothing to reveal.", Toast.LENGTH_SHORT).show();
    }

    private void checkAutoWin() {
        if (!hasEmptySlot() && isSpelledCorrect()) {
            resultText.setText("✅ Correct! Tap NEXT.");
            btnNext.setEnabled(true);
            speakText("Great job! That is correct.");
            animateCorrect();
        } else if (!hasEmptySlot()) {
            resultText.setText("❌ Not quite. Try again!");
            btnNext.setEnabled(false);
            speakText("Not quite. Try again.");
            animateWrong();
        } else {
            resultText.setText("");
            btnNext.setEnabled(false);
        }
    }

    private boolean removeFromBank(char c) {
        for (int i = 0; i < bank.size(); i++) {
            if (bank.get(i) == c) {
                bank.remove(i);
                return true;
            }
        }
        return false;
    }

    private void removeExtraFromSlots(char c, int keepIndex) {
        for (int i = 0; i < slots.size(); i++) {
            if (i == keepIndex) continue;
            Character s = slots.get(i);
            if (s != null && s.charValue() == c) {
                slots.set(i, null);
                bank.add(c);
                return;
            }
        }
    }

    // ---------- TTS helpers ----------

    private void speakPrompt() {
        if (!ttsReady) return;
        CharSequence clue = promptText.getText();
        if (clue == null || TextUtils.isEmpty(clue.toString())) return;

        String msg = difficultyLabel + ". " + clue + " Spell the word.";
        speakText(msg);
    }

    private void speakHintLetter(char c) {
        if (!ttsReady) return;
        speakText("The next letter is " + c);
    }

    // NEW: Speak a single letter when a tile is tapped
    private void speakLetter(char c) {
        if (!ttsReady) return;
        String text = String.valueOf(c);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null,
                    "letter-" + c + "-" + System.currentTimeMillis());
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "letter");
            tts.speak(text, TextToSpeech.QUEUE_ADD, params);
        }
    }

    private void speakText(String text) {
        if (!ttsReady || TextUtils.isEmpty(text)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-" + System.currentTimeMillis());
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utt");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    // ---------- Emoji hint helper ----------

    private String getHintEmoji(String wordUpper) {
        if (wordUpper == null) return "❓";
        switch (wordUpper) {
            // EASY
            case "CAT":   return "🐱";
            case "DOG":   return "🐶";
            case "SUN":   return "☀️";
            case "CAR":   return "🚗";
            case "BUS":   return "🚌";
            case "ANT":   return "🐜";
            case "HAT":   return "👒";
            case "PEN":   return "🖊️";
            case "MAP":   return "🗺️";
            case "CUP":   return "☕";
            case "BOX":   return "📦";
            case "BED":   return "🛏️";
            case "PIG":   return "🐷";
            case "RUG":   return "🧶";
            case "BAT":   return "🦇";
            case "FOX":   return "🦊";
            case "JAR":   return "🥫";
            case "NUT":   return "🥜";
            case "LOG":   return "🪵";
            case "EGG":   return "🥚";
            case "BUG":   return "🐞";
            case "BEE":   return "🐝";
            case "RAT":   return "🐭";
            case "OWL":   return "🦉";
            case "TOY":   return "🧸";
            case "KEY":   return "🔑";
            case "LIP":   return "👄";
            case "EAR":   return "👂";
            case "BOW":   return "🎀";
            case "ICE":   return "🧊";

            // MEDIUM
            case "FROG":  return "🐸";
            case "BIRD":  return "🐦";
            case "LION":  return "🦁";
            case "STAR":  return "⭐";
            case "FISH":  return "🐟";
            case "BEAR":  return "🐻";
            case "DUCK":  return "🦆";
            case "WORM":  return "🪱";
            case "GOAT":  return "🐐";
            case "DEER":  return "🦌";
            case "SHIP":  return "🚢";
            case "TREE":  return "🌳";
            case "MILK":  return "🥛";
            case "CAKE":  return "🎂";
            case "BALL":  return "⚽";
            case "BOOK":  return "📖";
            case "DESK":  return "🪑";
            case "SOAP":  return "🧼";
            case "MOON":  return "🌙";
            case "WOLF":  return "🐺";
            case "SAND":  return "🏖️";
            case "SNOW":  return "❄️";
            case "LEAF":  return "🍃";
            case "FIRE":  return "🔥";
            case "CORN":  return "🌽";
            case "HAND":  return "🤚";
            case "NOSE":  return "👃";
            case "RAIN":  return "🌧️";
            case "ROAD":  return "🛣️";
            case "ROPE":  return "🪢";

            // HARD
            case "APPLE":  return "🍎";
            case "HOUSE":  return "🏠";
            case "SMILE":  return "😊";
            case "TRAIN":  return "🚆";
            case "PLANT":  return "🪴";
            case "CHAIR":  return "🪑";
            case "BREAD":  return "🍞";
            case "SHEEP":  return "🐑";
            case "WATER":  return "💧";
            case "LIGHT":  return "💡";
            case "HORSE":  return "🐴";
            case "CLOUD":  return "☁️";
            case "MOUSE":  return "🐭";
            case "BRUSH":  return "🪥";
            case "HEART":  return "❤️";
            case "SNAKE":  return "🐍";
            case "SHIRT":  return "👕";
            case "FRUIT":  return "🍇";
            case "BLOOM":  return "🌸";
            case "SPOON":  return "🥄";
            case "BRICK":  return "🧱";
            case "PLANE":  return "✈️";
            case "EARTH":  return "🌍";
            case "BUNNY":  return "🐰";
            case "CROWN":  return "👑";
            case "SWEET":  return "🍬";
            case "TIGER":  return "🐯";
            case "MAGIC":  return "✨";
            case "PIZZA":  return "🍕";
            case "GRASS":  return "🌱";

            default:
                return "❓";
        }
    }

    // ---------- Animations ----------

    private void animateEmojiHint() {
        if (emojiHint == null) return;

        ScaleAnimation scale = new ScaleAnimation(
                0.7f, 1.15f,
                0.7f, 1.15f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(450);
        scale.setRepeatCount(1);
        scale.setRepeatMode(Animation.REVERSE);
        emojiHint.startAnimation(scale);
    }

    private void animateCorrect() {
        if (resultText == null) return;

        ScaleAnimation scale = new ScaleAnimation(
                0.8f, 1.2f,
                0.8f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(400);
        scale.setRepeatCount(1);
        scale.setRepeatMode(Animation.REVERSE);
        resultText.startAnimation(scale);
    }

    private void animateWrong() {
        if (answerRow == null) return;

        TranslateAnimation shake = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, -0.05f,
                Animation.RELATIVE_TO_SELF,  0.05f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f
        );
        shake.setDuration(80);
        shake.setRepeatCount(5);
        shake.setRepeatMode(Animation.REVERSE);
        answerRow.startAnimation(shake);
    }

    // ---------- Utility helpers ----------

    private ArrayList<Character> toBank(String wordUpper) {
        ArrayList<Character> out = new ArrayList<>();
        char[] arr = wordUpper.toCharArray();
        for (char c : arr) out.add(c);
        Collections.shuffle(out);
        return out;
    }

    private ArrayList<Character> emptySlots(int n) {
        ArrayList<Character> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(null);
        return out;
    }

    private int firstEmptySlot() {
        for (int i = 0; i < slots.size(); i++) if (slots.get(i) == null) return i;
        return -1;
    }

    private int lastFilledSlot() {
        for (int i = slots.size() - 1; i >= 0; i--) if (slots.get(i) != null) return i;
        return -1;
    }

    private boolean hasEmptySlot() {
        for (int i = 0; i < slots.size(); i++) if (slots.get(i) == null) return true;
        return false;
    }

    private boolean isSpelledCorrect() {
        StringBuilder sb = new StringBuilder();
        for (Character c : slots) sb.append(c == null ? "" : c);
        return currentWord.contentEquals(sb);
    }

    private int dp(int d) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(d * density);
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
