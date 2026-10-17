@file:OptIn(ExperimentalMaterial3Api::class)

package com.ocaranza.ficheromotores.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.ocaranza.ficheromotores.data.KIND_MANT
import com.ocaranza.ficheromotores.data.KIND_PRUEBA
import com.ocaranza.ficheromotores.data.MANT
import com.ocaranza.ficheromotores.data.MOTOR
import com.ocaranza.ficheromotores.data.Motor
import com.ocaranza.ficheromotores.data.PRUEBA
import com.ocaranza.ficheromotores.data.Registro
import com.ocaranza.ficheromotores.data.aisRows
import com.ocaranza.ficheromotores.data.ambT
import com.ocaranza.ficheromotores.data.calcPrueba
import com.ocaranza.ficheromotores.data.exportMotor
import com.ocaranza.ficheromotores.data.fieldValue
import com.ocaranza.ficheromotores.data.fieldsFor
import com.ocaranza.ficheromotores.data.motorSection
import com.ocaranza.ficheromotores.data.resumen
import com.ocaranza.ficheromotores.data.stDar
import com.ocaranza.ficheromotores.data.stIp
import com.ocaranza.ficheromotores.data.tipoLabel
import com.ocaranza.ficheromotores.util.fdate
import com.ocaranza.ficheromotores.util.fmtR
import com.ocaranza.ficheromotores.util.fx
import com.ocaranza.ficheromotores.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MotorScreen(vm: AppVM, motorId: String) {
    val e = C.x
    val ctx = LocalContext.current
    val m = vm.motor(motorId)
    if (m == null) {
        Empty("Motor no encontrado", "Este motor ya no está en el fichero.")
        return
    }
    val d = m.d
    val pruebas = vm.regsOf(m.id, KIND_PRUEBA)
    val mants = vm.regsOf(m.id, KIND_MANT)
    val res = resumen(m.id, vm.regs.toList())
    var confirmDel by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var fotoMenu by remember { mutableStateOf(false) }
    var delReg by remember { mutableStateOf<Registro?>(null) }

    // Cámara y galería
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) vm.addFoto(m.id, u)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addFoto(m.id, uri)
    }
    fun openCamera() {
        val dir = File(ctx.cacheDir, "camara").apply { mkdirs() }
        val f = File(dir, "foto-${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        camUri = u
        camera.launch(u)
    }

    val tabs = listOf(
        "placa" to "Placa", "mec" to "Mecánica",
        "pruebas" to "Pruebas ${pruebas.size}", "mant" to "Mantenimiento ${mants.size}", "fotos" to "Fotos ${m.fotos.size}",
    )
    val tabIndex = tabs.indexOfFirst { it.first == vm.tab }.coerceAtLeast(0)

    Scaffold(
        containerColor = e.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                title = {
                    Column {
                        Text(m.codigo.ifEmpty { "Motor" }, fontWeight = FontWeight.Bold, fontFamily = Mono, fontSize = 20.sp, maxLines = 1)
                        Text(listOf(tipoLabel(d["tipo"]), d["ubicacion"] ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 12.sp, color = e.muted, maxLines = 1)
                    }
                },
                actions = {
                    IconButton(onClick = { exportOpen = true }) { Icon(Icons.Filled.FileDownload, "Exportar") }
                    IconButton(onClick = { vm.go(Screen.MotorForm(m.id)) }) { Icon(Icons.Filled.Edit, "Editar ficha") }
                    IconButton(onClick = { confirmDel = true }) { Icon(Icons.Filled.Delete, "Eliminar motor") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = e.panel),
            )
        },
        floatingActionButton = {
            when (vm.tab) {
                "mant" -> ExtendedFloatingActionButton(onClick = { vm.go(Screen.RegForm(KIND_MANT, m.id, null)) },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Registrar mantenimiento") })
                "fotos" -> ExtendedFloatingActionButton(onClick = { fotoMenu = true },
                    icon = { Icon(Icons.Filled.AddAPhoto, null) }, text = { Text("Agregar foto") })
                else -> ExtendedFloatingActionButton(onClick = { vm.go(Screen.RegForm(KIND_PRUEBA, m.id, null)) },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Registrar prueba") })
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 8.dp, containerColor = e.panel) {
                tabs.forEachIndexed { i, (k, l) ->
                    Tab(selected = i == tabIndex, onClick = { vm.tab = k },
                        text = { Text(l.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, fontSize = 13.sp) })
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MantBadge(res)
                    res.ultPrueba?.let { StateBadge("Última prueba ${fdate(it.fecha)}", RES_STATE[it.v["resultado"]]) }
                }
                when (vm.tab) {
                    "placa" -> PlacaTab(m)
                    "mec" -> MecTab(m)
                    "pruebas" -> if (pruebas.isEmpty()) Empty("Sin pruebas registradas",
                        "Registrá megado, resistencia de bobinados, vacío, carga, vibraciones y temperaturas. Los índices se calculan solos.")
                    else pruebas.forEach { p -> PruebaCard(p, m, onEdit = { vm.go(Screen.RegForm(KIND_PRUEBA, m.id, p.id)) }, onDelete = { delReg = p }) }
                    "mant" -> if (mants.isEmpty()) Empty("Sin mantenimientos",
                        "Cargá lubricaciones, cambios de rodamientos, rebobinados y la fecha del próximo servicio.")
                    else mants.forEach { x -> MantCard(x, onEdit = { vm.go(Screen.RegForm(KIND_MANT, m.id, x.id)) }, onDelete = { delReg = x }) }
                    "fotos" -> FotosTab(vm, m)
                }
            }
        }
    }

    if (confirmDel) ConfirmDialog("Eliminar motor", "Se borra la ficha de ${m.codigo.ifEmpty { "este motor" }} con todas sus pruebas, mantenimientos y fotos.",
        "Eliminar", { vm.deleteMotor(m.id); vm.back(); vm.say("Motor eliminado") }) { confirmDel = false }
    delReg?.let { r ->
        ConfirmDialog(if (r.kind == KIND_PRUEBA) "Eliminar prueba" else "Eliminar mantenimiento", "El registro del ${fdate(r.fecha)} se borra del teléfono.",
            "Eliminar", { vm.deleteReg(r.id); vm.say("Registro eliminado") }) { delReg = null }
    }
    if (fotoMenu) OptionsDialog("Agregar foto", "La foto se guarda en el teléfono, dentro de la app.", listOf(
        Opcion("Sacar foto", "Abrir la cámara") { openCamera() },
        Opcion("Elegir de la galería", "Usar una foto que ya tenés") {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )) { fotoMenu = false }
    if (exportOpen) {
        val base = safeName(m.codigo)
        OptionsDialog("Exportar ${m.codigo.ifEmpty { "motor" }}", "Se genera un CSV que abre en Excel.", listOf(
            Opcion("Ficha completa", "Datos técnicos, ${pruebas.size} pruebas y ${mants.size} mantenimientos") {
                shareText(ctx, "motor-$base-ficha-${today()}.csv", exportMotor(m, vm.regs.toList(), "todo"), "text/csv")
            },
            Opcion("Solo datos técnicos", "Placa, rodamientos, brida, eje, medidas y bobinado") {
                shareText(ctx, "motor-$base-datos-${today()}.csv", exportMotor(m, vm.regs.toList(), "tec"), "text/csv")
            },
            Opcion("Solo historial", "Pruebas con sus índices calculados y mantenimientos") {
                shareText(ctx, "motor-$base-historial-${today()}.csv", exportMotor(m, vm.regs.toList(), "hist"), "text/csv")
            },
        )) { exportOpen = false }
    }
}

@Composable
private fun PlacaTab(m: Motor) {
    val e = C.x
    val d = m.d
    val placa = motorSection("placa").fields
        .filter { it.key !in setOf("marca", "modelo", "serie") }
        .mapNotNull { f -> fieldValue(f, d)?.let { f.label to it } }
    Surface(color = e.plate, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, e.plateLine), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text((d["marca"] ?: "Motor").uppercase(), fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.sp,
                    color = e.plateInk, modifier = Modifier.weight(1f))
                Text(listOfNotNull(d["modelo"], d["serie"]?.let { "Nº $it" }).joinToString(" · "), fontFamily = Mono, fontSize = 12.sp, color = e.plateInk)
            }
            Spacer(Modifier.size(8.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(e.plateLine))
            Spacer(Modifier.size(8.dp))
            if (placa.isEmpty()) Text("Sin datos de placa cargados.", color = e.plateInk, fontSize = 14.sp)
            else Column(Modifier.border(1.dp, e.plateLine)) {
                placa.chunked(2).forEach { row ->
                    Row {
                        row.forEach { (l, v) ->
                            Column(Modifier.weight(1f).border(0.5.dp, e.plateLine).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(l.uppercase(), fontSize = 10.sp, letterSpacing = 0.6.sp, color = e.plateInk.copy(alpha = 0.75f), maxLines = 1)
                                Text(v, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = e.plateInk)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    MOTOR.filter { it.forTipos != null && d["tipo"] in it.forTipos }.forEach { s -> SecCard(s.title) { FieldsView(s.fields, d) } }
    SecCard("Identificación") { FieldsView(motorSection("ident").fields, d) }
    if (!d["notas"].isNullOrBlank()) SecCard("Notas") { FieldsView(motorSection("notas").fields, d) }
}

@Composable
private fun MecTab(m: Motor) {
    val d = m.d
    SecCard(motorSection("rod").title) { FieldsView(motorSection("rod").fields, d) }
    SecCard(motorSection("brida").title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlangeDiagram(d, Modifier.width(120.dp))
            Column(Modifier.weight(1f)) {
                FieldsView(motorSection("brida").fields.filter { it.key in setOf("forma", "brida", "bCant") }, d, emptyText = null)
            }
        }
        FieldsView(motorSection("brida").fields.filter { it.key !in setOf("forma", "brida", "bCant") }, d)
    }
    SecCard(motorSection("eje").title) { FieldsView(motorSection("eje").fields, d) }
    SecCard(motorSection("aj").title) { FieldsView(motorSection("aj").fields, d) }
}

@Composable
private fun FlangeDiagram(d: Map<String, String>, modifier: Modifier) {
    val e = C.x
    val ink = e.ink; val muted = e.muted; val accent = e.accent; val panel = e.panel; val panel2 = e.panel2
    Canvas(modifier.aspectRatio(1f)) {
        val c = Offset(size.width / 2, size.height / 2)
        val s = size.minDimension / 160f
        drawCircle(panel2, 66 * s, c)
        drawCircle(muted, 66 * s, c, style = Stroke(2 * s))
        drawCircle(accent, 50 * s, c, style = Stroke(1.2f * s))
        drawCircle(panel, 32 * s, c)
        drawCircle(ink, 32 * s, c, style = Stroke(2 * s))
        drawCircle(muted, 11 * s, c)
        for (a in listOf(45.0, 135.0, 225.0, 315.0)) {
            val r = Math.toRadians(a)
            val p = Offset(c.x + (50 * s * Math.cos(r)).toFloat(), c.y + (50 * s * Math.sin(r)).toFloat())
            drawCircle(panel, 5 * s, p)
            drawCircle(ink, 5 * s, p, style = Stroke(1.5f * s))
        }
        val paint = Paint().apply { isAntiAlias = true; textSize = 11 * s; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.MONOSPACE }
        fun label(t: String, y: Float, col: Color) { paint.color = col.toArgb(); drawContext.canvas.nativeCanvas.drawText(t, c.x, y, paint) }
        label("M ${d["bM"] ?: ""}", 22 * s, accent)
        label("N ${d["bN"] ?: ""}", c.y - 18 * s, ink)
        label("P ${d["bP"] ?: ""}", 158 * s, muted)
    }
}

@Composable
private fun PruebaCard(p: Registro, m: Motor, onEdit: () -> Unit, onDelete: () -> Unit) {
    val e = C.x
    var open by remember { mutableStateOf(false) }
    val c = calcPrueba(p.v, m.tipo)
    val secs = PRUEBA.filter { it.id in p.secciones }
    SecCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fdate(p.fecha), fontFamily = Mono, fontWeight = FontWeight.Bold, color = e.ink)
            StateBadge(p.v["resultado"] ?: "—", RES_STATE[p.v["resultado"]])
            p.v["motivo"]?.let { StateBadge(it, null, dot = false) }
            Spacer(Modifier.weight(1f))
            if (p.tecnico.isNotBlank()) Text(p.tecnico, fontSize = 13.sp, color = e.muted, maxLines = 1)
        }
        Text(secs.joinToString(" · ") { it.title }, fontSize = 13.sp, color = e.muted)
        val at = ambT(p.v); val ah = p.v["amb_h"]
        if (at != null || ah != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (at != null) StateBadge("Ambiente $at °C", null, dot = false)
            if (ah != null) StateBadge("Humedad $ah % HR", null, dot = false)
        }
        secs.forEach { s ->
            if (s.id == "ais") {
                SubTitle(s.title)
                AisTable(p.v)
                CalcList(c["ais"])
            } else if (c[s.id] != null) {
                SubTitle(s.title)
                CalcList(c[s.id])
            }
        }
        p.v["obs"]?.let { Text(it, fontSize = 14.sp, color = e.ink) }
        TextButton(onClick = { open = !open }) { Text(if (open) "Ocultar valores medidos" else "Ver valores medidos") }
        if (open) {
            secs.filter { it.id != "ais" }.forEach { s ->
                SubTitle(s.title)
                FieldsView(fieldsFor(s, m.tipo, includeLegacy = true), p.v, emptyText = "Sin valores.")
            }
            p.v["instrumento"]?.let { Text("Instrumentos: $it", fontSize = 13.sp, color = e.muted) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Eliminar", color = e.bad) }
            }
        }
    }
}

/** Tabla de aislamiento: lecturas por medición con DAR e IP coloreados. */
@Composable
fun AisTable(v: Map<String, String>) {
    val e = C.x
    val rows = aisRows(v)
    if (rows.isEmpty()) { Text("Sin valores cargados.", color = e.muted, fontSize = 14.sp); return }
    Column {
        AisHeader()
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, Modifier.weight(1.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = e.ink)
                listOf(r.r30, r.r1, r.r10).forEach { x ->
                    Text(x?.let { fmtR(it) } ?: "—", Modifier.weight(1f), fontFamily = Mono, fontSize = 12.sp, color = e.ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                IdxCell(r.dar, r.dar?.let { stDar(it) }, Modifier.weight(0.9f))
                IdxCell(r.ip, if ((r.r1 ?: 0.0) > 5000) null else r.ip?.let { stIp(it) }, Modifier.weight(0.9f))
            }
            Divider()
        }
        Text("Valores en MΩ" + (v["ais_v"]?.let { " · tensión de prueba $it V" } ?: ""), fontSize = 12.sp, color = e.muted,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun AisHeader() {
    val e = C.x
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        listOf("Medición" to 1.5f, "30 s" to 1f, "1 min" to 1f, "10 min" to 1f, "DAR" to 0.9f, "IP" to 0.9f).forEachIndexed { i, (t, w) ->
            Text(t.uppercase(), Modifier.weight(w), fontSize = 10.sp, color = e.muted, fontWeight = FontWeight.Bold,
                textAlign = if (i == 0) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
        }
    }
    Divider()
}

@Composable
fun IdxCell(v: Double?, state: String?, modifier: Modifier) {
    val e = C.x
    val (bg, fg) = when (state) {
        "ok" -> e.okSoft to e.ok
        "obs" -> e.obsSoft to e.obs
        "mal" -> e.badSoft to e.bad
        else -> Color.Transparent to e.ink
    }
    Box(modifier.padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Text(v?.let { fx(it) } ?: "—", Modifier.background(if (v == null) Color.Transparent else bg, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
            fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun MantCard(x: Registro, onEdit: () -> Unit, onDelete: () -> Unit) {
    val e = C.x
    SecCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fdate(x.fecha), fontFamily = Mono, fontWeight = FontWeight.Bold, color = e.ink)
            StateBadge(x.v["tipo"] ?: "", "info")
            x.v["ot"]?.let { StateBadge(it, null, dot = false) }
            Spacer(Modifier.weight(1f))
            if (x.tecnico.isNotBlank()) Text(x.tecnico, fontSize = 13.sp, color = e.muted, maxLines = 1)
        }
        x.v["descripcion"]?.let { Text(it, fontSize = 14.sp, color = e.ink) }
        FieldsView(MANT.filter { it.key !in setOf("fecha", "tipo", "ot", "descripcion") }, x.v, emptyText = null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text("Editar") }
            TextButton(onClick = onDelete) { Text("Eliminar", color = e.bad) }
        }
    }
}

@Composable
private fun FotosTab(vm: AppVM, m: Motor) {
    var viewing by remember { mutableStateOf<String?>(null) }
    if (m.fotos.isEmpty()) {
        Empty("Sin fotos", "Sacá una foto de la placa, la brida o el bobinado y quedará guardada en la ficha.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            m.fotos.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { f ->
                        Thumb(vm.repo.fotoFile(f.file), Modifier.weight(1f).aspectRatio(4f / 3f).clickable { viewing = f.file })
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    viewing?.let { file ->
        var confirm by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(vm.repo.fotoFile(file), Modifier.fillMaxWidth().weight(1f), fit = true, maxSide = 2000)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(onClick = { confirm = true }) { Text("Quitar foto", color = Color(0xFFF08B83)) }
                    TextButton(onClick = { viewing = null }) { Text("Cerrar", color = Color.White) }
                }
            }
        }
        if (confirm) ConfirmDialog("Quitar foto", "La foto se borra del teléfono.", "Quitar",
            { vm.removeFoto(m.id, file); viewing = null }) { confirm = false }
    }
}

@Composable
private fun Thumb(file: File, modifier: Modifier, fit: Boolean = false, maxSide: Int = 500) {
    val e = C.x
    val bmp by produceState<Bitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxSide) }
    }
    Box(modifier.background(e.panel2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
        }
    }
}

private fun decodeSampled(f: File, maxSide: Int): Bitmap? {
    if (!f.exists()) return null
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, b)
    var s = 1
    while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxSide) s *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
}
