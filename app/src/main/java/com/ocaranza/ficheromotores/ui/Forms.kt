@file:OptIn(ExperimentalMaterial3Api::class)

package com.ocaranza.ficheromotores.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ocaranza.ficheromotores.data.AIS_P
import com.ocaranza.ficheromotores.data.AIS_T
import com.ocaranza.ficheromotores.data.AIS_V
import com.ocaranza.ficheromotores.data.Field
import com.ocaranza.ficheromotores.data.KIND_PRUEBA
import com.ocaranza.ficheromotores.data.MANT
import com.ocaranza.ficheromotores.data.MOTOR
import com.ocaranza.ficheromotores.data.Motor
import com.ocaranza.ficheromotores.data.PRUEBA
import com.ocaranza.ficheromotores.data.PRUEBA_AMB
import com.ocaranza.ficheromotores.data.PRUEBA_FIN
import com.ocaranza.ficheromotores.data.PRUEBA_GEN
import com.ocaranza.ficheromotores.data.Registro
import com.ocaranza.ficheromotores.data.aisRows
import com.ocaranza.ficheromotores.data.ambT
import com.ocaranza.ficheromotores.data.calcPrueba
import com.ocaranza.ficheromotores.data.fieldsFor
import com.ocaranza.ficheromotores.data.stDar
import com.ocaranza.ficheromotores.data.stIp
import com.ocaranza.ficheromotores.util.newId
import com.ocaranza.ficheromotores.util.nowIso
import com.ocaranza.ficheromotores.util.today

@Composable
private fun FormScaffold(vm: AppVM, title: String, subtitle: String, saveText: String, onSave: () -> Unit, content: @Composable () -> Unit) {
    val e = C.x
    Scaffold(
        containerColor = e.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar") } },
                title = {
                    Column {
                        Text(title.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 20.sp, maxLines = 1)
                        if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = e.muted, maxLines = 1)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = e.panel),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = { vm.back() }) { Text("Cancelar") }
                Button(onClick = onSave) { Text(saveText) }
            }
        }
    }
}

private fun firstMissing(fields: List<Field>, values: Map<String, String>): Field? =
    fields.firstOrNull { it.req && values[it.key].isNullOrBlank() }

/* ---------------- Ficha del motor ---------------- */

@Composable
fun MotorFormScreen(vm: AppVM, motorId: String?) {
    val m = motorId?.let { vm.motor(it) }
    val values = remember(motorId) { mutableStateMapOf<String, String>().apply { putAll(m?.d ?: emptyMap()) } }
    val tipo = values["tipo"]
    val visibles = MOTOR.filter { it.forTipos == null || tipo in it.forTipos }

    FormScaffold(vm, if (m != null) "Editar ficha" else "Nuevo motor", m?.codigo ?: "Datos de placa y medidas",
        if (m != null) "Guardar cambios" else "Guardar motor", onSave = {
            val miss = firstMissing(visibles.flatMap { it.fields }, values)
            if (miss != null) { vm.say("Completá: ${miss.label}"); return@FormScaffold }
            val codigo = values["codigo"]!!.trim()
            if (vm.motors.any { it.id != m?.id && it.codigo.equals(codigo, ignoreCase = true) }) {
                vm.say("Ya existe un motor con el código $codigo"); return@FormScaffold
            }
            val keep = visibles.flatMap { s -> s.fields.map { it.key } }.toSet()
            val nd = values.filter { (k, v) -> k in keep && v.isNotBlank() }.mapValues { it.value.trim() }
            if (m != null) {
                vm.saveMotor(m.copy(d = nd, act = nowIso()))
                vm.back()
                vm.say("Ficha actualizada")
            } else {
                val nm = Motor(id = newId(), d = nd, creado = nowIso())
                vm.saveMotor(nm)
                vm.replace(Screen.Ficha(nm.id))
                vm.tab = "placa"
                vm.say("Motor guardado")
            }
        }) {
        visibles.forEach { s ->
            SecCard(s.title) {
                FieldsForm(s.fields, values) { k, v -> values[k] = v }
            }
        }
    }
}

/* ---------------- Pruebas y mantenimientos ---------------- */

@Composable
fun RegFormScreen(vm: AppVM, kind: String, motorId: String, regId: String?) {
    val m = vm.motor(motorId) ?: run { Empty("Motor no encontrado", "Este motor ya no está en el fichero."); return }
    val rec = regId?.let { id -> vm.regs.firstOrNull { it.id == id } }
    val isP = kind == KIND_PRUEBA
    val values = remember(regId) {
        mutableStateMapOf<String, String>().apply {
            putAll(rec?.v ?: emptyMap())
            if (rec == null) put("fecha", today())
            if (isP) ambT(rec?.v ?: emptyMap())?.let { put("amb_t", it) }
        }
    }
    val secs = remember(regId) {
        mutableStateListOf<String>().apply {
            addAll(rec?.secciones ?: if (m.tipo == "cc") listOf("ais", "bob") else listOf("ais", "bob", "vac"))
        }
    }
    val title = if (isP) (if (rec != null) "Editar prueba" else "Nueva prueba") else (if (rec != null) "Editar mantenimiento" else "Nuevo mantenimiento")

    FormScaffold(vm, title, m.codigo, "Guardar", onSave = {
        val req = if (isP) PRUEBA_GEN + PRUEBA_FIN else MANT
        val miss = firstMissing(req, values)
        if (miss != null) { vm.say("Completá: ${miss.label}"); return@FormScaffold }
        if (isP && secs.isEmpty()) { vm.say("Marcá al menos un ensayo"); return@FormScaffold }
        val v = values.filter { it.value.isNotBlank() }.mapValues { it.value.trim() }.toMutableMap()
        if (isP) PRUEBA.filter { it.id !in secs }.forEach { s -> s.fields.forEach { f -> v.remove(f.key) } }
        val ordered = PRUEBA.map { it.id }.filter { it in secs }
        val r = Registro(
            id = rec?.id ?: newId(), motorId = m.id, kind = kind, v = v,
            secciones = if (isP) ordered else emptyList(),
            tecnico = rec?.tecnico?.ifBlank { null } ?: vm.tecnico,
            creado = rec?.creado ?: nowIso(),
            act = if (rec != null) nowIso() else null,
        )
        vm.saveReg(r)
        vm.tab = if (isP) "pruebas" else "mant"
        vm.back()
        vm.say(if (isP) "Prueba guardada" else "Mantenimiento guardado")
    }) {
        if (!isP) {
            SecCard("Mantenimiento") { FieldsForm(MANT, values) { k, x -> values[k] = x } }
            return@FormScaffold
        }
        val calc = calcPrueba(values, m.tipo)
        SecCard("Datos generales") { FieldsForm(PRUEBA_GEN, values) { k, x -> values[k] = x } }
        SecCard("Condiciones ambientales") { FieldsForm(PRUEBA_AMB, values) { k, x -> values[k] = x } }
        SecCard("Ensayos realizados") {
            PRUEBA.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { if (s.id in secs) secs.remove(s.id) else secs.add(s.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = s.id in secs, onCheckedChange = { c -> if (c) secs.add(s.id) else secs.remove(s.id) })
                    Text(s.title, fontSize = 15.sp)
                }
            }
        }
        PRUEBA.filter { it.id in secs }.forEach { s ->
            SecCard(s.title) {
                if (s.id == "ais") {
                    FieldsForm(listOf(AIS_V), values) { k, x -> values[k] = x }
                    AisForm(values)
                } else {
                    FieldsForm(fieldsFor(s, m.tipo, includeLegacy = false), values) { k, x -> values[k] = x }
                }
                val rows = calc[s.id]
                if (!rows.isNullOrEmpty()) {
                    Box(Modifier.fillMaxWidth().border(1.dp, C.x.accent, RoundedCornerShape(8.dp)).padding(10.dp)) { CalcList(rows) }
                }
            }
        }
        SecCard("Conclusión") { FieldsForm(PRUEBA_FIN, values) { k, x -> values[k] = x } }
    }
}

@Composable
private fun AisForm(values: MutableMap<String, String>) {
    val e = C.x
    val rows = aisRows(values, all = true).associateBy { it.q }
    Column {
        AisHeader()
        AIS_P.forEach { (q, label) ->
            val r = rows.getValue(q)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = e.ink)
                AIS_T.forEach { (t, _) ->
                    val k = "ais_${q}_$t"
                    CompactNum(values[k] ?: "", { values[k] = it }, Modifier.weight(1f).padding(horizontal = 2.dp))
                }
                IdxCell(r.dar, r.dar?.let { stDar(it) }, Modifier.weight(0.9f))
                IdxCell(r.ip, if ((r.r1 ?: 0.0) > 5000) null else r.ip?.let { stIp(it) }, Modifier.weight(0.9f))
            }
        }
        Text("Valores en MΩ. Cargá solo la lectura de 1 min para una medición puntual, o las tres para obtener DAR e IP.",
            fontSize = 12.sp, color = e.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CompactNum(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val e = C.x
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = e.ink, textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(e.accent),
        modifier = modifier
            .background(e.panel2, RoundedCornerShape(6.dp))
            .border(1.dp, e.line, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 9.dp),
    )
}

