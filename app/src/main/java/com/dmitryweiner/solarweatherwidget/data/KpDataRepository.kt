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

object KpDataRepository {
    private const val TAG = "KpDataRepository"
    private const val API_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"

    private val TIME_KEYS = arrayOf("time_tag", "timeTag", "time")
    private val VALUE_KEYS = arrayOf("Kp", "kp", "kp_index", "kpIndex", "estimated_kp")

    suspend fun fetchKpData(limit: Int): List<KpData> = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val url = URL(API_URL)
            connection = url.openConnection() as HttpsURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "KpIndexWidget/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "NOAA API returned HTTP $responseCode")
                throw DataError.ServerError
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            if (jsonArray.length() == 0) {
                Log.e(TAG, "Empty data received from NOAA API")
                throw DataError.InvalidData
            }

            val parsed = parseKpArray(jsonArray)
            if (parsed.isEmpty()) {
                Log.e(TAG, "No usable rows in NOAA Kp response")
                throw DataError.InvalidData
            }

            parsed.takeLast(limit)
        } catch (e: DataError) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet connection or DNS resolution failed for NOAA API", e)
            throw DataError.NoInternet
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timeout while fetching Kp data from $API_URL", e)
            throw DataError.Timeout
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse JSON response from NOAA API", e)
            throw DataError.InvalidData
        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching Kp data: ${e.message}", e)
            throw DataError.NoInternet
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching Kp data: ${e.javaClass.simpleName} - ${e.message}", e)
            throw DataError.Unknown(e.message)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Handles both the legacy array-of-arrays payload (with a header row) and
     * the current array-of-objects payload. Unknown/malformed rows are skipped
     * rather than aborting the whole parse.
     */
    private fun parseKpArray(array: JSONArray): List<KpData> {
        val first = array.opt(0) ?: return emptyList()
        return when (first) {
            is JSONObject -> parseObjectArray(array)
            is JSONArray -> parseLegacyArray(array)
            else -> emptyList()
        }
    }

    private fun parseObjectArray(array: JSONArray): List<KpData> {
        val out = mutableListOf<KpData>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val time = obj.findString(*TIME_KEYS) ?: continue
            val value = obj.findDouble(*VALUE_KEYS) ?: continue
            out.add(KpData(time, value))
        }
        return out
    }

    private fun parseLegacyArray(array: JSONArray): List<KpData> {
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

        val out = mutableListOf<KpData>()
        for (i in 1 until array.length()) {
            val row = array.optJSONArray(i) ?: continue
            val time = row.optString(timeIdx).takeIf { it.isNotEmpty() } ?: continue
            val value = row.optString(valueIdx).toDoubleOrNull() ?: continue
            out.add(KpData(time, value))
        }
        return out
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
