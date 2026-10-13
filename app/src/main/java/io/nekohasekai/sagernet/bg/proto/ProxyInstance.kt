package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.BuildConfig
import android.os.Build
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.ServiceNotification
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.PingWorker
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.runBlocking

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var notTmp = true

    var lastSelectorGroupId = -1L
    var displayProfileName = ServiceNotification.genTitle(profile)

    // for TrafficLooper
    var looper: TrafficLooper? = null

    override fun buildConfig() {
        super.buildConfig()
        lastSelectorGroupId = super.config.selectorGroupId
        //
        // config JSON tidak ditampilkan di logchat (user request)
    }

    // only use this in temporary instance
    fun buildConfigTmp() {
        notTmp = false
        buildConfig()
    }

    override suspend fun init() {
        super.init()
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override suspend fun loadConfig() {
        super.loadConfig()
    }

    override fun launch() {
        box.setAsMain()
        super.launch() // start box
        runOnDefaultDispatcher {
            looper = service?.let { TrafficLooper(it.data, this) }
            looper?.start()
        }
        // ping gaya HI dimulai SETELAH SSH handshake sukses (dilihat LogcatFragment)
    }

    override fun close() {
        super.close()
        PingWorker.stop()
        runBlocking {
            looper?.stop()
            looper = null
        }
    }
}
