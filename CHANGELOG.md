# Registro de cambios

Cada versión, lo nuevo primero. El apartado de cada versión es el texto que se ve en su página de
*Releases* y en el aviso de actualización de la app.

## 1.4.1

- **Ya no hay zonas muertas junto a Capri:** después de teletransportarse, tocar cerca de ella (no sobre ella) no
  respondía, ni Capri ni la app de abajo, hasta que la movías. La zona táctil ahora sigue la silueta de su cuerpo
  en vez de un cuadro grande alrededor, y mientras se teletransporta no bloquea nada.
- **Actualizar desde la app en más teléfonos:** en algunos (como Samsung con Android 16) la instalación fallaba con
  «Self update is blocked». Ahora la versión nueva se abre en la pantalla de instalación de Android, igual que si la
  abrieras desde el navegador. Si aun así falla, se abre su página para descargarla.

## 1.4.0

- **Tarjetas con animación:** los avisos de tus notificaciones entran deslizándose con un glitch y se van igual, ya
  no aparecen y desaparecen de golpe. Una barrita roja abajo te dice cuánto le queda en pantalla.
- **La barra de música ya no está siempre:** aparece cuando empieza una canción, cambia, o la pausas o reanudas, y
  a los 15 segundos se va sola. Si la tocas, se queda un rato más. En pausa se va a los 12 segundos.
- **Nueva pestaña «Comandos»:** todo lo que le puedes pedir a Capri, en voz alta o escrito en su chat, con ejemplos.
- **Actualiza desde la app otra vez:** *Actualizar* descarga e instala la versión nueva ahí mismo, sin pasar por el
  navegador (Android te pide confirmar). La primera vez te pide dejar que Capri instale apps.
- **Aviso del sistema cuando hay versión nueva:** la app revisa cada 3 horas, aunque Capri esté apagada, y te manda
  una notificación. Al tocarla, abre la app y empieza la actualización.
- **Novedades plegadas:** cada versión se muestra cerrada, con cuántos cambios trae; tócala para leerla.
- **Háblale más fluido:** cuando solo dices «Capri», te contesta en su panel sin hablar, para no taparte lo que
  dices después, y espera un poco más a que termines la frase.

## 1.3.1

- **Arreglo de «Háblale»:** Capri no reaccionaba al decirle «Capri…». Ahora usa el reconocedor de voz de Google
  cuando está en el teléfono (algunos teléfonos traen otro que no escucha desde segundo plano), ya no exige el modo
  sin conexión, y entiende su nombre aunque el teléfono lo escriba distinto («Kapri», «Capry», «Caprí»…).
- **Te contesta en voz alta aunque el teléfono esté en silencio**, con el volumen multimedia (el de la música). Si lo
  tienes en cero, solo te responde en su panel.
- **Ves qué está oyendo:** en *Ajustes → Háblale* aparece en vivo si está escuchando, qué fue lo último que oyó, o
  por qué no puede (micrófono ocupado, sin permiso, error del reconocedor).
- Ya no se puede quedar trabada después de un comando que tarda en responder.

## 1.3.0

- **Háblale:** di «Capri» y lo que quieras, como a un asistente. «Capri, ¿qué hora es?», «Capri, abre Spotify»,
  «Capri, siguiente canción», «Capri, sube el volumen», «Capri, prende la linterna», «Capri, pon un temporizador de
  10 minutos», «Capri, escóndete» y «Capri, ven». Lo demás se lo pregunta a su IA y te contesta en voz alta.
  Viene apagado: lo activas en *Ajustes → Háblale*. Solo escucha con la pantalla encendida, usa el reconocedor de
  voz de tu propio teléfono, no graba audio y solo se queda con lo que dices después de su nombre.
- **Permiso de micrófono opcional:** solo se pide al activar *Háblale*. En *Permisos* te explicamos para qué es y
  que algunas apps de banco o antivirus desconfían del micrófono; si la tuya se queja, lo desactivas.
- **Hecha para el teléfono:** sus textos ya no hablan de PC, escritorio ni ratón, y su menú ya no tiene opciones que
  solo servían en Windows (mirar tu pantalla, ponerse celosa de otras IA en el navegador). Claude Code con Termux y
  Ollama en tu PC por Wi-Fi siguen igual.
- **Nueva cara para la app:** los colores de Capri, el rojo de su melena y el verde de su máscara, junto con el
  amarillo cyberpunk de siempre.
- **Ya no choca con la barra de notificaciones:** las pantallas empiezan debajo de la barra de estado y la barra de
  pestañas queda encima de los botones de Android.
- **Ajustes más limpios:** el registro técnico ya no aparece; es solo para desarrollo.

## 1.2.0

- **Más ligera:** Capri ocupa unos 50 MB menos de RAM. Las imágenes de su intro ya no se quedan en memoria
  para siempre: se cargan cuando la intro se reproduce y se sueltan al terminar. La capa de efectos también suelta
  su lienzo de pantalla completa cuando no hay ningún efecto.
- **Menos batería en reposo:** entre fotograma y fotograma su animación duerme en vez de despertar con cada
  refresco de pantalla. El audio se cierra a los pocos segundos de su último sonido, y revisa el calor del
  teléfono y dónde se la puede tocar con menos frecuencia.
- **Cuando a Android le falta memoria**, o al apagar la pantalla, suelta todo lo que puede volver a cargar, así
  es menos probable que el sistema la cierre.
- Todo se ve y funciona igual que antes.

## 1.1.1

- **Más segura:** Capri ya no pide el permiso de *instalar apps*. Al tocar *Actualizar*, la versión nueva se
  descarga en tu navegador; ábrela y se instala encima, sin perder nada. Es justo el tipo de permiso que hace
  que los antivirus desconfíen.
- **Código legible:** el APK ya no ofusca los nombres de su código, así cualquiera (y cualquier antivirus) puede
  ver qué hace. Capri no lee nada de tus apps de banco ni manda tus datos a ningún lado.
- **Apps de banco:** algunas (como Nu) bloquean el acceso si una app puede ver tus notificaciones. Ese permiso
  sigue siendo opcional y apagado hasta que tú lo das. En *Permisos* te explicamos qué hacer si tu banco se queja.

## 1.1.0

- **Tarjeta de música a tu gusto:** arrástrala con el dedo a donde quieras (desde sus puntitos o cualquier parte
  que no sea un botón) y se queda ahí, incluso si reinicias. Con el botón **–** se pliega a una píldora pequeña
  con ♪ y play/pausa; toca ♪ para abrirla otra vez. Si Capri está sentada encima, se va con ella.

## 1.0.0

- **Numeración propia:** Capri Android estrena sus propias versiones, empezando por la 1.0.0. Ya no sigue la
  numeración de capri-desktop. A partir de ahora: el último número sube con los arreglos, el del medio con las
  funciones nuevas y el primero con los cambios grandes.
- Trae todo lo de la versión anterior: bienvenida animada, barra de píldora y el arreglo del teclado.

## 0.5.1-android.9

- **Bienvenida animada:** la primera vez que abres la app, Capri arranca con su intro, te pregunta cómo
  quieres que te llame, te ayuda a conectar su IA y te explica para qué sirve cada permiso.
- **Barra de píldora abajo:** tres pestañas. *Novedades* (estado, Iniciar, actualizaciones y lo nuevo de cada
  versión), *Permisos* (cada uno con su porqué) y *Ajustes* (cómo te llama, su IA y el registro).
- **Cambia cómo te llama** cuando quieras desde *Ajustes*, aunque esté en pantalla.
- **Arreglo del teclado:** Capri se ponía encima del teclado y se quedaba con tus toques, así que no podías
  escribir. Ahora, en cualquier app, se aparta por encima del teclado y las teclas son tuyas. En su chat, las
  letras llegan bien a su caja y, si tocas fuera, el chat se cierra.

## 0.5.1-android.8

- **Cambios claros en cada versión:** el aviso de actualización y la app muestran qué trae la versión nueva.
- **Capri te avisa de las actualizaciones:** te lo dice ella en pantalla y te llega una notificación con las
  novedades. Ahora lo comprueba cada pocas horas, no una vez al día.
- **Todo en español:** el repositorio, las versiones y este registro.

## 0.5.1-android.7

- **Gira contigo:** al girar la pantalla, Capri da una vuelta y se recoloca; si estaba sentada o asomada, se
  teletransporta al mismo sitio de la pantalla girada.

## 0.5.1-android.6

- **Arreglo:** la tarjeta de música se salía por abajo en algunos teléfonos y sus botones no se podían usar.
  Ahora se coloca según lo que se ve de la pantalla, y Capri se sienta sin tapar los botones.
- **Guiños en el panel de notificaciones:** su notificación va en sus colores, con su cara y lo último que dijo.

## 0.5.1-android.5

- **Actualizaciones desde la app:** la placa *Actualizaciones* busca versiones nuevas y las instala encima con
  un toque. Primera versión con la firma fija: hubo que desinstalar una última vez.

## 0.5.1-android.4

- **Tu música y tus notificaciones:** con el permiso opcional, Capri se sube a una tarjeta «sonando ahora» con
  sus controles y a una tarjeta de cada aviso que llega. Se puede apagar en su menú, en *Notificaciones*.

## 0.5.1-android.3

- **Su IA sin terminal:** gratis y sin cuenta con un toque, con una clave de Groq o Gemini, o con el Ollama de
  tu PC por Wi-Fi. Botón *Elegir su IA* en la app.

## 0.5.1-android.2

- **Arreglo:** Capri no aparecía al iniciarla.
- La pantalla de la app, con los colores y las fuentes de Capri, y un *Registro* para ver qué falla.

## 0.5.1-android.1

- Primera versión: Capri del escritorio en Android, con su servidor de hooks para Claude Code.
