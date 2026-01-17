package com.mustafafyp.guardianai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.services.MainForegroundService; // Ensure this import matches your service location
import java.util.Random;

public class BlockedAppActivity extends AppCompatActivity {

	private TextView txtQuestion, txtBlockedAppName;
	private RadioGroup radioGroupOptions;
	private RadioButton rbOption1, rbOption2, rbOption3;
	private Button btnSubmitAnswer;

	private int correctAnswer;
	private String blockedPackage;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blocked_app);

		// Bind Views
		txtQuestion = findViewById(R.id.txtQuestion);
		txtBlockedAppName = findViewById(R.id.txtBlockedAppName);
		radioGroupOptions = findViewById(R.id.radioGroupOptions);
		rbOption1 = findViewById(R.id.rbOption1);
		rbOption2 = findViewById(R.id.rbOption2);
		rbOption3 = findViewById(R.id.rbOption3);
		btnSubmitAnswer = findViewById(R.id.btnSubmitAnswer);

		// Get Data
		if (getIntent().hasExtra(MainForegroundService.BLOCKED_APP_NAME_EXTRA)) {
			blockedPackage = getIntent().getStringExtra(MainForegroundService.BLOCKED_APP_NAME_EXTRA);
			txtBlockedAppName.setText("Locked: " + blockedPackage);
		}

		generateQuestion();

		btnSubmitAnswer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				checkAnswer();
			}
		});
	}

	private void generateQuestion() {
		Random random = new Random();
		int a = random.nextInt(10) + 1;
		int b = random.nextInt(10) + 1;
		correctAnswer = a + b;

		txtQuestion.setText(a + " + " + b + " = ?");

		// Randomize options
		int correctIndex = random.nextInt(3);
		RadioButton[] options = {rbOption1, rbOption2, rbOption3};

		for (int i = 0; i < 3; i++) {
			if (i == correctIndex) {
				options[i].setText(String.valueOf(correctAnswer));
			} else {
				int wrong = correctAnswer + (random.nextInt(10) - 5);
				if (wrong == correctAnswer) wrong++;
				options[i].setText(String.valueOf(wrong));
			}
		}
	}

	private void checkAnswer() {
		int selectedId = radioGroupOptions.getCheckedRadioButtonId();
		if (selectedId == -1) {
			Toast.makeText(this, "Select an answer!", Toast.LENGTH_SHORT).show();
			return;
		}

		RadioButton selectedRb = findViewById(selectedId);
		int answer = Integer.parseInt(selectedRb.getText().toString());

		if (answer == correctAnswer) {
			Toast.makeText(this, "Correct! Unlocking...", Toast.LENGTH_SHORT).show();

			// Send Unlock Signal to Service (You need to handle this in your Service!)
			Intent intent = new Intent(this, MainForegroundService.class);
			intent.setAction("ACTION_UNLOCK_APP");
			intent.putExtra("PACKAGE_NAME", blockedPackage);
			startService(intent);

			// Close this blocking screen
			finish();
		} else {
			Toast.makeText(this, "Wrong! Try again.", Toast.LENGTH_SHORT).show();
			generateQuestion(); // New question
		}
	}

	@Override
	public void onBackPressed() {
		// Disable Back Button so they can't escape
		Intent startMain = new Intent(Intent.ACTION_MAIN);
		startMain.addCategory(Intent.CATEGORY_HOME);
		startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(startMain);
	}
}