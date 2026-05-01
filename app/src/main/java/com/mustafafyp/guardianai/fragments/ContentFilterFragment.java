package com.mustafafyp.guardianai.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.activities.ParentSignedInActivity;
import com.mustafafyp.guardianai.adapters.NetworkUsageAdapter;
import com.mustafafyp.guardianai.models.AppNetworkUsage;
import com.mustafafyp.guardianai.utils.NetworkUsageManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Module 9 — Content Filtering & Network Tracking (Parent UI)
 *
 * Displays:
 *  1. Service status card (accessibility service on/off + scan status)
 *  2. Two stat mini-cards (keyword count, detections today)
 *  3. Per-app network usage (top 5, today)
 *  4. Keyword management (add/remove keywords → Firebase)
 *  5. Recent keyword hit history
 *
 * UI style matches AiMonitorFragment exactly (same card structure, colours, spacing).
 */
public class ContentFilterFragment extends Fragment {

    private static final int MAX_HISTORY = 10;

    // Status card
    private TextView  txtCfStatus, txtAccessibilityStatus, txtCfLastScan;
    private ImageView imgStatusIconCf;

    // Stat mini cards
    private TextView txtKeywordCount, txtDetectionCount;

    // Network usage
    private RecyclerView recyclerNetworkUsage;
    private TextView     txtNoNetworkData, txtNetworkDate;

    // Keyword management
    private ChipGroup    chipGroupKeywords;
    private EditText     etNewKeyword;
    private MaterialButton btnAddKeyword;
    private TextView     btnClearAllKeywords, txtNoKeywords;

    // Detection history
    private LinearLayout layoutDetectionHistory;
    private TextView     txtNoDetections;

    // Firebase
    private String            childEmail;
    private String            childUid;
    private DatabaseReference dbRef;

    private ValueEventListener keywordsListener;
    private ValueEventListener hitsListener;
    private ValueEventListener networkListener;

    private final List<String> currentKeywords = new ArrayList<>();
    private int detectionCountToday = 0;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_content_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupDate();

        // Resolve child email (same pattern as AiMonitorFragment)
        if (getArguments() != null) {
            childEmail = getArguments().getString(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }
        if (childEmail == null && getActivity() != null) {
            childEmail = getActivity().getIntent()
                    .getStringExtra(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }

        dbRef = FirebaseDatabase.getInstance().getReference("users");

        setupAccessibilityStatusCheck();
        setupAddKeyword();
        setupClearAll();

        if (childEmail != null) lookupUidThenListen();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        removeListeners();
    }

    // ── View binding ───────────────────────────────────────────────────────

    private void bindViews(View v) {
        txtCfStatus           = v.findViewById(R.id.txtCfStatus);
        txtAccessibilityStatus = v.findViewById(R.id.txtAccessibilityStatus);
        txtCfLastScan         = v.findViewById(R.id.txtCfLastScan);
        imgStatusIconCf       = v.findViewById(R.id.imgStatusIconCf);

        txtKeywordCount    = v.findViewById(R.id.txtKeywordCount);
        txtDetectionCount  = v.findViewById(R.id.txtDetectionCount);

        recyclerNetworkUsage = v.findViewById(R.id.recyclerNetworkUsage);
        recyclerNetworkUsage.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerNetworkUsage.setNestedScrollingEnabled(false);
        txtNoNetworkData  = v.findViewById(R.id.txtNoNetworkData);
        txtNetworkDate    = v.findViewById(R.id.txtNetworkDate);

        chipGroupKeywords     = v.findViewById(R.id.chipGroupKeywords);
        etNewKeyword          = v.findViewById(R.id.etNewKeyword);
        btnAddKeyword         = v.findViewById(R.id.btnAddKeyword);
        btnClearAllKeywords   = v.findViewById(R.id.btnClearAllKeywords);
        txtNoKeywords         = v.findViewById(R.id.txtNoKeywords);

        layoutDetectionHistory = v.findViewById(R.id.layoutDetectionHistory);
        txtNoDetections        = v.findViewById(R.id.txtNoDetections);
    }

    // ── Firebase lookup ────────────────────────────────────────────────────

    private void lookupUidThenListen() {
        dbRef.child("childs").orderByChild("email").equalTo(childEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isSafe() || !snapshot.exists()) return;
                        childUid = snapshot.getChildren().iterator().next().getKey();
                        if (childUid != null) {
                            attachKeywordsListener();
                            attachHitsListener();
                            attachNetworkListener();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Keyword list listener ──────────────────────────────────────────────

    private void attachKeywordsListener() {
        keywordsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isSafe()) return;
                currentKeywords.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String kw = child.getValue(String.class);
                    if (kw != null && !kw.isEmpty()) currentKeywords.add(kw);
                }
                refreshKeywordChips();
                txtKeywordCount.setText(String.valueOf(currentKeywords.size()));
                updateStatusCard();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        dbRef.child("childs").child(childUid).child("keywordFilterList")
                .addValueEventListener(keywordsListener);
    }

    // ── Keyword hits listener ──────────────────────────────────────────────

    private void attachHitsListener() {
        hitsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isSafe()) return;
                refreshDetectionHistory(snapshot);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        dbRef.child("childs").child(childUid).child("keywordHits")
                .limitToLast(MAX_HISTORY)
                .addValueEventListener(hitsListener);
    }

    private void refreshDetectionHistory(@NonNull DataSnapshot snapshot) {
        detectionCountToday = 0;

        // Collect and reverse (Firebase returns oldest-first; we want newest-first)
        List<DataSnapshot> entries = new ArrayList<>();
        for (DataSnapshot entry : snapshot.getChildren()) entries.add(0, entry);

        if (entries.isEmpty()) {
            // Show the empty-state label, hide any old rows
            txtNoDetections.setVisibility(View.VISIBLE);
            // Remove only programmatically added rows (keep txtNoDetections which is in XML)
            int childCount = layoutDetectionHistory.getChildCount();
            if (childCount > 1) layoutDetectionHistory.removeViews(1, childCount - 1);
        } else {
            txtNoDetections.setVisibility(View.GONE);

            // Remove all previously added rows (leave txtNoDetections at index 0)
            int childCount = layoutDetectionHistory.getChildCount();
            if (childCount > 1) layoutDetectionHistory.removeViews(1, childCount - 1);

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

            int limit = Math.min(entries.size(), MAX_HISTORY);
            for (int i = 0; i < limit; i++) {
                DataSnapshot entry = entries.get(i);
                String keyword = safeString(entry, "keyword");
                String appName = safeString(entry, "appName");
                Long   ts      = entry.child("timestamp").getValue(Long.class);
                if (ts == null) ts = 0L;

                String hitDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(new Date(ts));
                if (today.equals(hitDate)) detectionCountToday++;

                // Divider above each row (except the first)
                if (i > 0) {
                    View div = new View(requireContext());
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1);
                    dlp.setMargins(dpToPx(12), 0, dpToPx(12), 0);
                    div.setLayoutParams(dlp);
                    div.setBackgroundColor(Color.parseColor("#F0F0F0"));
                    layoutDetectionHistory.addView(div);
                }

                layoutDetectionHistory.addView(buildHitRow(keyword, appName, ts, timeFmt));
            }
        }

        txtDetectionCount.setText(String.valueOf(detectionCountToday));
        updateStatusCard();
    }

    // ── Network usage listener ─────────────────────────────────────────────

    private void attachNetworkListener() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        long now = System.currentTimeMillis();

        // ── Dummy data for demonstration ──
        List<AppNetworkUsage> dummyData = new ArrayList<>();
        dummyData.add(new AppNetworkUsage("com.google.android.youtube", "YouTube", today, 845_600_000L, 12_400_000L, now));
        dummyData.add(new AppNetworkUsage("com.instagram.android", "Instagram", today, 284_200_000L, 35_100_000L, now));
        dummyData.add(new AppNetworkUsage("com.whatsapp", "WhatsApp", today, 45_100_000L, 28_900_000L, now));
        dummyData.add(new AppNetworkUsage("com.android.chrome", "Chrome", today, 38_400_000L, 5_200_000L, now));
        dummyData.add(new AppNetworkUsage("com.snapchat.android", "Snapchat", today, 15_600_000L, 8_400_000L, now));

        // Display dummy data unconditionally
        if (recyclerNetworkUsage != null && txtNoNetworkData != null) {
            recyclerNetworkUsage.setVisibility(View.VISIBLE);
            txtNoNetworkData.setVisibility(View.GONE);
            recyclerNetworkUsage.setAdapter(new NetworkUsageAdapter(dummyData));
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private void setupDate() {
        if (txtNetworkDate == null) return;
        String date = new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date());
        txtNetworkDate.setText(date);
    }

    @SuppressLint("SetTextI18n")
    private void updateStatusCard() {
        if (!isSafe()) return;
        if (detectionCountToday > 0) {
            txtCfStatus.setText("⚠ Alert");
            txtCfStatus.setTextColor(Color.parseColor("#D32F2F"));
            imgStatusIconCf.setImageResource(R.drawable.ic_warning);
            imgStatusIconCf.setColorFilter(Color.parseColor("#D32F2F"));
        } else if (!currentKeywords.isEmpty()) {
            txtCfStatus.setText("Active");
            txtCfStatus.setTextColor(Color.parseColor("#43A047"));
            imgStatusIconCf.setImageResource(R.drawable.ic_check);
            imgStatusIconCf.setColorFilter(Color.parseColor("#43A047"));
        } else {
            txtCfStatus.setText("No Keywords");
            txtCfStatus.setTextColor(Color.parseColor("#FF8F00"));
            imgStatusIconCf.setImageResource(R.drawable.ic_warning);
            imgStatusIconCf.setColorFilter(Color.parseColor("#FF8F00"));
        }

        long minAgo = (System.currentTimeMillis() - System.currentTimeMillis()) / 60000L;
        txtCfLastScan.setText("Last scan: just now");
    }

    private void setupAccessibilityStatusCheck() {
        if (!isSafe()) return;
        boolean enabled = isAccessibilityServiceEnabled();
        if (txtAccessibilityStatus != null) {
            txtAccessibilityStatus.setText(
                    enabled ? "Accessibility Service: Enabled" : "Accessibility Service: Disabled — Tap to enable");
            txtAccessibilityStatus.setTextColor(
                    Color.parseColor(enabled ? "#43A047" : "#D32F2F"));
            if (!enabled) {
                txtAccessibilityStatus.setOnClickListener(v ->
                        startActivity(new android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        if (getContext() == null) return false;
        android.view.accessibility.AccessibilityManager am =
                (android.view.accessibility.AccessibilityManager)
                        requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<android.accessibilityservice.AccessibilityServiceInfo> infos =
                am.getEnabledAccessibilityServiceList(
                        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String myPkg = requireContext().getPackageName();
        for (android.accessibilityservice.AccessibilityServiceInfo info : infos) {
            if (info.getId() != null && info.getId().startsWith(myPkg)) return true;
        }
        return false;
    }

    private void refreshKeywordChips() {
        if (!isSafe()) return;
        chipGroupKeywords.removeAllViews();
        if (currentKeywords.isEmpty()) {
            txtNoKeywords.setVisibility(View.VISIBLE);
            return;
        }
        txtNoKeywords.setVisibility(View.GONE);
        for (String kw : currentKeywords) {
            Chip chip = new Chip(requireContext());
            chip.setText(kw);
            chip.setCloseIconVisible(true);
            chip.setChipBackgroundColorResource(android.R.color.white);
            chip.setTextColor(Color.parseColor("#333333"));
            chip.setChipStrokeWidth(1f);
            chip.setChipStrokeColorResource(android.R.color.darker_gray);
            chip.setOnCloseIconClickListener(v -> removeKeyword(kw));
            chipGroupKeywords.addView(chip);
        }
    }

    private void setupAddKeyword() {
        btnAddKeyword.setOnClickListener(v -> addKeyword());
        etNewKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                addKeyword();
                return true;
            }
            return false;
        });
    }

    private void setupClearAll() {
        btnClearAllKeywords.setOnClickListener(v -> {
            if (childUid == null) return;
            dbRef.child("childs").child(childUid).child("keywordFilterList").removeValue();
        });
    }

    private void addKeyword() {
        if (!isSafe() || childUid == null) return;
        String kw = etNewKeyword.getText().toString().trim().toLowerCase(Locale.getDefault());
        if (TextUtils.isEmpty(kw) || currentKeywords.contains(kw)) {
            etNewKeyword.setText("");
            return;
        }
        List<String> updated = new ArrayList<>(currentKeywords);
        updated.add(kw);
        dbRef.child("childs").child(childUid).child("keywordFilterList").setValue(updated);
        etNewKeyword.setText("");
        hideKeyboard();
    }

    private void removeKeyword(String kw) {
        if (!isSafe() || childUid == null) return;
        List<String> updated = new ArrayList<>(currentKeywords);
        updated.remove(kw);
        dbRef.child("childs").child(childUid).child("keywordFilterList").setValue(updated);
    }

    /**
     * Builds a single keyword-hit row view.
     * IMPORTANT: Uses LinearLayout.LayoutParams (not RelativeLayout.LayoutParams)
     * because the row is added to a LinearLayout (layoutDetectionHistory).
     * Using the wrong LayoutParams type causes the row to measure as 0-height.
     */
    private View buildHitRow(String keyword, String appName, long timestamp,
                              SimpleDateFormat timeFmt) {
        // Outer row — must use LinearLayout.LayoutParams since parent is a LinearLayout
        RelativeLayout row = new RelativeLayout(requireContext());
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        // Warning icon (left anchor)
        ImageView icon = new ImageView(requireContext());
        icon.setId(View.generateViewId());
        RelativeLayout.LayoutParams iconLp = new RelativeLayout.LayoutParams(
                dpToPx(18), dpToPx(18));
        iconLp.addRule(RelativeLayout.ALIGN_PARENT_START);
        iconLp.addRule(RelativeLayout.CENTER_VERTICAL);
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_warning);
        icon.setColorFilter(Color.parseColor("#D32F2F"));
        row.addView(icon);

        // Time (right anchor — must be added BEFORE the text column so END_OF works)
        TextView tvTime = new TextView(requireContext());
        tvTime.setId(View.generateViewId());
        RelativeLayout.LayoutParams timeLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        timeLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        timeLp.addRule(RelativeLayout.CENTER_VERTICAL);
        tvTime.setLayoutParams(timeLp);
        tvTime.setText(timeFmt.format(new Date(timestamp)));
        tvTime.setTextColor(Color.parseColor("#9E9E9E"));
        tvTime.setTextSize(11);
        row.addView(tvTime);

        // Text column: keyword + app name (between icon and time)
        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        RelativeLayout.LayoutParams colLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        colLp.addRule(RelativeLayout.END_OF, icon.getId());
        colLp.addRule(RelativeLayout.START_OF, tvTime.getId());
        colLp.addRule(RelativeLayout.CENTER_VERTICAL);
        colLp.setMarginStart(dpToPx(8));
        colLp.setMarginEnd(dpToPx(8));
        textCol.setLayoutParams(colLp);

        TextView tvKeyword = new TextView(requireContext());
        tvKeyword.setText("\"" + keyword + "\"");
        tvKeyword.setTextColor(Color.parseColor("#D32F2F"));
        tvKeyword.setTextSize(13);
        tvKeyword.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvKeyword);

        TextView tvApp = new TextView(requireContext());
        tvApp.setText("in " + appName);
        tvApp.setTextColor(Color.parseColor("#757575"));
        tvApp.setTextSize(11);
        textCol.addView(tvApp);

        row.addView(textCol);
        return row;
    }

    private void removeListeners() {
        if (keywordsListener != null && childUid != null)
            dbRef.child("childs").child(childUid).child("keywordFilterList")
                    .removeEventListener(keywordsListener);
        if (hitsListener != null && childUid != null)
            dbRef.child("childs").child(childUid).child("keywordHits")
                    .removeEventListener(hitsListener);
        if (networkListener != null && childUid != null) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            dbRef.child("childs").child(childUid).child("networkUsage").child(today)
                    .removeEventListener(networkListener);
        }
    }

    private void hideKeyboard() {
        if (getActivity() == null || etNewKeyword == null) return;
        InputMethodManager imm = (InputMethodManager)
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etNewKeyword.getWindowToken(), 0);
    }

    private String safeString(DataSnapshot snap, String key) {
        String v = snap.child(key).getValue(String.class);
        return v != null ? v : "—";
    }

    private boolean isSafe() {
        return isAdded() && getActivity() != null && !getActivity().isFinishing();
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
