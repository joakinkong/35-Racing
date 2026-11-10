package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.FondoConLinea;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Tiempo;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import java.util.List;
import java.util.Locale;

/**
 * Resultado de la clasificación: quién larga en cada lugar. Se ve unos segundos y la carrera arranca sola: la que manda es
 * la CUENTA_REGRESIVA del servidor, y el reloj de esta pantalla solo la anticipa para que se sepa cuánto falta.
 */
public class PantallaGrilla extends PantallaBase {
    private static final float[] ANCHOS = {64f, 300f, 170f, 170f};
    private static final String[] ENCABEZADO = {"POS", "PILOTO", "VUELTA", "DIFERENCIA"};
    private static final float ANCHO_COLUMNA = 760f;
    private static final float ALTO_FILA = 44f;
    /** Alto máximo de la tabla: con más filas (hasta 22 autos) se desplaza. */
    private static final float ALTO_MAXIMO_TABLA = 480f;
    private static final float SEPARACION_FILAS = 4f;
    private static final float PADDING_FILA = 14f;

    private final Label aviso = new Label("", skin, "subtitulo");
    private float restante = Config.SEGUNDOS_GRILLA;
    private boolean salio;

    public PantallaGrilla(Main juego, List<ResultadoJugador> grilla) {
        super(juego);
        contenido.add(titular("CLASIFICACIÓN FINALIZADA", "GRILLA DE LARGADA")).left().padBottom(14).row();
        contenido.add(conDesplazamiento(tabla(grilla), ALTO_MAXIMO_TABLA)).width(ANCHO_COLUMNA).padBottom(12).row();
        contenido.add(aviso).left();
        actualizarAviso();
    }

    private Table tabla(List<ResultadoJugador> grilla) {
        Table tabla = new Table();
        Table encabezado = new Table();
        encabezado.pad(0f, PADDING_FILA, 0f, PADDING_FILA);
        for (int i = 0; i < ENCABEZADO.length; i++) {
            encabezado.add(new Label(ENCABEZADO[i], skin, "etiqueta")).width(ANCHOS[i]).left();
        }
        tabla.add(encabezado).growX().padBottom(6).row();
        float referencia = grilla.isEmpty() ? Float.NaN : grilla.get(0).tiempoTotal();
        for (ResultadoJugador lugar : grilla) {
            tabla.add(fila(lugar, referencia)).growX().height(ALTO_FILA).padBottom(SEPARACION_FILAS).row();
        }
        return tabla;
    }

    private Table fila(ResultadoJugador lugar, float referencia) {
        Table fila = new Table();
        fila.setBackground(recursos.conLinea(Paleta.SUPERFICIE_ALTA, colorDe(lugar.id()), FondoConLinea.Lado.IZQUIERDA));
        fila.pad(0f, PADDING_FILA, 0f, PADDING_FILA);
        Label posicion = new Label(String.format(Locale.ROOT, "%02d", lugar.posicion()), skin, "posicion");
        if (lugar.posicion() == 1) {
            posicion.setColor(Paleta.DORADO);
        }
        fila.add(posicion).width(ANCHOS[0]).left();
        fila.add(new Label(lugar.nombre(), skin, "subtitulo")).width(ANCHOS[1]).left();
        fila.add(new Label(Tiempo.formato(lugar.tiempoTotal()), skin, "numeros")).width(ANCHOS[2]).left();
        fila.add(new Label(diferencia(lugar, referencia), skin, "numeros")).width(ANCHOS[3]).left();
        return fila;
    }

    /** Lo que le sacó el primero; vacío para el primero y para quien no completó la vuelta. */
    private String diferencia(ResultadoJugador lugar, float referencia) {
        if (lugar.posicion() == 1 || Float.isNaN(lugar.tiempoTotal()) || Float.isNaN(referencia)) {
            return "";
        }
        return String.format(Locale.ROOT, "+%.3f", lugar.tiempoTotal() - referencia);
    }

    private Color colorDe(int id) {
        SesionRed sesion = juego.sesion();
        JugadorLobby jugador = sesion != null ? sesion.cliente().lobby().jugador(id) : null;
        return jugador != null ? Paleta.colorDePiloto(jugador.piloto()) : Paleta.SUPERFICIE_MAXIMA;
    }

    private void actualizarAviso() {
        aviso.setText("LA CARRERA EMPIEZA EN " + Math.max(0, (int) Math.ceil(restante)));
    }

    @Override
    protected void actualizar(float delta) {
        restante -= delta;
        actualizarAviso();
        SesionRed sesion = juego.sesion();
        if (sesion == null || salio) {
            return;
        }
        sesion.cliente().actualizar();
        Mensaje mensaje;
        while ((mensaje = sesion.cliente().sondear()) != null) {
            switch (mensaje.nombre()) {
                case Protocolo.CUENTA_REGRESIVA:
                    irA(PantallaCarrera.desde(juego, Protocolo.leerCuenta(mensaje)));
                    return;
                case Protocolo.CERRADA:
                    irA(new MenuPrincipal(juego, "La partida se cerró: " + mensaje.campo(0)));
                    return;
                case Protocolo.CONEXION_PERDIDA:
                    irA(new MenuPrincipal(juego, mensaje.campo(0)));
                    return;
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
