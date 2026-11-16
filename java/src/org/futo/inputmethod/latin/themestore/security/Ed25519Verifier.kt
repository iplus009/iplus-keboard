package org.futo.inputmethod.latin.themestore.security

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Pure RFC 8032 Ed25519 signature verifier working identically on Android API 24+ and JVM tests
 * with zero third-party cryptographic library dependencies.
 */
object Ed25519Verifier {
    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)
    private val FOUR = BigInteger.valueOf(4)
    private val FIVE = BigInteger.valueOf(5)
    private val EIGHT = BigInteger.valueOf(8)

    private val P: BigInteger = TWO.pow(255).subtract(BigInteger.valueOf(19))
    private val L: BigInteger = TWO.pow(252)
        .add(BigInteger("27742317777372353535851937790883648493"))
    private val D: BigInteger = BigInteger.valueOf(-121665)
        .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val SQRT_M1: BigInteger = TWO.modPow(P.subtract(BigInteger.ONE).divide(FOUR), P)

    private data class ExtPoint(
        val x: BigInteger,
        val y: BigInteger,
        val z: BigInteger,
        val t: BigInteger
    )

    private val NEUTRAL = ExtPoint(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    private val BASE_POINT: ExtPoint by lazy {
        val y = FOUR.multiply(FIVE.modInverse(P)).mod(P)
        val x = recoverX(y, 0) ?: error("Invalid Ed25519 base point")
        ExtPoint(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    fun verify(publicKey32: ByteArray, message: ByteArray, signature64: ByteArray): Boolean {
        if (publicKey32.size != 32 || signature64.size != 64) return false

        val pointA = decodePoint(publicKey32) ?: return false
        val rBytes = signature64.copyOfRange(0, 32)
        val sBytes = signature64.copyOfRange(32, 64)
        val pointR = decodePoint(rBytes) ?: return false

        val s = decodeLittleEndian(sBytes)
        if (s < BigInteger.ZERO || s >= L) return false

        val digest = MessageDigest.getInstance("SHA-512").apply {
            update(rBytes)
            update(publicKey32)
            update(message)
        }.digest()
        val k = decodeLittleEndian(digest).mod(L)

        val lhs = scalarMultiply(scalarMultiply(BASE_POINT, s), EIGHT)
        val rhsSum = pointAdd(pointR, scalarMultiply(pointA, k))
        val rhs = scalarMultiply(rhsSum, EIGHT)
        return pointsEqual(lhs, rhs)
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Odd hex length" }
        return ByteArray(clean.length / 2) { idx ->
            clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
    }

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger {
        val be = ByteArray(bytes.size)
        for (i in bytes.indices) {
            be[bytes.size - 1 - i] = bytes[i]
        }
        return BigInteger(1, be)
    }

    private fun decodePoint(bytes: ByteArray): ExtPoint? {
        if (bytes.size != 32) return null
        val copy = bytes.copyOf()
        val xSign = (copy[31].toInt() ushr 7) and 1
        copy[31] = (copy[31].toInt() and 0x7F).toByte()
        val y = decodeLittleEndian(copy)
        if (y >= P) return null
        val x = recoverX(y, xSign) ?: return null
        return ExtPoint(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    private fun recoverX(y: BigInteger, xSign: Int): BigInteger? {
        val y2 = y.multiply(y).mod(P)
        val num = y2.subtract(BigInteger.ONE).mod(P)
        val den = D.multiply(y2).add(BigInteger.ONE).mod(P)
        if (den == BigInteger.ZERO) return null
        val x2 = num.multiply(den.modInverse(P)).mod(P)
        if (x2 == BigInteger.ZERO) {
            return if (xSign == 1) null else BigInteger.ZERO
        }
        var x = x2.modPow(P.add(THREE).divide(EIGHT), P)
        if (x.multiply(x).mod(P) != x2) {
            x = x.multiply(SQRT_M1).mod(P)
        }
        if (x.multiply(x).mod(P) != x2) return null
        if (x.testBit(0) != (xSign == 1)) {
            x = P.subtract(x)
        }
        return x
    }

    private fun pointAdd(p: ExtPoint, q: ExtPoint): ExtPoint {
        val a = p.y.subtract(p.x).multiply(q.y.subtract(q.x)).mod(P)
        val b = p.y.add(p.x).multiply(q.y.add(q.x)).mod(P)
        val c = TWO.multiply(p.t).multiply(q.t).multiply(D).mod(P)
        val d = TWO.multiply(p.z).multiply(q.z).mod(P)
        val e = b.subtract(a).mod(P)
        val f = d.subtract(c).mod(P)
        val g = d.add(c).mod(P)
        val h = b.add(a).mod(P)
        return ExtPoint(
            x = e.multiply(f).mod(P),
            y = g.multiply(h).mod(P),
            z = f.multiply(g).mod(P),
            t = e.multiply(h).mod(P)
        )
    }

    private fun scalarMultiply(point: ExtPoint, scalar: BigInteger): ExtPoint {
        var acc = NEUTRAL
        var addend = point
        val bits = scalar.bitLength()
        for (i in 0 until bits) {
            if (scalar.testBit(i)) {
                acc = pointAdd(acc, addend)
            }
            addend = pointAdd(addend, addend)
        }
        return acc
    }

    private fun pointsEqual(p: ExtPoint, q: ExtPoint): Boolean {
        val xEq = p.x.multiply(q.z).mod(P) == q.x.multiply(p.z).mod(P)
        val yEq = p.y.multiply(q.z).mod(P) == q.y.multiply(p.z).mod(P)
        return xEq && yEq
    }
}
