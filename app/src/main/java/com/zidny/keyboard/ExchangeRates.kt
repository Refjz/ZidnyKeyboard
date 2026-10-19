package com.zidny.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Exchange rates for the 💱 converter, fetched from a free public source at most every few hours and kept on
 * the phone, so converting works offline with the last rates seen.
 */
object ExchangeRates {

    /** Units of each currency per US dollar, and when the source last updated them. */
    data class Rates(val perDollar: Map<String, Double>, val updatedMillis: Long)

    private const val PREFS = "exchange_rates"
    private const val URL_LATEST = "https://open.er-api.com/v6/latest/USD"
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L
    private var fetching = false

    /** The rates kept on the phone, or null before the first successful fetch. */
    fun saved(context: Context): Rates? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = p.getString("rates", null) ?: return null
        return try {
            val obj = JSONObject(json)
            Rates(obj.keys().asSequence().associateWith { obj.getDouble(it) }, p.getLong("updated", 0))
        } catch (e: org.json.JSONException) {
            null
        }
    }

    /** Fetches new rates when the saved ones are old or missing, then calls [onUpdated] on the main thread. */
    fun refreshIfStale(context: Context, onUpdated: (Rates?) -> Unit) {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (fetching || System.currentTimeMillis() - p.getLong("fetched", 0) < REFRESH_MS) return
        fetching = true
        val main = Handler(Looper.getMainLooper())
        thread {
            val ok = try {
                val connection = URL(URL_LATEST).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val obj = JSONObject(body)
                if (obj.optString("result") != "success") false else {
                    p.edit()
                        .putString("rates", obj.getJSONObject("rates").toString())
                        .putLong("updated", obj.optLong("time_last_update_unix") * 1000)
                        .putLong("fetched", System.currentTimeMillis())
                        .apply()
                    true
                }
            } catch (e: Exception) {
                false
            }
            main.post {
                fetching = false
                onUpdated(if (ok) saved(app) else null)
            }
        }
    }
}
