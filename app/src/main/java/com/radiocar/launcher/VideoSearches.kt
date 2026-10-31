package com.radiocar.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Atajos de búsqueda de videos ("Vallenato", "Rock 80s"…) guardados en la radio. */
class VideoSearches(context: Context) {

    private val prefs = context.getSharedPreferences("video", Context.MODE_PRIVATE)

    fun list(): List<String> =
        (prefs.getString(KEY, null) ?: DEFAULTS.joinToString("\n"))
            .split("\n")
            .filter { it.isNotBlank() }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        save((listOf(q) + list().filter { !it.equals(q, ignoreCase = true) }).take(MAX))
    }

    fun remove(query: String) = save(list().filter { it != query })

    private fun save(items: List<String>) {
        prefs.edit().putString(KEY, items.joinToString("\n")).apply()
    }

    companion object {
        private const val KEY = "searches"
        private const val MAX = 12
        private val DEFAULTS = listOf("Música para manejar", "Éxitos del momento")

        private val YOUTUBE_APPS = setOf("com.google.android.youtube", "app.revanced.android.youtube")

        /**
         * Intents a probar, en orden, para buscar [query] en la app de videos [pkg].
         * YouTube entiende ACTION_SEARCH; NewPipe y otras abren el enlace de resultados.
         */
        fun searchIntents(pkg: String?, query: String): List<Intent> {
            val url = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK
            val out = mutableListOf<Intent>()
            if (pkg != null && pkg in YOUTUBE_APPS) {
                out += Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra("query", query).addFlags(flags)
            }
            if (pkg != null) out += Intent(Intent.ACTION_VIEW, url).setPackage(pkg).addFlags(flags)
            out += Intent(Intent.ACTION_VIEW, url).addFlags(flags)
            return out
        }
    }
}
