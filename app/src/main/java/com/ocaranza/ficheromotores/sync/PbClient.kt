package com.ocaranza.ficheromotores.sync

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error del servidor. status = 0 cuando no hubo conexión. */
class PbException(val status: Int, message: String) : Exception(message)

/** Cliente mínimo para la API REST de PocketBase. */
class PbClient(baseUrl: String, private val email: String, private val password: String, var token: String? = null) {
    val base: String = normalizeUrl(baseUrl)
    var userName: String? = null
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        const val PAGE = 500

        /** Acepta "192.168.1.50:8090" o "http://100.64.1.2:8090/" y devuelve "http://...:8090". */
        fun normalizeUrl(u: String): String {
            var s = u.trim().trimEnd('/')
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s
        }

        /** Texto entre comillas para los filtros de PocketBase. */
        fun q(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
    }

    private fun url(path: String) = (base + path).toHttpUrl().newBuilder()

    private fun call(req: Request.Builder, auth: Boolean = true): String {
        if (auth) token?.let { req.header("Authorization", it) }
        req.header("Accept", "application/json")
        try {
            http.newCall(req.build()).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (!r.isSuccessful) throw PbException(r.code, errorMessage(text, r.code))
                return text
            }
        } catch (e: IOException) {
            throw PbException(0, "No se pudo conectar con el servidor. Revisá la dirección y que la PC esté encendida.")
        } catch (e: IllegalArgumentException) {
            throw PbException(0, "La dirección del servidor no es válida.")
        }
    }

    private fun errorMessage(text: String, code: Int): String {
        val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
        return when (code) {
            401, 403 -> "El servidor rechazó el acceso. Revisá el usuario y la contraseña."
            404 -> "En esa dirección no hay un servidor de Fichero de Motores."
            else -> if (msg.isNotBlank()) "Error del servidor ($code): $msg" else "Error del servidor ($code)."
        }
    }

    private fun post(path: String, body: JSONObject) = Request.Builder().url(url(path).build()).post(body.toString().toRequestBody(JSON_TYPE))

    /* ---------- Sesión ---------- */

    fun login() {
        val body = JSONObject().put("identity", email).put("password", password)
        val o = try {
            JSONObject(call(post("/api/collections/users/auth-with-password", body), auth = false))
        } catch (e: PbException) {
            if (e.status == 400) throw PbException(400, "Usuario o contraseña incorrectos.")
            throw e
        }
        token = o.getString("token")
        userName = o.optJSONObject("record")?.optString("name")?.ifBlank { null }
    }

    /** Renueva la sesión guardada o vuelve a entrar con la contraseña. */
    fun ensureAuth() {
        if (token != null) {
            try {
                val o = JSONObject(call(post("/api/collections/users/auth-refresh", JSONObject())))
                token = o.getString("token")
                userName = o.optJSONObject("record")?.optString("name")?.ifBlank { null }
                return
            } catch (e: PbException) {
                if (e.status == 0) throw e
            }
        }
        login()
    }

    /* ---------- Motores, pruebas y mantenimientos ---------- */

    fun listItemsSince(cursor: String?): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val u = url("/api/collections/items/records")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("perPage", PAGE.toString())
                .addQueryParameter("sort", "updated,id")
                .addQueryParameter("skipTotal", "1")
            if (!cursor.isNullOrBlank()) u.addQueryParameter("filter", "updated >= ${q(cursor)}")
            val o = JSONObject(call(Request.Builder().url(u.build()).get()))
            val items = o.getJSONArray("items")
            for (i in 0 until items.length()) out += items.getJSONObject(i)
            if (items.length() < PAGE) break
            page++
        }
        return out
    }

    fun findItem(uid: String): JSONObject? {
        val u = url("/api/collections/items/records")
            .addQueryParameter("perPage", "1").addQueryParameter("skipTotal", "1")
            .addQueryParameter("filter", "uid = ${q(uid)}")
        val items = JSONObject(call(Request.Builder().url(u.build()).get())).getJSONArray("items")
        return if (items.length() > 0) items.getJSONObject(0) else null
    }

    fun createItem(body: JSONObject): JSONObject = JSONObject(call(post("/api/collections/items/records", body)))

    fun updateItem(id: String, body: JSONObject): JSONObject =
        JSONObject(call(Request.Builder().url(url("/api/collections/items/records/$id").build()).patch(body.toString().toRequestBody(JSON_TYPE))))

    /* ---------- Fotos ---------- */

    fun findFoto(nombre: String): JSONObject? {
        val u = url("/api/collections/fotos/records")
            .addQueryParameter("perPage", "1").addQueryParameter("skipTotal", "1")
            .addQueryParameter("filter", "nombre = ${q(nombre)}")
        val items = JSONObject(call(Request.Builder().url(u.build()).get())).getJSONArray("items")
        return if (items.length() > 0) items.getJSONObject(0) else null
    }

    fun uploadFoto(nombre: String, motorUid: String, file: File) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("nombre", nombre)
            .addFormDataPart("motor_uid", motorUid)
            .addFormDataPart("archivo", nombre, file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        call(Request.Builder().url(url("/api/collections/fotos/records").build()).post(body))
    }

    private var fileToken: String? = null

    /** Descarga el archivo de un registro de foto a [dest]. */
    fun downloadFoto(record: JSONObject, dest: File) {
        val ft = fileToken ?: JSONObject(call(post("/api/files/token", JSONObject()))).getString("token").also { fileToken = it }
        val u = url("/api/files/fotos/${record.getString("id")}/${record.getString("archivo")}").addQueryParameter("token", ft)
        try {
            http.newCall(Request.Builder().url(u.build()).get().build()).execute().use { r ->
                if (!r.isSuccessful) throw PbException(r.code, "No se pudo bajar la foto (${r.code}).")
                val tmp = File(dest.parentFile, dest.name + ".part")
                r.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            }
        } catch (e: IOException) {
            throw PbException(0, "Se cortó la conexión al bajar una foto.")
        }
    }
}
