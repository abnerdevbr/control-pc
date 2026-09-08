package com.abnerluisz.controlpc

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
import kotlin.math.hypot

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = NetworkClient(scope)

    private var lastX = 0f
    private var lastY = 0f
    private var lastScrollX = 0f
    private var lastScrollY = 0f

    // true enquanto o dedo esta pressionado e segurando (long press ou duplo toque)
    // para selecionar texto / arrastar
    private var isSelecting = false

    // usados para detectar o toque com 3 dedos (clique direito): guardam o maior
    // numero de dedos que tocaram a tela durante o gesto atual, o instante em que
    // o primeiro dedo tocou e a posicao inicial, para saber se foi um toque rapido
    // (e nao um arraste ou um gesto de scroll)
    private var maxPointerCount = 1
    private var touchDownTime = 0L
    private var downX = 0f
    private var downY = 0f

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

        // toque com 3 dedos so conta como clique direito se durar menos que isso...
        private const val TAP_TIMEOUT_MS = 300L
        // ...e se os dedos nao andarem mais que isso (em pixels), senao e considerado
        // um arraste/scroll e nao um toque
        private const val TAP_MOVE_THRESHOLD_PX = 40f
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
        val backspaceBtn = findViewById<Button>(R.id.backspaceBtn)
        val deleteBtn = findViewById<Button>(R.id.deleteBtn)
        val copyBtn = findViewById<Button>(R.id.copyBtn)
        val pasteBtn = findViewById<Button>(R.id.pasteBtn)
        val cutBtn = findViewById<Button>(R.id.cutBtn)
        val shutdownBtn = findViewById<ImageButton>(R.id.shutdownBtn)

        // painel de atalhos avancados (setas, F1-F12, volume, etc.), escondido por padrao
        val advancedToggleBtn = findViewById<Button>(R.id.advancedToggleBtn)
        val advancedPanel = findViewById<android.view.View>(R.id.advancedPanel)
        val upBtn = findViewById<Button>(R.id.upBtn)
        val downBtn = findViewById<Button>(R.id.downBtn)
        val leftBtn = findViewById<Button>(R.id.leftBtn)
        val rightBtn = findViewById<Button>(R.id.rightBtn)
        val pageUpBtn = findViewById<Button>(R.id.pageUpBtn)
        val pageDownBtn = findViewById<Button>(R.id.pageDownBtn)
        val insertBtn = findViewById<Button>(R.id.insertBtn)
        val homeBtn = findViewById<Button>(R.id.homeBtn)
        val ctrlBtn = findViewById<Button>(R.id.ctrlBtn)
        val altBtn = findViewById<Button>(R.id.altBtn)
        val spaceBtn = findViewById<Button>(R.id.spaceBtn)
        val winBtn = findViewById<Button>(R.id.winBtn)
        val capsLockBtn = findViewById<Button>(R.id.capsLockBtn)
        val tabBtn = findViewById<Button>(R.id.tabBtn)
        val volumeUpBtn = findViewById<Button>(R.id.volumeUpBtn)
        val volumeDownBtn = findViewById<Button>(R.id.volumeDownBtn)
        val f1Btn = findViewById<Button>(R.id.f1Btn)
        val f2Btn = findViewById<Button>(R.id.f2Btn)
        val f3Btn = findViewById<Button>(R.id.f3Btn)
        val f4Btn = findViewById<Button>(R.id.f4Btn)
        val f5Btn = findViewById<Button>(R.id.f5Btn)
        val f6Btn = findViewById<Button>(R.id.f6Btn)
        val f7Btn = findViewById<Button>(R.id.f7Btn)
        val f8Btn = findViewById<Button>(R.id.f8Btn)
        val f9Btn = findViewById<Button>(R.id.f9Btn)
        val f10Btn = findViewById<Button>(R.id.f10Btn)
        val f11Btn = findViewById<Button>(R.id.f11Btn)
        val f12Btn = findViewById<Button>(R.id.f12Btn)

        // toque simples no touchpad = clique esquerdo
        // duplo toque = ativa o modo de arrasto (igual segurar), pra selecionar
        // texto ou arrastar algo movendo o dedo em seguida
        // pressionar e segurar (long press) = faz a mesma coisa que o duplo toque
        // toque com 3 dedos ao mesmo tempo = clique direito (tratado direto no
        // setOnTouchListener abaixo, o GestureDetector nao lida bem com isso)
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                sendClick("left")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                isSelecting = true
                sendMouseDown("left")
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
                    // avisa o ScrollView pai pra NAO interceptar esse gesto: sem isso,
                    // ele "rouba" o toque assim que detecta um arraste vertical e o
                    // touchpad para de receber eventos (o bug do touchpad travar)
                    touchpad.parent.requestDisallowInterceptTouchEvent(true)

                    lastX = event.x
                    lastY = event.y
                    touchDownTime = event.eventTime
                    maxPointerCount = 1
                    downX = event.x
                    downY = event.y
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    // guarda o maior numero de dedos que tocaram durante o gesto,
                    // pra saber depois se foi um toque com 3 dedos
                    if (event.pointerCount > maxPointerCount) {
                        maxPointerCount = event.pointerCount
                    }
                    // um segundo dedo tocou a tela: guarda a posicao media como base do scroll
                    if (event.pointerCount == 2) {
                        lastScrollX = averageX(event)
                        lastScrollY = averageY(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        // 2 (ou mais) dedos na tela = scroll (vertical e horizontal)
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
                    // devolve o controle pro ScrollView pra ele voltar a rolar
                    // normalmente no resto da tela (fora do touchpad)
                    touchpad.parent.requestDisallowInterceptTouchEvent(false)

                    if (isSelecting) {
                        // soltou o dedo depois de um duplo toque/long press: solta o botao esquerdo
                        sendMouseUp("left")
                        isSelecting = false
                    } else if (maxPointerCount >= 3) {
                        // gesto terminou com 3 dedos tendo tocado a tela: se foi rapido
                        // e sem quase nenhum arraste, conta como clique direito
                        val duration = event.eventTime - touchDownTime
                        val moved = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
                        if (duration < TAP_TIMEOUT_MS && moved < TAP_MOVE_THRESHOLD_PX) {
                            sendClick("right")
                        }
                    }
                }
            }
            true
        }

        leftClickBtn.setOnClickListener { sendClick("left") }
        rightClickBtn.setOnClickListener { sendClick("right") }
        enterBtn.setOnClickListener { sendKey("enter") }
        backspaceBtn.setOnClickListener { sendKey("backspace") }
        deleteBtn.setOnClickListener { sendKey("delete") }

        // cada alteracao no campo (digitar OU apagar) e refletida no PC.
        // diferente de antes, o campo agora MANTEM o texto digitado na tela
        // (nao se autolimpa mais), entao da pra ver o que foi digitado.
        // apagar com o backspace do teclado do celular continua mandando
        // backspace pro PC tambem.
        keyboardInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
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

            override fun afterTextChanged(s: Editable?) {}
        })

        copyBtn.setOnClickListener { sendHotkey(listOf("ctrl", "c")) }
        pasteBtn.setOnClickListener { sendHotkey(listOf("ctrl", "v")) }
        cutBtn.setOnClickListener { sendHotkey(listOf("ctrl", "x")) }
        shutdownBtn.setOnClickListener { confirmShutdown() }

        // o painel comeca escondido (GONE); o botao so alterna visivel/escondido
        advancedToggleBtn.setOnClickListener {
            if (advancedPanel.visibility == android.view.View.VISIBLE) {
                advancedPanel.visibility = android.view.View.GONE
                advancedToggleBtn.text = "Mostrar atalhos avançados"
            } else {
                advancedPanel.visibility = android.view.View.VISIBLE
                advancedToggleBtn.text = "Esconder atalhos avançados"
            }
        }

        upBtn.setOnClickListener { sendKey("up") }
        downBtn.setOnClickListener { sendKey("down") }
        leftBtn.setOnClickListener { sendKey("left") }
        rightBtn.setOnClickListener { sendKey("right") }
        pageUpBtn.setOnClickListener { sendKey("pageup") }
        pageDownBtn.setOnClickListener { sendKey("pagedown") }
        insertBtn.setOnClickListener { sendKey("insert") }
        homeBtn.setOnClickListener { sendKey("home") }
        ctrlBtn.setOnClickListener { sendKey("ctrl") }
        altBtn.setOnClickListener { sendKey("alt") }
        spaceBtn.setOnClickListener { sendKey("space") }
        winBtn.setOnClickListener { sendKey("win") }
        capsLockBtn.setOnClickListener { sendKey("capslock") }
        tabBtn.setOnClickListener { sendKey("tab") }
        volumeUpBtn.setOnClickListener { sendKey("volumeup") }
        volumeDownBtn.setOnClickListener { sendKey("volumedown") }
        f1Btn.setOnClickListener { sendKey("f1") }
        f2Btn.setOnClickListener { sendKey("f2") }
        f3Btn.setOnClickListener { sendKey("f3") }
        f4Btn.setOnClickListener { sendKey("f4") }
        f5Btn.setOnClickListener { sendKey("f5") }
        f6Btn.setOnClickListener { sendKey("f6") }
        f7Btn.setOnClickListener { sendKey("f7") }
        f8Btn.setOnClickListener { sendKey("f8") }
        f9Btn.setOnClickListener { sendKey("f9") }
        f10Btn.setOnClickListener { sendKey("f10") }
        f11Btn.setOnClickListener { sendKey("f11") }
        f12Btn.setOnClickListener { sendKey("f12") }

        tryAutoDiscoverAndConnect()
    }

    /** Pede confirmacao antes de desligar o PC, pra evitar toque acidental. */
    private fun confirmShutdown() {
        AlertDialog.Builder(this)
            .setTitle("Desligar PC")
            .setMessage("Tem certeza que deseja desligar o computador?")
            .setPositiveButton("Desligar") { _, _ -> sendShutdown() }
            .setNegativeButton("Cancelar", null)
            .show()
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

    private fun sendShutdown() {
        val json = JSONObject().put("type", "shutdown")
        client.send(json.toString() + "\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
    }
}