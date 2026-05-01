package com.mustafafyp.guardianai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.models.AppNetworkUsage;
import com.mustafafyp.guardianai.utils.NetworkUsageManager;

import java.util.List;

/**
 * Module 9 — Content Filtering & Network Tracking
 * RecyclerView adapter for per-app network usage entries.
 * Displays download (↓) and upload (↑) bytes with a scaled progress bar.
 */
public class NetworkUsageAdapter extends RecyclerView.Adapter<NetworkUsageAdapter.ViewHolder> {

    private final List<AppNetworkUsage> items;
    private final long                  maxBytes; // highest total for scaling the progress bar

    public NetworkUsageAdapter(List<AppNetworkUsage> items) {
        this.items = items;
        long max = 1L;
        for (AppNetworkUsage u : items) if (u.getTotalBytes() > max) max = u.getTotalBytes();
        this.maxBytes = max;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_network_usage, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        AppNetworkUsage u = items.get(position);

        h.tvAppName.setText(u.getAppName());
        h.tvDownload.setText("↓ " + NetworkUsageManager.formatBytes(u.getRxBytes()));
        h.tvUpload.setText("↑ " + NetworkUsageManager.formatBytes(u.getTxBytes()));

        int progress = (int) Math.min(100, (u.getTotalBytes() * 100L) / maxBytes);
        h.progressBar.setProgress(progress);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView    tvAppName;
        final TextView    tvDownload;
        final TextView    tvUpload;
        final ProgressBar progressBar;

        ViewHolder(View v) {
            super(v);
            tvAppName   = v.findViewById(R.id.tvNetAppName);
            tvDownload  = v.findViewById(R.id.tvNetDownload);
            tvUpload    = v.findViewById(R.id.tvNetUpload);
            progressBar = v.findViewById(R.id.progressNetUsage);
        }
    }
}
