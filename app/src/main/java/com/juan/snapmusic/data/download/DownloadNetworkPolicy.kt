package com.juan.snapmusic.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class DownloadNetworkPolicy(
    private val context: Context,
) {
    fun isUnmeteredConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun fileParallelism(): Int = if (isUnmeteredConnection()) 4 else 2

    fun muxSourceParallelism(): Int = if (isUnmeteredConnection()) 2 else 1
}
