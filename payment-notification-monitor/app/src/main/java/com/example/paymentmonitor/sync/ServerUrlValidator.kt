package com.example.paymentmonitor.sync

import com.example.paymentmonitor.BuildConfig
import java.net.URI

object ServerUrlValidator {
    fun normalize(value: String): String {
        val uri = runCatching { URI(value.trim()) }
            .getOrElse { throw IllegalArgumentException("服务地址格式无效") }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "服务地址不能包含用户信息、查询参数或 fragment"
        }
        require(!uri.host.isNullOrBlank()) { "服务地址缺少主机名" }
        val scheme = uri.scheme?.lowercase()
        if (BuildConfig.DEBUG) {
            require(scheme == "https" || (scheme == "http" && isDebugHttpHost(uri.host))) {
                "Debug HTTP 仅允许局域网、localhost 或 10.0.2.2"
            }
        } else {
            require(scheme == "https") { "Release 构建仅允许 HTTPS" }
        }
        require(uri.path.isNullOrBlank() || uri.path == "/") { "服务地址不能包含接口路径" }
        return value.trim().trimEnd('/')
    }

    private fun isDebugHttpHost(host: String): Boolean {
        if (host.equals("localhost", true) || host == "127.0.0.1" || host == "10.0.2.2") return true
        val octets = host.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }
}
