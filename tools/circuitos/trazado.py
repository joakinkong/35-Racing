"""Lee los SVG de f1-circuits-svg y los convierte en listas de puntos.

Cada SVG "detailed" tiene tres <path>:
  1. la línea central del circuito (trazo de 20), cerrada;
  2. la línea de largada (relleno blanco), un cuadrilátero;
  3. la flecha de sentido (trazo de 2): un ángulo cuya punta es el vértice del medio.
Los paths 2 y 3 pueden traer un atributo transform.
"""
import math
import re

NUMERO = re.compile(r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?')
TOKEN = re.compile(r'[MmLlHhVvCcSsQqTtAaZz]|' + NUMERO.pattern)
CANTIDAD_ARGS = {'m': 2, 'l': 2, 'h': 1, 'v': 1, 'c': 6, 's': 4, 'q': 4, 't': 2, 'a': 7, 'z': 0}


def leer_paths(texto_svg):
    """Devuelve una lista de (d, estilo, transform) de cada <path> del SVG."""
    paths = []
    for etiqueta in re.findall(r'<path\b([^>]*)/?>', texto_svg):
        d = re.search(r'\bd="([^"]*)"', etiqueta)
        estilo = re.search(r'\bstyle="([^"]*)"', etiqueta)
        transform = re.search(r'\btransform="([^"]*)"', etiqueta)
        paths.append((d.group(1), estilo.group(1) if estilo else '', transform.group(1) if transform else ''))
    return paths


def _bezier(puntos, t):
    while len(puntos) > 1:
        puntos = [((1 - t) * a[0] + t * b[0], (1 - t) * a[1] + t * b[1]) for a, b in zip(puntos, puntos[1:])]
    return puntos[0]


def _arco(p0, rx, ry, rot, grande, barrido, p1):
    """Aproxima un arco SVG (conversión de extremos a centro, apéndice F.6 de la especificación)."""
    if rx == 0 or ry == 0 or p0 == p1:
        return [p1]
    rx, ry = abs(rx), abs(ry)
    fi = math.radians(rot)
    cos_f, sin_f = math.cos(fi), math.sin(fi)
    dx, dy = (p0[0] - p1[0]) / 2, (p0[1] - p1[1]) / 2
    x1 = cos_f * dx + sin_f * dy
    y1 = -sin_f * dx + cos_f * dy
    escala = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
    if escala > 1:
        rx *= math.sqrt(escala)
        ry *= math.sqrt(escala)
    num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
    den = rx * rx * y1 * y1 + ry * ry * x1 * x1
    coef = math.sqrt(max(0.0, num / den)) * (-1 if grande == barrido else 1)
    cx1 = coef * rx * y1 / ry
    cy1 = -coef * ry * x1 / rx
    cx = cos_f * cx1 - sin_f * cy1 + (p0[0] + p1[0]) / 2
    cy = sin_f * cx1 + cos_f * cy1 + (p0[1] + p1[1]) / 2

    def angulo(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = angulo(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry)
    dt = angulo((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
    if not barrido and dt > 0:
        dt -= 2 * math.pi
    elif barrido and dt < 0:
        dt += 2 * math.pi
    pasos = max(4, int(abs(dt) / (math.pi / 36)))
    salida = []
    for i in range(1, pasos + 1):
        t = t1 + dt * i / pasos
        x = rx * math.cos(t)
        y = ry * math.sin(t)
        salida.append((cos_f * x - sin_f * y + cx, sin_f * x + cos_f * y + cy))
    return salida


def parsear_path(d, pasos_curva=24):
    """Convierte el atributo d en una lista de subtrazados; cada uno es (puntos, cerrado)."""
    tokens = TOKEN.findall(d)
    i = 0
    subtrazados = []
    actual = []
    inicio = pos = (0.0, 0.0)
    ultimo_control = None
    ultimo_cmd = ''
    cmd = ''
    while i < len(tokens):
        if tokens[i].isalpha():
            cmd = tokens[i]
            i += 1
            if cmd in 'Zz':
                if actual:
                    subtrazados.append((actual, True))
                    actual = []
                pos = inicio
                ultimo_control = None
                ultimo_cmd = 'z'
                continue
        minus = cmd.lower()
        rel = cmd.islower()
        n = CANTIDAD_ARGS[minus]
        args = [float(t) for t in tokens[i:i + n]]
        if len(args) < n:
            raise ValueError('path truncado: ' + d[:40])
        i += n
        ox, oy = pos if rel else (0.0, 0.0)
        control = None
        if minus == 'm':
            if actual:
                subtrazados.append((actual, False))
            pos = (args[0] + ox, args[1] + oy)
            inicio = pos
            actual = [pos]
            cmd = 'l' if rel else 'L'  # los pares siguientes de un M son L implícitos
        elif minus == 'l':
            pos = (args[0] + ox, args[1] + oy)
            actual.append(pos)
        elif minus == 'h':
            pos = (args[0] + (pos[0] if rel else 0), pos[1])
            actual.append(pos)
        elif minus == 'v':
            pos = (pos[0], args[0] + (pos[1] if rel else 0))
            actual.append(pos)
        elif minus in 'cs':
            if minus == 'c':
                c1 = (args[0] + ox, args[1] + oy)
                c2 = (args[2] + ox, args[3] + oy)
                fin = (args[4] + ox, args[5] + oy)
            else:
                if ultimo_cmd in ('c', 's') and ultimo_control:
                    c1 = (2 * pos[0] - ultimo_control[0], 2 * pos[1] - ultimo_control[1])
                else:
                    c1 = pos
                c2 = (args[0] + ox, args[1] + oy)
                fin = (args[2] + ox, args[3] + oy)
            for k in range(1, pasos_curva + 1):
                actual.append(_bezier([pos, c1, c2, fin], k / pasos_curva))
            control = c2
            pos = fin
        elif minus in 'qt':
            if minus == 'q':
                c1 = (args[0] + ox, args[1] + oy)
                fin = (args[2] + ox, args[3] + oy)
            else:
                if ultimo_cmd in ('q', 't') and ultimo_control:
                    c1 = (2 * pos[0] - ultimo_control[0], 2 * pos[1] - ultimo_control[1])
                else:
                    c1 = pos
                fin = (args[0] + ox, args[1] + oy)
            for k in range(1, pasos_curva + 1):
                actual.append(_bezier([pos, c1, fin], k / pasos_curva))
            control = c1
            pos = fin
        elif minus == 'a':
            fin = (args[5] + ox, args[6] + oy)
            actual.extend(_arco(pos, args[0], args[1], args[2], int(args[3]), int(args[4]), fin))
            pos = fin
        ultimo_control = control
        ultimo_cmd = minus
    if actual:
        subtrazados.append((actual, False))
    return subtrazados


def _multiplicar(a, b):
    return (a[0] * b[0] + a[2] * b[1], a[1] * b[0] + a[3] * b[1],
            a[0] * b[2] + a[2] * b[3], a[1] * b[2] + a[3] * b[3],
            a[0] * b[4] + a[2] * b[5] + a[4], a[1] * b[4] + a[3] * b[5] + a[5])


def matriz_de_transform(texto):
    """Compone una lista de transformaciones SVG en una matriz (a, b, c, d, e, f)."""
    m = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    for nombre, args in re.findall(r'(\w+)\s*\(([^)]*)\)', texto or ''):
        v = [float(x) for x in NUMERO.findall(args)]
        if nombre == 'translate':
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif nombre == 'scale':
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif nombre == 'rotate':
            a = math.radians(v[0])
            c, s = math.cos(a), math.sin(a)
            t = (c, s, -s, c, 0, 0)
            if len(v) == 3:
                t = _multiplicar(_multiplicar((1, 0, 0, 1, v[1], v[2]), t), (1, 0, 0, 1, -v[1], -v[2]))
        elif nombre == 'matrix':
            t = tuple(v)
        else:
            raise ValueError('transform no soportado: ' + nombre)
        m = _multiplicar(m, t)
    return m


def aplicar(m, punto):
    x, y = punto
    return (m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5])


class Trazado:
    """Línea central cerrada (en unidades del SVG, eje Y hacia abajo), largada y sentido."""

    def __init__(self, puntos, largada, sentido):
        self.puntos = puntos      # polilínea cerrada, sin repetir el primer punto al final
        self.largada = largada    # centro de la línea de largada
        self.sentido = sentido    # vector unitario en que se recorre el circuito en la largada


def resamplear(puntos, paso):
    """Reparte puntos a distancia pareja a lo largo de una polilínea cerrada."""
    cerrada = puntos + [puntos[0]]
    acumulado = [0.0]
    for a, b in zip(cerrada, cerrada[1:]):
        acumulado.append(acumulado[-1] + math.dist(a, b))
    total = acumulado[-1]
    cantidad = max(8, round(total / paso))
    salida = []
    j = 0
    for k in range(cantidad):
        objetivo = total * k / cantidad
        while acumulado[j + 1] < objetivo:
            j += 1
        largo = acumulado[j + 1] - acumulado[j]
        f = (objetivo - acumulado[j]) / largo if largo else 0.0
        a, b = cerrada[j], cerrada[j + 1]
        salida.append((a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
    return salida, total


def leer_trazado(ruta_svg, paso=1.0):
    with open(ruta_svg, encoding='utf-8') as f:
        paths = leer_paths(f.read())
    centro = largada = flecha = None
    for d, estilo, transform in paths:
        if 'stroke-width:20' in estilo and centro is None:
            subs = parsear_path(d)
            puntos = max((s[0] for s in subs), key=len)
            if len(puntos) > 1 and math.dist(puntos[0], puntos[-1]) < 1e-6:
                puntos = puntos[:-1]
            centro = puntos
        elif 'fill:#fff' in estilo:
            m = matriz_de_transform(transform)
            pts = [aplicar(m, p) for p in parsear_path(d)[0][0]]
            largada = (sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))
        elif 'stroke-width:2;' in estilo and flecha is None:
            m = matriz_de_transform(transform)
            flecha = [aplicar(m, p) for p in parsear_path(d)[0][0]]
    if centro is None or largada is None or flecha is None:
        raise ValueError('faltan elementos en ' + ruta_svg)
    # Flecha: el vértice del medio es la punta; los extremos son las alas.
    punta = flecha[len(flecha) // 2]
    alas = ((flecha[0][0] + flecha[-1][0]) / 2, (flecha[0][1] + flecha[-1][1]) / 2)
    vx, vy = punta[0] - alas[0], punta[1] - alas[1]
    largo = math.hypot(vx, vy)
    muestreo, total = resamplear(centro, paso)
    return Trazado(muestreo, largada, (vx / largo, vy / largo)), total
