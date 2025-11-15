package com.example.learnly;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class LevelSelectActivity extends AppCompatActivity {

    Button btnLevel1, btnLevel2, btnLevel3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.spelling_activity_level_select);

        btnLevel1 = findViewById(R.id.btnLevel1);
        btnLevel2 = findViewById(R.id.btnLevel2);
        btnLevel3 = findViewById(R.id.btnLevel3);

        btnLevel1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openLevel(0); // Level 1
            }
        });

        btnLevel2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openLevel(1); // Level 2
            }
        });

        btnLevel3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openLevel(2); // Level 3
            }
        });
    }

    private void openLevel(int index) {
        Intent intent = new Intent(LevelSelectActivity.this, SpellingGameActivity.class);
        intent.putExtra("LEVEL_INDEX", index);
        startActivity(intent);
    }
}
