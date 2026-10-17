package com.ocaranza.ficheromotores.data

import com.ocaranza.ficheromotores.util.fmtR
import com.ocaranza.ficheromotores.util.fx
import com.ocaranza.ficheromotores.util.num
import kotlin.math.abs

/** Estado de un resultado: "ok" (normal), "obs" (revisar), "mal" (falla) o null (informativo). */
data class CalcRow(val label: String, val value: String, val state: String? = null, val note: String? = null)

data class AisRow(
    val q: String, val label: String,
    val r30: Double?, val r1: Double?, val r10: Double?,
    val dar: Double?, val ip: Double?,
)

fun stIp(v: Double) = if (v < 1) "mal" else if (v < 2) "obs" else "ok"
fun stDar(v: Double) = if (v < 1) "mal" else if (v < 1.25) "obs" else "ok"

fun aisRows(p: Map<String, String>, all: Boolean = false): List<AisRow> =
    AIS_P.map { (q, label) ->
        val r30 = num(p["ais_${q}_30"])
        val r1 = num(p["ais_${q}_1"])
        val r10 = num(p["ais_${q}_10"])
        AisRow(
            q, label, r30, r1, r10,
            dar = if (r1 != null && r30 != null && r30 > 0) r1 / r30 else null,
            ip = if (r10 != null && r1 != null && r1 > 0) r10 / r1 else null,
        )
    }.filter { all || it.r30 != null || it.r1 != null || it.r10 != null }

/** Temperatura ambiente de la prueba (con compatibilidad con registros anteriores). */
fun ambT(p: Map<String, String>): String? = p["amb_t"] ?: p["tem_amb"] ?: p["bob_temp"]

private const val R25_NOTE = "R25 = Rt × 259,5 / (234,5 + t) · t = temperatura ambiente"

fun calcPrueba(p: Map<String, String>, tipo: String?): Map<String, List<CalcRow>> {
    val out = LinkedHashMap<String, MutableList<CalcRow>>()
    fun add(s: String, r: CalcRow) { out.getOrPut(s) { mutableListOf() }.add(r) }
    fun lst(vararg ks: String): List<Double> = ks.mapNotNull { num(p[it]) }
    fun unb(v: List<Double>): Pair<Double, Double> {
        val a = v.average()
        val u = if (a != 0.0) v.maxOf { abs(it - a) } / a * 100 else 0.0
        return a to u
    }

    // Aislamiento
    val ar = aisRows(p)
    ar.filter { it.r1 != null }.minByOrNull { it.r1!! }?.let { w ->
        add("ais", CalcRow("R 1 min más baja · ${w.label}", fmtR(w.r1!!) + " MΩ", if (w.r1 < 5) "mal" else "ok",
            "Referencia IEEE 43: mín. 5 MΩ bobinado aleatorio < 1 kV · 100 MΩ preformado"))
    }
    ar.filter { it.dar != null }.minByOrNull { it.dar!! }?.let { w ->
        add("ais", CalcRow("DAR más bajo · ${w.label}", fx(w.dar!!), stDar(w.dar), "DAR = R 1 min / R 30 s · aceptable desde 1,25"))
    }
    ar.filter { it.ip != null }.minByOrNull { it.ip!! }?.let { w ->
        val high = (w.r1 ?: 0.0) > 5000
        add("ais", CalcRow("IP más bajo · ${w.label}", fx(w.ip!!), if (high) null else stIp(w.ip),
            "IP = R 10 min / R 1 min · IEEE 43: mínimo 2,0" + if (high) " · con R 1 min > 5000 MΩ el IP no es indicativo" else ""))
    }

    // Bobinados
    val kt = tk(tipo)
    val tb = num(p["amb_t"] ?: p["bob_temp"])
    if (kt != "tri") {
        val ws = if (kt == "mono") listOf("bob_uv" to "bobina principal", "bob_vw" to "bobina auxiliar")
        else listOf("bob_ind" to "inducido", "bob_campo" to "campo")
        if (tb != null) ws.forEach { (key, l) ->
            num(p[key])?.let { v -> add("bob", CalcRow("R $l a 25 °C", fx(v * 259.5 / (234.5 + tb), 4) + " Ω", null, R25_NOTE)) }
        }
        if (kt == "mono") {
            val a = num(p["bob_uv"]); val b = num(p["bob_vw"])
            if (a != null && b != null && a > 0) add("bob", CalcRow("Relación auxiliar / principal", fx(b / a), null,
                "Normalmente la bobina auxiliar tiene mayor resistencia que la principal"))
        }
    }
    var rb = if (kt == "tri") lst("bob_u", "bob_v", "bob_w") else emptyList()
    if (kt == "tri" && rb.size < 2) rb = lst("bob_uv", "bob_vw", "bob_wu")
    if (rb.size >= 2) {
        val (a, u) = unb(rb)
        add("bob", CalcRow("Desequilibrio entre fases", fx(u, 1) + " %", if (u <= 2) "ok" else if (u <= 5) "obs" else "mal",
            "Máx. desvío respecto al promedio · referencia ≤ 2 %"))
        if (tb != null) add("bob", CalcRow("Promedio corregido a 25 °C (cobre)", fx(a * 259.5 / (234.5 + tb), 4) + " Ω", null, R25_NOTE))
    }

    // Vacío y carga
    for (s in listOf("vac", "car")) {
        val iv = lst("${s}_iu", "${s}_iv", "${s}_iw")
        if (iv.size >= 2) {
            val (a, u) = unb(iv)
            add(s, CalcRow("Corriente promedio", fx(a) + " A"))
            add(s, CalcRow("Desequilibrio de corriente", fx(u, 1) + " %", if (u <= 5) "ok" else if (u <= 10) "obs" else "mal", "Referencia ≤ 5 %"))
        }
        val v = num(p["${s}_v"])
        if (kt == "cc" && v != null && iv.isNotEmpty()) {
            val w = v * iv.average()
            add(s, CalcRow("Potencia absorbida (calculada)", if (s == "car") fx(w / 1000, 2) + " kW" else fx(w, 0) + " W", null, "P = V × I"))
        }
    }

    // Vibraciones
    val vv = lst("vib_lah", "vib_lav", "vib_laa", "vib_loah", "vib_loav", "vib_loaa")
    if (vv.isNotEmpty()) {
        val clase = p["vib_clase"] ?: "I"
        val lim = when (clase) {
            "II" -> listOf(1.12, 2.8, 7.1)
            "III" -> listOf(1.8, 4.5, 11.2)
            "IV" -> listOf(2.8, 7.1, 18.0)
            else -> listOf(0.71, 1.8, 4.5)
        }
        val m = vv.max()
        val z = if (m <= lim[0]) "A" else if (m <= lim[1]) "B" else if (m <= lim[2]) "C" else "D"
        add("vib", CalcRow("Máximo medido · zona $z", fx(m) + " mm/s", if (z == "D") "mal" else if (z == "C") "obs" else "ok",
            "Clase $clase: A ≤ ${fx(lim[0])} · B ≤ ${fx(lim[1])} · C ≤ ${fx(lim[2])} mm/s"))
    }

    // Temperaturas
    val ta = num(ambT(p))
    val tc = num(p["tem_carc"])
    val tr = lst("tem_la", "tem_loa")
    if (tr.isNotEmpty()) {
        val m = tr.max()
        add("tem", CalcRow("Rodamiento más caliente", fx(m, 1) + " °C", if (m <= 80) "ok" else if (m <= 95) "obs" else "mal",
            "Referencia: ≤ 80 °C normal, > 95 °C revisar"))
    }
    if (ta != null && tc != null) add("tem", CalcRow("Elevación de carcasa", fx(tc - ta, 1) + " K"))
    return out
}

/** Potencia de placa convertida a kW. */
fun potKW(d: Map<String, String>): Double? {
    val p = num(d["pot"]) ?: return null
    return p * when (d["potU"]) { "HP" -> 0.7457; "CV" -> 0.7355; else -> 1.0 }
}

