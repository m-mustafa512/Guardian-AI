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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {

    private Context context;
    private List<App> apps;
    private PackageManager packageManager;
    private long maxDuration = 0;

    public AppUsageAdapter(Context context, List<App> apps) {
        this.context = context;
        this.apps = new ArrayList<>(apps); // Create a copy to avoid modifying original list
        this.packageManager = context.getPackageManager();
        
        // Filter out apps with 0 usage and Sort
        filterAndSort();
    }

    private void filterAndSort() {
        List<App> filtered = new ArrayList<>();
        for (App app : apps) {
            if (app.getUsageDuration() > 0) {
                filtered.add(app);
            }
        }
        
        Collections.sort(filtered, new Comparator<App>() {
            @Override
            public int compare(App o1, App o2) {
                return Long.compare(o2.getUsageDuration(), o1.getUsageDuration());
            }
        });
        
        this.apps = filtered;
        
        if (!apps.isEmpty()) {
            maxDuration = apps.get(0).getUsageDuration();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app_usage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        App app = apps.get(position);

        holder.txtAppName.setText(app.getAppName());
        
        try {
            Drawable icon = packageManager.getApplicationIcon(app.getPackageName());
            holder.imgAppIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.imgAppIcon.setImageResource(R.drawable.ic_android);
        }

        // Calculate progress relative to max usage
        int progress = 0;
        if (maxDuration > 0) {
            progress = (int) ((app.getUsageDuration() * 100) / maxDuration);
        }
        holder.progressBarUsage.setProgress(progress);

        // Format Duration
        long totalMillis = app.getUsageDuration();
        long hours = TimeUnit.MILLISECONDS.toHours(totalMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60;
        holder.txtAppTime.setText(String.format("%dh %dm", hours, minutes));
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView txtAppName, txtAppTime;
        ProgressBar progressBarUsage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAppIcon = itemView.findViewById(R.id.imgAppIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            txtAppTime = itemView.findViewById(R.id.txtAppTime);
            progressBarUsage = itemView.findViewById(R.id.progressBarUsage);
        }
    }
}
