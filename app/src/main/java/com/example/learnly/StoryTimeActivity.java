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
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class StoryTimeActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "story_time_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private Button btnPlay, btnPause, btnStop;
    private Button btnStory1, btnStory2, btnStory3, btnBackToHome, btnNightMode;
    private SeekBar rateSeek, pitchSeek;
    private TextView statusText, storyTitleText, storyDescriptionText;
    private LinearLayout rootLayout;

    private ArrayList<String> chunks = new ArrayList<>();
    private int currentChunkIndex = 0;
    private boolean isPaused = false;

    private int currentStoryIndex = 0;

    private boolean isNightMode = false;

    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    // ---------- Original short children’s stories (no copyright issues) ----------

    private static final String[] STORY_TITLES = new String[]{
            "Mira and the Moon Door",
            "The Brave Little Cloud",
            "Zuzu the Playground Robot"
    };

    private static final String[] STORY_DESCRIPTIONS = new String[]{
            "Mira follows a firefly to a magical door in the sky.",
            "A shy cloud learns to be brave and bring gentle rain.",
            "A tiny robot discovers the joy of playing with friends."
    };

    private static final String[] STORY_TEXTS = new String[]{
            // 1) Mira and the Moon Door
            "Once upon a time, in a cozy little village, there lived a curious child named Mira. "
                    + "Every night, Mira looked up at the moon and wondered where dreams came from. "
                    + "One evening, a friendly firefly appeared at her window and whispered, \"Follow me.\" "
                    + "Mira tiptoed outside and followed the tiny light past sleepy trees and over a silver stream "
                    + "that giggled softly over the stones. "
                    + "At the edge of a quiet meadow, the firefly showed Mira a glowing door made of moonlight. "
                    + "When Mira stepped through, the sky filled with gentle music and warm colors, "
                    + "like the whole world was wrapped in a soft blanket. "
                    + "The stars hummed lullabies, and the moon smiled kindly at her. "
                    + "“Dreams are stories you tell your heart,” the firefly said. "
                    + "Mira thanked her new friend, drifted home on a moonbeam, and climbed into bed. "
                    + "That night, she dreamed the sweetest dreams, knowing that every night held a new story "
                    + "waiting to be told. The end.",
            // 2) The Brave Little Cloud
            "High above a busy town floated a tiny cloud named Puff. "
                    + "Puff was soft and fluffy, but also very shy. "
                    + "He watched the big, tall clouds roll by, bringing rain and shade and rainbow colors. "
                    + "“I am too small to help,” Puff sighed, drifting quietly across the bright blue sky. "
                    + "One hot summer day, the people in the town felt tired and sticky. "
                    + "Flowers drooped, and even the birds were quiet. "
                    + "Puff felt a tiny tickle inside, like raindrops wanting to dance. "
                    + "He wiggled and wobbled and puffed himself up as big as he could. "
                    + "“I will try,” he whispered bravely. "
                    + "Puff gathered all his courage, squeezed his eyes shut, and let the raindrops fall. "
                    + "Soft, cool rain sprinkled the town. Children laughed and spun in the puddles. "
                    + "Flowers lifted their heads. A rainbow stretched across the sky like a happy smile. "
                    + "“You did it!” the other clouds cheered. "
                    + "Puff wasn’t just a tiny cloud anymore. He was the brave little cloud who saved a hot summer day. "
                    + "From then on, whenever the town needed gentle rain, Puff knew exactly what to do. The end.",
            // 3) Zuzu the Playground Robot
            "In a quiet workshop at the edge of the city, a small robot named Zuzu blinked on for the very first time. "
                    + "Zuzu had shiny wheels, a round screen for a face, and a heart-shaped light that glowed softly. "
                    + "The inventor smiled and said, “Zuzu, your job is to help kids have fun at the playground.” "
                    + "Zuzu beeped happily. He rolled out to the playground and saw swings, slides, and a sandbox full of toys. "
                    + "Children were running and laughing, but Zuzu stayed still. "
                    + "“What if I am not fun?” he worried. "
                    + "A little girl noticed him and waved. “Hi, I’m Asha! Do you want to play?” "
                    + "Zuzu’s heart light blinked brighter. “I do not know how,” he said softly. "
                    + "Asha giggled. “It’s easy. We can learn together.” "
                    + "She showed Zuzu how to gently push the swings and how to draw smiley faces in the sand with his wheels. "
                    + "Zuzu used his screen to show silly pictures, and the kids laughed even more. "
                    + "Soon there was a whole line of children waiting for a turn with their new robot friend. "
                    + "As the sun began to set, Zuzu’s heart-shaped light glowed warm and bright. "
                    + "“Playing is just sharing happy moments,” he realized. "
                    + "Every day after that, Zuzu rolled to the playground, ready to make new games and new friends. The end."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_time);

        // --- Bind views ---
        rootLayout          = findViewById(R.id.root);
        statusText          = findViewById(R.id.statusText);
        storyTitleText      = findViewById(R.id.storyTitleText);
        storyDescriptionText= findViewById(R.id.storyDescriptionText);

        btnPlay             = findViewById(R.id.btnPlay);
        btnPause            = findViewById(R.id.btnPause);
        btnStop             = findViewById(R.id.btnStop);

        btnStory1           = findViewById(R.id.btnStory1);
        btnStory2           = findViewById(R.id.btnStory2);
        btnStory3           = findViewById(R.id.btnStory3);

        btnBackToHome       = findViewById(R.id.btnBackToHome);
        btnNightMode        = findViewById(R.id.btnNightMode);

        rateSeek            = findViewById(R.id.seekRate);
        pitchSeek           = findViewById(R.id.seekPitch);

        // default sliders
        rateSeek.setProgress(10);
        pitchSeek.setProgress(10);

        // --- Load night mode from SharedPreferences ---
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isNightMode = prefs.getBoolean(KEY_NIGHT_MODE, false);
        applyTheme();   // apply colors according to saved mode

        // --- Back to home ---
        btnBackToHome.setOnClickListener(v -> finish());

        // --- Night mode toggle ---
        btnNightMode.setOnClickListener(v -> {
            isNightMode = !isNightMode;
            // save to SharedPreferences
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(KEY_NIGHT_MODE, isNightMode);
            editor.apply();
            applyTheme();
        });

        // --- Audio focus setup ---
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        afChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                pauseTtsInternal(true);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                stopReading();
            }
        };

        // --- TTS init ---
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.US);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this,
                            "TTS language not available. Installing...", Toast.LENGTH_SHORT).show();
                    Intent install = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(install);
                    statusText.setText("Language data missing — prompted install.");
                } else {
                    ttsReady = true;
                    statusText.setText("TTS ready.");
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) { }

                        @Override
                        public void onDone(String utteranceId) {
                            runOnUiThread(() -> {
                                if (!isPaused && currentChunkIndex < chunks.size()) {
                                    speakFromCurrentIndex();
                                } else if (currentChunkIndex >= chunks.size()) {
                                    abandonAudioFocus();
                                    statusText.setText("Finished.");
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

        // --- Story buttons ---
        btnStory1.setOnClickListener(v -> loadStory(0));
        btnStory2.setOnClickListener(v -> loadStory(1));
        btnStory3.setOnClickListener(v -> loadStory(2));

        // --- Control buttons ---
        btnPlay.setOnClickListener(v -> onPlay());
        btnPause.setOnClickListener(v -> handlePauseClick());
        btnStop.setOnClickListener(v -> handleStopClick());

        // Load first story by default
        loadStory(0);
    }

    // Apply day/night colors and toggle text
    private void applyTheme() {
        if (rootLayout == null) return;

        if (isNightMode) {
            rootLayout.setBackgroundColor(Color.parseColor("#101822"));
            storyTitleText.setTextColor(Color.parseColor("#FFFFFF"));
            storyDescriptionText.setTextColor(Color.parseColor("#CFD8DC"));
            statusText.setTextColor(Color.parseColor("#CFD8DC"));
            btnNightMode.setText("☀ Day");
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#FFF8E1"));
            storyTitleText.setTextColor(Color.parseColor("#5D4037"));
            storyDescriptionText.setTextColor(Color.parseColor("#6D4C41"));
            statusText.setTextColor(Color.parseColor("#5D4037"));
            btnNightMode.setText("🌙 Night");
        }
    }

    // Choose a story, reset state & chunks
    private void loadStory(int index) {
        if (index < 0 || index >= STORY_TEXTS.length) index = 0;
        currentStoryIndex = index;

        if (ttsReady) tts.stop();
        abandonAudioFocus();
        isPaused = false;
        currentChunkIndex = 0;

        String fullText = STORY_TEXTS[currentStoryIndex];
        chunks = chunkText(fullText, 3500);

        storyTitleText.setText(STORY_TITLES[currentStoryIndex]);
        storyDescriptionText.setText(STORY_DESCRIPTIONS[currentStoryIndex]);
        statusText.setText("Ready: " + STORY_TITLES[currentStoryIndex]);
    }

    private void onPlay() {
        if (!ttsReady) {
            Toast.makeText(this, "TTS not ready yet.", Toast.LENGTH_SHORT).show();
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
            isPaused = false;
            statusText.setText("Resuming...");
            speakFromCurrentIndex();
        } else {
            currentChunkIndex = 0;
            statusText.setText("Playing: " + STORY_TITLES[currentStoryIndex]);
            speakFromCurrentIndex();
        }
    }

    private void handlePauseClick() { pauseTtsInternal(false); }

    private void handleStopClick() { stopReading(); }

    private void pauseTtsInternal(boolean transientLoss) {
        if (!ttsReady) return;
        if (!isPaused && currentChunkIndex < chunks.size()) {
            isPaused = true;
            tts.stop();
            statusText.setText(transientLoss ? "Paused (focus lost)" : "Paused.");
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

    private void speakFromCurrentIndex() {
        if (!ttsReady) return;
        if (currentChunkIndex >= chunks.size()) {
            abandonAudioFocus();
            statusText.setText("Finished.");
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

        currentChunkIndex++;
    }

    private void abandonAudioFocus() {
        if (audioManager != null && afChangeListener != null) {
            audioManager.abandonAudioFocus(afChangeListener);
        }
    }

    private ArrayList<String> chunkText(String text, int maxLen) {
        ArrayList<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        if (text.length() <= maxLen) {
            out.add(text);
            return out;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            int boundary = -1;
            for (int i = end - 1; i > start; i--) {
                char c = text.charAt(i);
                if (c == '.' || c == '!' || c == '?') {
                    boundary = i + 1;
                    break;
                }
            }
            if (boundary == -1 || boundary <= start) boundary = end;
            out.add(text.substring(start, boundary).trim());
            start = boundary;
        }
        return out;
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseTtsInternal(true);
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
