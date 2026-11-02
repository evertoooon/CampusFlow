package com.everton.campusflow.moodle

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Não é data class: evita que toString exponha o token acidentalmente.
internal class SessaoPersistida(val token: String, val usuario: Long, val geracao: String,
    val cursos: List<CursoMoodle>, val ultima: Long = 0, val tentativa: Long = 0,
    val bloqueada: Boolean = false, val segundoPlano: Boolean = false, val avisarNovas: Boolean = false) {
    fun com(ultima: Long = this.ultima, tentativa: Long = this.tentativa,
        bloqueada: Boolean = this.bloqueada, segundoPlano: Boolean = this.segundoPlano,
        avisarNovas: Boolean = this.avisarNovas) = SessaoPersistida(token, usuario, geracao, cursos,
            ultima, tentativa, bloqueada, segundoPlano, avisarNovas)
}

internal class CofreMoodle(context: Context, private val nome: String = "moodle-conexao") {
    private val arquivo = AtomicFile(File(context.noBackupFilesDir, "$nome.bin"))
    private val alias = "campusflow.$nome"
    private fun chave(criar: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(criar) { "Chave indisponível" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun ler(): SessaoPersistida? {
        // openRead também recupera um backup AtomicFile deixado por gravação interrompida.
        val bytes = try { arquivo.openRead().use { it.readBytes() } }
        catch (e: java.io.FileNotFoundException) { if (!arquivo.baseFile.exists()) return null else throw e }
        require(bytes.size in 29..65536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, chave(false), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        val plain = cipher.doFinal(bytes.copyOfRange(12,bytes.size))
        try {
            val json = JSONObject(String(plain, Charsets.UTF_8))
            val cursos = json.getJSONArray("cursos")
            require(cursos.length() in 1..20 && json.getLong("usuario") > 0)
            return SessaoPersistida(json.getString("token"),json.getLong("usuario"),json.getString("geracao"),
                (0 until cursos.length()).map { val c=cursos.getJSONObject(it); CursoMoodle(c.getLong("id"),c.getString("nome")) },
                json.optLong("ultima",0), json.optLong("tentativa",0), json.optBoolean("bloqueada",false),
                json.optBoolean("segundoPlano",false), json.optBoolean("avisarNovas",false))
        } finally { plain.fill(0) }
    }
    fun salvar(s: SessaoPersistida) {
        val cursos=JSONArray(); s.cursos.forEach { cursos.put(JSONObject().put("id",it.id).put("nome",it.nome)) }
        val plain=JSONObject().put("token",s.token).put("usuario",s.usuario).put("geracao",s.geracao)
            .put("cursos",cursos).put("ultima",s.ultima).put("tentativa",s.tentativa)
            .put("bloqueada",s.bloqueada).put("segundoPlano",s.segundoPlano).put("avisarNovas",s.avisarNovas)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,chave(true))
        val bytes=try { cipher.iv + cipher.doFinal(plain) } finally { plain.fill(0) }
        val stream=arquivo.startWrite()
        try { stream.write(bytes); arquivo.finishWrite(stream) }
        catch(e:Exception) { arquivo.failWrite(stream); throw e }
    }
    fun apagar() {
        arquivo.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); if(containsAlias(alias)) deleteEntry(alias) }
    }
}
