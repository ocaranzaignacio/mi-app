package com.ocaranza.ficheromotores.data

import org.json.JSONArray
import org.json.JSONObject

data class Foto(val file: String, val nombre: String, val fecha: String)

data class Motor(
    val id: String,
    val d: Map<String, String>,
    val fotos: List<Foto> = emptyList(),
    val creado: String,
    val act: String? = null,
) {
    val codigo: String get() = d["codigo"] ?: ""
    val tipo: String? get() = d["tipo"]
}

/** Prueba (kind = "prueba") o mantenimiento (kind = "mant"). */
data class Registro(
    val id: String,
    val motorId: String,
    val kind: String,
    val v: Map<String, String>,
    val secciones: List<String> = emptyList(),
    val tecnico: String = "",
    val creado: String,
    val act: String? = null,
) {
    val fecha: String get() = v["fecha"] ?: ""
}

const val KIND_PRUEBA = "prueba"
const val KIND_MANT = "mant"

/* ---------- JSON ---------- */

private fun mapToJson(m: Map<String, String>): JSONObject {
    val o = JSONObject()
    for ((k, v) in m) o.put(k, v)
    return o
}

private fun jsonToMap(o: JSONObject?): Map<String, String> {
    if (o == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        val v = o.opt(k)
        if (v != null && v != JSONObject.NULL && v !is JSONObject && v !is JSONArray) {
            val s = v.toString()
            if (s.isNotBlank()) out[k] = s
        }
    }
    return out
}

private fun strList(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
}

fun Motor.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("d", mapToJson(d))
    put("fotos", JSONArray().apply {
        fotos.forEach { f -> put(JSONObject().put("file", f.file).put("nombre", f.nombre).put("fecha", f.fecha)) }
    })
    put("creado", creado)
    if (act != null) put("act", act)
}

fun motorFromJson(o: JSONObject): Motor {
    val fa = o.optJSONArray("fotos")
    val fotos = if (fa == null) emptyList() else (0 until fa.length()).mapNotNull { i ->
        val f = fa.optJSONObject(i) ?: return@mapNotNull null
        val file = f.optString("file")
        if (file.isBlank()) null else Foto(file, f.optString("nombre"), f.optString("fecha"))
    }
    return Motor(
        id = o.getString("id"),
        d = jsonToMap(o.optJSONObject("d")),
        fotos = fotos,
        creado = o.optString("creado"),
        act = o.optString("act").ifBlank { null },
    )
}

fun Registro.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("motorId", motorId)
    put("kind", kind)
    put("v", mapToJson(v))
    put("secciones", JSONArray(secciones))
    put("tecnico", tecnico)
    put("creado", creado)
    if (act != null) put("act", act)
}

fun registroFromJson(o: JSONObject): Registro = Registro(
    id = o.getString("id"),
    motorId = o.getString("motorId"),
    kind = o.optString("kind", KIND_PRUEBA),
    v = jsonToMap(o.optJSONObject("v")),
    secciones = strList(o.optJSONArray("secciones")),
    tecnico = o.optString("tecnico"),
    creado = o.optString("creado"),
    act = o.optString("act").ifBlank { null },
)

data class Fichero(val motores: List<Motor>, val registros: List<Registro>)

fun Fichero.toJson(): JSONObject = JSONObject().apply {
    put("app", "fichero-motores")
    put("version", 1)
    put("motores", JSONArray().apply { motores.forEach { put(it.toJson()) } })
    put("registros", JSONArray().apply { registros.forEach { put(it.toJson()) } })
}

fun ficheroFromJson(text: String): Fichero {
    val o = JSONObject(text)
    val ma = o.optJSONArray("motores") ?: JSONArray()
    val ra = o.optJSONArray("registros") ?: JSONArray()
    val motores = (0 until ma.length()).mapNotNull { i -> ma.optJSONObject(i)?.let { runCatching { motorFromJson(it) }.getOrNull() } }
    val regs = (0 until ra.length()).mapNotNull { i -> ra.optJSONObject(i)?.let { runCatching { registroFromJson(it) }.getOrNull() } }
    return Fichero(motores, regs)
}
