@echo off
rem Fichero de Motores - detiene el servidor y quita el arranque automatico. Los datos NO se borran.
net session >nul 2>&1
if %errorlevel% neq 0 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -Command "Stop-ScheduledTask -TaskName FicheroMotoresServidor -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName FicheroMotoresServidor -Confirm:$false -ErrorAction SilentlyContinue; Get-NetFirewallRule -DisplayName 'Fichero de Motores (8090)' -ErrorAction SilentlyContinue | Remove-NetFirewallRule"
echo Servidor detenido. Los datos siguen en la carpeta pb_data.
pause
