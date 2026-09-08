package com.example.controlpc

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = NetworkClient(scope)
    private var lastX = 0f
    private var lastY = 0f

    // sensibilidade do movimento: aumente para o cursor andar mais rapido
    private val sensitivity = 1.5f

    private lateinit var gestureDetector: GestureDetector
    private lateinit var ipInput: EditText
    private lateinit var statusText: TextView

    companion object {
        private const val DISCOVERY_PORT = 5557
        private const val DISCOVERY_REQUEST = "CONTROLPC_DISCOVER"
        private const val DISCOVERY_REPLY = "CONTROLPC_HERE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ipInput = findViewById(R.id.ipInput)
        statusText = findViewById(R.id.statusText)
        val connectBtn = findViewById<Button>(R.id.connectBtn)
        val touchpad = findViewById<android.view.View>(R.id.touchpad)
        val leftClickBtn = findViewById<Button>(R.id.leftClickBtn)
        val rightClickBtn = findViewById<Button>(R.id.rightClickBtn)
        val keyboardInput = findViewById<EditText>(R.id.keyboardInput)

        // toque simples no touchpad = clique esquerdo
        // toque duplo rapido = clique direito
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                sendClick("left")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                sendClick("right")
                return true
            }
        })

        connectBtn.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            statusText.text = "Conectando..."
            client.connect(ip, 5555) { success, error ->
                statusText.text = if (success) "Conectado a $ip" else "Erro: $error"
            }
        }

        touchpad.setOnTouchListener { _, event ->
            // deixa o GestureDetector analisar o evento em paralelo,
            // ele so dispara toque simples/duplo se nao houve arraste
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastX) * sensitivity
                    val dy = (event.y - lastY) * sensitivity
                    lastX = event.x
                    lastY = event.y

                    val json = JSONObject()
                        .put("type", "move")
                        .put("dx", dx.toDouble())
                        .put("dy", dy.toDouble())
                    client.send(json.toString() + "\n")
                }
            }
            true
        }

        leftClickBtn.setOnClickListener { sendClick("left") }
        rightClickBtn.setOnClickListener { sendClick("right") }

        // cada caractere digitado aqui e enviado direto pro PC
        keyboardInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    val ultimoChar = s.last().toString()
                    val json = JSONObject().put("type", "text").put("value", ultimoChar)
                    client.send(json.toString() + "\n")
                    s.clear()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        tryAutoDiscoverAndConnect()
    }

    /** Manda um pedido em broadcast na rede local perguntando se tem
     * algum PC com o servidor rodando. Se achar, preenche o IP e
     * conecta sozinho. Se nao achar em alguns segundos, deixa o
     * campo de IP como esta, para digitar na mao. */
    private fun tryAutoDiscoverAndConnect() {
        statusText.text = "Procurando PC na rede..."
        scope.launch {
            val ip = discoverServer()
            withContext(Dispatchers.Main) {
                if (ip != null) {
                    ipInput.setText(ip)
                    statusText.text = "PC encontrado ($ip), conectando..."
                    client.connect(ip, 5555) { success, error ->
                        statusText.text = if (success) "Conectado a $ip" else "Erro: $error"
                    }
                } else {
                    statusText.text = "Nao encontrei o PC automaticamente, digite o IP"
                }
            }
        }
    }

    private fun discoverServer(): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 3000
            }

            val requestBytes = DISCOVERY_REQUEST.toByteArray()
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val requestPacket = DatagramPacket(requestBytes, requestBytes.size, broadcastAddress, DISCOVERY_PORT)
            socket.send(requestPacket)

            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket) // lanca excecao se ninguem responder no tempo limite

            val texto = String(responsePacket.data, 0, responsePacket.length)
            // localhost nunca e uma resposta valida do PC de verdade,
            // isso so acontece em emuladores com rede virtual isolada
            if (texto == DISCOVERY_REPLY && !responsePacket.address.isLoopbackAddress) {
                responsePacket.address.hostAddress
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun sendClick(button: String) {
        val json = JSONObject().put("type", "click").put("button", button)
        client.send(json.toString() + "\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
    }
}