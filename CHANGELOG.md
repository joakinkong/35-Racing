# Registro de Cambios

Todos los cambios significativos de este proyecto se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/), y este proyecto se adhiere al [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Agregado

- Pianos rojos y blancos en las curvas cerradas (`vista/Pianos`). Son solo dibujo: no cambian la física ni los JSON.
- Puente en el cruce de Suzuka: los autos de abajo pasan tapados por el puente y los de distintos niveles no se chocan ni se esquivan. El muro del puente es solo dibujo. No cambia el protocolo.
- `PruebaCruce`, en `tools/circuitos/verificacion`, comprueba los niveles y los choques en el cruce.
- Torneos, a elegir en la sala: un calendario con los circuitos que quiera el host, repetidos o no, y puntos acumulados, con el último siempre sumando 1. Con 8 autos se reparten 15, 12, 10, 8, 6, 4, 2 y 1. Protocolo versión 7.
- Clasificación opcional, a elegir en la sala: una vuelta, todos a la vez, cada uno sin ver a los demás; el orden de los tiempos arma la grilla y la carrera arranca sola. Protocolo versión 6.
- Bots: el host completa la grilla hasta 8 autos eligiendo la dificultad de cada uno (fácil, medio o difícil). Corren en el servidor, con los mismos controles que una persona. Se puede jugar una persona contra bots. Protocolo versión 5.
- `tools/bots`: `PruebaBots` mide los niveles sin red y `PruebaSalaBots` prueba las reglas de la sala contra el servidor real.
- Mapa del circuito en tiempo real, arriba a la derecha (`vista/MiniMapa`): el trazado, la meta y un punto del color de cada auto, con los de esta PC más grandes y con borde blanco. No cambia el protocolo.

### Cambiado

- La grilla de largada es escalonada, como en la F1: cada auto, 40 px más atrás que el anterior. No cambia ningún trazado.
- Hasta 8 jugadores por partida (`Config.MAX_JUGADORES`). Los 24 circuitos tienen 8 lugares de largada, sin cambios en el trazado ni en los 5 lugares que ya existían. El `ESTADO` con 8 autos ocupa 204 bytes. Protocolo versión 4.
- El pasto es de un verde un poco más fuerte.
- Toda la interfaz copia la estética de PIT WALL: colores, tarjetas, esquinas rectas y las fuentes Space Grotesk e Inter. Los colores de la pista también salen de esa paleta (la forma y la colisión no cambian).
- La interfaz pasa a 1280x720 (`Config.ANCHO_INTERFAZ`) para que el texto se vea nítido; el mundo sigue en 640x360 (`Config.ANCHO_MUNDO`, antes `ANCHO_VIRTUAL`).

### Agregado

- Fuentes generadas en `assets/fuentes` con `tools/fuentes/generar_fuentes.py`, sin dependencias nuevas, y sus licencias OFL.
- `tools/interfaz/CapturaPantallas`: recorre las pantallas del juego real y guarda una captura de cada una.
- `VistaCircuito` arma una sola vez la malla del circuito (círculos apilados sobre cada punto de la línea central, en tres capas: muro, pasto y asfalto) con la misma forma que usa la colisión, y dibuja solo los bloques que entran en la cámara. El lanzador activa antialiasing de 4 muestras (`Config.MUESTRAS_ANTIALIASING`).
- `CargadorCircuito` lee el JSON y rasteriza una grilla de colisión de 4 px (`Config.CELDA_COLISION`): pista, pasto y, todo lo demás, muro.
- HUD estilo Fórmula 1: `TorreHud` muestra una fila por auto con la posición (el puntero en rojo), la franja de su escudería, las tres letras del piloto y su mejor vuelta, más la vuelta del puntero; el panel de cada jugador queda con su vuelta y su tiempo.
- La prueba local ahora abre `PruebaLocal`, donde se elige el circuito entre los 24 (con `<` y `>` o las flechas) antes de correr; ESC en la carrera vuelve ahí.
- Skill `circuitos` (`.claude/skills/circuitos/SKILL.md`) y programas de verificación en `tools/circuitos/verificacion` (carga, atajos, vuelta de un bot y capturas de la vista real).

- Escuderías, pilotos y circuitos de la temporada 2026: `Catalogo` con las 11 escuderías, los 22 pilotos (número, país, color) y los 24 circuitos (Gran Premio, ciudad, kilómetros, curvas), con los datos de `f1.db` de proyecto f1.
- Circuitos vectoriales: los 24 circuitos de la temporada 2026 son archivos JSON en `assets/circuitos` (línea central, ancho de asfalto y de pasto en cada punto, checkpoints y largada; formato en `docs/FORMATO_CIRCUITO.md`) que genera `tools/circuitos/generar_circuitos.py` a partir de los trazados SVG de f1-circuits-svg (Jules Roy, CC BY 4.0). El asfalto va lo más ancho que permite cada circuito (los cortos, un poco más angostos), el pasto es lo que sobra entre el asfalto y el muro, y los tramos que quedan demasiado cerca se separan. Los 9 circuitos callejeros (Albert Park, Jeddah, Miami, Gilles-Villeneuve, Mónaco, Madring, Bakú, Marina Bay y Las Vegas) no tienen pasto: es asfalto con el muro pegado. Cada vuelta dura en promedio un minuto. Con `--vista` deja un PNG por circuito.
- Vueltas elegibles: el host elige de 1 a 10 vueltas en el lobby (`ELEGIR_VUELTAS`; el `LOBBY` las incluye). Protocolo versión 3.
- Lobby: cada jugador elige uno de los 22 pilotos (con un solo jugador por escudería) y el host elige el circuito. El HUD muestra el código del piloto y los autos se pintan con el color de su escudería.
- Protocolo versión 2 (la 3 agregó las vueltas): `ELEGIR_PILOTO` (0 a 21) reemplaza a `ELEGIR_AUTO`, `ELEGIR_CIRCUITO` (solo el host), el `LOBBY` trae el circuito elegido, el error `ESCUDERIA_OCUPADA` reemplaza a `AUTO_OCUPADO` y `CUENTA_REGRESIVA` trae el nombre del circuito elegido. El servidor pide el circuito elegido al host recién cuando se inicia la carrera (`ProveedorCircuitos`), que lo carga en su hilo de render; si falla, el host recibe `CIRCUITO_NO_CARGO`.

- Documentación de la etapa 9 (pruebas en LAN real): carrera completa con revancha entre 2 notebooks en el hotspot de un celular y pérdida UDP simulada sin problemas; en la red del colegio no se pudo unir nadie por el firewall. Quedan pendientes las pruebas con 3 y 5 jugadores.
- Robustez de red: si dejan de llegar estados UDP en plena carrera, aviso "Conexión inestable..." a 1 s y vuelta al menú a `Config.TIMEOUT_RED` (5 s), con un mensaje que apunta al firewall si nunca llegó ninguno; el servidor avisa en su consola si un jugador no manda entradas UDP; una falla inesperada en la simulación del servidor cierra la partida para todos con el motivo.
- Cierre de ventana a prueba de fallas en cualquier pantalla (sockets, hilos y puertos liberados) y tope de espera al conectar.
- Carrera sincronizada por UDP según `docs/PROTOCOLO.md`: el servidor simula la `Carrera` a 60 ticks por segundo con la última entrada de cada jugador y manda el `ESTADO` de todos los autos 30 veces por segundo; los clientes mandan su `ENTRADA` 60 veces por segundo y dibujan lo que llega, interpolado 100 ms en el pasado. Paquetes binarios en `PaquetesUdp`, descarte por secuencia y por token, `Interpolador` y `CarreraEnRed`.
- Resultados por TCP y revancha: el host vuelve al lobby con los mismos jugadores o cierra la partida para todos. Si un jugador abandona en carrera, su auto sale de la pista y los demás ven un aviso.
- `FotoCarrera`, `FotoAuto` y `FuenteCarrera`: la pantalla de carrera dibuja fotos de una `SimulacionLocal` (prueba local, sin red) o de la carrera en red.
- Pérdida de paquetes simulada (`Config.PERDIDA_UDP_SIMULADA`) y, con `DEBUG_RED`, la tecla F8 para cambiarla en vivo entre 0 %, 20 %, 50 % y 100 % (corte total).
- Conexión y lobby por TCP según `docs/PROTOCOLO.md`: paquete `red` con `ServidorPartida` (hilo de aceptación, un lector por cliente y un hilo de lógica que es el único que toca el estado), `ClientePartida` (conexión en segundo plano, cola thread-safe de mensajes y `PING` cada 2 s), `Protocolo`, `ConexionTcp`, `EstadoLobby`, `SesionRed` y `DireccionesLocales`.
- "Crear partida" (pide el nombre y levanta el servidor), "Unirse" (nombre e IP, con mensajes de error claros) y lobby en vivo con elección de auto sin repetir, "listo", botón de iniciar solo para el host y las IPs de la red del host. Al iniciar, cada jugador corre su propia carrera local con su nombre y color.
- Log en consola de cada mensaje de red (`Config.DEBUG_RED`) y constantes de red en `Config`.
- Diseño del protocolo de red en `docs/PROTOCOLO.md`: arquitectura con servidor autoritativo en el host, máquina de estados de la partida, mensajes TCP de texto con sus errores, paquetes UDP binarios `ENTRADA` (11 bytes) y `ESTADO` (132 bytes con 5 autos) con números de secuencia, frecuencias y ancho de banda, interpolación en el cliente, hilos, manejo de fallas y diagramas. Todavía sin código.
- Esqueleto del juego con estética Fórmula 1 (fondo carbono, rojo de largada, bandera de cuadros y pianos): `Main` extiende `Game` y hay cinco pantallas navegables (`MenuPrincipal`, `UnirsePartida`, `Lobby`, `Carrera`, `Resultados`), con ESC para volver.
- `Config` con las constantes globales, `Paleta` con los colores y `Recursos` que centraliza el `AssetManager`, el `SpriteBatch` y el `Skin` armado por código.
- Resolución virtual de 640x360 con `FitViewport` y filtro Nearest; ventana inicial de 1280x720 redimensionable.
- Paquetes `juego`, `vista`, `red`, `pantallas` y `util`, cada uno con su `package-info.java`.
- Circuito provisorio `assets/circuitos/circuito1.tmx` (óvalo de 32x32 con tileset propio en pixel art, 10 checkpoints y 5 lugares de largada), a reemplazar por el de arte manteniendo el formato.
- Modelo del juego sin dependencias gráficas: `Circuito`, `CargadorCircuito` (TiledMap a Circuito), `Superficie`, `EntradaAuto`, `ParametrosAuto` y `Auto` con física arcade (aceleración, freno, reversa, giro según velocidad, derrape leve, pasto y rebote contra muros).
- Vista de la carrera: `VistaCircuito`, `VistaAuto` (monoplaza dibujado con formas) y `CamaraSeguimiento`.
- `Carrera` con un auto manejable (flechas o WASD): simulación a paso fijo con acumulador, dibujo interpolado y HUD con la velocidad.
- Reglas de carrera en `juego` (sin dependencias gráficas, para que las simule el servidor): `Carrera` con estados `CUENTA_REGRESIVA`, `EN_CURSO` y `TERMINADA`, de 1 a 5 autos por id, cuenta regresiva de 3 s con los autos quietos, vueltas validadas por checkpoints en orden (no suma ir marcha atrás ni en contramano), tiempo por vuelta, mejor vuelta y tiempo total, posiciones por vueltas, checkpoint y distancia, cierre de 30 s después de que el primero termina y choques entre autos que se separan y se reparten la velocidad. Se suman `Participante`, `EstadoCarrera` y `ResultadoJugador`.
- Prueba local para 2 jugadores en el mismo teclado (J1 con WASD, J2 con flechas) con pantalla dividida (una cámara por jugador, siempre en zoom 2x) y HUD por jugador (vuelta, posición, tiempo actual y mejor vuelta), cartel de cuenta regresiva y "¡YA!".
- Resultados con datos reales (posición, piloto, tiempo total y mejor vuelta), que se muestran solos al terminar la carrera.
- `Tiempo` para formatear tiempos de carrera y una paleta de colores para distinguir hasta 5 autos.

- Documentación del proceso de desarrollo en `docs/proceso/`: una página por etapa (objetivo, qué se hizo, decisiones, problemas y su resolución, verificación, commits y capturas) y una línea de tiempo, más un PDF que las reúne (`docs/proceso/proceso-de-desarrollo.pdf`) y el script que lo regenera (`generar_pdf.py`).

### Corregido

- Los checkpoints del circuito provisorio van de muro a muro (pista más pasto de escape) y no solo sobre la pista: un auto que se sale al pasto igual los cruza y sus vueltas cuentan, con la desventaja de ir más lento.
- En la prueba local, cada jugador tiene su propia cámara con zoom fijo de 2x y la pantalla queda siempre dividida en dos mitades. Antes había una cámara compartida que seguía al que iba primero cuando se separaban, y el otro quedaba fuera de pantalla.

### Cambiado

- `VistaAuto` y `CamaraSeguimiento` reciben posición, ángulo y velocidad en lugar de un `Auto`; `ResultadoJugador` suma el id del jugador; el servidor recibe el `Circuito` ya cargado.
- La pantalla `Carrera` pasa a llamarse `PantallaCarrera`, para no chocar con el modelo `juego.Carrera`. "Prueba local" abre 2 jugadores y "Iniciar" en el Lobby, 1 jugador (hasta que haya red).
- La física del auto es más exigente: velocidad máxima de 450 px/s, la aceleración baja a medida que se acerca a ese máximo, el radio mínimo de giro crece con el cuadrado de la velocidad (radio = velocidad² / `ACELERACION_LATERAL`) y girar cuesta un poco de velocidad (`PERDIDA_POR_GIRO`). El zoom de la cámara pasa de 2 a 1,25.
- `Circuito` guarda la geometría de la pista y una grilla de colisión fina en lugar de una grilla de tiles; la API (`superficieEn`, `chocaConMuro`, checkpoints, largada) no cambia, así que `Auto`, `Carrera` y el servidor siguen igual. Un circuito sin los campos `linea`, `anchos` y `escapes` no se puede cargar.
- El servidor sigue pidiendo el circuito al host cuando se inicia la carrera, pero ya no hace falta cargarlo en el hilo de render por las texturas: se mantiene así por la regla de no tocar LibGDX desde los hilos de red.
- El panel de cada jugador ya no muestra la posición ni la mejor vuelta: pasaron a la torre de tiempos.

### Eliminado

- Los 25 mapas de Tiled (`.tmx`), los dos tilesets (`tileset.png` y `tileset16.png`) y `tools/circuitos/reducir_tileset.py`. `Superficie.desdeTexto` también, porque solo servía para leer las propiedades de Tiled.

## [0.1.0] - 2026-09-28

### Agregado

- Proyecto generado con gdx-liftoff (módulos core y lwjgl3).
- Repositorio en GitHub (rama main estable, rama develop para integración, ramas feature/* para funcionalidades).
- Wiki de GitHub con propuesta del proyecto en Markdown.
- README.md con descripción, tecnologías, instrucciones de compilación y ejecución.
- CHANGELOG.md (este archivo).
- .gitignore completo para Gradle, Java, Eclipse, IntelliJ y archivos del sistema.
- CLAUDE.md con contexto, arquitectura y convenciones del proyecto.
- Documentación de propuesta en docs/propuesta/ (PDF y figuras).

### Cambios

- Compilación configurada para Java 17 (LTS).
