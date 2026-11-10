package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.FilaTorneo;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
import ar.edu.et35.racing.red.EstadoTorneo;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.util.FondoConLinea;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Tiempo;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import java.util.List;
import java.util.Locale;

/**
 * Tabla de resultados de la carrera: posición, piloto, tiempo total y mejor vuelta. En red, el host puede pedir
 * revancha (vuelven todos al lobby con los mismos jugadores) y los demás esperan su decisión.
 *
 * <p>En un torneo, al lado va la tabla acumulada. El host larga la carrera siguiente desde acá, y la revancha queda para
 * el final del torneo.
 */
public class Resultados extends PantallaBase {
    private static final float[] ANCHOS = {64f, 300f, 170f, 170f};
    private static final float ANCHO_COLUMNA = 760f;
    private static final float ALTO_FILA = 44f;
    private static final float SEPARACION_FILAS = 4f;
    private static final float PADDING_FILA = 14f;
    private static final float ALTO_BOTON = 48f;
    /** Alto máximo de una tabla de resultados: con más filas (hasta 22 autos) se desplaza. */
    private static final float ALTO_MAXIMO_TABLA = 450f;
    private static final String[] ENCABEZADO = {"POS", "PILOTO", "TIEMPO", "MEJOR VUELTA"};
    private static final float[] ANCHOS_TORNEO = {52f, 220f, 130f, 160f, 60f};
    private static final String[] ENCABEZADO_TORNEO = {"POS", "PILOTO", "TIEMPO", "MEJOR VUELTA", "PTS"};
    private static final float[] ANCHOS_TABLA = {52f, 250f, 80f, 80f};
    private static final String[] ENCABEZADO_TABLA = {"POS", "PILOTO", "PTS", "GAN"};
    private static final float ANCHO_COLUMNA_TORNEO = 680f;
    private static final float ANCHO_TABLA_TORNEO = 490f;
    private static final float SEPARACION_TABLAS = 16f;

    private final Label mensaje = new Label("", skin, "suave");
    private boolean salio;

    public Resultados(Main juego, List<ResultadoJugador> resultados) {
        super(juego);
        SesionRed sesion = juego.sesion();
        EstadoTorneo torneo = sesion != null ? sesion.cliente().torneo() : null;
        if (torneo != null) {
            armarTorneo(sesion, torneo, resultados);
            return;
        }

        contenido.add(titular("CARRERA FINALIZADA", "RESULTADOS")).left().padBottom(14).row();
        contenido.add(conDesplazamiento(tabla(resultados), ALTO_MAXIMO_TABLA)).width(ANCHO_COLUMNA).padBottom(14).row();
        if (sesion != null && sesion.esAnfitrion()) {
            Table botones = new Table();
            botones.add(botonPrimario("REVANCHA", () -> sesion.cliente().revancha())).growX().uniformX().height(ALTO_BOTON).padRight(8);
            botones.add(boton("CERRAR LA PARTIDA", () -> irA(new MenuPrincipal(juego)))).growX().uniformX().height(ALTO_BOTON);
            contenido.add(botones).width(ANCHO_COLUMNA).row();
            mensaje.setText("La revancha vuelve a la sala de espera con los mismos jugadores");
        } else {
            contenido.add(boton("VOLVER AL MENÚ", () -> irA(new MenuPrincipal(juego)))).width(ANCHO_COLUMNA).height(ALTO_BOTON).row();
            if (sesion != null) {
                mensaje.setText("Esperando que el anfitrión elija la revancha...");
            }
        }
        contenido.add(mensaje).left().padTop(10);
    }

    /** Los resultados de la carrera, la tabla acumulada y, según quién sea y si quedan carreras, los botones. */
    private void armarTorneo(SesionRed sesion, EstadoTorneo torneo, List<ResultadoJugador> resultados) {
        boolean termino = torneo.esUltimaCarrera();
        String carrera = "CARRERA " + torneo.carrera() + " DE " + torneo.total();
        String campeon = torneo.tabla().isEmpty() ? "" : torneo.tabla().get(0).nombre();
        contenido.add(termino ? titular("TORNEO FINALIZADO  ·  " + carrera, "CAMPEÓN: " + campeon) : titular("TORNEO  ·  " + carrera, "RESULTADOS"))
            .left().padBottom(14).row();

        Table tablas = new Table();
        tablas.add(conDesplazamiento(tablaDeCarrera(resultados, torneo), ALTO_MAXIMO_TABLA)).width(ANCHO_COLUMNA_TORNEO).top()
            .padRight(SEPARACION_TABLAS);
        tablas.add(conDesplazamiento(tablaAcumulada(torneo), ALTO_MAXIMO_TABLA)).width(ANCHO_TABLA_TORNEO).top();
        contenido.add(tablas).padBottom(14).row();

        float anchoTotal = ANCHO_COLUMNA_TORNEO + SEPARACION_TABLAS + ANCHO_TABLA_TORNEO;
        if (sesion.esAnfitrion()) {
            Table botones = new Table();
            botones.add(termino
                ? botonPrimario("REVANCHA", () -> sesion.cliente().revancha())
                : botonPrimario("SIGUIENTE: " + Catalogo.circuito(torneo.siguiente()).gp().toUpperCase(Locale.ROOT), () -> sesion.cliente().siguienteCarrera()))
                .growX().uniformX().height(ALTO_BOTON).padRight(8);
            botones.add(boton("CERRAR LA PARTIDA", () -> irA(new MenuPrincipal(juego)))).growX().uniformX().height(ALTO_BOTON);
            contenido.add(botones).width(anchoTotal).row();
            mensaje.setText(termino ? "La revancha vuelve a la sala de espera con los mismos jugadores" : "");
        } else {
            contenido.add(boton("VOLVER AL MENÚ", () -> irA(new MenuPrincipal(juego)))).width(anchoTotal).height(ALTO_BOTON).row();
            mensaje.setText(termino ? "Esperando que el anfitrión elija la revancha..." : "Esperando que el anfitrión largue la carrera siguiente...");
        }
        contenido.add(mensaje).left().padTop(10);
    }

    private Table tablaDeCarrera(List<ResultadoJugador> resultados, EstadoTorneo torneo) {
        Table tabla = new Table();
        tabla.add(encabezado(ENCABEZADO_TORNEO, ANCHOS_TORNEO)).growX().padBottom(6).row();
        for (ResultadoJugador resultado : resultados) {
            FilaTorneo puntos = torneo.fila(resultado.id());
            Table fila = filaBase(resultado.id(), resultado.posicion(), ANCHOS_TORNEO[0]);
            fila.add(new Label(resultado.nombre(), skin, "subtitulo")).width(ANCHOS_TORNEO[1]).left();
            fila.add(new Label(Tiempo.formato(resultado.tiempoTotal()), skin, "numeros")).width(ANCHOS_TORNEO[2]).left();
            fila.add(new Label(Tiempo.formato(resultado.mejorVuelta()), skin, "numeros")).width(ANCHOS_TORNEO[3]).left();
            fila.add(new Label("+" + (puntos != null ? puntos.puntosCarrera() : 0), skin, "numeros")).width(ANCHOS_TORNEO[4]).left();
            tabla.add(fila).growX().height(ALTO_FILA).padBottom(SEPARACION_FILAS).row();
        }
        return tabla;
    }

    private Table tablaAcumulada(EstadoTorneo torneo) {
        Table tabla = new Table();
        tabla.add(encabezado(ENCABEZADO_TABLA, ANCHOS_TABLA)).growX().padBottom(6).row();
        List<FilaTorneo> filas = torneo.tabla();
        for (int i = 0; i < filas.size(); i++) {
            FilaTorneo acumulado = filas.get(i);
            Table fila = filaBase(acumulado.id(), i + 1, ANCHOS_TABLA[0]);
            fila.add(new Label(acumulado.nombre(), skin, "subtitulo")).width(ANCHOS_TABLA[1]).left();
            fila.add(new Label(String.valueOf(acumulado.puntos()), skin, "numeros")).width(ANCHOS_TABLA[2]).left();
            fila.add(new Label(String.valueOf(acumulado.victorias()), skin, "numeros")).width(ANCHOS_TABLA[3]).left();
            tabla.add(fila).growX().height(ALTO_FILA).padBottom(SEPARACION_FILAS).row();
        }
        return tabla;
    }

    private Table encabezado(String[] textos, float[] anchos) {
        Table encabezado = new Table();
        encabezado.pad(0f, PADDING_FILA, 0f, PADDING_FILA);
        for (int i = 0; i < textos.length; i++) {
            encabezado.add(new Label(textos[i], skin, "etiqueta")).width(anchos[i]).left();
        }
        return encabezado;
    }

    /** Una fila con el fondo, la franja de la escudería y la posición: el dato en sí lo agrega quien la pide. */
    private Table filaBase(int id, int lugar, float anchoPosicion) {
        Table fila = new Table();
        fila.setBackground(recursos.conLinea(Paleta.SUPERFICIE_ALTA, colorDeFranja(id), FondoConLinea.Lado.IZQUIERDA));
        fila.pad(0f, PADDING_FILA, 0f, PADDING_FILA);
        Label posicion = new Label(String.format(Locale.ROOT, "%02d", lugar), skin, "posicion");
        if (lugar == 1) {
            posicion.setColor(Paleta.DORADO);
        }
        fila.add(posicion).width(anchoPosicion).left();
        return fila;
    }

    private Table tabla(List<ResultadoJugador> resultados) {
        Table tabla = new Table();
        tabla.add(encabezado(ENCABEZADO, ANCHOS)).growX().padBottom(6).row();
        for (ResultadoJugador resultado : resultados) {
            tabla.add(fila(resultado)).growX().height(ALTO_FILA).padBottom(SEPARACION_FILAS).row();
        }
        return tabla;
    }

    /** Una fila al estilo de las posiciones de PIT WALL: franja de la escudería y el número en cursiva (dorado el primero). */
    private Table fila(ResultadoJugador resultado) {
        Table fila = filaBase(resultado.id(), resultado.posicion(), ANCHOS[0]);
        fila.add(new Label(resultado.nombre(), skin, "subtitulo")).width(ANCHOS[1]).left();
        fila.add(new Label(Tiempo.formato(resultado.tiempoTotal()), skin, "numeros")).width(ANCHOS[2]).left();
        fila.add(new Label(Tiempo.formato(resultado.mejorVuelta()), skin, "numeros")).width(ANCHOS[3]).left();
        return fila;
    }

    /** En red, el color de la escudería del jugador; en la prueba local no hay lobby que lo diga. */
    private Color colorDeFranja(int id) {
        SesionRed sesion = juego.sesion();
        JugadorLobby jugador = sesion != null ? sesion.cliente().lobby().jugador(id) : null;
        return jugador != null ? Paleta.colorDePiloto(jugador.piloto()) : Paleta.SUPERFICIE_MAXIMA;
    }

    /**
     * En red, la partida sigue abierta mientras se ven los resultados: se manda el PING y se atiende lo que llega.
     * Con un LOBBY (el host pidió revancha) se pasa a la sala de espera; con CUENTA_REGRESIVA (la carrera siguiente de un
     * torneo), a la carrera; con CERRADA o si se corta, al menú.
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
                case Protocolo.CUENTA_REGRESIVA:
                    irA(PantallaCarrera.desde(juego, Protocolo.leerCuenta(recibido)));
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
