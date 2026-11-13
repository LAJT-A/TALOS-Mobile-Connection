# Talos-app

App de Android para hablarle a Talos (el asistente que corre en la PC) por Bluetooth.

- **Hablar o escribir**: lo que digas se manda a la PC y la respuesta se lee en voz alta.
- **Música del teléfono**: la PC puede pausar, reanudar y cambiar de canción.
- **Llamadas**: la PC se entera de quién llama y puede contestar o colgar.

## Instalar

Cada cambio en `main` se compila solo. Descarga `Talos.apk` desde
[Releases](../../releases/latest) en el teléfono e instálalo.

## Protocolo

Una línea de texto por mensaje, sobre el puerto serie de Bluetooth (SPP).

| Sentido | Línea | Significado |
|---|---|---|
| Teléfono → PC | `texto` | Frase para Talos |
| Teléfono → PC | `#CALL nombre` / `#CALLSTART` / `#CALLEND` | Llamada entrante, contestada, terminada |
| PC → Teléfono | `texto` | Respuesta de Talos (se lee en voz alta) |
| PC → Teléfono | `#AUDIO base64` + `#TEXT texto` | La voz de Talos (MP3) y su texto para la pantalla |
| PC → Teléfono | `#PLAY` `#PAUSE` `#NEXT` `#PREV` `#VOLUP` `#VOLDOWN` `#VOL 0-100` | Música |
| PC → Teléfono | `#ANSWER` `#HANGUP` | Llamadas |
