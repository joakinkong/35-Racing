package ar.edu.et35.racing.vista;

import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.juego.FuenteCarrera;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Recursos;
import ar.edu.et35.racing.util.Tiempo;
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
 * puntero, y abajo una fila por auto, en orden de posición, con el número de posición (dorado el primero), la franja de
 * color de su escudería, las tres letras del piloto y su mejor vuelta. Los autos de esta PC se resaltan.
 *
 * Es una tabla de Scene2D: no toca nada de la simulación, solo lee la {@link FotoCarrera} que le pasan. En la clasificación
 * muestra solo los autos de esta PC: cada uno corre sin saber cómo les va a los demás.
 */
public class TorreHud extends Table {
    private static final int SIN_POSICION = 0;
    private static final float ANCHO_POSICION = 36f;
    private static final float ANCHO_FRANJA = 5f;
    private static final float ANCHO_CODIGO = 62f;
    private static final float ANCHO_TIEMPO = 104f;
    private static final float ALTO_FILA = 30f;
    private static final float ALTO_ENCABEZADO = 32f;
    private static final float SEPARACION = 2f;
    private static final float ANCHO_TORRE = ANCHO_POSICION + ANCHO_FRANJA + ANCHO_CODIGO + ANCHO_TIEMPO + 16f;

    private final Recursos recursos;
    private final Skin skin;
    private final FuenteCarrera fuente;
    private final Set<Integer> idsLocales;
    private final Drawable fondo;
    private final Drawable fondoPropio;
    /** Un Drawable por piloto, creados la primera vez que hacen falta (crear uno por cuadro llenaría la memoria). */
    private final Drawable[] colorPiloto = new Drawable[Catalogo.PILOTOS.size()];
    private final Label vuelta;
    private final boolean clasificacion;
    private final List<Fila> filas = new ArrayList<>();

    public TorreHud(Recursos recursos, FuenteCarrera fuente) {
        this(recursos, fuente, false);
    }

    public TorreHud(Recursos recursos, FuenteCarrera fuente, boolean clasificacion) {
        this.clasificacion = clasificacion;
        this.recursos = recursos;
        this.skin = recursos.skin;
        this.fuente = fuente;
        this.idsLocales = Set.copyOf(fuente.idsLocales());
        fondo = recursos.color(Paleta.FONDO_HUD);
        fondoPropio = recursos.color(Paleta.FONDO_HUD_PROPIO);

        vuelta = new Label("", skin, "etiquetaSuave");
        vuelta.setAlignment(Align.right);
        agregarEncabezado();
        pack();
    }

    /** La fila de arriba: el nombre del juego a la izquierda y la vuelta del puntero ("V 2/3") a la derecha. */
    private void agregarEncabezado() {
        Table encabezado = new Table();
        encabezado.setBackground(fondo);
        encabezado.pad(0f, 10f, 0f, 10f);
        encabezado.add(new Label(clasificacion ? "CLASIFICACIÓN" : "35 RACING", skin, "marcaChica")).left().expandX();
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
        List<FotoAuto> autos = clasificacion ? autosPropios(foto) : foto.autos();
        if (autos.isEmpty()) {
            return;
        }
        if (filas.size() != autos.size()) {
            armarFilas(autos.size());
        }
        FotoAuto puntero = autos.get(0);
        int total = fuente.vueltasTotales();
        vuelta.setText(clasificacion ? "" : puntero.terminado() ? "FIN" : "V " + Math.min(puntero.vueltas() + 1, total) + "/" + total);
        for (int i = 0; i < filas.size(); i++) {
            FotoAuto auto = autos.get(i);
            filas.get(i).actualizar(auto, clasificacion ? SIN_POSICION : i + 1, idsLocales.contains(auto.id()));
        }
    }

    private List<FotoAuto> autosPropios(FotoCarrera foto) {
        List<FotoAuto> propios = new ArrayList<>();
        for (FotoAuto auto : foto.autos()) {
            if (idsLocales.contains(auto.id())) {
                propios.add(auto);
            }
        }
        return propios;
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
        private final Label posicion = new Label("", skin, "numeros");
        private final Image franja = new Image();
        private final Label codigo = new Label("", skin, "numeros");
        private final Label tiempo = new Label("", skin, "numeros");

        Fila() {
            posicion.setAlignment(Align.center);
            tiempo.setAlignment(Align.right);
            tabla.setBackground(fondo);
            tabla.add(posicion).width(ANCHO_POSICION);
            tabla.add(franja).width(ANCHO_FRANJA).height(ALTO_FILA - 10f);
            tabla.add(codigo).width(ANCHO_CODIGO).padLeft(8f).left();
            tabla.add(tiempo).width(ANCHO_TIEMPO).padRight(8f).right();
        }

        void actualizar(FotoAuto auto, int lugar, boolean propio) {
            tabla.setBackground(propio ? fondoPropio : fondo);
            posicion.setText(lugar == SIN_POSICION ? "–" : String.valueOf(lugar));
            posicion.setColor(lugar == 1 ? Paleta.DORADO : Paleta.TEXTO);
            franja.setDrawable(colorDe(fuente.piloto(auto.id())));
            codigo.setText(Catalogo.piloto(fuente.piloto(auto.id())).codigo());
            codigo.setColor(auto.desconectado() ? Paleta.TEXTO_APAGADO : Paleta.TEXTO);
            if (auto.desconectado()) {
                tiempo.setText("SALIÓ");
                tiempo.setColor(Paleta.TEXTO_APAGADO);
            } else if (auto.terminado()) {
                tiempo.setText("FIN");
                tiempo.setColor(Paleta.SECUNDARIO);
            } else {
                tiempo.setText(Tiempo.formato(auto.mejorVuelta()));
                tiempo.setColor(Paleta.TEXTO);
            }
        }
    }
}
