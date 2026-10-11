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
            uuid = "a8f1d5e6-7d97-450b-91ec-71feb321f9c1"
            encryption = "auto"
            security = "tls"
            sni = "ws.rezadhar.dpdns.org"
            host = "ws.rezadhar.dpdns.org"
            path = "/vmess"
        }
    }

    // SSH akun sshreza (via Tailscale)
    private fun sshInjectorBean(): SSHBean {
        return SSHBean().apply {
            name = "Reza SSH Injector (Payload)"
            serverAddress = "100.70.100.27"
            serverPort = 22
            username = "sshreza"
            authType = SSHBean.AUTH_TYPE_PASSWORD
            password = "Reza28416"
            injectorBugHost = "sshws.rezadhar.dpdns.org:80"
            injectorPayload = "GET /inject HTTP/1.1\r\nHost: sshws.rezadhar.dpdns.org\r\nX-Online-Host: sshws.rezadhar.dpdns.org\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: cmV6YWJveHBheWxvYWRrZXkxMg==\r\nSec-WebSocket-Version: 13\r\n\r\n"
        }
    }

private fun injectorBean(): VMessBean {
        return VMessBean().apply {
            name = "Reza VMess (NoTLS 80)"
            serverAddress = "sshws.rezadhar.dpdns.org"
            serverPort = 80
            uuid = "a8f1d5e6-7d97-450b-91ec-71feb321f9c1"
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
                ProfileManager.createProfile(group.id, sshInjectorBean())

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
