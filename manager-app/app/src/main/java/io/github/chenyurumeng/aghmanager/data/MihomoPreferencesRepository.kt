package io.github.chenyurumeng.aghmanager.data

import android.content.Context

class MihomoPreferencesRepository(context: Context) {
    companion object {
        private const val PREFS = "agh_manager_mihomo"
        private const val KEY_FAVORITES = "favorite_nodes"
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun favorites(): Set<String> =
        preferences.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    fun toggleFavorite(node: String): Set<String> {
        val next = favorites().toMutableSet()
        if (!next.add(node)) next.remove(node)
        preferences.edit().putStringSet(KEY_FAVORITES, next).apply()
        return next.toSet()
    }
}
