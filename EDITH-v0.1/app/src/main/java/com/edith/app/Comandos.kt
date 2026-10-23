package com.edith.app

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Executa as ações que a EDITH pede no aparelho.
 * O modelo nunca mexe no celular: ele só escreve um comando em texto,
 * o app interpreta aqui e devolve o resultado.
 */
class Comandos(
    private val activity: Activity,
    private val aoAtualizar: () -> String = { "Atualização não configurada." }
) {

    private val prefs = activity.getSharedPreferences("edith", Context.MODE_PRIVATE)

    /** Palavras que não ajudam a identificar app ("abre o app do banco"). */
    private val palavrasVazias = setOf(
        "abre", "abrir", "app", "aplicativo", "o", "a", "os", "as", "de", "do", "da",
        "no", "na", "meu", "minha", "para", "pra", "favor", "por"
    )

    /**
     * Pacotes que o Android às vezes esconde da consulta pública.
     * Tentados por último, quando nada bateu pelo nome.
     */
    private val pacotesConhecidos = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "instagram" to listOf("com.instagram.android"),
        "spotify" to listOf("com.spotify.music"),
        "facebook" to listOf("com.facebook.katana"),
        "messenger" to listOf("com.facebook.orca"),
        "telegram" to listOf("org.telegram.messenger"),
        "youtube" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "mapa" to listOf("com.google.android.apps.maps"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "uber" to listOf("com.ubercab"),
        "ifood" to listOf("br.com.brainweb.ifood"),
        "nubank" to listOf("com.nu.production"),
        "caixa" to listOf("br.com.gabba.Caixa"),
        "banco do brasil" to listOf("br.com.bb.android"),
        "itau" to listOf("com.itau"),
        "bradesco" to listOf("com.bradesco"),
        "picpay" to listOf("com.picpay"),
        "mercado livre" to listOf("com.mercadolibre"),
        "shopee" to listOf("com.shopee.br"),
        "kwai" to listOf("com.kwai.video"),
        "discord" to listOf("com.discord"),
        "twitch" to listOf("tv.twitch.android.app"),
        "linkedin" to listOf("com.linkedin.android"),
        "github" to listOf("com.github.android"),
        "camera" to listOf("com.sec.android.app.camera", "com.google.android.GoogleCamera"),
        "galeria" to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        "fotos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "relogio" to listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
        "calculadora" to listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
        "configuracoes" to listOf("com.android.settings"),
        "telefone" to listOf("com.samsung.android.dialer", "com.google.android.dialer"),
        "contatos" to listOf("com.samsung.android.app.contacts", "com.google.android.contacts"),
        "mensagens" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        "play store" to listOf("com.android.vending")
    )
    private var appsCache: List<Pair<String, String>>? = null

    fun executar(acao: Acao): String {
        // O modelo às vezes manda a ação como {"abrir_app": "whatsapp"}: sem nome, usa a própria chave.
        val acao = if (acao.nome.isBlank() || acao.nome == "json") acao.comChaveComoAcao() else acao
        return try {
            executarSeguro(acao)
        } catch (e: Exception) {
            android.util.Log.e("EDITH-Comandos", "Falha ao executar ${acao.nome}", e)
            "Falhou (${acao.nome}): ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun executarSeguro(acao: Acao): String = try {
        when (acao.nome) {
            "abrir_camera", "camera", "câmera" -> abrirCamera()
            "abrir_app", "abrir", "abrir_aplicativo" -> abrirApp(
                acao.texto("app") ?: acao.texto("nome") ?: acao.texto("pacote")
                    ?: acao.texto(acao.nome) ?: ""
            )
            "listar_apps", "apps" -> listarApps()
            "lanterna" -> lanterna(acao.texto("estado") ?: "alternar")
            "volume" -> volume(acao)
            "alarme" -> alarme(acao)
            "timer", "temporizador" -> timer(acao)
            "lembrete" -> lembrete(acao)
            "brilho" -> brilho(acao)
            "painel" -> painel(acao.texto("nome") ?: "")
            "pesquisar" -> pesquisar(acao.texto("termo") ?: "")
            "atualizar", "atualizar_app", "verificar_atualizacao", "upgrade" -> aoAtualizar()
            "versao", "versão" -> versao()
            "ensinar_app", "apelido", "mapear_app" -> ensinar(
                acao.texto("apelido") ?: acao.texto("nome") ?: "",
                acao.texto("pacote") ?: acao.texto("app") ?: acao.texto("destino") ?: ""
            )
            "meus_apps", "listar_tudo", "diagnostico_apps" -> resumoDescoberta()
            else -> "Não conheço a ação \"${acao.nome}\"."
        }
    } catch (e: Exception) {
        android.util.Log.e("EDITH-Comandos", "Falha ao executar ${acao.nome}", e)
        "Falhou (${acao.nome}): ${e.message ?: e.javaClass.simpleName}"
    }

    // ---------- versão ----------

    @Suppress("DEPRECATION")
    private fun versao(): String = try {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        "Estou na versão ${info.versionName} (build ${info.versionCode})."
    } catch (e: Exception) {
        "Não consegui ler minha própria versão."
    }

    // ---------- apps ----------

    private fun appsInstalados(): List<Pair<String, String>> {
        appsCache?.let { return it }
        val pm = activity.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // No Android 13+ a consulta antiga devolve lista vazia para alguns apps.
        val encontrados = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                android.content.pm.PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val lista = encontrados
            .map { info ->
                val rotulo = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                // Sem rótulo legível ainda dá para abrir pelo pacote.
                (if (rotulo.isEmpty()) info.activityInfo.packageName else rotulo) to
                    info.activityInfo.packageName
            }
            .distinctBy { it.second }
            .sortedBy { normalizar(it.first) }
        appsCache = lista
        return lista
    }

    /** Tudo que o Android deixa a EDITH enxergar, para diagnosticar a descoberta. */
    fun resumoDescoberta(): String = try {
        val apps = appsInstalados()
        val pm = activity.packageManager
        val servicoVoz = Intent(android.speech.RecognitionService.SERVICE_INTERFACE)
        val falantes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(
                servicoVoz,
                android.content.pm.PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(servicoVoz, 0)
        }.mapNotNull { it.serviceInfo?.packageName }.distinct()
        buildString {
            appendLine("Apps visíveis para a EDITH: ${apps.size}")
            if (apps.isEmpty()) {
                appendLine("Nenhum app com tela inicial foi liberado pelo Android.")
            } else {
                appendLine(apps.take(30).joinToString(", ") { it.first })
            }
            appendLine("Reconhecimento de voz: ${if (falantes.isEmpty()) "nenhum" else falantes.joinToString(", ")}")
        }.trim()
    } catch (e: Exception) {
        android.util.Log.e("EDITH-Comandos", "Falha no resumo de descoberta", e)
        "Não consegui consultar o Android: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun abrirApp(nomeBruto: String): String {
        val alvo = normalizar(nomeBruto)
        if (alvo.isBlank()) return "Não sei qual app abrir."
        if (alvo in setOf("camera", "camera do celular", "camera fotografica")) return abrirCamera()
        // Pacote direto (com.spotify.music) não passa pelo Android 11+ sem <queries>.
        if (nomeBruto.contains(".") && !nomeBruto.contains(" ")) {
            abrirPorPacote(nomeBruto.trim())?.let { return it }
        }

        val apps = try {
            appsInstalados()
        } catch (e: Exception) {
            android.util.Log.e("EDITH-Comandos", "Falha ao consultar aplicativos lançáveis", e)
            return "Não consegui consultar os aplicativos deste aparelho. Verifique as permissões do sistema e tente novamente."
        }

        // Apelido que o usuário ensinou ("insta" -> Instagram) vem antes de tudo.
        apelidos()[alvo]?.let { pacote ->
            abrirPorPacote(pacote)?.let { return it }
        }

        val palavras = alvo.split(" ").filter { it.length > 1 && it !in palavrasVazias }
        fun pontos(nome: String): Int {
            val n = normalizar(nome)
            if (n == alvo) return 1000
            var p = 0
            if (n.startsWith(alvo)) p += 100
            if (n.contains(alvo)) p += 50
            p += palavras.count { n.contains(it) } * 10
            if (palavras.isNotEmpty() && palavras.all { n.contains(it) }) p += 30
            return p
        }
        val porNome = apps
            .map { it to pontos(it.first) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
        val porPacote = apps.firstOrNull {
            it.second.lowercase(Locale.ROOT) == alvo || normalizar(it.second) == alvo
        }

        // Resolve pelo nome exibido, pelo pacote descoberto ou pela tabela de pacotes conhecidos.
        val escolhido = porNome ?: porPacote ?: run {
            val candidatos = pacotesConhecidos[alvo]
                ?: pacotesConhecidos.entries.firstOrNull { (chave, _) -> alvo.contains(chave) }?.value
            val aberto = candidatos?.firstNotNullOfOrNull { pacote ->
                abrirPorPacote(pacote)?.let { it to pacote }
            }
            if (aberto != null) return aberto.first
            null
        } ?: return "Não achei nenhum app chamado \"$nomeBruto\". Instalados: ${nomesApps(12)}"

        val pm = activity.packageManager
        val pacote = escolhido.second
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

        return try {
            val launchIntent = pm.getLaunchIntentForPackage(pacote)
            if (launchIntent != null) {
                launchIntent.addFlags(flags)
                activity.startActivity(launchIntent)
                "Abrindo ${escolhido.first}."
            } else {
                // Segunda tentativa: resolver a atividade launcher do pacote.
                val intentPacote = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(pacote)
                    addFlags(flags)
                }
                if (intentPacote.resolveActivity(pm) != null) {
                    activity.startActivity(intentPacote)
                    "Abrindo ${escolhido.first}."
                } else {
                    // Última tentativa: buscar e iniciar o componente launcher explicitamente.
                    val launcher = pm.queryIntentActivities(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                        0
                    ).firstOrNull { it.activityInfo.packageName == pacote }
                        ?: return "Encontrei ${escolhido.first}, mas o Android não disponibilizou uma tela inicial para ele."

                    val explicito = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = android.content.ComponentName(
                            launcher.activityInfo.packageName,
                            launcher.activityInfo.name
                        )
                        addFlags(flags)
                    }
                    activity.startActivity(explicito)
                    "Abrindo ${escolhido.first}."
                }
            }
        } catch (e: ActivityNotFoundException) {
            android.util.Log.e("EDITH-Comandos", "Nenhuma Activity de inicialização para $pacote", e)
            "Não consegui abrir ${escolhido.first}. Verifique se o app está instalado."
        } catch (e: SecurityException) {
            android.util.Log.e("EDITH-Comandos", "Permissão negada ao abrir $pacote", e)
            "Não consegui abrir ${escolhido.first}. Verifique se o app está instalado e habilitado."
        } catch (e: NullPointerException) {
            android.util.Log.e("EDITH-Comandos", "Referência nula ao abrir $pacote", e)
            "Não consegui abrir ${escolhido.first}. O Android não forneceu uma tela de abertura."
        } catch (e: Exception) {
            android.util.Log.e("EDITH-Comandos", "Falha ao abrir $pacote", e)
            "Não consegui abrir ${escolhido.first}: ${e.message ?: e.javaClass.simpleName}."
        }
    }

    /** Abre o app de câmera do sistema sem solicitar permissão CAMERA. */
    private fun abrirCamera(): String {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return try {
            if (intent.resolveActivity(activity.packageManager) == null) {
                return "Não encontrei um aplicativo de câmera disponível neste aparelho."
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            "Abrindo a câmera."
        } catch (e: ActivityNotFoundException) {
            android.util.Log.e("EDITH-Comandos", "Nenhuma câmera compatível foi encontrada", e)
            "Não consegui abrir a câmera: o Android não encontrou um aplicativo compatível."
        } catch (e: SecurityException) {
            android.util.Log.e("EDITH-Comandos", "Acesso à câmera recusado pelo Android", e)
            "Não consegui abrir a câmera porque o Android bloqueou a inicialização."
        } catch (e: Exception) {
            android.util.Log.e("EDITH-Comandos", "Falha ao iniciar a câmera", e)
            "Não consegui abrir a câmera: ${e.message ?: e.javaClass.simpleName}."
        }
    }

    /** Tenta abrir um pacote mesmo que ele não apareça na lista de apps lançáveis. */
    private fun abrirPorPacote(pacote: String): String? = try {
        val intent = activity.packageManager.getLaunchIntentForPackage(pacote)
        if (intent == null) {
            null
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            activity.startActivity(intent)
            val rotulo = appsCache?.firstOrNull { it.second == pacote }?.first ?: pacote
            "Abrindo $rotulo."
        }
    } catch (e: Exception) {
        android.util.Log.e("EDITH-Comandos", "Falha ao abrir o pacote $pacote", e)
        null
    }

    // ---------- apelidos aprendidos ----------

    /** Apelidos que o usuário ensinou: "insta" -> com.instagram.android. */
    fun apelidos(): Map<String, String> = try {
        val bruto = prefs.getString("apelidos", "") ?: ""
        if (bruto.isBlank()) emptyMap()
        else bruto.split("\n").mapNotNull { linha ->
            val partes = linha.split("=", limit = 2)
            if (partes.size != 2 || partes[1].isBlank()) null
            else normalizar(partes[0]) to partes[1].trim()
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    /** Ensina um apelido. Devolve a confirmação que vai para o chat. */
    fun ensinar(apelido: String, pacoteOuNome: String): String {
        val chave = normalizar(apelido)
        if (chave.isBlank()) return "Não entendi o apelido."
        val pacote = if (pacoteOuNome.contains(".") && !pacoteOuNome.contains(" ")) {
            pacoteOuNome.trim()
        } else {
            val alvo = normalizar(pacoteOuNome)
            appsInstalados()
                .firstOrNull { normalizar(it.first) == alvo }
                ?.second
                ?: appsInstalados().firstOrNull { normalizar(it.first).contains(alvo) }?.second
                ?: return "Não achei nenhum app parecido com \"$pacoteOuNome\" para apelidar."
        }
        val atual = apelidos().toMutableMap()
        atual[chave] = pacote
        prefs.edit()
            .putString("apelidos", atual.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .apply()
        return "Aprendi: \"$apelido\" abre $pacote."
    }

    /** Lista curta de apps instalados, usada no prompt para a EDITH saber o que existe. */
    fun nomesApps(limite: Int = 40): String = try {
        val apps = appsInstalados()
        if (apps.isEmpty()) "(não consegui listar)"
        else apps.take(limite).joinToString(", ") { it.first }
    } catch (e: Exception) {
        android.util.Log.e("EDITH-Comandos", "Falha ao montar lista de aplicativos", e)
        "(não consegui listar)"
    }

    private fun listarApps(): String {
        val apps = appsInstalados()
        if (apps.isEmpty()) return "Não consegui listar os apps."
        val nomes = apps.take(25).joinToString(", ") { it.first }
        return "Apps instalados ($nomes${if (apps.size > 25) "..." else ""})."
    }

    // ---------- lanterna ----------

    private fun lanterna(estado: String): String {
        val cm = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Este aparelho não expõe lanterna para apps."
        val atual = prefs.getBoolean("lanterna", false)
        val ligar = when (estado.lowercase(Locale.ROOT)) {
            "ligar", "ligada", "on", "acender", "acende", "true" -> true
            "desligar", "desligada", "off", "apagar", "false" -> false
            else -> !atual
        }
        cm.setTorchMode(id, ligar)
        prefs.edit().putBoolean("lanterna", ligar).apply()
        return if (ligar) "Lanterna ligada." else "Lanterna desligada."
    }

    // ---------- volume ----------

    private fun volume(acao: Acao): String {
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val fluxo = when ((acao.texto("fluxo") ?: "musica").lowercase(Locale.ROOT)) {
            "alarme" -> AudioManager.STREAM_ALARM
            "chamada", "toque", "ring" -> AudioManager.STREAM_RING
            "notificacao", "notificação" -> AudioManager.STREAM_NOTIFICATION
            "sistema" -> AudioManager.STREAM_SYSTEM
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(fluxo)
        if (max <= 0) return "Este fluxo de áudio não permite ajuste."
        val atual = am.getStreamVolume(fluxo)
        val estado = (acao.texto("estado") ?: "").lowercase(Locale.ROOT)
        val passo = (max / 10).coerceAtLeast(1)

        val alvo = when {
            estado == "mudo" || estado == "mudo_total" -> 0
            acao.tem("nivel") -> (max * acao.inteiro("nivel", 50).coerceIn(0, 100) + 50) / 100
            acao.tem("delta") -> {
                val d = acao.inteiro("delta", 0)
                val passos = ((max * kotlin.math.abs(d)) / 100.0).toInt().coerceAtLeast(1)
                if (d >= 0) atual + passos else atual - passos
            }
            estado == "aumentar" || estado == "subir" -> atual + passo
            estado == "diminuir" || estado == "baixar" -> atual - passo
            else -> atual
        }.coerceIn(0, max)

        am.setStreamVolume(fluxo, alvo, 0)
        val pct = alvo * 100 / max
        val nome = if (fluxo == AudioManager.STREAM_MUSIC) "mídia" else "som"
        return "Volume de $nome em $pct%."
    }

    // ---------- alarme, timer, lembrete ----------

    private fun alarme(acao: Acao): String {
        val hm = horaMinuto(acao.texto("hora") ?: "") ?: return "Não entendi o horário. Manda tipo 7:30."
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hm.first)
            .putExtra(AlarmClock.EXTRA_MINUTES, hm.second)
            .putExtra(AlarmClock.EXTRA_MESSAGE, acao.texto("label") ?: "EDITH")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return abrir(intent, "Alarme para %02d:%02d.".format(hm.first, hm.second))
    }

    private fun timer(acao: Acao): String {
        val segundos = when {
            acao.tem("segundos") -> acao.inteiro("segundos")
            acao.tem("minutos") -> acao.inteiro("minutos") * 60
            else -> 0
        }
        if (segundos <= 0) return "Preciso da duração do timer."
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, segundos)
            .putExtra(AlarmClock.EXTRA_MESSAGE, acao.texto("label") ?: "EDITH")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return abrir(intent, "Timer de $segundos segundos criado.")
    }

    private fun lembrete(acao: Acao): String {
        val quando = try {
            val txt = acao.texto("inicio")
            if (txt.isNullOrBlank()) Date(System.currentTimeMillis() + 3600_000L)
            else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(txt)
                ?: Date(System.currentTimeMillis() + 3600_000L)
        } catch (e: Exception) {
            Date(System.currentTimeMillis() + 3600_000L)
        }
        val titulo = acao.texto("texto") ?: acao.texto("titulo") ?: "Lembrete da EDITH"
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, quando.time)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, quando.time + 30 * 60_000L)
            .putExtra(CalendarContract.Events.TITLE, titulo)
            .putExtra(CalendarContract.Events.DESCRIPTION, "Criado pela EDITH")
        val formatado = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")).format(quando)
        return abrir(intent, "Lembrete \"$titulo\" para $formatado.")
    }

    private fun horaMinuto(txt: String): Pair<Int, Int>? {
        val limpo = txt.lowercase(Locale.ROOT)
            .replace("h", ":")
            .replace(".", ":")
            .replace("horas", ":00")
        val m = Regex("""(\d{1,2})\s*:?\s*(\d{2})?""").find(limpo) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    // ---------- brilho e painéis ----------

    private fun brilho(acao: Acao): String {
        if (!Settings.System.canWrite(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${activity.packageName}")
            )
            return abrir(
                intent,
                "Preciso de permissão para mexer no brilho. Abri a tela: ligue \"Permitir alterar configurações\" e peça de novo."
            )
        }
        val nivel = acao.inteiro("nivel", 50).coerceIn(1, 100)
        val valor = (nivel * 255 / 100).coerceIn(1, 255)
        Settings.System.putInt(
            activity.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, valor)
        return "Brilho em $nivel%."
    }

    private fun painel(nome: String): String {
        val n = normalizar(nome)
        val intent = when {
            n.startsWith("wifi") || n.startsWith("internet") ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else Intent(Settings.ACTION_WIFI_SETTINGS)
            n.startsWith("bluetooth") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            n.startsWith("localiza") || n.startsWith("gps") -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            n.startsWith("dados") || n.startsWith("rede") -> Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            n.startsWith("som") -> Intent(Settings.ACTION_SOUND_SETTINGS)
            n.startsWith("acessibilidade") -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            n.startsWith("notifica") -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            n.startsWith("bateria") -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            n.startsWith("tela") || n.startsWith("display") -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        return abrir(intent, "Abri o painel de ${n.ifBlank { "configurações" }}.")
    }

    private fun pesquisar(termo: String): String {
        if (termo.isBlank()) return "Pesquisar o quê?"
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, termo)
        if (intent.resolveActivity(activity.packageManager) == null) {
            val navegador = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(termo))
            )
            return abrir(navegador, "Busquei \"$termo\" no navegador.")
        }
        return abrir(intent, "Busquei \"$termo\".")
    }

    private fun abrir(intent: Intent, mensagemOk: String): String {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(activity.packageManager) == null) {
            return "Nenhum app do aparelho sabe fazer isso."
        }
        activity.startActivity(intent)
        return mensagemOk
    }

    private fun normalizar(txt: String): String {
        val semAcento = Normalizer.normalize(txt, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        // Mantém letras, números e ponto (pacotes como com.spotify.music).
        return semAcento.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9. ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
