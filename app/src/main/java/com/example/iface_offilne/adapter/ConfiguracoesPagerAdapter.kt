package com.example.iface_offilne.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.iface_offilne.ui.ConfiguracoesTabFragment
import com.example.iface_offilne.ui.HistoricoTabFragment
import com.example.iface_offilne.ui.SobreTabFragment

class ConfiguracoesPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ConfiguracoesTabFragment()
            1 -> HistoricoTabFragment()
            2 -> SobreTabFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
} 