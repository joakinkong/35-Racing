#!/usr/bin/env python3
"""Junta los documentos de docs/proceso en un solo PDF.

Uso (desde cualquier carpeta):  python docs/proceso/generar_pdf.py

Lee README.md y los archivos NN-nombre.md (uno por etapa), los convierte a HTML y los imprime a PDF con
Microsoft Edge o Google Chrome en modo headless. Solo usa la biblioteca estándar de Python.
Salida: docs/proceso/proceso-de-desarrollo.pdf
"""
import html
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date
from pathlib import Path

CARPETA = Path(__file__).resolve().parent
RAIZ = CARPETA.parent.parent
SALIDA = CARPETA / "proceso-de-desarrollo.pdf"
REPO_URL = "https://github.com/joakinkong/35-Racing"
INTEGRANTES = ["Joaquin Barreira", "Madai Andacaba", "Antonella Vivacqua", "Joaquin Rodrigues", "Mateo Guinsburg"]

NAVEGADORES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    "msedge", "google-chrome", "chromium", "chrome",
]

CSS = """
@page { size: A4; margin: 18mm 16mm 20mm 16mm;
        @bottom-center { content: "35 Racing - Proceso de desarrollo  |  " counter(page); font: 8pt 'Segoe UI', Arial, sans-serif; color: #777; } }
* { box-sizing: border-box; }
body { font: 10.5pt/1.5 'Segoe UI', Arial, sans-serif; color: #1b1b22; margin: 0; }
h1 { font-size: 21pt; margin: 0 0 6mm; padding-bottom: 3mm; border-bottom: 3px solid #E10600; }
h2 { font-size: 14pt; margin: 8mm 0 3mm; color: #15151E; border-left: 4px solid #E10600; padding-left: 3mm; }
h3 { font-size: 11.5pt; margin: 6mm 0 2mm; }
h1, h2, h3 { page-break-after: avoid; }
p { margin: 0 0 3mm; }
ul, ol { margin: 0 0 3mm; padding-left: 7mm; }
li { margin-bottom: 1.2mm; }
a { color: #0b57d0; text-decoration: none; }
code { font: 9pt Consolas, 'Courier New', monospace; background: #f0f0f3; padding: 0 1.2mm; border-radius: 1mm; }
pre { background: #f0f0f3; padding: 3mm; border-radius: 1.5mm; font: 8.5pt Consolas, monospace; white-space: pre-wrap; }
table { border-collapse: collapse; width: 100%; margin: 0 0 4mm; font-size: 9.5pt; page-break-inside: auto; }
tr { page-break-inside: avoid; }
th, td { border: 1px solid #cfcfd6; padding: 1.6mm 2.4mm; vertical-align: top; text-align: left; }
th { background: #15151E; color: #fff; font-weight: 600; }
td img { display: block; width: 100%; height: auto; border: 1px solid #cfcfd6; }
figure { margin: 3mm 0 4mm; text-align: center; page-break-inside: avoid; }
figure img { max-width: 72%; height: auto; border: 1px solid #cfcfd6; }
figcaption { font-size: 8.5pt; color: #666; margin-top: 1mm; }
hr { border: 0; border-top: 1px solid #cfcfd6; margin: 5mm 0; }
section.etapa { page-break-before: always; }
.portada { height: 240mm; display: flex; flex-direction: column; justify-content: center; }
.portada .banda { height: 14mm; margin-bottom: 14mm;
  background: repeating-conic-gradient(#15151E 0% 25%, #fff 0% 50%) 0 0 / 14mm 14mm; border: 1px solid #15151E; }
.portada h1 { font-size: 34pt; border: 0; margin-bottom: 2mm; }
.portada .sub { font-size: 16pt; color: #E10600; margin-bottom: 12mm; }
.portada .dato { font-size: 11pt; margin-bottom: 1.5mm; }
.portada .integrantes { margin-top: 8mm; font-size: 11pt; }
.indice { page-break-before: always; }
.indice ul { list-style: none; padding-left: 0; }
.indice li { margin-bottom: 2mm; }
"""


# ------------------------------------------------------------------ Markdown mínimo -> HTML
def slug_etapa(nombre_archivo: str) -> str:
    return "etapa-" + Path(nombre_archivo).stem.split("-")[0]


def resolver_enlace(destino: str, carpeta_doc: Path) -> str:
    """Enlaces entre etapas pasan a anclas internas; otros relativos, a GitHub; el resto queda igual."""
    if re.match(r"^[a-z]+://", destino) or destino.startswith("#"):
        return destino
    ruta, _, ancla = destino.partition("#")
    if ruta.endswith(".md") and (carpeta_doc / ruta).resolve().parent == CARPETA:
        nombre = Path(ruta).name
        return "#" + ("inicio" if nombre == "README.md" else slug_etapa(nombre))
    absoluta = (carpeta_doc / ruta).resolve()
    try:
        relativa = absoluta.relative_to(RAIZ).as_posix()
    except ValueError:
        return destino
    return f"{REPO_URL}/blob/main/{relativa}" + (f"#{ancla}" if ancla else "")


def inline(texto: str, carpeta_doc: Path) -> str:
    codigos = []

    def guardar_codigo(m):
        codigos.append("<code>" + html.escape(m.group(1)) + "</code>")
        return f"\x00{len(codigos) - 1}\x00"

    texto = re.sub(r"`([^`]+)`", guardar_codigo, texto)
    texto = html.escape(texto, quote=False)

    def imagen(m):
        alt, src = m.group(1), m.group(2)
        ruta = (carpeta_doc / src).resolve()
        return f'<img src="{ruta.as_uri()}" alt="{html.escape(alt)}">'

    def enlace(m):
        destino = resolver_enlace(html.unescape(m.group(2)), carpeta_doc)
        return f'<a href="{html.escape(destino)}">{m.group(1)}</a>'

    texto = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", imagen, texto)
    texto = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", enlace, texto)
    texto = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", texto)
    texto = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", texto)
    return re.sub(r"\x00(\d+)\x00", lambda m: codigos[int(m.group(1))], texto)


def celdas(linea: str):
    linea = linea.strip().strip("|")
    return [c.strip() for c in linea.split("|")]


def lista(lineas, i, carpeta_doc):
    """Convierte un bloque de lista (con un nivel de anidado) y devuelve (html, índice siguiente)."""
    patron = re.compile(r"^(\s*)([-*]|\d+\.)\s+(.*)$")
    m0 = patron.match(lineas[i])
    ordenada = m0.group(2)[0].isdigit()
    base = len(m0.group(1))
    items = []  # [texto, [subitems]]
    while i < len(lineas):
        m = patron.match(lineas[i])
        if not m:
            break
        sangria = len(m.group(1))
        if sangria <= base:
            items.append([m.group(3), []])
        elif items:
            items[-1][1].append(m.group(3))
        i += 1
    etiqueta = "ol" if ordenada else "ul"
    salida = [f"<{etiqueta}>"]
    for texto, subitems in items:
        sub = ""
        if subitems:
            sub = "<ul>" + "".join(f"<li>{inline(s, carpeta_doc)}</li>" for s in subitems) + "</ul>"
        salida.append(f"<li>{inline(texto, carpeta_doc)}{sub}</li>")
    salida.append(f"</{etiqueta}>")
    return "\n".join(salida), i


def markdown_a_html(md: str, carpeta_doc: Path) -> str:
    lineas = md.replace("\r\n", "\n").split("\n")
    salida, i = [], 0
    while i < len(lineas):
        linea = lineas[i]
        if not linea.strip():
            i += 1
        elif linea.startswith("```"):
            bloque, i = [], i + 1
            while i < len(lineas) and not lineas[i].startswith("```"):
                bloque.append(lineas[i])
                i += 1
            i += 1
            salida.append("<pre>" + html.escape("\n".join(bloque)) + "</pre>")
        elif re.match(r"^#{1,3}\s", linea):
            nivel = len(linea) - len(linea.lstrip("#"))
            salida.append(f"<h{nivel}>{inline(linea[nivel:].strip(), carpeta_doc)}</h{nivel}>")
            i += 1
        elif re.match(r"^-{3,}\s*$", linea):
            salida.append("<hr>")
            i += 1
        elif linea.lstrip().startswith("|"):
            filas = []
            while i < len(lineas) and lineas[i].lstrip().startswith("|"):
                filas.append(lineas[i])
                i += 1
            encabezado = celdas(filas[0])
            cuerpo = [celdas(f) for f in filas[2:]]
            t = ["<table><thead><tr>" + "".join(f"<th>{inline(c, carpeta_doc)}</th>" for c in encabezado) + "</tr></thead><tbody>"]
            for fila in cuerpo:
                t.append("<tr>" + "".join(f"<td>{inline(c, carpeta_doc)}</td>" for c in fila) + "</tr>")
            t.append("</tbody></table>")
            salida.append("\n".join(t))
        elif re.match(r"^(\s*)([-*]|\d+\.)\s+", linea):
            bloque, i = lista(lineas, i, carpeta_doc)
            salida.append(bloque)
        else:
            parrafo = []
            while i < len(lineas) and lineas[i].strip() and not re.match(r"^(#{1,3}\s|\||```|(\s*)([-*]|\d+\.)\s+)", lineas[i]):
                parrafo.append(lineas[i].strip())
                i += 1
            texto = " ".join(parrafo)
            solo_imagen = re.fullmatch(r"!\[([^\]]*)\]\(([^)]+)\)", texto)
            if solo_imagen:
                salida.append(f"<figure>{inline(texto, carpeta_doc)}<figcaption>{html.escape(solo_imagen.group(1))}</figcaption></figure>")
            else:
                salida.append(f"<p>{inline(texto, carpeta_doc)}</p>")
    return "\n".join(salida)


# ------------------------------------------------------------------ Armado del documento
def titulo_de(md: str) -> str:
    for linea in md.splitlines():
        if linea.startswith("# "):
            return linea[2:].strip()
    return "Sin título"


def git(*args) -> str:
    try:
        return subprocess.run(["git", *args], cwd=RAIZ, capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        return ""


def armar_html() -> str:
    etapas = sorted(p for p in CARPETA.glob("[0-9][0-9]-*.md"))
    intro = (CARPETA / "README.md").read_text(encoding="utf-8")
    ultima = etapas[-1].stem.split("-")[0].lstrip("0") or "0" if etapas else "-"
    commit = git("rev-parse", "--short", "HEAD")
    hoy = date.today().strftime("%d/%m/%Y")

    partes = [
        '<div class="portada"><div class="banda"></div>',
        "<h1>35 Racing</h1>",
        '<div class="sub">Documentación del proceso de desarrollo</div>',
        '<div class="dato">Proyecto final de Programación sobre Redes</div>',
        '<div class="dato">Videojuego de carreras 2D multijugador en red local (Java 17 + libGDX)</div>',
        f'<div class="dato">Repositorio: <a href="{REPO_URL}">{REPO_URL}</a></div>',
        f'<div class="dato">Documento actualizado hasta la etapa {ultima} &middot; generado el {hoy}'
        + (f" &middot; versión {commit}" if commit else "") + "</div>",
        '<div class="integrantes"><strong>Integrantes</strong><br>' + "<br>".join(INTEGRANTES) + "</div></div>",
        '<section class="indice"><h1>Contenido</h1><ul>',
        '<li><a href="#inicio">Cómo trabajamos y línea de tiempo</a></li>',
    ]
    for p in etapas:
        titulo = titulo_de(p.read_text(encoding="utf-8"))
        partes.append(f'<li><a href="#{slug_etapa(p.name)}">{html.escape(titulo)}</a></li>')
    partes.append("</ul></section>")

    partes.append('<section class="etapa" id="inicio">' + markdown_a_html(intro, CARPETA) + "</section>")
    for p in etapas:
        partes.append(f'<section class="etapa" id="{slug_etapa(p.name)}">'
                      + markdown_a_html(p.read_text(encoding="utf-8"), CARPETA) + "</section>")

    return ('<!doctype html><html lang="es"><head><meta charset="utf-8"><title>35 Racing - Proceso de desarrollo</title>'
            f"<style>{CSS}</style></head><body>" + "\n".join(partes) + "</body></html>")


def buscar_navegador() -> str:
    for candidato in NAVEGADORES:
        if os.path.isfile(candidato) or shutil.which(candidato):
            return candidato
    sys.exit("No encontré Microsoft Edge ni Google Chrome para generar el PDF.")


def main():
    navegador = buscar_navegador()
    with tempfile.TemporaryDirectory() as tmp:
        archivo_html = Path(tmp) / "proceso.html"
        archivo_html.write_text(armar_html(), encoding="utf-8")
        perfil = Path(tmp) / "perfil"
        if SALIDA.exists():
            SALIDA.unlink()
        subprocess.run([navegador, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                        f"--user-data-dir={perfil}", f"--print-to-pdf={SALIDA}", archivo_html.as_uri()],
                       check=True, capture_output=True, timeout=180)
    if not SALIDA.exists():
        sys.exit("El navegador no generó el PDF.")
    print(f"PDF generado: {SALIDA} ({SALIDA.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
