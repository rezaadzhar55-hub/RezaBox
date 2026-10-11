package io.nekohasekai.sagernet.fmt.ssh

import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

fun buildSingBoxOutboundSSHBean(bean: SSHBean): SingBoxOptions.Outbound_SSHOptions {
    return SingBoxOptions.Outbound_SSHOptions().apply {
        type = "ssh"
        if (bean.injectorMethod == 1 || bean.injectorMethod == 2 || (bean.injectorMethod == 0 && !bean.injectorPayload.isNullOrBlank())) {
            detour = "injector"
        }
        server = bean.serverAddress
        server_port = bean.serverPort
        user = bean.username
        if (bean.publicKey.isNotBlank()) {
            host_key = bean.publicKey.listByLineOrComma()
        }
        when (bean.authType) {
            SSHBean.AUTH_TYPE_PRIVATE_KEY -> {
                private_key = bean.privateKey
                private_key_passphrase = bean.privateKeyPassphrase
            }
            else -> {
                password = bean.password
            }
        }
    }
}
