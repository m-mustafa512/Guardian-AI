package com.mustafafyp.guardianai.adapters;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.models.Alert;

import java.util.ArrayList;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    private Context context;
    private ArrayList<Alert> alerts;

    public AlertAdapter(Context context, ArrayList<Alert> alerts) {
        this.context = context;
        this.alerts = alerts;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        Alert alert = alerts.get(position);
        holder.txtAlertTitle.setText(alert.getTitle());
        holder.txtAlertMessage.setText(alert.getMessage());
        
        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(alert.getTimestamp(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        holder.txtAlertTime.setText(timeAgo);
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    public class AlertViewHolder extends RecyclerView.ViewHolder {
        TextView txtAlertTitle, txtAlertMessage, txtAlertTime;

        public AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            txtAlertTitle = itemView.findViewById(R.id.txtAlertTitle);
            txtAlertMessage = itemView.findViewById(R.id.txtAlertMessage);
            txtAlertTime = itemView.findViewById(R.id.txtAlertTime);
        }
    }
}
