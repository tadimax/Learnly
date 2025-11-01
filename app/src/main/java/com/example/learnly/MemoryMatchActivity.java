package com.example.learnly;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.DragEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class MemoryMatchActivity extends AppCompatActivity {

    // UI
    private TextView gridSizeText, targetsText, roundText, phaseText;
    private SeekBar gridSeek, targetsSeek, roundSeek;
    private Button btnStart;
    private ProgressBar timerBar;
    private GridLayout grid;
    private LinearLayout paletteRow;

    // State
    private final Random rng = new Random();
    private int gridSize = 3;        // 3..6
    private int targetCount = 4;     // colored cells to memorize/solve
    private long memorizeMs = 2500;  // fixed memorize time (simple)
    private long solveMs = 12000;    // per-round user-selected (5..60s)
    private CountDownTimer timer;
    private boolean inSolve = false;

    // Board
    private int total;
    private int[] cellColor;         // actual color for targets (gray otherwise)
    private boolean[] isTarget;      // which cells are targets
    private boolean[] revealed;      // targets already solved

    // Colors (last = gray)
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

        // Bind views (NO custom helpers)
        gridSizeText = findViewById(R.id.gridSizeText);
        targetsText  = findViewById(R.id.targetsText);
        roundText    = findViewById(R.id.roundText);
        phaseText    = findViewById(R.id.phaseText);
        gridSeek     = findViewById(R.id.gridSeek);
        targetsSeek  = findViewById(R.id.targetsSeek);
        roundSeek    = findViewById(R.id.roundSeek);
        btnStart     = findViewById(R.id.btnStart);
        timerBar     = findViewById(R.id.timerBar);
        grid         = findViewById(R.id.grid);
        paletteRow   = findViewById(R.id.paletteRow);

        // Grid size (3..6)
        gridSeek.setMax(3); // progress 0..3 -> 3..6
        gridSeek.setProgress(0);
        updateGridLabel();
        gridSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                gridSize = 3 + p;
                clampTargets();
                updateGridLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Targets (1..grid^2)
        targetsSeek.setMax(gridSize * gridSize);
        clampTargets();
        updateTargetsLabel();
        targetsSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                targetCount = Math.max(1, p);
                clampTargets();
                updateTargetsLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Round duration (5..60s)
        roundSeek.setMax(55); // 0..55 -> 5..60
        int defSec = Math.max(5, Math.min(60, (int)(solveMs / 1000)));
        roundSeek.setProgress(defSec - 5);
        roundText.setText("Round: " + defSec + "s");
        roundSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                int s = 5 + p;
                solveMs = s * 1000L;
                roundText.setText("Round: " + s + "s");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Start button
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startRound(); }
        });
    }

    private void startRound() {
        cancelTimer();
        inSolve = false;
        phaseText.setText("Memorize the COLORED targets…");
        timerBar.setProgress(0);

        buildBoard();
        buildGridViews(false);    // show: targets colored, others gray
        paletteRow.removeAllViews();

        // Memorize -> Conceal -> Solve
        timerBar.setMax((int) memorizeMs);
        timerBar.setProgress((int) memorizeMs);
        timer = new CountDownTimer(memorizeMs, 16) {
            @Override public void onTick(long millisUntilFinished) {
                timerBar.setProgress((int) millisUntilFinished);
            }
            @Override public void onFinish() {
                timerBar.setProgress(0);
                concealAll();
                buildPalette();
                enterSolve();
            }
        }.start();
    }

    private void enterSolve() {
        inSolve = true;
        phaseText.setText("Drag from palette into correct TARGET cells.");
        timerBar.setMax((int) solveMs);
        timerBar.setProgress((int) solveMs);
        cancelTimer();
        timer = new CountDownTimer(solveMs, 50) {
            @Override public void onTick(long l) { timerBar.setProgress((int) l); }
            @Override public void onFinish() { inSolve = false; toast("Time's up!"); }
        }.start();
    }

    private void buildBoard() {
        total = gridSize * gridSize;
        cellColor = new int[total];
        isTarget  = new boolean[total];
        revealed  = new boolean[total];

        // Pick distinct target positions
        targetCount = Math.max(1, Math.min(targetCount, total));
        Set<Integer> chosen = new HashSet<>();
        while (chosen.size() < targetCount) chosen.add(rng.nextInt(total));

        // Color pool (exclude gray)
        ArrayList<Integer> colorPool = new ArrayList<Integer>();
        for (int i = 0; i < COLORS.length - 1; i++) colorPool.add(COLORS[i]);
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

        // Calculate square size from grid width
        grid.post(new Runnable() {
            @Override public void run() {
                int w = grid.getWidth();
                int m = dp(4);
                int side = Math.max(dp(36), (w - m * 2 * gridSize) / gridSize);

                for (int i = 0; i < total; i++) {
                    View cell = new View(MemoryMatchActivity.this);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = side;
                    lp.height = side;
                    lp.setMargins(m, m, m, m);
                    cell.setLayoutParams(lp);

                    int shown;
                    if (!isTarget[i]) shown = CONCEAL;
                    else if (concealed && !revealed[i]) shown = CONCEAL;
                    else shown = cellColor[i];

                    cell.setBackgroundColor(shown);

                    final int idx = i;
                    cell.setOnDragListener(new View.OnDragListener() {
                        @Override public boolean onDrag(View v, DragEvent e) {
                            return onCellDrag(idx, v, e);
                        }
                    });

                    grid.addView(cell);
                }
            }
        });
    }

    private void concealAll() { buildGridViews(true); }

    private void buildPalette() {
        palette.clear();
        HashMap<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for (int i = 0; i < total; i++) {
            if (isTarget[i] && !revealed[i]) {
                Integer c = cellColor[i];
                counts.put(c, counts.getOrDefault(c, 0) + 1);
            }
        }
        for (Integer c : counts.keySet()) {
            for (int k = 0; k < counts.get(c); k++) palette.add(c);
        }
        Collections.shuffle(palette, rng);

        paletteRow.removeAllViews();
        int size = dp(36), margin = dp(6);
        for (int i = 0; i < palette.size(); i++) {
            final int color = palette.get(i);
            View tile = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            tile.setLayoutParams(lp);
            tile.setBackgroundColor(color);
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (!inSolve) return true;
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        v.startDragAndDrop(null, shadow, color, 0);
                    } else {
                        v.startDrag(null, shadow, color, 0);
                    }
                    return true;
                }
            });
            paletteRow.addView(tile);
        }
    }

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
                if (!isTarget[idx]) { toast("Not a target cell"); return false; }
                if (revealed[idx])  { toast("Already filled"); return false; }

                Object payload = e.getLocalState();
                if (!(payload instanceof Integer)) return false;
                int draggedColor = (Integer) payload;

                if (draggedColor == cellColor[idx]) {
                    revealed[idx] = true;
                    cellView.setBackgroundColor(draggedColor);
                    consumeOnePaletteTile(draggedColor);
                    if (allTargetsSolved()) {
                        toast("Solved!");
                        cancelTimer();
                        inSolve = false;
                    }
                } else {
                    cellView.setBackgroundColor(Color.parseColor("#44FF0000"));
                    cellView.postDelayed(new Runnable() {
                        @Override public void run() {
                            cellView.setBackgroundColor(CONCEAL);
                        }
                    }, 180);
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                cellView.setAlpha(1f);
                return true;
        }
        return false;
    }

    private void consumeOnePaletteTile(@ColorInt int color) {
        // remove one matching tile from paletteRow
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

    private void clampTargets() {
        int max = gridSize * gridSize;
        targetsSeek.setMax(max);
        if (targetCount < 1) targetCount = 1;
        if (targetCount > max) targetCount = max;
        if (targetsSeek.getProgress() != targetCount) {
            targetsSeek.setProgress(targetCount);
        }
    }

    private void updateGridLabel()    { gridSizeText.setText("Grid: " + gridSize + "×" + gridSize); }
    private void updateTargetsLabel() { targetsText.setText("Targets: " + targetCount); }

    private int dp(int d) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(d * density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void cancelTimer() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    @Override protected void onPause() {
        super.onPause();
        cancelTimer();
    }
}
