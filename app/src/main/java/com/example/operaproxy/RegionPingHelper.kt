package com.example.operaproxy

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Помощник для измерения пинга (задержки) до регионов.
 */
object RegionPingHelper {
    private const val TAG = "RegionPing"
    private const val PORT = 443
    private const val CONNECT_TIMEOUT_MS = 8000
    private val executor = Executors.newCachedThreadPool()

    private val hostByRegion = mapOf(
        "EU" to listOf("eu0.sec-tunnel.com", "api.sec-tunnel.com", "api2.sec-tunnel.com"),
        "AS" to listOf("as0.sec-tunnel.com", "api.sec-tunnel.com", "api2.sec-tunnel.com"),
        "AM" to listOf("am0.sec-tunnel.com", "api.sec-tunnel.com", "api2.sec-tunnel.com")
    )

    data class PingResult(val success: Boolean, val latencyMs: Long, val error: String? = null)

    fun pingRegion(country: String): PingResult {
        val hosts = hostByRegion[country] ?: return PingResult(false, -1, "Unknown region")
        
        for (host in hosts) {
            val result = measureTcpConnect(host, PORT)
            if (result.success) return result
        }
        return PingResult(false, -1, "All hosts failed")
    }

    fun pingAllRegions(onProgress: (Map<String, PingResult>) -> Unit): Map<String, PingResult> {
        val results = ConcurrentHashMap<String, PingResult>()
        val regions = listOf("EU", "AS", "AM")

        regions.forEach { region ->
            executor.submit {
                results[region] = pingRegion(region)
                onProgress(HashMap(results))
            }
        }

        // Ждем завершения (максимум 12 секунд)
        var elapsed = 0
        while (results.size < 3 && elapsed < 12000) {
            Thread.sleep(200)
            elapsed += 200
        }
        return HashMap(results)
    }

    private fun measureTcpConnect(host: String, port: Int): PingResult {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                val latency = System.currentTimeMillis() - start
                PingResult(true, latency)
            }
        } catch (e: Exception) {
            PingResult(false, -1, e.message)
        }
    }

    fun selectBestRegion(results: Map<String, PingResult>): String? {
        return results.filter { it.value.success && it.value.latencyMs > 0 }
            .minByOrNull { it.value.latencyMs }?.key
    }
}
