package com.mustafafyp.guardianai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.activities.ChildDetailsActivity;
import com.mustafafyp.guardianai.activities.ParentSignedInActivity;
import com.mustafafyp.guardianai.adapters.AppUsageAdapter;
import com.mustafafyp.guardianai.customviews.CustomBarChartView;
import com.mustafafyp.guardianai.models.App;
import com.mustafafyp.guardianai.models.Child;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ScreenTimeFragment extends Fragment {

    private CustomBarChartView barChartView;
    private RecyclerView recyclerViewApps;
    private TextView txtTotalTime;
    private String childEmail;
    private DatabaseReference databaseReference;
    private AppUsageAdapter appUsageAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_screen_time, container, false);

        barChartView = view.findViewById(R.id.barChart);
        recyclerViewApps = view.findViewById(R.id.recyclerViewApps);
        txtTotalTime = view.findViewById(R.id.txtTotalTime);

        recyclerViewApps.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewApps.setNestedScrollingEnabled(false);

        // Get child email from arguments or activity
        if (getArguments() != null) {
            childEmail = getArguments().getString(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }
        
        // If args are missing, try to recover from Activity intent if possible (fallback)
        if (childEmail == null && getActivity() != null) {
             childEmail = getActivity().getIntent().getStringExtra(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }

        databaseReference = FirebaseDatabase.getInstance().getReference("users");
        
        if (childEmail != null) {
            fetchChildData();
        }

        return view;
    }

    // Screen Time feature restored


    // Safety check for async callbacks
    private boolean isSafe() {
        return isAdded() && getActivity() != null && !getActivity().isFinishing();
    }

    private void fetchChildData() {
        databaseReference.child("childs").orderByChild("email").equalTo(childEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isSafe()) return;
                        
                        if (snapshot.exists()) {
                            DataSnapshot childNode = snapshot.getChildren().iterator().next();
                            String uid = childNode.getKey();
                            if (uid != null) {
                                fetchDailyHistory(uid);
                                fetchApps(uid);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void fetchDailyHistory(String uid) {
        databaseReference.child("childs").child(uid).child("dailyUsage")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isSafe()) return;

                        List<Long> dataPoints = new ArrayList<>();
                        List<String> labels = new ArrayList<>();
                        
                        // Prepare 7 days labels
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        SimpleDateFormat labelSdf = new SimpleDateFormat("EEE", Locale.getDefault()); // Mon, Tue
                        
                        Calendar calendar = Calendar.getInstance();
                        // Go back 6 days to have 7 days including today
                        calendar.add(Calendar.DAY_OF_YEAR, -6);

                        for (int i = 0; i < 7; i++) {
                            String dateKey = sdf.format(calendar.getTime());
                            String label = labelSdf.format(calendar.getTime());
                            
                            long usage = 0;
                            if (snapshot.hasChild(dateKey)) {
                                Object val = snapshot.child(dateKey).getValue();
                                if (val instanceof Long) {
                                    usage = (Long) val;
                                }
                            }
                            
                            dataPoints.add(usage);
                            labels.add(label);
                            
                            // If it's today (last iteration)
                            if (i == 6) {
                                updateTotalTimeUI(usage);
                            }

                            calendar.add(Calendar.DAY_OF_YEAR, 1);
                        }
                        
                        barChartView.setData(dataPoints, labels);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateTotalTimeUI(long durationMillis) {
        if (!isSafe()) return;
        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60;
        txtTotalTime.setText(String.format("%dh %dm", hours, minutes));
    }

    private void fetchApps(String uid) {
        // Updated Path: appStats/{uid}/apps
        databaseReference.child("appStats").child(uid).child("apps")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                         if (!isSafe()) return;
                         
                         List<App> apps = new ArrayList<>();
                         for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                             App app = appSnapshot.getValue(App.class);
                             if (app != null) {
                                 apps.add(app);
                             }
                         }
                         
                         if (getContext() != null) {
                             appUsageAdapter = new AppUsageAdapter(getContext(), apps);
                             recyclerViewApps.setAdapter(appUsageAdapter);
                         }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
}
