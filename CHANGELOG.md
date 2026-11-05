# Registro de cambios

Cada versión, lo nuevo primero. El apartado de cada versión es el texto que se ve en su página de
*Releases* y en el aviso de actualización de la app.

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
