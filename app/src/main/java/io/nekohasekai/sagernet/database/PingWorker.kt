package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL

// Ping gaya HTTP Injector: request berkala ke cdn-cgi/trace via VPN, log hasil.
// Opsional: diatur di Settings (aktif/mati, interval detik, target).
object PingWorker {

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        val enabled = DataStore.pingEnabled
        if (!enabled) return
        val interval = DataStore.pingInterval.coerceIn(5, 600)
        val target = DataStore.pingTarget
        stop()
        job = scope.launch {
            // log IP lokal gaya HI
            try {
                val localIp = java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
                Logs.i("[PING] Local IP: ${localIp?.hostAddress ?: "tidak ketemu"}")
            } catch (e: Exception) {
                Logs.i("[PING] Local IP: gagal deteksi")
            }
            Logs.i("[PING] Ping: aktif (interval ${interval}s -> $target)")
            while (isActive) {
                delay(interval * 1000L)
                try {
                    val start = System.currentTimeMillis()
                    val conn = URL(target).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.setRequestProperty("User-Agent", "RezaBox")
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    val ms = System.currentTimeMillis() - start
                    conn.inputStream.close()
                    Logs.i("[PING] Ping: $code ($ms ms)")
                } catch (e: Exception) {
                    Logs.i("[PING] Ping: gagal (${e.message})")
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
