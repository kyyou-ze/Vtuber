package com.icegirl.vtuber

import android.content.Context

/**
 * Menyimpan API key Gemini di SharedPreferences lokal (di HP saja, tidak
 * pernah dikirim ke mana pun kecuali langsung ke endpoint Gemini saat
 * memanggil API). User mengisi/menghapusnya lewat dialog pengaturan di app.
 */
object ApiKeyStore {
    private const val PREFS_NAME = "icegirl_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun save(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun hasKey(context: Context): Boolean = get(context).isNotBlank()
}
