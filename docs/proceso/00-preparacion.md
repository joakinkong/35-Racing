# Etapa 0: Preparación

## Objetivo

Dejar listo el entorno y el proyecto base, según la guía: herramientas instaladas, proyecto generado con gdx-liftoff, importado en Eclipse, documentos de la propuesta dentro del repositorio y repositorio público en GitHub.

## Qué se hizo

**Herramientas.** Se verificó lo que ya estaba instalado: JDK 21 (cumple el mínimo de Java 17), Git 2.53 con el nombre y el mail configurados, y la CLI de GitHub (`gh`), que después se usó para crear ramas y gestionar colaboradores.

**Proyecto con gdx-liftoff** (versión 1.14.2.3), con estas opciones:

| Opción | Valor |
|---|---|
| Nombre del proyecto | 35 Racing |
| Paquete | `ar.edu.et35.racing` |
| Clase principal | `Main` |
| Plataformas | Core y Desktop (LWJGL3), nada más |
| Extensiones | Freetype únicamente (sin Box2D ni Ashley) |
| Plantilla | Classic |

El proyecto se importó en Eclipse como *Existing Gradle Project* y `Lwjgl3Launcher` abrió la ventana con el logo de libGDX. Antes de importarlo se comprobó que compilara con Gradle (`gradlew lwjgl3:classes`, sin errores).

**Documentos del repositorio.**
- `docs/propuesta/propuesta.pdf`: la propuesta aprobada (versión 2), con un nombre sin espacios para poder referenciarla.
- `docs/propuesta/img/`: las tres figuras de la propuesta.
- `CLAUDE.md`: el contexto fijo del proyecto para el asistente (stack, arquitectura, convenciones y forma de trabajo), con el nombre y el paquete ya completados.

**Repositorio.** [joakinkong/35-Racing](https://github.com/joakinkong/35-Racing), público (necesario para tener Wiki) y con la Wiki activada. Ramas `main` y `develop`. Se invitó como colaboradores a `jasinski1988` (profesor), `JoacoR2007`, `mateoguinsburg35` y `antonellavivacqua`.

## Decisiones

- **Paquete `ar.edu.et35.racing`:** sigue la convención de dominio invertido e identifica a la escuela.
- **Repositorio `35-Racing`:** GitHub no acepta espacios en el nombre. Cuando se fue a crear ya existía uno vacío con ese nombre en la misma cuenta, y se usó ese en lugar de crear otro.
- **Java 17 en Gradle:** gdx-liftoff genera el proyecto compilando para Java 8, pero las pautas piden Java 17. Se cambió `sourceCompatibility`, `targetCompatibility` y `release` a 17 en `build.gradle` y `lwjgl3/build.gradle`, y se volvió a compilar. Con Java 8 no se habrían podido usar funciones del lenguaje como los `record`, que el proyecto usa más adelante.

## Problemas encontrados

- `gh` no tenía sesión iniciada: se resolvió con `gh auth login`.
- El repositorio ya existía vacío: se verificó que estaba vacío, era público y tenía la Wiki activa antes de usarlo.

## Commits

- [`9b1bca7`](https://github.com/joakinkong/35-Racing/commit/9b1bca7): Configuración inicial del proyecto con LibGDX Liftoff.
- [`ecaae86`](https://github.com/joakinkong/35-Racing/commit/ecaae86): Fix: Compilar con Java 17 según las pautas de la materia.
