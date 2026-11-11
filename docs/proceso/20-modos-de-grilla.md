# Etapa 20: Modos de grilla (a mano, clasificación o al azar)

## Objetivo

Que la grilla de largada no dependa siempre de la clasificación ni del orden de llegada a la sala. Joaquín pidió que, sin clasificación, el host pueda cambiar la grilla a mano o sortearla, y después precisó que la opción "clasificación" de la sala tenga tres modos: **sin clasificación**, **clasificación** o **al azar**. Con "al azar", todas las carreras (también las de un torneo) largan con una grilla sorteada; con "sin clasificación", se larga con la grilla que armó el host.

## Qué se hizo

| Pieza | Qué cambió |
|---|---|
| `juego/ModoGrilla` (nueva) | Los tres modos: `MANUAL` ("a mano"), `CLASIFICACION` y `AL_AZAR`; el número del modo es el que viaja por la red |
| `ServidorPartida` | Guarda el orden de la grilla como una lista de ids. `MOVER_GRILLA` (sube o baja un lugar) y `GRILLA_AL_AZAR` (mezcla), solo del host, en el lobby y con la grilla a mano. Con "al azar", sortea otra vez antes de cada carrera. `ELEGIR_GRILLA` reemplaza a `ELEGIR_CLASIFICACION` |
| `EstadoLobby`, `Protocolo`, `ClientePartida` | El `LOBBY` trae el modo de grilla en lugar de un 0 o un 1, con los autos en el orden de la grilla. Protocolo versión 9 |
| `Lobby` | El selector "Clasificación" pasa a "Grilla" con tres valores. La lista de autos muestra el lugar de cada uno; en el modo a mano el host ve flechas para subir y bajar y el botón "Sortear grilla"; debajo, una línea dice de dónde sale la largada |
| `PruebaCapacidad` | Comprueba los tres modos contra el servidor real |

## Decisiones

**El orden vive en el servidor.** El host solo pide mover un lugar o sortear, y el servidor decide: el cliente no puede armar una grilla inválida (repetir un auto, dejar a uno afuera). Como el `LOBBY` ya manda los autos en una lista, la lista pasó a ir en el orden de la grilla y no hizo falta un campo nuevo.

**Orden por defecto sin cambios.** Sin tocar nada, la grilla es la de siempre: las personas primero, en el orden en que entraron, y después los bots. Una persona que entra queda después de la última persona; un bot, al final. Lo que el host ya movió se respeta aunque entren o salgan autos.

**Flechas y no arrastrar.** Con 22 autos y una lista con desplazamiento, arrastrar es incómodo; las flechas suben o bajan un lugar y el botón de sorteo resuelve el caso de querer mezclar todo. Los botones llevan "^" y "v" porque las fuentes del juego no tienen triángulos.

**Torneo.** Con "a mano", todas las carreras largan con la grilla del host. Con "al azar", se sortea antes de cada una. Con "clasificación", cada carrera arranca con su clasificación, como antes. La alternativa de que la grilla dependa de la tabla de puntos quedó para más adelante.

**Solo en la sala.** El orden se arma antes de iniciar; no hay forma de cambiarlo con la carrera empezada.

## Problemas y cómo se resolvieron

| Problema | Causa | Solución |
|---|---|---|
| Anticipado: un auto que se va deja un hueco en el orden guardado | El orden guardado tiene ids que ya no existen | Antes de usarlo, el servidor lo sincroniza: saca los que se fueron y suma los nuevos |
| Los botones de grilla ocupaban el ancho del nombre | La lista ya estaba al límite del panel | Con las flechas, el nombre se acorta con puntos suspensivos; sin ellas, conserva el ancho |

## Verificación

- `PruebaCapacidad`, contra el servidor real: sin tocar nada, las personas largan antes que los bots; el host sube un lugar al último y el que estaba antepenúltimo baja; sortear no pierde ni repite a nadie; un invitado no puede mover la grilla; con clasificación no se mueve ni se sortea; con 22 autos se larga exactamente con la grilla que armó el host. En una sala de 12 con la grilla al azar, la largada es un sorteo de los mismos 12 autos y no el orden del lobby.
- `PruebaClasificacion` (clasificación, grilla y carrera con el servidor real) sigue pasando con el modo nuevo.
- Se revisó una captura de la sala real con 21 autos en modo a mano, con las flechas y el botón de sorteo (`CapturaPantallas veintidos`).

**Sin verificar:**
- **Torneo con "al azar":** se comprobó el sorteo de la primera carrera, no que cada carrera siguiente largue con una grilla distinta.
- **Red real:** no se probó entre varias PC.
- **A ojo:** las flechas con 22 filas y el desplazamiento no se usaron con el mouse; solo se miró la captura.
- **Desempate del torneo:** "va adelante el que entró antes" ahora sigue el orden de la grilla del host al iniciar, no el de llegada a la sala.

## Commits y pull request

Rama `feature/veintidos-autos`, los mismos commits que la etapa 19: `6affd0f` y `eece4ca`. Entró a `main` con el PR [#25](https://github.com/joakinkong/35-Racing/pull/25).

La misma rama siguió con ajustes menores: la aureola del auto propio y la escala del dibujo de los autos (`0c3893b`) y la limpieza de la documentación (`87cba34`). Entraron a `main` con el PR [#26](https://github.com/joakinkong/35-Racing/pull/26).
