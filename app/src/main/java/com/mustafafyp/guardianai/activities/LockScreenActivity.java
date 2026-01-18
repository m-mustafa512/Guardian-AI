package com.mustafafyp.guardianai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.mustafafyp.guardianai.R;

public class LockScreenActivity extends AppCompatActivity {

    private String blockedPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);

        // 1. Get the package name of the app we just blocked
        if (getIntent().hasExtra("PACKAGE_NAME")) {
            blockedPackage = getIntent().getStringExtra("PACKAGE_NAME");
        }

        // 2. Prevent the user from just pressing "Back" to enter the app
        // (We override onBackPressed below)

        Button btnSolve = findViewById(R.id.btnSolveQuiz);
        btnSolve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Go to the Quiz!
                Intent intent = new Intent(LockScreenActivity.this, QuizActivity.class);
                intent.putExtra("PACKAGE_NAME", blockedPackage); // Pass the package name along
                startActivity(intent);
                finish(); // Close this lock screen
            }
        });
    }

    // 3. DISABLE BACK BUTTON
    // If they press back, we go to the Home Screen, NOT the blocked app.
    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }
}