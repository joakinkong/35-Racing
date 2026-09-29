# Cómo fuimos trabajando: proceso de desarrollo de 35 Racing

Este documento muestra el proceso real de trabajo del proyecto, etapa por etapa: qué se pidió, qué se hizo, qué decisiones se tomaron, qué problemas aparecieron y cómo se comprobó que funcionaba. Está escrito para que se pueda seguir en orden y verificar contra el historial de Git del repositorio.

## Cómo trabajamos

**Uso de inteligencia artificial.** El proyecto se desarrolla con Claude Code (un asistente de IA que trabaja dentro del repositorio) siguiendo una guía de prompts por etapas. La IA escribe y revisa código y documentación; el grupo decide qué se hace, aprueba los planes, prueba el juego y pide cambios. Los commits se hacen desde la cuenta de Joaquín Barreira y no llevan la línea `Co-Authored-By`, por lo que la IA no figura como colaboradora en GitHub: su uso se declara acá.

El ciclo que se repitió en cada etapa fue este:

1. **Plan primero.** Antes de escribir código, el asistente muestra un plan (clases a crear o modificar y decisiones a tomar) y se espera la confirmación del grupo. Es una regla del archivo [CLAUDE.md](../../CLAUDE.md).
2. **Rama de trabajo.** Cada etapa se hace en una rama `feature/...` que sale de `develop`.
3. **Implementación.** Código en español, con constantes en `Config` y `ParametrosAuto` (sin números mágicos) y el modelo del juego separado de lo gráfico.
4. **Verificación.** Además de compilar con Gradle, se escribieron pruebas descartables que abren el juego con la ventana oculta, simulan autos con entradas guionadas y sacan capturas de pantalla. Los resultados numéricos de cada prueba están en los documentos de cada etapa.
5. **Corrección.** Los errores que encontró la prueba o el grupo al jugar se corrigieron en commits propios, con prefijo `Fix:`.
6. **Integración.** Pull request de la rama de trabajo a `develop`, y de `develop` a `main` al cerrar un hito. El [CHANGELOG](../../CHANGELOG.md) se actualiza en cada etapa.

Los commits usan los prefijos `Feat:`, `Fix:`, `Docs:` y `Refactor:`, como pide la guía.

## Línea de tiempo

Las etapas 0 a 4 se hicieron el 28/09/2026, entre las 21:16 y las 23:45 (hora local, según los commits). Esta documentación del proceso se armó a continuación, y las etapas 5 a 8 el 29/09/2026.

| Etapa | Qué quedó funcionando | Pull request |
|---|---|---|
| [0. Preparación](00-preparacion.md) | Proyecto LibGDX (core + lwjgl3) con Java 17, repositorio público en GitHub con ramas `main` y `develop` | (commits directos) |
| [1. Pre-entrega 1](01-pre-entrega-1.md) | README, CHANGELOG, `.gitignore` y la propuesta pasada a Markdown para la Wiki | [#1](https://github.com/joakinkong/35-Racing/pull/1) |
| [2. Esqueleto y pantallas](02-esqueleto-pantallas.md) | Menú, unirse, lobby, carrera y resultados navegables, con estética de Fórmula 1 | [#2](https://github.com/joakinkong/35-Racing/pull/2) |
| [3. Auto y circuito](03-auto-y-circuito.md) | Un auto manejable en un circuito, con física arcade, cámara y HUD | [#3](https://github.com/joakinkong/35-Racing/pull/3) |
| [4. Reglas de carrera](04-reglas-de-carrera.md) | Carrera completa para 2 jugadores en el mismo teclado: cuenta regresiva, vueltas, posiciones, choques y resultados | [#4](https://github.com/joakinkong/35-Racing/pull/4) y [#5](https://github.com/joakinkong/35-Racing/pull/5) |
| Documentación del proceso | Esta documentación por etapas y su versión en PDF | [#6](https://github.com/joakinkong/35-Racing/pull/6) |
| [5. Protocolo de red](05-protocolo-de-red.md) | Especificación completa de la comunicación en red en [docs/PROTOCOLO.md](../PROTOCOLO.md) (diseño, todavía sin código) | [#7](https://github.com/joakinkong/35-Racing/pull/7) |
| [6. Conexión y lobby por TCP](06-conexion-y-lobby-tcp.md) | Crear una partida, unirse por IP, lobby en vivo con elección de auto y "listo", e inicio de la carrera (cada uno todavía corre la suya) | [#9](https://github.com/joakinkong/35-Racing/pull/9) |
| [7. Carrera sincronizada por UDP](07-carrera-sincronizada-udp.md) | La carrera se juega en red: el servidor la simula, los clientes mandan sus teclas y dibujan lo que llega; resultados, revancha y tolerancia a pérdida de paquetes | [#9](https://github.com/joakinkong/35-Racing/pull/9) y [#10](https://github.com/joakinkong/35-Racing/pull/10) |
| [8. Robustez de red](08-robustez-de-red.md) | Aviso y vuelta al menú si dejan de llegar datos UDP, fallas del servidor que cierran la partida con motivo, y cierre de ventana limpio desde cualquier pantalla | Pendiente |

Estado del repositorio al cerrar la etapa 8: `develop` tiene todo hasta la etapa 7 (con la red completa); `main` llega hasta la etapa 4 y esta documentación; la etapa 8 está en la rama `feature/robustez-red`.

## Lo que sigue

- **Antes de seguir:** que los cinco integrantes lean [docs/PROTOCOLO.md](../PROTOCOLO.md), porque es lo que tienen que poder explicar.
- **Ahora:** jugar la carrera en red con varias instancias y, cuanto antes, en las computadoras del colegio (TCP 7777 y UDP 7778): si la red bloquea algo, mejor enterarse ahora. La [etapa 8](08-robustez-de-red.md) explica cómo se ve cada falla y cómo reproducirla.
- **Etapa 9:** pruebas en la LAN real con 2, 3 y 5 PC.
- **Pendiente de arte:** el circuito definitivo en Tiled y los sprites. Hoy el circuito y los autos son provisorios (ver [etapa 3](03-auto-y-circuito.md)).

## Participación del equipo

Hasta la etapa 8 todos los commits salieron de la cuenta de Joaquín Barreira. El resto del grupo tiene invitación como colaborador del repositorio, y las tareas de arte (circuito y sprites) están asignadas al resto del equipo según la guía. Cuando haya commits de los demás integrantes, se van a reflejar en el historial y en esta documentación.
