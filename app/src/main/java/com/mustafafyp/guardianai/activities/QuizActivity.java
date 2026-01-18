package com.mustafafyp.guardianai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.mustafafyp.guardianai.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class QuizActivity extends AppCompatActivity {

    private TextView txtQuestion;
    private Button btnOpt1, btnOpt2, btnOpt3, btnOpt4;
    private String blockedPackage;
    private int correctAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        // 1. Get the locked package name
        if (getIntent().hasExtra("PACKAGE_NAME")) {
            blockedPackage = getIntent().getStringExtra("PACKAGE_NAME");
        }

        txtQuestion = findViewById(R.id.txtQuestion);
        btnOpt1 = findViewById(R.id.btnOption1);
        btnOpt2 = findViewById(R.id.btnOption2);
        btnOpt3 = findViewById(R.id.btnOption3);
        btnOpt4 = findViewById(R.id.btnOption4);

        generateMathQuestion();
    }

    private void generateMathQuestion() {
        Random random = new Random();
        int a = random.nextInt(20) + 1; // Number between 1-20
        int b = random.nextInt(20) + 1;
        correctAnswer = a + b;

        txtQuestion.setText("What is " + a + " + " + b + "?");

        // Create options
        ArrayList<Integer> options = new ArrayList<>();
        options.add(correctAnswer);
        options.add(correctAnswer + random.nextInt(5) + 1); // Wrong answer 1
        options.add(correctAnswer - random.nextInt(5) - 1); // Wrong answer 2
        options.add(correctAnswer + 10);                   // Wrong answer 3

        // Shuffle options so the answer isn't always the first button
        Collections.shuffle(options);

        // Assign to buttons
        setupButton(btnOpt1, options.get(0));
        setupButton(btnOpt2, options.get(1));
        setupButton(btnOpt3, options.get(2));
        setupButton(btnOpt4, options.get(3));
    }

    private void setupButton(Button btn, final int value) {
        btn.setText(String.valueOf(value));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (value == correctAnswer) {
                    unlockApp();
                } else {
                    Toast.makeText(QuizActivity.this, "Wrong! Try again.", Toast.LENGTH_SHORT).show();
                    generateMathQuestion(); // Generate a new question
                }
            }
        });
    }

    private void unlockApp() {
        Toast.makeText(this, "Correct! App Unlocked.", Toast.LENGTH_SHORT).show();

        // 1. Send signal to Background Service to unblock this app
        Intent intent = new Intent(this, com.mustafafyp.guardianai.services.MainForegroundService.class);
        intent.setAction("ACTION_UNLOCK_APP");
        intent.putExtra("PACKAGE_NAME", blockedPackage);
        startService(intent);

        // 2. Close Quiz and Lock Screen
        finish();
    }

    // Disable Back Button to prevent cheating
    @Override
    public void onBackPressed() {
        // Do nothing
    }
}