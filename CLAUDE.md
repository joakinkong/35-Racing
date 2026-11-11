# 35 Racing

Proyecto final de Programación sobre Redes (grupo de 5). Juego de carreras 2D estilo F1 arcade, vista cenital, con la estética de PIT WALL (la página de F1 de Joaquín, donde se piensa sumar como minijuego). Multijugador por LAN: de 2 a 22 autos (Config.MAX_JUGADORES = 22): hasta 8 personas (Config.MAX_PERSONAS), cada una en su PC, y el resto bots. El host elige la capacidad de la sala al crearla. Uno crea la partida (host: servidor + jugador) y el resto se une por IP.

## Stack (obligatorio por las pautas de la materia)
- Java 17 + LibGDX (proyecto de gdx-liftoff), Gradle, Eclipse. Módulos: core (todo el juego) y lwjgl3 (lanzador de escritorio).
- Red: solo java.net (Socket, ServerSocket, DatagramSocket). Prohibido usar KryoNet, Netty u otras librerías de red. Nada de bases de datos.
- Sin Box2D: física arcade propia.
- No agregar dependencias sin preguntar.

## Arquitectura
- Paquetes bajo ar.edu.et35.racing:
  - pantallas: Screens de LibGDX (menú, unirse, lobby, carrera, resultados).
  - juego: modelo y simulación (Carrera, Auto, Circuito, EntradaAuto, y los bots: NivelBot, RecorridoBot, PilotoBot). Sin clases gráficas ni lectura de teclado: lo usa también el servidor.
  - vista: dibujo del modelo (mapa, autos, HUD).
  - red: servidor y cliente. Especificación en docs/PROTOCOLO.md.
  - util: Config (constantes), Paleta (colores de PIT WALL), Recursos (fuentes y estilos) y utilidades.
- Simulación a paso fijo (1/60 s), separada del render.
- Dos resoluciones: la interfaz está a 1280x720 (Config.ANCHO_INTERFAZ) y el mundo a 640x360 (Config.ANCHO_MUNDO). Las piezas comunes de las pantallas (titular, tarjeta, botones) están en PantallaBase.
- Fuentes: Space Grotesk e Inter, ya generadas como imagen + .fnt en assets/fuentes (tools/fuentes/generar_fuentes.py). Un tamaño nuevo requiere volver a generarlas.
- Todo lo que un jugador hace con su auto es una EntradaAuto (acelerar, frenar, giro). Es lo que viaja por red.
- Circuitos: archivos JSON vectoriales en assets/circuitos (línea central, ancho de asfalto y de pasto en cada punto, checkpoints, largada; formato en docs/FORMATO_CIRCUITO.md), convertidos a Circuito (datos puros: geometría para dibujar y una grilla fina de superficies para la colisión). Los genera tools/circuitos/generar_circuitos.py a partir de trazados SVG (hay una skill, circuitos, que explica cómo). Ya no se usa Tiled.
- Dibujo del circuito: VistaCircuito arma una sola vez círculos apilados en tres capas (muro, pasto, asfalto) con la misma forma que usa la colisión; el lanzador activa antialiasing (Config.MUESTRAS_ANTIALIASING). Los pianos (vista/Pianos) se calculan al cargar y son solo dibujo. En un cruce, juego/ZonaCruce da las muestras del puente y el nivel de cada auto: PantallaCarrera dibuja pista, autos de abajo, puente y resto de los autos, y Carrera no choca autos de niveles distintos. El muro del puente no es sólido. El entorno (calles y edificios en los callejeros; pasto, tribunas, ambulancias y muros en los permanentes) lo ubica vista/DisposicionEntorno al cargar y lo dibuja vista/VistaEntorno; es solo dibujo, y sus sprites están en assets/entorno, armados por tools/entorno/armar_atlas.py desde Modern Exteriors de LimeZu. Los autos son polígonos (vista/VistaAuto) con los colores de la escudería. El HUD de carrera es una torre de tiempos estilo F1 (vista/TorreHud) y un mapa del circuito en tiempo real (vista/MiniMapa), que solo lee la foto de la carrera.
- Red: servidor autoritativo embebido en el proceso del host, con hilo propio. El host también se conecta como cliente a 127.0.0.1, así hay un solo camino de código para los clientes.
  - TCP: lobby, selección, inicio y fin de carrera, desconexiones (lo que no se puede perder).
  - UDP: entradas cliente → servidor y estado de los autos servidor → clientes (lo que se reemplaza constantemente).
- Torneo: juego/Torneo tiene el calendario y los puntos; el servidor corre una Carrera por circuito y manda TORNEO antes de RESULTADOS, y el cliente lo guarda como guarda el lobby.
- Clasificación: es una Carrera de una vuelta en modo clasificación (todos salen del mismo lugar y no se chocan); el servidor pasa por la fase GRILLA y arranca solo la carrera, y el cliente solo dibuja sus propios autos.
- Bots: son jugadores sin conexión que corre el servidor; PilotoBot solo produce una EntradaAuto, igual que una persona. La dificultad se ajusta en NivelBot y se mide con tools/bots/PruebaBots.
- Hilos: nunca tocar objetos de LibGDX (texturas, Stage, pantallas) desde hilos de red. Los mensajes pasan por colas thread-safe y se procesan en el hilo de render (o con Gdx.app.postRunnable).

## Convenciones
- Código en español (clases, métodos, variables), salvo la API de LibGDX. Comentarios en español, solo donde el código no sea obvio.
- Nada de números mágicos: física, puertos, frecuencias y tiempos van en Config o ParametrosAuto.
- Liberar recursos en dispose() (texturas, sockets, hilos).
- Commits con prefijo Feat:, Fix:, Docs:, Refactor:, Style:, Test:. Rama develop + ramas feature/*.
- CHANGELOG.md en formato Keep a Changelog.

## Cómo trabajar
- Respondé en español rioplatense.
- Antes de implementar, mostrá un plan breve (clases a crear o modificar) y esperá confirmación.
- Si algo contradice este archivo o docs/PROTOCOLO.md, avisá antes de hacerlo.
- En el código de red, explicá brevemente el porqué de cada decisión: el grupo tiene que poder defenderlo.
- Al terminar, decí cómo probarlo en Eclipse (con varias instancias si hay red de por medio).
- No hagas commits ni push: los hace cada integrante.
- Al cerrar cada etapa, documentá el proceso: agregá `docs/proceso/NN-nombre.md` (objetivo, qué se hizo, decisiones, problemas y cómo se resolvieron, verificación, commits y PR) actualizá la línea de tiempo de `docs/proceso/README.md` y regenerá el PDF con `python docs/proceso/generar_pdf.py` (queda en `docs/proceso/proceso-de-desarrollo.pdf`). No menciones el uso de IA en esa documentación.

## Comandos
- Correr: Eclipse → módulo lwjgl3 → Lwjgl3Launcher → Run As → Java Application (o `gradlew lwjgl3:run`; en PowerShell, `.\gradlew`).
- Jar ejecutable: `gradlew lwjgl3:jar` → lwjgl3/build/libs/.
- Probar red en una sola PC: lanzar Lwjgl3Launcher varias veces (cada ventana es un jugador).
