package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.red.DireccionesLocales;
import ar.edu.et35.racing.red.EstadoLobby;
import ar.edu.et35.racing.red.EstadoLobby.JugadorLobby;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import java.util.List;

/**
 * Sala de espera en vivo. Muestra los jugadores que informa el servidor, deja elegir auto (sin repetir), marcar
 * "listo" y, al host, iniciar. Los mensajes de la red se procesan acá, en el hilo de render.
 */
public class Lobby extends PantallaBase {
    private static final float ANCHO_PANEL_JUGADORES = 290f;
    private static final float ANCHO_PANEL_CONTROLES = 270f;
    private static final float ALTO_FILA = 22f;
    private static final float ANCHO_MUESTRA = 40f;
    private static final float ALTO_MUESTRA = 28f;
    private static final float BORDE_MUESTRA = 2f;

    private final ClientePartida cliente;
    private final boolean anfitrion;
    private final Table panelJugadores = new Table();
    private final Table panelControles = new Table();
    private final Label mensajeError = new Label("", skin, "rojo");
    private final Label textoDeRed = new Label("", skin, "gris");
    private boolean navego;

    public Lobby(Main juego) {
        super(juego);
        SesionRed sesion = juego.sesion();
        this.cliente = sesion != null ? sesion.cliente() : null;
        this.anfitrion = sesion != null && sesion.esAnfitrion();

        panelJugadores.setBackground(recursos.color(Paleta.GRIS_OSCURO));
        panelJugadores.pad(6);
        panelJugadores.top();
        panelControles.top();

        contenido.add(new Label("SALA DE ESPERA", skin, "titulo")).padBottom(8).row();
        Table cuerpo = new Table();
        cuerpo.add(panelJugadores).width(ANCHO_PANEL_JUGADORES).top().padRight(10);
        cuerpo.add(panelControles).width(ANCHO_PANEL_CONTROLES).top();
        contenido.add(cuerpo).row();
        contenido.add(mensajeError).height(18).padTop(4).row();
        contenido.add(textoDeRed).padTop(2).row();
        contenido.add(new Label("ESC: salir de la partida", skin, "gris")).padTop(2);

        reconstruir();
    }

    /** Si somos el host, las IPs para dictarles a los demás; si no, con quién estamos conectados (se actualiza con el lobby). */
    private String armarTextoDeRed() {
        if (cliente == null) {
            return "";
        }
        if (anfitrion) {
            List<String> ips = DireccionesLocales.listar();
            String direcciones = ips.isEmpty() ? "(no se encontró ninguna red)" : String.join("  /  ", ips);
            return "Pasales tu IP a los demás: " + direcciones + "   (puerto " + Config.PUERTO_TCP + ")";
        }
        return "Conectado a la partida de " + nombreDelHost();
    }

    private String nombreDelHost() {
        JugadorLobby host = cliente.lobby().jugador(cliente.lobby().idHost());
        return host != null ? host.nombre() : "?";
    }

    // ------------------------------------------------------------------ red

    @Override
    protected void actualizar(float delta) {
        if (cliente == null) {
            volver();
            return;
        }
        if (navego) {
            return;
        }
        cliente.actualizar();
        boolean cambioElLobby = false;
        Mensaje mensaje;
        while ((mensaje = cliente.sondear()) != null) {
            switch (mensaje.nombre()) {
                case Protocolo.LOBBY:
                    cambioElLobby = true;
                    mensajeError.setText("");
                    break;
                case Protocolo.ERROR:
                    mensajeError.setText(mensaje.campo(1));
                    break;
                case Protocolo.CUENTA_REGRESIVA:
                    empezarCarrera(mensaje);
                    return;
                case Protocolo.CERRADA:
                    salir("La partida se cerró: " + mensaje.campo(0));
                    return;
                case Protocolo.CONEXION_PERDIDA:
                    salir(mensaje.campo(0));
                    return;
                default:
                    break;
            }
        }
        if (cambioElLobby) {
            reconstruir();
        }
    }

    /** CUENTA_REGRESIVA trae el circuito y las vueltas; la carrera la simula el servidor y acá solo se dibuja. */
    private void empezarCarrera(Mensaje mensaje) {
        navego = true;
        juego.irA(new PantallaCarrera(juego, mensaje.campo(0), mensaje.entero(1, Config.VUELTAS)));
    }

    private void salir(String aviso) {
        navego = true;
        juego.irA(new MenuPrincipal(juego, aviso));
    }

    @Override
    protected void volver() {
        salir(null);
    }

    // ------------------------------------------------------------------ dibujo de los paneles

    private void reconstruir() {
        panelJugadores.clearChildren();
        panelControles.clearChildren();
        if (cliente == null) {
            return;
        }
        textoDeRed.setText(armarTextoDeRed());
        EstadoLobby lobby = cliente.lobby();
        armarJugadores(lobby);
        armarControles(lobby);
    }

    private void armarJugadores(EstadoLobby lobby) {
        for (int i = 0; i < Config.MAX_JUGADORES; i++) {
            if (i >= lobby.jugadores().size()) {
                panelJugadores.add(new Label("libre", skin, "gris")).colspan(3).height(ALTO_FILA).left().row();
                continue;
            }
            JugadorLobby jugador = lobby.jugadores().get(i);
            String texto = jugador.nombre();
            if (jugador.id() == cliente.miId()) {
                texto += "  (vos)";
            }
            if (jugador.id() == lobby.idHost()) {
                texto += "  [anfitrión]";
            }
            panelJugadores.add(new Image(recursos.color(colorDe(jugador.auto())))).size(12).padRight(8);
            panelJugadores.add(new Label(texto, skin)).height(ALTO_FILA).left().expandX();
            panelJugadores.add(jugador.listo() ? new Label("LISTO", skin, "verde") : new Label("esperando", skin, "gris"))
                .right().row();
        }
    }

    private void armarControles(EstadoLobby lobby) {
        JugadorLobby yo = lobby.jugador(cliente.miId());
        boolean estoyListo = yo != null && yo.listo();

        panelControles.add(new Label("ELEGÍ TU AUTO", skin, "gris")).left().padBottom(4).row();
        Table muestras = new Table();
        for (int auto = 0; auto < Config.MAX_JUGADORES; auto++) {
            muestras.add(muestra(lobby, yo, auto)).padRight(4);
        }
        panelControles.add(muestras).left().padBottom(10).row();

        TextButton botonListo = boton(estoyListo ? "CANCELAR LISTO" : "ESTOY LISTO", () -> cliente.marcarListo(!estoyListo));
        panelControles.add(botonListo).width(ANCHO_PANEL_CONTROLES).height(28).padBottom(6).row();

        if (anfitrion) {
            TextButton botonIniciar = boton("INICIAR CARRERA", cliente::iniciar);
            botonIniciar.setDisabled(!lobby.sePuedeIniciar());
            panelControles.add(botonIniciar).width(ANCHO_PANEL_CONTROLES).height(28).padBottom(4).row();
            if (!lobby.sePuedeIniciar()) {
                panelControles.add(new Label("Hacen falta " + Config.MIN_JUGADORES + " jugadores y todos listos", skin, "gris"))
                    .left();
            }
        }
    }

    /**
     * Un cuadradito de color por auto. El propio lleva un borde blanco; los que eligió otro jugador quedan
     * deshabilitados y muestran la inicial de su dueño.
     */
    private Table muestra(EstadoLobby lobby, JugadorLobby yo, int auto) {
        JugadorLobby duenio = lobby.duenioDelAuto(auto);
        boolean esMio = duenio != null && yo != null && duenio.id() == yo.id();
        boolean ocupadoPorOtro = duenio != null && !esMio;

        Color color = colorDe(auto);
        TextButtonStyle estilo = new TextButtonStyle(recursos.color(color), recursos.color(color.cpy().mul(0.7f, 0.7f, 0.7f, 1f)),
            null, skin.getFont("normal"));
        estilo.over = recursos.color(color.cpy().lerp(Color.WHITE, 0.3f));
        estilo.disabled = recursos.color(Paleta.GRIS);
        estilo.fontColor = Paleta.BLANCO;
        estilo.disabledFontColor = Paleta.GRIS_CLARO;

        String inicial = ocupadoPorOtro ? duenio.nombre().substring(0, 1).toUpperCase() : "";
        TextButton boton = new TextButton(inicial, estilo);
        boton.setDisabled(ocupadoPorOtro);
        boton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent evento, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!esMio) {
                    cliente.elegirAuto(auto);
                }
            }
        });

        Table marco = new Table();
        if (esMio) {
            marco.setBackground(recursos.color(Paleta.BLANCO));
        }
        marco.add(boton).width(ANCHO_MUESTRA).height(ALTO_MUESTRA).pad(BORDE_MUESTRA);
        return marco;
    }

    private static Color colorDe(int auto) {
        return Paleta.COLORES_AUTOS[Math.floorMod(auto, Paleta.COLORES_AUTOS.length)];
    }
}
