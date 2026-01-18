package com.mustafafyp.guardianai.activities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.mustafafyp.guardianai.R;

public class GenerateQRActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_qr); // Ensure this matches XML name

        ImageView imgQRCode = findViewById(R.id.imgQRCode);
        Button btnDone = findViewById(R.id.btnDone);

        // 1. Get current Parent's unique ID
        String parentId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // 2. Generate QR Code
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            // We encode the ParentID so the child knows who to connect to
            Bitmap bitmap = barcodeEncoder.encodeBitmap(parentId, BarcodeFormat.QR_CODE, 600, 600);
            imgQRCode.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        btnDone.setOnClickListener(v -> finish());
    }
}