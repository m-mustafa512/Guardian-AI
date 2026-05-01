package com.mustafafyp.guardianai.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import java.util.Random;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.widget.ImageView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.models.Child;
import com.mustafafyp.guardianai.dialogfragments.InformationDialogFragment;
import com.mustafafyp.guardianai.dialogfragments.PasswordValidationDialogFragment;
import com.mustafafyp.guardianai.dialogfragments.PermissionExplanationDialogFragment;
import com.mustafafyp.guardianai.interfaces.OnPasswordValidationListener;
import com.mustafafyp.guardianai.interfaces.OnPermissionExplanationListener;
import com.mustafafyp.guardianai.services.MainForegroundService;
import com.mustafafyp.guardianai.utils.Constant;
import com.mustafafyp.guardianai.utils.SharedPrefsUtils;
import com.mustafafyp.guardianai.utils.Validators;

public class ChildSignedInActivity extends AppCompatActivity implements OnPermissionExplanationListener, OnPasswordValidationListener {
	public static final int JOB_ID = 38;
	public static final String CHILD_EMAIL = "childEmail";
	private static final String TAG = "ChildSignedInTAG";
	private FirebaseAuth auth;
	private FirebaseUser user;
	private ImageButton btnBack;
	private ImageButton btnSettings;
	private TextView txtTitle;
	private androidx.appcompat.widget.Toolbar toolbar;
	private ValueEventListener aiStatusListener;
	private String childUidForAiDot;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_child_signed_in);
		
		boolean childFirstLaunch = SharedPrefsUtils.getBooleanPreference(this, Constant.CHILD_FIRST_LAUNCH, true);
		if (childFirstLaunch) startActivity(new Intent(this, PermissionsActivity.class));
		else {
			
			auth = FirebaseAuth.getInstance();
			user = auth.getCurrentUser();
			
			String email = user.getEmail();
            /*PersistableBundle bundle = new PersistableBundle();
            bundle.putString(CHILD_EMAIL, email);*/
			
			toolbar = findViewById(R.id.action_bar);
			//Button logic removed as we have new layout with menu button in toolbar
			//btnBack = findViewById(R.id.btnBack);
			//btnBack.setImageDrawable(getResources().getDrawable(R.drawable.ic_home_));
			btnSettings = findViewById(R.id.btnSettings);
			btnSettings.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startPasswordValidationDialogFragment();
				}
			});
			txtTitle = findViewById(R.id.txtTitle);
			txtTitle.setText(getString(R.string.home));
			
			//schedualJob(bundle);
			startMainForegroundService(email);
			
			if (!Validators.isLocationOn(this)) startPermissionExplanationDialogFragment();
			
			if (!Validators.isInternetAvailable(this))
				startInformationDialogFragment(getResources().getString(R.string.you_re_offline_ncheck_your_connection_and_try_again));
			
			setupRealtimeListeners(email);
			displayRandomSafetyTip();
			setupSOSButton(email);
			setupAiStatusDot(email);
		}
	}

	private void setupSOSButton(String email) {
		com.google.android.material.floatingactionbutton.FloatingActionButton fabSOS = findViewById(R.id.fabSOS);
		if (fabSOS == null) return;
		
		fabSOS.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Show confirmation
				new android.app.AlertDialog.Builder(ChildSignedInActivity.this)
					.setTitle("Send SOS Alert?")
					.setMessage("This will immediately notify your parents with your current location.")
					.setPositiveButton("Send SOS", (dialog, which) -> {
						sendSOSAlert(email);
					})
					.setNegativeButton("Cancel", null)
					.show();
			}
		});
	}

	private void sendSOSAlert(String email) {
		android.util.Log.i("SOS_DEBUG", "sendSOSAlert called with email: " + email);
		com.google.firebase.database.DatabaseReference ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users/childs");
		ref.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
			@Override
			public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
				android.util.Log.i("SOS_DEBUG", "Firebase query returned, exists: " + snapshot.exists());
				if (snapshot.exists()) {
					String uid = snapshot.getChildren().iterator().next().getKey();
					android.util.Log.i("SOS_DEBUG", "Found child UID: " + uid);
					if (uid != null) {
						// Push alert
						com.mustafafyp.guardianai.models.Alert sosAlert = new com.mustafafyp.guardianai.models.Alert(
							"SOS Emergency",
							"Child has triggered an SOS alert!",
							System.currentTimeMillis()
						);
						com.google.firebase.database.FirebaseDatabase.getInstance()
							.getReference("users/childs/" + uid + "/alerts")
							.push()
							.setValue(sosAlert)
							.addOnSuccessListener(v -> android.util.Log.i("SOS_DEBUG", "SOS Alert written SUCCESSFULLY"))
							.addOnFailureListener(e -> android.util.Log.e("SOS_DEBUG", "SOS Alert write FAILED", e));
						
						Toast.makeText(ChildSignedInActivity.this, "SOS Alert Sent!", Toast.LENGTH_SHORT).show();
					}
				} else {
					android.util.Log.e("SOS_DEBUG", "No child found with email: " + email);
				}
			}

			@Override
			public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
				android.util.Log.e("SOS_DEBUG", "Firebase query cancelled", error.toException());
			}
		});
	}


	private void setupRealtimeListeners(String email) {
		com.google.firebase.database.DatabaseReference ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users/childs");
		ref.orderByChild("email").equalTo(email).addValueEventListener(new com.google.firebase.database.ValueEventListener() {
			@Override
			public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
				if (snapshot.exists()) {
					com.google.firebase.database.DataSnapshot childNode = snapshot.getChildren().iterator().next();
					Child child = childNode.getValue(Child.class);
					if (child != null) {
						updateDashboardUI(child);
					}
				}
			}

			@Override
			public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
		});
	}

	private void updateDashboardUI(Child child) {
		TextView txtStatus = findViewById(R.id.txtStatus); 
		TextView txtStatusDesc = findViewById(R.id.txtStatusDesc);
		ImageView imgStatus = findViewById(R.id.imgStatus);
		TextView txtGreeting = findViewById(R.id.txtGreeting);
		TextView txtCurrentDate = findViewById(R.id.txtCurrentDate);

		if (txtStatus == null || child.getScreenLock() == null) return;

		// Update Greeting
		if (txtGreeting != null) {
			java.util.Calendar c = java.util.Calendar.getInstance();
			int timeOfDay = c.get(java.util.Calendar.HOUR_OF_DAY);
			String greeting;
			if (timeOfDay < 12) greeting = "Good Morning, ";
			else if (timeOfDay < 16) greeting = "Good Afternoon, ";
			else greeting = "Good Evening, ";
			
			txtGreeting.setText(greeting + child.getName() + "!");
		}

		// Update Date
		if (txtCurrentDate != null) {
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, MMMM dd", java.util.Locale.getDefault());
			txtCurrentDate.setText(sdf.format(new java.util.Date()));
		}

		if (child.getScreenLock().isLocked()) {
			txtStatus.setText("Device is Locked");
			txtStatus.setTextColor(android.graphics.Color.RED);
			if (imgStatus != null) imgStatus.setColorFilter(android.graphics.Color.RED);
			txtStatusDesc.setText("Your parents have temporarily locked this device.");
		} else if (child.getLocation() != null && child.getLocation().isOutOfFence()) {
			txtStatus.setText("Outside Safe Zone");
			txtStatus.setTextColor(android.graphics.Color.parseColor("#FBC02D")); // Amber
			if (imgStatus != null) imgStatus.setColorFilter(android.graphics.Color.parseColor("#FBC02D"));
			txtStatusDesc.setText("You have left the area designated as safe by your parents.");
		} else {
			txtStatus.setText("Device is Protected");
			txtStatus.setTextColor(android.graphics.Color.parseColor("#43A047"));
			if (imgStatus != null) imgStatus.setColorFilter(android.graphics.Color.parseColor("#43A047"));
			txtStatusDesc.setText("Your parents are keeping you safe online.");
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateScreenTime();
	}

	private void updateScreenTime() {
		TextView txtScreenTime = findViewById(R.id.txtScreenTime);
		if (txtScreenTime == null) return;

		try {
			android.app.usage.UsageStatsManager usageStatsManager = (android.app.usage.UsageStatsManager) getSystemService(android.content.Context.USAGE_STATS_SERVICE);
			if (usageStatsManager == null) {
				txtScreenTime.setText("N/A");
				return;
			}

			long endTime = System.currentTimeMillis();
			long startTime = endTime - (1000 * 60 * 60 * 24); // 24 hours
			java.util.List<android.app.usage.UsageStats> stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

			long totalTime = 0;
			if (stats != null) {
				for (android.app.usage.UsageStats usageStats : stats) {
					totalTime += usageStats.getTotalTimeInForeground();
				}
			}

			long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(totalTime);
			long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalTime) % 60;
			txtScreenTime.setText(String.format("%dh %dm", hours, minutes));

		} catch (Exception e) {
			e.printStackTrace();
			txtScreenTime.setText("Error");
		}
	}

	private void displayRandomSafetyTip() {
		TextView txtSafetyTip = findViewById(R.id.txtSafetyTip);
		if (txtSafetyTip == null) return;

		String[] tips = getResources().getStringArray(R.array.safety_tips);
		if (tips.length > 0) {
			int randomIndex = new Random().nextInt(tips.length);
			txtSafetyTip.setText(tips[randomIndex]);
		}
	}

	// ── AI Status Dot ─────────────────────────────────────────────────────────

	private void setupAiStatusDot(String email) {
		View dot = findViewById(R.id.viewAiStatusDot);
		if (dot == null) return;

		// Lookup child UID by email, then listen to aiStatus
		FirebaseDatabase.getInstance().getReference("users/childs")
				.orderByChild("email").equalTo(email)
				.addListenerForSingleValueEvent(new ValueEventListener() {
					@Override
					public void onDataChange(@NonNull DataSnapshot snapshot) {
						if (!snapshot.exists()) return;
						childUidForAiDot = snapshot.getChildren().iterator().next().getKey();
						if (childUidForAiDot == null) return;

						aiStatusListener = new ValueEventListener() {
							@Override
							public void onDataChange(@NonNull DataSnapshot s) {
								Object rawAnomaly = s.child("isAnomaly").getValue();
								boolean isAnomaly = Boolean.TRUE.equals(rawAnomaly);
								runOnUiThread(() -> dot.setBackgroundTintList(
										android.content.res.ColorStateList.valueOf(
												isAnomaly
														? Color.parseColor("#D32F2F")
														: Color.parseColor("#43A047"))));
							}
							@Override
							public void onCancelled(@NonNull DatabaseError e) {}
						};

						FirebaseDatabase.getInstance()
								.getReference("users/childs/" + childUidForAiDot + "/aiStatus")
								.addValueEventListener(aiStatusListener);
					}
					@Override
					public void onCancelled(@NonNull DatabaseError e) {}
				});
	}
	
	private void startMainForegroundService(String email) {
		Intent intent = new Intent(this, MainForegroundService.class);
		intent.putExtra(CHILD_EMAIL, email);
		ContextCompat.startForegroundService(this, intent);
		
	}
	
	private void startPermissionExplanationDialogFragment() {
		PermissionExplanationDialogFragment permissionExplanationDialogFragment = new PermissionExplanationDialogFragment();
		Bundle bundle = new Bundle();
		bundle.putInt(Constant.PERMISSION_REQUEST_CODE, Constant.CHILD_LOCATION_PERMISSION_REQUEST_CODE);
		permissionExplanationDialogFragment.setArguments(bundle);
		permissionExplanationDialogFragment.setCancelable(false);
		permissionExplanationDialogFragment.show(getSupportFragmentManager(), Constant.PERMISSION_EXPLANATION_FRAGMENT_TAG);
	}
	
	private void startInformationDialogFragment(String message) {
		InformationDialogFragment informationDialogFragment = new InformationDialogFragment();
		Bundle bundle = new Bundle();
		bundle.putString(Constant.INFORMATION_MESSAGE, message);
		informationDialogFragment.setArguments(bundle);
		informationDialogFragment.setCancelable(false);
		informationDialogFragment.show(getSupportFragmentManager(), Constant.INFORMATION_DIALOG_FRAGMENT_TAG);
	}
	
	private void startPasswordValidationDialogFragment() {
		PasswordValidationDialogFragment passwordValidationDialogFragment = new PasswordValidationDialogFragment();
		passwordValidationDialogFragment.setCancelable(false);
		passwordValidationDialogFragment.show(getSupportFragmentManager(), Constant.PASSWORD_VALIDATION_DIALOG_FRAGMENT_TAG);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == Constant.DEVICE_ADMIN_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				Log.i(TAG, "onActivityResult: DONE");
			}
		}
	}
	
	@Override
	public void onBackPressed() {
		// Don't go back to LoginActivity, just minimize the app
		moveTaskToBack(true);
	}
	
	@Override
	public void onOk(int requestCode) {
		startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
	}
	
	@Override
	public void onCancel(int switchId) {
		Toast.makeText(this, getString(R.string.canceled), Toast.LENGTH_SHORT).show();
		
	}
	
	@Override
	public void onValidationOk() {
		Intent intent = new Intent(ChildSignedInActivity.this, SettingsActivity.class);
		startActivity(intent);
	}
	
    /*@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void schedualJob(PersistableBundle bundle) {
        ComponentName componentName = new ComponentName(this, UploadAppsService.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000)
                .setExtras(bundle)
                .build();
        JobScheduler jobScheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        int resultCode = jobScheduler.schedule(jobInfo);

        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            //Success
        } else {
            //Failure
        }
    }*/

    /*@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void cancelJob() {
        JobScheduler jobScheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(JOB_ID);
        //Job cancelled
    }*/
	
	
}
