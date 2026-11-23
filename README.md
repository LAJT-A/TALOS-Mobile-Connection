<div align="center">

# TALOS Mobile Connection (TMC)

**La app de Android para usar [TALOS](https://github.com/LAJT-A/TALOS) desde tu celular.**

Se conecta a la PC por Bluetooth: no necesita Wi-Fi ni datos.

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Bluetooth](https://img.shields.io/badge/conexión-Bluetooth-0082FC?logo=bluetooth&logoColor=white)

</div>

---

## Qué hace

- **Hablarle a TALOS**: tocas *HABLAR* (o escribes), la frase viaja a la PC y la respuesta suena en el celular con la misma voz de TALOS.
- **Música del teléfono**: TALOS pausa, reanuda, cambia de canción, ajusta el volumen y busca canciones en Spotify del celular.
- **Llamadas**: TALOS te avisa quién llama y puede contestar o colgar.
- **WhatsApp**: TALOS se entera de los mensajes que llegan y te los puede leer.
- **Apps de la PC**: un menú con todas las apps de tu PC, con sus logos y buscador; tocas una y se abre en la PC.

## Instalar

1. Descarga `Talos.apk` desde [Releases](../../releases/latest) en el celular e instálalo.
2. Empareja el celular con la PC por Bluetooth.
3. En Windows, crea un puerto COM **entrante**: *Configuración → Bluetooth y dispositivos → Más opciones de Bluetooth → Puertos COM → Agregar → Entrante*.
4. Abre TMC, acepta los permisos y toca **Elegir PC**.
5. Para canciones y WhatsApp, toca el aviso amarillo y activa el *acceso a notificaciones* para TMC.

Cada cambio en `main` se compila solo con GitHub Actions y se publica en Releases.

## Protocolo

Una línea de texto por mensaje, sobre el puerto serie de Bluetooth (SPP).

| Sentido | Línea | Significado |
|---|---|---|
| Teléfono → PC | `texto` | Frase para Talos |
| Teléfono → PC | `#CALL nombre` / `#CALLSTART` / `#CALLEND` | Llamada entrante, contestada, terminada |
| PC → Teléfono | `texto` | Respuesta de Talos (se lee en voz alta) |
| PC → Teléfono | `#AUDIO base64` + `#TEXT texto` | La voz de Talos (MP3) y su texto para la pantalla |
| PC → Teléfono | `#PLAY` `#PAUSE` `#NEXT` `#PREV` `#VOLUP` `#VOLDOWN` `#VOL 0-100` | Música |
| PC → Teléfono | `#PLAYSEARCH texto` | Busca y pone una canción en el teléfono |
| PC → Teléfono | `#ANSWER` `#HANGUP` | Llamadas |
| PC → Teléfono | `#APPS a|b|c` | Lista de apps de la PC para el menú |
| Teléfono → PC | `#APPS` / `#OPEN nombre` | Pide la lista / abre una app en la PC |
| Teléfono → PC | `#ICONS clave,clave` | Pide los logos que le faltan |
| PC → Teléfono | `#ICON clave base64` | Logo (PNG) de una app |
| PC → Teléfono | `#CONTACT nombre` | Pide el número de un contacto |
| Teléfono → PC | `#NUMBER pedido|nombre|número` | El contacto encontrado (vacío si no hay) |
| Teléfono → PC | `#PLAYING título|artista` | Lo que empezó a sonar |
| Teléfono → PC | `#WA nombre|mensaje` | Llegó un WhatsApp (chats, no grupos) |
| PC → Teléfono | `#WAREPLY nombre|mensaje` | Responde ese chat desde su notificación |
