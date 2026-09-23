package io.github.chenyurumeng.aghmanager.model

data class AghTlsValidation(
    val validCert: Boolean = false,
    val validChain: Boolean = false,
    val validKey: Boolean = false,
    val validPair: Boolean = false,
    val warning: String = "",
    val subject: String = "",
    val issuer: String = "",
    val keyType: String = "",
    val notBefore: String = "",
    val notAfter: String = "",
    val dnsNames: List<String> = emptyList()
)

data class AghTlsConfig(
    val enabled: Boolean = false,
    val serverName: String = "",
    val forceHttps: Boolean = false,
    val httpsPort: Int = 0,
    val dotPort: Int = 0,
    val doqPort: Int = 0,
    val certificatePem: String = "",
    val certificatePath: String = "",
    val privateKeyPath: String = "",
    val privateKeySaved: Boolean = false,
    val servePlainDns: Boolean = true,
    val dnscryptPort: Int = 0,
    val dnscryptConfigFile: String = "",
    val validation: AghTlsValidation = AghTlsValidation()
) {
    fun toDraft(): AghTlsDraft = AghTlsDraft(
        enabled = enabled,
        serverName = serverName,
        httpsPort = httpsPort.toString(),
        dotPort = dotPort.toString(),
        doqPort = doqPort.toString(),
        certificatePem = certificatePem,
        certificatePath = certificatePath,
        privateKeyPath = privateKeyPath,
        existingPrivateKeySaved = privateKeySaved,
        forceHttps = forceHttps,
        servePlainDns = servePlainDns,
        dnscryptPort = dnscryptPort,
        dnscryptConfigFile = dnscryptConfigFile
    )
}

data class AghTlsDraft(
    val enabled: Boolean = false,
    val serverName: String = "",
    val httpsPort: String = "0",
    val dotPort: String = "0",
    val doqPort: String = "0",
    val certificatePem: String = "",
    val certificatePath: String = "",
    val privateKeyPath: String = "",
    val privateKeyPem: String = "",
    val replacePrivateKey: Boolean = false,
    val existingPrivateKeySaved: Boolean = false,
    val forceHttps: Boolean = false,
    val servePlainDns: Boolean = true,
    val dnscryptPort: Int = 0,
    val dnscryptConfigFile: String = ""
)

data class AghTlsUiState(
    val loading: Boolean = true,
    val validating: Boolean = false,
    val applying: Boolean = false,
    val current: AghTlsConfig? = null,
    val pending: AghTlsDraft? = null,
    val validation: AghTlsValidation? = null,
    val error: String = ""
) {
    val dirty: Boolean
        get() {
            val currentValue = current ?: return false
            val pendingValue = pending ?: return false
            return currentValue.toDraft() != pendingValue
        }
}
