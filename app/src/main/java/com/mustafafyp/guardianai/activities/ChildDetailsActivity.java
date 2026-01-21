package com.mustafafyp.guardianai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.fragments.ActivityLogFragment;
import com.mustafafyp.guardianai.fragments.AppsFragment;
import com.mustafafyp.guardianai.fragments.LocationFragment;
import com.mustafafyp.guardianai.fragments.ScreenTimeFragment;
import com.mustafafyp.guardianai.models.App;

import java.util.ArrayList;

import static com.mustafafyp.guardianai.activities.ParentSignedInActivity.APPS_EXTRA;
import static com.mustafafyp.guardianai.activities.ParentSignedInActivity.CHILD_EMAIL_EXTRA;
import static com.mustafafyp.guardianai.activities.ParentSignedInActivity.CHILD_NAME_EXTRA;

public class ChildDetailsActivity extends AppCompatActivity {
	private static final String TAG = "ChildDetailsTAG";
	private ArrayList<App> apps;
	private ImageButton btnBack;
	private ImageButton btnAlerts;
	private TextView txtTitle;
	private String childEmail;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_child_details);
		
		btnBack = findViewById(R.id.btnBack);
		btnBack.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onBackPressed();
			}
		});
		btnAlerts = findViewById(R.id.btnSettings); // Repurposing settings button for alerts
		btnAlerts.setImageResource(R.drawable.ic_bell); // Use a bell icon
		btnAlerts.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				android.util.Log.i("ALERTS_DEBUG", "Alerts button clicked, childEmail: " + childEmail);
				if (childEmail != null) {
					Intent alertsIntent = new Intent(ChildDetailsActivity.this, AlertsActivity.class);
					alertsIntent.putExtra(AlertsActivity.CHILD_EMAIL_EXTRA, childEmail);
					startActivity(alertsIntent);
				} else {
					android.util.Log.e("ALERTS_DEBUG", "Cannot open alerts - childEmail is null!");
				}
			}
		});
		txtTitle = findViewById(R.id.txtTitle);
		
		Intent intent = getIntent();
		String childName = intent.getStringExtra(CHILD_NAME_EXTRA);
		childEmail = intent.getStringExtra(CHILD_EMAIL_EXTRA);
		android.util.Log.i("ALERTS_DEBUG", "ChildDetailsActivity received childEmail: " + childEmail);
		apps = intent.getParcelableArrayListExtra(APPS_EXTRA);
        /*for (App app : apps) {
            Log.i(TAG, "onItemClick: appName: " + app.getAppName() + " " + "packageName" + app.getPackageName());

        }*/
		
		//setTitle(childName + "'s device");
		String title = childName + getString(R.string.upper_dot_s) + " " + getString(R.string.device);
		txtTitle.setText(title);
		
		getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new AppsFragment()).commit();
		
		BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
		bottomNav.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
			@Override
			public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
				Fragment selectedFragment = null;
				
				Bundle bundle = new Bundle();

				int id = menuItem.getItemId();

				if (id == R.id.navApps) {
					selectedFragment = new AppsFragment();
					//bundle.putParcelableArrayList(APPS_EXTRA, apps);  //not needed since we're sending it from
					//selectedFragment.setArguments(bundle);            //the ParentSignedInActivity

				} else if (id == R.id.navLocation) {
					selectedFragment = new LocationFragment();
					//bundle.putString(CHILD_EMAIL_EXTRA, childEmail);
					//selectedFragment.setArguments(bundle);

				} else if (id == R.id.navActivityLog) {
					selectedFragment = new ActivityLogFragment();

				} else if (id == R.id.navScreenTime) {
					selectedFragment = new ScreenTimeFragment();
				}


				getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, selectedFragment).commit();
				return true;
			}
		});
	}
	
	@Override
	public void onBackPressed() {
		startActivity(new Intent(this, ParentSignedInActivity.class));
	}
}
