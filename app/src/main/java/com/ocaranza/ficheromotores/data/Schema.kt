package com.ocaranza.ficheromotores.data

/** Tipos de campo de los formularios. */
enum class FT { TEXT, NUM, SELECT, AREA, DATE }

data class Field(
    val key: String,
    val label: String,
    val type: FT,
    val unit: String? = null,
    val ph: String? = null,
    val opts: List<Pair<String, String>> = emptyList(),
    val req: Boolean = false,
    val wide: Boolean = false,
    /** Solo se muestra para estos tipos normalizados: tri, mono, cc. */
    val only: Set<String>? = null,
    /** Campo de un formato anterior: se muestra si tiene valor pero no se pide en el formulario. */
    val legacy: Boolean = false,
)

data class Section(
    val id: String,
    val title: String,
    val fields: List<Field>,
    /** Sección visible solo para estos tipos de motor (valor del campo "tipo"). */
    val forTipos: Set<String>? = null,
)

private fun o(vararg xs: String) = xs.map { it to it }
private fun tx(k: String, l: String, ph: String? = null, u: String? = null, req: Boolean = false) =
    Field(k, l, FT.TEXT, unit = u, ph = ph, req = req)
private fun nm(k: String, l: String, u: String? = null, ph: String? = null, only: Set<String>? = null, legacy: Boolean = false) =
    Field(k, l, FT.NUM, unit = u, ph = ph, only = only, legacy = legacy)
private fun sel(k: String, l: String, opts: List<Pair<String, String>>, req: Boolean = false, u: String? = null) =
    Field(k, l, FT.SELECT, unit = u, opts = opts, req = req)
private fun ar(k: String, l: String, ph: String? = null, req: Boolean = false) =
    Field(k, l, FT.AREA, ph = ph, req = req, wide = true)
private fun dt(k: String, l: String, req: Boolean = false) = Field(k, l, FT.DATE, req = req)

val TIPOS = listOf(
    "tri" to "Trifásico inducción",
    "mono" to "Monofásico",
    "cc" to "Corriente continua",
    "bomba" to "Motobomba",
    "motorred" to "Motorreductor",
)
val ESTADOS = listOf(
    "serv" to "En servicio",
    "taller" to "En taller",
    "reserva" to "Reserva",
    "baja" to "Baja",
)

fun tipoLabel(k: String?): String = TIPOS.firstOrNull { it.first == k }?.second ?: ""
fun estadoLabel(k: String?): String = ESTADOS.firstOrNull { it.first == k }?.second ?: ""

/** Normaliza el tipo de motor para decidir qué campos de prueba se muestran. */
fun tk(tipo: String?): String = when (tipo) {
    "mono" -> "mono"
    "cc" -> "cc"
    else -> "tri"
}

val BRIDAS = o("Sin brida", "FF (agujeros pasantes)", "FT (agujeros roscados)", "NEMA C-face", "NEMA D-flange")

val MOTOR: List<Section> = listOf(
    Section("ident", "Identificación", listOf(
        tx("codigo", "Código / TAG", ph = "MT-0142", req = true),
        sel("tipo", "Tipo de motor", TIPOS, req = true),
        sel("estado", "Estado", ESTADOS),
        tx("cliente", "Cliente / planta"),
        tx("ubicacion", "Ubicación", ph = "Molienda, línea 2"),
        tx("equipo", "Equipo accionado", ph = "Bomba P-301"),
    )),
    Section("placa", "Datos de placa", listOf(
        tx("marca", "Marca", ph = "WEG"), tx("modelo", "Modelo"), tx("serie", "Nº de serie"),
        nm("anio", "Año fabricación"), nm("pot", "Potencia"), sel("potU", "Unidad potencia", o("kW", "HP", "CV")),
        tx("tension", "Tensión", ph = "380/660", u = "V"), tx("corriente", "Corriente", ph = "28,5/16,4", u = "A"),
        sel("frec", "Frecuencia", o("50", "60"), u = "Hz"), nm("rpm", "Velocidad", u = "rpm"),
        sel("polos", "Número de polos", o("2", "4", "6", "8", "10", "12")), nm("fp", "cos φ"),
        nm("rend", "Rendimiento", u = "%"), sel("ie", "Clase eficiencia", o("IE1", "IE2", "IE3", "IE4", "IE5")),
        nm("fs", "Factor servicio"), tx("carcasa", "Carcasa (frame)", ph = "160M"),
        sel("aisl", "Clase aislación", o("A", "B", "F", "H")), tx("ip", "Grado IP", ph = "IP55"),
        tx("servicio", "Servicio", ph = "S1"), sel("conexion", "Conexión", o("Δ", "Y", "Δ/Y", "YY/Y", "Dahlander", "Serie/paralelo")),
        nm("tamb", "Temp. ambiente máx.", u = "°C"), nm("alt", "Altitud máx.", u = "m"),
        nm("peso", "Peso", u = "kg"), tx("norma", "Norma", ph = "IEC 60034-1"),
    )),
    Section("mono", "Datos monofásico", listOf(
        tx("capArr", "Capacitor arranque", ph = "216-259", u = "µF"), nm("capArrV", "Tensión cap. arranque", u = "V"),
        nm("capPerm", "Capacitor permanente", u = "µF"), nm("capPermV", "Tensión cap. permanente", u = "V"),
        sel("centrif", "Interruptor centrífugo", o("Sí", "No")), sel("protTerm", "Protector térmico", o("Sí", "No")),
    ), forTipos = setOf("mono")),
    Section("cc", "Datos corriente continua", listOf(
        sel("excit", "Excitación", o("Independiente", "Shunt", "Serie", "Compuesta", "Imanes permanentes")),
        nm("vInd", "Tensión inducido", u = "V"), nm("iInd", "Corriente inducido", u = "A"),
        nm("vCampo", "Tensión campo", u = "V"), nm("iCampo", "Corriente campo", u = "A"),
        tx("escob", "Medida escobillas", ph = "12,5×32×40 mm"), nm("nEscob", "Cantidad escobillas"),
    ), forTipos = setOf("cc")),
    Section("bomba", "Datos de bomba", listOf(
        nm("caudal", "Caudal", u = "m³/h"), nm("altura", "Altura manométrica", u = "m"),
        nm("impulsor", "Ø impulsor", u = "mm"), tx("sello", "Sello mecánico", ph = "MG1 Ø35"),
        tx("succion", "Succión", u = "DN"), tx("descarga", "Descarga", u = "DN"), tx("fluido", "Fluido"),
    ), forTipos = setOf("bomba")),
    Section("red", "Datos del reductor", listOf(
        tx("redMarca", "Marca reductor", ph = "SEW, Lentax, Bonfiglioli"), tx("redModelo", "Modelo reductor", ph = "KA67"),
        tx("redSerie", "Nº de serie reductor"),
        sel("redTipo", "Tipo de reductor", o("Sinfín corona", "Coaxial helicoidal", "Ejes paralelos", "Ortogonal cónico-helicoidal", "Planetario", "Cicloidal")),
        tx("redI", "Relación de reducción (i)", ph = "28,5"), nm("redRpm", "Velocidad de salida", u = "rpm"),
        nm("redPar", "Par de salida", u = "Nm"), nm("redFs", "Factor de servicio reductor"),
        sel("redMontaje", "Montaje del reductor", o("Con patas", "Con brida", "Eje hueco", "Eje hueco con brazo de reacción", "Eje hueco con disco de contracción")),
        sel("redPos", "Posición de montaje", o("M1", "M2", "M3", "M4", "M5", "M6")),
        nm("redEje", "Ø eje de salida", u = "mm"), nm("redEjeHueco", "Ø eje hueco", u = "mm"),
        tx("redAceite", "Aceite", ph = "ISO VG 220 mineral"), nm("redAceiteL", "Cantidad de aceite", u = "L"),
        nm("redAceiteInt", "Intervalo cambio de aceite", u = "h"),
        tx("redRodEnt", "Rodamientos eje de entrada", ph = "6206-2RS"), tx("redRodSal", "Rodamientos eje de salida", ph = "30210"),
        tx("redRetEnt", "Retén de entrada", ph = "30×47×7"), tx("redRetSal", "Retén de salida", ph = "55×80×10"),
    ), forTipos = setOf("motorred")),
    Section("rod", "Rodamientos y lubricación", listOf(
        tx("rodLA", "Rodamiento LA", ph = "6309-2Z-C3"), tx("rodLOA", "Rodamiento LOA", ph = "6308-2Z-C3"),
        tx("retLA", "Retén / sello LA", ph = "V-ring 45"), tx("retLOA", "Retén / sello LOA"),
        tx("grasa", "Grasa", ph = "Polyrex EM"), nm("lubInt", "Intervalo lubricación", u = "h"), nm("lubCant", "Cantidad grasa", u = "g"),
    )),
    Section("brida", "Montaje y brida", listOf(
        sel("forma", "Forma constructiva", o("B3", "B5", "B35", "B14", "B34", "V1", "V3", "V5", "V6", "V18")),
        sel("brida", "Tipo de brida", BRIDAS),
        nm("bM", "M · círculo agujeros", u = "mm"), nm("bN", "N · Ø encastre", u = "mm"), nm("bP", "P · Ø exterior", u = "mm"),
        tx("bS", "S · Ø / rosca agujeros", ph = "14 / M12", u = "mm"), nm("bCant", "Cantidad agujeros"), nm("bT", "T · altura encastre", u = "mm"),
        nm("bLA", "LA · espesor brida", u = "mm"),
    )),
    Section("eje", "Eje y patas", listOf(
        nm("ejeD", "D · Ø eje LA", u = "mm"), nm("ejeE", "E · largo eje", u = "mm"),
        nm("chF", "F · ancho chaveta", u = "mm"), nm("chGA", "GA · altura eje+chaveta", u = "mm"),
        tx("ejeRosca", "Rosca punta eje", ph = "M16"), nm("ejeDLOA", "Ø eje LOA", u = "mm"),
        nm("H", "H · altura de eje", u = "mm"), nm("A", "A · entre agujeros (frente)", u = "mm"),
        nm("B", "B · entre agujeros (lateral)", u = "mm"), nm("C", "C · eje a 1º agujero", u = "mm"), nm("K", "K · Ø agujero pata", u = "mm"),
    )),
    Section("aj", "Medidas estructurales y ajustes", listOf(
        nm("L", "L · largo total", u = "mm"), nm("HD", "HD · altura total", u = "mm"),
        nm("alojLA", "Alojamiento rod. LA (tapa)", u = "mm"), nm("alojLOA", "Alojamiento rod. LOA (tapa)", u = "mm"),
        nm("asLA", "Asiento rod. en eje LA", u = "mm"), nm("asLOA", "Asiento rod. en eje LOA", u = "mm"),
        nm("entrehierro", "Entrehierro", u = "mm"), ar("ajNotas", "Observaciones de medición", ph = "Ej.: tapa LA ovalizada 0,04 mm"),
    )),
    Section("notas", "Notas", listOf(ar("notas", "Notas generales"))),
)

fun motorSection(id: String): Section = MOTOR.first { it.id == id }
val MOTOR_FIELDS: List<Field> = MOTOR.flatMap { it.fields }

/* ---------------- Pruebas ---------------- */

val PRUEBA_GEN = listOf(
    dt("fecha", "Fecha", req = true),
    sel("motivo", "Motivo", o("Recepción", "Diagnóstico", "Post-reparación", "Rutina", "Entrega")),
    tx("instrumento", "Instrumentos", ph = "Megger MIT525, pinza Fluke 376"),
)
val PRUEBA_AMB = listOf(
    nm("amb_t", "Temperatura ambiente", u = "°C"),
    nm("amb_h", "Humedad ambiente", u = "% HR"),
)

val AIS_P = listOf(
    "ut" to "U – Carcasa", "vt" to "V – Carcasa", "wt" to "W – Carcasa",
    "uv" to "U – V", "vw" to "V – W", "wu" to "W – U",
)
val AIS_T = listOf("30" to "30 s", "1" to "1 min", "10" to "10 min")
val AIS_V = sel("ais_v", "Tensión de prueba", listOf("250" to "250 V", "500" to "500 V", "1000" to "1000 V", "2500" to "2500 V", "5000" to "5000 V"))

private val TRI = setOf("tri")
private val MONO = setOf("mono")
private val CC = setOf("cc")

val PRUEBA: List<Section> = listOf(
    Section("ais", "Resistencia de aislamiento",
        listOf(AIS_V) + AIS_P.flatMap { (q, ql) -> AIS_T.map { (t, tl) -> nm("ais_${q}_$t", "$ql · $tl", u = "MΩ") } }),
    Section("bob", "Resistencia de bobinados", listOf(
        nm("bob_u", "Fase U (U1–U2)", u = "Ω", only = TRI), nm("bob_v", "Fase V (V1–V2)", u = "Ω", only = TRI), nm("bob_w", "Fase W (W1–W2)", u = "Ω", only = TRI),
        nm("bob_uv", "Bobina principal", u = "Ω", only = MONO), nm("bob_vw", "Bobina auxiliar", u = "Ω", only = MONO),
        nm("bob_uv", "U–V (registro anterior)", u = "Ω", only = TRI, legacy = true),
        nm("bob_vw", "V–W (registro anterior)", u = "Ω", only = TRI, legacy = true),
        nm("bob_wu", "W–U (registro anterior)", u = "Ω", only = TRI, legacy = true),
        nm("bob_ind", "Inducido", u = "Ω", only = CC), nm("bob_campo", "Campo", u = "Ω", only = CC),
    )),
    Section("vac", "Prueba en vacío", listOf(
        nm("vac_v", "Tensión", u = "V"), nm("vac_f", "Frecuencia", u = "Hz"), nm("vac_rpm", "Velocidad", u = "rpm"),
        nm("vac_iu", "Corriente U", u = "A"), nm("vac_iv", "Corriente V", u = "A"), nm("vac_iw", "Corriente W", u = "A"),
    )),
    Section("car", "Prueba en carga", listOf(
        nm("car_v", "Tensión", u = "V"), nm("car_f", "Frecuencia", u = "Hz"), nm("car_rpm", "Velocidad", u = "rpm"),
        nm("car_iu", "Corriente U", u = "A"), nm("car_iv", "Corriente V", u = "A"), nm("car_iw", "Corriente W", u = "A"),
    )),
    Section("vib", "Vibraciones (mm/s RMS)", listOf(
        sel("vib_clase", "Clase ISO 10816-1", listOf("I" to "I · hasta 15 kW", "II" to "II · 15 a 75 kW", "III" to "III · grande, base rígida", "IV" to "IV · grande, base flexible")),
        nm("vib_lah", "LA horizontal", u = "mm/s"), nm("vib_lav", "LA vertical", u = "mm/s"), nm("vib_laa", "LA axial", u = "mm/s"),
        nm("vib_loah", "LOA horizontal", u = "mm/s"), nm("vib_loav", "LOA vertical", u = "mm/s"), nm("vib_loaa", "LOA axial", u = "mm/s"),
    )),
    Section("tem", "Temperaturas", listOf(
        nm("tem_carc", "Carcasa", u = "°C"),
        nm("tem_la", "Rodamiento LA", u = "°C"), nm("tem_loa", "Rodamiento LOA", u = "°C"), nm("tem_min", "Duración de marcha", u = "min"),
    )),
)

val PRUEBA_FIN = listOf(
    sel("resultado", "Resultado", o("Aprobado", "Observado", "Rechazado"), req = true),
    ar("obs", "Observaciones"),
)

val MANT = listOf(
    dt("fecha", "Fecha", req = true),
    sel("tipo", "Tipo de trabajo", o("Preventivo", "Correctivo", "Predictivo", "Lubricación", "Cambio de rodamientos", "Rebobinado", "Limpieza y secado", "Balanceo", "Alineación"), req = true),
    tx("ot", "Orden de trabajo", ph = "OT-2026-118"),
    ar("descripcion", "Trabajo realizado", req = true),
    ar("repuestos", "Repuestos utilizados", ph = "2× 6309-2Z-C3, retén 45×62×8"),
    nm("horas", "Horas hombre", u = "h"), nm("horometro", "Horómetro motor", u = "h"),
    sel("estadoFinal", "Estado final", o("Operativo", "Operativo con observaciones", "Fuera de servicio")),
    dt("proxima", "Próximo mantenimiento"),
)

/** Campos de una sección de prueba que aplican al tipo de motor. */
fun fieldsFor(sec: Section, tipo: String?, includeLegacy: Boolean): List<Field> {
    val k = tk(tipo)
    return sec.fields.filter { f -> (f.only == null || k in f.only) && (includeLegacy || !f.legacy) }
}

/** Valor legible de un campo: traduce opciones, formatea fechas y agrega la unidad. */
fun fieldValue(f: Field, d: Map<String, String>): String? {
    val raw = d[f.key]
    if (raw.isNullOrBlank()) return null
    if (f.key == "potU") return null
    if (f.key == "pot") return "$raw ${d["potU"] ?: "kW"}"
    var v = raw
    if (f.type == FT.SELECT) v = f.opts.firstOrNull { it.first == raw }?.second ?: raw
    if (f.type == FT.DATE) v = com.ocaranza.ficheromotores.util.fdate(raw)
    return if (f.unit != null && f.type != FT.SELECT) "$v ${f.unit}" else v
}

/** Valor para planillas: traduce opciones pero no agrega unidad. */
fun optionValue(f: Field, d: Map<String, String>): String {
    val raw = d[f.key] ?: return ""
    return if (f.type == FT.SELECT) f.opts.firstOrNull { it.first == raw }?.second ?: raw else raw
}
