"""Genera los mapas .tmx de los circuitos a partir de los trazados SVG de f1-circuits-svg.

Uso:
    python tools/circuitos/generar_circuitos.py [circuito ...] [--svg RUTA] [--salida RUTA] [--vista RUTA]

Sin nombres genera los 24 circuitos de la temporada 2026. Con --vista deja además un PNG por circuito para
revisarlos a ojo. Los mapas usan tiles de 16 px (assets/circuitos/tileset16.png, que sale de reducir_tileset.py) y el mismo
formato que circuito1.tmx (capas "suelo", "checkpoints" y "largada", propiedad "anguloLargada"), así que
CargadorCircuito los lee sin cambios.

Los trazados de f1-circuits-svg son de Jules Roy (CC BY 4.0): https://github.com/julesr0y/f1-circuits-svg
"""
import argparse
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trazado  # noqa: E402

# Tiles de 16 px (tileset16.png): más finos que los de 32 px, así entra un muro entre dos tramos de pista pegados.
TILE = 16
# El ancho de la pista (en tiles) cambia a lo largo del circuito, entre ANCHO_MIN y ANCHO_MAX: en cada punto es el
# más ancho que deja SEPARACION_TILES libres (muro y pasto) hasta el tramo de pista más cercano que no es su vecino.
# Así Mónaco, que tiene tramos pegados por todos lados, queda más angosto que Austin, y las horquillas se angostan
# solas. A cada lado de la pista hay ESCAPE tiles de pasto y MURO de muro.
ANCHO_MIN = 6
ANCHO_MAX = 12
SEPARACION_TILES = 3       # pasto, muro y pasto entre dos tramos pegados
ARCO_MINIMO = 700          # tramos más cerca que esto a lo largo de la pista no cuentan (son la misma curva)
CAMBIO_ANCHO = 0.25        # cuánto puede cambiar el ancho (en tiles) entre dos muestras, para que no haya escalones
ESCAPE = 2
MURO = 1
MARGEN_EXTERIOR = 6

# Ids de los tiles del tileset (gid de Tiled). Las propiedades están en CABECERA_TILESET.
PASTO, PISTA, MURO_T, META, PASTO_EXTERIOR = 1, 2, 3, 4, 5

# Duración de una vuelta limpia: SEGUNDOS_VUELTA en promedio, un poco más en los circuitos más largos que el promedio
# y menos en los más cortos (SEGUNDOS_POR_KM por cada kilómetro de diferencia). El largo en píxeles sale de multiplicar
# esa duración por una velocidad media razonable (menor que ParametrosAuto.VELOCIDAD_MAX, por las curvas).
SEGUNDOS_VUELTA = 75
SEGUNDOS_POR_KM = 8
VELOCIDAD_MEDIA = 280      # px/s
PASO_MUESTREO = 8          # distancia entre muestras de la línea central, en píxeles de mundo
SEPARACION_CHECKPOINTS = 288
LARGADA_ATRAS = 56         # píxeles entre la meta y el primer auto
LARGADA_ENTRE_FILAS = 80
LARGADA_LATERAL = 40      # separación del eje de la pista; se achica si la pista es angosta
JUGADORES_MAX = 5

# (id del SVG, kilómetros reales). El nombre del archivo sale del id con guiones bajos.
CIRCUITOS = [
    ('melbourne-2', 5.278), ('shanghai-1', 5.451), ('suzuka-2', 5.807), ('bahrain-1', 5.412),
    ('jeddah-1', 6.174), ('miami-1', 5.412), ('montreal-6', 4.361), ('monaco-6', 3.337),
    ('catalunya-6', 4.657), ('madring-1', 5.473), ('spielberg-3', 4.318), ('silverstone-8', 5.891),
    ('spa-francorchamps-4', 7.004), ('hungaroring-3', 4.381), ('zandvoort-5', 4.259), ('monza-7', 5.793),
    ('baku-1', 6.003), ('marina-bay-4', 4.940), ('austin-1', 5.513), ('mexico-city-3', 4.304),
    ('interlagos-2', 4.309), ('las-vegas-1', 6.201), ('lusail-1', 5.419), ('yas-marina-2', 5.281),
]

KM_PROMEDIO = sum(km for _, km in CIRCUITOS) / len(CIRCUITOS)

CABECERA_TILESET = """ <tileset firstgid="1" name="tileset16" tilewidth="16" tileheight="16" tilecount="5" columns="5">
  <image source="tileset16.png" width="80" height="16"/>
  <tile id="0">
   <properties>
    <property name="superficie" value="pasto"/>
   </properties>
  </tile>
  <tile id="1">
   <properties>
    <property name="superficie" value="pista"/>
   </properties>
  </tile>
  <tile id="2">
   <properties>
    <property name="superficie" value="muro"/>
   </properties>
  </tile>
  <tile id="3">
   <properties>
    <property name="superficie" value="pista"/>
   </properties>
  </tile>
  <tile id="4">
   <properties>
    <property name="superficie" value="pasto"/>
   </properties>
  </tile>
 </tileset>
"""


def nombre_archivo(id_svg):
    return id_svg.rsplit('-', 1)[0].replace('-', '_')


class Circuito:
    """Un circuito ya pasado a píxeles de mundo (eje Y hacia abajo, como Tiled)."""

    def __init__(self, id_svg, km, ruta_svg):
        self.id_svg = id_svg
        self.nombre = nombre_archivo(id_svg)
        self.km = km
        t, total = trazado.leer_trazado(ruta_svg, paso=0.5)
        segundos = SEGUNDOS_VUELTA + SEGUNDOS_POR_KM * (km - KM_PROMEDIO)
        self.escala = segundos * VELOCIDAD_MEDIA / total
        puntos = [(x * self.escala, y * self.escala) for x, y in t.puntos]
        largada = (t.largada[0] * self.escala, t.largada[1] * self.escala)
        puntos = self._remuestrear(puntos, PASO_MUESTREO)
        # El primer punto es el más cercano a la línea de largada y la lista va en el sentido de la flecha.
        n = len(puntos)
        i0 = min(range(n), key=lambda i: math.dist(puntos[i], largada))
        puntos = puntos[i0:] + puntos[:i0]
        tangente = self._tangente(puntos, 0)
        if tangente[0] * t.sentido[0] + tangente[1] * t.sentido[1] < 0:
            puntos = [puntos[0]] + puntos[:0:-1]
        self.puntos = puntos
        self.paso = PASO_MUESTREO
        self.anchos = self._calcular_anchos()
        self.ancho_minimo = min(self.anchos)
        self.ancho_maximo = max(self.anchos)

    def medio(self, i):
        """Mitad del ancho de la pista (px) en la muestra i."""
        return self.anchos[i % len(self.puntos)] * TILE / 2

    def _calcular_anchos(self):
        """Ancho de pista en cada muestra, según qué tan cerca pasa otro tramo de la pista."""
        pts = self.puntos
        n = len(pts)
        excluidos = set()
        for i, j in detectar_cruces(self):
            for k in (i, j):
                excluidos.update((k + d) % n for d in range(-40, 41))
        minimo_arco = int(ARCO_MINIMO / self.paso)
        # Más allá de este radio el ancho ya es el máximo, así que alcanza con mirar las celdas vecinas.
        lado_celda = (ANCHO_MAX + SEPARACION_TILES) * TILE
        celdas = {}
        for i, (px, py) in enumerate(pts):
            celdas.setdefault((int(px // lado_celda), int(py // lado_celda)), []).append(i)
        anchos = []
        for a in range(n):
            mejor = float('inf')
            if a not in excluidos:
                ax, ay = pts[a]
                cx, cy = int(ax // lado_celda), int(ay // lado_celda)
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        for b in celdas.get((cx + dx, cy + dy), ()):
                            if b in excluidos or min(abs(b - a), n - abs(b - a)) <= minimo_arco:
                                continue
                            d2 = (pts[b][0] - ax) ** 2 + (pts[b][1] - ay) ** 2
                            if d2 < mejor:
                                mejor = d2
            separacion = math.sqrt(mejor)
            anchos.append(max(ANCHO_MIN, min(ANCHO_MAX, (separacion - SEPARACION_TILES * TILE) / TILE)))
        # Sin escalones: el ancho no puede cambiar más que CAMBIO_ANCHO por muestra (dos vueltas para cerrar el lazo).
        for _ in range(2):
            for a in range(n):
                anchos[a] = min(anchos[a], anchos[a - 1] + CAMBIO_ANCHO)
            for a in range(n - 1, -1, -1):
                anchos[a] = min(anchos[a], anchos[(a + 1) % n] + CAMBIO_ANCHO)
        return anchos

    @staticmethod
    def _remuestrear(puntos, paso):
        resultado, _ = trazado.resamplear(puntos, paso)
        return resultado

    def _tangente(self, puntos, i, ventana=4):
        n = len(puntos)
        a, b = puntos[(i - ventana) % n], puntos[(i + ventana) % n]
        dx, dy = b[0] - a[0], b[1] - a[1]
        largo = math.hypot(dx, dy)
        return dx / largo, dy / largo

    def tangente(self, i):
        return self._tangente(self.puntos, i)

    def desplazar(self, dx, dy):
        self.puntos = [(x + dx, y + dy) for x, y in self.puntos]

    @property
    def largo(self):
        return len(self.puntos) * self.paso

    def punto_a_distancia(self, s):
        """Posición y tangente a s píxeles de arco desde la largada (s puede ser negativo)."""
        n = len(self.puntos)
        f = s / self.paso
        i = math.floor(f)
        r = f - i
        a, b = self.puntos[i % n], self.puntos[(i + 1) % n]
        return (a[0] + (b[0] - a[0]) * r, a[1] + (b[1] - a[1]) * r), self.tangente(i % n)


def construir_suelo(c):
    """Devuelve (columnas, filas, origen_x, origen_y, suelo) con el gid de cada tile (suelo[fila][columna])."""
    margen = ANCHO_MAX * TILE / 2 + (ESCAPE + MURO + MARGEN_EXTERIOR) * TILE
    xs = [p[0] for p in c.puntos]
    ys = [p[1] for p in c.puntos]
    origen_x = math.floor((min(xs) - margen) / TILE) * TILE
    origen_y = math.floor((min(ys) - margen) / TILE) * TILE
    columnas = math.ceil((max(xs) + margen - origen_x) / TILE)
    filas = math.ceil((max(ys) + margen - origen_y) / TILE)
    c.desplazar(-origen_x, -origen_y)

    # Para cada tile, "exceso" = distancia a la línea central menos la mitad del ancho de ese tramo, tomando la muestra
    # más cercana: <= 0 es pista, y después vienen el pasto de escape y el muro. Se guarda también el exceso del tramo
    # más cercano que no sea vecino (otra parte de la pista): si es casi igual, el tile está a mitad de camino entre dos
    # tramos pegados y ahí va el muro, para que no se pueda cruzar de uno al otro.
    n = len(c.puntos)
    minimo_arco = int(ARCO_MINIMO / c.paso)
    alcance = (ESCAPE + MURO) * TILE

    def estampar(procesar):
        for i, (px, py) in enumerate(c.puntos):
            medio = c.medio(i)
            radio_tiles = int((medio + alcance) // TILE) + 2
            col0, fila0 = int(px // TILE), int(py // TILE)
            for fila in range(max(0, fila0 - radio_tiles), min(filas, fila0 + radio_tiles + 1)):
                for col in range(max(0, col0 - radio_tiles), min(columnas, col0 + radio_tiles + 1)):
                    procesar(i, fila, col, math.hypot((col + 0.5) * TILE - px, (fila + 0.5) * TILE - py) - medio)

    mejor = [[(None, -1)] * columnas for _ in range(filas)]

    def primero(i, fila, col, exceso):
        if mejor[fila][col][0] is None or exceso < mejor[fila][col][0]:
            mejor[fila][col] = (exceso, i)

    estampar(primero)
    segundo = [[None] * columnas for _ in range(filas)]

    def otro_tramo(i, fila, col, exceso):
        i1 = mejor[fila][col][1]
        if i1 >= 0 and min(abs(i - i1), n - abs(i - i1)) > minimo_arco:
            if segundo[fila][col] is None or exceso < segundo[fila][col]:
                segundo[fila][col] = exceso

    estampar(otro_tramo)
    suelo = [[PASTO_EXTERIOR] * columnas for _ in range(filas)]
    for fila in range(filas):
        for col in range(columnas):
            exceso = mejor[fila][col][0]
            if exceso is None:
                continue
            if exceso <= 0:
                suelo[fila][col] = PISTA
            elif exceso > alcance:
                continue
            elif segundo[fila][col] is not None and segundo[fila][col] - exceso <= TILE:
                suelo[fila][col] = MURO_T
            elif exceso <= ESCAPE * TILE:
                suelo[fila][col] = PASTO
            else:
                suelo[fila][col] = MURO_T
    # Línea de meta: una tira de tiles de pista cruzando la pista en la largada.
    (px, py), (tx, ty) = c.punto_a_distancia(0)
    for fila in range(filas):
        for col in range(columnas):
            if suelo[fila][col] != PISTA:
                continue
            dx, dy = (col + 0.5) * TILE - px, (fila + 0.5) * TILE - py
            u = dx * tx + dy * ty
            v = -dx * ty + dy * tx
            if abs(u) <= TILE and abs(v) <= c.medio(0):
                suelo[fila][col] = META
    return columnas, filas, suelo


def _intersecan(p1, p2, p3, p4):
    d = (p2[0] - p1[0]) * (p4[1] - p3[1]) - (p2[1] - p1[1]) * (p4[0] - p3[0])
    if abs(d) < 1e-9:
        return False
    t = ((p3[0] - p1[0]) * (p4[1] - p3[1]) - (p3[1] - p1[1]) * (p4[0] - p3[0])) / d
    u = ((p3[0] - p1[0]) * (p2[1] - p1[1]) - (p3[1] - p1[1]) * (p2[0] - p1[0])) / d
    return 0 <= t < 1 and 0 <= u < 1


def detectar_cruces(c):
    """Pares (i, j) de muestras donde la línea central se cruza consigo misma, con i < j y un solo par por cruce."""
    pts = c.puntos
    n = len(pts)
    celdas = {}
    for i, p in enumerate(pts):
        celdas.setdefault((int(p[0] // TILE), int(p[1] // TILE)), []).append(i)
    cruces = []
    for i in range(n):
        a, b = pts[i], pts[(i + 1) % n]
        cx, cy = int(a[0] // TILE), int(a[1] // TILE)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for j in celdas.get((cx + dx, cy + dy), []):
                    if j <= i or min(j - i, n - (j - i)) <= 3:
                        continue
                    if _intersecan(a, b, pts[j], pts[(j + 1) % n]):
                        # Un mismo cruce aparece en varias celdas vecinas: se queda con uno por zona.
                        if not any(abs(i - i2) < 8 and abs(j - j2) < 8 for i2, j2 in cruces):
                            cruces.append((i, j))
    return cruces


def construir_cruces(c, columnas, filas, suelo, cruces):
    """Para cada cruce, el tramo anterior pasa "por debajo" (túnel) y el posterior "por arriba" (puente).

    Devuelve la capa "puente" (gid de los tiles de pista del puente que tapan el túnel, 0 donde no hay)
    y, por cruce, el rectángulo que los contiene con los ángulos de ambos tramos.
    """
    puente = [[0] * columnas for _ in range(filas)]
    zonas = []
    n = len(c.puntos)
    for iu, ib in cruces:
        tu, tb = c.tangente(iu), c.tangente(ib)
        medio_b = c.medio(ib)
        hasta_muro_u = c.medio(iu) + (ESCAPE + MURO) * TILE
        seno = max(0.35, abs(tu[0] * tb[1] - tu[1] * tb[0]))
        ventana = int(min(320.0, (c.medio(ib) + hasta_muro_u) / seno) / c.paso) + 1

        def tiles_cerca(centro, radio):
            tiles = set()
            r_tiles = int(radio // TILE) + 1
            for k in range(centro - ventana, centro + ventana + 1):
                px, py = c.puntos[k % n]
                col0, fila0 = int(px // TILE), int(py // TILE)
                for fila in range(max(0, fila0 - r_tiles), min(filas, fila0 + r_tiles + 1)):
                    for col in range(max(0, col0 - r_tiles), min(columnas, col0 + r_tiles + 1)):
                        if (px - (col + 0.5) * TILE) ** 2 + (py - (fila + 0.5) * TILE) ** 2 <= radio * radio:
                            tiles.add((fila, col))
            return tiles

        solape = tiles_cerca(ib, medio_b) & tiles_cerca(iu, hasta_muro_u)
        solape = {(f, col) for f, col in solape if suelo[f][col] in (PISTA, META)}
        if not solape:
            continue
        for f, col in solape:
            puente[f][col] = suelo[f][col]
        filas_z = [f for f, _ in solape]
        cols_z = [col for _, col in solape]
        x0, x1 = min(cols_z) * TILE, (max(cols_z) + 1) * TILE
        y0, y1 = min(filas_z) * TILE, (max(filas_z) + 1) * TILE
        # El mapa del juego tiene el eje Y hacia arriba: los ángulos se miden con el Y invertido.
        zonas.append((x0, y0, x1 - x0, y1 - y0,
                      math.degrees(math.atan2(-tu[1], tu[0])), math.degrees(math.atan2(-tb[1], tb[0]))))
    return puente, zonas


def calcular_checkpoints(c):
    """Cuadrados centrados en la línea central, de la meta en adelante. Devuelve [(x, y, lado)]."""
    cantidad = max(8, round(c.largo / SEPARACION_CHECKPOINTS))
    resultado = []
    for k in range(cantidad):
        s = c.largo * k / cantidad
        (x, y), _ = c.punto_a_distancia(s)
        lado = 2 * c.medio(round(s / c.paso)) + 2 * (ESCAPE + MURO) * TILE
        resultado.append((x - lado / 2, y - lado / 2, lado))
    return resultado


def calcular_largada(c):
    """Posiciones 1 a 5, en filas de dos, detrás de la meta y sobre la línea central."""
    posiciones = []
    for i in range(JUGADORES_MAX):
        atras = LARGADA_ATRAS + LARGADA_ENTRE_FILAS * (i // 2)
        (x, y), (tx, ty) = c.punto_a_distancia(-atras)
        lateral = min(LARGADA_LATERAL, c.medio(round(-atras / c.paso)) - 16)
        lado = lateral if i % 2 == 0 else -lateral
        posiciones.append((x - ty * lado, y + tx * lado))
    (_, _), (tx, ty) = c.punto_a_distancia(0)
    # El mapa del juego tiene el eje Y hacia arriba: el ángulo se mide con el Y invertido.
    angulo = math.degrees(math.atan2(-ty, tx))
    return posiciones, angulo


def validar(c, columnas, filas, suelo, checkpoints, largada):
    avisos = []
    for i, (x, y) in enumerate(largada):
        col, fila = int(x // TILE), int(y // TILE)
        if not (0 <= col < columnas and 0 <= fila < filas) or suelo[fila][col] not in (PISTA, META):
            avisos.append('el lugar de largada %d no cae sobre la pista' % (i + 1))
    # Un checkpoint no debería contener tramos de pista lejanos (en arco) del suyo.
    n = len(c.puntos)
    cercania = int((ANCHO_MAX + 2 * (ESCAPE + MURO)) * TILE * 1.5 / c.paso)
    conflictos = 0
    for k, (x, y, lado) in enumerate(checkpoints):
        centro_i = round(n * k / len(checkpoints))
        for j in range(0, n, 2):
            d = min((j - centro_i) % n, (centro_i - j) % n)
            if d > cercania:
                px, py = c.puntos[j]
                if x <= px <= x + lado and y <= py <= y + lado:
                    # Solo molesta si ahí se va en el mismo sentido: en contramano el juego no lo cuenta.
                    ox, oy = c.tangente(j)
                    sx, sy = c.tangente(centro_i % n)
                    if ox * sx + oy * sy > 0.3:
                        conflictos += 1
                        break
    if conflictos:
        avisos.append('%d de %d checkpoints incluyen otro tramo de la pista' % (conflictos, len(checkpoints)))
    return avisos


def escribir_tmx(ruta, c, columnas, filas, suelo, checkpoints, largada, angulo, puente=None, zonas=()):
    lineas = ['<?xml version="1.0" encoding="UTF-8"?>']
    siguiente_objeto = 1 + len(checkpoints) + len(largada) + len(zonas)
    lineas.append('<map version="1.10" tiledversion="1.10.2" orientation="orthogonal" renderorder="right-down" '
                  'width="%d" height="%d" tilewidth="%d" tileheight="%d" infinite="0" nextlayerid="%d" nextobjectid="%d">'
                  % (columnas, filas, TILE, TILE, 6 if zonas else 4, siguiente_objeto))
    lineas.append(' <properties>')
    lineas.append('  <property name="anguloLargada" type="float" value="%s"/>' % redondear(angulo))
    lineas.append(' </properties>')
    lineas.append(CABECERA_TILESET.rstrip('\n'))
    lineas.append(' <layer id="1" name="suelo" width="%d" height="%d">' % (columnas, filas))
    lineas.append('  <data encoding="csv">')
    for i, fila in enumerate(suelo):
        lineas.append(','.join(str(g) for g in fila) + (',' if i < filas - 1 else ''))
    lineas.append('</data>')
    lineas.append(' </layer>')
    if zonas:
        lineas.append(' <layer id="4" name="puente" width="%d" height="%d">' % (columnas, filas))
        lineas.append('  <data encoding="csv">')
        for i, fila in enumerate(puente):
            lineas.append(','.join(str(g) for g in fila) + (',' if i < filas - 1 else ''))
        lineas.append('</data>')
        lineas.append(' </layer>')
    lineas.append(' <objectgroup id="2" name="checkpoints">')
    for orden, (x, y, lado) in enumerate(checkpoints):
        lineas.append('  <object id="%d" x="%s" y="%s" width="%s" height="%s">'
                      % (orden + 1, redondear(x), redondear(y), redondear(lado), redondear(lado)))
        lineas.append('   <properties>')
        lineas.append('    <property name="orden" type="int" value="%d"/>' % orden)
        lineas.append('   </properties>')
        lineas.append('  </object>')
    lineas.append(' </objectgroup>')
    lineas.append(' <objectgroup id="3" name="largada">')
    for i, (x, y) in enumerate(largada):
        lineas.append('  <object id="%d" x="%s" y="%s" width="16" height="16">'
                      % (len(checkpoints) + i + 1, redondear(x - 8), redondear(y - 8)))
        lineas.append('   <properties>')
        lineas.append('    <property name="posicion" type="int" value="%d"/>' % (i + 1))
        lineas.append('   </properties>')
        lineas.append('  </object>')
    lineas.append(' </objectgroup>')
    if zonas:
        lineas.append(' <objectgroup id="5" name="cruces">')
        for k, (x, y, ancho, alto, ang_tunel, ang_puente) in enumerate(zonas):
            lineas.append('  <object id="%d" x="%s" y="%s" width="%s" height="%s">'
                          % (len(checkpoints) + len(largada) + k + 1, redondear(x), redondear(y),
                             redondear(ancho), redondear(alto)))
            lineas.append('   <properties>')
            lineas.append('    <property name="anguloTunel" type="float" value="%s"/>' % redondear(ang_tunel))
            lineas.append('    <property name="anguloPuente" type="float" value="%s"/>' % redondear(ang_puente))
            lineas.append('   </properties>')
            lineas.append('  </object>')
        lineas.append(' </objectgroup>')
    lineas.append('</map>')
    with open(ruta, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lineas) + '\n')


def redondear(v):
    texto = ('%.3f' % v).rstrip('0').rstrip('.')
    return texto if texto not in ('-0', '') else '0'


# ---------------------------------------------------------------- vista previa

COLORES = {
    PASTO: (76, 150, 70), PISTA: (62, 62, 72), MURO_T: (205, 55, 55), META: (245, 245, 245),
    PASTO_EXTERIOR: (40, 105, 45),
}


def escribir_png(ruta, ancho, alto, pixeles):
    crudo = b''.join(b'\x00' + bytes(fila) for fila in pixeles)

    def trozo(tipo, datos):
        cuerpo = tipo + datos
        return struct.pack('>I', len(datos)) + cuerpo + struct.pack('>I', zlib.crc32(cuerpo) & 0xffffffff)

    with open(ruta, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(trozo(b'IHDR', struct.pack('>IIBBBBB', ancho, alto, 8, 2, 0, 0, 0)))
        f.write(trozo(b'IDAT', zlib.compress(crudo, 9)))
        f.write(trozo(b'IEND', b''))


def escribir_vista(ruta, c, columnas, filas, suelo, checkpoints, largada, px=2, puente=None):
    ancho, alto = columnas * px, filas * px
    img = [[0] * (ancho * 3) for _ in range(alto)]
    for fila in range(filas):
        for col in range(columnas):
            color = (90, 130, 230) if puente and puente[fila][col] else COLORES[suelo[fila][col]]
            for dy in range(px):
                linea = img[fila * px + dy]
                for dx in range(px):
                    o = (col * px + dx) * 3
                    linea[o:o + 3] = color

    def punto(x, y, color, radio=2):
        cx, cy = int(x / TILE * px), int(y / TILE * px)
        for yy in range(max(0, cy - radio), min(alto, cy + radio + 1)):
            for xx in range(max(0, cx - radio), min(ancho, cx + radio + 1)):
                img[yy][xx * 3:xx * 3 + 3] = color

    for x, y, lado in checkpoints:
        punto(x + lado / 2, y + lado / 2, (250, 220, 40), 1)
    for x, y in largada:
        punto(x, y, (60, 140, 255), 2)
    escribir_png(ruta, ancho, alto, img)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('circuitos', nargs='*', help='nombres (monza, suzuka, ...); sin nombres, los 24')
    ap.add_argument('--svg', default=os.path.expanduser('~/Desktop/proyecto f1/f1-circuits-svg-main/circuits/detailed/white'),
                    help='carpeta con los SVG "detailed"')
    raiz = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ap.add_argument('--salida', default=os.path.join(raiz, 'assets', 'circuitos'))
    ap.add_argument('--vista', help='carpeta donde dejar un PNG de revisión por circuito')
    args = ap.parse_args()

    elegidos = [c for c in CIRCUITOS if not args.circuitos or nombre_archivo(c[0]) in args.circuitos]
    if not elegidos:
        sys.exit('No hay circuitos con esos nombres: ' + ', '.join(nombre_archivo(c[0]) for c in CIRCUITOS))
    os.makedirs(args.salida, exist_ok=True)
    if args.vista:
        os.makedirs(args.vista, exist_ok=True)
    for id_svg, km in elegidos:
        c = Circuito(id_svg, km, os.path.join(args.svg, id_svg + '.svg'))
        columnas, filas, suelo = construir_suelo(c)
        checkpoints = calcular_checkpoints(c)
        largada, angulo = calcular_largada(c)
        avisos = validar(c, columnas, filas, suelo, checkpoints, largada)
        puente, zonas = construir_cruces(c, columnas, filas, suelo, detectar_cruces(c))
        escribir_tmx(os.path.join(args.salida, c.nombre + '.tmx'), c, columnas, filas, suelo, checkpoints, largada,
                     angulo, puente, zonas)
        if args.vista:
            escribir_vista(os.path.join(args.vista, c.nombre + '.png'), c, columnas, filas, suelo, checkpoints, largada,
                           puente=puente)
        print('%-18s %3dx%-3d tiles  pista de %.1f a %.1f tiles  vuelta %5.0f px (~%2.0f s)  %2d checkpoints%s%s'
              % (c.nombre, columnas, filas, c.ancho_minimo, c.ancho_maximo, c.largo, c.largo / VELOCIDAD_MEDIA, len(checkpoints),
                 '  cruces: %d' % len(zonas) if zonas else '',
                 ''.join('\n    AVISO: ' + a for a in avisos)))


if __name__ == '__main__':
    main()
