package io.github.unidentified24.spock.data.update

import java.math.BigInteger

object ReleaseVersion {
    private val stable = Regex("""[vV]?[0-9]+(?:\.[0-9]+)+""")
    fun isNewer(remote: String, installed: String): Boolean {
        if (!stable.matches(remote.trim()) || !stable.matches(installed.trim())) return false
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.').map(::BigInteger)
        val a = parts(remote); val b = parts(installed)
        for (index in 0 until maxOf(a.size, b.size)) {
            val result = a.getOrElse(index) { BigInteger.ZERO }.compareTo(b.getOrElse(index) { BigInteger.ZERO })
            if (result != 0) return result > 0
        }
        return false
    }
}
