package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.runBlocking

object PreloadedProfiles {

    private const val PREF_KEY = "preloaded_profiles_v1"

    // VMess akun testreza
    private fun vmessBean(): VMessBean {
        return VMessBean().apply {
            name = "Reza VMess (CF)"
            serverAddress = "ws.rezadhar.dpdns.org"
            serverPort = 443
            uuid = "ce957d11-918d-4dbb-98d7-f5670889186d"
            encryption = "auto"
            security = "tls"
            sni = "ws.rezadhar.dpdns.org"
            host = "ws.rezadhar.dpdns.org"
            path = "/vmess"
        }
    }

    // SSH akun sshreza (via Tailscale)
    private fun injectorBean(): VMessBean {
        return VMessBean().apply {
            name = "Reza VMess (NoTLS 80)"
            serverAddress = "sshws.rezadhar.dpdns.org"
            serverPort = 80
            uuid = "ce957d11-918d-4dbb-98d7-f5670889186d"
            encryption = "auto"
            security = ""      // tanpa TLS (NoTLS, port 80)
            host = "sshws.rezadhar.dpdns.org"
            path = "/vmess"
        }
    }

    fun injectIfNeeded() {
        runBlocking {
            try {
                // paksa log level info tiap launch (HP lama sudah punya flag=1)
                DataStore.configurationStore.putString("logLevel", "2")

                val already = DataStore.configurationStore.getString(PREF_KEY).let { it == "1" || it == "2" }
                if (already) return@runBlocking

                val group = GroupManager.createGroup(ProxyGroup().apply {
                    name = "Reza (VPS)"
                    type = GroupType.BASIC
                })

                ProfileManager.createProfile(group.id, vmessBean())
                ProfileManager.createProfile(group.id, injectorBean())

                // aktifkan log level info supaya Logcat tidak kosong
                DataStore.configurationStore.putString("logLevel", "2")
                DataStore.configurationStore.putString(PREF_KEY, "2")
                Logs.i("Preloaded profiles injected")
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }
}
