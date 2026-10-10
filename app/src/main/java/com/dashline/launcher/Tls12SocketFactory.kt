package com.dashline.launcher

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(host: String, port: Int): Socket = enableTls12(delegate.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        enableTls12(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        enableTls12(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        enableTls12(delegate.createSocket(address, port, localAddress, localPort))

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        enableTls12(delegate.createSocket(socket, host, port, autoClose))

    private fun enableTls12(socket: Socket): Socket {
        if (socket is SSLSocket && "TLSv1.2" in socket.supportedProtocols) {
            socket.enabledProtocols = arrayOf("TLSv1.2")
        }
        return socket
    }

    companion object {
        fun install() {
            HttpsURLConnection.setDefaultSSLSocketFactory(
                Tls12SocketFactory(HttpsURLConnection.getDefaultSSLSocketFactory())
            )
        }
    }
}
