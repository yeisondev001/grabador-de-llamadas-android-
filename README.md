# Grabadora de Llamadas Android

App personal (Flutter + Kotlin nativo) que graba **llamadas telefónicas normales (GSM)** y **llamadas de WhatsApp** en Android, con aviso previo ("¿Grabar?" / "No") antes de empezar.

> Proyecto de uso personal. Grabar llamadas sin consentimiento es ilegal en muchos países; úsala solo en tu propio teléfono y con conocimiento de las partes.

## Características

- 📞 **Llamadas normales:** detecta llamada entrante/saliente y muestra aviso con botones **Grabar / No**
- 💬 **Llamadas de WhatsApp:** detecta la llamada por la notificación y muestra el mismo aviso
- 🔊 **Auto-altavoz:** al grabar WhatsApp enciende el altavoz para captar ambas voces
- 🎙️ **Doble vía de grabación:** intenta `VOICE_CALL` (ambas vías nativas); si el sistema lo bloquea, hace fallback automático a micrófono (detección de silencio ~2.5 s)
- 📻 Lista de grabaciones con reproductor y borrado
- ⚙️ Ajustes: modo pregunta o automático, activar/desactivar GSM o WhatsApp, altavoz automático, forzar micrófono
- ♻️ Servicio en primer plano que sobrevive reinicios (BootReceiver)

## Cómo funciona

```
Flutter (UI)  ⇄  MethodChannel  ⇄  CallRecorderService (Kotlin, foreground)
                                      ├─ TelephonyCallback → llamadas GSM
                                      ├─ WsNotificationListener → llamadas WhatsApp
                                      └─ AudioRecord → MediaCodec (AAC) → MediaMuxer (.m4a)
```

- **Llamada GSM:** `TelephonyCallback` (API 31+) / `PhoneStateListener` (fallback)
- **WhatsApp:** `NotificationListenerService` filtra notificaciones `CATEGORY_CALL` de `com.whatsapp`
- **Salida:** AAC-LC 128 kbps, 48 kHz mono en `Android/media/com.papa.grabador_llamadas/Grabaciones/`
- El plan completo de desarrollo está en [PLAN.md](PLAN.md)

## Limitaciones (por diseño de Android)

- En dispositivos modernos `VOICE_CALL` suele estar bloqueado para apps de terceros (Android 10+); en ese caso la app graba por micrófono y **el altavoz debe estar encendido** para oír a la otra persona
- WhatsApp no ofrece API de grabación; la única vía sin root es micrófono + altavoz
- La calidad depende del volumen del altavoz

## Compilar

```bash
flutter pub get
dart run flutter_launcher_icons   # opcional, icono incluido
flutter build apk --release
```

APK de salida: `build/app/outputs/flutter-apk/app-release.apk`

## Permisos que usa

| Permiso | Para qué |
|---|---|
| `RECORD_AUDIO` | Grabar micrófono |
| `READ_PHONE_STATE` | Detectar llamadas GSM |
| Acceso a notificaciones | Detectar llamadas de WhatsApp |
| `POST_NOTIFICATIONS` | Notificación del servicio |
| `MODIFY_AUDIO_SETTINGS` | Altavoz automático |
| Batería sin restricciones | Evitar que HiOS/Android mate el servicio |

## Stack

Flutter 3.44 · Kotlin · minSdk 26 / targetSdk 35 · `audioplayers` · `shared_preferences` · `flutter_launcher_icons`

Probado en **Infinix Smart 10** (Android 15 Go).
