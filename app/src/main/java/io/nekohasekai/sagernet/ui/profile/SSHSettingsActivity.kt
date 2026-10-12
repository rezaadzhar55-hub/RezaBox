package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class SSHSettingsActivity : ProfileSettingsActivity<SSHBean>() {

    override fun createEntity() = SSHBean()

    override fun SSHBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverAuthType = authType
        DataStore.serverPassword = password
        DataStore.serverPrivateKey = privateKey
        DataStore.serverPassword1 = privateKeyPassphrase
        DataStore.serverCertificates = publicKey
        DataStore.sshInjectorBugHost = injectorBugHost
        DataStore.sshInjectorPayload = injectorPayload
        DataStore.sshInjectorProxy = injectorProxy
        DataStore.sshInjectorMethod = injectorMethod
    }

    override fun SSHBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        authType = DataStore.serverAuthType
        when (authType) {
            SSHBean.AUTH_TYPE_NONE -> {
            }
            SSHBean.AUTH_TYPE_PASSWORD -> {
                password = DataStore.serverPassword
            }
            SSHBean.AUTH_TYPE_PRIVATE_KEY -> {
                privateKey = DataStore.serverPrivateKey
                privateKeyPassphrase = DataStore.serverPassword1
            }
        }
        publicKey = DataStore.serverCertificates
        injectorBugHost = DataStore.sshInjectorBugHost
        injectorPayload = DataStore.sshInjectorPayload
        injectorProxy = DataStore.sshInjectorProxy
        injectorMethod = DataStore.sshInjectorMethod
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.ssh_preferences)

        // ===== kunci pengaturan (dari file .rz) =====
        val cacheKey = "rzLock:" + DataStore.editingId
        val lockProfile = DataStore.profileCacheStore.getBoolean(cacheKey + ":profile", false)
        val lockPayload = DataStore.profileCacheStore.getBoolean(cacheKey + ":payload", false)
        if (lockProfile || lockPayload) {
            val hide: (String) -> Unit = { key ->
                findPreference<androidx.preference.Preference>(key)?.isVisible = false
            }
            if (lockProfile) {
                // kunci profil: sembunyikan SEMUA (SSH + payload)
                hide(Key.SERVER_ADDRESS)
                hide(Key.SERVER_PORT)
                hide(Key.SERVER_USERNAME)
                hide(Key.SERVER_AUTH_TYPE)
                hide(Key.SERVER_PASSWORD)
                hide(Key.SERVER_PRIVATE_KEY)
                hide(Key.SERVER_PASSWORD1)
                hide(Key.SERVER_CERTIFICATES)
                hide(Key.SSH_INJECTOR_BUGHOST)
                hide(Key.SSH_INJECTOR_PAYLOAD)
                hide(Key.SSH_INJECTOR_PROXY)
                hide(Key.SSH_INJECTOR_METHOD)
            } else {
                // kunci payload: sembunyikan payload saja, SSH tetap tampil & bisa diedit
                hide(Key.SSH_INJECTOR_BUGHOST)
                hide(Key.SSH_INJECTOR_PAYLOAD)
                hide(Key.SSH_INJECTOR_PROXY)
                hide(Key.SSH_INJECTOR_METHOD)
            }
        }
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        val password = findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        val privateKey = findPreference<EditTextPreference>(Key.SERVER_PRIVATE_KEY)!!
        val privateKeyPassphrase = findPreference<EditTextPreference>(Key.SERVER_PASSWORD1)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        val authType = findPreference<SimpleMenuPreference>(Key.SERVER_AUTH_TYPE)!!
        fun updateAuthType(type: Int = DataStore.serverAuthType) {
            password.isVisible = type == SSHBean.AUTH_TYPE_PASSWORD
            privateKey.isVisible = type == SSHBean.AUTH_TYPE_PRIVATE_KEY
            privateKeyPassphrase.isVisible = type == SSHBean.AUTH_TYPE_PRIVATE_KEY
        }
        updateAuthType()
        authType.setOnPreferenceChangeListener { _, newValue ->
            updateAuthType((newValue as String).toInt())
            true
        }
    }

}