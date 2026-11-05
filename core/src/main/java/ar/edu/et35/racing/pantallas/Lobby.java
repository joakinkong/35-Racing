package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.InfoCircuito;
import ar.edu.et35.racing.juego.Piloto;
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
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import java.util.List;

/**
 * Sala de espera en vivo. Muestra los jugadores que informa el servidor, deja elegir piloto (una escudería por
 * jugador), marcar "listo" y, al host, elegir el circuito y las vueltas e iniciar. Los mensajes de la red se procesan
 * acá, en el hilo de render.
 *
 * <p>Dos columnas: a la izquierda los jugadores y la carrera (circuito y vueltas); a la derecha la grilla de pilotos y
 * los botones. Los textos de ancho fijo llevan puntos suspensivos para que nunca se pisen con el de al lado.
 */
public class Lobby extends PantallaBase {
    private static final float ANCHO_COLUMNA_IZQUIERDA = 280f;
    private static final float ANCHO_COLUMNA_DERECHA = 322f;
    private static final float SEPARACION_COLUMNAS = 8f;
    private static final float ALTO_FILA_JUGADOR = 22f;
    private static final float ANCHO_NOMBRE_JUGADOR = 132f;
    private static final float ANCHO_PILOTO_JUGADOR = 68f;
    private static final float ALTO_FILA_PILOTO = 17f;
    /** La fuente del título está a escala 3: en esta sala sobra espacio vertical, así que se achica. */
    private static final float ESCALA_TITULO = 0.67f;
    private static final float ANCHO_ESCUDERIA = 104f;
    private static final float ANCHO_BOTON_PILOTO = 100f;
    private static final float BORDE_PILOTO = 1f;
    private static final float ALTO_BOTON = 26f;
    private static final float ALTO_SELECTOR = 20f;
    private static final float ANCHO_FLECHA = 30f;
    private static final float ANCHO_ETIQUETA_SELECTOR = 90f;

    private final ClientePartida cliente;
    private final boolean anfitrion;
    private final Table panelJugadores = new Table();
    private final Table panelCarrera = new Table();
    private final Table panelPilotos = new Table();
    private final Table panelBotones = new Table();
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
        panelCarrera.top();
        panelPilotos.top();
        panelBotones.top();

        Table izquierda = new Table();
        izquierda.top();
        izquierda.add(panelJugadores).width(ANCHO_COLUMNA_IZQUIERDA).padBottom(8).row();
        izquierda.add(panelCarrera).width(ANCHO_COLUMNA_IZQUIERDA).left();
        Table derecha = new Table();
        derecha.top();
        derecha.add(panelPilotos).width(ANCHO_COLUMNA_DERECHA).padBottom(6).row();
        derecha.add(panelBotones).width(ANCHO_COLUMNA_DERECHA);

        Label titulo = new Label("SALA DE ESPERA", skin, "titulo");
        titulo.setFontScale(ESCALA_TITULO);
        contenido.add(titulo).padBottom(4).row();
        Table cuerpo = new Table();
        cuerpo.add(izquierda).width(ANCHO_COLUMNA_IZQUIERDA).top().padRight(SEPARACION_COLUMNAS);
        cuerpo.add(derecha).width(ANCHO_COLUMNA_DERECHA).top();
        contenido.add(cuerpo).row();
        contenido.add(mensajeError).height(18).padTop(2).row();
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
        panelCarrera.clearChildren();
        panelPilotos.clearChildren();
        panelBotones.clearChildren();
        if (cliente == null) {
            return;
        }
        textoDeRed.setText(armarTextoDeRed());
        EstadoLobby lobby = cliente.lobby();
        armarJugadores(lobby);
        armarCarrera(lobby);
        armarPilotos(lobby);
        armarBotones(lobby);
    }

    /** Un texto de ancho fijo: si no entra, se corta con puntos suspensivos en lugar de pisar al de al lado. */
    private Label etiqueta(String texto, String estilo) {
        Label label = new Label(texto, skin, estilo);
        label.setEllipsis(true);
        return label;
    }

    private void armarJugadores(EstadoLobby lobby) {
        for (int i = 0; i < Config.MAX_JUGADORES; i++) {
            if (i >= lobby.jugadores().size()) {
                panelJugadores.add(etiqueta("libre", "gris")).colspan(3).height(ALTO_FILA_JUGADOR).left().row();
                continue;
            }
            JugadorLobby jugador = lobby.jugadores().get(i);
            Piloto piloto = Catalogo.piloto(jugador.piloto());
            String texto = jugador.nombre();
            if (jugador.id() == cliente.miId()) {
                texto += " (vos)";
            }
            if (jugador.id() == lobby.idHost()) {
                texto += " [host]";
            }
            // Una sola línea por jugador: con dos, el piloto de uno se pisaba con el nombre del siguiente.
            panelJugadores.add(new Image(recursos.color(Paleta.colorDePiloto(jugador.piloto())))).size(8, 16).padRight(6);
            panelJugadores.add(etiqueta(texto, "default")).width(ANCHO_NOMBRE_JUGADOR).height(ALTO_FILA_JUGADOR).left();
            panelJugadores.add(etiqueta(piloto.apellido(), "gris")).width(ANCHO_PILOTO_JUGADOR).height(ALTO_FILA_JUGADOR).left();
            panelJugadores.add(jugador.listo() ? new Label("LISTO", skin, "verde") : new Label("...", skin, "gris"))
                .right().expandX().row();
        }
    }

    /** Circuito y vueltas: el host los cambia con las flechas; los demás los ven. */
    private void armarCarrera(EstadoLobby lobby) {
        InfoCircuito info = Catalogo.circuito(lobby.circuito());
        panelCarrera.add(etiqueta("CARRERA", "gris")).width(ANCHO_COLUMNA_IZQUIERDA).left().padBottom(2).row();
        panelCarrera.add(etiqueta(info.gp() + " - " + info.ciudad(), "default")).width(ANCHO_COLUMNA_IZQUIERDA).left().row();
        panelCarrera.add(etiqueta(String.valueOf(info.km()).replace('.', ',') + " km - " + info.curvas() + " curvas", "gris"))
            .width(ANCHO_COLUMNA_IZQUIERDA).left().padBottom(4).row();

        int totalCircuitos = Catalogo.CIRCUITOS.size();
        panelCarrera.add(selector("CIRCUITO", (lobby.circuito() + 1) + " / " + totalCircuitos,
            () -> cliente.elegirCircuito(Math.floorMod(lobby.circuito() - 1, totalCircuitos)),
            () -> cliente.elegirCircuito(Math.floorMod(lobby.circuito() + 1, totalCircuitos)))).width(ANCHO_COLUMNA_IZQUIERDA)
            .padBottom(3).row();
        panelCarrera.add(selector("VUELTAS", String.valueOf(lobby.vueltas()),
            () -> cliente.elegirVueltas(Math.max(Config.MIN_VUELTAS, lobby.vueltas() - 1)),
            () -> cliente.elegirVueltas(Math.min(Config.MAX_VUELTAS, lobby.vueltas() + 1)))).width(ANCHO_COLUMNA_IZQUIERDA)
            .row();
        if (!anfitrion) {
            panelCarrera.add(etiqueta("Las elige el anfitrión", "gris")).width(ANCHO_COLUMNA_IZQUIERDA).left().padTop(2);
        }
    }

    /** Una fila "ETIQUETA  <  valor  >"; solo el host tiene las flechas. */
    private Table selector(String nombre, String valor, Runnable menos, Runnable mas) {
        Table fila = new Table();
        fila.add(etiqueta(nombre, "gris")).width(ANCHO_ETIQUETA_SELECTOR).left();
        if (anfitrion) {
            fila.add(boton("<", menos)).width(ANCHO_FLECHA).height(ALTO_SELECTOR);
            fila.add(new Label(valor, skin)).expandX();
            fila.add(boton(">", mas)).width(ANCHO_FLECHA).height(ALTO_SELECTOR);
        } else {
            fila.add(new Label(valor, skin)).left().expandX();
        }
        return fila;
    }

    /** Una fila por escudería con sus dos pilotos. Si otro jugador ya tiene la escudería, sus dos botones quedan bloqueados. */
    private void armarPilotos(EstadoLobby lobby) {
        JugadorLobby yo = lobby.jugador(cliente.miId());
        panelPilotos.add(etiqueta("ELEGÍ TU PILOTO", "gris")).colspan(4).left().padBottom(3).row();
        for (int escuderia = 0; escuderia < Catalogo.ESCUDERIAS.size(); escuderia++) {
            panelPilotos.add(new Image(recursos.color(Paleta.colorDeEscuderia(escuderia)))).size(6, ALTO_FILA_PILOTO - 3)
                .padRight(4);
            panelPilotos.add(etiqueta(Catalogo.ESCUDERIAS.get(escuderia).nombre(), "gris")).width(ANCHO_ESCUDERIA)
                .height(ALTO_FILA_PILOTO).left();
            for (int k = 0; k < 2; k++) {
                panelPilotos.add(botonPiloto(lobby, yo, escuderia * 2 + k)).width(ANCHO_BOTON_PILOTO + 2 * BORDE_PILOTO)
                    .height(ALTO_FILA_PILOTO);
            }
            panelPilotos.row();
        }
    }

    /**
     * Un botón con el color de la escudería y el apellido del piloto. El propio lleva un borde blanco; si la escudería
     * la tiene otro jugador, queda deshabilitado.
     */
    private Table botonPiloto(EstadoLobby lobby, JugadorLobby yo, int piloto) {
        int escuderia = Catalogo.piloto(piloto).escuderia();
        JugadorLobby duenioEscuderia = lobby.duenioDeEscuderia(escuderia);
        boolean esMio = lobby.duenioDelPiloto(piloto) != null && yo != null && lobby.duenioDelPiloto(piloto).id() == yo.id();
        boolean ocupadoPorOtro = duenioEscuderia != null && (yo == null || duenioEscuderia.id() != yo.id());

        Color color = Paleta.colorDeEscuderia(escuderia);
        TextButtonStyle estilo = new TextButtonStyle(recursos.color(color), recursos.color(color.cpy().mul(0.7f, 0.7f, 0.7f, 1f)),
            null, skin.getFont("normal"));
        estilo.over = recursos.color(color.cpy().lerp(Color.WHITE, 0.3f));
        estilo.disabled = recursos.color(Paleta.GRIS);
        estilo.fontColor = Paleta.textoSobre(color);
        estilo.disabledFontColor = Paleta.GRIS_CLARO;

        TextButton boton = new TextButton(Catalogo.piloto(piloto).apellido(), estilo);
        boton.getLabel().setEllipsis(true);
        boton.getLabelCell().width(ANCHO_BOTON_PILOTO - 4);
        boton.setDisabled(ocupadoPorOtro);
        boton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent evento, Actor actor) {
                if (!esMio) {
                    cliente.elegirPiloto(piloto);
                }
            }
        });

        Table marco = new Table();
        if (esMio) {
            marco.setBackground(recursos.color(Paleta.BLANCO));
        }
        marco.add(boton).width(ANCHO_BOTON_PILOTO).height(ALTO_FILA_PILOTO - 2 * BORDE_PILOTO).pad(BORDE_PILOTO);
        return marco;
    }

    private void armarBotones(EstadoLobby lobby) {
        JugadorLobby yo = lobby.jugador(cliente.miId());
        boolean estoyListo = yo != null && yo.listo();
        TextButton botonListo = boton(estoyListo ? "CANCELAR LISTO" : "ESTOY LISTO", () -> cliente.marcarListo(!estoyListo));
        if (!anfitrion) {
            panelBotones.add(botonListo).width(ANCHO_COLUMNA_DERECHA).height(ALTO_BOTON);
            return;
        }
        float ancho = (ANCHO_COLUMNA_DERECHA - 6f) / 2f;
        TextButton botonIniciar = boton("INICIAR CARRERA", cliente::iniciar);
        botonIniciar.setDisabled(!lobby.sePuedeIniciar());
        panelBotones.add(botonListo).width(ancho).height(ALTO_BOTON).padRight(6);
        panelBotones.add(botonIniciar).width(ancho).height(ALTO_BOTON).row();
        if (!lobby.sePuedeIniciar()) {
            panelBotones.add(etiqueta("Hacen falta " + Config.MIN_JUGADORES + " jugadores y todos listos", "gris"))
                .colspan(2).width(ANCHO_COLUMNA_DERECHA).left().padTop(2);
        }
    }
}
