package com.example.learnly;

import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

public class SpellingGameActivity extends AppCompatActivity {

    // UI
    private TextView levelText, promptText, resultText;
    private LinearLayout answerRow;
    private GridLayout lettersGrid;
    private Button btnBackspace, btnHint, btnReset, btnNext;

    // Game state
    private int levelIndex = 0;               // current level index
    private String currentWord = "";          // target word (uppercase)
    private ArrayList<Character> bank;        // shuffled letter bank
    private ArrayList<Character> slots;       // user-filled slots (size = word length)

    // TTS
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // Level data: 3 levels (3, 4, 5 letters)
    // Each level: {WORD (UPPERCASE), spoken/text clue}
    private static final String[][] LEVELS = new String[][]{
            {"CAT",   "A small pet that says meow."},   // Level 1 – 3 letters
            {"FROG",  "It hops and says ribbit."},      // Level 2 – 4 letters
            {"APPLE", "A red or green fruit."}          // Level 3 – 5 letters
    };

    // Config
    private static final int MAX_GRID_COLS = 7; // letter button grid width

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spelling_game);

        // Bind views
        levelText   = findViewById(R.id.levelText);
        promptText  = findViewById(R.id.promptText);
        resultText  = findViewById(R.id.resultText);
        answerRow   = findViewById(R.id.answerRow);
        lettersGrid = findViewById(R.id.lettersGrid);
        btnBackspace= findViewById(R.id.btnBackspace);
        btnHint     = findViewById(R.id.btnHint);
        btnReset    = findViewById(R.id.btnReset);
        btnNext     = findViewById(R.id.btnNext);

        // Init TTS
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int res = tts.setLanguage(Locale.US);
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(SpellingGameActivity.this,
                                "TTS language not available.", Toast.LENGTH_SHORT).show();
                    } else {
                        ttsReady = true;
                        // Once ready, speak the current prompt if we already loaded it
                        speakPrompt();
                    }
                } else {
                    Toast.makeText(SpellingGameActivity.this,
                            "Failed to initialize TTS.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Button handlers
        btnBackspace.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { backspaceOne(); }
        });
        btnHint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { revealOneLetter(); }
        });
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { resetCurrent(); }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { nextLevel(); }
        });

        // Start at level chosen from LevelSelectActivity
        int startIndex = getIntent().getIntExtra("LEVEL_INDEX", 0);
        loadLevel(startIndex);
    }

    // ---------- Level lifecycle ----------

    private void loadLevel(int index) {
        if (index < 0) index = 0;
        if (index >= LEVELS.length) index = LEVELS.length - 1;
        levelIndex = index;

        String word   = LEVELS[levelIndex][0];
        String prompt = LEVELS[levelIndex][1];

        currentWord = word.toUpperCase(Locale.US);
        bank  = toBank(currentWord);                 // shuffled letters
        slots = emptySlots(currentWord.length());    // all empty

        // UI
        levelText.setText("Level " + (levelIndex + 1) + " / " + LEVELS.length);
        promptText.setText(prompt);
        resultText.setText("");
        btnNext.setEnabled(false);

        buildAnswerRow();
        buildLettersGrid();
        speakPrompt();  // speak prompt whenever level loads (if TTS is ready)
    }

    private void nextLevel() {
        if (!isSpelledCorrect()) {
            Toast.makeText(this, "Finish this word first!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (levelIndex + 1 < LEVELS.length) {
            loadLevel(levelIndex + 1);
        } else {
            resultText.setText("🏆 All levels complete! Great job!");
            btnNext.setEnabled(false);
            speakText("All levels complete. Great job!");
        }
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
            slotView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
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
                }
            });

            answerRow.addView(slotView);
        }
    }

    private void buildLettersGrid() {
        lettersGrid.removeAllViews();
        int n = bank.size();
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

            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    placeLetter(letter);
                }
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

        if (!hasEmptySlot()) {
            if (isSpelledCorrect()) {
                resultText.setText("✅ Correct! Tap NEXT.");
                btnNext.setEnabled(true);
                speakText("Great job! That is correct.");
            } else {
                resultText.setText("❌ Not quite. Try again!");
                btnNext.setEnabled(false);
                speakText("Not quite. Try again.");
            }
        } else {
            resultText.setText("");
            btnNext.setEnabled(false);
        }
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

        // First try to fix a wrong letter
        for (int i = 0; i < target.length; i++) {
            Character s = slots.get(i);
            if (s != null && s.charValue() != target[i]) {
                // Return wrong letter to bank
                bank.add(s);
                // Consume correct letter from bank
                boolean found = removeFromBank(target[i]);
                slots.set(i, Character.valueOf(target[i]));
                if (!found) removeExtraFromSlots(target[i], i);
                Collections.shuffle(bank);
                buildAnswerRow();
                buildLettersGrid();
                speakHintLetter(target[i], i);
                checkAutoWin();
                return;
            }
        }
        // Otherwise fill first empty slot with correct letter
        for (int i = 0; i < target.length; i++) {
            Character s = slots.get(i);
            if (s == null) {
                boolean found = removeFromBank(target[i]);
                slots.set(i, Character.valueOf(target[i]));
                if (!found) removeExtraFromSlots(target[i], i);
                Collections.shuffle(bank);
                buildAnswerRow();
                buildLettersGrid();
                speakHintLetter(target[i], i);
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
        } else if (!hasEmptySlot()) {
            resultText.setText("❌ Not quite. Try again!");
            btnNext.setEnabled(false);
            speakText("Not quite. Try again.");
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
                bank.add(Character.valueOf(c));
                return;
            }
        }
    }

    // ---------- TTS helpers ----------

    private void speakPrompt() {
        if (!ttsReady) return;
        String clue = promptText.getText() != null ? promptText.getText().toString() : "";
        if (TextUtils.isEmpty(clue)) return;

        // Example: "Level 1. A small pet that says meow. Spell the word."
        String msg = "Level " + (levelIndex + 1) + ". " + clue + " Spell the word.";
        speakText(msg);
    }

    private void speakHintLetter(char c, int positionIndex) {
        if (!ttsReady) return;
        // positionIndex is 0-based; for kids: "The next letter is C."
        String msg = "The next letter is " + c;
        speakText(msg);
    }

    private void speakText(String text) {
        if (!ttsReady || TextUtils.isEmpty(text)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-" + System.currentTimeMillis());
        } else {
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utt");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    // ---------- Helpers ----------

    private ArrayList<Character> toBank(String wordUpper) {
        char[] arr = wordUpper.toCharArray();
        ArrayList<Character> out = new ArrayList<Character>();
        for (char c : arr) out.add(Character.valueOf(c));
        Collections.shuffle(out);
        return out;
    }

    private ArrayList<Character> emptySlots(int n) {
        ArrayList<Character> out = new ArrayList<Character>(n);
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
