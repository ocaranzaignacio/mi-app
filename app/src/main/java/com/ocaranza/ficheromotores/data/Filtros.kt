package com.ocaranza.ficheromotores.data

import com.ocaranza.ficheromotores.util.daysTo
import com.ocaranza.ficheromotores.util.num

data class Resumen(val ultPrueba: Registro?, val ultMant: Registro?) {
    val proxMant: String? get() = ultMant?.v?.get("proxima")
}

fun resumen(motorId: String, regs: List<Registro>): Resumen {
    val mine = regs.filter { it.motorId == motorId }
    return Resumen(
        ultPrueba = mine.filter { it.kind == KIND_PRUEBA }.maxByOrNull { it.fecha },
        ultMant = mine.filter { it.kind == KIND_MANT }.maxByOrNull { it.fecha },
    )
}

/** Filtros del listado. Las claves vacías no filtran. */
data class Filtros(
    val q: String = "",
    val potMin: String = "",
    val potMax: String = "",
    val polos: String = "",
    val tipo: String = "",
    val estado: String = "",
    val marca: String = "",
    val carcasa: String = "",
    val tension: String = "",
    val rod: String = "",
    val lugar: String = "",
    val brida: String = "",
    val prueba: String = "",
    val mant: String = "",
)

val PRUEBA_F = listOf("Aprobado" to "Aprobado", "Observado" to "Observado", "Rechazado" to "Rechazado", "none" to "Sin pruebas")
val MANT_F = listOf("venc" to "Vencido", "30" to "Vence en 30 días", "none" to "Sin programar")

private fun has(v: String?, q: String) = (v ?: "").contains(q, ignoreCase = true)

fun matches(m: Motor, r: Resumen, f: Filtros): Boolean {
    val d = m.d
    val k = potKW(d)
    num(f.potMin)?.let { mn -> if (k == null || k < mn - 1e-6) return false }
    num(f.potMax)?.let { mx -> if (k == null || k > mx + 1e-6) return false }
    if (f.polos.isNotEmpty() && d["polos"] != f.polos) return false
    if (f.tipo.isNotEmpty() && d["tipo"] != f.tipo) return false
    if (f.estado.isNotEmpty() && d["estado"] != f.estado) return false
    if (f.marca.isNotEmpty() && !has(d["marca"], f.marca)) return false
    if (f.carcasa.isNotEmpty() && !has(d["carcasa"], f.carcasa)) return false
    if (f.tension.isNotEmpty() && !has(d["tension"], f.tension)) return false
    if (f.rod.isNotEmpty() && !(has(d["rodLA"], f.rod) || has(d["rodLOA"], f.rod))) return false
    if (f.lugar.isNotEmpty() && !(has(d["cliente"], f.lugar) || has(d["ubicacion"], f.lugar) || has(d["equipo"], f.lugar))) return false
    if (f.brida.isNotEmpty() && d["brida"] != f.brida) return false
    if (f.prueba.isNotEmpty()) {
        if (f.prueba == "none") { if (r.ultPrueba != null) return false }
        else if (r.ultPrueba?.v?.get("resultado") != f.prueba) return false
    }
    if (f.mant.isNotEmpty()) {
        val x = daysTo(r.proxMant)
        when (f.mant) {
            "venc" -> if (!(x != null && x < 0)) return false
            "30" -> if (!(x != null && x in 0L..30L)) return false
            "none" -> if (r.proxMant != null) return false
        }
    }
    val q = f.q.trim()
    if (q.isNotEmpty()) {
        val hay = listOf("codigo", "marca", "modelo", "serie", "cliente", "ubicacion", "equipo", "rodLA", "rodLOA", "carcasa")
            .joinToString(" ") { d[it] ?: "" }
        if (!hay.contains(q, ignoreCase = true)) return false
    }
    return true
}

/** Orden natural por código: MT-2 antes que MT-10. */
val naturalOrder: Comparator<String> = Comparator { a, b ->
    val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
    val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ra.size, rb.size)) {
        val x = ra[i]; val y = rb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) (x.toBigIntegerOrNull() ?: 0.toBigInteger()).compareTo(y.toBigIntegerOrNull() ?: 0.toBigInteger())
        else x.compareTo(y)
        if (c != 0) return@Comparator c
    }
    ra.size - rb.size
}

/** Textos de los filtros activos, para mostrarlos como etiquetas y en la planilla. */
fun filtrosActivos(f: Filtros): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    if (f.potMin.isNotBlank()) out += "potMin" to "Potencia ≥ ${f.potMin} kW"
    if (f.potMax.isNotBlank()) out += "potMax" to "Potencia ≤ ${f.potMax} kW"
    if (f.polos.isNotBlank()) out += "polos" to "Polos: ${f.polos}"
    if (f.tipo.isNotBlank()) out += "tipo" to "Tipo: ${tipoLabel(f.tipo)}"
    if (f.estado.isNotBlank()) out += "estado" to "Estado: ${estadoLabel(f.estado)}"
    if (f.marca.isNotBlank()) out += "marca" to "Marca: ${f.marca}"
    if (f.carcasa.isNotBlank()) out += "carcasa" to "Carcasa: ${f.carcasa}"
    if (f.tension.isNotBlank()) out += "tension" to "Tensión: ${f.tension}"
    if (f.rod.isNotBlank()) out += "rod" to "Rodamiento: ${f.rod}"
    if (f.lugar.isNotBlank()) out += "lugar" to "Cliente/ubicación: ${f.lugar}"
    if (f.brida.isNotBlank()) out += "brida" to "Brida: ${f.brida}"
    if (f.prueba.isNotBlank()) out += "prueba" to "Última prueba: ${PRUEBA_F.firstOrNull { it.first == f.prueba }?.second ?: f.prueba}"
    if (f.mant.isNotBlank()) out += "mant" to "Mantenimiento: ${MANT_F.firstOrNull { it.first == f.mant }?.second ?: f.mant}"
    return out
}

fun Filtros.sin(key: String): Filtros = when (key) {
    "potMin" -> copy(potMin = ""); "potMax" -> copy(potMax = ""); "polos" -> copy(polos = "")
    "tipo" -> copy(tipo = ""); "estado" -> copy(estado = ""); "marca" -> copy(marca = "")
    "carcasa" -> copy(carcasa = ""); "tension" -> copy(tension = ""); "rod" -> copy(rod = "")
    "lugar" -> copy(lugar = ""); "brida" -> copy(brida = ""); "prueba" -> copy(prueba = "")
    "mant" -> copy(mant = ""); else -> this
}
