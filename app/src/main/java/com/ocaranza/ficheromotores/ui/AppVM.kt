package com.ocaranza.ficheromotores.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ocaranza.ficheromotores.data.Fichero
import com.ocaranza.ficheromotores.data.Filtros
import com.ocaranza.ficheromotores.data.Foto
import com.ocaranza.ficheromotores.data.Motor
import com.ocaranza.ficheromotores.data.Registro
import com.ocaranza.ficheromotores.data.Repo
import com.ocaranza.ficheromotores.data.ficheroFromJson
import com.ocaranza.ficheromotores.data.toJson
import com.ocaranza.ficheromotores.util.nowIso
import com.ocaranza.ficheromotores.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Lista : Screen
    data class Ficha(val motorId: String) : Screen
    data class MotorForm(val motorId: String?) : Screen
    data class RegForm(val kind: String, val motorId: String, val regId: String?) : Screen
    data object Ajustes : Screen
}

class AppVM(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val prefs = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val writeLock = Mutex()

    val motors = mutableStateListOf<Motor>()
    val regs = mutableStateListOf<Registro>()
    var loaded by mutableStateOf(false)
        private set

    val stack = mutableStateListOf<Screen>(Screen.Lista)
    val current: Screen get() = stack.last()

    var filtros by mutableStateOf(Filtros())
    var showFiltros by mutableStateOf(false)
    var tab by mutableStateOf("placa")
    var tecnico by mutableStateOf(prefs.getString("tecnico", "") ?: "")
        private set
    var toast by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) { repo.load() }
            motors.addAll(f.motores)
            regs.addAll(f.registros)
            loaded = true
        }
    }

    fun go(s: Screen) {
        if (s is Screen.Ficha && current !is Screen.RegForm && current !is Screen.MotorForm) tab = "placa"
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Reemplaza la pantalla actual (por ejemplo, del formulario a la ficha recién creada). */
    fun replace(s: Screen) {
        stack.removeAt(stack.lastIndex)
        stack.add(s)
    }

    fun say(msg: String) { toast = msg }

    fun setTecnicoName(n: String) {
        tecnico = n
        prefs.edit().putString("tecnico", n).apply()
    }

    private fun persist() {
        val snap = Fichero(motors.toList(), regs.toList())
        viewModelScope.launch(Dispatchers.IO) {
            writeLock.withLock { repo.save(snap) }
        }
    }

    fun motor(id: String): Motor? = motors.firstOrNull { it.id == id }
    fun regsOf(motorId: String, kind: String): List<Registro> =
        regs.filter { it.motorId == motorId && it.kind == kind }.sortedByDescending { it.fecha }

    fun saveMotor(m: Motor) {
        val i = motors.indexOfFirst { it.id == m.id }
        if (i >= 0) motors[i] = m else motors.add(m)
        persist()
    }

    fun deleteMotor(id: String) {
        motor(id)?.fotos?.forEach { repo.borrarFoto(it.file) }
        motors.removeAll { it.id == id }
        regs.removeAll { it.motorId == id }
        persist()
    }

    fun saveReg(r: Registro) {
        val i = regs.indexOfFirst { it.id == r.id }
        if (i >= 0) regs[i] = r else regs.add(r)
        persist()
    }

    fun deleteReg(id: String) {
        regs.removeAll { it.id == id }
        persist()
    }

    fun addFoto(motorId: String, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { repo.importarFoto(uri) }
            if (name == null) { say("No se pudo guardar la foto."); return@launch }
            val m = motor(motorId) ?: return@launch
            saveMotor(m.copy(fotos = m.fotos + Foto(name, "Foto", today()), act = nowIso()))
            say("Foto agregada")
        }
    }

    fun removeFoto(motorId: String, file: String) {
        val m = motor(motorId) ?: return
        saveMotor(m.copy(fotos = m.fotos.filter { it.file != file }, act = nowIso()))
        repo.borrarFoto(file)
        say("Foto quitada")
    }

    fun backupJson(): String = Fichero(motors.toList(), regs.toList()).toJson().toString(2)

    /** Suma un respaldo a los datos actuales. Si un registro ya existe, queda el más reciente. */
    fun importBackup(text: String): String {
        val f = try { ficheroFromJson(text) } catch (e: Exception) { return "El archivo no es un respaldo válido." }
        if (f.motores.isEmpty() && f.registros.isEmpty()) return "El respaldo no tiene datos."
        var nm = 0; var nr = 0
        for (m in f.motores) {
            val i = motors.indexOfFirst { it.id == m.id }
            if (i < 0) { motors.add(m.copy(fotos = m.fotos.filter { repo.fotoFile(it.file).exists() })); nm++ }
            else if ((m.act ?: m.creado) > (motors[i].act ?: motors[i].creado)) {
                // Conserva las fotos locales: el respaldo no las trae.
                motors[i] = m.copy(fotos = motors[i].fotos); nm++
            }
        }
        for (r in f.registros) {
            val i = regs.indexOfFirst { it.id == r.id }
            if (i < 0) { regs.add(r); nr++ }
            else if ((r.act ?: r.creado) > (regs[i].act ?: regs[i].creado)) { regs[i] = r; nr++ }
        }
        persist()
        return "Respaldo importado: $nm motores y $nr registros nuevos o actualizados."
    }
}
