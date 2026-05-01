package com.mustafafyp.guardianai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.widget.SwitchCompat;
import com.mustafafyp.guardianai.models.App;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mustafafyp.guardianai.R;
import com.mustafafyp.guardianai.interfaces.OnChildClickListener;
import com.mustafafyp.guardianai.models.Child;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChildAdapter extends RecyclerView.Adapter<ChildAdapter.ChildAdapterViewHolder> {
	private Context context;
	private ArrayList<Child> childs;
	private OnChildClickListener onChildClickListener;
	
	
	public ChildAdapter(Context context, ArrayList<Child> childs) {
		this.context = context;
		this.childs = childs;
	}
	
	public void setOnChildClickListener(OnChildClickListener listener) {
		this.onChildClickListener = listener;
	}
	
	@NonNull
	@Override
	public ChildAdapterViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
		View view = LayoutInflater.from(context).inflate(R.layout.card_child, viewGroup, false);
		return new ChildAdapterViewHolder(view);
	}
	
	@Override
	public void onBindViewHolder(@NonNull final ChildAdapterViewHolder childAdapterViewHolder, int i) {
		Child child = childs.get(i);
		childAdapterViewHolder.txtChildName.setText(child.getName());
		
		if (child.getScreenLock() != null) {
			childAdapterViewHolder.switchLockPhone.setChecked(child.getScreenLock().isLocked());
		}
		Picasso.get().load(child.getProfileImage()).placeholder(R.drawable.ic_profile_image).error(R.drawable.ic_profile_image).into(childAdapterViewHolder.imgChild);
		
		// Calculate Stats
		long totalUsage = child.getTotalScreenTime();
		App topApp = null;
		if (child.getApps() != null) {
			for (App app : child.getApps()) {
				if (app == null) continue;
				if (topApp == null || app.getUsageDuration() > topApp.getUsageDuration()) {
					topApp = app;
				}
			}
		}

		// Format Screen Time
		long hours = (totalUsage / (1000 * 60 * 60));
		long minutes = (totalUsage / (1000 * 60)) % 60;
		childAdapterViewHolder.txtTotalTime.setText(String.format("%dh %dm", hours, minutes));

		// Set Top App
		if (topApp != null && topApp.getUsageDuration() > 0) {
			childAdapterViewHolder.txtTopApp.setText(topApp.getAppName());
		} else {
			childAdapterViewHolder.txtTopApp.setText("None");
		}

		if (child.isAppDeleted()) {
			childAdapterViewHolder.layoutDeletedApp.setVisibility(View.VISIBLE);
			childAdapterViewHolder.txtDeletedApp.setText(child.getName() + " " + context.getResources().getString(R.string.deleted_the_app));
			childAdapterViewHolder.imgChild.setEnabled(false);
			childAdapterViewHolder.txtChildName.setEnabled(false);
			childAdapterViewHolder.switchLockPhone.setEnabled(false);
			childAdapterViewHolder.switchLockPhone.setClickable(false);
		}

		// Battery & Device Info
		childAdapterViewHolder.txtDeviceModel.setText(child.getDeviceModel() != null ? child.getDeviceModel() : "Unknown Device");
		childAdapterViewHolder.txtBatteryLevel.setText(child.getBatteryLevel() + "%");
		
		// Color battery based on level
		int batteryColor;
		if (child.getBatteryLevel() > 50) batteryColor = android.graphics.Color.parseColor("#43A047"); // Green
		else if (child.getBatteryLevel() > 20) batteryColor = android.graphics.Color.parseColor("#FBC02D"); // Amber
		else batteryColor = android.graphics.Color.parseColor("#D32F2F"); // Red
		
		childAdapterViewHolder.txtBatteryLevel.setTextColor(batteryColor);
		childAdapterViewHolder.imgBattery.setColorFilter(batteryColor);
	}
	
	@Override
	public int getItemCount() {
		return childs.size();
	}
	
	public class ChildAdapterViewHolder extends RecyclerView.ViewHolder {
		private CircleImageView imgChild;
		private TextView txtChildName;
		private SwitchCompat switchLockPhone;
		private LinearLayout layoutDeletedApp;
		private TextView txtDeletedApp;
		private TextView txtTotalTime;
		private TextView txtTopApp;
		private ImageView imgTopApp;
		private TextView txtDeviceModel;
		private TextView txtBatteryLevel;
		private ImageView imgBattery;
		
		public ChildAdapterViewHolder(@NonNull View itemView) {
			super(itemView);
			imgChild = itemView.findViewById(R.id.imgChild);
			txtChildName = itemView.findViewById(R.id.txtChildName);
			
			itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onChildClickListener != null) {
						int position = getAdapterPosition();
						if (position != RecyclerView.NO_POSITION)
							//onChildClickListener.onItemClick(v, position);
							onChildClickListener.onItemClick(position);
					}
				}
			});
			
			switchLockPhone = itemView.findViewById(R.id.switchLockPhone);
			switchLockPhone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (buttonView.isPressed()) {
						int position = getAdapterPosition();
						onChildClickListener.onBtnLockClick(isChecked, childs.get(position));
					}
				}
			});
			
			layoutDeletedApp = itemView.findViewById(R.id.layoutDeletedApp);
			layoutDeletedApp.setVisibility(View.GONE);
			txtDeletedApp = itemView.findViewById(R.id.txtDeletedApp);
			txtTotalTime = itemView.findViewById(R.id.txtTotalTime);
			txtTopApp = itemView.findViewById(R.id.txtTopApp);
			imgTopApp = itemView.findViewById(R.id.imgTopApp);
			txtDeviceModel = itemView.findViewById(R.id.txtDeviceModel);
			txtBatteryLevel = itemView.findViewById(R.id.txtBatteryLevel);
			imgBattery = itemView.findViewById(R.id.imgBattery);
		}
	}
	
	
}
