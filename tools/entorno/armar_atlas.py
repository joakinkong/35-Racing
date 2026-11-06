#!/usr/bin/env python3
"""Arma assets/entorno a partir de Modern Exteriors (LimeZu, https://limezu.itch.io/modernexteriors).

Uso (desde la raíz del proyecto):  python tools/entorno/armar_atlas.py [carpeta del pack]

Junta los sprites de la lista en una sola imagen, a la mitad de su tamaño, con un archivo .atlas en el formato de LibGDX.
El prefijo de cada nombre (arbol_, edificio_, ...) es el grupo que usa DisposicionEntorno: para sumar un sprite alcanza con
agregarlo acá con ese prefijo. Las baldosas del fondo se copian aparte, porque se repiten como textura. Necesita Pillow.
"""
import sys
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parents[2]
SALIDA = RAIZ / 'assets' / 'entorno'
PACK_POR_DEFECTO = Path.home() / 'Desktop' / 'Modern_Exteriors_48x48'
ANCHO_ATLAS = 2048
RELLENO = 2  # px transparentes entre sprites, para que el filtro no traiga pedazos del vecino


def pack(carpeta, prefijo, *nombres):
    return [(carpeta, prefijo, n) for n in nombres]


GEN = '4_Generic_Building_Singles_48x48'
MER = '9_Shopping_Center_and_Markets_Singles_48x48'
CAS = '24_Additional_Houses_Singles_48x48'
VIL = '7_Villas_Singles_48x48'
GAR = '17_Garden_Singles_48x48'
PRO = '3_City_Props_Singles_48x48'
ESC = '13_School_Singles_48x48'
VEH = '10_Vehicles_Singles_48x48'
CAM = '11_Camping_Singles_48x48'
TER = '1_Terrains_and_Fences_Singles_48x48'
CIU = '2_City_Terrains_Singles_48x48'

SPRITES = {}
for nombre, carpeta, archivo in [
    ('edificio_condo1', GEN, 'ME_Singles_Generic_Building_48x48_Condo_1_1'),
    ('edificio_condo2', GEN, 'ME_Singles_Generic_Building_48x48_Condo_2_1'),
    ('edificio_condo3', GEN, 'ME_Singles_Generic_Building_48x48_Condo_5_1'),
    ('edificio_condo4', GEN, 'ME_Singles_Generic_Building_48x48_Condo_5_2'),
    ('edificio_condo5', GEN, 'ME_Singles_Generic_Building_48x48_Condo_6'),
    ('edificio_ferreteria', GEN, 'ME_Singles_Generic_Building_48x48_Hardware_Store'),
    ('edificio_shopping', MER, 'ME_Singles_Shopping_Center_and_Markets_48x48_Mall_1'),
    ('edificio_mercado1', MER, 'ME_Singles_Shopping_Center_and_Markets_48x48_Market_Small_1'),
    ('edificio_mercado2', MER, 'ME_Singles_Shopping_Center_and_Markets_48x48_Market_Big_1'),
    ('edificio_mercado3', MER, 'ME_Singles_Shopping_Center_and_Markets_48x48_Market_Medium_1'),
    ('edificio_casa1', CAS, '24_Additional_Houses_Terraced_House_1_48x48'),
    ('edificio_casa2', CAS, '24_Additional_Houses_Terraced_House_2_48x48'),
    ('edificio_casa3', CAS, '24_Additional_Houses_Terraced_House_3_48x48'),
    ('edificio_villa1', VIL, 'ME_Singles_Villas_48x48_Villa_1'),
    ('edificio_villa2', VIL, 'ME_Singles_Villas_48x48_Villa_2'),
    ('arbol_1', VIL, 'ME_Singles_Villas_48x48_Tree_1'),
    ('arbol_2', VIL, 'ME_Singles_Villas_48x48_Tree_3'),
    ('arbol_3', VIL, 'ME_Singles_Villas_48x48_Tree_4'),
    ('arbusto_1', GAR, 'ME_Singles_Garden_48x48_Bush_18'),
    ('arbusto_2', GAR, 'ME_Singles_Garden_48x48_Bush_19'),
    ('arbusto_3', GAR, 'ME_Singles_Garden_48x48_Bush_22'),
    ('cartel_1', PRO, 'ME_Singles_City_Props_48x48_Billboard_2'),
    ('cartel_2', PRO, 'ME_Singles_City_Props_48x48_Billboard_3'),
    ('farol_1', PRO, 'ME_Singles_City_Props_48x48_Street_Lamp_5'),
    ('barrera_1', PRO, 'ME_Singles_City_Props_48x48_Traffic_Barrier_1'),
    ('tribuna_abajo', ESC, 'ME_Singles_School_48x48_Stands_1'),
    ('tribuna_arriba', ESC, 'ME_Singles_School_48x48_Stands_1'),
    ('tribuna_izq', ESC, 'ME_Singles_School_48x48_Stands_2'),
    ('tribuna_der', ESC, 'ME_Singles_School_48x48_Stands_3'),
    ('neumatico_1', CAM, 'ME_Singles_Camping_48x48_Tire_1'),
    ('ambulancia_der', VEH, 'ME_Singles_Vehicles_48x48_Ambulance_Right_1'),
    ('ambulancia_izq', VEH, 'ME_Singles_Vehicles_48x48_Ambulance_Left_1'),
    ('ambulancia_arr', VEH, 'ME_Singles_Vehicles_48x48_Ambulance_Up_1'),
    ('ambulancia_aba', VEH, 'ME_Singles_Vehicles_48x48_Ambulance_Down_1'),
    ('valla_h', TER, 'ME_Singles_Terrains_and_Fences_48x48_Fence_1_2'),
    ('valla_v', TER, 'ME_Singles_Terrains_and_Fences_48x48_Fence_1_4'),
    ('muro_h', TER, 'ME_Singles_Terrains_and_Fences_48x48_Wall_2_2'),
    ('muro_v', TER, 'ME_Singles_Terrains_and_Fences_48x48_Wall_2_4'),
]:
    SPRITES[nombre] = (carpeta, archivo)
for i in range(1, 9):
    for sentido, en_ingles in (('der', 'Right'), ('izq', 'Left'), ('arr', 'Up'), ('aba', 'Down')):
        SPRITES[f'auto_{sentido}_{i}'] = (VEH, f'ME_Singles_Vehicles_48x48_Car_{en_ingles}_{i}')

# El pack trae gradas que miran abajo, a la izquierda y a la derecha. Las que miran arriba se sacan dando vuelta las primeras.
VOLTEAR_VERTICAL = {'tribuna_arriba'}

BALDOSAS = {
    'fondo_pasto': (TER, 'ME_Singles_Terrains_and_Fences_48x48_Grass_1_22'),
    'fondo_vereda': (CIU, 'ME_Singles_City_Terrains_48x48_Sidewalk_1_9'),
    'fondo_calle': (CIU, 'ME_Singles_City_Terrains_48x48_Asphalt_1_Variation_21'),
}


def abrir(pack_dir, carpeta, archivo):
    return Image.open(pack_dir / 'ME_Theme_Sorter_48x48' / carpeta / (archivo + '.png')).convert('RGBA')


def main():
    pack_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else PACK_POR_DEFECTO
    SALIDA.mkdir(parents=True, exist_ok=True)
    imagenes = {}
    for nombre, (carpeta, archivo) in SPRITES.items():
        im = abrir(pack_dir, carpeta, archivo)
        if nombre in VOLTEAR_VERTICAL:
            im = im.transpose(Image.FLIP_TOP_BOTTOM)
        # Se recorta lo transparente: el tamaño del sprite tiene que ser lo que se ve, para que no queden huecos al ubicarlo.
        im = im.crop(im.getbbox())
        imagenes[nombre] = im.resize((max(1, im.width // 2), max(1, im.height // 2)), Image.LANCZOS)

    x = y = RELLENO
    alto_fila = 0
    lugares = {}
    for nombre in sorted(imagenes, key=lambda n: -imagenes[n].height):
        im = imagenes[nombre]
        if x + im.width + RELLENO > ANCHO_ATLAS:
            x = RELLENO
            y += alto_fila + RELLENO
            alto_fila = 0
        lugares[nombre] = (x, y)
        x += im.width + RELLENO
        alto_fila = max(alto_fila, im.height)
    alto = y + alto_fila + RELLENO

    atlas = Image.new('RGBA', (ANCHO_ATLAS, alto), (0, 0, 0, 0))
    lineas = ['entorno.png', f'size: {ANCHO_ATLAS},{alto}', 'format: RGBA8888', 'filter: Linear,Linear', 'repeat: none']
    for nombre in sorted(lugares):
        lx, ly = lugares[nombre]
        atlas.paste(imagenes[nombre], (lx, ly))
        lineas += [nombre, f'  bounds: {lx}, {ly}, {imagenes[nombre].width}, {imagenes[nombre].height}']
    atlas.save(SALIDA / 'entorno.png', optimize=True)
    (SALIDA / 'entorno.atlas').write_text('\n'.join(lineas) + '\n', encoding='utf-8')
    for nombre, (carpeta, archivo) in BALDOSAS.items():
        abrir(pack_dir, carpeta, archivo).convert('RGB').save(SALIDA / (nombre + '.png'), optimize=True)
    # La vereda del pack tiene juntas muy marcadas y es muy clara: a esta escala distrae de la pista.
    vereda = Image.open(SALIDA / 'fondo_vereda.png')
    plano = Image.new('RGB', vereda.size, (150, 147, 140))
    Image.blend(vereda, plano, 0.75).save(SALIDA / 'fondo_vereda.png', optimize=True)
    print(f'{len(imagenes)} sprites, atlas de {ANCHO_ATLAS}x{alto}, {(SALIDA / "entorno.png").stat().st_size // 1024} KB')


if __name__ == '__main__':
    main()
