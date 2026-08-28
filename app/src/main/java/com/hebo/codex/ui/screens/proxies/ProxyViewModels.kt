package com.hebo.codex.ui.screens.proxies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.DEFAULT_PROXY_CONNECT_TIMEOUT_MILLIS
import com.hebo.codex.domain.DEFAULT_SSH_KEEP_ALIVE_INTERVAL_MILLIS
import com.hebo.codex.domain.DEFAULT_SSH_KEEP_ALIVE_MAX_FAILURES
import com.hebo.codex.domain.HttpProxyAuthentication
import com.hebo.codex.domain.ProxyConfiguration
import com.hebo.codex.domain.ProxyProfile
import com.hebo.codex.domain.ProxyTestResult
import com.hebo.codex.domain.ProxyTlsPolicy
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.Socks5Authentication
import com.hebo.codex.domain.Socks5DnsResolution
import com.hebo.codex.domain.SshAuthentication
import com.hebo.codex.domain.SshHostKeyRecord
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.parseProxyHeaders
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProxyListUiState(
    val proxies: List<ProxyProfile> = emptyList(),
    val removingProxyId: String? = null,
    val errorMessage: UserFacingError? = null,
)

class ProxyListViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProxyListUiState())
    val state: StateFlow<ProxyListUiState> = mutableState

    init {
        viewModelScope.launch {
            repository.proxies.collect { proxies -> mutableState.update { it.copy(proxies = proxies) } }
        }
    }

    fun remove(proxyId: String) {
        val proxy = mutableState.value.proxies.singleOrNull { it.id == proxyId } ?: return
        if (proxy.referencedServerCount != 0 || mutableState.value.removingProxyId != null) return
        viewModelScope.launch {
            mutableState.update { it.copy(removingProxyId = proxyId, errorMessage = null) }
            runSuspendCatching { repository.removeProxy(proxyId) }
                .onFailure { error ->
                    mutableState.update { it.copy(errorMessage = error.toUserFacingError("删除代理失败")) }
                }
            mutableState.update { it.copy(removingProxyId = null) }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProxyListViewModel(repository) }
        }
    }
}

enum class ProxyType {
    HTTP_CONNECT,
    SOCKS5,
    SSH,
}

data class ProxyEditorUiState(
    val isEditing: Boolean,
    val isLoading: Boolean = isEditing,
    val name: String = "",
    val type: ProxyType = ProxyType.HTTP_CONNECT,
    val httpUrl: String = "",
    val httpAuthentication: HttpProxyAuthentication = HttpProxyAuthentication.NONE,
    val httpUsername: String = "",
    val httpHeaders: String = "",
    val httpTlsPolicy: ProxyTlsPolicy = ProxyTlsPolicy.STRICT,
    val host: String = "",
    val port: String = "",
    val socksAuthentication: Socks5Authentication = Socks5Authentication.NONE,
    val socksUsername: String = "",
    val socksDnsResolution: Socks5DnsResolution = Socks5DnsResolution.PROXY,
    val sshUsername: String = "",
    val sshAuthentication: SshAuthentication = SshAuthentication.PRIVATE_KEY,
    val privateKeyUri: String? = null,
    val credential: String = "",
    val credentialConfigured: Boolean = false,
    val clearCredential: Boolean = false,
    val timeoutSeconds: String = (DEFAULT_PROXY_CONNECT_TIMEOUT_MILLIS / 1_000).toString(),
    val keepAliveSeconds: String = (DEFAULT_SSH_KEEP_ALIVE_INTERVAL_MILLIS / 1_000).toString(),
    val keepAliveMaxFailures: String = DEFAULT_SSH_KEEP_ALIVE_MAX_FAILURES.toString(),
    val sshHostKey: SshHostKeyRecord? = null,
    val servers: List<ServerProfile> = emptyList(),
    val testServerId: String? = null,
    val pendingHostKey: ProxyTestResult.HostKeyConfirmationRequired? = null,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isRemovingHostKey: Boolean = false,
    val errorMessage: UserFacingError? = null,
) {
    val isBusy: Boolean
        get() = isSaving || isTesting || isRemovingHostKey

    val canSave: Boolean
        get() = !isLoading && !isBusy && name.isNotBlank()

    val canTest: Boolean
        get() = isEditing && !isLoading && !isBusy && testServerId != null
}

class ProxyEditorViewModel(
    private val repository: CodexRepository,
    private val proxyId: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProxyEditorUiState(isEditing = proxyId != null))
    private val mutableSavedEvents = Channel<Unit>(Channel.BUFFERED)
    private val mutableTestSucceededEvents = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<ProxyEditorUiState> = mutableState
    val savedEvents = mutableSavedEvents.receiveAsFlow()
    val testSucceededEvents = mutableTestSucceededEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.servers.collect { servers ->
                mutableState.update { current ->
                    current.copy(
                        servers = servers,
                        testServerId = current.testServerId?.takeIf { id -> servers.any { it.id == id } }
                            ?: servers.firstOrNull()?.id,
                    )
                }
            }
        }
        if (proxyId == null) {
            mutableState.update { it.copy(isLoading = false) }
        } else {
            load(proxyId)
        }
    }

    fun updateName(value: String) = edit { copy(name = value) }
    fun updateType(value: ProxyType) = edit {
        if (type == value) this else copy(
            type = value,
            port = if (port.isBlank()) defaultPort(value) else port,
            credential = "",
            clearCredential = credentialConfigured || clearCredential,
            credentialConfigured = false,
        )
    }
    fun updateHttpUrl(value: String) = edit { copy(httpUrl = value) }
    fun updateHttpAuthentication(value: HttpProxyAuthentication) = edit {
        if (httpAuthentication == value) this else copy(
            httpAuthentication = value,
            credential = "",
            clearCredential = credentialConfigured || clearCredential,
            credentialConfigured = false,
        )
    }
    fun updateHttpUsername(value: String) = edit { copy(httpUsername = value) }
    fun updateHttpHeaders(value: String) = edit { copy(httpHeaders = value) }
    fun updateHttpTlsPolicy(value: ProxyTlsPolicy) = edit { copy(httpTlsPolicy = value) }
    fun updateHost(value: String) = edit { copy(host = value) }
    fun updatePort(value: String) = edit { copy(port = value.filter(Char::isDigit)) }
    fun updateSocksAuthentication(value: Socks5Authentication) = edit {
        if (socksAuthentication == value) this else copy(
            socksAuthentication = value,
            credential = "",
            clearCredential = credentialConfigured || clearCredential,
            credentialConfigured = false,
        )
    }
    fun updateSocksUsername(value: String) = edit { copy(socksUsername = value) }
    fun updateSocksDnsResolution(value: Socks5DnsResolution) = edit { copy(socksDnsResolution = value) }
    fun updateSshUsername(value: String) = edit { copy(sshUsername = value) }
    fun updateSshAuthentication(value: SshAuthentication) = edit {
        if (sshAuthentication == value) this else copy(
            sshAuthentication = value,
            credential = "",
            clearCredential = credentialConfigured || clearCredential,
            credentialConfigured = false,
        )
    }
    fun updatePrivateKeyUri(value: String) = edit { copy(privateKeyUri = value) }
    fun reportPrivateKeySelectionError(error: Throwable) = edit {
        copy(errorMessage = error.toUserFacingError("无法保存私钥访问权限"))
    }
    fun updateCredential(value: String) = edit {
        copy(credential = value, clearCredential = if (value.isNotEmpty()) false else clearCredential)
    }
    fun clearOptionalCredential() = edit {
        copy(credential = "", credentialConfigured = false, clearCredential = true)
    }
    fun updateTimeoutSeconds(value: String) = edit { copy(timeoutSeconds = value.filter(Char::isDigit)) }
    fun updateKeepAliveSeconds(value: String) = edit { copy(keepAliveSeconds = value.filter(Char::isDigit)) }
    fun updateKeepAliveMaxFailures(value: String) = edit { copy(keepAliveMaxFailures = value.filter(Char::isDigit)) }
    fun updateTestServer(value: String) = edit { copy(testServerId = value) }

    fun save() {
        val snapshot = mutableState.value
        if (!snapshot.canSave) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, errorMessage = null) }
            runSuspendCatching {
                val configuration = snapshot.configuration()
                if (proxyId == null) {
                    repository.addProxy(snapshot.name, configuration, snapshot.credential.takeIf(String::isNotEmpty))
                } else {
                    repository.updateProxy(
                        proxyId,
                        snapshot.name,
                        configuration,
                        snapshot.credential.takeIf(String::isNotEmpty),
                        snapshot.clearCredential,
                    )
                }
            }.onSuccess {
                mutableSavedEvents.send(Unit)
            }.onFailure { error ->
                mutableState.update { it.copy(errorMessage = error.toUserFacingError("保存代理失败")) }
            }
            mutableState.update { it.copy(isSaving = false) }
        }
    }

    fun test() {
        val id = proxyId ?: return
        val targetServerId = mutableState.value.testServerId ?: return
        viewModelScope.launch { test(id, targetServerId) }
    }

    fun confirmHostKey() {
        val id = proxyId ?: return
        val pending = mutableState.value.pendingHostKey ?: return
        val targetServerId = mutableState.value.testServerId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isTesting = true, pendingHostKey = null, errorMessage = null) }
            runSuspendCatching {
                repository.confirmSshHostKey(id, pending.observed)
                mutableState.update {
                    it.copy(sshHostKey = pending.observed.copy(confirmedAtMillis = System.currentTimeMillis()))
                }
                repository.testProxy(id, targetServerId)
            }.onSuccess { result -> handleTestResult(result) }
                .onFailure { error -> showTestFailure(error) }
            mutableState.update { it.copy(isTesting = false) }
        }
    }

    fun dismissHostKey() = edit { copy(pendingHostKey = null) }

    fun removeHostKey() {
        val id = proxyId ?: return
        if (mutableState.value.sshHostKey == null) return
        viewModelScope.launch {
            mutableState.update { it.copy(isRemovingHostKey = true, errorMessage = null) }
            runSuspendCatching { repository.removeSshHostKey(id) }
                .onSuccess { mutableState.update { it.copy(sshHostKey = null) } }
                .onFailure { error ->
                    mutableState.update { it.copy(errorMessage = error.toUserFacingError("移除 SSH 主机密钥失败")) }
                }
            mutableState.update { it.copy(isRemovingHostKey = false) }
        }
    }

    fun clearError() = edit { copy(errorMessage = null) }

    private suspend fun test(id: String, targetServerId: String) {
        mutableState.update { it.copy(isTesting = true, pendingHostKey = null, errorMessage = null) }
        runSuspendCatching { repository.testProxy(id, targetServerId) }
            .onSuccess(::handleTestResult)
            .onFailure(::showTestFailure)
        mutableState.update { it.copy(isTesting = false) }
    }

    private fun handleTestResult(result: ProxyTestResult) {
        when (result) {
            ProxyTestResult.Succeeded -> viewModelScope.launch { mutableTestSucceededEvents.send(Unit) }
            is ProxyTestResult.HostKeyConfirmationRequired -> mutableState.update {
                it.copy(pendingHostKey = result)
            }
        }
    }

    private fun showTestFailure(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.toUserFacingError("测试代理失败")) }
    }

    private fun load(id: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.proxies.first { proxies -> proxies.any { it.id == id } }.single { it.id == id } }
                .onSuccess { profile -> mutableState.update { it.from(profile) } }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isLoading = false, errorMessage = error.toUserFacingError("加载代理失败"))
                    }
                }
        }
    }

    private fun edit(transform: ProxyEditorUiState.() -> ProxyEditorUiState) {
        mutableState.update(transform)
    }

    companion object {
        fun factory(repository: CodexRepository, proxyId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProxyEditorViewModel(repository, proxyId) }
        }
    }
}

private fun ProxyEditorUiState.configuration(): ProxyConfiguration {
    val timeoutMillis = requireNotNull(timeoutSeconds.toLongOrNull()) { "请输入连接超时" } * 1_000
    return when (type) {
        ProxyType.HTTP_CONNECT -> ProxyConfiguration.HttpConnect(
            url = httpUrl,
            authentication = httpAuthentication,
            username = httpUsername.takeIf(String::isNotBlank),
            headers = parseProxyHeaders(httpHeaders),
            connectTimeoutMillis = timeoutMillis,
            tlsPolicy = httpTlsPolicy,
        )
        ProxyType.SOCKS5 -> ProxyConfiguration.Socks5(
            host = host,
            port = requireNotNull(port.toIntOrNull()) { "请输入代理端口" },
            authentication = socksAuthentication,
            username = socksUsername.takeIf(String::isNotBlank),
            dnsResolution = socksDnsResolution,
            connectTimeoutMillis = timeoutMillis,
        )
        ProxyType.SSH -> ProxyConfiguration.Ssh(
            host = host,
            port = requireNotNull(port.toIntOrNull()) { "请输入 SSH 端口" },
            username = sshUsername,
            authentication = sshAuthentication,
            privateKeyUri = privateKeyUri,
            connectTimeoutMillis = timeoutMillis,
            keepAliveIntervalMillis = requireNotNull(keepAliveSeconds.toIntOrNull()) { "请输入 SSH 保活间隔" } * 1_000,
            keepAliveMaxFailures = requireNotNull(keepAliveMaxFailures.toIntOrNull()) { "请输入 SSH 保活失败次数" },
        )
    }
}

private fun ProxyEditorUiState.from(profile: ProxyProfile): ProxyEditorUiState {
    val base = copy(
        isLoading = false,
        name = profile.name,
        credentialConfigured = profile.credentialConfigured,
        sshHostKey = profile.sshHostKey,
    )
    return when (val configuration = profile.configuration) {
        is ProxyConfiguration.HttpConnect -> base.copy(
            type = ProxyType.HTTP_CONNECT,
            httpUrl = configuration.url,
            httpAuthentication = configuration.authentication,
            httpUsername = configuration.username.orEmpty(),
            httpHeaders = configuration.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            httpTlsPolicy = configuration.tlsPolicy,
            timeoutSeconds = (configuration.connectTimeoutMillis / 1_000).toString(),
        )
        is ProxyConfiguration.Socks5 -> base.copy(
            type = ProxyType.SOCKS5,
            host = configuration.host,
            port = configuration.port.toString(),
            socksAuthentication = configuration.authentication,
            socksUsername = configuration.username.orEmpty(),
            socksDnsResolution = configuration.dnsResolution,
            timeoutSeconds = (configuration.connectTimeoutMillis / 1_000).toString(),
        )
        is ProxyConfiguration.Ssh -> base.copy(
            type = ProxyType.SSH,
            host = configuration.host,
            port = configuration.port.toString(),
            sshUsername = configuration.username,
            sshAuthentication = configuration.authentication,
            privateKeyUri = configuration.privateKeyUri,
            timeoutSeconds = (configuration.connectTimeoutMillis / 1_000).toString(),
            keepAliveSeconds = (configuration.keepAliveIntervalMillis / 1_000).toString(),
            keepAliveMaxFailures = configuration.keepAliveMaxFailures.toString(),
        )
    }
}

private fun defaultPort(type: ProxyType): String = when (type) {
    ProxyType.HTTP_CONNECT -> "8080"
    ProxyType.SOCKS5 -> "1080"
    ProxyType.SSH -> "22"
}
