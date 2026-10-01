package com.campusway.core.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 网络连通性判断。
 *
 * 只用于「首次引导需要联网校验导航 Key」这一件事；
 * 日常使用中课表、校园平面图、楼内导览都不依赖它。
 */
object Connectivity {

    /**
     * 当前是否具备可用的互联网连接。
     *
     * 同时检查 INTERNET 与 VALIDATED 两个能力：
     * 只连上 WiFi 但无法出网（例如需要网页认证的校园网）时，
     * VALIDATED 为 false，这里会判为"没网"，避免把 Key 校验失败误报成 Key 无效。
     */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
