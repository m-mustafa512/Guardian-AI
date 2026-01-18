package com.mustafafyp.guardianai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class ScanQRActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start the Camera Scanner immediately
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setOrientationLocked(true);
        integrator.setPrompt("Scan Parent's QR Code");
        integrator.initiateScan();
    }

    // Handle the result when camera closes
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
                finish();
            } else {
                // QR Scanned Successfully!
                String parentId = result.getContents();
                linkToParent(parentId);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void linkToParent(String parentId) {
        String childId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();

        // 1. Tell the Child who their parent is
        db.child("users").child(childId).child("linked_parent").setValue(parentId);

        // 2. Add Child to Parent's list (Critical for the Dashboard to work!)
        db.child("users").child(parentId).child("childs").child(childId).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Linked Successfully!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, ChildSignedInActivity.class));
                    finish();
                });
    }
}