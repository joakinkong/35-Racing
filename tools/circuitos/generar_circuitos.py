import argparse
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trazado

VERSION_FORMATO = 1

ANCHO_MIN = 72
ANCHO_MAX = 128
SEPARACION_MINIMA = 12
ARCO_MINIMO = 300
CAMBIO_ANCHO = 4.0
FACTOR_ANCHO_CORTO = 0.8
KM_CORTO = 3.4
KM_LARGO = 5.4
ARCO_REPULSION = 160
ITERACIONES_SEPARACION = 120
VENTANA_EMPUJE = 6
VENTANA_CURVA = 5
FACTOR_CURVA = 1.6
ESCAPE = 24
CAMBIO_ESCAPE = 4.0
MURO = 6
MARGEN_MUNDO = 240

SEGUNDOS_VUELTA = 64
SEGUNDOS_POR_KM = 8
VELOCIDAD_MEDIA = 350
PASO_MUESTREO = 8
SEPARACION_CHECKPOINTS = 288
HOLGURA_CHECKPOINT = 96
LARGADA_ATRAS = 56
LARGADA_ESCALON = 40
LARGADA_AUTOS_CON_ESCALON = 8
LARGADA_ESCALON_APRETADO = 28
LARGADA_LATERAL = 40
LARGADA_BORDE = 16
JUGADORES_MAX = 22
CELDA_BUSQUEDA = 16

CIRCUITOS = [
    ('melbourne-2', 5.278), ('shanghai-1', 5.451), ('suzuka-2', 5.807), ('bahrain-1', 5.412),
    ('jeddah-1', 6.174), ('miami-1', 5.412), ('montreal-6', 4.361), ('monaco-6', 3.337),
    ('catalunya-6', 4.657), ('madring-1', 5.473), ('spielberg-3', 4.318), ('silverstone-8', 5.891),
    ('spa-francorchamps-4', 7.004), ('hungaroring-3', 4.381), ('zandvoort-5', 4.259), ('monza-7', 5.793),
    ('baku-1', 6.003), ('marina-bay-4', 4.940), ('austin-1', 5.513), ('mexico-city-3', 4.304),
    ('interlagos-2', 4.309), ('las-vegas-1', 6.201), ('lusail-1', 5.419), ('yas-marina-2', 5.281),
]

FACTOR_ANCHO_CALLEJERO = 0.85
CALLEJEROS = {'melbourne', 'jeddah', 'miami', 'montreal', 'monaco', 'madring', 'baku', 'marina_bay', 'las_vegas'}

KM_PROMEDIO = sum(km for _, km in CIRCUITOS) / len(CIRCUITOS)


def nombre_archivo(id_svg):
    return id_svg.rsplit('-', 1)[0].replace('-', '_')


def factor_ancho(km):
    t = max(0.0, min(1.0, (km - KM_CORTO) / (KM_LARGO - KM_CORTO)))
    return FACTOR_ANCHO_CORTO + (1 - FACTOR_ANCHO_CORTO) * t


class Circuito:
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
        n = len(puntos)
        i0 = min(range(n), key=lambda i: math.dist(puntos[i], largada))
        puntos = puntos[i0:] + puntos[:i0]
        tangente = self._tangente(puntos, 0)
        if tangente[0] * t.sentido[0] + tangente[1] * t.sentido[1] < 0:
            puntos = [puntos[0]] + puntos[:0:-1]
        self.puntos = puntos
        self.paso = PASO_MUESTREO
        self.limite_ancho_min = ANCHO_MIN * factor_ancho(km)
        self.limite_ancho_max = ANCHO_MAX * factor_ancho(km)
        self.callejero = self.nombre in CALLEJEROS
        self.cruces = detectar_cruces(self)
        self._separar_curvas_cerradas()
        self.cruces = detectar_cruces(self)
        self.cercanos = self._buscar_cercanos()
        self.anchos = self._calcular_anchos()
        self.escapes = self._calcular_escapes()
        if self.callejero:
            self.anchos = [max(self.limite_ancho_min, (a + 2 * e) * FACTOR_ANCHO_CALLEJERO)
                           for a, e in zip(self.anchos, self.escapes)]
            self.escapes = [0.0] * len(self.escapes)
        self.ancho_minimo = min(self.anchos)
        self.ancho_maximo = max(self.anchos)

    def _separar_curvas_cerradas(self):
        n = len(self.puntos)
        d_min = self.limite_ancho_min + SEPARACION_MINIMA
        arco_min = int(ARCO_REPULSION / self.paso)
        excluidos = set()
        for i, j in self.cruces:
            for k in (i, j):
                excluidos.update((k + d) % n for d in range(-40, 41))
        pts = [list(p) for p in self.puntos]
        lado = d_min * 2
        celdas = {}
        for i, (x, y) in enumerate(pts):
            celdas.setdefault((int(x // lado), int(y // lado)), []).append(i)
        pares = []
        for a in range(n):
            if a in excluidos:
                continue
            cx, cy = int(pts[a][0] // lado), int(pts[a][1] // lado)
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for b in celdas.get((cx + dx, cy + dy), ()):
                        arco = min(b - a, n - (b - a))
                        if b > a and b not in excluidos and arco > arco_min \
                                and math.dist(pts[a], pts[b]) < d_min * 1.5:
                            pares.append((a, b))
        for _ in range(ITERACIONES_SEPARACION):
            empuje = [[0.0, 0.0] for _ in range(n)]
            hubo = False
            for a, b in pares:
                vx, vy = pts[a][0] - pts[b][0], pts[a][1] - pts[b][1]
                d = math.hypot(vx, vy)
                if 1e-6 < d < d_min:
                    f = (d_min - d) * 0.5 / d
                    empuje[a][0] += vx * f
                    empuje[a][1] += vy * f
                    empuje[b][0] -= vx * f
                    empuje[b][1] -= vy * f
                    hubo = True
            if not hubo:
                break
            for _ in range(2):
                empuje = [[sum(empuje[(i + k) % n][c] for k in range(-VENTANA_EMPUJE, VENTANA_EMPUJE + 1))
                           / (2 * VENTANA_EMPUJE + 1) for c in (0, 1)] for i in range(n)]
            pts = [[p[0] + e[0], p[1] + e[1]] for p, e in zip(pts, empuje)]
        self.puntos = self._remuestrear([tuple(p) for p in pts], self.paso)

    def medio(self, i):
        return self.anchos[i % len(self.puntos)] / 2

    def _buscar_cercanos(self):
        pts = self.puntos
        n = len(pts)
        excluidos = set()
        for i, j in self.cruces:
            for k in (i, j):
                excluidos.update((k + d) % n for d in range(-40, 41))
        minimo_arco = int(ARCO_MINIMO / self.paso)
        self.alcance = ANCHO_MAX + 2 * ESCAPE + MURO
        celdas = {}
        for i, (px, py) in enumerate(pts):
            celdas.setdefault((int(px // self.alcance), int(py // self.alcance)), []).append(i)
        cercanos = []
        for a in range(n):
            lista = []
            if a not in excluidos:
                ax, ay = pts[a]
                cx, cy = int(ax // self.alcance), int(ay // self.alcance)
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        for b in celdas.get((cx + dx, cy + dy), ()):
                            if b in excluidos or min(abs(b - a), n - abs(b - a)) <= minimo_arco:
                                continue
                            d = math.hypot(pts[b][0] - ax, pts[b][1] - ay)
                            if d < self.alcance:
                                lista.append((b, d))
            cercanos.append(lista)
        return cercanos

    def _calcular_anchos(self):
        n = len(self.puntos)
        anchos = []
        for a in range(n):
            mejor = min((d for _, d in self.cercanos[a]), default=float('inf'))
            tope_curva = FACTOR_CURVA * self.radio_de_curva(a)
            anchos.append(max(self.limite_ancho_min, min(self.limite_ancho_max, mejor - MURO, tope_curva)))
        return suavizar_hacia_abajo(anchos, CAMBIO_ANCHO)

    def radio_de_curva(self, i):
        n = len(self.puntos)
        a, b, c = self.puntos[(i - VENTANA_CURVA) % n], self.puntos[i], self.puntos[(i + VENTANA_CURVA) % n]
        doble_area = abs((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]))
        if doble_area < 1e-9:
            return float('inf')
        return math.dist(a, b) * math.dist(b, c) * math.dist(c, a) / (2 * doble_area)

    def _calcular_escapes(self):
        n = len(self.puntos)
        escapes = []
        for a in range(n):
            e = ESCAPE
            for b, d in self.cercanos[a]:
                e = min(e, (d - self.anchos[a] / 2 - self.anchos[b] / 2 - MURO) / 2)
            escapes.append(max(0.0, e))
        return suavizar_hacia_abajo(escapes, CAMBIO_ESCAPE)

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
        n = len(self.puntos)
        f = s / self.paso
        i = math.floor(f)
        r = f - i
        a, b = self.puntos[i % n], self.puntos[(i + 1) % n]
        return (a[0] + (b[0] - a[0]) * r, a[1] + (b[1] - a[1]) * r), self.tangente(i % n)


def suavizar_hacia_abajo(valores, cambio):
    n = len(valores)
    for _ in range(2):
        for a in range(n):
            valores[a] = min(valores[a], valores[a - 1] + cambio)
        for a in range(n - 1, -1, -1):
            valores[a] = min(valores[a], valores[(a + 1) % n] + cambio)
    return valores


def encuadrar(c):
    xs = [p[0] for p in c.puntos]
    ys = [p[1] for p in c.puntos]
    origen_x = min(xs) - MARGEN_MUNDO
    origen_y = min(ys) - MARGEN_MUNDO
    c.desplazar(-origen_x, -origen_y)
    return math.ceil(max(xs) + MARGEN_MUNDO - origen_x), math.ceil(max(ys) + MARGEN_MUNDO - origen_y)


def _intersecan(p1, p2, p3, p4):
    d = (p2[0] - p1[0]) * (p4[1] - p3[1]) - (p2[1] - p1[1]) * (p4[0] - p3[0])
    if abs(d) < 1e-9:
        return False
    t = ((p3[0] - p1[0]) * (p4[1] - p3[1]) - (p3[1] - p1[1]) * (p4[0] - p3[0])) / d
    u = ((p3[0] - p1[0]) * (p2[1] - p1[1]) - (p3[1] - p1[1]) * (p2[0] - p1[0])) / d
    return 0 <= t < 1 and 0 <= u < 1


def detectar_cruces(c):
    pts = c.puntos
    n = len(pts)
    celdas = {}
    for i, p in enumerate(pts):
        celdas.setdefault((int(p[0] // CELDA_BUSQUEDA), int(p[1] // CELDA_BUSQUEDA)), []).append(i)
    cruces = []
    for i in range(n):
        a, b = pts[i], pts[(i + 1) % n]
        cx, cy = int(a[0] // CELDA_BUSQUEDA), int(a[1] // CELDA_BUSQUEDA)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for j in celdas.get((cx + dx, cy + dy), []):
                    if j <= i or min(j - i, n - (j - i)) <= 3:
                        continue
                    if _intersecan(a, b, pts[j], pts[(j + 1) % n]):
                        if not any(abs(i - i2) < 8 and abs(j - j2) < 8 for i2, j2 in cruces):
                            cruces.append((i, j))
    return cruces


def calcular_checkpoints(c):
    cantidad = max(8, round(c.largo / SEPARACION_CHECKPOINTS))
    resultado = []
    for k in range(cantidad):
        s = c.largo * k / cantidad
        (x, y), _ = c.punto_a_distancia(s)
        lado = 2 * c.medio(round(s / c.paso)) + HOLGURA_CHECKPOINT
        resultado.append((x - lado / 2, y - lado / 2, lado))
    return resultado


def calcular_largada(c):
    posiciones = []
    for i in range(JUGADORES_MAX):
        normales = min(i, LARGADA_AUTOS_CON_ESCALON - 1)
        atras = LARGADA_ATRAS + LARGADA_ESCALON * normales + LARGADA_ESCALON_APRETADO * (i - normales)
        (x, y), (tx, ty) = c.punto_a_distancia(-atras)
        lateral = min(LARGADA_LATERAL, c.medio(round(-atras / c.paso)) - LARGADA_BORDE)
        lado = lateral if i % 2 == 0 else -lateral
        posiciones.append((x - ty * lado, y + tx * lado))
    (_, _), (tx, ty) = c.punto_a_distancia(0)
    angulo = math.degrees(math.atan2(-ty, tx))
    return posiciones, angulo


def validar(c, checkpoints, largada):
    avisos = []
    n = len(c.puntos)
    for i, (x, y) in enumerate(largada):
        k = min(range(n), key=lambda j: math.hypot(c.puntos[j][0] - x, c.puntos[j][1] - y))
        if math.hypot(c.puntos[k][0] - x, c.puntos[k][1] - y) > c.medio(k):
            avisos.append('el lugar de largada %d no cae sobre la pista' % (i + 1))
    pegados = sum(1 for a in range(n) for b, d in c.cercanos[a] if b > a and d - c.medio(a) - c.medio(b) < MURO)
    if pegados:
        avisos.append('%d pares de muestras tienen los bordes de pista a menos de %d px (no entra el muro)' % (pegados, MURO))
    cercania = int((ANCHO_MAX + 2 * HOLGURA_CHECKPOINT) * 1.5 / c.paso)
    conflictos = 0
    for k, (x, y, lado) in enumerate(checkpoints):
        centro_i = round(n * k / len(checkpoints))
        for j in range(0, n, 2):
            d = min((j - centro_i) % n, (centro_i - j) % n)
            if d > cercania:
                px, py = c.puntos[j]
                if x <= px <= x + lado and y <= py <= y + lado:
                    ox, oy = c.tangente(j)
                    sx, sy = c.tangente(centro_i % n)
                    if ox * sx + oy * sy > 0.3:
                        conflictos += 1
                        break
    if conflictos:
        avisos.append('%d de %d checkpoints incluyen otro tramo de la pista' % (conflictos, len(checkpoints)))
    return avisos


def numero(v):
    texto = ('%.1f' % v).rstrip('0').rstrip('.')
    return texto if texto not in ('-0', '') else '0'


def escribir_json(ruta, c, ancho_mundo, alto_mundo, checkpoints, largada, angulo):
    def y_juego(y):
        return alto_mundo - y

    lineas = ['{']
    lineas.append(' "version": %d,' % VERSION_FORMATO)
    lineas.append(' "nombre": "%s",' % c.nombre)
    lineas.append(' "origen": "f1-circuits-svg (Jules Roy, CC BY 4.0)",')
    lineas.append(' "callejero": %s,' % ('true' if c.callejero else 'false'))
    lineas.append(' "ancho": %d,' % ancho_mundo)
    lineas.append(' "alto": %d,' % alto_mundo)
    lineas.append(' "anguloLargada": %s,' % numero(angulo))
    lineas.append(' "muro": %s,' % numero(MURO))
    lineas.append(' "paso": %s,' % numero(c.paso))
    lineas.append(' "linea": [%s],' % ','.join('[%s,%s]' % (numero(x), numero(y_juego(y))) for x, y in c.puntos))
    lineas.append(' "anchos": [%s],' % ','.join(numero(v) for v in c.anchos))
    lineas.append(' "escapes": [%s],' % ','.join(numero(v) for v in c.escapes))
    lineas.append(' "cruces": [%s],' % ','.join('{"tunel":%d,"puente":%d}' % (i, j) for i, j in c.cruces))
    lineas.append(' "checkpoints": [')
    lineas.append(',\n'.join('  {"orden":%d,"x":%s,"y":%s,"ancho":%s,"alto":%s}'
                             % (orden, numero(x), numero(y_juego(y + lado)), numero(lado), numero(lado))
                             for orden, (x, y, lado) in enumerate(checkpoints)))
    lineas.append(' ],')
    lineas.append(' "largada": [')
    lineas.append(',\n'.join('  {"posicion":%d,"x":%s,"y":%s}' % (i + 1, numero(x), numero(y_juego(y)))
                             for i, (x, y) in enumerate(largada)))
    lineas.append(' ]')
    lineas.append('}')
    with open(ruta, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lineas) + '\n')


ESCALA_VISTA = 0.25
FONDO_VISTA = (40, 105, 45)
PASTO_VISTA = (76, 150, 70)
PISTA_VISTA = (62, 62, 72)
MURO_VISTA = (205, 55, 55)
META_VISTA = (245, 245, 245)


def escribir_png(ruta, ancho, alto, filas):
    crudo = b''.join(b'\x00' + bytes(fila) for fila in filas)

    def trozo(tipo, datos):
        cuerpo = tipo + datos
        return struct.pack('>I', len(datos)) + cuerpo + struct.pack('>I', zlib.crc32(cuerpo) & 0xffffffff)

    with open(ruta, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(trozo(b'IHDR', struct.pack('>IIBBBBB', ancho, alto, 8, 2, 0, 0, 0)))
        f.write(trozo(b'IDAT', zlib.compress(crudo, 9)))
        f.write(trozo(b'IEND', b''))


def escribir_vista(ruta, c, ancho_mundo, alto_mundo, checkpoints, largada):
    ancho, alto = int(ancho_mundo * ESCALA_VISTA) + 1, int(alto_mundo * ESCALA_VISTA) + 1
    filas = [bytearray(bytes(FONDO_VISTA) * ancho) for _ in range(alto)]

    def disco(x, y, radio, color):
        cx, cy, r = x * ESCALA_VISTA, y * ESCALA_VISTA, max(radio * ESCALA_VISTA, 0.6)
        pintura = bytes(color)
        for yy in range(max(0, int(cy - r)), min(alto, int(cy + r) + 1)):
            medio = math.sqrt(max(0.0, r * r - (yy + 0.5 - cy) ** 2))
            x0, x1 = max(0, int(cx - medio)), min(ancho, int(cx + medio) + 1)
            if x1 > x0:
                filas[yy][x0 * 3:x1 * 3] = pintura * (x1 - x0)

    for capa in range(3):
        for i, (x, y) in enumerate(c.puntos):
            medio = c.medio(i)
            if capa == 0:
                disco(x, y, medio + c.escapes[i] + MURO, MURO_VISTA)
            elif capa == 1:
                disco(x, y, medio + c.escapes[i], PASTO_VISTA)
            else:
                disco(x, y, medio, PISTA_VISTA)
    (px, py), (tx, ty) = c.punto_a_distancia(0)
    paso = 1 / ESCALA_VISTA
    for k in range(int(-c.medio(0) / paso), int(c.medio(0) / paso) + 1):
        disco(px - ty * k * paso, py + tx * k * paso, 5, META_VISTA)
    for x, y, lado in checkpoints:
        disco(x + lado / 2, y + lado / 2, 6, (250, 220, 40))
    for x, y in largada:
        disco(x, y, 8, (60, 140, 255))
    escribir_png(ruta, ancho, alto, filas)


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
        ancho_mundo, alto_mundo = encuadrar(c)
        checkpoints = calcular_checkpoints(c)
        largada, angulo = calcular_largada(c)
        avisos = validar(c, checkpoints, largada)
        escribir_json(os.path.join(args.salida, c.nombre + '.json'), c, ancho_mundo, alto_mundo, checkpoints, largada, angulo)
        if args.vista:
            escribir_vista(os.path.join(args.vista, c.nombre + '.png'), c, ancho_mundo, alto_mundo, checkpoints, largada)
        print('%-18s %4dx%-4d px  pista de %3.0f a %3.0f px  vuelta %5.0f px (~%2.0f s)  %2d checkpoints%s%s'
              % (c.nombre, ancho_mundo, alto_mundo, c.ancho_minimo, c.ancho_maximo, c.largo, c.largo / VELOCIDAD_MEDIA,
                 len(checkpoints), '  cruces: %d' % len(c.cruces) if c.cruces else '',
                 ''.join('\n    AVISO: ' + a for a in avisos)))


if __name__ == '__main__':
    main()
