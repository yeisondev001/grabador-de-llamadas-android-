# Grabador de Llamadas — Plan de desarrollo

**Objetivo:** APK personal (para el Infinix Smart 10 de mi papá, Android 15 Go) que grabe **ambas vías** de:
1. Llamadas telefónicas normales (GSM)
2. Llamadas de WhatsApp

Uso privado, no se publica en tiendas.

---

## 1. Stack

| Capa | Tecnología |
|---|---|
| UI / lógica | Flutter 3.44 (Dart 3.12), Material 3 |
| Reproducción de audio | `audioplayers` 6.x |
| Preferencias | `shared_preferences` |
| Grabación nativa | Kotlin + `AudioRecord` + `MediaCodec` (AAC) + `MediaMuxer` (.m4a) |
| Detección llamada normal | `TelephonyCallback` (API 31+) / `PhoneStateListener` (fallback) |
| Detección llamada WhatsApp | `NotificationListenerService` (paquete `com.whatsapp`) |
| Canal Dart ↔ Kotlin | MethodChannel + EventChannel |
| minSdk / targetSdk | 26 / 35 |
| Distribución | APK directo (debug/release con firma debug) |

---

## 2. Factibilidad real en el Infinix Smart 10

| Vía | Llamada normal | WhatsApp |
|---|---|---|
| `AudioSource.VOICE_CALL` (ambas vías nativas) | Probablemente bloqueado (Android 15). Se **intenta** al iniciar; si devuelve silencio → fallback automático | No aplica |
| `AudioSource.MIC` + altavoz en alto | ✅ Ambas voces mezcladas (con ruido ambiente) | ✅ Único método sin root |
| Grabadora nativa del dialer Infinix (HiOS) | Verificar en Ajustes del teléfono: los Infinix suelen incluir "Grabar llamadas" en la app Teléfono. Si existe, úsala para GSM y esta app queda para WhatsApp | No |

**Regla de oro:** para oír a la otra persona por MIC, el **altavoz debe estar activado**. La app puede activarlo automáticamente (`MODIFY_AUDIO_SETTINGS`).

---

## 3. Arquitectura

```
┌─────────────────── Flutter (Dart) ───────────────────┐
│  HomeScreen   RecordingsScreen   SettingsScreen      │
│        │ (MethodChannel + EventChannel)              │
├───────────── Capa nativa (Kotlin) ───────────────────┤
│  RecorderPlugin  ← canal único                       │
│  CallRecorderService (foreground, type=microphone)   │
│    └ AudioRecord → MediaCodec AAC → MediaMuxer       │
│  CallStateTracker  (llamada GSM empieza/termina)     │
│  WsNotificationListener (llamada WS empieza/termina) │
└──────────────────────────────────────────────────────┘
```

- **Un solo servicio** de grabación: quién detecta la llamada le pasa el "motivo" (GSM / WS) y el servicio arranca solo.
- EventChannel notifica a Flutter: `recordingStarted`, `recordingStopped`, `source` (VOICE_CALL|MIC), errores.

## 4. Estructura de archivos

```
lib/
  main.dart                     (entrada, tema, permisos al inicio)
  models/recording.dart
  services/recorder_channel.dart
  screens/home_screen.dart
  screens/recordings_screen.dart
  screens/settings_screen.dart
android/app/src/main/kotlin/com/papa/grabador_llamadas/
  MainActivity.kt               (registra canal)
  RecorderPlugin.kt             (métodos del canal)
  CallRecorderService.kt        (grabación)
  CallStateTracker.kt           (GSM)
  WsNotificationListener.kt     (WhatsApp)
android/app/src/main/AndroidManifest.xml (permisos + servicios)
```

## 5. Permisos (qué pedimos y cuándo)

| Permiso | Uso | Cómo se pide |
|---|---|---|
| `RECORD_AUDIO` | Grabar mic | Runtime |
| `READ_PHONE_STATE` | Detectar llamada GSM | Runtime |
| `POST_NOTIFICATIONS` | Notificación foreground (Android 13+) | Runtime |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Servicio en primer plano | Manifest |
| `MODIFY_AUDIO_SETTINGS` | Auto-altavoz | Manifest |
| Acceso a notificaciones | Detectar llamadas WhatsApp | Pantalla de ajustes del sistema |

## 6. Fases

- [x] **Fase 0 — Proyecto:** flutter create + dependencias
- [x] **Fase 1 — Núcleo nativo:** Manifest, CallRecorderService (VOICE_CALL→MIC con detección de silencio), notificación foreground
- [x] **Fase 2 — Detección:** CallStateTracker (GSM) + WsNotificationListener (WhatsApp) + auto altavoz
- [x] **Fase 3 — Puente Dart↔Kotlin:** RecorderPlugin (permisos, start/stop, lista, eventos)
- [x] **Fase 4 — UI Flutter:** estado en vivo, lista de grabaciones con reproductor, ajustes (toggles GSM/WS, auto-altavoz, borrar)
- [ ] **Fase 5 — Pruebas en el Smart 10:** instalar APK, verificar ambas vías, ajustar sensibilidad
- [ ] **Fase 6 — Pulido:** renombrar archivo con contacto/fecha, icono, excluir optimización de batería (HiOS mata servicios)

> Nota: el puente usa sondeo de estado cada 2 s (sin EventChannel) y los ajustes viven en SharedPreferences del lado Kotlin (fuente única de verdad). Los archivos se guardan en `Android/media/com.papa.grabador_llamadas/Grabaciones` (visible en el explorador de archivos). BootReceiver reinicia el servicio tras reiniciar el teléfono (abrir la app una vez para revalidar micrófono).

## 7. Formato de salida

- Carpeta: `Android/media/com.papa.grabador_llamadas/Grabaciones/` (visible en el explorador de archivos, sin permisos extra)
- Nombre: `LLAMADA_20260831_1530.m4a` / `WHATSAPP_20260831_1530.m4a`
- Códec: AAC-LC 128 kbps, 48 kHz mono

## 8. Riesgos y limitaciones conocidos

1. **HiOS (capa de Infinix) mata servicios en segundo plano** → pedir "no optimizar batería" para la app (Fase 6).
2. Si `VOICE_CALL` funciona pero suena vacío/solo una vía → la app hace fallback a MIC automático (test de silencio ~1 s).
3. WhatsApp puede mostrar notificaciones distintas entre versiones → el listener filtra por paquete + categoría `CALL`; si falla, hay botón manual de grabar.
4. Calidad MIC+altavoz depende del volumen del altavoz → en ajustes, la app sube volumen de llamada al activarse (opcional).
5. Legal: solo para el teléfono de mi papá, con conocimiento de las partes.

## 9. Cómo verificar cada fase

- Fase 1: botón manual "grabar prueba" → archivo .m4a reproducible
- Fase 2: llamada GSM real y llamada WS real → empieza/para sola, log visible en Home
- Fase 5: escuchar grabaciones verificando **ambas voces**
