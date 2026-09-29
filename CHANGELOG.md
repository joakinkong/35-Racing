# Registro de Cambios

Todos los cambios significativos de este proyecto se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/), y este proyecto se adhiere al [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Agregado

- Esqueleto del juego con estética Fórmula 1 (fondo carbono, rojo de largada, bandera de cuadros y pianos): `Main` extiende `Game` y hay cinco pantallas navegables (`MenuPrincipal`, `UnirsePartida`, `Lobby`, `Carrera`, `Resultados`), con ESC para volver.
- `Config` con las constantes globales, `Paleta` con los colores y `Recursos` que centraliza el `AssetManager`, el `SpriteBatch` y el `Skin` armado por código.
- Resolución virtual de 640x360 con `FitViewport` y filtro Nearest; ventana inicial de 1280x720 redimensionable.
- Paquetes `juego`, `vista`, `red`, `pantallas` y `util`, cada uno con su `package-info.java`.
- Circuito provisorio `assets/circuitos/circuito1.tmx` (óvalo de 32x32 con tileset propio en pixel art, 10 checkpoints y 5 lugares de largada), a reemplazar por el de arte manteniendo el formato.
- Modelo del juego sin dependencias gráficas: `Circuito`, `CargadorCircuito` (TiledMap a Circuito), `Superficie`, `EntradaAuto`, `ParametrosAuto` y `Auto` con física arcade (aceleración, freno, reversa, giro según velocidad, derrape leve, pasto y rebote contra muros).
- Vista de la carrera: `VistaCircuito`, `VistaAuto` (monoplaza dibujado con formas) y `CamaraSeguimiento`.
- `Carrera` con un auto manejable (flechas o WASD): simulación a paso fijo con acumulador, dibujo interpolado y HUD con la velocidad.
- Reglas de carrera en `juego` (sin dependencias gráficas, para que las simule el servidor): `Carrera` con estados `CUENTA_REGRESIVA`, `EN_CURSO` y `TERMINADA`, de 1 a 5 autos por id, cuenta regresiva de 3 s con los autos quietos, vueltas validadas por checkpoints en orden (no suma ir marcha atrás ni en contramano), tiempo por vuelta, mejor vuelta y tiempo total, posiciones por vueltas, checkpoint y distancia, cierre de 30 s después de que el primero termina y choques entre autos que se separan y se reparten la velocidad. Se suman `Participante`, `EstadoCarrera` y `ResultadoJugador`.
- Prueba local para 2 jugadores en el mismo teclado (J1 con WASD, J2 con flechas) con cámara que encuadra a los dos y HUD por jugador (vuelta, posición, tiempo actual y mejor vuelta), cartel de cuenta regresiva y "¡YA!".
- Resultados con datos reales (posición, piloto, tiempo total y mejor vuelta), que se muestran solos al terminar la carrera.
- `Tiempo` para formatear tiempos de carrera y una paleta de colores para distinguir hasta 5 autos.

### Corregido

- Los checkpoints del circuito provisorio van de muro a muro (pista más pasto de escape) y no solo sobre la pista: un auto que se sale al pasto igual los cruza y sus vueltas cuentan, con la desventaja de ir más lento.
- En la prueba local, cuando los dos autos se separan más de lo que entra con el zoom máximo, la pantalla se divide y cada jugador ve su propio auto. Antes la cámara seguía siempre al que iba primero y el otro quedaba fuera de pantalla.

### Cambiado

- La pantalla `Carrera` pasa a llamarse `PantallaCarrera`, para no chocar con el modelo `juego.Carrera`. "Prueba local" abre 2 jugadores y "Iniciar" en el Lobby, 1 jugador (hasta que haya red).
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
