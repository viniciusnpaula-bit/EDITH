package com.edith.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val history = JSONArray()
    private lateinit var chatList: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var micBtn: Button
    private lateinit var sendBtn: Button
    private lateinit var statusDot: View
    private lateinit var thinkingView: TextView
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var comandos: Comandos
    private lateinit var atualizador: Atualizador
    private var ocupado = false

    private val persona = "Você é a EDITH, a IA que vive no celular Android do usuário. " +
        "Responda sempre em português do Brasil, de forma curta (1 a 3 frases), sarcástica e " +
        "espirituosa (estilo filme), mas sempre prestativa e direta ao ponto."

    private val protocolo = """
        CONTROLE DO APARELHO:
        Você pode executar ações no celular. Escreva cada comando sozinho em uma linha, exatamente assim:
        [[ACAO]] {"acao":"nome", ...}
        Ações disponíveis:
        - abrir_app: {"acao":"abrir_app","app":"whatsapp"}
        - lanterna: {"acao":"lanterna","estado":"ligar"} (ou "desligar", "alternar")
        - volume: {"acao":"volume","nivel":30} ou {"acao":"volume","delta":-10} ou {"acao":"volume","estado":"mudo"}
          (fluxo opcional: "musica", "alarme", "chamada", "notificacao", "sistema")
        - alarme: {"acao":"alarme","hora":"07:30","label":"Acordar"}
        - timer: {"acao":"timer","segundos":600,"label":"Macarrão"}
        - lembrete: {"acao":"lembrete","texto":"Comprar café","inicio":"2026-10-22 09:00"}
        - brilho: {"acao":"brilho","nivel":40}
        - painel: {"acao":"painel","nome":"wifi"} (wifi, bluetooth, localizacao, dados, som,
          acessibilidade, notificacoes, bateria, tela, configuracoes)
        - pesquisar: {"acao":"pesquisar","termo":"previsão do tempo"} (abre a busca no navegador)
        - listar_apps: {"acao":"listar_apps"}
        - atualizar: {"acao":"atualizar"} (olha no GitHub se saiu versão nova e oferece instalar)
        - versao: {"acao":"versao"}
        Regras: escreva o texto normal para o usuário e o comando em uma linha separada; no máximo 3
        comandos por resposta; o app executa e devolve o resultado como mensagem de sistema; nunca diga
        que fez algo sem ter mandado o comando; não invente ações fora da lista. Ligações e SMS ainda
        não estão implementados: diga que está em construção. Wi-Fi, Bluetooth e modo avião ainda não
        ligam sozinhos: use "painel" para abrir o ajuste. Se o pedido não for uma ação no aparelho,
        responda normalmente sem comando.
    """.trimIndent()

    companion object {
        private const val REQUEST_MIC_PERMISSION = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#05070D")
        window.navigationBarColor = Color.parseColor("#05070D")
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#05070D"))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(16), dp(12), dp(16), dp(12))
            insets
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        statusDot = View(this).apply {
            background = forma("#35D6E8", 99)
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply {
                marginEnd = dp(10)
            }
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = "EDITH"
            textSize = 22f
            letterSpacing = 0.06f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#E8EEF7"))
        }
        val version = TextView(this).apply {
            text = "v0.4.0 · ASSISTENTE PESSOAL"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        heading.addView(title)
        heading.addView(version)
        val upgradeBtn = botaoRedondo("⟳", "#0E1524", "#94A3B8", 42) {
            escrever("Você", "⟳ procura atualização")
            verificarAtualizacao()
        }.apply { contentDescription = "Procurar atualização da EDITH" }
        val keyBtn = botaoRedondo("Chave", "#0E1524", "#E8EEF7", 58) {
            pedirChave()
        }
        top.addView(statusDot)
        top.addView(heading)
        top.addView(upgradeBtn)
        top.addView(keyBtn)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        chatList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        thinkingView = TextView(this).apply {
            text = "EDITH está pensando…"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        chatList.addView(thinkingView)
        scroll.addView(chatList)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        input = EditText(this).apply {
            hint = "Fale com a EDITH…"
            textSize = 15f
            setSingleLine(false)
            maxLines = 4
            setTextColor(Color.parseColor("#E8EEF7"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = forma("#0E1524", 18, "#1E293B")
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        micBtn = botaoRedondo("🎙", "#0E1524", "#35D6E8", 46) {
            iniciarMicrofone()
        }.apply { contentDescription = "Falar com a EDITH" }
        (micBtn.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
        sendBtn = botaoRedondo("➤", "#35D6E8", "#05070D", 46) {
            enviar()
        }.apply { contentDescription = "Enviar mensagem" }
        bottom.addView(input)
        bottom.addView(micBtn)
        bottom.addView(sendBtn)

        root.addView(top)
        root.addView(scroll)
        root.addView(bottom)
        setContentView(root)

        atualizador = Atualizador(this)
        comandos = Comandos(this) { verificarAtualizacao() }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("pt", "BR"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        escrever(
            "EDITH",
            "Olá. Agora eu mexo no aparelho, não só falo. Tente: \"abre o WhatsApp\", " +
                "\"acende a lanterna\", \"volume 30\", \"alarme 7:30\", \"brilho 40\" ou \"abre o Wi-Fi\". " +
                "Para me atualizar, toque em ⟳."
        )
        checarAtualizacaoAutomatica()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun forma(fill: String, radiusDp: Int, stroke: String? = null): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun botaoRedondo(
        label: String, fill: String, text: String, sizeDp: Int, action: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = if (label == "Chave") 12f else 18f
        isAllCaps = false
        setTextColor(Color.parseColor(text))
        background = forma(fill, sizeDp / 2, if (fill == "#0E1524") "#1E293B" else null)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(8), dp(4), dp(8), dp(4))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
            marginStart = dp(6)
        }
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun atualizarEstado(estado: String) {
        val cor = when (estado) {
            "ouvindo" -> "#F59E0B"
            "pensando" -> "#35D6E8"
            else -> "#35D6E8"
        }
        statusDot.background = forma(cor, 99)
        if (estado == "pensando" || estado == "ouvindo") {
            statusDot.animate().alpha(0.35f).setDuration(550).withEndAction {
                statusDot.animate().alpha(1f).setDuration(550).start()
            }.start()
        } else {
            statusDot.animate().cancel()
            statusDot.alpha = 1f
        }
        micBtn.background = forma(if (estado == "ouvindo") "#F59E0B" else "#0E1524", 99, "#1E293B")
    }
    private fun prefs() = getSharedPreferences("edith", MODE_PRIVATE)

    private fun escrever(quem: String, texto: String) {
        val actionMessage = quem.contains("Aparelho")
        val isUser = quem == "Você"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(4), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                maxWidth.coerceAtLeast(dp(180)), ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = if (actionMessage) forma("#05070D", 4, "#1E293B")
                else if (isUser) forma("#12303A", 18)
                else forma("#0E1524", 18, "#1E293B")
        }
        if (actionMessage) {
            val stripe = View(this).apply {
                background = forma("#35D6E8", 2)
                layoutParams = LinearLayout.LayoutParams(dp(3), dp(24)).apply {
                    marginEnd = dp(9)
                }
            }
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actionRow.addView(stripe)
            val actionText = TextView(this).apply {
                text = "⚙ Aparelho  ·  $texto"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            actionRow.addView(actionText)
            bubble.addView(actionRow)
        } else {
            val sender = TextView(this).apply {
                text = quem.uppercase(Locale("pt", "BR"))
                textSize = 12f
                letterSpacing = 0.05f
                setTextColor(Color.parseColor(if (isUser) "#9FEAF2" else "#94A3B8"))
                alpha = 0.9f
            }
            val body = TextView(this).apply {
                text = texto
                textSize = 15f
                setTextColor(Color.parseColor("#E8EEF7"))
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            bubble.addView(sender)
            bubble.addView(body)
        }
        row.addView(bubble)
        chatList.addView(row, chatList.childCount - 1)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun pedirChave() {
        val et = EditText(this).apply {
            hint = "gsk_..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Chave da Groq")
            .setView(et)
            .setPositiveButton("Salvar") { _, _ ->
                val key = et.text.toString().trim()
                if (key.isEmpty()) {
                    escrever("EDITH", "A chave está vazia. Tente novamente.")
                } else {
                    prefs().edit().putString("key", key).apply()
                    escrever("EDITH", "Chave salva.")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun iniciarMicrofone() {
        if (listening) {
            pararMicrofone()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            escrever("EDITH", "Este Android não disponibilizou um serviço de reconhecimento de fala. Confira as configurações de idioma e reconhecimento de voz do aparelho.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        micBtn.text = "■"
                        atualizarEstado("ouvindo")
                        escrever("EDITH", "Estou ouvindo...")
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        micBtn.text = "…"
                    }
                    override fun onError(error: Int) {
                        listening = false
                        micBtn.text = "🎙"
                        atualizarEstado("idle")
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi o que você disse. Tente novamente."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não ouvi nenhuma fala. Toque no microfone e tente novamente."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "O Android não concedeu permissão para o microfone."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecimento de voz está ocupado. Aguarde um instante."
                            else -> "O reconhecimento de voz falhou (código $error). Tente novamente."
                        }
                        escrever("EDITH", msg)
                    }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        micBtn.text = "🎙"
                        atualizarEstado("idle")
                        val recognized = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (recognized.isNotBlank()) {
                            input.setText(recognized)
                            input.setSelection(input.text.length)
                            escrever("EDITH", "Entendi: \"$recognized\". Vou responder.")
                            enviar()
                        } else {
                            escrever("EDITH", "Não consegui transformar essa fala em texto. Tente novamente.")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            listening = true
            micBtn.text = "■"
            atualizarEstado("ouvindo")
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            listening = false
            micBtn.text = "🎙"
            atualizarEstado("idle")
            escrever("EDITH", "Não consegui iniciar o microfone: ${e.message ?: "erro desconhecido"}.")
        }
    }

    private fun pararMicrofone() {
        speechRecognizer?.cancel()
        listening = false
        micBtn.text = "🎙"
        atualizarEstado("idle")
        escrever("EDITH", "Escuta cancelada.")
    }

    @Deprecated("Callback de permissão mantido para compatibilidade")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarMicrofone()
            } else {
                Toast.makeText(this, "Permita o acesso ao microfone para ditar mensagens.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sistema(): String {
        val agora = SimpleDateFormat("EEEE, dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(Date())
        return buildString {
            appendLine(persona)
            appendLine()
            appendLine("Contexto: agora é $agora no aparelho do usuário.")
            appendLine("Você está na versão ${atualizador.versaoAtual()} do app.")
            appendLine("Apps instalados: ${comandos.nomesApps()}")
            appendLine()
            append(protocolo)
        }
    }

    private fun enviar() {
        if (ocupado) {
            escrever("EDITH", "Calma. Ainda estou resolvendo o pedido anterior.")
            return
        }
        val texto = input.text.toString().trim()
        if (texto.isEmpty()) return
        val key = prefs().getString("key", "") ?: ""
        if (key.isEmpty()) {
            escrever("EDITH", "Primeiro toque em Chave e cole sua chave da Groq.")
            return
        }
        input.setText("")
        escrever("Você", texto)
        history.put(JSONObject().put("role", "user").put("content", texto))
        while (history.length() > 20) history.remove(0)
        ocupado = true
        sendBtn.isEnabled = false
        sendBtn.alpha = 0.45f
        thinkingView.visibility = View.VISIBLE
        atualizarEstado("pensando")
        Thread {
            try {
                val bruto = chamarGroq(key)
                runOnUiThread { aplicarResposta(bruto) }
            } catch (e: Exception) {
                runOnUiThread {
                    escrever("EDITH", "Erro: ${e.message ?: "falha de conexão"}")
                }
            } finally {
                runOnUiThread {
                    ocupado = false
                    sendBtn.isEnabled = true
                    sendBtn.alpha = 1f
                    thinkingView.visibility = View.GONE
                    atualizarEstado("idle")
                }
            }
        }.start()
    }

    /** Separa a fala da EDITH dos comandos e executa os comandos no aparelho. */
    private fun aplicarResposta(bruto: String) {
        val (limpo, acoes) = ParserAcoes.extrair(bruto)
        if (limpo.isNotBlank()) {
            escrever("EDITH", limpo)
            falar(limpo)
        } else if (acoes.isEmpty()) {
            escrever("EDITH", "(não consegui formular uma resposta)")
        }
        for (acao in acoes) {
            val resultado = comandos.executar(acao)
            escrever("⚙ Aparelho", resultado)
            history.put(
                JSONObject().put("role", "system").put("content", "Resultado do aparelho: $resultado")
            )
        }
    }

    private fun chamarGroq(key: String): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sistema()))
        for (i in 0 until history.length()) msgs.put(history.getJSONObject(i))
        val body = JSONObject()
            .put("model", "openai/gpt-oss-120b")
            .put("messages", msgs)

        val c = URL("https://api.groq.com/openai/v1/chat/completions")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "EDITH/0.4.0")
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) return "Erro $code: $txt"

        val out = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        history.put(JSONObject().put("role", "assistant").put("content", out))
        return out
    }

    private fun falar(texto: String) {
        if (ttsReady && texto.isNotBlank()) {
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "edith_resposta")
        }
    }

    // ---------- modo de ação: upgrade da própria EDITH ----------

    /** Procura versão nova no GitHub. Devolve o texto que vai para o chat. */
    private fun verificarAtualizacao(): String {
        escrever("EDITH", "Deixa eu ver no GitHub se saiu versão nova...")
        Thread {
            try {
                val release = atualizador.buscarRelease()
                val atual = atualizador.versaoAtual()
                runOnUiThread {
                    if (Versao.maisNova(release.versao, atual)) {
                        oferecerAtualizacao(release, atual)
                    } else {
                        escrever("EDITH", "Você já está na $atual, a mais nova. Continue me subestimando.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    escrever("EDITH", "Não consegui verificar: ${e.message ?: "erro desconhecido"}.")
                }
            }
        }.start()
        return "Procurando versão nova no GitHub."
    }

    private fun oferecerAtualizacao(release: Atualizador.Release, atual: String) {
        falar("Saiu a ${release.versao}. Quer que eu instale?")
        AlertDialog.Builder(this)
            .setTitle("EDITH ${release.versao} disponível")
            .setMessage("Você está na $atual. Baixar e instalar agora?")
            .setPositiveButton("Instalar") { _, _ -> baixarEInstalar(release) }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun baixarEInstalar(release: Atualizador.Release) {
        escrever("EDITH", "Baixando a ${release.versao}. Não me interrompa.")
        Thread {
            try {
                val apk = atualizador.baixar(release)
                runOnUiThread {
                    escrever("EDITH", "Baixado (${apk.length() / 1024} KB). Abrindo o instalador.")
                    if (atualizador.podeInstalar()) {
                        atualizador.instalar(apk)
                    } else {
                        escrever(
                            "EDITH",
                            "O Android precisa da sua autorização: ligue \"Permitir desta fonte\" e toque em ⟳ de novo."
                        )
                        atualizador.pedirPermissaoInstalacao()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    escrever("EDITH", "O download falhou: ${e.message ?: "erro desconhecido"}.")
                }
            }
        }.start()
    }

    /** Checagem silenciosa, no máximo uma vez a cada 6 horas. */
    private fun checarAtualizacaoAutomatica() {
        val agora = System.currentTimeMillis()
        val ultima = prefs().getLong("ultima_checagem_upgrade", 0L)
        if (agora - ultima < 6 * 60 * 60 * 1000L) return
        prefs().edit().putLong("ultima_checagem_upgrade", agora).apply()
        Thread {
            try {
                val release = atualizador.buscarRelease()
                if (Versao.maisNova(release.versao, atualizador.versaoAtual())) {
                    runOnUiThread {
                        escrever("EDITH", "Saiu a ${release.versao}. Toque em ⟳ que eu instalo.")
                    }
                }
            } catch (e: Exception) {
                // sem internet ou sem release: não incomoda o usuário
            }
        }.start()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}
