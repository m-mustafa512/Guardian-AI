package com.mustafafyp.guardianai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
	public void onBindViewHolder(@NonNull final ChildAdapterViewHolder holder, int i) {
		final Child child = childs.get(i);
		final int position = i;

		// 1. Set Basic Info
		holder.txtChildName.setText(child.getName());
		Picasso.get().load(child.getProfileImage())
				.placeholder(R.drawable.ic_face)
				.error(R.drawable.ic_face)
				.into(holder.imgChild);

		// 2. Set Stats (Null checks to prevent crashes)
		String usage = child.getDailyUsage() != null ? child.getDailyUsage() : "0h 0m";
		String topApp = child.getTopApp() != null ? child.getTopApp() : "None";
		holder.txtUsageSummary.setText(usage);
		holder.txtTopApp.setText(topApp);

		// 3. Handle Lock Button Logic
		boolean isLocked = (child.getScreenLock() != null && child.getScreenLock().isLocked());
		if (isLocked) {
			holder.btnQuickLock.setText("Unlock");
			holder.btnQuickLock.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")); // Green
		} else {
			holder.btnQuickLock.setText("Lock");
			holder.btnQuickLock.setBackgroundColor(android.graphics.Color.parseColor("#FF5252")); // Red
		}

		// --- BUTTON CLICKS ---

		// Lock/Unlock Action
		holder.btnQuickLock.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					boolean currentLockState = (child.getScreenLock() != null && child.getScreenLock().isLocked());
					// Toggle state: If locked, we want to unlock (false). If unlocked, we want to lock (true).
					onChildClickListener.onBtnLockClick(!currentLockState, child);
				}
			}
		});

		// Map Action
		holder.btnQuickLocation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					// Ideally, we will add a specific map listener later.
					// For now, this opens the details page (same as clicking the card)
					onChildClickListener.onItemClick(position);
					Toast.makeText(context, "Opening Map Module...", Toast.LENGTH_SHORT).show();
				}
			}
		});

		// Stats/Report Action
		holder.btnQuickReport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					onChildClickListener.onItemClick(position);
				}
			}
		});

		// Card Click (Default)
		holder.itemView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					onChildClickListener.onItemClick(position);
				}
			}
		});
	}

	@Override
	public int getItemCount() {
		return childs.size();
	}

	public class ChildAdapterViewHolder extends RecyclerView.ViewHolder {
		CircleImageView imgChild;
		TextView txtChildName, txtUsageSummary, txtTopApp, txtStatus;
		Button btnQuickLock, btnQuickLocation, btnQuickReport;

		public ChildAdapterViewHolder(@NonNull View itemView) {
			super(itemView);
			imgChild = itemView.findViewById(R.id.imgChild);
			txtChildName = itemView.findViewById(R.id.txtChildName);
			txtStatus = itemView.findViewById(R.id.txtStatus);
			txtUsageSummary = itemView.findViewById(R.id.txtUsageSummary);
			txtTopApp = itemView.findViewById(R.id.txtTopApp);

			// Map the New Buttons
			btnQuickLock = itemView.findViewById(R.id.btnQuickLock);
			btnQuickLocation = itemView.findViewById(R.id.btnQuickLocation);
			btnQuickReport = itemView.findViewById(R.id.btnQuickReport);
		}
	}
}