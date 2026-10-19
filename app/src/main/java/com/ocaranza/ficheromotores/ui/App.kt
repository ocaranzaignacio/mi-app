@file:OptIn(ExperimentalMaterial3Api::class)

package com.ocaranza.ficheromotores.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ocaranza.ficheromotores.data.KIND_MANT
import com.ocaranza.ficheromotores.data.KIND_PRUEBA
import com.ocaranza.ficheromotores.util.today

@Composable
fun FicheroApp(vm: AppVM) {
    val ctx = LocalContext.current
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }
    when (val s = vm.current) {
        Screen.Lista -> ListScreen(vm)
        is Screen.Ficha -> MotorScreen(vm, s.motorId)
        is Screen.MotorForm -> MotorFormScreen(vm, s.motorId)
        is Screen.RegForm -> RegFormScreen(vm, s.kind, s.motorId, s.regId)
        Screen.Ajustes -> AjustesScreen(vm)
    }
}

@Composable
fun AjustesScreen(vm: AppVM) {
    val e = C.x
    val ctx = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            vm.say(if (text == null) "No se pudo leer el archivo." else vm.importBackup(text))
        }
    }
    val nPruebas = vm.regs.count { it.kind == KIND_PRUEBA }
    val nMants = vm.regs.count { it.kind == KIND_MANT }

    Scaffold(
        containerColor = e.bg,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                title = { Text("AJUSTES", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = e.panel),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecCard("Técnico") {
                Text("Este nombre queda guardado en cada prueba y mantenimiento que registres desde este teléfono.",
                    fontSize = 14.sp, color = e.muted)
                OutlinedTextField(
                    value = vm.tecnico, onValueChange = { vm.setTecnicoName(it) }, singleLine = true,
                    label = { Text("Nombre del técnico") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            ServidorCard(vm)
            SecCard("Respaldo") {
                Text("En este teléfono hay ${vm.motors.size} motores, $nPruebas pruebas y $nMants mantenimientos. " +
                    (if (vm.servidor == null) "Sin servidor, los datos están solo acá: exportá un respaldo cada tanto y guardalo en Drive o mandalo por WhatsApp. "
                    else "También están en el servidor. ") + "Las fotos no se incluyen en el respaldo.",
                    fontSize = 14.sp, color = e.muted)
                Button(onClick = { shareText(ctx, "respaldo-fichero-motores-${today()}.json", vm.backupJson(), "application/json") },
                    modifier = Modifier.fillMaxWidth()) { Text("Exportar respaldo") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Importar respaldo") }
                Text("Importar suma los datos del archivo a los que ya tenés. Si un mismo registro está en los dos lados, queda la versión más reciente.",
                    fontSize = 12.sp, color = e.muted)
            }
            SecCard("Acerca de") {
                Text("Fichero de Motores ${com.ocaranza.ficheromotores.BuildConfig.VERSION_NAME} · Registro de motores, pruebas de laboratorio y mantenimiento.", fontSize = 14.sp, color = e.muted)
            }
        }
    }
}

@Composable
private fun ServidorCard(vm: AppVM) {
    val e = C.x
    val srv = vm.servidor
    SecCard("Servidor") {
        if (srv == null) {
            var url by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            var pass by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var probando by remember { mutableStateOf(false) }
            Text("Conectá la app al servidor del taller para compartir motores, pruebas, mantenimientos y fotos con los demás técnicos. " +
                "La app sigue funcionando sin señal y sincroniza sola cuando ve al servidor.", fontSize = 14.sp, color = e.muted)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Dirección del servidor") }, placeholder = { Text("http://100.64.12.3:8090") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Usuario (email)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            error?.let { Text(it, color = e.bad, fontSize = 14.sp) }
            Button(
                onClick = {
                    probando = true; error = null
                    vm.conectar(url, email, pass) { err ->
                        probando = false
                        if (err == null) vm.say("Conectado al servidor") else error = err
                    }
                },
                enabled = !probando, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (probando) "Conectando…" else "Conectar") }
            Text("La dirección y el usuario te los da quien instaló el servidor en la PC del taller.", fontSize = 12.sp, color = e.muted)
        } else {
            var confirmar by remember { mutableStateOf(false) }
            Text("Conectado a ${srv.url} como ${srv.email}.", fontSize = 14.sp, color = e.ink)
            SyncStatusLine(vm)
            Button(onClick = { vm.sincronizarAhora() }, enabled = !vm.syncing, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.syncing) "Sincronizando…" else "Sincronizar ahora")
            }
            OutlinedButton(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
            if (confirmar) ConfirmDialog("Desconectar del servidor",
                "Los datos quedan en este teléfono, pero dejan de sincronizarse con los demás técnicos.", "Desconectar",
                { vm.desconectar(); vm.say("Desconectado del servidor") }) { confirmar = false }
        }
    }
}

@Composable
fun SyncStatusLine(vm: AppVM) {
    val e = C.x
    val hora = vm.lastSync?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime() }
    val txt = when {
        vm.syncing -> "Sincronizando…"
        vm.syncError != null -> vm.syncError!! + " Se vuelve a intentar solo."
        hora != null -> "Sincronizado a las %02d:%02d.".format(hora.hour, hora.minute)
        else -> "Esperando la primera sincronización."
    } + if (vm.pendientes > 0) " ${vm.pendientes} ${if (vm.pendientes == 1) "cambio" else "cambios"} por subir." else ""
    Text(txt, fontSize = 13.sp, color = if (vm.syncError != null) e.bad else e.muted)
}
