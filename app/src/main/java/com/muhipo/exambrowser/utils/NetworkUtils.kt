package com.muhipo.exambrowser.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkUtils(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnline(): Boolean {
        // Jika proses sudah terikat/bound ke WiFi lokal, langsung anggap online
        if (connectivityManager.boundNetworkForProcess != null) return true

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun registerNetworkCallback(onNetworkAvailable: () -> Unit, onNetworkLost: () -> Unit): ConnectivityManager.NetworkCallback {
        // Minta khusus jaringan Wi-Fi tanpa mewajibkan internet publik
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // PAKSA SELURUH TRAFFIC APLIKASI UNTUK LEWAT WIFI INI SECARA MUTLAK
                connectivityManager.bindProcessToNetwork(network)
                onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                if (connectivityManager.boundNetworkForProcess == network) {
                    connectivityManager.bindProcessToNetwork(null)
                }
                onNetworkLost()
            }
        }

        try {
            // requestNetwork meminta OS mempertahankan jaringan WiFi aktif (walau data seluler menyala)
            connectivityManager.requestNetwork(request, callback)
        } catch (_: Exception) {
            connectivityManager.registerNetworkCallback(request, callback)
        }

        // Langsung paksa bind ke WiFi yang sedang aktif saat ini tanpa menunggu callback
        forceBindToCurrentWifi()

        return callback
    }

    /**
     * Memaksa aplikasi untuk langsung mengikat (bind) lalu lintas data ke jaringan WiFi yang terhubung saat ini.
     */
    fun forceBindToCurrentWifi() {
        try {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    connectivityManager.bindProcessToNetwork(network)
                    break
                }
            }
        } catch (_: Exception) {
        }
    }

    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
            if (connectivityManager.boundNetworkForProcess != null) {
                connectivityManager.bindProcessToNetwork(null)
            }
        } catch (_: Exception) {
        }
    }
}
