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
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val history = JSONArray()
    private var mainThread: Thread? = null
    private lateinit var memoria: Memoria
    private var escutaContinua = true
    private var falarRespostas = true
    private var ultimaFala = ""
    /** Reabre o microfone sozinho depois da resposta, por alguns segundos. */
    private var retomarEscuta = false
    private var tempoParaEscutar: Runnable? = null
    private val mao = android.os.Handler(android.os.Looper.getMainLooper())
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
    private lateinit var homeRoot: LinearLayout
    private var settingsKeyInput: EditText? = null

    /** Executa na thread da interface: o app pode estar em segundo plano (ex.: microfone). */
    private fun naTela(bloco: () -> Unit) {
        runOnUiThread {
            try {
                bloco()
            } catch (e: Exception) {
                Log.e("EDITH", "Falha ao atualizar a interface", e)
            }
        }
    }

    private val persona = "Você é a EDITH, a IA que vive no celular Android do usuário. " +
        "Responda sempre em português do Brasil, de forma curta (1 a 3 frases), sarcástica e " +
        "espirituosa (estilo filme), mas sempre prestativa e direta ao ponto."

    private val protocolo = """
        CONTROLE DO APARELHO:
        Você pode executar ações no celular. Escreva cada comando sozinho em uma linha, exatamente assim:
        [[ACAO]] {"acao":"nome", ...}
        Ações disponíveis:
        - abrir_camera: {"acao":"abrir_camera"} (abre a câmera do sistema)
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
        - meus_apps: {"acao":"meus_apps"} (mostra quantos e quais apps a EDITH consegue ver)
        - ensinar_app: {"acao":"ensinar_app","apelido":"insta","app":"instagram"}
          (use quando o usuário disser "quando eu falar X, abre Y")
        - atualizar: {"acao":"atualizar"} (olha no GitHub se saiu versão nova e oferece instalar)
        - versao: {"acao":"versao"}
        Regras: escreva o texto normal para o usuário e o comando em uma linha separada; no máximo 3
        comandos por resposta; o app executa e devolve o resultado como mensagem de sistema; nunca diga
        que fez algo sem ter mandado o comando; não invente ações fora da lista. NUNCA repita o JSON
        no meio da frase: o comando vai numa linha só, começando com [[ACAO]]. Não peça confirmação
        para ações simples (abrir app, lanterna, volume, alarme): execute direto e comente depois. Ligações e SMS ainda
        não estão implementados: diga que está em construção. Wi-Fi, Bluetooth e modo avião ainda não
        ligam sozinhos: use "painel" para abrir o ajuste. Se o pedido não for uma ação no aparelho,
        responda normalmente sem comando.
    """.trimIndent()

    companion object {
        private const val REQUEST_MIC_PERMISSION = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instalarCapturaDeFalhas()
        window.statusBarColor = Cores.cor(Cores.FUNDO)
        window.navigationBarColor = Cores.cor(Cores.FUNDO)
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Cores.cor(Cores.FUNDO))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                dp(16) + insets.systemWindowInsetLeft,
                dp(12) + insets.systemWindowInsetTop,
                dp(16) + insets.systemWindowInsetRight,
                dp(12) + insets.systemWindowInsetBottom
            )
            insets
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        statusDot = View(this).apply {
            background = forma(Cores.PRIMARIA, 99)
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
            setTextColor(Cores.cor(Cores.TEXTO))
        }
        val version = TextView(this).apply {
            text = "v0.7.0 · ASSISTENTE PESSOAL"
            textSize = 12f
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
        }
        heading.addView(title)
        heading.addView(version)
        val upgradeBtn = botaoRedondo("⟳", Cores.SUPERFICIE, Cores.TEXTO_SUAVE, 42) {
            escrever("Você", "⟳ procura atualização")
            verificarAtualizacao()
        }.apply { contentDescription = "Procurar atualização da EDITH" }
        val keyBtn = botaoRedondo("⚙", Cores.SUPERFICIE, Cores.PRIMARIA, 46) {
            abrirConfiguracoes()
        }.apply { contentDescription = "Abrir configurações" }
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
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
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
            setTextColor(Cores.cor(Cores.TEXTO))
            setHintTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            background = forma(Cores.SUPERFICIE, 18, Cores.BORDA)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        micBtn = botaoRedondo("🎙", Cores.SUPERFICIE, Cores.PRIMARIA, 46) {
            iniciarMicrofone()
        }.apply { contentDescription = "Falar com a EDITH" }
        (micBtn.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
        mainThread = Thread.currentThread()
        sendBtn = botaoRedondo("➤", Cores.PRIMARIA, Cores.FUNDO, 46) {
            enviar()
        }.apply { contentDescription = "Enviar mensagem" }
        bottom.addView(input)
        bottom.addView(micBtn)
        bottom.addView(sendBtn)

        root.addView(top)
        root.addView(scroll)
        root.addView(bottom)
        root.addView(TextView(this).apply {
            text = Preferencias.resumo(prefs())
            textSize = 11f
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        })
        homeRoot = root
        setContentView(root)

        atualizador = Atualizador(this)
        comandos = Comandos(this) { verificarAtualizacao() }
        memoria = Memoria(this)
        escutaContinua = Preferencias.vozContinua(prefs())
        falarRespostas = Preferencias.falarRespostas(prefs())
        restaurarConversa()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("pt", "BR"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        if (history.length() == 0) {
            escrever(
                "EDITH",
                "Olá. Agora eu mexo no aparelho, não só falo. Tente: \"abre o WhatsApp\", " +
                    "\"acende a lanterna\", \"volume 30\", \"alarme 7:30\", \"brilho 40\" ou \"abre o Wi-Fi\". " +
                    "Com a escuta contínua ligada, toque uma vez no 🎙 e fale à vontade."
            )
            atalhosRapidos()
        }
        checarAtualizacaoAutomatica()
    }

    /** Sugestões tocáveis para começar sem digitar. */
    private fun atalhosRapidos() {
        val sugestoes = listOf("Que horas são?", "Acende a lanterna", "Abre o WhatsApp", "Alarme 7:30")
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        sugestoes.forEach { sugestao ->
            linha.addView(Button(this).apply {
                text = sugestao
                textSize = 12f
                isAllCaps = false
                setTextColor(Cores.cor(Cores.PRIMARIA_ESCURA))
                background = forma(Cores.CARTAO, 20, Cores.BORDA)
                stateListAnimator = null
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    input.setText(sugestao)
                    enviar()
                }
            })
        }
        chatList.addView(linha, chatList.childCount - 1)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
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
        label: String, fill: String, textColorHex: String, sizeDp: Int, action: () -> Unit
    ): Button = Button(this).apply {
        this.text = label
        textSize = if (label.length > 3) 15f else 18f
        isAllCaps = false
        setTextColor(Color.parseColor(textColorHex))
        background = forma(fill, sizeDp / 2, if (fill == Cores.SUPERFICIE) Cores.BORDA else null)
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

    /** Botão de largura total, com texto legível. */
    private fun botaoLargo(
        label: String,
        fill: String = Cores.SUPERFICIE,
        textoCor: String = Cores.PRIMARIA,
        acao: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Cores.cor(textoCor))
        background = forma(fill, 14, Cores.BORDA)
        stateListAnimator = null
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        setOnClickListener { acao() }
    }

    /** Linha de ajuste com título, explicação e interruptor. */
    private fun interruptor(
        titulo: String,
        explicacao: String,
        ligadoInicial: Boolean,
        aoMudar: (Boolean) -> Unit
    ): LinearLayout {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textos.addView(TextView(this).apply {
            text = titulo
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Cores.cor(Cores.TEXTO))
        })
        textos.addView(TextView(this).apply {
            text = explicacao
            textSize = 12f
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            setPadding(0, dp(3), dp(12), 0)
        })
        val chave = android.widget.Switch(this).apply {
            isChecked = ligadoInicial
            setOnCheckedChangeListener { _, marcado -> aoMudar(marcado) }
        }
        linha.addView(textos)
        linha.addView(chave)
        return linha
    }

    private fun atualizarEstado(estado: String) {
        if (Thread.currentThread() !== mainThread) {
            naTela { atualizarEstado(estado) }
            return
        }
        val cor = when (estado) {
            "ouvindo" -> Cores.ALERTA
            "pensando" -> Cores.PRIMARIA
            else -> Cores.PRIMARIA
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
        micBtn.background = forma(if (estado == "ouvindo") Cores.ALERTA else Cores.SUPERFICIE, 99, Cores.BORDA)
    }
    private fun prefs() = getSharedPreferences("edith", MODE_PRIVATE)

    private fun escrever(quem: String, texto: String) {
        if (Thread.currentThread() !== mainThread) {
            naTela { escrever(quem, texto) }
            return
        }
        try {
            desenharMensagem(quem, texto)
        } catch (e: Exception) {
            Log.e("EDITH", "Falha ao desenhar mensagem", e)
            Diagnostico.registrar(this, e, "escrever")
        }
    }

    private fun desenharMensagem(quem: String, texto: String) {
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
            background = if (actionMessage) forma(Cores.FUNDO, 4, Cores.BORDA)
                else if (isUser) forma(Cores.BOLHA_USUARIO, 18)
                else forma(Cores.SUPERFICIE, 18, Cores.BORDA)
        }
        if (actionMessage) {
            val stripe = View(this).apply {
                background = forma(Cores.PRIMARIA, 2)
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
                setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            }
            actionRow.addView(actionText)
            bubble.addView(actionRow)
        } else {
            val sender = TextView(this).apply {
                text = quem.uppercase(Locale("pt", "BR"))
                textSize = 12f
                letterSpacing = 0.05f
                setTextColor(Cores.cor(if (isUser) Cores.PRIMARIA_ESCURA else Cores.TEXTO_SUAVE))
                alpha = 0.9f
            }
            val body = TextView(this).apply {
                text = texto
                textSize = 15f
                setTextColor(Cores.cor(Cores.TEXTO))
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            bubble.addView(sender)
            bubble.addView(body)
        }
        row.addView(bubble)
        chatList.addView(row, chatList.childCount - 1)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    /** Guarda o stack trace da próxima queda para o usuário copiar em Configurações. */
    private fun instalarCapturaDeFalhas() {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            Diagnostico.registrar(this, erro, "thread ${thread.name}")
            anterior?.uncaughtException(thread, erro)
        }
    }

    private fun abrirDiagnostico() {
        val relatorio = Diagnostico.ultimo(this)
        val texto = if (relatorio.isBlank()) {
            "Nenhuma falha registrada desde a última limpeza. Se a EDITH fechar, o erro aparece aqui."
        } else {
            relatorio
        }
        AlertDialog.Builder(this)
            .setTitle("Última falha registrada")
            .setMessage(texto)
            .setPositiveButton("Copiar") { _, _ ->
                val area = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                area.setPrimaryClip(android.content.ClipData.newPlainText("EDITH log", texto))
                Toast.makeText(this, "Relatório copiado.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Limpar") { _, _ ->
                Diagnostico.limpar(this)
                Toast.makeText(this, "Diagnóstico limpo.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    /** Repinta as últimas trocas salvas, para a conversa continuar de onde parou. */
    private fun restaurarConversa() {
        val salvo = memoria.carregar()
        val inicio = (salvo.length() - 8).coerceAtLeast(0)
        for (i in inicio until salvo.length()) {
            val item = salvo.optJSONObject(i) ?: continue
            val papel = item.optString("role")
            val conteudo = item.optString("content").trim()
            if (conteudo.isBlank()) continue
            if (papel == "user") {
                desenharMensagem("Você", conteudo)
            } else if (papel == "assistant") {
                desenharMensagem("EDITH", conteudo)
            }
            history.put(item)
        }
        if (history.length() > 0) {
            desenharMensagem("EDITH", "(retomando a conversa de antes — memória local)")
        }
    }

    private fun abrirConfiguracoes() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(32))
            setBackgroundColor(Cores.cor(Cores.FUNDO))
        }
        // A tela de ajustes precisa rolar: em tela pequena o botão de salvar sumia.
        val rolagem = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = botaoRedondo("‹", Cores.SUPERFICIE, Cores.PRIMARIA, 44) {
            settingsKeyInput = null
            setContentView(homeRoot)
        }.apply { contentDescription = "Voltar para a EDITH" }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        heading.addView(TextView(this).apply {
            text = "Configurações"
            textSize = 23f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Cores.cor(Cores.TEXTO))
        })
        heading.addView(TextView(this).apply {
            text = "Personalize sua assistente"
            textSize = 13f
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
        })
        header.addView(back)
        header.addView(heading)
        page.addView(header)

        page.addView(TextView(this).apply {
            text = "CONEXÃO COM A IA"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(Cores.cor(Cores.PRIMARIA))
            setPadding(0, dp(34), 0, dp(10))
        })
        page.addView(TextView(this).apply {
            text = "Chave da API Groq"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Cores.cor(Cores.TEXTO))
        })
        page.addView(TextView(this).apply {
            text = "Cole sua chave aqui. Ela fica salva localmente neste aparelho."
            textSize = 13f
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            setPadding(0, dp(6), 0, dp(12))
        })
        settingsKeyInput = EditText(this).apply {
            hint = "gsk_..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(prefs().getString("key", "") ?: "")
            setTextColor(Cores.cor(Cores.TEXTO))
            setHintTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            background = forma(Cores.SUPERFICIE, 16, Cores.BORDA)
            setPadding(dp(15), dp(14), dp(15), dp(14))
        }
        page.addView(settingsKeyInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addView(botaoLargo("Salvar chave", Cores.PRIMARIA, Cores.FUNDO) {
            val key = settingsKeyInput?.text?.toString()?.trim().orEmpty()
            if (key.isBlank()) {
                Toast.makeText(this, "Cole uma chave da Groq antes de salvar.", Toast.LENGTH_LONG).show()
            } else {
                prefs().edit().putString("key", key).apply()
                Toast.makeText(this, "Chave da API salva neste aparelho.", Toast.LENGTH_SHORT).show()
                setContentView(homeRoot)
                escrever("EDITH", "Conexão configurada. Estou pronta.")
                settingsKeyInput = null
            }
        })
        page.addView(TextView(this).apply {
            text = "A chave não deve ser compartilhada. Ela é armazenada nas preferências privadas do aplicativo."
            textSize = 12f
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            setPadding(0, dp(12), 0, 0)
        })
        page.addView(TextView(this).apply {
            text = "VOZ E MEMÓRIA"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(Cores.cor(Cores.PRIMARIA))
            setPadding(0, dp(28), 0, dp(10))
        })
        page.addView(interruptor(
            "Escuta contínua",
            "Depois de responder, ela volta a ouvir sozinha por alguns segundos.",
            escutaContinua
        ) { ligado ->
            escutaContinua = ligado
            Preferencias.definir(prefs(), Preferencias.VIVA, ligado)
        })
        page.addView(interruptor(
            "Falar as respostas",
            "Lê em voz alta o que a EDITH responde.",
            falarRespostas
        ) { ligado ->
            falarRespostas = ligado
            Preferencias.definir(prefs(), Preferencias.FALAR_RESPOSTAS, ligado)
            if (!ligado) tts?.stop()
        })
        page.addView(TextView(this).apply {
            text = memoria.resumo(history)
            textSize = 12f
            setTextColor(Cores.cor(Cores.TEXTO_SUAVE))
            setPadding(0, dp(6), 0, dp(10))
        })
        page.addView(botaoLargo("Apagar memória da conversa", Cores.SUPERFICIE, Cores.TEXTO_SUAVE) {
            AlertDialog.Builder(this)
                .setTitle("Apagar a conversa guardada?")
                .setMessage("A EDITH esquece tudo o que foi dito até aqui neste aparelho.")
                .setPositiveButton("Apagar") { _, _ ->
                    memoria.limpar()
                    while (history.length() > 0) history.remove(0)
                    setContentView(homeRoot)
                    escrever("EDITH", "Memória apagada. Quem é você mesmo?")
                }
                .setNegativeButton("Cancelar", null)
                .show()
        })

        page.addView(TextView(this).apply {
            text = "DIAGNÓSTICO"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(Cores.cor(Cores.PRIMARIA))
            setPadding(0, dp(28), 0, dp(10))
        })
        page.addView(botaoLargo("Ver último erro registrado", Cores.SUPERFICIE, Cores.TEXTO_SUAVE) {
            abrirDiagnostico()
        })
        page.addView(botaoLargo("Ver apps que a EDITH enxerga", Cores.SUPERFICIE, Cores.TEXTO_SUAVE) {
            AlertDialog.Builder(this)
                .setTitle("Apps visíveis para a EDITH")
                .setMessage(comandos.resumoDescoberta())
                .setPositiveButton("Fechar", null)
                .show()
        })
        page.addView(botaoLargo("Verificar atualização  ⟳", Cores.SUPERFICIE, Cores.PRIMARIA) {
            setContentView(homeRoot)
            escrever("EDITH", "⟳ procura atualização")
            verificarAtualizacao()
        })
        setContentView(rolagem)
    }

    private fun pedirChave() = abrirConfiguracoes()

    private fun iniciarMicrofone() = iniciarMicrofoneInterno(false)

    /**
     * [automatico] = true quando a escuta foi reaberta sozinha depois da resposta.
     * Nesse modo a EDITH não anuncia "estou ouvindo" nem reclama de silêncio:
     * se ninguém falou, ela só volta a dormir.
     */
    private fun iniciarMicrofoneInterno(automatico: Boolean) {
        if (listening) {
            if (!automatico) pararMicrofone()
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
                        naTela {
                            micBtn.text = "■"
                            atualizarEstado("ouvindo")
                            if (!automatico) escrever("EDITH", "Estou ouvindo...")
                        }
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        naTela { micBtn.text = "…" }
                    }
                    override fun onError(error: Int) {
                        listening = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi o que você disse. Tente novamente."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não ouvi nenhuma fala. Toque no microfone e tente novamente."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "O Android não concedeu permissão para o microfone."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecimento de voz está ocupado. Aguarde um instante."
                            else -> "O reconhecimento de voz falhou (código $error). Tente novamente."
                        }
                        naTela {
                            micBtn.text = "🎙"
                            atualizarEstado("idle")
                            val silencio = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            if (!automatico || !silencio) escrever("EDITH", msg)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        val recognized = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        naTela {
                            micBtn.text = "🎙"
                            atualizarEstado("idle")
                            if (recognized.isNotBlank()) {
                                input.setText(recognized)
                                input.setSelection(input.text.length)
                                if (!automatico) escrever("EDITH", "Entendi: \"$recognized\". Vou responder.")
                                retomarEscuta = escutaContinua
                                enviar()
                            } else if (!automatico) {
                                escrever("EDITH", "Não consegui transformar essa fala em texto. Tente novamente.")
                            }
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
            naTela {
                micBtn.text = "🎙"
                atualizarEstado("idle")
                escrever("EDITH", "Não consegui iniciar o microfone: ${e.message ?: "erro desconhecido"}.")
            }
        }
    }

    private fun pararMicrofone() {
        speechRecognizer?.cancel()
        listening = false
        naTela {
            micBtn.text = "🎙"
            atualizarEstado("idle")
            escrever("EDITH", "Escuta cancelada.")
        }
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
            val apelidos = comandos.apelidos()
            if (apelidos.isNotEmpty()) {
                appendLine("Apelidos que o usuário ensinou: " +
                    apelidos.entries.joinToString(", ") { "${it.key} -> ${it.value}" })
            }
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
            escrever("EDITH", "Primeiro toque em ⚙ e cole sua chave da Groq.")
            return
        }
        // O teclado some ao enviar: ele redimensiona a janela e, com foco no campo,
        // o Android pode derrubar a Activity no meio da resposta.
        input.setText("")
        input.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        try {
            escrever("Você", texto)
        } catch (e: Exception) {
            Log.e("EDITH", "Falha ao desenhar a mensagem do usuário", e)
        }
        history.put(JSONObject().put("role", "user").put("content", texto))
        while (history.length() > 40) history.remove(0)
        memoria.salvar(history)
        ocupado = true
        sendBtn.isEnabled = false
        sendBtn.alpha = 0.45f
        thinkingView.visibility = View.VISIBLE
        atualizarEstado("pensando")
        Thread {
            try {
                val bruto = chamarGroq(key)
                naTela {
                    try {
                        aplicarResposta(bruto)
                    } catch (e: Exception) {
                        Log.e("EDITH", "Falha ao processar resposta ou executar ações", e)
                        Diagnostico.registrar(this, e, "aplicarResposta")
                        escrever("EDITH", "Recebi a resposta, mas não consegui processá-la. Tente reformular o pedido.")
                    }
                }
            } catch (e: Exception) {
                Log.e("EDITH", "Falha na chamada da Groq", e)
                naTela { escrever("EDITH", "Erro: ${e.message ?: "falha de conexão"}") }
            } finally {
                naTela {
                    ocupado = false
                    sendBtn.isEnabled = true
                    sendBtn.alpha = 1f
                    thinkingView.visibility = View.GONE
                    atualizarEstado("idle")
                    agendarProximaEscuta()
                }
            }
        }.start()
    }

    /** Separa a fala da EDITH dos comandos e executa os comandos no aparelho. */
    private fun aplicarResposta(bruto: String) {
        val (limpo, acoes) = ParserAcoes.extrair(bruto)
        // Não anuncie que a câmera abriu antes de iniciar a atividade nativa.
        fun eAcaoCamera(acao: Acao): Boolean {
            val nome = acao.nome.lowercase(Locale.ROOT)
            val app = (acao.texto("app") ?: acao.texto("nome") ?: "").lowercase(Locale.ROOT)
            return nome in setOf("abrir_camera", "camera", "câmera") ||
                (nome in setOf("abrir_app", "abrir", "abrir_aplicativo") &&
                    app.contains("camer"))
        }
        val acaoCameraPresente = acoes.any(::eAcaoCamera)
        val falaLimpa = semRestoDeComando(limpo)
        val falaPrometeCamera = falaLimpa.lowercase(Locale("pt", "BR")).let {
            (it.contains("camera") || it.contains("câmera")) &&
                (it.contains("abr") || it.contains("inici"))
        }
        if (falaLimpa.isNotBlank() && !(falaPrometeCamera && !acaoCameraPresente) &&
            !acaoCameraPresente) {
            escrever("EDITH", falaLimpa)
            falar(falaLimpa)
        } else if (falaPrometeCamera && !acaoCameraPresente) {
            escrever("EDITH", "Não recebi um comando válido para abrir a câmera. Tente novamente.")
        } else if (acoes.isEmpty() && falaLimpa.isBlank()) {
            escrever("EDITH", "(não consegui formular uma resposta)")
        }
        for (acao in acoes) {
            val resultado = comandos.executar(acao)
            escrever("⚙ Aparelho", resultado)
            // A confirmação da câmera só é mostrada depois que startActivity teve sucesso.
            if (eAcaoCamera(acao) || resultado.startsWith("Não consegui abrir") ||
                resultado.startsWith("Não achei nenhum app chamado") ||
                resultado.startsWith("Falhou (")) {
                falar(resultado)
            }
            history.put(
                JSONObject().put("role", "system").put("content", "Resultado do aparelho: $resultado")
            )
        }
        if (acaoCameraPresente && falaLimpa.isNotBlank()) {
            // O resultado real (sucesso ou falha) já foi mostrado acima; não repita o texto da IA.
        }
    }

    /**
     * A Groq (e qualquer API compatível com OpenAI) recusa duas mensagens "system"
     * seguidas. O resultado do aparelho entra no histórico como system, então ele
     * é anexado à última mensagem do usuário.
     */
    private fun mensagensParaApi(): JSONArray {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sistema()))
        val recorte = memoria.paraPrompt(history)
        var i = 0
        while (i < recorte.length()) {
            val atual = recorte.getJSONObject(i)
            if (atual.optString("role") == "system") {
                val anterior = if (msgs.length() > 1) msgs.getJSONObject(msgs.length() - 1) else null
                if (anterior != null) {
                    val juntar = anterior.optString("content") + "\n\n" + atual.optString("content")
                    anterior.put("content", juntar)
                } else {
                    msgs.put(atual)
                }
            } else {
                msgs.put(atual)
            }
            i++
        }
        return msgs
    }

    /** Remove qualquer sobra de comando (JSON solto, marcador) antes de falar. */
    private fun semRestoDeComando(texto: String): String {
        var t = texto
        t = t.replace(Regex("\\{\\s*\"acao\"\\s*:.*?\\}", RegexOption.DOT_MATCHES_ALL), " ")
        t = t.replace(Regex("(?i)\\[\\[?\\s*A[Cc][Aa][Oo]\\s*\\]?\\]"), " ")
        t = t.replace(Regex("\\s+"), " ")
        return t.trim()
    }

    private fun chamarGroq(key: String): String {
        val msgs = mensagensParaApi()
        val body = JSONObject()
            .put("model", "openai/gpt-oss-120b")
            .put("messages", msgs)

        val c = URL("https://api.groq.com/openai/v1/chat/completions")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "EDITH/0.6.0")
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) {
            // Erro de API não entra no histórico: envenenaria a próxima chamada.
            Log.w("EDITH", "Groq respondeu $code")
            val detalhe = when {
                code == 401 || code == 403 -> "A chave da Groq foi recusada. Toque em ⚙ e confira a chave."
                code == 429 -> "A Groq pediu calma: muitas chamadas seguidas. Tente em alguns segundos."
                code in 500..599 -> "A Groq está fora do ar agora. Tente de novo em instantes."
                else -> "A Groq recusou o pedido (HTTP $code)."
            }
            return detalhe
        }

        val out = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        history.put(JSONObject().put("role", "assistant").put("content", out))
        memoria.salvar(history)
        return out
    }

    private fun falar(texto: String) {
        if (!falarRespostas) return
        if (ttsReady && texto.isNotBlank()) {
            ultimaFala = texto
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "edith_resposta")
        }
    }

    /** Depois de responder, reabre o microfone sozinho por alguns segundos. */
    private fun agendarProximaEscuta() {
        if (!retomarEscuta || !escutaContinua) {
            retomarEscuta = false
            return
        }
        retomarEscuta = false
        tempoParaEscutar?.let { mao.removeCallbacks(it) }
        val tarefa = Runnable {
            if (!isFinishing && !listening && checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
                iniciarMicrofoneInterno(true)
            }
        }
        tempoParaEscutar = tarefa
        mao.postDelayed(tarefa, 700)
    }

    // ---------- modo de ação: upgrade da própria EDITH ----------

    /** Procura versão nova no GitHub. Devolve o texto que vai para o chat. */
    private fun verificarAtualizacao(): String {
        escrever("EDITH", "Deixa eu ver no GitHub se saiu versão nova...")
        Thread {
            try {
                val release = atualizador.buscarRelease()
                val atual = atualizador.versaoAtual()
                // A tag da release é a mesma enquanto o APK muda: o build é que conta.
                val jaAtualizado = !Versao.maisNova(release.versao, atual) &&
                    release.build <= atualizador.buildAtual()
                runOnUiThread {
                    if (!jaAtualizado) {
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
            .setMessage(
                "Você está na $atual (build ${atualizador.buildAtual()}). " +
                    "A nova é a build ${release.build}. Baixar e instalar agora?"
            )
            .setPositiveButton("Instalar") { _, _ -> baixarEInstalar(release) }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun baixarEInstalar(release: Atualizador.Release) {
        escrever("EDITH", "Baixando a ${release.versao}. Não me interrompa.")
        // Sem esta permissão o download é inútil: pede antes de gastar dados.
        if (!atualizador.podeInstalar()) {
            escrever(
                "EDITH",
                "O Android precisa da sua autorização: ligue \"Permitir desta fonte\" e toque em ⟳ de novo."
            )
            atualizador.pedirPermissaoInstalacao()
            return
        }
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
                val buildAtual = atualizador.buildAtual()
                val novidade = Versao.maisNova(release.versao, atualizador.versaoAtual()) ||
                    release.build > buildAtual
                if (novidade) {
                    runOnUiThread {
                        escrever(
                            "EDITH",
                            "Saiu a ${release.versao} (build ${release.build}). Toque em ⟳ que eu instalo."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("EDITH", "Verificação automática de atualização falhou", e)
            }
        }.start()
    }

    override fun onDestroy() {
        tempoParaEscutar?.let { mao.removeCallbacks(it) }
        tempoParaEscutar = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}