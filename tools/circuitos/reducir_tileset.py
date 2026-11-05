"""Reduce a la mitad el tileset de 32 px (assets/circuitos/tileset.png) y deja tileset16.png, con tiles de 16 px.

Los circuitos generados usan tiles de 16 px para poder poner muros finos entre tramos de pista pegados. Cada píxel
nuevo es el promedio de un bloque de 2x2 del original, así que los cuadros del muro y de la meta (de 8 px) pasan a 4 px
sin deformarse.

Uso:
    python tools/circuitos/reducir_tileset.py
"""
import os
import struct
import zlib

CARPETA = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'assets', 'circuitos')


def leer_png_rgb(ruta):
    datos = open(ruta, 'rb').read()
    assert datos[:8] == b'\x89PNG\r\n\x1a\n'
    posicion = 8
    idat = b''
    while posicion < len(datos):
        largo, tipo = struct.unpack('>I4s', datos[posicion:posicion + 8])
        cuerpo = datos[posicion + 8:posicion + 8 + largo]
        if tipo == b'IHDR':
            ancho, alto, profundidad, color, _, _, entrelazado = struct.unpack('>IIBBBBB', cuerpo)
            assert profundidad == 8 and color in (2, 6) and entrelazado == 0, 'solo PNG RGB o RGBA de 8 bits'
        elif tipo == b'IDAT':
            idat += cuerpo
        posicion += 12 + largo
    canales = 3 if color == 2 else 4
    crudo = zlib.decompress(idat)
    paso = ancho * canales
    filas = []
    anterior = bytearray(paso)
    for y in range(alto):
        filtro = crudo[y * (paso + 1)]
        fila = bytearray(crudo[y * (paso + 1) + 1:(y + 1) * (paso + 1)])
        for i in range(paso):
            a = fila[i - canales] if i >= canales else 0
            b = anterior[i]
            c = anterior[i - canales] if i >= canales else 0
            if filtro == 1:
                fila[i] = (fila[i] + a) & 255
            elif filtro == 2:
                fila[i] = (fila[i] + b) & 255
            elif filtro == 3:
                fila[i] = (fila[i] + (a + b) // 2) & 255
            elif filtro == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                predictor = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                fila[i] = (fila[i] + predictor) & 255
        filas.append(fila)
        anterior = fila
    return ancho, alto, canales, filas


def escribir_png(ruta, ancho, alto, canales, filas):
    def trozo(tipo, cuerpo):
        bloque = tipo + cuerpo
        return struct.pack('>I', len(cuerpo)) + bloque + struct.pack('>I', zlib.crc32(bloque) & 0xffffffff)

    crudo = b''.join(b'\x00' + bytes(f) for f in filas)
    with open(ruta, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(trozo(b'IHDR', struct.pack('>IIBBBBB', ancho, alto, 8, 2 if canales == 3 else 6, 0, 0, 0)))
        f.write(trozo(b'IDAT', zlib.compress(crudo, 9)))
        f.write(trozo(b'IEND', b''))


def main():
    ancho, alto, canales, filas = leer_png_rgb(os.path.join(CARPETA, 'tileset.png'))
    nuevas = []
    for y in range(0, alto - 1, 2):
        fila = bytearray()
        for x in range(0, ancho - 1, 2):
            for c in range(canales):
                suma = sum(filas[y + dy][(x + dx) * canales + c] for dy in (0, 1) for dx in (0, 1))
                fila.append((suma + 2) // 4)
        nuevas.append(fila)
    destino = os.path.join(CARPETA, 'tileset16.png')
    escribir_png(destino, ancho // 2, alto // 2, canales, nuevas)
    print('%s: %dx%d' % (destino, ancho // 2, alto // 2))


if __name__ == '__main__':
    main()
