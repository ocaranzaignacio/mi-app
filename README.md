# Fichero de Motores

App Android nativa (Kotlin + Jetpack Compose) para registrar motores eléctricos: datos de placa, rodamientos, brida, medidas estructurales, datos de bobinado, pruebas de laboratorio con índices calculados (DAR, IP, desequilibrios, zonas ISO 10816-1) y mantenimiento.

- Los datos se guardan solo en el teléfono (archivo JSON privado de la app) y las fotos en el almacenamiento interno de la app.
- Desde **Ajustes** se exporta e importa un respaldo en JSON.
- Las planillas se exportan en CSV (separador `;`) y se comparten desde el menú de Android.

## Compilar

Cada push ejecuta el workflow **Compilar APK** (`.github/workflows/build.yml`), que usa Java 17 y Gradle 8.9 y publica el APK de debug como artifact **FicheroMotores-APK-debug**. También se puede ejecutar a mano desde la pestaña Actions con *Run workflow*.

Versiones: Android Gradle Plugin 8.5.2, Kotlin 2.0.21, Compose BOM 2024.09.03, compileSdk 34, minSdk 26 (Android 8.0).
