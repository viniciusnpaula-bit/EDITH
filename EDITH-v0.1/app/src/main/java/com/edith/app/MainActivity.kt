package com.edith.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
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

class MainActivity : Activity() {
    private val history = JSONArray()
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    private val persona = "Você é a EDITH, assistente de IA no celular do usuário. " +
        "Responda sempre em português do Brasil, de forma curta, sarcástica e espirituosa " +
        "(estilo filme), mas sempre prestativa."

    companion object {
        private const val REQUEST_MIC_PERMISSION = 102
        private const val REQUEST_SPEECH = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 64, 24, 24)
            setBackgroundColor(Color.parseColor("#0B1220"))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "EDITH v0.1.2"
            textSize = 24f
            setTextColor(Color.parseColor("#4FC3F7"))
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
        }
        val keyBtn = Button(this).apply {
            text = "Chave"
            setOnClickListener { pedirChave() }
        }
        top.addView(title)
        top.addView(keyBtn)

        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(match, 0, 1f)
        }
        log = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        scroll.addView(log)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Fale com a EDITH..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
        }
        val micBtn = Button(this).apply {
            text = "🎙"
            contentDescription = "Falar com a EDITH"
            setOnClickListener { iniciarMicrofone() }
        }
        val sendBtn = Button(this).apply {
            text = "Enviar"
            setOnClickListener { enviar() }
        }
        bottom.addView(input)
        bottom.addView(micBtn)
        bottom.addView(sendBtn)

        root.addView(top)
        root.addView(scroll)
        root.addView(bottom)
        setContentView(root)

        escrever("EDITH", "Olá. Toque em Chave para colar sua chave da Groq. Use 🎙 para ditar uma mensagem.")
    }

    private fun prefs() = getSharedPreferences("edith", MODE_PRIVATE)

    private fun escrever(quem: String, texto: String) {
        log.append("$quem: $texto\n\n")
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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
            return
        }
        abrirReconhecimentoDeVoz()
    }

    private fun abrirReconhecimentoDeVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua mensagem para a EDITH")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (_: Exception) {
            Toast.makeText(this, "O reconhecimento de voz não está disponível neste celular.", Toast.LENGTH_LONG).show()
            escrever("EDITH", "Não encontrei um serviço de reconhecimento de voz no aparelho. Verifique se o app Google está instalado e atualizado.")
        }
    }

    @Deprecated("Usado para compatibilidade com versões Android suportadas")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == RESULT_OK) {
                val falas = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val reconhecido = falas?.firstOrNull().orEmpty()
                if (reconhecido.isNotBlank()) {
                    input.setText(reconhecido)
                    input.setSelection(input.text.length)
                    escrever("EDITH", "Entendi: \"$reconhecido\". Toque em Enviar para continuar.")
                } else {
                    Toast.makeText(this, "Não consegui entender. Tente falar novamente.", Toast.LENGTH_SHORT).show()
                }
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(this, "Não foi possível reconhecer a fala. Tente novamente.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Callback de permissão mantido para compatibilidade")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                abrirReconhecimentoDeVoz()
            } else {
                Toast.makeText(this, "Permita o acesso ao microfone para ditar mensagens.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviar() {
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
        Thread {
            val resposta = try {
                chamarGroq(key)
            } catch (e: Exception) {
                "Erro: ${e.message ?: "falha de conexão"}"
            }
            runOnUiThread { escrever("EDITH", resposta) }
        }.start()
    }

    private fun chamarGroq(key: String): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", persona))
        for (i in 0 until history.length()) msgs.put(history.getJSONObject(i))
        val body = JSONObject()
            .put("model", "openai/gpt-oss-120b")
            .put("messages", msgs)

        val c = URL("https://api.groq.com/openai/v1/chat/completions")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "EDITH/0.1.2")
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
}
