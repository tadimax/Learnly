package com.example.learnly;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class StoryTimeActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private Button btnPlay, btnPause, btnStop;
    private SeekBar rateSeek, pitchSeek;
    private TextView statusText;

    private ArrayList<String> chunks = new ArrayList<>();
    private int currentChunkIndex = 0;
    private boolean isPaused = false;

    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    private static final String SAMPLE_STORY =
            "Once upon a time, in a cozy little village, there lived a curious child named Mira. "
                    + "Every night, Mira looked up at the moon and wondered where dreams came from. "
                    + "One evening, a friendly firefly appeared at her window and whispered, ‘Follow me.’ "
                    + "They floated past sleeping trees and over a silver stream that giggled softly. "
                    + "At the edge of a meadow, the firefly showed Mira a glowing door made of starlight. "
                    + "When she stepped through, the sky filled with gentle music and warm colors, "
                    + "like the world was wrapped in a soft blanket. The stars hummed lullabies, "
                    + "and the moon smiled kindly. ‘Dreams are stories you tell your heart,’ the firefly said. "
                    + "Mira thanked her new friend, drifted home on a moonbeam, and fell asleep, "
                    + "knowing that every night held a new story waiting to be told. The end.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_time);

        btnPlay = findViewById(R.id.btnPlay);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        rateSeek = findViewById(R.id.seekRate);
        pitchSeek = findViewById(R.id.seekPitch);
        statusText = findViewById(R.id.statusText);

        rateSeek.setProgress(10);
        pitchSeek.setProgress(10);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        afChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                pauseTtsInternal(true);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                stopReading();
            }
        };

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.US);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS language not available. Installing...", Toast.LENGTH_SHORT).show();
                    Intent install = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(install);
                    statusText.setText("Language data missing—prompted install.");
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
                            runOnUiThread(() -> Toast.makeText(StoryTimeActivity.this,
                                    "TTS error on chunk: " + utteranceId, Toast.LENGTH_SHORT).show());
                        }
                    });
                }
            } else {
                Toast.makeText(this, "Failed to initialize TTS.", Toast.LENGTH_SHORT).show();
                statusText.setText("TTS init failed.");
            }
        });

        btnPlay.setOnClickListener(v -> onPlay());
        btnPause.setOnClickListener(v -> handlePauseClick());

        btnStop.setOnClickListener(v -> handleStopClick());


        chunks = chunkText(SAMPLE_STORY, 3500);
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
            statusText.setText("Playing...");
            speakFromCurrentIndex();
        }
    }

    private void handlePauseClick() { pauseTtsInternal(false); }
    // change this:
    protected void onStop() { stopReading(); }

    // to something like:
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
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        abandonAudioFocus();
    }
}
