# Etapa 5: Diseño del protocolo de red

## Objetivo

Diseñar, sin escribir código, cómo se comunican las computadoras durante una partida. El resultado es [docs/PROTOCOLO.md](../PROTOCOLO.md): la especificación que se va a implementar en las etapas 6 a 8 y lo que el grupo tiene que poder explicar. La guía pedía cubrir la arquitectura, la máquina de estados de la partida, los mensajes TCP y UDP con su formato exacto, las frecuencias con la cuenta del ancho de banda, la interpolación en el cliente, los hilos, las fallas, la justificación de TCP y UDP y un diagrama de secuencia de una partida completa.

Para esta etapa se usó el modelo Claude Opus 5.5, como recomienda la guía para los pasos de diseño.

## El plan (se mostró y se confirmó antes de escribir)

Antes de redactar el documento se presentaron las decisiones principales, y el grupo confirmó estas tres, que eran las que tenían alternativas:

| Decisión | Lo elegido | Alternativa que se descartó |
|---|---|---|
| Frecuencia de los estados y retraso de dibujo | 30 estados por segundo y 100 ms de retraso de interpolación | 60 por segundo y 50 ms (menos demora, el doble de tráfico) |
| Predicción del auto propio | No, en la primera versión | Predecir desde el principio (más complejo) |
| Detección de caídas | `PING` cada 2 s y conexión perdida a los 6 s sin datos | (sin alternativa planteada) |

## Qué se decidió

- **Servidor autoritativo dentro del proceso del host,** y el host se conecta a su propio servidor por `127.0.0.1` como un cliente más, así hay un solo camino de código.
- **Un solo hilo toca el estado de la partida** (el de simulación). Los demás hilos le pasan datos por una cola de eventos (lo que llega por TCP y no se puede perder) o por un valor "última entrada" que se sobrescribe (lo que llega por UDP). Así el modelo no necesita sincronización.
- **TCP en texto** (una línea por mensaje, campos separados por `;`) para el lobby, la largada, los resultados y las desconexiones, con una tabla de 8 códigos de error.
- **UDP en binario** con dos paquetes: `ENTRADA` (11 bytes, cliente al servidor, 60 por segundo) y `ESTADO` (132 bytes con los 5 autos, servidor a todos, 30 por segundo). Cada uno lleva un número de secuencia y se descarta todo lo que llega con un número menor o igual al último.
- **El servidor aprende la dirección UDP de cada cliente** con un token que entrega por TCP en la bienvenida: guarda la dirección de origen de la primera `ENTRADA` válida.

## Números que se verificaron

Los tamaños de los paquetes se comprobaron armándolos campo por campo con un script (la suma de los tamaños de cada campo), y las cuentas de ancho de banda y demora, calculándolas en el mismo script en lugar de estimarlas a mano:

| Qué | Valor |
|---|---|
| `ENTRADA` | 11 bytes (39 con las cabeceras IP y UDP) |
| `ESTADO` con 5 autos | 132 bytes (160 con cabeceras); entra en un paquete sin fragmentarse (el máximo es 1.472) |
| Tráfico en la placa de red del host con 5 jugadores | 28.560 bytes/s, unos 0,23 Mbit/s (el 0,23 % de una red de 100 Mbit/s) |
| Demora desde la tecla hasta ver el auto propio | unos 135 ms típica, 171 ms en el peor caso |
| Lo mismo con 60 estados por segundo y 50 ms | unos 77 ms típica, 104 ms en el peor caso |

## Problemas encontrados y cómo se resolvieron

1. **Los números del plan no coincidían con el diseño final.** En el plan se habían estimado 10 bytes para `ENTRADA` y 141 para `ESTADO`. Al armar los formatos campo por campo quedaron en 11 y 132 bytes (se agregó un byte de tipo a la entrada y se sacó la velocidad del estado, que el cliente puede estimar). El documento usa los números verificados y se le informó al grupo la diferencia. El cambio en el ancho de banda es mínimo.
2. **La demora de 30 Hz con 100 ms resultó mayor de lo que parecía.** El cálculo paso a paso mostró que casi toda la demora es el retraso de interpolación y no la red (en una LAN un paquete tarda de 1 a 5 ms en ir y volver). Se mantuvo lo confirmado, pero el documento deja el plan para la etapa 7: probar, y si la dirección se siente lenta, pasar a 60 Hz y 50 ms cambiando dos constantes; la predicción queda como último recurso.
3. **El servidor no puede cargar el circuito.** Al revisar el código actual contra el diseño, se vio que cargar el mapa de Tiled crea texturas, algo que en LibGDX solo se puede hacer en el hilo de render, no en un hilo del servidor. Se resolvió en el diseño: el host carga el `Circuito` y se lo pasa al servidor al crearlo.
4. **Decimales y configuración regional.** En una PC configurada en español, `String.format` escribe los decimales con coma, y el otro lado no los puede leer como número. Se decidió que todos los tiempos de los mensajes TCP vayan en milisegundos enteros.
5. **Diagramas.** Los tres diagramas Mermaid del documento (arquitectura, estados y secuencia) se renderizaron con la biblioteca oficial de Mermaid para confirmar que no tienen errores de sintaxis antes de subirlos. GitHub los muestra como gráficos.

## Cambios al modelo que quedan para la etapa 7

El documento termina con una lista de 7 cosas del código actual que complican la red. Entre ellas, que la pantalla de carrera simula y dibuja a la vez (en red el cliente solo dibuja), que `VistaAuto` y la cámara necesitan un `Auto` con física, que hace falta marcar un jugador como desconectado y que el color del auto sale del id del jugador y no del auto elegido en el lobby.

## Próximo paso

La guía indica que los cinco integrantes lean [docs/PROTOCOLO.md](../PROTOCOLO.md) antes de seguir con la etapa 6 (conexión y lobby por TCP), porque es lo que tienen que poder explicar.

## Commits

- Documentación de la etapa en la rama `docs/protocolo-red`: el protocolo, esta página y la actualización del PDF.
