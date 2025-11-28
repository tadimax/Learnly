package com.example.learnly;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class MemoryMatchActivity extends AppCompatActivity {

    private static final String TAG = "MemoryMatchActivity";
    private static final String APP_NAME = "MemoryMatch"; // miniApps/MemoryMatch in Firebase

    // UI
    private TextView gridSizeText, targetsText, roundText, phaseText;
    private ProgressBar timerBar;
    private GridLayout grid;
    private LinearLayout paletteRow;
    private Button btnStart;
    private Button btnBackToHome;
    private SeekBar targetsSeek;
    private LottieAnimationView animCongrats, animTrophy;

    // Firebase
    private DatabaseReference mAppSettingsRef;

    // State / config
    private final Random rng = new Random();
    private int gridSize = 3;        // set via Firebase difficulty
    private int targetCount = 4;     // will be clamped based on grid
    private long memorizeMs = 2500;  // memorize phase
    private long solveMs = 12000;    // user-adjustable via slider
    private CountDownTimer timer;
    private boolean inSolve = false;

    // Progress / leveling
    private int solvedStreak = 0;    // how many puzzles solved in a row

    // Board
    private int total;
    private int[] cellColor;         // actual color for targets (gray otherwise)
    private boolean[] isTarget;      // which cells are targets
    private boolean[] revealed;      // targets already solved

    // Colors (last = gray / concealed)
    private static final int[] COLORS = new int[]{
            Color.parseColor("#F44336"), Color.parseColor("#E91E63"),
            Color.parseColor("#9C27B0"), Color.parseColor("#3F51B5"),
            Color.parseColor("#2196F3"), Color.parseColor("#009688"),
            Color.parseColor("#4CAF50"), Color.parseColor("#8BC34A"),
            Color.parseColor("#FFC107"), Color.parseColor("#FF9800"),
            Color.parseColor("#795548"), Color.parseColor("#9E9E9E")
    };
    private static final int CONCEAL = COLORS[COLORS.length - 1];

    // Palette multiset
    private final ArrayList<Integer> palette = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory_match);

        // Bind views
        gridSizeText = findViewById(R.id.gridSizeText);
        targetsText  = findViewById(R.id.targetsText);
        roundText    = findViewById(R.id.roundText);
        phaseText    = findViewById(R.id.phaseText);
        timerBar     = findViewById(R.id.timerBar);
        grid         = findViewById(R.id.grid);
        paletteRow   = findViewById(R.id.paletteRow);
        btnStart     = findViewById(R.id.btnStart);
        btnBackToHome = findViewById(R.id.btnBackToHome);
        targetsSeek  = findViewById(R.id.targetsSeek);
        animCongrats = findViewById(R.id.animCongrats);
        animTrophy   = findViewById(R.id.animTrophy);

        // Targets slider
        targetsSeek.setMax(gridSize * gridSize);
        targetsSeek.setProgress(targetCount);
        targetsSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                targetCount = Math.max(1, p);
                clampTargets();
                updateTargetsLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Home button
        btnBackToHome.setOnClickListener(v -> {
            Intent i = new Intent(MemoryMatchActivity.this, HomeActivity.class);
            startActivity(i);
            finish();
        });

        // Round duration slider
        View roundSeekView = findViewById(R.id.roundSeek);
        if (roundSeekView instanceof SeekBar) {
            SeekBar roundSeek = (SeekBar) roundSeekView;
            roundSeek.setMax(55); // 0..55 -> 5..60 seconds
            int defSec = Math.max(5, Math.min(60, (int) (solveMs / 1000)));
            roundSeek.setProgress(defSec - 5);
            roundText.setText("Round: " + defSec + "s");
            roundSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int s = 5 + progress;
                    solveMs = s * 1000L;
                    roundText.setText("Round: " + s + "s");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
        }

        // Start button
        btnStart.setOnClickListener(v -> startRound());

        // --- Parental controls / difficulty from Firebase ---
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
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String difficulty = "Easy";
                boolean isEnabled = true;

                if (snapshot.exists()) {
                    Boolean enabledFromDB = snapshot.child("enabled").getValue(Boolean.class);
                    String difficultyFromDB = snapshot.child("difficulty").getValue(String.class);

                    if (enabledFromDB != null) isEnabled = enabledFromDB;
                    if (difficultyFromDB != null) difficulty = difficultyFromDB;
                }

                if (!isEnabled) {
                    Toast.makeText(MemoryMatchActivity.this,
                            "This game is disabled by your parent.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                initializeDifficulty(difficulty);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MemoryMatchActivity.this,
                        "Failed to load settings. Using Easy mode.", Toast.LENGTH_LONG).show();
                initializeDifficulty("Easy");
            }
        });
    }

    /**
     * Map Firebase difficulty to grid size & starting targetCount.
     * Easy   -> 3x3
     * Medium -> 4x4
     * Hard   -> 5x5
     */
    private void initializeDifficulty(String difficulty) {
        switch (difficulty) {
            case "Medium":
                gridSize = 4;
                targetCount = 5;   // reasonable starting challenge
                break;
            case "Hard":
                gridSize = 5;
                targetCount = 6;
                break;
            case "Easy":
            default:
                gridSize = 3;
                targetCount = 3;
                break;
        }
        clampTargets();

        // sync slider with difficulty-based grid
        targetsSeek.setMax(gridSize * gridSize);
        targetsSeek.setProgress(targetCount);

        updateGridLabel();
        updateTargetsLabel();
        phaseText.setText("Tap START to play");
    }

    // ---------- Round control ----------

    private void startRound() {
        cancelTimer();
        inSolve = false;
        phaseText.setText("Memorize the COLORED tiles…");
        timerBar.setProgress(0);

        buildBoard();
        buildGridViews(false);    // show: targets colored, others gray
        paletteRow.removeAllViews();

        // Memorize -> Conceal -> Solve
        timerBar.setMax((int) memorizeMs);
        timerBar.setProgress((int) memorizeMs);

        timer = new CountDownTimer(memorizeMs, 16) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerBar.setProgress((int) millisUntilFinished);
            }

            @Override
            public void onFinish() {
                timerBar.setProgress(0);
                concealAll();
                buildPalette();
                enterSolve();
            }
        }.start();
    }

    private void enterSolve() {
        inSolve = true;
        phaseText.setText("Drag a color into the correct squares!");
        timerBar.setMax((int) solveMs);
        timerBar.setProgress((int) solveMs);
        cancelTimer();
        timer = new CountDownTimer(solveMs, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerBar.setProgress((int) millisUntilFinished);
            }

            @Override
            public void onFinish() {
                inSolve = false;
                toast("Time's up! Let's try again.");
                solvedStreak = 0;  // break streak
            }
        }.start();
    }

    // ---------- Board building ----------

    private void buildBoard() {
        total = gridSize * gridSize;
        cellColor = new int[total];
        isTarget  = new boolean[total];
        revealed  = new boolean[total];

        // Make sure targetCount fits new grid
        clampTargets();

        // Pick distinct target positions
        Set<Integer> chosen = new HashSet<>();
        while (chosen.size() < targetCount) {
            chosen.add(rng.nextInt(total));
        }

        // Color pool (exclude gray)
        ArrayList<Integer> colorPool = new ArrayList<>();
        for (int i = 0; i < COLORS.length - 1; i++) {
            colorPool.add(COLORS[i]);
        }
        Collections.shuffle(colorPool, rng);

        for (int i = 0; i < total; i++) {
            if (chosen.contains(i)) {
                isTarget[i] = true;
                cellColor[i] = colorPool.get(rng.nextInt(colorPool.size()));
            } else {
                isTarget[i] = false;
                cellColor[i] = CONCEAL;
            }
            revealed[i] = false;
        }
    }

    private void buildGridViews(boolean concealed) {
        grid.removeAllViews();
        grid.setColumnCount(gridSize);
        grid.setRowCount(gridSize);

        grid.post(() -> {
            int w = grid.getWidth();
            int margin = dp(2);
            int side = Math.max(dp(52), (w - margin * 2 * gridSize) / gridSize); // bigger squares

            for (int i = 0; i < total; i++) {
                View cell = new View(MemoryMatchActivity.this);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = side;
                lp.height = side;
                lp.setMargins(margin, margin, margin, margin);
                cell.setLayoutParams(lp);

                int shownColor;
                if (!isTarget[i]) {
                    shownColor = CONCEAL;
                } else if (concealed && !revealed[i]) {
                    shownColor = CONCEAL;
                } else {
                    shownColor = cellColor[i];
                }

                cell.setBackgroundColor(shownColor);

                final int idx = i;
                cell.setOnDragListener((v, e) -> onCellDrag(idx, v, e));

                grid.addView(cell);
            }
        });
    }

    private void concealAll() {
        buildGridViews(true);
    }

    private void buildPalette() {
        palette.clear();
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < total; i++) {
            if (isTarget[i] && !revealed[i]) {
                int c = cellColor[i];
                counts.put(c, counts.getOrDefault(c, 0) + 1);
            }
        }
        for (Integer c : counts.keySet()) {
            for (int k = 0; k < counts.get(c); k++) {
                palette.add(c);
            }
        }
        Collections.shuffle(palette, rng);

        paletteRow.removeAllViews();
        int size = dp(52);  // larger tap & drag area
        int margin = dp(6);
        for (int i = 0; i < palette.size(); i++) {
            final int color = palette.get(i);
            View tile = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            tile.setLayoutParams(lp);
            tile.setBackgroundColor(color);

            // One-tap-and-drag: start drag on ACTION_DOWN
            tile.setOnTouchListener((v, event) -> {
                if (!inSolve) return true;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        v.startDragAndDrop(null, shadow, color, 0);
                    } else {
                        v.startDrag(null, shadow, color, 0);
                    }
                    return true;
                }
                return false;
            });

            paletteRow.addView(tile);
        }
    }

    // ---------- Drag handling ----------

    private boolean onCellDrag(int idx, View cellView, DragEvent e) {
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                cellView.setAlpha(0.9f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                cellView.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP: {
                cellView.setAlpha(1f);
                if (!inSolve) return false;
                if (!isTarget[idx]) {
                    toast("Oops, that square was gray.");
                    return false;
                }
                if (revealed[idx]) {
                    toast("That square is already filled.");
                    return false;
                }

                Object payload = e.getLocalState();
                if (!(payload instanceof Integer)) return false;
                int draggedColor = (Integer) payload;

                if (draggedColor == cellColor[idx]) {
                    revealed[idx] = true;
                    cellView.setBackgroundColor(draggedColor);
                    consumeOnePaletteTile(draggedColor);

                    if (allTargetsSolved()) {
                        onPuzzleSolved();
                    }
                } else {
                    // Flash red briefly to show wrong drop
                    cellView.setBackgroundColor(Color.parseColor("#44FF0000"));
                    cellView.postDelayed(() -> cellView.setBackgroundColor(CONCEAL), 200);
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                cellView.setAlpha(1f);
                return true;
        }
        return false;
    }

    private void onPuzzleSolved() {
        toast("Great job! You found them all!");
        cancelTimer();
        inSolve = false;

        solvedStreak++;

        // Decide which animation & level-up logic
        boolean isTrophy = (solvedStreak % 10 == 0);

        if (isTrophy) {
            // level up: +1 target within this grid
            targetCount++;
            clampTargets();
            targetsSeek.setProgress(targetCount);
            updateTargetsLabel();
            toast("Level up! More colors to remember!");
            showTrophyAnimation();
        } else {
            showCongratsAnimation();
        }

        // Automatically start next round after animation
        int delay = isTrophy ? 2200 : 1500;
        grid.postDelayed(this::startRound, delay);
    }

    // ---------- Lottie helpers ----------

    private void showCongratsAnimation() {
        animTrophy.setVisibility(View.GONE);

        animCongrats.setAnimation(R.raw.congratulations);
        animCongrats.setVisibility(View.VISIBLE);
        animCongrats.playAnimation();

        animCongrats.postDelayed(() ->
                        animCongrats.setVisibility(View.GONE),
                1500
        );
    }

    private void showTrophyAnimation() {
        animCongrats.setVisibility(View.GONE);

        animTrophy.setAnimation(R.raw.trophy);
        animTrophy.setVisibility(View.VISIBLE);
        animTrophy.playAnimation();

        animTrophy.postDelayed(() ->
                        animTrophy.setVisibility(View.GONE),
                2000
        );
    }

    private void consumeOnePaletteTile(@ColorInt int color) {
        for (int i = 0; i < paletteRow.getChildCount(); i++) {
            View v = paletteRow.getChildAt(i);
            int bg = colorOf(v);
            if (bg == color) {
                paletteRow.removeViewAt(i);
                break;
            }
        }
    }

    private boolean allTargetsSolved() {
        for (int i = 0; i < total; i++) {
            if (isTarget[i] && !revealed[i]) return false;
        }
        return true;
    }

    @ColorInt
    private int colorOf(View v) {
        try {
            ColorDrawable cd = (ColorDrawable) v.getBackground();
            return cd.getColor();
        } catch (Exception ex) {
            return CONCEAL;
        }
    }

    // ---------- Helpers ----------

    private void clampTargets() {
        int max = gridSize * gridSize;
        if (targetCount < 1) targetCount = 1;
        if (targetCount > max) targetCount = max;
    }

    private void updateGridLabel() {
        gridSizeText.setText("Grid: " + gridSize + "×" + gridSize);
    }

    private void updateTargetsLabel() {
        targetsText.setText("Targets: " + targetCount);
    }

    private int dp(int d) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(d * density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelTimer();
    }
}
