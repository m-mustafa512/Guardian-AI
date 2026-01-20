package com.mustafafyp.guardianai.adapters;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.models.App;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.UsageViewHolder> {

    private Context context;
    private ArrayList<App> apps;
    private long maxDuration = 0;

    public UsageAdapter(Context context, ArrayList<App> apps) {
        this.context = context;
        this.apps = apps;
        
        // Sort by duration descending
        Collections.sort(this.apps, new Comparator<App>() {
            @Override
            public int compare(App o1, App o2) {
                return Long.compare(o2.getUsageDuration(), o1.getUsageDuration());
            }
        });

        if (!apps.isEmpty()) {
            maxDuration = apps.get(0).getUsageDuration();
        }
    }

    @NonNull
    @Override
    public UsageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_usage_report, parent, false);
        return new UsageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UsageViewHolder holder, int position) {
        App app = apps.get(position);
        holder.txtAppName.setText(app.getAppName());
        holder.txtUsageDuration.setText(formatDuration(app.getUsageDuration()));

        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(app.getPackageName());
            holder.imgAppIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.imgAppIcon.setImageResource(R.drawable.ic_android);
        }

        if (maxDuration > 0) {
            int progress = (int) ((app.getUsageDuration() * 100) / maxDuration);
            holder.progressBarUsage.setProgress(progress);
        } else {
            holder.progressBarUsage.setProgress(0);
        }
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return String.format("%dh %dm", hours, minutes);
    }

    public class UsageViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView txtAppName, txtUsageDuration;
        ProgressBar progressBarUsage;

        public UsageViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAppIcon = itemView.findViewById(R.id.imgAppIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            txtUsageDuration = itemView.findViewById(R.id.txtUsageDuration);
            progressBarUsage = itemView.findViewById(R.id.progressBarUsage);
        }
    }
}
