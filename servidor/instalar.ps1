# Fichero de Motores: instala el servidor PocketBase en esta PC.
$ErrorActionPreference = 'Stop'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $dir
$exe = Join-Path $dir 'pocketbase.exe'
$tarea = 'FicheroMotoresServidor'

function Paso($t) { Write-Host ''; Write-Host "== $t" -ForegroundColor Cyan }

try {
  if (-not (Test-Path $exe)) { throw "No encuentro pocketbase.exe en $dir. Extraé todo el ZIP antes de ejecutar." }
  if ($dir -match 'OneDrive|\\Downloads|\\Descargas|\\Desktop|\\Escritorio') {
    Write-Host "Atención: la carpeta está en $dir." -ForegroundColor Yellow
    Write-Host "Conviene moverla a C:\FicheroServidor para que funcione siempre." -ForegroundColor Yellow
    $r = Read-Host '¿Seguir igual? (s/n)'
    if ($r -notmatch '^[sS]') { exit 1 }
  }

  Write-Host ''
  Write-Host 'FICHERO DE MOTORES - Instalación del servidor' -ForegroundColor Green

  # Si ya estaba instalado, se detiene para poder actualizar.
  if (Get-ScheduledTask -TaskName $tarea -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $tarea -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
  }

  Paso 'Preparando la base de datos'
  & $exe migrate up
  if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar la base de datos.' }

  Paso 'Cuenta de administrador (para entrar al panel y crear técnicos)'
  $email = Read-Host 'Email del administrador'
  while ($true) {
    $s1 = Read-Host 'Contraseña (mínimo 10 caracteres)' -AsSecureString
    $s2 = Read-Host 'Repetí la contraseña' -AsSecureString
    $p1 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s1))
    $p2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s2))
    if ($p1 -ne $p2) { Write-Host 'Las contraseñas no coinciden.' -ForegroundColor Yellow; continue }
    if ($p1.Length -lt 10) { Write-Host 'Tiene que tener al menos 10 caracteres.' -ForegroundColor Yellow; continue }
    break
  }
  & $exe superuser upsert $email $p1
  if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el administrador. Revisá que el email sea válido.' }

  Paso 'Permitiendo conexiones en el puerto 8090 (firewall)'
  Get-NetFirewallRule -DisplayName 'Fichero de Motores (8090)' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
  New-NetFirewallRule -DisplayName 'Fichero de Motores (8090)' -Direction Inbound -Protocol TCP -LocalPort 8090 -Action Allow -Profile Any | Out-Null

  Paso 'Configurando el arranque automático al encender la PC'
  $accion = New-ScheduledTaskAction -Execute $exe -Argument 'serve --http=0.0.0.0:8090' -WorkingDirectory $dir
  $disparador = New-ScheduledTaskTrigger -AtStartup
  $ajustes = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
  $cuenta = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
  Register-ScheduledTask -TaskName $tarea -Action $accion -Trigger $disparador -Settings $ajustes -Principal $cuenta -Force | Out-Null
  Start-ScheduledTask -TaskName $tarea

  Paso 'Evitando que la PC se suspenda cuando está enchufada'
  powercfg /change standby-timeout-ac 0 | Out-Null
  powercfg /change hibernate-timeout-ac 0 | Out-Null

  Paso 'Comprobando que el servidor responde'
  $ok = $false
  for ($i = 0; $i -lt 20; $i++) {
    try { Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8090/api/health' -TimeoutSec 2 | Out-Null; $ok = $true; break } catch { Start-Sleep -Seconds 1 }
  }
  if (-not $ok) { throw 'El servidor no arrancó. Reiniciá la PC y volvé a ejecutar 1-INSTALAR.bat.' }

  Write-Host ''
  Write-Host 'LISTO: el servidor está funcionando y arranca solo con la PC.' -ForegroundColor Green
  Write-Host ''
  Write-Host 'Direcciones para poner en la app (Ajustes > Servidor):'
  $ips = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' }
  foreach ($ip in $ips) {
    $tipo = if ($ip.IPAddress -like '100.*') { '(Tailscale: funciona desde cualquier lugar)' } else { "(red local: $($ip.InterfaceAlias))" }
    Write-Host ("   http://{0}:8090   {1}" -f $ip.IPAddress, $tipo) -ForegroundColor White
  }
  Write-Host ''
  Write-Host 'Se abre el panel en el navegador: entrá con el email y la contraseña de administrador.'
  Write-Host 'Ahí creá un usuario por técnico (colección "users").'
  Start-Process 'http://127.0.0.1:8090/_/'
} catch {
  Write-Host ''
  Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
