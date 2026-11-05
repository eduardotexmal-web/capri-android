# Capri para Android

Port de [capri-desktop](https://github.com/mochiiwuff/capri-desktop), de mochiiwuff, a Android.
Capri, el constructo de *Project Capricorn* de Cyberpunk: Edgerunners, vive encima de tus apps, se sube a tu
música y a tus notificaciones, charla contigo y te cuenta lo que hace tu IA de programación: **Claude Code**,
**Codex** o **Antigravity**.

Es **la misma Capri**: su motor visual (WebGL, animación cuadro a cuadro, efectos, frases en 5 idiomas, chat,
vínculo, recordatorios) se copia **sin cambios** del original y corre en una capa flotante. Solo se reescribe
lo que en Windows hacían Electron, Node y PowerShell.

## Instalar

1. Descarga el APK de la [última versión](https://github.com/emmanuelquintana/capri-android/releases/latest).
2. Ábrelo en el teléfono y permite instalar apps de esa fuente.
3. Abre Capri, dale los permisos que pide (mostrarse sobre otras apps, su notificación y, si quieres, ver tus
   notificaciones) y pulsa **Iniciar Capri**.

Después se actualiza sola: la app busca versiones nuevas en *Releases*, te avisa con una notificación y la
instala encima con un toque, sin desinstalar. Qué cambia en cada versión está en el
[registro de cambios](CHANGELOG.md).

## Qué hace en el teléfono

- **Su IA, sin terminal:** gratis y sin cuenta con un toque, con una clave de Groq o Gemini (también gratis),
  o con el Ollama de tu PC por Wi-Fi. Todo desde su panel o desde la app (*Elegir su IA*).
- **Tu música:** cuando algo suena, aparece su tarjeta «sonando ahora» con los botones ⏮ ⏯ ⏭ y Capri se
  sienta encima, como en las ventanas del PC.
- **Tus notificaciones:** cuando llega algo, sale una tarjeta con la app y el título (nunca el texto) y ella
  brinca encima. Tocarla abre la notificación.
- **Guiños en el panel de notificaciones:** su notificación va en sus colores, con su cara y lo último que dijo.
- **Gira contigo:** si giras la pantalla, da una vuelta y se recoloca.
- **Todo lo del original:** intro, teletransportes, cut-ins, caras, flotar, asomarse, caer, dormir, lanzarla y
  atraparla, informe de misión, personalización, vínculo, celos, chat, recordatorios, sonidos, idiomas.

Gestos: mantén pulsado sobre ella para su menú (el clic derecho del PC), arrástrala para lanzarla y acaríciala
con dos dedos.

### Conectarla a Claude Code, Codex o Antigravity

Es lo único que necesita una terminal, porque esas IAs corren en una. En el teléfono, con
[Termux](https://termux.dev): pulsa **Conectarme** en su bienvenida (o *Instalar hooks* en su menú), el comando
queda copiado y se abre Termux; pégalo y pulsa Enter:

```sh
curl -fsS http://127.0.0.1:47823/install.sh | sh
```

Instala los hooks del original en `~/.capri` y los añade a `~/.claude/settings.json` (con copia de seguridad).
Para Codex o Antigravity: `| sh -s -- codex` o `| sh -s -- agy`. Para quitarlos: `| sh -s -- --uninstall`.

## Cómo funciona

| Escritorio (Electron) | Android |
| --- | --- |
| Ventana transparente a pantalla completa, siempre encima | Capa flotante (`TYPE_APPLICATION_OVERLAY`) a pantalla completa; los toques pasan a tus apps |
| `preload.js` + `main.js` (IPC) | `assets/android/host.js` + `HostBridge.kt` |
| Servidor de hooks en `127.0.0.1:47823` | El mismo servidor, mismo puerto y mismo formato (`HookServer.kt`) |
| Icono de la bandeja y su menú | Notificación fija y el mismo menú dibujado como uno de sus paneles |
| Las ventanas donde se sienta | Sus tarjetas de música y de avisos (`notes.js`, `CapriNotifications.kt`) |
| `llm.js`, `ollama.js`, `labels.js`, `launch.js` | Los mismos archivos, sin tocar (`tools/sync-upstream.mjs`) |

Android no deja leer las ventanas de otras apps ni cerrarlas, así que eso se adapta o queda fuera.

## Desarrollo

```sh
# copiar el original (con `npm ci --omit=dev` hecho en él) a los assets
node tools/sync-upstream.mjs ../capri-desktop
# ver la página en Chromium a tamaño de teléfono, con un Kotlin simulado
node tools/preview/run.mjs
```

Capri Android tiene su propia numeración, `MAYOR.MENOR.PARCHE` (empezó en 1.0.0): *PARCHE* para arreglos,
*MENOR* para funciones nuevas y *MAYOR* para cambios grandes. `versionCode` es MAYOR×10000 + MENOR×100 + PARCHE.

Cada push a `main` compila el APK en GitHub Actions y lo publica como la versión de `versionName`
(`app/build.gradle.kts`). Para publicar una versión: sube `versionName` y `versionCode`, y añade su
apartado al principio de [CHANGELOG.md](CHANGELOG.md); ese texto es lo que se ve en la versión y en el aviso
de la app. Todas se firman con la misma llave (secreto `CAPRI_SIGNING_PASSPHRASE` y `app/signing/`): no borres
ninguno de los dos o las actualizaciones dejarán de instalarse encima.

Cuando sale una versión nueva de capri-desktop, una tarea diaria abre un *issue* en este repo para portarla.

## Créditos y licencia

Required Notice: Copyright (c) 2026 mochiiwuff (https://github.com/mochiiwuff/capri-desktop)

El código está bajo la [PolyForm Noncommercial License 1.0.0](LICENSE), como el original: puedes usarlo,
cambiarlo y compartirlo **gratis**. No se puede vender ni cobrar por él de ninguna forma.

Capri / Project Capricorn, Cyberpunk y Cyberpunk: Edgerunners pertenecen a CD PROJEKT RED, Studio Trigger y
Netflix. Es un proyecto de fans, sin afiliación. Las imágenes de `assets/capri/` vienen del original y **no**
están cubiertas por la licencia del código. Fuentes: Chakra Petch y JetBrains Mono (SIL Open Font License).
