package com.resxite.gameboost

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import sun.security.x509.*

private fun gen(): Pair<PrivateKey, Certificate> {
    val g = KeyPairGenerator.getInstance("RSA")
    g.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
    val kp = g.generateKeyPair()
    val pub = kp.public
    val priv = kp.private
    val algo = "SHA512withRSA"
    val ext = CertificateExtensions()
    ext.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pub).identifier))
    val name = X500Name("CN=GameBooster")
    val from = Date()
    val to = Date(System.currentTimeMillis() + 3650L * 86400000L)
    ext.set("PrivateKeyUsage", PrivateKeyUsageExtension(from, to))
    val info = X509CertInfo()
    info.set("version", CertificateVersion(2))
    info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
    info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algo)))
    info.set("subject", CertificateSubjectName(name))
    info.set("key", CertificateX509Key(pub))
    info.set("validity", CertificateValidity(from, to))
    info.set("issuer", CertificateIssuerName(name))
    info.set("extensions", ext)
    val c = X509CertImpl(info)
    c.sign(priv, algo)
    return Pair(priv, c)
}

class AdbMgr(ctx: Context) : AbsAdbConnectionManager() {
    private val pk: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val kf = File(ctx.filesDir, "adb_key")
        val cf = File(ctx.filesDir, "adb_cert")
        var k: PrivateKey? = null
        var c: Certificate? = null
        try {
            if (kf.exists() && cf.exists()) {
                k = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
                c = cf.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            }
        } catch (e: Exception) {
            k = null
            c = null
        }
        if (k == null || c == null) {
            val g = gen()
            k = g.first
            c = g.second
            kf.writeBytes(k.encoded)
            cf.writeBytes(c.encoded)
        }
        pk = k!!
        cert = c!!
    }

    override fun getPrivateKey(): PrivateKey = pk
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "GameBooster"
}

object Adb {
    @Volatile
    var pairingPort = 0

    private var mgr: AdbMgr? = null
    private var nsd: NsdManager? = null
    private var disc: NsdManager.DiscoveryListener? = null

    private fun m(ctx: Context): AdbMgr =
        mgr ?: synchronized(this) { mgr ?: AdbMgr(ctx.applicationContext).also { mgr = it } }

    fun connected(ctx: Context): Boolean = try {
        m(ctx).isConnected
    } catch (e: Throwable) {
        false
    }

    fun connect(ctx: Context): Boolean {
        try {
            m(ctx).autoConnect(ctx.applicationContext, 6000L)
        } catch (e: Throwable) {
        }
        return connected(ctx)
    }

    fun pair(ctx: Context, port: Int, code: String): Boolean = try {
        val r: Any? = m(ctx).pair("127.0.0.1", port, code)
        r != false
    } catch (e: Throwable) {
        false
    }

    fun run(ctx: Context, cmd: String): String = try {
        val s = m(ctx).openStream("shell:$cmd")
        try {
            s.openInputStream().bufferedReader().readText()
        } finally {
            s.close()
        }
    } catch (e: Throwable) {
        ""
    }

    fun startPairDiscovery(ctx: Context) {
        stopPairDiscovery()
        pairingPort = 0
        val n = ctx.getSystemService(NsdManager::class.java)
        nsd = n
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String?) {}
            override fun onDiscoveryStopped(t: String?) {}
            override fun onStartDiscoveryFailed(t: String?, e: Int) {}
            override fun onStopDiscoveryFailed(t: String?, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo?) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                n.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo?, e: Int) {}
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        pairingPort = i.port
                    }
                })
            }
        }
        disc = l
        try {
            n.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Throwable) {
        }
    }

    fun stopPairDiscovery() {
        try {
            disc?.let { nsd?.stopServiceDiscovery(it) }
        } catch (e: Throwable) {
        }
        disc = null
    }
}
