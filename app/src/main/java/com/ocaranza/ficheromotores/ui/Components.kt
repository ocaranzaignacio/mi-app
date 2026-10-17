package com.ocaranza.ficheromotores.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ocaranza.ficheromotores.data.CalcRow
import com.ocaranza.ficheromotores.data.FT
import com.ocaranza.ficheromotores.data.Field
import com.ocaranza.ficheromotores.data.fieldValue
import com.ocaranza.ficheromotores.util.fdate
import java.io.File
import java.time.LocalDate

/* ---------- Etiquetas de estado ---------- */

@Composable
fun StateBadge(text: String, state: String?, dot: Boolean = true) {
    val e = C.x
    val (bg, fg) = when (state) {
        "ok" -> e.okSoft to e.ok
        "obs" -> e.obsSoft to e.obs
        "mal" -> e.badSoft to e.bad
        "info" -> e.accentSoft to e.accent
        else -> e.panel2 to e.muted
    }
    Row(
        Modifier.background(bg, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).background(fg, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

val RES_STATE = mapOf("Aprobado" to "ok", "Observado" to "obs", "Rechazado" to "mal")

/* ---------- Tarjeta de sección ---------- */

@Composable
fun SecCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val e = C.x
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = e.panel,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, e.line),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title.uppercase(), style = SectionTitle, color = e.muted)
            content()
        }
    }
}

/** Grilla de dos columnas con etiqueta y valor. */
@Composable
fun KvGrid(items: List<Pair<String, String>>) {
    val e = C.x
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (l, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(l, fontSize = 12.sp, color = e.muted)
                        Text(v, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = e.ink)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Muestra los campos con valor: los cortos en grilla y los textos largos debajo. */
@Composable
fun FieldsView(fields: List<Field>, d: Map<String, String>, emptyText: String? = "Sin datos cargados.") {
    val e = C.x
    val items = fields.mapNotNull { f -> fieldValue(f, d)?.let { Triple(f.label, it, f.type) } }
    val short = items.filter { it.third != FT.AREA }.map { it.first to it.second }
    val long = items.filter { it.third == FT.AREA }
    if (items.isEmpty()) {
        if (emptyText != null) Text(emptyText, color = e.muted, fontSize = 14.sp)
        return
    }
    if (short.isNotEmpty()) KvGrid(short)
    long.forEach { (l, v, _) ->
        Column {
            Text(l, fontSize = 12.sp, color = e.muted)
            Text(v, fontSize = 14.sp, color = e.ink)
        }
    }
}

@Composable
fun CalcList(rows: List<CalcRow>?) {
    if (rows.isNullOrEmpty()) return
    val e = C.x
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { r ->
            val (bg, fg) = when (r.state) {
                "ok" -> e.okSoft to e.ok
                "obs" -> e.obsSoft to e.obs
                "mal" -> e.badSoft to e.bad
                else -> e.panel2 to e.ink
            }
            Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, Modifier.weight(1f), fontSize = 14.sp, color = e.ink)
                    Text(r.value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, color = fg, fontSize = 14.sp)
                }
                if (r.note != null) Text(r.note, fontSize = 12.sp, color = e.muted)
            }
        }
    }
}

/* ---------- Campos de formulario ---------- */

@Composable
fun FieldInput(f: Field, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val label = if (f.req) "${f.label} *" else f.label
    when (f.type) {
        FT.SELECT -> SelectField(label, value, f.opts, onChange, modifier, allowEmpty = true)
        FT.DATE -> DateField(label, value, onChange, modifier)
        else -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = modifier.fillMaxWidth(),
            label = { Text(label, maxLines = 1) },
            placeholder = f.ph?.let { { Text(it, maxLines = 1) } },
            suffix = f.unit?.let { { Text(it, fontFamily = Mono, fontSize = 12.sp) } },
            singleLine = f.type != FT.AREA,
            minLines = if (f.type == FT.AREA) 3 else 1,
            keyboardOptions = KeyboardOptions(keyboardType = if (f.type == FT.NUM) KeyboardType.Decimal else KeyboardType.Text),
        )
    }
}

@Composable
fun SelectField(
    label: String, value: String, opts: List<Pair<String, String>>, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, allowEmpty: Boolean = true, emptyLabel: String = "—",
) {
    var open by remember { mutableStateOf(false) }
    val shown = opts.firstOrNull { it.first == value }?.second ?: if (value.isEmpty()) "" else value
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowEmpty) DropdownMenuItem(text = { Text(emptyLabel) }, onClick = { onChange(""); open = false })
            opts.forEach { (k, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onChange(k); open = false }) }
        }
    }
}

@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = fdate(value), onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable {
            val d = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
            DatePickerDialog(ctx, { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        })
    }
}

/** Ubica los campos de a dos por fila; los textos largos ocupan la fila entera. */
fun fieldRows(fields: List<Field>): List<List<Field>> {
    val out = mutableListOf<List<Field>>()
    var pending: Field? = null
    for (f in fields) {
        if (f.wide || f.type == FT.AREA) {
            pending?.let { out += listOf(it); pending = null }
            out += listOf(f)
        } else if (pending == null) {
            pending = f
        } else {
            out += listOf(pending!!, f)
            pending = null
        }
    }
    pending?.let { out += listOf(it) }
    return out
}

@Composable
fun FieldsForm(fields: List<Field>, values: Map<String, String>, onChange: (String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fieldRows(fields).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f ->
                    FieldInput(f, values[f.key] ?: "", { onChange(f.key, it) }, Modifier.weight(1f))
                }
                if (row.size == 1 && !(row[0].wide || row[0].type == FT.AREA)) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ---------- Diálogos ---------- */

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = C.x.bad) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

data class Opcion(val label: String, val desc: String, val action: () -> Unit)

@Composable
fun OptionsDialog(title: String, text: String, options: List<Opcion>, onDismiss: () -> Unit) {
    val e = C.x
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, color = e.muted)
                options.forEach { o ->
                    Column(
                        Modifier.fillMaxWidth()
                            .border(1.dp, e.line, RoundedCornerShape(10.dp))
                            .background(e.panel2, RoundedCornerShape(10.dp))
                            .clickable { onDismiss(); o.action() }
                            .padding(12.dp)
                    ) {
                        Text(o.label, fontWeight = FontWeight.SemiBold, color = e.ink)
                        Text(o.desc, fontSize = 13.sp, color = e.muted)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/* ---------- Compartir archivos ---------- */

fun shareText(ctx: Context, fileName: String, content: String, mime: String) {
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, fileName)
    f.writeText(content, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Compartir $fileName").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun safeName(s: String): String = s.replace(Regex("[^\\w.-]+"), "_").ifEmpty { "motor" }

@Composable
fun Divider(color: Color = C.x.line) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SubTitle(text: String) {
    Text(text.uppercase(), style = SectionTitle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp))
}
