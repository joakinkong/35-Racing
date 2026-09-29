# Etapa 1: Pre-entrega 1

## Objetivo

Completar la documentación mínima de la primera pre-entrega: propuesta en la Wiki, `.gitignore`, CHANGELOG y README.

## Qué se hizo

- **Propuesta en Markdown para la Wiki.** Se extrajo el texto del PDF de la propuesta y se pasó a `docs/wiki/Propuesta-del-Proyecto.md`, respetando el texto y la numeración de secciones. La portada quedó resumida en un encabezado con el título, la fecha y los integrantes. Se dejaron afuera la tabla de contenidos y la sección de aprobación, y se insertaron las tres figuras con su epígrafe. Después la página se cargó a mano en la Wiki de GitHub.
- **`.gitignore`.** Se revisó el que generó liftoff contra la lista pedida: ya cubría todo (Gradle, `bin/`, `.project`, `.classpath`, `.settings/`, `.idea/`, `Thumbs.db`, etc.) salvo `.claude/settings.local.json`, que se agregó.
- **CHANGELOG.** Se creó `CHANGELOG.md` en español con formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/) y una entrada `[0.1.0]` para el proyecto generado, el repositorio, el README, el CHANGELOG y la Wiki.
- **README.** Se reemplazó el README genérico de liftoff por uno propio: nombre, integrantes, descripción breve basada en la propuesta, tecnologías, enlace a la Wiki, cómo compilar y ejecutar (Eclipse y consola) y estado actual.

## Decisiones

- **Fecha del CHANGELOG en formato ISO** (`2026-09-28`), que es el que usa Keep a Changelog.
- **README solo con lo que ya existe:** describe el estado real ("configuración inicial y estructura del proyecto") y no promete funciones que todavía no están.

## Problemas encontrados

- **Lectura del PDF.** La herramienta de lectura de PDF del asistente necesita `poppler`, que no está instalado. Se resolvió extrayendo el texto con la biblioteca `pypdf` de Python, que además hizo falta configurar con codificación UTF-8 para que no fallaran los caracteres especiales.
- **Link roto en el CHANGELOG.** El CHANGELOG enlazaba al tag `v0.1.0`, que nunca se creó, así que daba error 404. Se quitó el enlace en lugar de crear un tag que nadie había pedido ([`6436374`](https://github.com/joakinkong/35-Racing/commit/6436374)).

## Checklist de la pre-entrega 1 (estado al cerrar la etapa)

| Ítem | Estado |
|---|---|
| El proyecto compila y corre en escritorio | Cumplido |
| `.gitignore` adecuado | Cumplido |
| README con nombre, integrantes, descripción, tecnologías, Wiki e instrucciones | Cumplido |
| CHANGELOG con la entrada inicial | Cumplido |
| Wiki activada, con la propuesta | Cumplido |
| Todos los integrantes y los 3 profesores como colaboradores | Incompleto: había 4 invitaciones enviadas y faltaban Madai y dos de los profesores |
| Commits de varios integrantes | Incompleto: todos los commits eran de una sola cuenta |

## Commits y pull request

- [`85c0daf`](https://github.com/joakinkong/35-Racing/commit/85c0daf): Docs: README, CHANGELOG y .gitignore para pre-entrega 1.
- [`a023fda`](https://github.com/joakinkong/35-Racing/commit/a023fda): Docs: Propuesta del proyecto en Markdown para la Wiki.
- [`6436374`](https://github.com/joakinkong/35-Racing/commit/6436374): Docs: Quitar link roto al tag v0.1.0 del CHANGELOG.
- Pull request [#1](https://github.com/joakinkong/35-Racing/pull/1): `develop` a `main`.
