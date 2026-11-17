import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

GROTESK = "SpaceGrotesk.ttf"
INTER = "Inter.ttf"

ANCHO_ATLAS = 1024
SEPARACION = 2
INCLINACION = 0.21
SOBREMUESTREO = 4

LATINOS = "".join(chr(c) for c in range(0xA1, 0x100))
TODOS = "".join(chr(c) for c in range(0x20, 0x7F)) + LATINOS + "–—…"
CARTEL = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZÓ¡!"

ESTILOS = [
    ("titulo", GROTESK, [700], 72, True, -2, False, TODOS),
    ("marca", GROTESK, [700], 30, True, -1, False, TODOS),
    ("encabezado", GROTESK, [700], 40, False, -1, False, TODOS),
    ("subtitulo", GROTESK, [700], 22, False, 0, False, TODOS),
    ("etiqueta", GROTESK, [700], 15, False, 2, False, TODOS),
    ("numeros", GROTESK, [700], 19, False, 0, True, TODOS),
    ("posicion", GROTESK, [700], 24, True, 0, True, TODOS),
    ("cartel", GROTESK, [700], 96, True, -3, False, CARTEL),
    ("normal", INTER, [20, 500], 19, False, 0, False, TODOS),
    ("chico", INTER, [14, 400], 15, False, 0, False, TODOS),
]


def cargar(carpeta, archivo, ejes, tamanio):
    fuente = ImageFont.truetype(str(carpeta / archivo), tamanio)
    fuente.set_variation_by_axes(ejes)
    return fuente


def glifo_recto(fuente, caracter):
    izquierda, arriba, derecha, abajo = fuente.getbbox(caracter, anchor="ls")
    if derecha <= izquierda or abajo <= arriba:
        return None
    imagen = Image.new("L", (derecha - izquierda, abajo - arriba), 0)
    ImageDraw.Draw(imagen).text((-izquierda, -arriba), caracter, fill=255, font=fuente, anchor="ls")
    return imagen, izquierda, arriba


def glifo_inclinado(fuente_grande, caracter):
    izquierda, arriba, derecha, abajo = fuente_grande.getbbox(caracter, anchor="ls")
    if derecha <= izquierda or abajo <= arriba:
        return None
    margen = int(max(abs(arriba), abs(abajo)) * INCLINACION) + SOBREMUESTREO
    origen_x = (margen - izquierda + SOBREMUESTREO) // SOBREMUESTREO * SOBREMUESTREO
    linea_base = (-arriba + SOBREMUESTREO) // SOBREMUESTREO * SOBREMUESTREO
    ancho = origen_x + derecha + margen
    alto = linea_base + abajo + SOBREMUESTREO
    ancho += -ancho % SOBREMUESTREO
    alto += -alto % SOBREMUESTREO
    recto = Image.new("L", (ancho, alto), 0)
    ImageDraw.Draw(recto).text((origen_x, linea_base), caracter, fill=255, font=fuente_grande, anchor="ls")
    inclinado = recto.transform(
        (ancho, alto), Image.AFFINE, (1, INCLINACION, -INCLINACION * linea_base, 0, 1, 0), Image.BICUBIC)
    reducido = inclinado.resize((ancho // SOBREMUESTREO, alto // SOBREMUESTREO), Image.BOX)
    caja = reducido.getbbox()
    if caja is None:
        return None
    return (reducido.crop(caja), caja[0] - origen_x // SOBREMUESTREO, caja[1] - linea_base // SOBREMUESTREO)


def generar(estilo, entrada, salida):
    nombre, archivo, ejes, tamanio, cursiva, espaciado, digitos_fijos, caracteres = estilo
    fuente = cargar(entrada, archivo, ejes, tamanio)
    grande = cargar(entrada, archivo, ejes, tamanio * SOBREMUESTREO) if cursiva else None
    ascenso, descenso = fuente.getmetrics()
    ancho_digito = max(fuente.getlength(str(d)) for d in range(10))

    glifos = []
    for caracter in caracteres:
        datos = glifo_inclinado(grande, caracter) if cursiva else glifo_recto(fuente, caracter)
        avance = fuente.getlength(caracter)
        centrado = 0
        if digitos_fijos and caracter.isdigit():
            centrado = round((ancho_digito - avance) / 2)
            avance = ancho_digito
        glifos.append((caracter, datos, round(avance) + espaciado, centrado))

    x = y = SEPARACION
    alto_estante = 0
    ubicados = []
    for caracter, datos, avance, centrado in glifos:
        if datos is None:
            ubicados.append((caracter, None, 0, 0, avance, centrado))
            continue
        ancho, alto = datos[0].size
        if x + ancho + SEPARACION > ANCHO_ATLAS:
            x = SEPARACION
            y += alto_estante + SEPARACION
            alto_estante = 0
        ubicados.append((caracter, datos, x, y, avance, centrado))
        x += ancho + SEPARACION
        alto_estante = max(alto_estante, alto)
    alto_atlas = 1
    while alto_atlas < y + alto_estante + SEPARACION:
        alto_atlas *= 2

    alfa = Image.new("L", (ANCHO_ATLAS, alto_atlas), 0)
    lineas = [
        f'info face="{nombre}" size={tamanio} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 '
        f"padding=0,0,0,0 spacing={SEPARACION},{SEPARACION}",
        f"common lineHeight={ascenso + descenso} base={ascenso} scaleW={ANCHO_ATLAS} scaleH={alto_atlas} pages=1 packed=0",
        f'page id=0 file="{nombre}.png"',
        f"chars count={len(ubicados)}",
    ]
    for caracter, datos, gx, gy, avance, centrado in ubicados:
        if datos is None:
            ancho = alto = desde_x = desde_y = 0
        else:
            imagen, desde_x, arriba = datos
            alfa.paste(imagen, (gx, gy))
            ancho, alto = imagen.size
            desde_x += centrado
            desde_y = ascenso + arriba
        lineas.append(
            f"char id={ord(caracter)} x={gx} y={gy} width={ancho} height={alto} xoffset={desde_x} yoffset={desde_y} "
            f"xadvance={avance} page=0 chnl=15")
    blanco = Image.new("L", alfa.size, 255)
    Image.merge("LA", (blanco, alfa)).save(salida / f"{nombre}.png", optimize=True)
    (salida / f"{nombre}.fnt").write_text("\n".join(lineas) + "\n", encoding="utf-8")
    print(f"{nombre}: {len(ubicados)} caracteres, atlas {ANCHO_ATLAS}x{alto_atlas}")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    entrada = Path(sys.argv[1])
    salida = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / "assets" / "fuentes"
    salida.mkdir(parents=True, exist_ok=True)
    for estilo in ESTILOS:
        generar(estilo, entrada, salida)


if __name__ == "__main__":
    main()
