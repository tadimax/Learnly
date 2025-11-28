package com.example.learnly;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class StoryTimeActivity extends AppCompatActivity {

    // Night mode prefs
    private static final String PREFS_NAME = "story_time_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    // UI
    private View root;
    private Button btnPlay, btnPause, btnStop, btnBackToHome;
    private Button btnStory1, btnStory2, btnStory3;
    private SeekBar rateSeek, pitchSeek;
    private TextView statusText;
    private TextView storyTitleText;
    private ToggleButton toggleNightMode;

    // TTS
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // Stories
    private static final String STORY_1 =
            "Once upon a time, in a cosy little village, there lived a curious child named Mira. "
                    + "Every night, Mira looked up at the moon and wondered where dreams came from. "
                    + "One evening, a friendly firefly appeared at her window and said, follow me. "
                    + "They floated past sleeping trees and over a silver stream that giggled softly. "
                    + "At the edge of a meadow, the firefly showed Mira a glowing door made of starlight. "
                    + "When she stepped through, the sky filled with gentle music and warm colours. "
                    + "The stars hummed lullabies, and the moon smiled kindly. "
                    + "Dreams are stories you tell your heart, the firefly said. "
                    + "Mira drifted home on a moonbeam and fell asleep, ready for the next adventure.";

    private static final String STORY_2 =
            "In a bright town by the sea, a boy named Leo had a red kite that could fly higher than all the rooftops. "
                    + "One windy afternoon, a huge gust of wind tugged the kite right out of Leo's hands and sent it sailing away. "
                    + "Leo chased it past the bakery, past the library, and all the way to the sandy beach. "
                    + "There, a group of laughing seagulls had tangled the kite's tail in a tall dune bush. "
                    + "Leo sighed, but instead of scolding the birds, he gently freed the kite and shared a smile. "
                    + "The seagulls helped by flapping their wings to lift the kite into the sky again. "
                    + "From that day on, the gulls followed Leo whenever he flew his kite, "
                    + "like a little cloud of feathery friends in the blue sky.";

    private static final String STORY_3 =
            "Deep in a quiet forest, a tiny fox named Pip loved collecting sounds. "
                    + "He listened to leaves crunching, owls hooting, and streams bubbling over smooth stones. "
                    + "One evening, Pip noticed that the crickets were completely silent. "
                    + "He followed the silence until he found a cricket family hiding under a log, afraid of the dark. "
                    + "Pip showed them how the stars made gentle patterns overhead "
                    + "and how the moonlight drew silver lines on the ground. "
                    + "Feeling safe, the crickets began to sing again, filling the forest with music. "
                    + "Pip curled up in his den, happy that he had helped bring the nighttime song back to the world.";

    // Titles
    private static final String TITLE_1 = "Moonbeam Dreams";
    private static final String TITLE_2 = "Leo and the Red Kite";
    private static final String TITLE_3 = "Pip and the Night Song";

    // Current story state
    private ArrayList<String> chunks = new ArrayList<>(); // one sentence per chunk
    private int currentChunkIndex = 0;   // index of CURRENT sentence
    private boolean isPaused = false;
    private boolean isNightMode = false;
    private int currentStoryId = 1;

    // Audio focus
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_time);

        // ---- Bind views ----
        root           = findViewById(R.id.root);
        if (root == null) {
            root = findViewById(R.id.main); // fallback if old id
        }

        btnPlay        = findViewById(R.id.btnPlay);
        btnPause       = findViewById(R.id.btnPause);
        btnStop        = findViewById(R.id.btnStop);
        btnBackToHome  = findViewById(R.id.btnBackToHome);
        btnStory1      = findViewById(R.id.btnStory1);
        btnStory2      = findViewById(R.id.btnStory2);
        btnStory3      = findViewById(R.id.btnStory3);
        rateSeek       = findViewById(R.id.seekRate);
        pitchSeek      = findViewById(R.id.seekPitch);
        statusText     = findViewById(R.id.statusText);
        storyTitleText = findViewById(R.id.storyTitleText);
        toggleNightMode = findViewById(R.id.toggleNightMode);

        // ---- Night mode prefs ----
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isNightMode = prefs.getBoolean(KEY_NIGHT_MODE, false);
        toggleNightMode.setChecked(isNightMode);
        applyTheme();

        toggleNightMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isNightMode = isChecked;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_NIGHT_MODE, isNightMode)
                    .apply();
            applyTheme();
        });

        // SeekBars
        rateSeek.setProgress(10);
        pitchSeek.setProgress(10);

        // Back to home
        btnBackToHome.setOnClickListener(v -> {
            Intent i = new Intent(StoryTimeActivity.this, HomeActivity.class);
            startActivity(i);
            finish();
        });

        // Story buttons
        btnStory1.setOnClickListener(v -> selectStory(1));
        btnStory2.setOnClickListener(v -> selectStory(2));
        btnStory3.setOnClickListener(v -> selectStory(3));

        // Audio focus
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        afChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                pauseTtsInternal(true);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                stopReading();
            }
        };

        // TTS init
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.US);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS language not available. Please install English TTS.", Toast.LENGTH_SHORT).show();
                    statusText.setText("Language data missing — please install TTS.");
                } else {
                    ttsReady = true;
                    statusText.setText("TTS ready. Choose a story and press Play.");
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            runOnUiThread(() -> {
                                if (isPaused) return;

                                // Move to next sentence only AFTER the current one finishes
                                currentChunkIndex++;
                                if (currentChunkIndex < chunks.size()) {
                                    speakCurrentChunk();
                                } else {
                                    abandonAudioFocus();
                                    statusText.setText("Finished.");
                                    currentChunkIndex = 0;
                                    isPaused = false;
                                }
                            });
                        }

                        @Override
                        public void onError(String utteranceId) {
                            runOnUiThread(() -> Toast.makeText(
                                    StoryTimeActivity.this,
                                    "TTS error on chunk: " + utteranceId,
                                    Toast.LENGTH_SHORT
                            ).show());
                        }
                    });
                }
            } else {
                Toast.makeText(this, "Failed to initialize TTS.", Toast.LENGTH_SHORT).show();
                statusText.setText("TTS init failed.");
            }
        });

        // Play/Pause/Stop
        btnPlay.setOnClickListener(v -> onPlayClicked());
        btnPause.setOnClickListener(v -> onPauseClicked());
        btnStop.setOnClickListener(v -> onStopClicked());

        // Default story
        selectStory(1);
    }

    // ---------- Story selection ----------

    private void selectStory(int id) {
        currentStoryId = id;

        String storyText;
        String titleText;

        switch (id) {
            case 2:
                storyText = STORY_2;
                titleText = TITLE_2;
                break;
            case 3:
                storyText = STORY_3;
                titleText = TITLE_3;
                break;
            case 1:
            default:
                storyText = STORY_1;
                titleText = TITLE_1;
                break;
        }

        if (storyTitleText != null) {
            storyTitleText.setText(titleText);
        }

        // Stop any current playback
        if (ttsReady && tts != null) {
            tts.stop();
        }
        abandonAudioFocus();

        // Reset state and re-chunk story INTO SENTENCES
        currentChunkIndex = 0;
        isPaused = false;
        chunks = chunkIntoSentences(storyText);

        statusText.setText("Story " + id + " selected: " + titleText + ". Press Play to start.");
        updateStoryButtonsUI();
    }

    private void updateStoryButtonsUI() {
        btnStory1.setAlpha(currentStoryId == 1 ? 1.0f : 0.6f);
        btnStory2.setAlpha(currentStoryId == 2 ? 1.0f : 0.6f);
        btnStory3.setAlpha(currentStoryId == 3 ? 1.0f : 0.6f);
    }

    // ---------- Theme ----------

    private void applyTheme() {
        if (root == null) return;

        if (isNightMode) {
            root.setBackgroundColor(Color.parseColor("#121212"));
            if (statusText != null) statusText.setTextColor(Color.WHITE);
            if (storyTitleText != null) storyTitleText.setTextColor(Color.WHITE);
            toggleNightMode.setText("☀️");   // sun when in night mode

            tintButtonTextWhite(btnBackToHome, btnPlay, btnPause, btnStop,
                    btnStory1, btnStory2, btnStory3);
        } else {
            root.setBackgroundColor(Color.parseColor("#FFF8E1"));
            if (statusText != null) statusText.setTextColor(Color.parseColor("#4E342E"));
            if (storyTitleText != null) storyTitleText.setTextColor(Color.parseColor("#5D4037"));
            toggleNightMode.setText("🌙");   // moon when in day mode

            tintButtonTextDay(btnBackToHome, btnPlay, btnPause, btnStop,
                    btnStory1, btnStory2, btnStory3);
        }
    }

    private void tintButtonTextWhite(Button... buttons) {
        for (Button b : buttons) {
            if (b != null) b.setTextColor(Color.WHITE);
        }
    }

    private void tintButtonTextDay(Button... buttons) {
        for (Button b : buttons) {
            if (b != null) b.setTextColor(Color.BLACK);
        }
    }

    // ---------- Buttons ----------

    private void onPlayClicked() {
        if (!ttsReady) {
            Toast.makeText(this, "TTS not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (chunks == null || chunks.isEmpty()) {
            Toast.makeText(this, "No story selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        float rate = Math.max(0.1f, rateSeek.getProgress() / 10f);
        float pitch = Math.max(0.1f, pitchSeek.getProgress() / 10f);
        tts.setSpeechRate(rate);
        tts.setPitch(pitch);

        int result = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        );
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Toast.makeText(this, "Could not get audio focus.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPaused) {
            // Resume from the CURRENT sentence
            isPaused = false;
            statusText.setText("Resuming story " + currentStoryId + "...");
            speakCurrentChunk();
        } else {
            // Start from beginning
            isPaused = false;
            currentChunkIndex = 0;
            statusText.setText("Playing story " + currentStoryId + "...");
            speakCurrentChunk();
        }
    }

    private void onPauseClicked() {
        pauseTtsInternal(false);
    }

    private void onStopClicked() {
        stopReading();
    }

    // ---------- TTS helpers ----------

    private void pauseTtsInternal(boolean transientLoss) {
        if (!ttsReady) return;

        if (!isPaused && currentChunkIndex < chunks.size()) {
            isPaused = true;
            // IMPORTANT: do NOT change currentChunkIndex.
            // It still points to the current sentence,
            // which we will replay from the start on resume.
            tts.stop();
            statusText.setText(transientLoss ? "Paused (audio focus lost)" : "Paused.");
        }
    }

    private void stopReading() {
        if (!ttsReady) return;

        isPaused = false;
        currentChunkIndex = 0;
        tts.stop();
        abandonAudioFocus();
        statusText.setText("Stopped.");
    }

    private void speakCurrentChunk() {
        if (!ttsReady) return;

        if (currentChunkIndex < 0 || currentChunkIndex >= chunks.size()) {
            abandonAudioFocus();
            statusText.setText("Finished.");
            currentChunkIndex = 0;
            isPaused = false;
            return;
        }

        String text = chunks.get(currentChunkIndex);
        String utteranceId = "utt-" + currentChunkIndex + "-" + UUID.randomUUID();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager != null && afChangeListener != null) {
            audioManager.abandonAudioFocus(afChangeListener);
        }
    }

    // ---------- Sentence chunking ----------

    /**
     * Split text into sentence-like chunks based on '.', '!' and '?'.
     * This lets us pause/resume at the sentence level.
     */
    private ArrayList<String> chunkIntoSentences(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (text == null) return out;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (c == '.' || c == '!' || c == '?') {
                String chunk = sb.toString().trim();
                if (!chunk.isEmpty()) {
                    out.add(chunk);
                }
                sb.setLength(0);
            }
        }
        // leftover (no terminal punctuation at end)
        String tail = sb.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }

        return out;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        abandonAudioFocus();
    }
}
