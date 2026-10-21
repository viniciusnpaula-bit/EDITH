package com.edith.app

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
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
    private var appsCache: List<Pair<String, String>>? = null

    fun executar(acao: Acao): String = try {
        when (acao.nome) {
            "abrir_app", "abrir", "abrir_aplicativo" -> abrirApp(
                acao.texto("app") ?: acao.texto("nome") ?: acao.texto("pacote") ?: ""
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
            else -> "Não conheço a ação \"${acao.nome}\"."
        }
    } catch (e: Exception) {
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
        val lista = pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val rotulo = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (rotulo.isEmpty()) null else rotulo to info.activityInfo.packageName
            }
            .distinctBy { it.second }
            .sortedBy { normalizar(it.first) }
        appsCache = lista
        return lista
    }

    private fun abrirApp(nomeBruto: String): String {
        val alvo = normalizar(nomeBruto)
        if (alvo.isBlank()) return "Não sei qual app abrir."
        val apps = appsInstalados()

        val porNome = apps.firstOrNull { normalizar(it.first) == alvo }
            ?: apps.filter { normalizar(it.first).startsWith(alvo) }.minByOrNull { it.first.length }
            ?: apps.filter { normalizar(it.first).contains(alvo) }.minByOrNull { it.first.length }
        val porPacote = apps.firstOrNull {
            val pacote = normalizar(it.second)
            pacote == alvo || pacote.endsWith(" $alvo") || alvo == it.second.lowercase(Locale.ROOT)
        }
        val escolhido = porNome ?: porPacote
            ?: return "Não achei nenhum app chamado \"$nomeBruto\". Instalados: ${nomesApps(12)}"

        // Em alguns aparelhos, getLaunchIntentForPackage() retorna null mesmo
        // quando o app aparece na consulta de atividades. Use a Activity launcher
        // encontrada pelo PackageManager e abra-a explicitamente.
        val pm = activity.packageManager
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).firstOrNull { it.activityInfo.packageName == escolhido.second }
            ?: return "Encontrei ${escolhido.first}, mas não achei uma tela inicial que o Android possa abrir."

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = android.content.ComponentName(
                launcher.activityInfo.packageName,
                launcher.activityInfo.name
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        return try {
            activity.startActivity(intent)
            "Abrindo ${escolhido.first}."
        } catch (e: android.content.ActivityNotFoundException) {
            "O Android não conseguiu abrir ${escolhido.first}. Tente abrir pelo ícone do aparelho."
        } catch (e: SecurityException) {
            "O Android bloqueou a abertura de ${escolhido.first}."
        }
    }

    /** Lista curta de apps instalados, usada no prompt para a EDITH saber o que existe. */
    fun nomesApps(limite: Int = 40): String {
        val apps = appsInstalados()
        if (apps.isEmpty()) return "(não consegui listar)"
        return apps.take(limite).joinToString(", ") { it.first }
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
        return semAcento.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
