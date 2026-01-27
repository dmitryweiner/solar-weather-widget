package com.dmitryweiner.solarweatherwidget.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

object KpDataRepository {
    private const val TAG = "KpDataRepository"
    private const val API_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"

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

            if (jsonArray.length() <= 1) {
                Log.e(TAG, "Empty data received from NOAA API")
                throw DataError.InvalidData
            }

            val dataList = mutableListOf<KpData>()
            for (i in 1 until jsonArray.length()) {
                val row = jsonArray.getJSONArray(i)
                val timeTag = row.getString(0)
                val kpValue = row.getString(1).toDoubleOrNull() ?: 0.0
                dataList.add(KpData(timeTag, kpValue))
            }

            dataList.takeLast(limit)
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
}
