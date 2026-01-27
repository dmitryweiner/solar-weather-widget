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
 * Repository for fetching solar weather data from NOAA SWPC APIs
 */
object SolarDataRepository {
    private const val TAG = "SolarDataRepository"
    
    private const val KP_INDEX_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    private const val PROTON_FLUX_URL = "https://services.swpc.noaa.gov/json/goes/primary/integral-protons-plot-3-day.json"
    private const val XRAY_FLUX_URL = "https://services.swpc.noaa.gov/json/goes/primary/xrays-3-day.json"

    /**
     * Fetch solar data based on the data source type
     */
    suspend fun fetchData(dataSource: DataSource, limit: Int): List<SolarData> {
        return when (dataSource) {
            DataSource.KP_INDEX -> fetchKpData(limit)
            DataSource.PROTON_FLUX -> fetchProtonFluxData(limit)
            DataSource.XRAY_FLUX -> fetchXrayFluxData(limit)
        }
    }

    private suspend fun fetchKpData(limit: Int): List<SolarData> = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val url = URL(KP_INDEX_URL)
            connection = url.openConnection() as HttpsURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "SolarWeatherWidget/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "Kp API returned HTTP $responseCode")
                throw DataError.ServerError
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            if (jsonArray.length() <= 1) {
                Log.e(TAG, "Empty Kp data received")
                throw DataError.InvalidData
            }

            val dataList = mutableListOf<SolarData>()
            for (i in 1 until jsonArray.length()) {
                val row = jsonArray.getJSONArray(i)
                val timeTag = row.getString(0)
                val kpValue = row.getString(1).toDoubleOrNull() ?: 0.0
                dataList.add(SolarData(timeTag, kpValue, DataSource.KP_INDEX))
            }

            dataList.takeLast(limit)
        } catch (e: DataError) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet connection for Kp data", e)
            throw DataError.NoInternet
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching Kp data", e)
            throw DataError.Timeout
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse Kp JSON", e)
            throw DataError.InvalidData
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching Kp data: ${e.message}", e)
            throw DataError.NoInternet
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching Kp data: ${e.message}", e)
            throw DataError.Unknown(e.message)
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun fetchProtonFluxData(limit: Int): List<SolarData> = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val url = URL(PROTON_FLUX_URL)
            connection = url.openConnection() as HttpsURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "SolarWeatherWidget/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "Proton Flux API returned HTTP $responseCode")
                throw DataError.ServerError
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            if (jsonArray.length() == 0) {
                Log.e(TAG, "Empty Proton Flux data received")
                throw DataError.InvalidData
            }

            val dataList = mutableListOf<SolarData>()
            
            // Sample data to get approximately 'limit' points spread across the time range
            val step = maxOf(1, jsonArray.length() / limit)
            
            for (i in 0 until jsonArray.length() step step) {
                val item = jsonArray.getJSONObject(i)
                val timeTag = item.optString("time_tag", "")
                // Use >=10 MeV proton flux as the primary metric
                val flux = item.optDouble("flux", 0.0)
                
                if (timeTag.isNotEmpty() && flux > 0) {
                    dataList.add(SolarData(timeTag, flux, DataSource.PROTON_FLUX))
                }
            }

            dataList.takeLast(limit)
        } catch (e: DataError) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet connection for Proton Flux data", e)
            throw DataError.NoInternet
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching Proton Flux data", e)
            throw DataError.Timeout
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse Proton Flux JSON", e)
            throw DataError.InvalidData
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching Proton Flux data: ${e.message}", e)
            throw DataError.NoInternet
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching Proton Flux data: ${e.message}", e)
            throw DataError.Unknown(e.message)
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun fetchXrayFluxData(limit: Int): List<SolarData> = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val url = URL(XRAY_FLUX_URL)
            connection = url.openConnection() as HttpsURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "SolarWeatherWidget/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "X-ray Flux API returned HTTP $responseCode")
                throw DataError.ServerError
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            if (jsonArray.length() == 0) {
                Log.e(TAG, "Empty X-ray Flux data received")
                throw DataError.InvalidData
            }

            val dataList = mutableListOf<SolarData>()
            
            // Sample data to get approximately 'limit' points spread across the time range
            val step = maxOf(1, jsonArray.length() / limit)
            
            for (i in 0 until jsonArray.length() step step) {
                val item = jsonArray.getJSONObject(i)
                val timeTag = item.optString("time_tag", "")
                // Use long wavelength X-ray flux (0.1-0.8 nm)
                val flux = item.optDouble("flux", 0.0)
                
                if (timeTag.isNotEmpty() && flux > 0) {
                    dataList.add(SolarData(timeTag, flux, DataSource.XRAY_FLUX))
                }
            }

            dataList.takeLast(limit)
        } catch (e: DataError) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet connection for X-ray Flux data", e)
            throw DataError.NoInternet
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching X-ray Flux data", e)
            throw DataError.Timeout
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse X-ray Flux JSON", e)
            throw DataError.InvalidData
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching X-ray Flux data: ${e.message}", e)
            throw DataError.NoInternet
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching X-ray Flux data: ${e.message}", e)
            throw DataError.Unknown(e.message)
        } finally {
            connection?.disconnect()
        }
    }
}
