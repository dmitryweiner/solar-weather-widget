package com.dmitryweiner.solarweatherwidget.data

sealed class DataError : Exception() {
    object NoInternet : DataError()
    object Timeout : DataError()
    object InvalidData : DataError()
    object ServerError : DataError()
    data class Unknown(override val message: String?) : DataError()
}
