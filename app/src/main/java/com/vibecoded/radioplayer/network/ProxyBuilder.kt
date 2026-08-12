package com.vibecoded.radioplayer.network

import com.vibecoded.radioplayer.data.ProxyType
import com.vibecoded.radioplayer.data.Station
import java.net.InetSocketAddress
import java.net.Proxy

object ProxyBuilder {
    fun build(station: Station): Proxy {
        val host = station.proxyHost
        val port = station.proxyPort
        if (station.proxyType == ProxyType.NONE || host.isNullOrBlank() || port == null) {
            return Proxy.NO_PROXY
        }
        val type = when (station.proxyType) {
            ProxyType.HTTP -> Proxy.Type.HTTP
            ProxyType.SOCKS5 -> Proxy.Type.SOCKS
            ProxyType.NONE -> Proxy.Type.DIRECT
        }
        return Proxy(type, InetSocketAddress(host, port))
    }
}
