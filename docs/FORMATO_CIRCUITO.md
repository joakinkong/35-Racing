# Formato de circuito (vectorial)

Cada circuito es un archivo `assets/circuitos/<nombre>.json`. Reemplaza a los mapas de Tiled (`.tmx`): en lugar de
una grilla de tiles, guarda la **geometría** de la pista. El juego la dibuja con líneas y polígonos, y para la
colisión la rasteriza en una grilla fina al cargarla.

Lo genera `tools/circuitos/generar_circuitos.py` a partir de los trazados SVG de f1-circuits-svg (Jules Roy, CC BY 4.0).
`<nombre>` es el mismo identificador que viaja por red (`InfoCircuito.archivo`).

## Unidades y ejes

- Todo está en **píxeles de mundo**.
- El eje Y va **hacia arriba** y el origen es la esquina inferior izquierda del mapa, igual que `Circuito`.
- Los ángulos están en grados: 0 = derecha, 90 = arriba.

## Campos

| Campo | Tipo | Qué es |
|---|---|---|
| `version` | entero | Versión del formato (hoy 1). Si cambia, el cargador tiene que rechazar las que no conozca. |
| `nombre` | texto | Nombre del archivo sin extensión. |
| `origen` | texto | Atribución del trazado. |
| `callejero` | booleano | `true` en los circuitos callejeros o temporales: no tienen pasto. Lo que en un circuito permanente sería pasto de escape es asfalto (`anchos` ya lo incluye), todos los `escapes` valen 0 y el muro queda pegado al asfalto. |
| `ancho`, `alto` | entero | Tamaño del mapa en px. Lo que queda fuera cuenta como muro. |
| `anguloLargada` | número | Hacia dónde apuntan los autos en la largada. |
| `muro` | número | Grosor de la línea de muro, solo para dibujar (px). |
| `paso` | número | Distancia entre dos muestras de la línea central (px). |
| `linea` | `[[x, y], ...]` | Línea central de la pista, **cerrada** (la última muestra conecta con la primera). Va en el sentido de la carrera y la muestra 0 es la línea de meta. |
| `anchos` | `[número, ...]` | Ancho **total** de la pista (px) en cada muestra de `linea`. |
| `escapes` | `[número, ...]` | Pasto de escape (px) a **cada lado** de la pista en cada muestra. |
| `cruces` | `[{tunel, puente}, ...]` | Lugares donde la pista pasa por encima de sí misma. `tunel` y `puente` son índices de `linea`: el tramo `tunel` va por debajo y el `puente` por arriba. Puede estar vacío. |
| `checkpoints` | `[{orden, x, y, ancho, alto}, ...]` | Rectángulos (esquina inferior izquierda más tamaño). `orden` va de 0 a N-1 sin saltos; el 0 es la meta. |
| `largada` | `[{posicion, x, y}, ...]` | Centro de cada lugar, con `posicion` de 1 a 22, escalonados como en la F1: cada uno de los primeros 8 autos, 40 px de pista más atrás que el anterior, y del noveno en adelante la grilla se aprieta a 28 px por auto para que los 22 entren detrás de la meta. |

`linea`, `anchos` y `escapes` tienen siempre la misma cantidad de elementos.

## Cómo se interpreta la geometría

Para cada punto del mundo, se toma la muestra de `linea` más cercana (interpolando entre muestras) y su distancia `d`:

- `d <= anchos / 2` → **pista**.
- `d <= anchos / 2 + escapes` → **pasto** (de escape).
- más lejos → **muro**. El muro es sólido hacia afuera: el auto no puede cruzarlo aunque vaya muy rápido (a 8 px por
  cuadro, una línea de 6 px de grosor se podría atravesar de un salto).

Lo que manda es el asfalto: `anchos` es lo más ancho que permite el circuito y solo se achica si otro tramo pasa tan
cerca que no entra ni el muro pegado, y nunca baja de un mínimo. El pasto (`escapes`) es lo que sobra entre el asfalto y
el muro, hasta un tope; donde no sobra nada vale 0 y el muro queda pegado al asfalto, como en Mónaco. Los tramos que
quedan demasiado cerca para ese mínimo se separan al generar el circuito. En los `cruces` no se achica ni se separa
nada: ahí un tramo pasa por encima del otro.
## Qué dibuja la vista

Tres capas de círculos apilados sobre cada muestra de `linea`, de afuera hacia adentro: el muro (la franja de grosor
`muro` justo después del pasto), el pasto y el asfalto. Es la misma forma que usa la colisión, así que lo que se ve es
lo que se choca. Con antialiasing de 4 muestras los bordes salen lisos.

Los pianos no están en el archivo: la vista los calcula al cargar a partir de la línea central (`vista/Pianos`) y son
solo dibujo. En los `cruces`, el tramo `puente` se dibuja encima de los autos que van por el `tunel`, como una cinta sin
pasto (`juego/ZonaCruce`). La grilla de colisión no cambia: el muro del puente tampoco es sólido, y solo los autos en
niveles distintos dejan de chocarse.

El entorno tampoco está en el archivo: `vista/DisposicionEntorno` lo calcula al cargar a partir de la línea central y del
campo `callejero`. Es solo dibujo.

## Parámetros del generador

Están al principio de `generar_circuitos.py`: `ANCHO_MIN` y `ANCHO_MAX` (asfalto), `ESCAPE` (pasto máximo), `MURO`,
`SEPARACION_MINIMA`, `FACTOR_ANCHO_CORTO` / `KM_CORTO` / `KM_LARGO` (los circuitos cortos son un poco más angostos),
`PASO_MUESTREO`, entre otros. Si se cambian, hay que regenerar los 24 circuitos.
