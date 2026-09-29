# Registro de Cambios

Todos los cambios significativos de este proyecto se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/), y este proyecto se adhiere al [Versionado Semántico](https://semver.org/lang/es/).

## [Sin publicar]

### Agregado

- Esqueleto del juego con estética Fórmula 1 (fondo carbono, rojo de largada, bandera de cuadros y pianos): `Main` extiende `Game` y hay cinco pantallas navegables (`MenuPrincipal`, `UnirsePartida`, `Lobby`, `Carrera`, `Resultados`), con ESC para volver.
- `Config` con las constantes globales, `Paleta` con los colores y `Recursos` que centraliza el `AssetManager`, el `SpriteBatch` y el `Skin` armado por código.
- Resolución virtual de 640x360 con `FitViewport` y filtro Nearest; ventana inicial de 1280x720 redimensionable.
- Paquetes `juego`, `vista`, `red`, `pantallas` y `util`, cada uno con su `package-info.java`.

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

---

[0.1.0]: https://github.com/joakinkong/35-Racing/releases/tag/v0.1.0
