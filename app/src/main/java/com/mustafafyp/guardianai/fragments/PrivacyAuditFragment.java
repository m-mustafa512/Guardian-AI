package com.mustafafyp.guardianai.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.activities.ParentSignedInActivity;
import com.mustafafyp.guardianai.models.AppPermissionInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Module 10 — Privacy & Permission Awareness (Parent UI)
 *
 * Displays:
 *  1. Privacy Health Score card (0–100, circular progress, last scan time)
 *  2. Data Categories row (Sensitive Access count · Normal Access count)
 *  3. App Analysis list — per-app risk cards with Review Permissions / Fix / Ignore buttons
 *
 * UI style matches ContentFilterFragment and AiMonitorFragment exactly
 * (same card structure, #F5F5F5 background, white cards, same colour palette).
 */
public class PrivacyAuditFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView     txtPrivacyScore, txtPrivacyLastScan;
    private ProgressBar  progressPrivacyScoreFg;
    private TextView     txtSensitiveCount, txtNormalCount;
    private LinearLayout layoutPrivacyAppList;
    private TextView     txtPrivacyNoApps;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private String            childEmail;
    private String            childUid;
    private DatabaseReference dbRef;
    private ValueEventListener auditListener;

    // ── Current filter (ALL / HIGH / MEDIUM / LOW) ────────────────────────────
    private String currentFilter = "ALL";

    // ── Cached data for re-filtering without re-fetching ─────────────────────
    private final List<AppPermissionInfo> cachedApps = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_privacy_audit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);

        // Resolve childEmail — same pattern as ContentFilterFragment
        if (getArguments() != null) {
            childEmail = getArguments().getString(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }
        if (childEmail == null && getActivity() != null) {
            childEmail = getActivity().getIntent()
                    .getStringExtra(ParentSignedInActivity.CHILD_EMAIL_EXTRA);
        }

        dbRef = FirebaseDatabase.getInstance().getReference("users");
        setupFilterButton(view);

        if (childEmail != null) lookupUidThenListen();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (auditListener != null && childUid != null) {
            dbRef.child("childs").child(childUid).child("privacyAudit").child("apps")
                    .removeEventListener(auditListener);
        }
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews(View v) {
        txtPrivacyScore      = v.findViewById(R.id.txtPrivacyScore);
        txtPrivacyLastScan   = v.findViewById(R.id.txtPrivacyLastScan);
        progressPrivacyScoreFg = v.findViewById(R.id.progressPrivacyScoreFg);
        txtSensitiveCount    = v.findViewById(R.id.txtSensitiveCount);
        txtNormalCount       = v.findViewById(R.id.txtNormalCount);
        layoutPrivacyAppList = v.findViewById(R.id.layoutPrivacyAppList);
        txtPrivacyNoApps     = v.findViewById(R.id.txtPrivacyNoApps);
    }

    // ── Firebase: UID lookup then listener ────────────────────────────────────

    private void lookupUidThenListen() {
        dbRef.child("childs").orderByChild("email").equalTo(childEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isSafe() || !snapshot.exists()) return;
                        childUid = snapshot.getChildren().iterator().next().getKey();
                        if (childUid != null) attachAuditListener();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void attachAuditListener() {
        auditListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isSafe()) return;
                cachedApps.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    AppPermissionInfo info = child.getValue(AppPermissionInfo.class);
                    if (info != null) cachedApps.add(info);
                }
                renderAuditData(cachedApps);
                updateLastScanTime();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };

        dbRef.child("childs").child(childUid).child("privacyAudit").child("apps")
                .addValueEventListener(auditListener);
    }

    // ── UI rendering ──────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private void renderAuditData(List<AppPermissionInfo> apps) {
        if (!isSafe()) return;

        // Count HIGH/MEDIUM (sensitive) vs LOW (normal)
        int sensitiveCount = 0;
        int normalCount    = 0;
        for (AppPermissionInfo app : apps) {
            if ("HIGH".equals(app.getRiskLevel()) || "MEDIUM".equals(app.getRiskLevel())) {
                sensitiveCount++;
            } else {
                normalCount++;
            }
        }

        // Update category mini-cards
        txtSensitiveCount.setText(String.format(Locale.getDefault(), "%02d", sensitiveCount));
        txtNormalCount.setText(String.format(Locale.getDefault(), "%02d", normalCount));

        // Compute and display health score
        int score = computeScore(apps);
        txtPrivacyScore.setText(String.valueOf(score));
        progressPrivacyScoreFg.setProgress(score);

        // Colour the score text by health
        int scoreColor;
        if (score >= 75)      scoreColor = Color.parseColor("#43A047");
        else if (score >= 50) scoreColor = Color.parseColor("#FF8F00");
        else                  scoreColor = Color.parseColor("#D32F2F");
        txtPrivacyScore.setTextColor(scoreColor);

        // Tint the circular progress ring to match
        progressPrivacyScoreFg.getProgressDrawable().setTint(scoreColor);

        // Build per-app card list
        buildAppList(apps);
    }

    @SuppressLint("SetTextI18n")
    private void buildAppList(List<AppPermissionInfo> allApps) {
        if (!isSafe()) return;

        // Clear existing cards but keep the empty-state view
        int childCount = layoutPrivacyAppList.getChildCount();
        if (childCount > 1) layoutPrivacyAppList.removeViews(1, childCount - 1);

        // Apply filter
        List<AppPermissionInfo> filtered = new ArrayList<>();
        for (AppPermissionInfo a : allApps) {
            if ("ALL".equals(currentFilter) || currentFilter.equals(a.getRiskLevel())) {
                filtered.add(a);
            }
        }

        if (filtered.isEmpty()) {
            txtPrivacyNoApps.setText(
                    allApps.isEmpty()
                            ? "Scanning apps for permission risks…\nMake sure the child device is running."
                            : "No apps match the current filter.");
            txtPrivacyNoApps.setVisibility(View.VISIBLE);
            return;
        }

        txtPrivacyNoApps.setVisibility(View.GONE);

        for (AppPermissionInfo app : filtered) {
            layoutPrivacyAppList.addView(buildAppCard(app));
        }
    }

    /**
     * Builds a CardView representing one app's permission risk.
     * Matches the mockup layout: icon placeholder | name + perm tags | risk badge
     * Buttons differ by risk level.
     */
    @SuppressLint("SetTextI18n")
    private View buildAppCard(AppPermissionInfo app) {
        boolean isHigh   = "HIGH".equals(app.getRiskLevel());
        boolean isMedium = "MEDIUM".equals(app.getRiskLevel());

        // Card colour border
        String borderHex = isHigh ? "#D32F2F" : (isMedium ? "#FF8F00" : "#43A047");
        int    borderClr = Color.parseColor(borderHex);

        // Root card
        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardLp);
        card.setRadius(dpToPx(16));
        card.setCardElevation(dpToPx(3));
        card.setCardBackgroundColor(Color.WHITE);
        card.setUseCompatPadding(true);

        // Inner padding container
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        inner.setLayoutParams(new CardView.LayoutParams(
                CardView.LayoutParams.MATCH_PARENT,
                CardView.LayoutParams.WRAP_CONTENT));

        // Top row: icon placeholder + name column + risk badge
        RelativeLayout topRow = new RelativeLayout(requireContext());
        topRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // App icon placeholder (coloured circle with first letter)
        CardView iconBadge = new CardView(requireContext());
        iconBadge.setId(View.generateViewId());
        RelativeLayout.LayoutParams iconLp =
                new RelativeLayout.LayoutParams(dpToPx(42), dpToPx(42));
        iconLp.addRule(RelativeLayout.ALIGN_PARENT_START);
        iconLp.addRule(RelativeLayout.CENTER_VERTICAL);
        iconBadge.setLayoutParams(iconLp);
        iconBadge.setRadius(dpToPx(10));
        iconBadge.setCardElevation(0);
        iconBadge.setCardBackgroundColor(getAvatarColor(app.getAppName()));

        TextView iconLetter = new TextView(requireContext());
        iconLetter.setLayoutParams(new CardView.LayoutParams(
                CardView.LayoutParams.MATCH_PARENT,
                CardView.LayoutParams.MATCH_PARENT));
        iconLetter.setGravity(android.view.Gravity.CENTER);
        iconLetter.setText(app.getAppName().isEmpty() ? "?" :
                String.valueOf(app.getAppName().charAt(0)).toUpperCase(Locale.getDefault()));
        iconLetter.setTextColor(Color.WHITE);
        iconLetter.setTextSize(16);
        iconLetter.setTypeface(null, Typeface.BOLD);
        iconBadge.addView(iconLetter);
        topRow.addView(iconBadge);

        // Risk badge (top-right)
        TextView riskBadge = new TextView(requireContext());
        riskBadge.setId(View.generateViewId());
        RelativeLayout.LayoutParams badgeLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        badgeLp.addRule(RelativeLayout.CENTER_VERTICAL);
        riskBadge.setLayoutParams(badgeLp);
        riskBadge.setText(app.getRiskLevel() + " RISK");
        riskBadge.setTextColor(borderClr);
        riskBadge.setTextSize(11);
        riskBadge.setTypeface(null, Typeface.BOLD);
        riskBadge.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        // Rounded background matching risk colour
        riskBadge.setBackgroundColor(alphaColor(borderClr, 20));
        topRow.addView(riskBadge);

        // App name + permission tags column (between icon and badge)
        LinearLayout nameCol = new LinearLayout(requireContext());
        nameCol.setOrientation(LinearLayout.VERTICAL);
        RelativeLayout.LayoutParams nameColLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        nameColLp.addRule(RelativeLayout.END_OF, iconBadge.getId());
        nameColLp.addRule(RelativeLayout.START_OF, riskBadge.getId());
        nameColLp.addRule(RelativeLayout.CENTER_VERTICAL);
        nameColLp.setMarginStart(dpToPx(12));
        nameColLp.setMarginEnd(dpToPx(8));
        nameCol.setLayoutParams(nameColLp);

        // App name
        TextView tvAppName = new TextView(requireContext());
        tvAppName.setText(app.getAppName());
        tvAppName.setTextColor(Color.parseColor("#1A1A1A"));
        tvAppName.setTextSize(15);
        tvAppName.setTypeface(null, Typeface.BOLD);
        nameCol.addView(tvAppName);

        // Permission tags row
        if (app.getSensitivePermissions() != null && !app.getSensitivePermissions().isEmpty()) {
            LinearLayout tagsRow = new LinearLayout(requireContext());
            tagsRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams tagsLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tagsLp.topMargin = dpToPx(4);
            tagsRow.setLayoutParams(tagsLp);

            int tagLimit = Math.min(app.getSensitivePermissions().size(), 3);
            for (int i = 0; i < tagLimit; i++) {
                String perm = app.getSensitivePermissions().get(i);
                TextView tag = new TextView(requireContext());
                LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                tagLp.setMarginEnd(dpToPx(8));
                tag.setLayoutParams(tagLp);
                tag.setText(getPermIcon(perm) + " " + perm);
                tag.setTextColor(Color.parseColor("#757575"));
                tag.setTextSize(11);
                tagsRow.addView(tag);
            }
            nameCol.addView(tagsRow);
        } else {
            TextView tvNoPerms = new TextView(requireContext());
            LinearLayout.LayoutParams noPermsLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            noPermsLp.topMargin = dpToPx(4);
            tvNoPerms.setLayoutParams(noPermsLp);
            tvNoPerms.setText("No sensitive data access");
            tvNoPerms.setTextColor(Color.parseColor("#9E9E9E"));
            tvNoPerms.setTextSize(11);
            nameCol.addView(tvNoPerms);
        }

        topRow.addView(nameCol);
        inner.addView(topRow);

        // ── Bottom action area by risk level ──────────────────────────────────

        if (isHigh) {
            // "Review Permissions ›" full-width teal button
            MaterialButton btnReview = new MaterialButton(requireContext());
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
            btnLp.topMargin = dpToPx(12);
            btnReview.setLayoutParams(btnLp);
            btnReview.setText("Review Permissions  ›");
            btnReview.setTextSize(14);
            btnReview.setTypeface(null, Typeface.BOLD);
            btnReview.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1789FB")));
            btnReview.setTextColor(Color.WHITE);
            btnReview.setCornerRadius(dpToPx(12));
            btnReview.setInsetTop(0);
            btnReview.setInsetBottom(0);
            final String pkg = app.getPackageName();
            btnReview.setOnClickListener(v -> openAppSettings(pkg));
            inner.addView(btnReview);

        } else if (isMedium) {
            // "Ignore" + "Fix" side-by-side buttons
            LinearLayout btnRow = new LinearLayout(requireContext());
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            btnRowLp.topMargin = dpToPx(12);
            btnRow.setLayoutParams(btnRowLp);

            MaterialButton btnIgnore = new MaterialButton(requireContext());
            LinearLayout.LayoutParams ignoreLp = new LinearLayout.LayoutParams(
                    0, dpToPx(46), 1f);
            ignoreLp.setMarginEnd(dpToPx(8));
            btnIgnore.setLayoutParams(ignoreLp);
            btnIgnore.setText("Ignore");
            btnIgnore.setTextSize(13);
            btnIgnore.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.WHITE));
            btnIgnore.setTextColor(Color.parseColor("#757575"));
            btnIgnore.setCornerRadius(dpToPx(12));
            btnIgnore.setInsetTop(0);
            btnIgnore.setInsetBottom(0);
            btnIgnore.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
            btnIgnore.setStrokeWidth(dpToPx(1));
            // Ignore hides this card in-session
            btnIgnore.setOnClickListener(v -> {
                if (card.getParent() != null) {
                    ((LinearLayout) card.getParent()).removeView(card);
                }
            });

            MaterialButton btnFix = new MaterialButton(requireContext());
            LinearLayout.LayoutParams fixLp = new LinearLayout.LayoutParams(
                    0, dpToPx(46), 1f);
            btnFix.setLayoutParams(fixLp);
            btnFix.setText("Fix");
            btnFix.setTextSize(13);
            btnFix.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#F5F5F5")));
            btnFix.setTextColor(Color.parseColor("#333333"));
            btnFix.setCornerRadius(dpToPx(12));
            btnFix.setInsetTop(0);
            btnFix.setInsetBottom(0);
            final String pkgM = app.getPackageName();
            btnFix.setOnClickListener(v -> openAppSettings(pkgM));

            btnRow.addView(btnIgnore);
            btnRow.addView(btnFix);
            inner.addView(btnRow);

        } else {
            // LOW RISK — green checkmark, no buttons
            LinearLayout lowRow = new LinearLayout(requireContext());
            lowRow.setOrientation(LinearLayout.HORIZONTAL);
            lowRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lowRowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lowRowLp.topMargin = dpToPx(8);
            lowRow.setLayoutParams(lowRowLp);

            ImageView ivCheck = new ImageView(requireContext());
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                    dpToPx(18), dpToPx(18));
            checkLp.setMarginEnd(dpToPx(6));
            ivCheck.setLayoutParams(checkLp);
            ivCheck.setImageResource(R.drawable.ic_check);
            ivCheck.setColorFilter(Color.parseColor("#43A047"));
            lowRow.addView(ivCheck);

            TextView tvSafe = new TextView(requireContext());
            tvSafe.setText("Safe — No sensitive permissions");
            tvSafe.setTextColor(Color.parseColor("#43A047"));
            tvSafe.setTextSize(12);
            lowRow.addView(tvSafe);

            inner.addView(lowRow);
        }

        card.addView(inner);
        return card;
    }

    // ── Filter button ─────────────────────────────────────────────────────────

    private void setupFilterButton(View root) {
        TextView btnFilter = root.findViewById(R.id.btnPrivacyFilter);
        if (btnFilter == null) return;
        btnFilter.setOnClickListener(v -> {
            // Cycle through: ALL → HIGH → MEDIUM → LOW → ALL
            switch (currentFilter) {
                case "ALL":    currentFilter = "HIGH";   break;
                case "HIGH":   currentFilter = "MEDIUM"; break;
                case "MEDIUM": currentFilter = "LOW";    break;
                default:       currentFilter = "ALL";    break;
            }
            btnFilter.setText("Filter: " + currentFilter + " ▾");
            buildAppList(cachedApps);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateLastScanTime() {
        if (!isSafe() || childUid == null) return;
        dbRef.child("childs").child(childUid).child("privacyAudit").child("lastScanned")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isSafe()) return;
                        Long ts = snapshot.getValue(Long.class);
                        if (ts == null) {
                            txtPrivacyLastScan.setText("Last Scan: —");
                            return;
                        }
                        long minAgo = (System.currentTimeMillis() - ts) / 60000L;
                        if (minAgo < 1)
                            txtPrivacyLastScan.setText("Last Scan: just now");
                        else if (minAgo == 1)
                            txtPrivacyLastScan.setText("Last Scan: 1 min ago");
                        else if (minAgo < 60)
                            txtPrivacyLastScan.setText("Last Scan: " + minAgo + " mins ago");
                        else
                            txtPrivacyLastScan.setText("Last Scan: " +
                                    new SimpleDateFormat("h:mm a", Locale.getDefault())
                                            .format(new Date(ts)));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    /**
     * Privacy health score: starts at 100, deducted per risky app.
     * HIGH = -20 pts, MEDIUM = -8 pts, LOW = -2 pts. Floor is 0.
     */
    private int computeScore(List<AppPermissionInfo> apps) {
        int score = 100;
        for (AppPermissionInfo a : apps) {
            if ("HIGH".equals(a.getRiskLevel()))        score -= 20;
            else if ("MEDIUM".equals(a.getRiskLevel())) score -= 8;
            else                                        score -= 2;
        }
        return Math.max(0, score);
    }

    private void openAppSettings(String packageName) {
        if (!isSafe() || packageName == null) return;
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** Returns a stable avatar color based on the first character of the app name. */
    private int getAvatarColor(String name) {
        String[] colors = {
                "#E53935", "#8E24AA", "#1E88E5", "#00897B",
                "#FB8C00", "#6D4C41", "#546E7A", "#D81B60"
        };
        int idx = (name == null || name.isEmpty()) ? 0 : (Math.abs(name.charAt(0)) % colors.length);
        return Color.parseColor(colors[idx]);
    }

    /** Returns a small emoji/icon to prefix the permission label. */
    private String getPermIcon(String label) {
        if (label == null) return "•";
        switch (label) {
            case "Camera":       return "📷";
            case "Microphone":   return "🎤";
            case "Location":     return "📍";
            case "Contacts":     return "👤";
            case "SMS":          return "💬";
            case "Call Log":     return "📞";
            case "Phone Identity": return "🪪";
            case "Account Info": return "🔑";
            case "Storage":      return "💾";
            case "Biometrics":   return "🔒";
            case "Body Sensors": return "❤️";
            case "Calendar":     return "📅";
            case "App History":  return "⏱";
            default:             return "•";
        }
    }

    /** Returns the color with reduced alpha for tinted background chips */
    private int alphaColor(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private boolean isSafe() {
        return isAdded() && getActivity() != null && !getActivity().isFinishing();
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
