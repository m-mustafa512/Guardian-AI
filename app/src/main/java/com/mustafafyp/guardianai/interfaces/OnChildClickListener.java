package com.mustafafyp.guardianai.interfaces;

import com.mustafafyp.guardianai.models.Child;

public interface OnChildClickListener {
    void onItemClick(int position);

    // Module 5: Web Shield (Must accept Child, no boolean)
    void onWebFilterClick(Child child);

    // Module 2: Remote Lock (Must accept boolean and Child)
    void onBtnLockClick(boolean checked, Child child);

    void onLockPhoneSet(int hours, int minutes);
    void onLockCanceled();
}