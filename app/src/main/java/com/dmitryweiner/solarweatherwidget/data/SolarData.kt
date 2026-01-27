package com.dmitryweiner.solarweatherwidget.data

/**
 * Unified data model for all solar weather data sources.
 * Used for chart rendering regardless of the data source.
 */
data class SolarData(
    val timeTag: String,
    val value: Double,
    val dataSource: DataSource
) {
    companion object {
        /**
         * Convert KpData to SolarData
         */
        fun fromKpData(kpData: KpData): SolarData {
            return SolarData(
                timeTag = kpData.timeTag,
                value = kpData.kpIndex,
                dataSource = DataSource.KP_INDEX
            )
        }

        /**
         * Convert a list of KpData to SolarData
         */
        fun fromKpDataList(kpDataList: List<KpData>): List<SolarData> {
            return kpDataList.map { fromKpData(it) }
        }
    }
}

/**
 * Metadata for each data source to help with chart rendering
 */
object DataSourceConfig {
    fun getMaxValue(dataSource: DataSource): Double {
        return when (dataSource) {
            DataSource.KP_INDEX -> 9.0
            DataSource.PROTON_FLUX -> 1e5  // Log scale, typical max for display
            DataSource.XRAY_FLUX -> 1e-3  // Log scale, typical max for display
        }
    }

    fun getMinValue(dataSource: DataSource): Double {
        return when (dataSource) {
            DataSource.KP_INDEX -> 0.0
            DataSource.PROTON_FLUX -> 1e-2  // Log scale minimum
            DataSource.XRAY_FLUX -> 1e-9   // Log scale minimum
        }
    }

    fun useLogScale(dataSource: DataSource): Boolean {
        return when (dataSource) {
            DataSource.KP_INDEX -> false
            DataSource.PROTON_FLUX -> true
            DataSource.XRAY_FLUX -> true
        }
    }
}
