package com.mustafafyp.guardianai.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mustafafyp.guardianai.R;
import java.util.HashMap;

public class WebFilterActivity extends AppCompatActivity {

    private Switch switchAdult, switchGambling, switchSocial;
    private Button btnSaveRules;
    private String childUid; // Passed from Intent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_filter);

        childUid = getIntent().getStringExtra("CHILD_UID");

        switchAdult = findViewById(R.id.switchAdult);
        switchGambling = findViewById(R.id.switchGambling);
        switchSocial = findViewById(R.id.switchSocial);
        btnSaveRules = findViewById(R.id.btnSaveRules);

        btnSaveRules.setOnClickListener(v -> saveRules());
    }

    private void saveRules() {
        if(childUid == null) return;

        HashMap<String, Object> rules = new HashMap<>();
        rules.put("block_adult", switchAdult.isChecked());
        rules.put("block_gambling", switchGambling.isChecked());
        rules.put("block_social", switchSocial.isChecked());

        // Save to Firebase -> Child Listeners will pick this up
        FirebaseDatabase.getInstance().getReference("users")
                .child("childs").child(childUid).child("web_filter")
                .updateChildren(rules)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Rules Synced!", Toast.LENGTH_SHORT).show());
    }
}