package com.mustafafyp.guardianai.activities;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.adapters.AlertAdapter;
import com.mustafafyp.guardianai.models.Alert;

import java.util.ArrayList;
import java.util.Collections;

public class AlertsActivity extends AppCompatActivity {

    public static final String CHILD_EMAIL_EXTRA = "child_email";
    private RecyclerView recyclerViewAlerts;
    private AlertAdapter alertAdapter;
    private ArrayList<Alert> alerts;
    private DatabaseReference databaseReference;
    private String childEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Alerts");
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        recyclerViewAlerts = findViewById(R.id.recyclerViewAlerts);
        recyclerViewAlerts.setLayoutManager(new LinearLayoutManager(this));
        
        childEmail = getIntent().getStringExtra(CHILD_EMAIL_EXTRA);
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
        
        loadAlerts();
    }

    private void loadAlerts() {
        if (childEmail == null) return;
        
        databaseReference.child("childs").orderByChild("email").equalTo(childEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    DataSnapshot nodeShot = dataSnapshot.getChildren().iterator().next();
                    String uid = nodeShot.getKey();
                    
                    databaseReference.child("childs").child(uid).child("alerts")
                            .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot alertsSnapshot) {
                            alerts = new ArrayList<>();
                            for (DataSnapshot alertShot : alertsSnapshot.getChildren()) {
                                Alert alert = alertShot.getValue(Alert.class);
                                alerts.add(alert);
                            }
                            Collections.reverse(alerts); // Newest first
                            alertAdapter = new AlertAdapter(AlertsActivity.this, alerts);
                            recyclerViewAlerts.setAdapter(alertAdapter);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
}
