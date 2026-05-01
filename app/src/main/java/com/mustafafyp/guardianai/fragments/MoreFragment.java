package com.mustafafyp.guardianai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.mustafafyp.guardianai.R;

/**
 * "More" hub fragment — shown when the user taps the More item in the bottom navigation.
 *
 * Acts as a secondary menu that houses features that don't fit in the main 5-item bar:
 *   - Activity Log
 *   - Content Filter & Network (Module 9)
 *   - Future features (Sensor Logs etc.) can be added here as new card rows.
 *
 * Sub-navigation: tapping a card replaces the fragment container in
 * ChildDetailsActivity with the target fragment, keeping the bottom nav visible.
 */
public class MoreFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CardView cardActivityLog    = view.findViewById(R.id.cardActivityLog);
        CardView cardContentFilter  = view.findViewById(R.id.cardContentFilter);

        cardActivityLog.setOnClickListener(v -> navigateTo(new ActivityLogFragment()));
        cardContentFilter.setOnClickListener(v -> navigateTo(new ContentFilterFragment()));
    }

    /**
     * Replaces the shared fragment container in ChildDetailsActivity with the given fragment.
     * Uses the parent activity's FragmentManager so the bottom navigation stays visible.
     */
    private void navigateTo(Fragment target) {
        if (getActivity() == null) return;
        FragmentManager fm = getActivity().getSupportFragmentManager();
        fm.beginTransaction()
                .replace(R.id.fragmentContainer, target)
                .addToBackStack(null)
                .commit();
    }
}
