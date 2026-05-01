package com.mustafafyp.guardianai.ai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Guardian AI — Behavior Anomaly Detector
 *
 * Uses a pre-trained Autoencoder (TFLite) trained on the Kaggle
 * Screen Time & App Usage Dataset to detect unusual child phone behavior.
 *
 * Input  : float[] of size INPUT_DIM — normalized daily usage features.
 * Output : boolean — true if anomaly detected, false if normal.
 */
public class BehaviorAnomalyDetector {

    private static final String TAG        = "GuardianAI_Detector";
    private static final String MODEL_FILE = "anomaly_autoencoder.tflite";

    // ─────────────────────────────────────────────────────────────────
    // UPDATE THESE after running the Python training script:
    // ─────────────────────────────────────────────────────────────────

    // From guardian_threshold.txt
    private static final float ANOMALY_THRESHOLD = 0.40023f;  // ← REPLACE
    //0.10151985620725366f real value
    //0.0011f value for testing
    // Count lines in guardian_features.txt
    public static final int INPUT_DIM = 27;  // ← REPLACE with actual count

    // ─────────────────────────────────────────────────────────────────

    private Interpreter tflite;
    private boolean isLoaded = false;

    public BehaviorAnomalyDetector(Context context) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            tflite = new Interpreter(loadModelFile(context), options);
            isLoaded = true;
            Log.d(TAG, "✅ Autoencoder model loaded successfully.");
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to load TFLite model: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fd.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(),
                fd.getDeclaredLength()
        );
    }

    /**
     * Run the Autoencoder and check if the given behavior is anomalous.
     *
     * @param normalizedFeatures float[] of size INPUT_DIM, all values between 0.0 and 1.0
     * @return true if ANOMALY detected, false if NORMAL behavior
     */
    public boolean isAnomaly(float[] normalizedFeatures) {
        if (!isLoaded || tflite == null) {
            Log.w(TAG, "Model not loaded. Skipping anomaly check.");
            return false;
        }
        if (normalizedFeatures.length != INPUT_DIM) {
            Log.e(TAG, "Input mismatch! Expected " + INPUT_DIM
                    + " features, got " + normalizedFeatures.length);
            return false;
        }

        float[][] input  = new float[1][INPUT_DIM];
        float[][] output = new float[1][INPUT_DIM];
        input[0] = normalizedFeatures;

        // Run the Autoencoder
        tflite.run(input, output);

        // MAE between input and reconstruction
        float totalError = 0f;
        for (int i = 0; i < INPUT_DIM; i++) {
            totalError += Math.abs(input[0][i] - output[0][i]);
        }
        float mae = totalError / INPUT_DIM;

        Log.d(TAG, String.format(
                "MAE=%.6f | Threshold=%.6f | Anomaly=%b", mae, ANOMALY_THRESHOLD, mae > ANOMALY_THRESHOLD
        ));

        return mae > ANOMALY_THRESHOLD;
    }

    /**
     * Get the raw anomaly score (higher = more unusual).
     * Use this to log to Firebase alongside alerts.
     */
    public float getAnomalyScore(float[] normalizedFeatures) {
        if (!isLoaded || tflite == null) return 0f;

        float[][] input  = new float[1][INPUT_DIM];
        float[][] output = new float[1][INPUT_DIM];
        input[0] = normalizedFeatures;
        tflite.run(input, output);

        float totalError = 0f;
        for (int i = 0; i < INPUT_DIM; i++) {
            totalError += Math.abs(input[0][i] - output[0][i]);
        }
        return totalError / INPUT_DIM;
    }

    /**
     * Min-Max normalization helper.
     * Use the values from guardian_scaler.pkl (see FeatureNormConstants.java).
     */
    public static float normalize(float rawValue, float featureMin, float featureMax) {
        if (featureMax == featureMin) return 0f;
        return Math.max(0f, Math.min(1f, (rawValue - featureMin) / (featureMax - featureMin)));
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
            isLoaded = false;
        }
    }
}
