package io.nekohasekai.sagernet.database

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
    private fun sshBean(): SSHBean {
        return SSHBean().apply {
            name = "Reza SSH (Tailscale)"
            serverAddress = "100.70.100.27"
            serverPort = 22
            username = "sshreza"
            authType = SSHBean.AUTH_TYPE_PASSWORD
            password = "Reza28416"
        }
    }

    fun injectIfNeeded() {
        runBlocking {
            try {
                val already = DataStore.configurationStore.getString(PREF_KEY) == "1"
                if (already) return@runBlocking

                val group = GroupManager.createGroup(ProxyGroup().apply {
                    name = "Reza (VPS)"
                    type = GroupType.BASIC
                })

                ProfileManager.createProfile(group.id, vmessBean())
                ProfileManager.createProfile(group.id, sshBean())

                DataStore.configurationStore.putString(PREF_KEY, "1")
                Logs.i("Preloaded profiles injected")
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }
}
