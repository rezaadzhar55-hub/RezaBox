package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.runBlocking

object PreloadedProfiles {

    private const val GROUP_NAME = "Reza (VPS)"

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

    private fun injectorBean(): VMessBean {
        return VMessBean().apply {
            name = "Reza VMess (NoTLS 80)"
            serverAddress = "sshws.rezadhar.dpdns.org"
            serverPort = 80
            uuid = "a8f1d5e6-7d97-450b-91ec-71feb321f9c1"
            encryption = "auto"
            security = ""
            host = "sshws.rezadhar.dpdns.org"
            path = "/vmess"
        }
    }

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

    fun injectIfNeeded() {
        runBlocking {
            try {
                DataStore.configurationStore.putString("logLevel", "2")

                val found = SagerDatabase.groupDao.allGroups().firstOrNull { it.name == GROUP_NAME }
                val group: ProxyGroup = found ?: GroupManager.createGroup(ProxyGroup().apply {
                    name = GROUP_NAME
                    type = GroupType.BASIC
                })

                val entities = SagerDatabase.proxyDao.getByGroup(group.id)
                val names = entities.mapNotNull { runCatching { it.requireBean().name }.getOrNull() }

                suspend fun ensure(bean: AbstractBean) {
                    if (names.none { it == bean.name }) {
                        ProfileManager.createProfile(group.id, bean)
                        Logs.i("Preloaded profile added: " + bean.name)
                    }
                }
                ensure(vmessBean())
                ensure(injectorBean())
                ensure(sshInjectorBean())

                // hapus profil SSH Tailscale lama (nama default = alamat:port)
                entities.forEach { pe ->
                    val b = runCatching { pe.requireBean() }.getOrNull()
                    if (b is SSHBean && (b.injectorPayload.isNullOrBlank()) && b.name == "100.70.100.27:22") {
                        runCatching { ProfileManager.deleteProfile(pe.groupId, pe.id) }
                        Logs.i("Old tailscale SSH profile removed")
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }
}
