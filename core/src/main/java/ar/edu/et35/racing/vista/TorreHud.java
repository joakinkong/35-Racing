package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.juego.FuenteCarrera;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Recursos;
import ar.edu.et35.racing.util.Tiempo;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Torre de tiempos al estilo de las transmisiones de Fórmula 1: arriba el nombre del juego y la vuelta en que va el
 * puntero, y abajo una fila por auto, en orden de posición, con el número de posición (rojo el primero), la franja de
 * color de su escudería, las tres letras del piloto y su mejor vuelta. Los autos de esta PC se resaltan.
 *
 * Es una tabla de Scene2D: no toca nada de la simulación, solo lee la {@link FotoCarrera} que le pasan.
 */
public class TorreHud extends Table {
    private static final float ANCHO_POSICION = 18f;
    private static final float ANCHO_FRANJA = 4f;
    private static final float ANCHO_CODIGO = 34f;
    private static final float ANCHO_TIEMPO = 62f;
    private static final float ALTO_FILA = 17f;
    private static final float ALTO_ENCABEZADO = 18f;
    private static final float SEPARACION = 1f;
    private static final float ANCHO_TORRE = ANCHO_POSICION + ANCHO_FRANJA + ANCHO_CODIGO + ANCHO_TIEMPO + 4 * SEPARACION;

    private static final Color FONDO = new Color(0.08f, 0.08f, 0.11f, 0.9f);
    private static final Color FONDO_PROPIO = new Color(0.24f, 0.24f, 0.31f, 0.95f);
    private static final Color FONDO_POSICION = new Color(0.16f, 0.16f, 0.2f, 0.95f);

    private final Recursos recursos;
    private final Skin skin;
    private final FuenteCarrera fuente;
    private final Set<Integer> idsLocales;
    private final Drawable fondo;
    private final Drawable fondoPropio;
    private final Drawable fondoPosicion;
    private final Drawable fondoPuntero;
    /** Un Drawable por piloto, creados la primera vez que hacen falta (crear uno por cuadro llenaría la memoria). */
    private final Drawable[] colorPiloto = new Drawable[Catalogo.PILOTOS.size()];
    private final Label vuelta;
    private final List<Fila> filas = new ArrayList<>();

    public TorreHud(Recursos recursos, FuenteCarrera fuente) {
        this.recursos = recursos;
        this.skin = recursos.skin;
        this.fuente = fuente;
        this.idsLocales = Set.copyOf(fuente.idsLocales());
        fondo = recursos.color(FONDO);
        fondoPropio = recursos.color(FONDO_PROPIO);
        fondoPosicion = recursos.color(FONDO_POSICION);
        fondoPuntero = recursos.color(Paleta.ROJO);

        vuelta = new Label("", skin, "gris");
        vuelta.setAlignment(Align.right);
        agregarEncabezado();
        pack();
    }

    /** La fila de arriba: el nombre del juego a la izquierda y la vuelta del puntero ("V 2/3") a la derecha. */
    private void agregarEncabezado() {
        Table encabezado = new Table();
        encabezado.setBackground(fondo);
        encabezado.pad(0f, 5f, 0f, 5f);
        encabezado.add(new Label("35 RACING", skin)).left().expandX();
        encabezado.add(vuelta).right();
        add(encabezado).width(ANCHO_TORRE).height(ALTO_ENCABEZADO).padBottom(SEPARACION).row();
    }

    /** Ancho fijo de la torre, para ubicarla en la pantalla. */
    public float anchoTorre() {
        return ANCHO_TORRE;
    }

    /** Pone al día los textos y colores con lo que hay en la carrera. Las filas se arman con la primera foto. */
    public void actualizar(FotoCarrera foto) {
        if (foto == null || foto.autos().isEmpty()) {
            return;
        }
        if (filas.size() != foto.autos().size()) {
            armarFilas(foto.autos().size());
        }
        FotoAuto puntero = foto.autos().get(0);
        int total = fuente.vueltasTotales();
        vuelta.setText(puntero.terminado() ? "FIN" : "V " + Math.min(puntero.vueltas() + 1, total) + "/" + total);
        for (int i = 0; i < filas.size(); i++) {
            FotoAuto auto = foto.autos().get(i);
            filas.get(i).actualizar(auto, i + 1, idsLocales.contains(auto.id()));
        }
    }

    private void armarFilas(int cantidad) {
        filas.clear();
        clearChildren();
        agregarEncabezado();
        for (int i = 0; i < cantidad; i++) {
            Fila fila = new Fila();
            filas.add(fila);
            add(fila.tabla).width(ANCHO_TORRE).height(ALTO_FILA).padBottom(SEPARACION).row();
        }
        pack();
    }

    private Drawable colorDe(int piloto) {
        int indice = Catalogo.pilotoValido(piloto) ? piloto : 0;
        if (colorPiloto[indice] == null) {
            colorPiloto[indice] = recursos.color(Paleta.colorDePiloto(indice));
        }
        return colorPiloto[indice];
    }

    /** Una fila de la torre: posición, franja de escudería, código y tiempo. */
    private class Fila {
        private final Table tabla = new Table();
        private final Table casillaPosicion = new Table();
        private final Label posicion = new Label("", skin);
        private final Image franja = new Image();
        private final Label codigo = new Label("", skin);
        private final Label tiempo = new Label("", skin);

        Fila() {
            posicion.setAlignment(Align.center);
            tiempo.setAlignment(Align.right);
            casillaPosicion.add(posicion).center();
            tabla.setBackground(fondo);
            tabla.add(casillaPosicion).width(ANCHO_POSICION).growY().padRight(SEPARACION);
            tabla.add(franja).width(ANCHO_FRANJA).growY().padRight(SEPARACION);
            tabla.add(codigo).width(ANCHO_CODIGO).padLeft(3f).left();
            tabla.add(tiempo).width(ANCHO_TIEMPO).padRight(3f).right();
        }

        void actualizar(FotoAuto auto, int lugar, boolean propio) {
            tabla.setBackground(propio ? fondoPropio : fondo);
            casillaPosicion.setBackground(lugar == 1 ? fondoPuntero : fondoPosicion);
            posicion.setText(String.valueOf(lugar));
            franja.setDrawable(colorDe(fuente.piloto(auto.id())));
            codigo.setText(Catalogo.piloto(fuente.piloto(auto.id())).codigo());
            codigo.setColor(auto.desconectado() ? Paleta.GRIS_CLARO : Paleta.BLANCO);
            if (auto.desconectado()) {
                tiempo.setText("SALIÓ");
                tiempo.setColor(Paleta.GRIS_CLARO);
            } else if (auto.terminado()) {
                tiempo.setText("FIN");
                tiempo.setColor(Paleta.VERDE);
            } else {
                tiempo.setText(Tiempo.formato(auto.mejorVuelta()));
                tiempo.setColor(Paleta.BLANCO);
            }
        }
    }
}
