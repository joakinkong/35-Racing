package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Tiempo;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import java.util.List;

/**
 * Tabla de resultados de la carrera: posición, piloto, tiempo total y mejor vuelta. En red, el host puede pedir
 * revancha (vuelven todos al lobby con los mismos jugadores) y los demás esperan su decisión.
 */
public class Resultados extends PantallaBase {
    private static final float[] ANCHOS = {40f, 130f, 100f, 110f};
    private static final float ALTO_FILA = 20f;
    private static final String[] ENCABEZADO = {"POS", "PILOTO", "TIEMPO", "MEJOR VUELTA"};

    private final Label mensaje = new Label("", skin, "gris");
    private boolean salio;

    public Resultados(Main juego, List<ResultadoJugador> resultados) {
        super(juego);
        SesionRed sesion = juego.sesion();

        contenido.add(new Label("RESULTADOS", skin, "titulo")).padBottom(10).row();
        contenido.add(tabla(resultados)).padBottom(10).row();
        if (sesion != null && sesion.esAnfitrion()) {
            Table botones = new Table();
            botones.add(boton("REVANCHA", () -> sesion.cliente().revancha())).width(160).height(30).padRight(8);
            botones.add(boton("CERRAR LA PARTIDA", () -> irA(new MenuPrincipal(juego)))).width(180).height(30);
            contenido.add(botones).row();
            mensaje.setText("La revancha vuelve a la sala de espera con los mismos jugadores");
        } else {
            contenido.add(boton("VOLVER AL MENÚ", () -> irA(new MenuPrincipal(juego)))).width(180).height(30).row();
            if (sesion != null) {
                mensaje.setText("Esperando que el anfitrión elija la revancha...");
            }
        }
        contenido.add(mensaje).padTop(8);
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
     * En red, la partida sigue abierta mientras se ven los resultados: se manda el PING y se atiende lo que llega.
     * Con un LOBBY (el host pidió revancha) se pasa a la sala de espera; con CERRADA o si se corta, al menú.
     */
    @Override
    protected void actualizar(float delta) {
        SesionRed sesion = juego.sesion();
        if (sesion == null || salio) {
            return;
        }
        sesion.cliente().actualizar();
        Mensaje recibido;
        while ((recibido = sesion.cliente().sondear()) != null) {
            switch (recibido.nombre()) {
                case Protocolo.LOBBY:
                    irA(new Lobby(juego));
                    return;
                case Protocolo.CERRADA:
                    irA(new MenuPrincipal(juego, "La partida se cerró: " + recibido.campo(0)));
                    return;
                case Protocolo.CONEXION_PERDIDA:
                    irA(new MenuPrincipal(juego, recibido.campo(0)));
                    return;
                case Protocolo.ERROR:
                    mensaje.setText(recibido.campo(1));
                    break;
                default:
                    break;
            }
        }
    }

    private void irA(PantallaBase siguiente) {
        salio = true;
        juego.irA(siguiente);
    }

    @Override
    protected void volver() {
        irA(new MenuPrincipal(juego));
    }
}
