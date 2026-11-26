package ru.dedmos.dromprofit.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import ru.dedmos.dromprofit.BuildConfig
import ru.dedmos.dromprofit.domain.usecases.comparison.ComparisonSelectionStore

internal class SelectionIdsStore(context: Context) : ComparisonSelectionStore {
    private val preferences = context.getSharedPreferences("comparison-selection", Context.MODE_PRIVATE)
    private val key = if (BuildConfig.DROM_PROFIT_API_BASE_URL.isBlank()) {
        "demo/${BuildConfig.DROM_PROFIT_DEMO_SCENARIO}/${BuildConfig.DROM_PROFIT_DEMO_EMPTY}"
    } else {
        "api/" + BuildConfig.DROM_PROFIT_API_BASE_URL.trimEnd('/').sha256()
    }

    override fun read(): List<String> {
        val value = preferences.getString(key, null) ?: return emptyList()
        return try {
            val ids = JSONArray(value)
            List(ids.length()) { ids.optString(it) }.filter(String::isNotBlank).distinct()
        } catch (_: JSONException) {
            emptyList()
        }
    }

    override fun write(ids: List<String>) {
        preferences.edit().putString(key, JSONArray(ids).toString()).apply()
    }
}
