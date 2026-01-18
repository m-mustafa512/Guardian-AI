package com.mustafafyp.guardianai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView; // <--- ADD THIS LINE

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

		// 2. Set Stats (Safety check to prevent crash)
		String usage = child.getDailyUsage() != null ? child.getDailyUsage() : "0h 0m";
		String topApp = child.getTopApp() != null ? child.getTopApp() : "None";
		holder.txtUsageSummary.setText(usage);
		holder.txtTopApp.setText(topApp);

		// 3. Handle Lock Button Logic (Using your App's Drawables)
		boolean isLocked = (child.getScreenLock() != null && child.getScreenLock().isLocked());
		if (isLocked) {
			holder.btnQuickLock.setText("Unlock");
			// Use your existing green button drawable
			holder.btnQuickLock.setBackgroundResource(R.drawable.button_ok);
		} else {
			holder.btnQuickLock.setText("Lock");
			// Use your existing red button drawable
			holder.btnQuickLock.setBackgroundResource(R.drawable.button_cancel);
		}

		// --- BUTTON CLICKS ---

		// A. Lock/Unlock Action
		holder.btnQuickLock.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					boolean currentLockState = (child.getScreenLock() != null && child.getScreenLock().isLocked());
					// Toggle: If currently locked, we want to unlock (false), and vice versa.
					onChildClickListener.onBtnLockClick(!currentLockState, child);
				}
			}
		});

		// B. Map Action (Module 6)
		holder.btnQuickLocation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					// This opens the details page where the Map is located
					onChildClickListener.onItemClick(position);
				}
			}
		});

		// C. Web Shield Action (Module 5 - NEW)
		holder.btnWebShield.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					// Opens the Web Shield Settings
					onChildClickListener.onWebFilterClick(child);
				}
			}
		});

		// D. Stats/Report Action
		holder.btnQuickReport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onChildClickListener != null) {
					onChildClickListener.onItemClick(position);
				}
			}
		});

		// E. General Card Click
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

	// Paste this at the bottom of ChildAdapter.java, replacing the old ViewHolder class
	// PASTE THIS AT THE VERY BOTTOM OF ChildAdapter.java
	public class ChildAdapterViewHolder extends RecyclerView.ViewHolder {
		de.hdodenhof.circleimageview.CircleImageView imgChild; // Ensure full path if needed
		TextView txtChildName, txtUsageSummary, txtTopApp, txtStatus;
		ImageView imgStatusDot;
		Button btnQuickLock, btnQuickLocation, btnWebShield, btnQuickReport; // <--- MUST HAVE THIS

		public ChildAdapterViewHolder(@NonNull View itemView) {
			super(itemView);
			// 1. Find Text Views & Images
			imgChild = itemView.findViewById(R.id.imgChild);
			txtChildName = itemView.findViewById(R.id.txtChildName);
			txtStatus = itemView.findViewById(R.id.txtStatus);
			imgStatusDot = itemView.findViewById(R.id.imgStatusDot); // This might cause error if XML isn't updated
			txtUsageSummary = itemView.findViewById(R.id.txtUsageSummary);
			txtTopApp = itemView.findViewById(R.id.txtTopApp);

			// 2. Find Action Buttons
			btnQuickLock = itemView.findViewById(R.id.btnQuickLock);
			btnQuickLocation = itemView.findViewById(R.id.btnQuickLocation);

			// --- CRITICAL FIX: THIS LINE PREVENTS THE CRASH ---
			btnWebShield = itemView.findViewById(R.id.btnWebShield);
			// --------------------------------------------------

			btnQuickReport = itemView.findViewById(R.id.btnQuickReport);
		}
	}
}