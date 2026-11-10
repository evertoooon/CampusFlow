package com.everton.campusflow

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class CampusFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        aplicarTema(temaEscuro())
    }

    fun temaEscuro(): Boolean =
        getSharedPreferences("aparencia", Context.MODE_PRIVATE).getBoolean("tema_escuro", false)

    fun escolherTema(escuro: Boolean) {
        getSharedPreferences("aparencia", Context.MODE_PRIVATE)
            .edit().putBoolean("tema_escuro", escuro).apply()
        aplicarTema(escuro)
    }

    private fun aplicarTema(escuro: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (escuro) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )
    }
}
