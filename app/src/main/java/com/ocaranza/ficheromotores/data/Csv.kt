package com.ocaranza.ficheromotores.data

import com.ocaranza.ficheromotores.util.fdate
import com.ocaranza.ficheromotores.util.fmtR
import com.ocaranza.ficheromotores.util.fx
import com.ocaranza.ficheromotores.util.today

private fun cell(v: String?): String {
    val s = v ?: ""
    return if (s.contains(';') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

/** CSV con separador ";" y marca BOM para que Excel en español lo abra bien. */
fun csv(rows: List<List<String?>>): String = "﻿" + rows.joinToString("\r\n") { r -> r.joinToString(";") { cell(it) } }

fun exportList(list: List<Motor>, regs: List<Registro>, todo: Boolean, filtros: Filtros): String {
    val desc = (filtrosActivos(filtros).map { it.second } + listOfNotNull(filtros.q.trim().takeIf { it.isNotEmpty() }?.let { "Búsqueda: $it" }))
        .joinToString(" · ").ifEmpty { "Ninguno" }
    val rows = mutableListOf<List<String?>>(
        listOf("LISTADO DE MOTORES", "Generado ${fdate(today())}"),
        listOf("Filtros", desc),
        emptyList(),
    )
    if (!todo) {
        val cols = listOf("codigo", "tipo", "estado", "marca", "modelo", "serie", "pot", "potU", "tension", "corriente", "rpm", "polos",
            "carcasa", "rodLA", "rodLOA", "forma", "brida", "cliente", "ubicacion", "equipo")
        val fs = cols.map { k -> MOTOR_FIELDS.first { it.key == k } }
        rows += fs.map { it.label } + listOf("Potencia (kW)", "Última prueba", "Resultado", "Próximo mantenimiento")
        for (m in list) {
            val r = resumen(m.id, regs)
            val k = potKW(m.d)
            rows += fs.map { optionValue(it, m.d) } + listOf(k?.let { fx(it, 2) } ?: "", fdate(r.ultPrueba?.fecha),
                r.ultPrueba?.v?.get("resultado") ?: "", fdate(r.proxMant))
        }
    } else {
        rows += MOTOR_FIELDS.map { f -> f.label + (f.unit?.let { " ($it)" } ?: "") } +
            listOf("Potencia (kW)", "Última prueba", "Resultado", "Último mantenimiento", "Próximo mantenimiento")
        for (m in list) {
            val r = resumen(m.id, regs)
            val k = potKW(m.d)
            rows += MOTOR_FIELDS.map { optionValue(it, m.d) } + listOf(k?.let { fx(it, 2) } ?: "", fdate(r.ultPrueba?.fecha),
                r.ultPrueba?.v?.get("resultado") ?: "", fdate(r.ultMant?.fecha), fdate(r.proxMant))
        }
    }
    return csv(rows)
}

/** mode: "todo", "tec" (solo datos técnicos) o "hist" (solo historial). */
fun exportMotor(m: Motor, regs: List<Registro>, mode: String): String {
    val d = m.d
    val rows = mutableListOf<List<String?>>(listOf("FICHA DE MOTOR", d["codigo"] ?: ""), listOf("Generado", fdate(today())))
    if (mode != "hist") {
        for (s in MOTOR) {
            if (s.forTipos != null && d["tipo"] !in s.forTipos) continue
            val items = s.fields.mapNotNull { f -> fieldValue(f, d)?.let { listOf(f.label, it) } }
            if (items.isEmpty()) continue
            rows += emptyList<String>()
            rows += listOf(s.title.uppercase())
            rows += items
        }
    } else {
        rows += listOf("Motor", motorSpec(d))
    }
    if (mode != "tec") {
        val pruebas = regs.filter { it.motorId == m.id && it.kind == KIND_PRUEBA }.sortedByDescending { it.fecha }
        val mants = regs.filter { it.motorId == m.id && it.kind == KIND_MANT }.sortedByDescending { it.fecha }
        if (pruebas.isNotEmpty()) {
            rows += emptyList<String>()
            rows += listOf("PRUEBAS")
            for (p in pruebas) {
                val c = calcPrueba(p.v, m.tipo)
                rows += emptyList<String>()
                rows += listOf("Fecha", fdate(p.fecha), "Motivo", p.v["motivo"] ?: "", "Resultado", p.v["resultado"] ?: "", "Técnico", p.tecnico)
                val at = ambT(p.v); val ah = p.v["amb_h"]
                if (at != null || ah != null) {
                    rows += listOf("Condiciones ambientales")
                    if (at != null) rows += listOf("", "Temperatura ambiente", "$at °C")
                    if (ah != null) rows += listOf("", "Humedad ambiente", "$ah % HR")
                }
                for (s in PRUEBA) {
                    if (s.id !in p.secciones) continue
                    rows += listOf(s.title)
                    if (s.id == "ais") {
                        p.v["ais_v"]?.let { rows += listOf("", "Tensión de prueba", "$it V") }
                        rows += listOf("", "Medición", "30 s (MΩ)", "1 min (MΩ)", "10 min (MΩ)", "DAR", "IP")
                        aisRows(p.v).forEach { r ->
                            rows += listOf("", r.label, r.r30?.let { fmtR(it) } ?: "", r.r1?.let { fmtR(it) } ?: "",
                                r.r10?.let { fmtR(it) } ?: "", r.dar?.let { fx(it) } ?: "", r.ip?.let { fx(it) } ?: "")
                        }
                    } else {
                        fieldsFor(s, m.tipo, includeLegacy = true).forEach { f -> fieldValue(f, p.v)?.let { rows += listOf("", f.label, it) } }
                    }
                    c[s.id]?.forEach { r -> rows += listOf("", r.label, r.value) }
                }
                p.v["instrumento"]?.let { rows += listOf("Instrumentos", it) }
                p.v["obs"]?.let { rows += listOf("Observaciones", it) }
            }
        }
        if (mants.isNotEmpty()) {
            rows += emptyList<String>()
            rows += listOf("MANTENIMIENTOS")
            rows += listOf("Fecha", "Tipo", "OT", "Trabajo", "Repuestos", "Horas", "Horómetro", "Estado final", "Próximo", "Técnico")
            for (x in mants) {
                val v = x.v
                rows += listOf(fdate(x.fecha), v["tipo"], v["ot"], v["descripcion"], v["repuestos"], v["horas"], v["horometro"],
                    v["estadoFinal"], fdate(v["proxima"]), x.tecnico)
            }
        }
    }
    return csv(rows)
}

fun motorSpec(d: Map<String, String>): String =
    listOfNotNull(
        d["marca"],
        d["pot"]?.let { "$it ${d["potU"] ?: "kW"}" },
        d["rpm"]?.let { "$it rpm" },
        d["tension"]?.let { "$it V" },
        d["carcasa"],
    ).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Sin datos de placa" }
