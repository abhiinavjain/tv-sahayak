package com.tvsahayak.app.control

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * The phone's identity towards the box: a self-signed RSA certificate the box remembers after pairing.
 * The box's own certificate is self-signed too, so we pin it on first pairing (trust on first use).
 */
class CertStore(private val context: Context) {
    private val file = File(context.filesDir, "atv_client.p12")
    private val password = "tvsahayak".toCharArray()
    private val prefs = context.getSharedPreferences("atv_pins", Context.MODE_PRIVATE)

    val keyStore: KeyStore by lazy { load() ?: create() }
    val clientCert: X509Certificate get() = keyStore.getCertificate("client") as X509Certificate

    private fun load(): KeyStore? = if (!file.exists()) null else runCatching {
        KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, password) } }
    }.getOrNull()

    private fun create(): KeyStore {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=atvremote/TV Sahayak, O=TV Sahayak, C=IN")
        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 86_400_000L),
            Date(now + 20L * 365 * 86_400_000L), name, kp.public)
            .build(JcaContentSignerBuilder("SHA256WithRSA").build(kp.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("client", kp.private, password, arrayOf(cert))
        }
        file.outputStream().use { ks.store(it, password) }
        return ks
    }

    fun pinnedFingerprint(host: String): String? = prefs.getString(host, null)
    fun pin(host: String, cert: X509Certificate) = prefs.edit().putString(host, fingerprint(cert)).apply()
    fun forget(host: String) = prefs.edit().remove(host).apply()

    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    /** TLS with our client cert. Server certs are checked against the pin (when [host] was paired before). */
    fun socketFactory(host: String?): SSLSocketFactory {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                val pinned = host?.let { pinnedFingerprint(it) } ?: return
                if (fingerprint(chain[0]) != pinned) throw java.security.cert.CertificateException("Box certificate changed; pair again")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(tm), null) }.socketFactory
    }
}
