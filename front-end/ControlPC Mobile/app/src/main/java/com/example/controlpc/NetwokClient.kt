package com.example.controlpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

class NetworkClient(private val scope: CoroutineScope) {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val sendChannel = Channel<String>(Channel.UNLIMITED)

    var isConnected = false
        private set

    fun connect(ip: String, port: Int, onResult: (Boolean, String?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val s = Socket(ip, port)
                s.tcpNoDelay = true // manda cada pacote na hora, sem acumular
                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
                isConnected = true
                launch { processSendQueue() }
                withContext(Dispatchers.Main) { onResult(true, null) }
            } catch (e: Exception) {
                isConnected = false
                withContext(Dispatchers.Main) { onResult(false, e.message) }
            }
        }
    }

    private suspend fun processSendQueue() {
        for (msg in sendChannel) {
            try {
                writer?.println(msg)
                if (writer?.checkError() == true) {
                    isConnected = false
                }
            } catch (e: Exception) {
                isConnected = false
            }
        }
    }

    fun send(json: String) {
        if (isConnected) {
            sendChannel.trySend(json)
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            isConnected = false
        }
    }
}