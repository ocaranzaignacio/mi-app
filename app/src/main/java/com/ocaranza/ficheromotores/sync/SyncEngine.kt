package com.ocaranza.ficheromotores.sync

import com.ocaranza.ficheromotores.data.Motor
import com.ocaranza.ficheromotores.data.Registro
import com.ocaranza.ficheromotores.data.motorFromJson
import com.ocaranza.ficheromotores.data.registroFromJson
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/* ---------- Marcas de tiempo ---------- */

fun stampOf(iso: String?): Long = try { Instant.parse(iso).toEpochMilli() } catch (e: Exception) { 0L }
val Motor.stamp: Long get() = stampOf(act ?: creado)
val Registro.stamp: Long get() = stampOf(act ?: creado)

/** Marca de un registro borrado, para que no reaparezca desde otro equipo. */
data class Borrado(val tipo: String, val motorUid: String?, val ts: Long)

/** Lo que viaja al servidor: un motor o un registro con su JSON completo. */
data class SyncItem(
    val uid: String,
    val tipo: String, // "motor" o "registro"
    val motorUid: String?,
    val datos: JSONObject?,
    val actualizado: Long,
    val borrado: Boolean,
) {
    fun toBody(): JSONObject = JSONObject()
        .put("uid", uid).put("tipo", tipo).put("motor_uid", motorUid ?: "")
        .put("datos", datos ?: JSONObject.NULL).put("actualizado", actualizado).put("borrado", borrado)

    companion object {
        fun fromRecord(o: JSONObject) = SyncItem(
            uid = o.getString("uid"),
            tipo = o.optString("tipo", "motor"),
            motorUid = o.optString("motor_uid").ifBlank { null },
            datos = o.optJSONObject("datos"),
            actualizado = o.optLong("actualizado"),
            borrado = o.optBoolean("borrado"),
        )
    }
}

/** Estado de sincronización que se guarda en el teléfono. */
data class SyncState(
    val pendientes: Set<String> = emptySet(),
    val borrados: Map<String, Borrado> = emptyMap(),
    val cursor: String? = null,
    val fotosSubidas: Set<String> = emptySet(),
    val servidor: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pendientes", JSONArray(pendientes.toList()))
        put("borrados", JSONObject().apply {
            borrados.forEach { (k, b) -> put(k, JSONObject().put("tipo", b.tipo).put("motorUid", b.motorUid ?: "").put("ts", b.ts)) }
        })
        put("cursor", cursor ?: "")
        put("fotosSubidas", JSONArray(fotosSubidas.toList()))
        put("servidor", servidor ?: "")
    }

    companion object {
        fun fromJson(text: String): SyncState {
            val o = JSONObject(text)
            fun set(a: JSONArray?) = if (a == null) emptySet() else (0 until a.length()).map { a.getString(it) }.toSet()
            val bo = o.optJSONObject("borrados")
            val borr = mutableMapOf<String, Borrado>()
            bo?.keys()?.forEach { k ->
                val b = bo.getJSONObject(k)
                borr[k] = Borrado(b.optString("tipo", "registro"), b.optString("motorUid").ifBlank { null }, b.optLong("ts"))
            }
            return SyncState(
                pendientes = set(o.optJSONArray("pendientes")),
                borrados = borr,
                cursor = o.optString("cursor").ifBlank { null },
                fotosSubidas = set(o.optJSONArray("fotosSubidas")),
                servidor = o.optString("servidor").ifBlank { null },
            )
        }
    }
}

data class MergeResult(
    val motores: List<Motor>,
    val registros: List<Registro>,
    val borrados: Map<String, Borrado>,
    val cambios: Int,
    /** Registros locales más nuevos que los del servidor: hay que volver a subirlos. */
    val repush: Set<String>,
)

object SyncEngine {

    /**
     * Sube los cambios pendientes. Si el servidor ya tiene una versión más nueva, no la pisa:
     * esa versión llega después con [pull]. Devuelve los uid ya resueltos con su marca de tiempo.
     */
    fun push(client: PbClient, items: List<SyncItem>): List<Pair<String, Long>> {
        val done = mutableListOf<Pair<String, Long>>()
        for (it in items) {
            val existing = client.findItem(it.uid)
            if (existing == null) {
                try {
                    client.createItem(it.toBody())
                } catch (e: PbException) {
                    // Otro equipo lo creó al mismo tiempo: se compara y se actualiza si corresponde.
                    val again = if (e.status == 400) client.findItem(it.uid) else null
                    if (again == null) throw e
                    if (again.optLong("actualizado") < it.actualizado) client.updateItem(again.getString("id"), it.toBody())
                }
            } else if (existing.optLong("actualizado") < it.actualizado) {
                client.updateItem(existing.getString("id"), it.toBody())
            }
            done += it.uid to it.actualizado
        }
        return done
    }

    /** Baja lo que cambió en el servidor desde [cursor]. Devuelve los cambios y el cursor nuevo. */
    fun pull(client: PbClient, cursor: String?): Pair<List<SyncItem>, String?> {
        val recs = client.listItemsSince(cursor)
        val newCursor = recs.maxOfOrNull { it.optString("updated") }?.takeIf { it.isNotBlank() } ?: cursor
        return recs.map { SyncItem.fromRecord(it) } to newCursor
    }

    /** Aplica los cambios del servidor a los datos locales: gana la modificación más reciente. */
    fun merge(
        motores: List<Motor>, registros: List<Registro>, borrados: Map<String, Borrado>, remote: List<SyncItem>,
    ): MergeResult {
        val m = LinkedHashMap<String, Motor>().apply { motores.forEach { put(it.id, it) } }
        val r = LinkedHashMap<String, Registro>().apply { registros.forEach { put(it.id, it) } }
        val b = HashMap(borrados)
        var cambios = 0
        val repush = mutableSetOf<String>()
        for (it in remote) {
            val local = m[it.uid]?.stamp ?: r[it.uid]?.stamp ?: b[it.uid]?.ts ?: -1L
            if (it.actualizado < local) { repush += it.uid; continue }
            if (it.actualizado == local) continue
            if (it.borrado) {
                if (m.remove(it.uid) != null || r.remove(it.uid) != null) cambios++
                b[it.uid] = Borrado(it.tipo, it.motorUid, it.actualizado)
            } else {
                val d = it.datos ?: continue
                if (it.tipo == "motor") {
                    val mm = runCatching { motorFromJson(d) }.getOrNull() ?: continue
                    m[it.uid] = mm.copy(id = it.uid)
                } else {
                    val rr = runCatching { registroFromJson(d) }.getOrNull() ?: continue
                    r[it.uid] = rr.copy(id = it.uid)
                }
                b.remove(it.uid)
                cambios++
            }
        }
        return MergeResult(m.values.toList(), r.values.toList(), b, cambios, repush)
    }
}
