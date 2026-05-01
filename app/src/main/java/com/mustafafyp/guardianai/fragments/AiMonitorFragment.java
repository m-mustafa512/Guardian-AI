package com.mustafafyp.guardianai.fragments;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.activities.ParentSignedInActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AiMonitorFragment extends Fragment {

    // Must match BehaviorAnomalyDetector.ANOMALY_THRESHOLD
    private static final float ANOMALY_THRESHOLD = 0.10151985620725366f;
    private static final int   MAX_HISTORY       = 5;

    private TextView    txtCurrentStatus, txtScoreValue, txtLastChecked, txtNoHistory;
    private ImageView   imgStatusIconAi;
    private ProgressBar progressScore;
    private LinearLayout layoutHistory;

    private String            childEmail;
    private String            childUid;
    private DatabaseReference dbRef;
    private ValueEventListener aiStatusListener;

    // Simple in-memory history (newest first)
    private static class HistoryEntry {
        float   score;
        boolean isAnomaly;
        long    timestamp;
        HistoryEntry(float score, boolean isAnomaly, long timestamp) {
            this.score     = score;
            this.isAnomaly = isAnomaly;
            this.timestamp = timestamp;
        }
    }
    private final List<HistoryEntry> historyList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_ai_monitor, container, false);

        txtCurrentStatus = view.findViewById(R.id.txtCurrentStatus);
        txtScoreValue    = view.findViewById(R.id.txtScoreValue);
        txtLastChecked   = view.findViewById(R.id.txtLastChecked);
        progressScore    = view.findViewById(R.id.progressScore);
        layoutHistory    = view.findViewById(R.id.layoutHistory);
        txtNoHistory     = view.findViewById(R.id.txtNoHistory);
        imgStatusIconAi  = view.findViewById(R.id.imgStatusIconAi);

        // Resolve childEmail from arguments then activity intent (same pattern as ScreenTimeFragment)
        if (getArguments() != null) {
            childEmail = getArguments().getString(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }
        if (childEmail == null && getActivity() != null) {
            childEmail = getActivity().getIntent()
                    .getStringExtra(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }

        dbRef = FirebaseDatabase.getInstance().getReference("users");
        if (childEmail != null) lookupUidThenListen();

        return view;
    }

    // ── Firebase ───────────────────────────────────────────────────────────

    private void lookupUidThenListen() {
        dbRef.child("childs").orderByChild("email").equalTo(childEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isSafe() || !snapshot.exists()) return;
                        childUid = snapshot.getChildren().iterator().next().getKey();
                        if (childUid != null) attachListener();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void attachListener() {
        aiStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isSafe() || !snapshot.exists()) return;

                Object rawScore     = snapshot.child("score").getValue();
                Object rawAnomaly   = snapshot.child("isAnomaly").getValue();
                Object rawTimestamp = snapshot.child("lastChecked").getValue();

                if (rawScore == null) return;

                float   score     = rawScore instanceof Double
                        ? ((Double) rawScore).floatValue()
                        : ((Long) rawScore).floatValue();
                boolean isAnomaly = Boolean.TRUE.equals(rawAnomaly);
                long    ts        = rawTimestamp instanceof Long
                        ? (Long) rawTimestamp
                        : System.currentTimeMillis();

                updateStatusCard(score, isAnomaly, ts);

                historyList.add(0, new HistoryEntry(score, isAnomaly, ts));
                if (historyList.size() > MAX_HISTORY) historyList.remove(MAX_HISTORY);
                refreshHistory();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };

        dbRef.child("childs").child(childUid).child("aiStatus")
                .addValueEventListener(aiStatusListener);
    }

    // ── UI updates ─────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private void updateStatusCard(float score, boolean isAnomaly, long timestamp) {
        if (!isSafe()) return;

        int scorePercent = (int) Math.min(100,
                Math.round((score / ANOMALY_THRESHOLD) * 100));

        txtScoreValue.setText(scorePercent + "/100");
        progressScore.setProgress(scorePercent);

        if (isAnomaly) {
            txtCurrentStatus.setText("Anomalous");
            txtCurrentStatus.setTextColor(Color.parseColor("#D32F2F"));
            imgStatusIconAi.setImageResource(R.drawable.ic_warning);
            imgStatusIconAi.setColorFilter(Color.parseColor("#D32F2F"));
            progressScore.setProgressTintList(
                    ColorStateList.valueOf(Color.parseColor("#D32F2F")));
        } else {
            txtCurrentStatus.setText("Normal");
            txtCurrentStatus.setTextColor(Color.parseColor("#43A047"));
            imgStatusIconAi.setImageResource(R.drawable.ic_check);
            imgStatusIconAi.setColorFilter(Color.parseColor("#43A047"));
            progressScore.setProgressTintList(
                    ColorStateList.valueOf(Color.parseColor("#1789FB")));
        }

        long minAgo = (System.currentTimeMillis() - timestamp) / 60000L;
        if (minAgo < 1) {
            txtLastChecked.setText("Last checked: just now");
        } else if (minAgo == 1) {
            txtLastChecked.setText("Last checked: 1 minute ago");
        } else {
            txtLastChecked.setText("Last checked: " + minAgo + " minutes ago");
        }
    }

    @SuppressLint("SetTextI18n")
    private void refreshHistory() {
        if (!isSafe()) return;

        layoutHistory.removeAllViews();

        if (historyList.isEmpty()) {
            layoutHistory.addView(txtNoHistory);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());

        for (int i = 0; i < historyList.size(); i++) {
            HistoryEntry entry = historyList.get(i);
            int scorePercent   = (int) Math.min(100,
                    Math.round((entry.score / ANOMALY_THRESHOLD) * 100));

            // Root row
            RelativeLayout row = new RelativeLayout(requireContext());
            RelativeLayout.LayoutParams rowLp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dpToPx(2));
            row.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
            row.setLayoutParams(rowLp);

            // Circle icon on left
            ImageView icon = new ImageView(requireContext());
            icon.setId(View.generateViewId());
            RelativeLayout.LayoutParams iconLp = new RelativeLayout.LayoutParams(
                    dpToPx(36), dpToPx(36));
            iconLp.addRule(RelativeLayout.CENTER_VERTICAL);
            icon.setLayoutParams(iconLp);
            icon.setImageResource(entry.isAnomaly ? R.drawable.ic_warning : R.drawable.ic_check);
            icon.setColorFilter(entry.isAnomaly
                    ? Color.parseColor("#D32F2F")
                    : Color.parseColor("#43A047"));
            row.addView(icon);

            // Time + score text column (center)
            LinearLayout textCol = new LinearLayout(requireContext());
            textCol.setId(View.generateViewId());
            textCol.setOrientation(LinearLayout.VERTICAL);
            RelativeLayout.LayoutParams textLp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            textLp.addRule(RelativeLayout.END_OF, icon.getId());
            textLp.addRule(RelativeLayout.CENTER_VERTICAL);
            textLp.setMarginStart(dpToPx(12));
            textCol.setLayoutParams(textLp);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText(sdf.format(new Date(entry.timestamp)));
            tvTime.setTextColor(Color.parseColor("#333333"));
            tvTime.setTextSize(14);
            tvTime.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(tvTime);

            TextView tvScore = new TextView(requireContext());
            tvScore.setText("Anomaly Score: " + scorePercent);
            tvScore.setTextColor(Color.parseColor("#757575"));
            tvScore.setTextSize(12);
            textCol.addView(tvScore);

            row.addView(textCol);

            // Status badge on right
            TextView tvBadge = new TextView(requireContext());
            RelativeLayout.LayoutParams badgeLp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            badgeLp.addRule(RelativeLayout.ALIGN_PARENT_END);
            badgeLp.addRule(RelativeLayout.CENTER_VERTICAL);
            tvBadge.setLayoutParams(badgeLp);
            tvBadge.setText(entry.isAnomaly ? "ANOMALOUS" : "NORMAL");
            tvBadge.setTextColor(entry.isAnomaly
                    ? Color.parseColor("#D32F2F")
                    : Color.parseColor("#43A047"));
            tvBadge.setTextSize(12);
            tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvBadge);

            layoutHistory.addView(row);

            // Divider (except last)
            if (i < historyList.size() - 1) {
                View divider = new View(requireContext());
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divLp.setMargins(dpToPx(12), 0, dpToPx(12), 0);
                divider.setLayoutParams(divLp);
                divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
                layoutHistory.addView(divider);
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean isSafe() {
        return isAdded() && getActivity() != null && !getActivity().isFinishing();
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (aiStatusListener != null && childUid != null) {
            dbRef.child("childs").child(childUid).child("aiStatus")
                    .removeEventListener(aiStatusListener);
        }
    }
}
