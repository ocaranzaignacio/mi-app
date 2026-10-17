@file:OptIn(ExperimentalMaterial3Api::class)

package com.ocaranza.ficheromotores.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ocaranza.ficheromotores.data.BRIDAS
import com.ocaranza.ficheromotores.data.ESTADOS
import com.ocaranza.ficheromotores.data.Filtros
import com.ocaranza.ficheromotores.data.MANT_F
import com.ocaranza.ficheromotores.data.Motor
import com.ocaranza.ficheromotores.data.PRUEBA_F
import com.ocaranza.ficheromotores.data.Resumen
import com.ocaranza.ficheromotores.data.TIPOS
import com.ocaranza.ficheromotores.data.estadoLabel
import com.ocaranza.ficheromotores.data.exportList
import com.ocaranza.ficheromotores.data.filtrosActivos
import com.ocaranza.ficheromotores.data.matches
import com.ocaranza.ficheromotores.data.motorSpec
import com.ocaranza.ficheromotores.data.naturalOrder
import com.ocaranza.ficheromotores.data.resumen
import com.ocaranza.ficheromotores.data.sin
import com.ocaranza.ficheromotores.data.tipoLabel
import com.ocaranza.ficheromotores.util.daysTo
import com.ocaranza.ficheromotores.util.fdate
import com.ocaranza.ficheromotores.util.today

@Composable
fun ListScreen(vm: AppVM) {
    val e = C.x
    val ctx = LocalContext.current
    var exportOpen by remember { mutableStateOf(false) }
    val all = vm.motors.toList()
    val regs = vm.regs.toList()
    val res = all.associate { it.id to resumen(it.id, regs) }
    val f = vm.filtros
    val list = all.filter { matches(it, res.getValue(it.id), f) }.sortedWith(compareBy<Motor, String>(naturalOrder) { it.codigo })
    val venc = all.count { val x = daysTo(res.getValue(it.id).proxMant); x != null && x < 0 }
    val taller = all.count { it.d["estado"] == "taller" }
    val activos = filtrosActivos(f)

    Scaffold(
        containerColor = e.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FICHERO DE MOTORES", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 20.sp)
                        Text("Registro técnico del taller", fontSize = 12.sp, color = e.muted)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.go(Screen.Ajustes) }) { Icon(Icons.Filled.Settings, "Ajustes y respaldo") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = e.panel),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.go(Screen.MotorForm(null)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nuevo motor") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Motores", all.size, false, Modifier.weight(1f))
                    Stat("En taller", taller, false, Modifier.weight(1f))
                    Stat("Mant. vencido", venc, venc > 0, Modifier.weight(1f))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = f.q, onValueChange = { vm.filtros = f.copy(q = it) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("Buscar TAG, marca, serie, rodamiento…", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                    )
                    OutlinedButton(onClick = { vm.showFiltros = !vm.showFiltros }) {
                        Icon(Icons.Filled.FilterList, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (activos.isEmpty()) "Filtros" else "Filtros · ${activos.size}")
                    }
                }
            }
            if (vm.showFiltros) item { FiltrosPanel(vm) }
            if (activos.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    activos.forEach { (k, label) ->
                        FilterChip(
                            selected = true, onClick = { vm.filtros = f.sin(k) },
                            label = { Text(label) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Quitar filtro") },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (all.isEmpty()) "" else if (list.size == all.size) "${all.size} motores" else "${list.size} de ${all.size} motores",
                        color = e.muted, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { exportOpen = true }, enabled = list.isNotEmpty()) {
                        Icon(Icons.Filled.FileDownload, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Exportar listado")
                    }
                }
            }
            if (!vm.loaded) {
                item { Empty("Cargando fichero…", "Leyendo los datos guardados en el teléfono.") }
            } else if (all.isEmpty()) {
                item { Empty("El fichero está vacío", "Tocá Nuevo motor para cargar el primero con sus datos de placa, rodamientos y medidas. Si tenés un respaldo, importalo desde Ajustes.") }
            } else if (list.isEmpty()) {
                item { Empty(null, "Ningún motor coincide con los filtros.") }
            } else {
                items(list, key = { it.id }) { m -> MotorCard(m, res.getValue(m.id)) { vm.go(Screen.Ficha(m.id)) } }
            }
        }
    }

    if (exportOpen) {
        val n = list.size
        OptionsDialog(
            "Exportar listado",
            "$n ${if (n == 1) "motor" else "motores"}${if (activos.isNotEmpty() || f.q.isNotBlank()) " según los filtros aplicados" else ""}. Se genera un CSV que abre en Excel.",
            listOf(
                Opcion("Resumen", "Código, placa principal, rodamientos, brida, ubicación, última prueba y próximo mantenimiento") {
                    shareText(ctx, "motores-resumen-${today()}.csv", exportList(list, regs, false, f), "text/csv")
                },
                Opcion("Todos los datos técnicos", "Cada campo de la ficha en una columna") {
                    shareText(ctx, "motores-todo-${today()}.csv", exportList(list, regs, true, f), "text/csv")
                },
            ),
        ) { exportOpen = false }
    }
}

@Composable
private fun Stat(label: String, n: Int, alert: Boolean, modifier: Modifier) {
    val e = C.x
    Surface(modifier, color = e.panel, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, e.line)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("$n", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (alert) e.bad else e.ink)
            Text(label.uppercase(), fontSize = 11.sp, color = e.muted, letterSpacing = 0.8.sp, maxLines = 1)
        }
    }
}

@Composable
fun Empty(title: String?, text: String) {
    val e = C.x
    Surface(Modifier.fillMaxWidth(), color = e.panel, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, e.line)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) Text(title.uppercase(), style = SectionTitle.copy(fontSize = 18.sp), color = e.ink)
            Text(text, color = e.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun MantBadge(r: Resumen) {
    val p = r.proxMant ?: return
    val d = daysTo(p) ?: return
    when {
        d < 0 -> StateBadge("Mant. vencido ${fdate(p)}", "mal")
        d <= 15 -> StateBadge("Mant. ${fdate(p)}", "obs")
        else -> StateBadge("Próx. mant. ${fdate(p)}", "info")
    }
}

@Composable
private fun MotorCard(m: Motor, r: Resumen, onClick: () -> Unit) {
    val e = C.x
    val d = m.d
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = e.panel, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, e.line),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.codigo.ifEmpty { "(sin código)" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = e.ink, modifier = Modifier.weight(1f))
                Text(tipoLabel(d["tipo"]).uppercase(), fontSize = 11.sp, color = e.muted, letterSpacing = 0.8.sp)
            }
            Text(motorSpec(d), color = e.muted, fontSize = 14.sp)
            val lugar = listOfNotNull(d["cliente"], d["ubicacion"]).joinToString(" — ")
            if (lugar.isNotEmpty()) Text(lugar, color = e.muted, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d["estado"]?.let { st ->
                    StateBadge(estadoLabel(st), when (st) { "taller" -> "obs"; "baja" -> null; else -> "ok" })
                }
                r.ultPrueba?.let { p ->
                    val res = p.v["resultado"] ?: "—"
                    StateBadge("Prueba ${fdate(p.fecha)}: $res", RES_STATE[res])
                }
                MantBadge(r)
            }
        }
    }
}

@Composable
private fun FiltrosPanel(vm: AppVM) {
    val f = vm.filtros
    fun set(x: Filtros) { vm.filtros = x }
    SecCard(null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumFilter("Potencia desde (kW)", f.potMin, Modifier.weight(1f)) { set(f.copy(potMin = it)) }
                NumFilter("Potencia hasta (kW)", f.potMax, Modifier.weight(1f)) { set(f.copy(potMax = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("Polos", f.polos, listOf("2", "4", "6", "8", "10", "12").map { it to it }, { set(f.copy(polos = it)) }, Modifier.weight(1f), emptyLabel = "Todos")
                SelectField("Tipo", f.tipo, TIPOS, { set(f.copy(tipo = it)) }, Modifier.weight(1f), emptyLabel = "Todos")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("Estado", f.estado, ESTADOS, { set(f.copy(estado = it)) }, Modifier.weight(1f), emptyLabel = "Todos")
                TextFilter("Marca", f.marca, Modifier.weight(1f)) { set(f.copy(marca = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextFilter("Carcasa", f.carcasa, Modifier.weight(1f)) { set(f.copy(carcasa = it)) }
                TextFilter("Tensión", f.tension, Modifier.weight(1f)) { set(f.copy(tension = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextFilter("Rodamiento", f.rod, Modifier.weight(1f)) { set(f.copy(rod = it)) }
                TextFilter("Cliente / ubicación", f.lugar, Modifier.weight(1f)) { set(f.copy(lugar = it)) }
            }
            SelectField("Brida", f.brida, BRIDAS, { set(f.copy(brida = it)) }, emptyLabel = "Todas")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField("Última prueba", f.prueba, PRUEBA_F, { set(f.copy(prueba = it)) }, Modifier.weight(1f), emptyLabel = "Todas")
                SelectField("Mantenimiento", f.mant, MANT_F, { set(f.copy(mant = it)) }, Modifier.weight(1f), emptyLabel = "Todos")
            }
            Text("Las potencias en HP o CV se convierten a kW para comparar.", fontSize = 12.sp, color = C.x.muted)
            TextButton(onClick = { set(Filtros(q = f.q)) }, modifier = Modifier.align(Alignment.End)) { Text("Limpiar filtros") }
        }
    }
}

@Composable
private fun TextFilter(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier, singleLine = true, label = { Text(label, maxLines = 1) })
}

@Composable
private fun NumFilter(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, modifier, singleLine = true, label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}
