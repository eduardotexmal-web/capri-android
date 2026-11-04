**Español** · [English](#english)

# Capri para Android

Port no oficial de [capri-desktop](https://github.com/mochiiwuff/capri-desktop) (de mochiiwuff) a Android.
Capri, el constructo de *Project Capricorn* de Cyberpunk: Edgerunners, vive encima de tus apps y te cuenta lo
que hace tu IA de programación: **Claude Code**, **Codex** o **Antigravity**.

La idea es que sea **la misma Capri**: su motor visual (WebGL, animación cuadro a cuadro, efectos, frases en
5 idiomas, chat, vínculo, recordatorios) se copia **sin cambios** del original y corre en una capa flotante.
Solo se reescribe lo que en Windows hacían Electron, Node y PowerShell.

## Cómo funciona

| Escritorio (Electron) | Android |
| --- | --- |
| Ventana transparente a pantalla completa, siempre encima, el ratón pasa a través | Capa flotante (`TYPE_APPLICATION_OVERLAY`) a pantalla completa, los toques pasan a tus apps |
| `preload.js` + `main.js` (IPC) | `assets/android/host.js` + `HostBridge.kt` |
| Servidor de hooks en `127.0.0.1:47823` | El mismo servidor, mismo puerto y mismo formato (`HookServer.kt`) |
| Icono de la bandeja y su menú | Notificación fija (Menú, Guardarla, Salir) y el mismo menú dibujado como uno de sus paneles |
| Clic derecho sobre ella | Mantener pulsado sobre ella |
| Ctrl+Alt+C | Botón de la notificación y mosaico de Ajustes rápidos |
| Pasar el ratón por su melena (acariciarla) | Dos dedos sobre ella |
| `llm.js`, `ollama.js`, `labels.js`, `launch.js` | Los mismos archivos, sin tocar, cargados en la página (`tools/sync-upstream.mjs`) |

## Instalar

1. Descarga el APK de la última compilación (pestaña **Actions** → *Android APK* → artefacto `Capri-Android`)
   o de **Releases**.
2. Ábrelo en el teléfono y permite instalar apps de esa fuente.
3. Abre Capri, dale permiso para mostrarse sobre otras apps y para su notificación, y pulsa **Iniciar Capri**.

### Conectarla a Claude Code

Claude Code corre en [Termux](https://termux.dev) en el propio teléfono. Pulsa **Conectarme** en su
bienvenida (o *Instalar hooks* en su menú): el comando queda copiado y se abre Termux. Pégalo y pulsa Enter.
Es esto:

```sh
curl -fsS http://127.0.0.1:47823/install.sh | sh
```

Instala sus hooks (los del original, sin cambios) en `~/.capri` y los añade a `~/.claude/settings.json`
con el instalador del original, que guarda una copia de seguridad. Cierra Claude Code y vuelve a abrirlo.
Para Codex o Antigravity: `| sh -s -- codex` o `| sh -s -- agy`. Para quitarlos: `| sh -s -- --uninstall`.

### Su chat

Mismos proveedores que el original: Pollinations (sin cuenta), Groq, Gemini, OpenRouter, OpenAI, Mistral,
DeepSeek, cualquier servidor compatible con OpenAI, y Ollama (en Termux en el teléfono, o en tu PC: pon su
dirección, por ejemplo `192.168.1.20:11434`). Las claves se guardan cifradas con el Keystore de Android.

## Qué es igual y qué cambia

Igual: intro, ticker, teletransportes, victoria, error, satélite, cut-ins, caras, flotar, asomarse, caer,
dormir hasta la máscara, lanzarla y atraparla, informe de misión, personalización, vínculo, celos, chat,
recordatorios, sonidos, idiomas, demo.

Adaptado o pendiente (ver el plan): Android no deja leer las ventanas de otras apps, así que no puede
sentarse en ellas ni poseerlas como en Windows; tampoco cerrar otras apps ni apagar el teléfono. Mirar tu
pantalla, buscar archivos, la app en primer plano (celos, juegos) y sentir el teléfono (batería, red, USB)
llegan en las siguientes fases.

## Desarrollo

```sh
# copiar el original (con `npm ci --omit=dev` hecho en él) a los assets
node tools/sync-upstream.mjs ../capri-desktop
# ver la página en Chromium a tamaño de teléfono, con un Kotlin simulado
node tools/preview/run.mjs
# el APK (necesita el SDK de Android)
./gradlew assembleRelease
```

El original se copia tal cual. Los únicos cambios están en `tools/sync-upstream.mjs` y se comprueban en
cada sincronización: dos etiquetas en `index.html` y un arreglo de una línea en `fx.js` (la intro ahora
termina aunque se salte sus últimos cuadros en un teléfono lento; antes Capri no aparecía nunca).

## Créditos y licencia

Required Notice: Copyright (c) 2026 mochiiwuff (https://github.com/mochiiwuff/capri-desktop)

El código está bajo la [PolyForm Noncommercial License 1.0.0](LICENSE), como el original: puedes usarlo,
cambiarlo y compartirlo **gratis**. No se puede vender ni cobrar por él de ninguna forma.

Capri / Project Capricorn, Cyberpunk y Cyberpunk: Edgerunners pertenecen a CD PROJEKT RED, Studio Trigger y
Netflix. Es un proyecto de fans, sin afiliación. Las imágenes de `assets/capri/` vienen del original y **no**
están cubiertas por la licencia del código. Fuentes: Chakra Petch y JetBrains Mono (SIL Open Font License).

---

<a id="english"></a>

# Capri for Android (English)

An unofficial Android port of [capri-desktop](https://github.com/mochiiwuff/capri-desktop) by mochiiwuff. Her
renderer (WebGL sprite, frame-by-frame acting, effects, lines in 5 languages, chat, bond, reminders) is copied
unchanged into a transparent overlay; only the Electron/Node/PowerShell layer is rewritten (Kotlin + a
`window.host` shim). The hook server keeps port 47823 and the same JSON, so the original hooks work unchanged
from Termux: `curl -fsS http://127.0.0.1:47823/install.sh | sh`.

Long-press her for her menu, two fingers on her mane to pet her, the notification or the Quick Settings tile
for Ctrl+Alt+C. Builds come from GitHub Actions (artifact `Capri-Android`).

Required Notice: Copyright (c) 2026 mochiiwuff (https://github.com/mochiiwuff/capri-desktop). Licensed under
PolyForm Noncommercial 1.0.0: free, never for sale. Character art belongs to its rights holders and is not
covered by the code's license.
