package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Tiempo;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import java.util.List;

/** Tabla de resultados de la carrera: posición, piloto, tiempo total y mejor vuelta. */
public class Resultados extends PantallaBase {
    private static final float[] ANCHOS = {40f, 130f, 100f, 110f};
    private static final float ALTO_FILA = 20f;
    private static final String[] ENCABEZADO = {"POS", "PILOTO", "TIEMPO", "MEJOR VUELTA"};

    public Resultados(Main juego, List<ResultadoJugador> resultados) {
        super(juego);

        contenido.add(new Label("RESULTADOS", skin, "titulo")).padBottom(10).row();
        contenido.add(tabla(resultados)).padBottom(10).row();
        contenido.add(boton("VOLVER AL MENÚ", () -> juego.irA(new MenuPrincipal(juego)))).width(180).height(30);
    }

    private Table tabla(List<ResultadoJugador> resultados) {
        Table tabla = new Table();
        tabla.setBackground(recursos.color(Paleta.GRIS_OSCURO));
        tabla.pad(6);
        agregarFila(tabla, ENCABEZADO, "gris", "gris");
        for (ResultadoJugador resultado : resultados) {
            String[] fila = {
                String.valueOf(resultado.posicion()),
                resultado.nombre(),
                Tiempo.formato(resultado.tiempoTotal()),
                Tiempo.formato(resultado.mejorVuelta()),
            };
            // El ganador va en rojo.
            agregarFila(tabla, fila, resultado.posicion() == 1 ? "rojo" : "default", "default");
        }
        return tabla;
    }

    private void agregarFila(Table tabla, String[] textos, String estiloPosicion, String estilo) {
        for (int i = 0; i < textos.length; i++) {
            tabla.add(new Label(textos[i], skin, i == 0 ? estiloPosicion : estilo)).width(ANCHOS[i]).height(ALTO_FILA).left();
        }
        tabla.row();
    }

    /**
     * Mientras se ven los resultados, la partida en red (si la hay) sigue abierta: se manda el PING y se vacían los
     * mensajes, para que el servidor no dé por caído a este jugador. Se cierra al volver al menú.
     */
    @Override
    protected void actualizar(float delta) {
        SesionRed sesion = juego.sesion();
        if (sesion != null) {
            sesion.cliente().actualizar();
            while (sesion.cliente().sondear() != null) {
                // Todavía no hay nada que mostrar de la red en esta pantalla.
            }
        }
    }

    @Override
    protected void volver() {
        juego.irA(new MenuPrincipal(juego));
    }
}
