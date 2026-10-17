package com.ocaranza.ficheromotores.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

private val AR: Locale = Locale("es", "AR")

/** Convierte texto con coma o punto decimal en número. Devuelve null si está vacío o no es válido. */
fun num(s: String?): Double? {
    if (s.isNullOrBlank()) return null
    val v = s.trim().replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
    return if (v.isFinite()) v else null
}

/** Formatea con coma decimal (es-AR) y hasta [d] decimales. */
fun fx(v: Double, d: Int = 2): String {
    val nf = NumberFormat.getNumberInstance(AR)
    nf.maximumFractionDigits = d
    nf.minimumFractionDigits = 0
    return nf.format(v)
}

fun fmtR(v: Double): String = fx(v, if (v < 10) 2 else if (v < 100) 1 else 0)

fun today(): String = LocalDate.now().toString()

fun nowIso(): String = Instant.now().toString()

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(20)

/** "2026-10-17" -> "17/10/2026" */
fun fdate(s: String?): String {
    if (s.isNullOrBlank()) return ""
    val p = s.split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else s
}

/** Días desde hoy hasta la fecha (negativo si ya pasó). */
fun daysTo(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    return try {
        ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s))
    } catch (e: Exception) {
        null
    }
}
