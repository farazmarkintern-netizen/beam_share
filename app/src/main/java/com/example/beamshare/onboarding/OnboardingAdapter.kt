package com.example.beamshare.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingShareFragment()
            1 -> OnboardingHistoryFragment()
            2 -> OnboardingAllInOneFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}