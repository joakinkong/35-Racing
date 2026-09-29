# 35 Racing

Proyecto final de Programación sobre Redes (grupo de 5). Juego de carreras 2D estilo F1 arcade, vista cenital, pixel art. Multijugador por LAN: de 2 a 5 jugadores (Config.MAX_JUGADORES = 5), cada uno en su PC. Uno crea la partida (host: servidor + jugador) y el resto se une por IP.

## Stack (obligatorio por las pautas de la materia)
- Java 17 + LibGDX (proyecto de gdx-liftoff), Gradle, Eclipse. Módulos: core (todo el juego) y lwjgl3 (lanzador de escritorio).
- Red: solo java.net (Socket, ServerSocket, DatagramSocket). Prohibido usar KryoNet, Netty u otras librerías de red. Nada de bases de datos.
- Sin Box2D: física arcade propia.
- No agregar dependencias sin preguntar.

## Arquitectura
- Paquetes bajo ar.edu.et35.racing:
  - pantallas: Screens de LibGDX (menú, unirse, lobby, carrera, resultados).
  - juego: modelo y simulación (Carrera, Auto, Circuito, EntradaAuto). Sin clases gráficas ni lectura de teclado: lo usa también el servidor.
  - vista: dibujo del modelo (mapa, autos, HUD).
  - red: servidor y cliente. Especificación en docs/PROTOCOLO.md.
  - util: Config (constantes) y utilidades.
- Simulación a paso fijo (1/60 s), separada del render.
- Todo lo que un jugador hace con su auto es una EntradaAuto (acelerar, frenar, giro). Es lo que viaja por red.
- Circuitos: mapas de Tiled (.tmx) en assets/circuitos, convertidos a Circuito (datos puros: superficies, checkpoints, largada).
- Red: servidor autoritativo embebido en el proceso del host, con hilo propio. El host también se conecta como cliente a 127.0.0.1, así hay un solo camino de código para los clientes.
  - TCP: lobby, selección, inicio y fin de carrera, desconexiones (lo que no se puede perder).
  - UDP: entradas cliente → servidor y estado de los autos servidor → clientes (lo que se reemplaza constantemente).
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

## Comandos
- Correr: Eclipse → módulo lwjgl3 → Lwjgl3Launcher → Run As → Java Application (o `gradlew lwjgl3:run`; en PowerShell, `.\gradlew`).
- Jar ejecutable: `gradlew lwjgl3:jar` → lwjgl3/build/libs/.
- Probar red en una sola PC: lanzar Lwjgl3Launcher varias veces (cada ventana es un jugador).
