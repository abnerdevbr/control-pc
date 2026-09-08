package com.abnerluisz.controlpc

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var lastScrollX = 0f
    private var lastScrollY = 0f

    // true enquanto o dedo esta pressionado e segurando (long press) para selecionar texto
    private var isSelecting = false

    // true enquanto o proprio codigo esta limpando o campo de teclado.
    // evita que o s.clear() do afterTextChanged dispare o TextWatcher de novo
    // (o que mandava um backspace fantasma pro PC depois de cada tecla real)
    private var isClearingKeyboardInput = false

    // sensibilidade do movimento: aumente para o cursor andar mais rapido
    private val sensitivity = 1.5f

    // sensibilidade do scroll com 2 dedos
    private val scrollSensitivity = 1.2f

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

        // empurra o conteudo pra dentro da area segura, sem ficar
        // escondido atras da status bar (topo) nem da barra de
        // navegacao/gesto do sistema (embaixo)
        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        val paddingOriginal = rootLayout.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                paddingOriginal + systemBars.left,
                paddingOriginal + systemBars.top,
                paddingOriginal + systemBars.right,
                // usa o maior entre a barra do sistema e o teclado: quando o
                // teclado abre, esse padding cresce e empurra a barra de
                // digitacao (que fica no fim do layout) pra cima, grudada nele
                paddingOriginal + maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }

        ipInput = findViewById(R.id.ipInput)
        statusText = findViewById(R.id.statusText)
        val connectBtn = findViewById<Button>(R.id.connectBtn)
        val touchpad = findViewById<android.view.View>(R.id.touchpad)
        val leftClickBtn = findViewById<Button>(R.id.leftClickBtn)
        val rightClickBtn = findViewById<Button>(R.id.rightClickBtn)
        val keyboardInput = findViewById<EditText>(R.id.keyboardInput)
        val enterBtn = findViewById<Button>(R.id.enterBtn)
        val copyBtn = findViewById<Button>(R.id.copyBtn)
        val pasteBtn = findViewById<Button>(R.id.pasteBtn)

        // toque simples no touchpad = clique esquerdo
        // toque duplo rapido = clique direito
        // pressionar e segurar (long press) = comeca a "prender" o botao esquerdo,
        // pra poder arrastar e selecionar texto igual num notebook
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                sendClick("left")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                sendClick("right")
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                isSelecting = true
                sendMouseDown("left")
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
            // ele so dispara toque simples/duplo/long-press se nao houve arraste com 2 dedos
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    // um segundo dedo tocou a tela: guarda a posicao media como base do scroll
                    if (event.pointerCount == 2) {
                        lastScrollX = averageX(event)
                        lastScrollY = averageY(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        // 2 dedos na tela = scroll (vertical e horizontal)
                        val avgX = averageX(event)
                        val avgY = averageY(event)
                        val dx = (avgX - lastScrollX) * scrollSensitivity
                        val dy = (avgY - lastScrollY) * scrollSensitivity
                        lastScrollX = avgX
                        lastScrollY = avgY
                        // arrastar os dedos pra cima rola pra cima (scroll natural),
                        // por isso o sinal invertido
                        sendScroll(-dx, -dy)
                    } else {
                        // 1 dedo = move o cursor (e arrasta selecionando, se isSelecting = true)
                        val dx = (event.x - lastX) * sensitivity
                        val dy = (event.y - lastY) * sensitivity
                        lastX = event.x
                        lastY = event.y
                        sendMove(dx, dy)
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    // ainda sobra 1 dedo na tela depois que o outro saiu:
                    // recalcula a base pra nao dar um "pulo" no cursor
                    if (event.pointerCount - 1 == 1) {
                        val remainingIndex = if (event.actionIndex == 0) 1 else 0
                        lastX = event.getX(remainingIndex)
                        lastY = event.getY(remainingIndex)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // soltou o dedo: se estava selecionando, solta o botao esquerdo
                    if (isSelecting) {
                        sendMouseUp("left")
                        isSelecting = false
                    }
                }
            }
            true
        }

        leftClickBtn.setOnClickListener { sendClick("left") }
        rightClickBtn.setOnClickListener { sendClick("right") }
        enterBtn.setOnClickListener { sendKey("enter") }

        // cada alteracao no campo (digitar OU apagar) e refletida no PC
        keyboardInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // ignora a mudanca gerada pelo nosso proprio s.clear() em afterTextChanged,
                // senao ela e interpretada como "apagou N caracteres" e manda backspaces fantasmas
                if (isClearingKeyboardInput) return

                if (count > before) {
                    // foram inseridos caracteres (digitacao, autocorretor, colar texto)
                    val inserted = s?.subSequence(start + before, start + count)?.toString().orEmpty()
                    if (inserted.isNotEmpty()) {
                        val json = JSONObject().put("type", "text").put("value", inserted)
                        client.send(json.toString() + "\n")
                    }
                } else if (before > count) {
                    // foram apagados caracteres: manda um backspace pra cada um
                    val apagados = before - count
                    repeat(apagados) { sendKey("backspace") }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (isClearingKeyboardInput) return
                // o texto de verdade fica so no PC, aqui o campo e sempre limpo
                isClearingKeyboardInput = true
                s?.clear()
                isClearingKeyboardInput = false
            }
        })

        copyBtn.setOnClickListener { sendHotkey(listOf("ctrl", "c")) }
        pasteBtn.setOnClickListener { sendHotkey(listOf("ctrl", "v")) }

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

    private fun averageX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getX(i)
        return sum / event.pointerCount
    }

    private fun averageY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount
    }

    private fun sendMove(dx: Float, dy: Float) {
        val json = JSONObject().put("type", "move").put("dx", dx.toDouble()).put("dy", dy.toDouble())
        client.send(json.toString() + "\n")
    }

    private fun sendScroll(dx: Float, dy: Float) {
        val json = JSONObject().put("type", "scroll").put("dx", dx.toDouble()).put("dy", dy.toDouble())
        client.send(json.toString() + "\n")
    }

    private fun sendClick(button: String) {
        val json = JSONObject().put("type", "click").put("button", button)
        client.send(json.toString() + "\n")
    }

    private fun sendMouseDown(button: String) {
        val json = JSONObject().put("type", "mousedown").put("button", button)
        client.send(json.toString() + "\n")
    }

    private fun sendMouseUp(button: String) {
        val json = JSONObject().put("type", "mouseup").put("button", button)
        client.send(json.toString() + "\n")
    }

    private fun sendKey(key: String) {
        val json = JSONObject().put("type", "key").put("value", key)
        client.send(json.toString() + "\n")
    }

    private fun sendHotkey(keys: List<String>) {
        val json = JSONObject().put("type", "hotkey").put("keys", org.json.JSONArray(keys))
        client.send(json.toString() + "\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
    }
}