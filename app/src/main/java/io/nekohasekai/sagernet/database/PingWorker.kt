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
            while (isActive) {
                // ping PERTAMA langsung tanpa menunggu interval (muncul saat Connected)
                try {
                    // gaya HI: ukur waktu TCP connect saja (bukan full HTTPS) -> realistis & stabil
                    val uri = URL(target)
                    val host = uri.host
                    val port = if (uri.port > 0) uri.port else if (target.startsWith("https")) 443 else 80
                    // resolve dulu (pakai DNS tunnel)
                    val addr = java.net.InetAddress.getByName(host)
                    val start = System.currentTimeMillis()
                    val sock = java.net.Socket()
                    sock.connect(java.net.InetSocketAddress(addr, port), 5000)
                    val ms = System.currentTimeMillis() - start
                    sock.close()
                    Logs.i("[PING] $ms ms")
                } catch (e: Exception) {
                    Logs.i("[PING] Ping: gagal (TIMEOUT)")
                }
                delay(interval * 1000L)
            }
        }
    }

    fun startIfNeeded() {
        if (job?.isActive == true) return
        start()
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
