package com.mustafafyp.guardianai.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.adapters.UsageAdapter;
import com.mustafafyp.guardianai.models.App;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class UsageReportActivity extends AppCompatActivity {

    public static final String APPS_EXTRA = "com.mustafafyp.guardianai.APPS_EXTRA";
    private RecyclerView recyclerViewUsage;
    private TextView txtTotalTime;
    private ArrayList<App> apps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_report);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        txtTotalTime = findViewById(R.id.txtTotalTime);
        recyclerViewUsage = findViewById(R.id.recyclerViewUsage);
        recyclerViewUsage.setLayoutManager(new LinearLayoutManager(this));

        if (getIntent() != null && getIntent().hasExtra(APPS_EXTRA)) {
            apps = getIntent().getParcelableArrayListExtra(APPS_EXTRA);
            setupUI();
        }
    }

    private void setupUI() {
        if (apps == null) return;
        
        long totalMillis = 0;
        for (App app : apps) {
            totalMillis += app.getUsageDuration();
        }

        txtTotalTime.setText("Total Screen Time: " + formatDuration(totalMillis));

        UsageAdapter usageAdapter = new UsageAdapter(this, apps);
        recyclerViewUsage.setAdapter(usageAdapter);
    }

    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return String.format("%dh %dm", hours, minutes);
    }
}
