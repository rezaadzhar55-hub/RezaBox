package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListListener
import libcore.Libcore
import moe.matsuri.nb4a.utils.SendLog
import io.nekohasekai.sagernet.SagerNet
import java.io.File

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    lateinit var binding: LayoutLogcatBinding

    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)

        if (Build.VERSION.SDK_INT >= 23) {
            binding.textview.breakStrategy = 0 // simple
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        reloadSession()
        startAutoRefresh()
        logDeviceInfoOnce()
    }

    // info device/IP/jaringan langsung muncul saat Log dibuka (tanpa perlu start VPN)
    private var deviceHeader: String = ""

    private fun logDeviceInfoOnce() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val cm = io.nekohasekai.sagernet.SagerNet.connectivity
                val net = cm.activeNetwork
                val caps = net?.let { cm.getNetworkCapabilities(it) }
                val netType = when {
                    caps == null -> "UNKNOWN"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "SELULER"
                    else -> "LAIN"
                }
                // operator (untuk seluler)
                var operator = ""
                if (netType == "SELULER") {
                    runCatching {
                        val tm = requireContext().getSystemService(android.telephony.TelephonyManager::class.java)
                        operator = tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: ""
                    }
                }
                val lp = net?.let { cm.getLinkProperties(it) }
                val localIp = lp?.linkAddresses?.firstOrNull {
                    it.address is java.net.Inet4Address
                }?.address?.hostAddress
                val lines = listOf(
                    "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.SUPPORTED_ABIS[0]})",
                    "App version: ${io.nekohasekai.sagernet.BuildConfig.VERSION_NAME}",
                    "Status Jaringan: ${if (caps != null) "CONNECTED" else "PUTUS"} to $netType${if (operator.isNotBlank()) " ($operator)" else ""}",
                    "Local IP: ${localIp ?: "-"}",
                )
                withContext(Dispatchers.Main) {
                    deviceHeader = lines.joinToString("\n")
                    binding.textview.text = deviceHeader + "\n\n" + binding.textview.text
                }
            }
        }
    }

    // auto-refresh: log baru muncul otomatis tanpa tekan refresh
    private var autoRefreshJob: Job? = null
    private var lastLen = -1L

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(1000)
                try {
                    val len = withContext(Dispatchers.IO) {
                        File(SagerNet.application.cacheDir, "neko.log").length()
                    }
                    if (len != lastLen) {
                        lastLen = len
                        withContext(Dispatchers.Main) { reloadSession() }
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    private fun getColorForLine(line: String): ForegroundColorSpan {
        var color = ForegroundColorSpan(Color.GRAY)
        when {
            // log gaya HTTP Injector
            line.contains("[PING]") -> color = ForegroundColorSpan((0xFF1E90FF).toInt()) // biru
            line.contains("101 Switching Protocols") || line.contains("200 Connection established") ||
                line.contains("Terhubung") || line.contains("handshake ok") ||
                line.contains("Accepted password") -> color = ForegroundColorSpan((0xFF00B36B).toInt()) // hijau
            line.contains("Sidik jari") || line.contains("Local IP") ->
                color = ForegroundColorSpan((0xFF00B36B).toInt())
            line.contains("INFO[") || line.contains(" [Info]") -> {
                color = ForegroundColorSpan((0xFF86C166).toInt())
            }

            line.contains("ERROR[") || line.contains(" [Error]") -> {
                color = ForegroundColorSpan(Color.RED)
            }

            line.contains("WARN[") || line.contains(" [Warning]") -> {
                color = ForegroundColorSpan(Color.RED)
            }
        }
        return color
    }

    private fun reloadSession() {
        // rapikan log gaya HI: buang noise DNS/packet, ringkas format
        val rawLines = String(SendLog.getNekoLog(50 * 1024)).lines()
        val sb = StringBuilder()
        for (line in rawLines) {
            val l = line.trim()
            // skip noise: DNS packet, fakeip, geoip extract, GC, chatty
            if (l.isEmpty()) continue
            if (l.contains("inbound packet connection") && l.contains(":53")) continue
            if (l.contains("Extract geoip") || l.contains("Extract >>")) continue
            if (l.contains("Rejecting re-init") || l.contains("NoClassDefFoundError")) continue
            if (l.contains("chatty")) continue
            if (l.contains("Accessing hidden")) continue
            // sembunyikan baris tag internal [Info]/[Debug]/[Warning] [BaseService$Interface$...] dll
            if (l.contains("Ping: aktif (interval") || l.contains("[PingWorker")) continue
            // header tag outbound/inbound dari sing-box (outbound/injector[injector]: dst)
            if (Regex("^(outbound|inbound)/[a-z]+(\\[[a-z]+\\])?:?$").containsMatchIn(l)) continue
            if (l.contains("[BaseService") || l.contains("Interface$") ||
                l.contains("[Info] [") || l.contains("[Debug] [") ||
                l.contains("[Warning] [") || l.contains("[Error] [")) {
                // baris hanya berupa tag tanpa konten berguna -> buang; kontennya tetap lewat jika ada teks lain
                val stripped = l.replace(Regex("\\[(Info|Debug|Warning|Error)\\] \\[[^\\]]*\\]?"), "").trim()
                if (stripped.isEmpty() || stripped == "]") continue
                // jika sisa kontennya adalah pesan HI yang kita tampilkan, tampilkan versi bersih
                sb.appendLine(stripped)
                continue
            }
            // sembunyikan inbound/outbound per-koneksi & config JSON (gaya HI: cukup payload & status)
            if (l.contains("inbound connection") || l.contains("outbound connection")) continue
            if (l.contains("inbound packet") || l.contains("outbound packet")) continue
            if (l.contains("inbound/tun") || l.contains("inbound/mixed")) continue
            val jsonKeys = arrayOf("\"dns\"", "\"outbounds\"", "\"inbounds\"", "\"route\"", "\"log\"", "\"type\":", "server_address", "server_port")
            if (jsonKeys.any { l.contains(it) }) continue
            if (l.contains("injector connection to")) continue  // detail teknis ssh settings
            // sembunyikan SEMUA pengaturan teknis SSH/VMess saat menghubungkan
            val sshNoise = arrayOf(
                "Sidik jari Hostkey", "Key exchange algorithm", "Using algorithm",
                "Pengguna:", "Password auth available", "Otentifikasi dengan Sandi",
                "SSH handshake ok", "server host key", "host key alg", "kex", "cipher",
                "ssh://", "Kompresi ssh", "auth attempt", "Auth success", "handshake",
                "vmess", "vless", "encryption", "alterId", "security", "network: ws"
            )
            if (sshNoise.any { l.contains(it, ignoreCase = true) }) continue
            // ringkas timestamp panjang: "2026/10/11 19:26:27 " -> "19:26:27 "
            var out = l
            val t = Regex("""^\d{4}/\d{2}/\d{2} (\d{2}:\d{2}:\d{2}) """).find(l)
            if (t != null) out = l.substring(t.range.last + 1)
            // ringkas INFO[0000] -> hilangkan padding
            out = out.replace(Regex("""INFO\[\d+\] """), "")
            out = out.replace(Regex("""ERROR\[\d+\] """), "ERROR: ")
            out = out.replace(Regex("""WARN\[\d+\] """), "WARN: ")
            sb.appendLine(out)
        }
        val span = SpannableString(sb.toString())
        var offset = 0
        for (line in span.lines()) {
            val color = getColorForLine(line)
            span.setSpan(
                color, offset, offset + line.length, SPAN_EXCLUSIVE_EXCLUSIVE
            )
            offset += line.length + 1
        }
        binding.textview.text = if (deviceHeader.isNotBlank())
            deviceHeader + "\n\n" + span else span
        binding.textview.clearFocus()
        // 等 textview 完成最终 layout 再滚动到底部
        binding.textview.doOnLayout {
            binding.scroolview.scrollTo(0, binding.textview.height)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_logcat -> {
                runOnDefaultDispatcher {
                    try {
                        Libcore.nekoLogClear()
                        Runtime.getRuntime().exec("/system/bin/logcat -c")
                    } catch (e: Exception) {
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                        return@runOnDefaultDispatcher
                    }
                    onMainDispatcher {
                        binding.textview.text = ""
                    }
                }

            }

            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "NB4A")
                }
            }

            R.id.action_refresh -> {
                reloadSession()
            }
        }
        return true
    }

}