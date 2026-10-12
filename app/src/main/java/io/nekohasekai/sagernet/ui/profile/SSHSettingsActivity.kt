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

    private fun androidx.preference.PreferenceFragmentCompat.enable(keys: List<String>) {
        keys.forEach { k ->
            findPreference<androidx.preference.Preference>(k)?.let {
                it.isEnabled = true
                it.isSelectable = true
            }
        }
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
            val disable: (String) -> Unit = { key ->
                findPreference<androidx.preference.Preference>(key)?.let {
                    it.isEnabled = false
                    it.isSelectable = false
                }
            }
            // server & auth selalu terkunci bila lockProfile
            disable(Key.PROFILE_NAME)
            disable(Key.SERVER_ADDRESS)
            disable(Key.SERVER_PORT)
            disable(Key.SERVER_USERNAME)
            disable(Key.SERVER_AUTH_TYPE)
            disable(Key.SERVER_PASSWORD)
            disable(Key.SERVER_PRIVATE_KEY)
            disable(Key.SERVER_PASSWORD1)
            disable(Key.SERVER_CERTIFICATES)
            if (lockProfile) {
                // kunci profil: SSH + payload semuanya
                disable(Key.SSH_INJECTOR_BUG_HOST)
                disable(Key.SSH_INJECTOR_PAYLOAD)
                disable(Key.SSH_INJECTOR_PROXY)
                disable(Key.SSH_INJECTOR_METHOD)
            } else if (lockPayload) {
                // kunci payload: SSH (server/auth) bebas, payload terkunci
                // (SSH sudah di-enable ulang di bawah)
                enable(listOf(
                    Key.SERVER_ADDRESS, Key.SERVER_PORT, Key.SERVER_USERNAME,
                    Key.SERVER_AUTH_TYPE, Key.SERVER_PASSWORD,
                    Key.SERVER_PRIVATE_KEY, Key.SERVER_PASSWORD1, Key.SERVER_CERTIFICATES
                ))
                disable(Key.SSH_INJECTOR_BUG_HOST)
                disable(Key.SSH_INJECTOR_PAYLOAD)
                disable(Key.SSH_INJECTOR_PROXY)
                disable(Key.SSH_INJECTOR_METHOD)
            }
            // nama profil boleh diedit kalau hanya lockPayload
            if (!lockProfile) {
                enable(listOf(Key.PROFILE_NAME))
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