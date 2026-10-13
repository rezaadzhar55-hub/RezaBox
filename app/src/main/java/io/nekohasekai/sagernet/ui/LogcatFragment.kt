package io.nekohasekai.sagernet.ui

import io.nekohasekai.sagernet.database.DataStore

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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

        logDeviceInfoOnce()
        reloadSession()
        startAutoRefresh()
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
                    val existing = binding.textview.text
                    val b = android.text.SpannableStringBuilder(deviceHeader + "\n\n")
                    if (existing is android.text.Spannable) b.append(existing) else b.append(existing)
                    binding.textview.text = b
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


    private fun isCurrentProfileLocked(): Boolean {
        return try {
            val id = DataStore.currentProfile
            if (id == 0L) return false
            DataStore.profileCacheStore.getBoolean("rzLock:$id:payload", false) ||
                DataStore.profileCacheStore.getBoolean("rzLock:$id:profile", false)
        } catch (e: Exception) {
            false
        }
    }

    private fun getColorForLine(line: String): ForegroundColorSpan {
        val hi = when {
            line.contains("Starting SSH Service") -> (0xFFFFFF00).toInt()      // kuning
            line.contains("Started Tunnel Tread") -> (0xFFFFFF00).toInt()
            line.contains("Tunnel Type") -> (0xFF00C853).toInt()               // hijau muda
            line.contains("Connecting") -> (0xFFFF9800).toInt()                // oranye
            line.contains("SSH banner") -> (0xFF4FC3F7).toInt()                // biru muda
            line.contains("Fingerprint") -> (0xFFB39DDB).toInt()               // ungu
            line.contains("Key exchange") || line.contains("Using algorithm") -> (0xFF90A4AE).toInt() // abu terang
            line.contains("Authenticating with password") -> (0xFFFFD54F).toInt() // kuning muda
            line.contains("Connected") || line.contains("Accepted password") -> (0xFF00E676).toInt() // HIJAU terang
            line.contains("[PING]") -> (0xFFBDBDBD).toInt() // baris ping netral; angka diwarnai terpisah
            line.contains("101 Switching Protocols") || line.contains("200 Connection established") -> (0xFF00E676).toInt()
            line.contains("Local IP") || line.contains("Format Payload") || line.contains("Mengirim payload") -> (0xFFBDBDBD).toInt()
            line.contains("ERROR") || line.contains("Koneksi Hilang") || line.contains("gagal") -> (0xFFFF5252).toInt()
            line.contains("WARN") -> (0xFFFFAB40).toInt()
            else -> null
        }
        return ForegroundColorSpan(hi ?: Color.GRAY)
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
            if (l.contains("Ping: aktif (interval")) continue  // sembunyikan info interval, hasil ping tetap tampil
            if (l.contains("traffic count")) continue
            // diminta user: baris info device/jaringan & [PING] info dihilangkan
            if (l.contains("Status Jaringan") || l.contains("Local IP") ||
                l.startsWith("Device:")) continue
            if (l.contains("[PING] Device") || l.contains("[PING] Local IP")) continue
            // header tag outbound/inbound dari sing-box (outbound/injector[injector]: dst)
            if (Regex("^(outbound|inbound)/[a-z]+(\\[[a-z]+\\])?:?$").containsMatchIn(l)) continue
            if (l.contains("sing-box started")) continue
            // baris tag-only dengan INFO[..] prefix (INFO[0000] outbound/ssh[proxy]:)
            val lNoTs = l.replace(Regex("^\\[?\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\]?\\s*"), "")
            if (Regex("^(outbound|inbound)/[a-z]+(\\[[a-z]+\\])?:\\s*$").containsMatchIn(lNoTs.trim())) continue
            if (l.contains("[BaseService") || l.contains("Interface$") ||
                l.contains("[Info] [") || l.contains("[Debug] [") ||
                l.contains("[Warning] [") || l.contains("[Error] [")) {
                // baris hanya berupa tag tanpa konten berguna -> buang; kontennya tetap lewat jika ada teks lain
                val stripped = l.replace(Regex("\\[(Info|Debug|Warning|Error)\\] \\[[^\\]]*\\]?"), "").trim()
                if (stripped.isEmpty() || stripped == "]") continue
                // tag-only dari sing-box tidak boleh lolos lewat jalur ini juga
                if (Regex("(outbound|inbound)/[a-z]+(\\[[a-z]+\\])?:\\s*$").containsMatchIn(stripped)) continue
                if (stripped.startsWith("sing-box started")) continue
                // jika sisa kontennya adalah pesan HI yang kita tampilkan, tampilkan versi bersih
                if (stripped.isNotBlank()) {
                    sb.appendLine(stripped)
                    sb.appendLine("────────────────────────────────────────────────────────────────")
                }
                continue
            }
            // sembunyikan inbound/outbound per-koneksi & config JSON (gaya HI: cukup payload & status)
            if (l.contains("inbound connection") || l.contains("outbound connection")) continue
            if (l.contains("inbound packet") || l.contains("outbound packet")) continue
            if (l.contains("inbound/tun") || l.contains("inbound/mixed")) continue
            val jsonKeys = arrayOf("\"dns\"", "\"outbounds\"", "\"inbounds\"", "\"route\"", "\"log\"", "\"type\":", "server_address", "server_port")
            if (jsonKeys.any { l.contains(it) }) continue
            if (l.contains("injector connection to")) continue  // detail teknis ssh settings
            // noise yang tidak bisa diperbaiki: domain MDM Asus sudah NXDOMAIN, UDP tak didukung SSH
            if (l.contains("mdm.asus.com")) continue
            if (l.contains("UDP is not supported by outbound")) continue
            if (l.contains("only IP queries are supported by fakeip")) continue
            // profil terkunci (export .rz): payload & bug host JANGAN ditampilkan di logchat
            if (isCurrentProfileLocked() &&
                (l.contains("Format Payload") || l.contains("Mengirim payload") ||
                    l.contains("payload") || l.contains("bug host", ignoreCase = true) ||
                    l.contains("Bug Host"))) continue
            // sembunyikan SEMUA pengaturan teknis SSH/VMess saat menghubungkan
            val sshNoise = arrayOf(
                "Pengguna:", "Password auth available", "Otentifikasi dengan Sandi",
                "server host key", "host key alg", "ssh://", "Kompresi ssh", "auth attempt",
                "vmess", "vless", "alterId", "network: ws"
            )
            if (sshNoise.any { l.contains(it, ignoreCase = true) }) continue
            // ringkas timestamp panjang: "2026/10/11 19:26:27 " -> "19:26:27 "
            var out = l.replace(Regex("outbound/[a-z]+(\\[[a-z]+\\])?:\\s*"), "")
                      .replace(Regex("inbound/[a-z]+(\\[[a-z]+\\])?:\\s*"), "")
            val t = Regex("""^\d{4}/\d{2}/\d{2} (\d{2}:\d{2}:\d{2}) """).find(l)
            if (t != null) out = l.substring(t.range.last + 1)
            // ringkas INFO[0000] -> hilangkan padding
            out = out.replace(Regex("""INFO\[\d+\] """), "")
            out = out.replace(Regex("""ERROR\[\d+\] """), "ERROR: ")
            out = out.replace(Regex("""WARN\[\d+\] """), "WARN: ")
            if (out.isNotBlank()) {
                sb.appendLine(out)
                sb.appendLine("────────────────────────────────────────────────────────────────")
            }
        }
        // Normalisasi garis: buang semua garis lama, sisipkan TEPAT SATU garis antar baris konten
        val G = "────────────────────────────────────────────────────────────────"
        val contentLines = sb.toString().lines()
            .filter { it.isNotBlank() && !it.startsWith(G) }
        var txt = contentLines.joinToString("\n$G\n")
        if (contentLines.isNotEmpty()) txt = "$G\n" + txt + "\n$G"
        val span = SpannableString(txt)
        var offset = 0
        for (line in span.lines()) {
            val color = getColorForLine(line)
            span.setSpan(
                color, offset, offset + line.length, SPAN_EXCLUSIVE_EXCLUSIVE
            )
            offset += line.length + 1
        }
        // baris "Connected" dibuat bold
        var off3 = 0
        for (line in span.lines()) {
            if (line.contains("Connected")) {
                span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), off3, off3 + line.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            off3 += line.length + 1
        }

        // warnai HANYA latency: "[PING] 87 ms" -> "87 ms" berwarna, sisanya netral
        var off2 = 0
        for (line in span.lines()) {
            val m = Regex("\\[PING\\] ([0-9]+ ms)").find(line)
            if (m != null) {
                val msv = m.groupValues[1].substringBefore(" ").toIntOrNull() ?: 0
                val c = when {
                    msv < 200 -> (0xFF00E676).toInt()
                    msv < 500 -> (0xFFFFEB3B).toInt()
                    else -> (0xFFFF5252).toInt()
                }
                // warnai HANYA "87 ms" (mulai setelah "[PING] ")
                span.setSpan(ForegroundColorSpan(c), off2 + m.range.first + 7, off2 + m.range.last + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            off2 += line.length + 1
        }

        binding.textview.text = if (deviceHeader.isNotBlank()) {
            val b = android.text.SpannableStringBuilder(deviceHeader + "\n\n")
            b.append(span)
            b
        } else span
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
                    lastLen = -1L
                    onMainDispatcher {
                        binding.textview.text = deviceHeader
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