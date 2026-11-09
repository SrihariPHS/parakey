package com.parakey.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.parakey.keyboard.voice.AudioRecorder
import com.parakey.keyboard.voice.ModelRepository
import org.json.JSONObject
import java.io.File

class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action") ?: "state"
        val out = intent.getStringExtra("out")
        val result = try {
            when (action) {
                "keymap" -> serviceJson { it.debugKeyMap() }
                "state" -> serviceJson { it.debugState() }
                "dictate_start" -> {
                    ParakeyImeService.instance?.debugStartDictation()
                    ok("dictate_start")
                }
                "dictate_stop" -> {
                    ParakeyImeService.instance?.debugStopDictation()
                    ok("dictate_stop")
                }
                "cancel" -> {
                    ParakeyImeService.instance?.debugCancelDictation()
                    ok("cancel")
                }
                "inject_wav" -> {
                    ParakeyImeService.instance?.debugInjectWav(intent.getStringExtra("wav").orEmpty())
                    ok("inject_wav")
                }
                "insert" -> {
                    ParakeyImeService.instance?.debugInsertText(intent.getStringExtra("text").orEmpty())
                    ok("insert")
                }
                "clear" -> {
                    ParakeyImeService.instance?.debugClearInput()
                    ok("clear")
                }
                "theme" -> {
                    ParakeyImeService.instance?.debugSetTheme(intent.getStringExtra("theme") ?: "system")
                    ok("theme")
                }
                "model_sha" -> {
                    val repo = ModelRepository(context)
                    val model = repo.resolveModel() ?: throw IllegalStateException("model not installed")
                    val tokens = repo.resolveTokens() ?: throw IllegalStateException("tokens not installed")
                    JSONObject()
                        .put("ok", true)
                        .put("model", repo.sha256(model))
                        .put("tokens", repo.sha256(tokens))
                        .put("path", model.absolutePath)
                }
                "set_clipboard" -> {
                    val manager = context.getSystemService(android.content.ClipboardManager::class.java)
                    manager?.setPrimaryClip(
                        android.content.ClipData.newPlainText("Parakey test", intent.getStringExtra("text").orEmpty()),
                    )
                    ok("set_clipboard")
                }
                "focus_field" -> {
                    val focused = TryActivity.instance?.debugFocus(intent.getStringExtra("field") ?: "message") ?: false
                    if (focused) ok("focus_field") else error("The test pad is not open")
                }
                "clipboard" -> {
                    ParakeyImeService.instance?.onClipboardRequest()
                    ok("clipboard")
                }
                "mic_source" -> {
                    AudioRecorder.forceSource = when (intent.getStringExtra("source")) {
                        "mic" -> android.media.MediaRecorder.AudioSource.MIC
                        "voice" -> android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
                        else -> null
                    }
                    ok("mic_source")
                }
                "play_wav" -> {
                    val path = intent.getStringExtra("wav").orEmpty()
                    val file = resolveFile(context, path)
                    if (file == null || !file.isFile) {
                        error("WAV file not found: $path")
                    } else {
                        WavPlayer.play(context, file)
                        ok("play_wav")
                    }
                }
                "ping" -> ok("pong")
                else -> error("Unknown action: $action")
            }
        } catch (t: Throwable) {
            error(t.message ?: t.javaClass.simpleName)
        }
        writeOut(context, out, result)
        Log.i("ParakeyDebug", "$action -> ${result.toString()}")
    }

    private fun serviceJson(block: (ParakeyImeService) -> String?): JSONObject {
        val service = ParakeyImeService.instance ?: return error("IME service is not running")
        val value = block(service) ?: return error("IME input view is not ready")
        return JSONObject(value)
    }

    private fun ok(action: String): JSONObject = JSONObject().put("ok", true).put("action", action)

    private fun error(message: String): JSONObject = JSONObject().put("ok", false).put("error", message)

    private fun writeOut(context: Context, out: String?, result: JSONObject) {
        if (out.isNullOrBlank()) return
        try {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val file = if (out.startsWith("/")) File(out) else File(base, out)
            file.parentFile?.mkdirs()
            file.writeText(result.toString())
        } catch (t: Throwable) {
            Log.e("ParakeyDebug", "Could not write $out", t)
        }
    }

    private fun resolveFile(context: Context, path: String): File? {
        if (path.isBlank()) return null
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return if (path.startsWith("/")) File(path) else File(base, path)
    }
}
