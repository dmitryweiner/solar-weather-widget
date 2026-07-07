package com.dmitryweiner.solarweatherwidget.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * Repository for fetching solar weather data from NOAA SWPC APIs.
 *
 * The NOAA endpoints occasionally change their JSON shape (array-of-arrays vs
 * array-of-objects, new/renamed fields, extra energy channels). Parsers here try
 * to be tolerant of those variations: field lookups fall through a list of
 * candidate names, and energy filters fall back to "any energy" when the
 * expected channel is absent.
 */
object SolarDataRepository {
    private const val TAG = "SolarDataRepository"

    private const val KP_INDEX_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    private const val PROTON_FLUX_URL = "https://services.swpc.noaa.gov/json/goes/primary/integral-protons-plot-3-day.json"
    private const val XRAY_FLUX_URL = "https://services.swpc.noaa.gov/json/goes/primary/xrays-3-day.json"

    // Primary channels we chart. If NOAA renames them we keep the first
    // available channel that starts with the expected prefix as a fallback.
    private const val PROTON_TARGET_ENERGY = ">=10 MeV"
    private const val XRAY_TARGET_ENERGY = "0.1-0.8nm"

    private val KP_TIME_KEYS = arrayOf("time_tag", "timeTag", "time")
    private val KP_VALUE_KEYS = arrayOf("Kp", "kp", "kp_index", "kpIndex", "estimated_kp")
    private val FLUX_TIME_KEYS = arrayOf("time_tag", "timeTag", "time")
    private val FLUX_VALUE_KEYS = arrayOf("flux", "value", "observed_flux")
    private val FLUX_ENERGY_KEYS = arrayOf("energy", "channel", "band")

    suspend fun fetchData(dataSource: DataSource, limit: Int): List<SolarData> {
        return when (dataSource) {
            DataSource.KP_INDEX -> fetchKpData(limit)
            DataSource.PROTON_FLUX -> fetchFluxData(
                url = PROTON_FLUX_URL,
                targetEnergy = PROTON_TARGET_ENERGY,
                dataSource = DataSource.PROTON_FLUX,
                limit = limit,
            )
            DataSource.XRAY_FLUX -> fetchFluxData(
                url = XRAY_FLUX_URL,
                targetEnergy = XRAY_TARGET_ENERGY,
                dataSource = DataSource.XRAY_FLUX,
                limit = limit,
            )
        }
    }

    private suspend fun fetchKpData(limit: Int): List<SolarData> = withContext(Dispatchers.IO) {
        val json = fetchJson(KP_INDEX_URL, "Kp")
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse Kp JSON as array", e)
            throw DataError.InvalidData
        }

        val parsed = parseKpArray(array)
        if (parsed.isEmpty()) {
            Log.e(TAG, "Kp API returned no usable rows")
            throw DataError.InvalidData
        }
        parsed.takeLast(limit)
    }

    private suspend fun fetchFluxData(
        url: String,
        targetEnergy: String,
        dataSource: DataSource,
        limit: Int,
    ): List<SolarData> = withContext(Dispatchers.IO) {
        val json = fetchJson(url, dataSource.name)
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse $dataSource JSON as array", e)
            throw DataError.InvalidData
        }

        val filtered = parseFluxArray(array, targetEnergy, dataSource)
        if (filtered.isEmpty()) {
            Log.e(TAG, "$dataSource API returned no usable rows")
            throw DataError.InvalidData
        }
        downsample(filtered, limit)
    }

    /**
     * Handles both legacy (array-of-arrays with header row) and current
     * (array-of-objects) Kp payload shapes.
     */
    private fun parseKpArray(array: JSONArray): List<SolarData> {
        if (array.length() == 0) return emptyList()

        val first = array.opt(0)
        return when (first) {
            is JSONObject -> parseKpObjectArray(array)
            is JSONArray -> parseKpLegacyArray(array)
            else -> emptyList()
        }
    }

    private fun parseKpObjectArray(array: JSONArray): List<SolarData> {
        val out = mutableListOf<SolarData>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val time = obj.findString(*KP_TIME_KEYS) ?: continue
            val value = obj.findDouble(*KP_VALUE_KEYS) ?: continue
            out.add(SolarData(time, value, DataSource.KP_INDEX))
        }
        return out
    }

    private fun parseKpLegacyArray(array: JSONArray): List<SolarData> {
        // Header row tells us where the time_tag and Kp columns live; fall back
        // to (0, 1) if we can't decode it.
        val header = array.optJSONArray(0)
        var timeIdx = 0
        var valueIdx = 1
        if (header != null) {
            for (i in 0 until header.length()) {
                when (header.optString(i).lowercase()) {
                    "time_tag", "timetag", "time" -> timeIdx = i
                    "kp", "kp_index", "kpindex", "estimated_kp" -> valueIdx = i
                }
            }
        }

        val out = mutableListOf<SolarData>()
        for (i in 1 until array.length()) {
            val row = array.optJSONArray(i) ?: continue
            val time = row.optString(timeIdx).takeIf { it.isNotEmpty() } ?: continue
            val value = row.optString(valueIdx).toDoubleOrNull() ?: continue
            out.add(SolarData(time, value, DataSource.KP_INDEX))
        }
        return out
    }

    /**
     * Parses a flux payload as array-of-objects. Filters by the requested
     * energy channel; if that channel is absent (schema change) falls back to
     * treating every item as usable so the widget still shows something.
     */
    private fun parseFluxArray(
        array: JSONArray,
        targetEnergy: String,
        dataSource: DataSource,
    ): List<SolarData> {
        if (array.length() == 0) return emptyList()

        val hasEnergyField = (0 until array.length())
            .asSequence()
            .mapNotNull { array.optJSONObject(it) }
            .any { obj -> FLUX_ENERGY_KEYS.any { obj.has(it) } }

        val hasTargetEnergy = hasEnergyField && (0 until array.length())
            .asSequence()
            .mapNotNull { array.optJSONObject(it) }
            .any { it.findString(*FLUX_ENERGY_KEYS) == targetEnergy }

        if (hasEnergyField && !hasTargetEnergy) {
            Log.w(TAG, "$dataSource: target energy '$targetEnergy' not present; using all channels")
        }

        val out = mutableListOf<SolarData>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (hasTargetEnergy) {
                val energy = obj.findString(*FLUX_ENERGY_KEYS) ?: continue
                if (energy != targetEnergy) continue
            }
            val time = obj.findString(*FLUX_TIME_KEYS) ?: continue
            val value = obj.findDouble(*FLUX_VALUE_KEYS) ?: continue
            if (value <= 0) continue
            out.add(SolarData(time, value, dataSource))
        }
        return out
    }

    /**
     * Evenly downsamples the tail of [list] to at most [limit] items, keeping
     * the most recent point. Preserves chronological order.
     */
    private fun <T> downsample(list: List<T>, limit: Int): List<T> {
        if (limit <= 0 || list.size <= limit) return list
        val step = list.size.toDouble() / limit
        val picked = ArrayList<T>(limit)
        for (i in 0 until limit) {
            val idx = (i * step).toInt().coerceAtMost(list.size - 1)
            picked.add(list[idx])
        }
        // Ensure the latest point is included so "current" readings are accurate.
        if (picked.last() !== list.last()) picked[picked.size - 1] = list.last()
        return picked
    }

    private fun fetchJson(url: String, label: String): String {
        var connection: HttpsURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "SolarWeatherWidget/1.0")
            }

            val code = connection.responseCode
            if (code != 200) {
                Log.e(TAG, "$label API returned HTTP $code")
                throw DataError.ServerError
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: DataError) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet connection for $label", e)
            throw DataError.NoInternet
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching $label", e)
            throw DataError.Timeout
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching $label: ${e.message}", e)
            throw DataError.NoInternet
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching $label: ${e.message}", e)
            throw DataError.Unknown(e.message)
        } finally {
            connection?.disconnect()
        }
    }

    private fun JSONObject.findString(vararg keys: String): String? {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val v = optString(key, "")
                if (v.isNotEmpty()) return v
            }
        }
        return null
    }

    private fun JSONObject.findDouble(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            when (val v = opt(key)) {
                is Number -> return v.toDouble()
                is String -> v.toDoubleOrNull()?.let { return it }
                else -> {}
            }
        }
        return null
    }
}
