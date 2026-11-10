package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.InfoCircuito;
import ar.edu.et35.racing.juego.ModoGrilla;
import ar.edu.et35.racing.juego.NivelBot;
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
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import java.util.List;
import java.util.StringJoiner;

/**
 * Sala de espera en vivo. Muestra los jugadores que informa el servidor, deja elegir piloto (una escudería por
 * jugador), marcar "listo" y, al host, elegir modo, circuito o calendario, vueltas, clasificación y bots, e iniciar. Los mensajes de la red se procesan
 * acá, en el hilo de render.
 *
 * <p>Dos columnas de tarjetas: a la izquierda los jugadores y la carrera (modo, circuito y vueltas); a la derecha la grilla de
 * pilotos o, si se pide en un torneo, el calendario, y los botones. Los textos de ancho fijo llevan puntos suspensivos para que nunca se pisen con el de al lado.
 */
public class Lobby extends PantallaBase {
    private static final float ANCHO_COLUMNA_IZQUIERDA = 540f;
    private static final float ANCHO_COLUMNA_DERECHA = 660f;
    private static final float SEPARACION_COLUMNAS = 16f;
    private static final float PADDING_TARJETA = 18f;
    private static final float ANCHO_DENTRO_IZQUIERDA = ANCHO_COLUMNA_IZQUIERDA - 2 * PADDING_TARJETA;
    private static final float ANCHO_DENTRO_DERECHA = ANCHO_COLUMNA_DERECHA - 2 * PADDING_TARJETA;
    private static final float ALTO_FILA_JUGADOR = 26f;
    /** Aire a la derecha de cada fila de la lista, para que la barra de desplazamiento no tape el estado. */
    private static final float PADDING_BARRA = 12f;
    /** Alto visible de la lista de autos: con 22 no entran todos y el resto se ve desplazando. */
    private static final float ALTO_LISTA_JUGADORES = 206f;
    private static final float ANCHO_NIVEL_BOT = 92f;
    private static final float ANCHO_QUITAR_BOT = 28f;
    private static final float ALTO_BOTON_BOT = 25f;
    private static final float ANCHO_NOMBRE_JUGADOR = 230f;
    private static final float ANCHO_NOMBRE_CON_FLECHAS = 160f;
    private static final float ANCHO_LUGAR = 28f;
    private static final float ANCHO_FLECHA_GRILLA = 24f;
    private static final float ANCHO_SORTEO = 150f;
    private static final float ANCHO_PILOTO_JUGADOR = 60f;
    private static final float ALTO_FILA_PILOTO = 32f;
    private static final float ANCHO_ESCUDERIA = 122f;
    private static final float ANCHO_FRANJA = 5f;
    private static final float ANCHO_BOTON_PILOTO = 236f;
    private static final float ANCHO_PESTANIA = 200f;
    private static final float BORDE_PILOTO = 2f;
    private static final float ALTO_BOTON = 48f;
    private static final float ALTO_SELECTOR = 30f;
    private static final float ANCHO_FLECHA = 48f;
    private static final float ANCHO_ETIQUETA_SELECTOR = 150f;
    private static final int COLUMNAS_CALENDARIO = 4;
    private static final float ALTO_FILA_CALENDARIO = 24f;
    private static final float SEPARACION_CALENDARIO = 2f;
    private static final float ANCHO_CELDA_CALENDARIO = ANCHO_DENTRO_DERECHA / COLUMNAS_CALENDARIO - SEPARACION_CALENDARIO;

    private final ClientePartida cliente;
    private final boolean anfitrion;
    private final Table panelJugadores = tarjeta(Paleta.SECUNDARIO);
    private final Table panelCarrera = tarjeta();
    private final Table panelPilotos = tarjeta();
    private final Table panelBotones = new Table();
    private final Label mensajeError = new Label("", skin, "alerta");
    private final Label textoDeRed = new Label("", skin, "suave");
    private boolean navego;
    private ScrollPane listaJugadores;
    /** Si la columna derecha muestra el calendario del torneo en lugar de los pilotos. */
    private boolean verCalendario;
    /** Solo el host: si tocar un piloto de la grilla suma un bot con ese piloto, en lugar de elegirlo para sí. */
    private boolean agregandoBots;
    /** Nivel con el que el host suma los bots nuevos. */
    private NivelBot nivelNuevoBot = NivelBot.MEDIO;
    /** Dónde estaba la lista de autos, para que al llegar un LOBBY nuevo no vuelva al principio. */
    private float desplazamientoLista;

    public Lobby(Main juego) {
        super(juego);
        SesionRed sesion = juego.sesion();
        this.cliente = sesion != null ? sesion.cliente() : null;
        this.anfitrion = sesion != null && sesion.esAnfitrion();

        panelBotones.top();

        Table izquierda = new Table();
        izquierda.top();
        izquierda.add(panelJugadores).width(ANCHO_COLUMNA_IZQUIERDA).padBottom(SEPARACION_COLUMNAS).row();
        izquierda.add(panelCarrera).width(ANCHO_COLUMNA_IZQUIERDA).left();
        Table derecha = new Table();
        derecha.top();
        derecha.add(panelPilotos).width(ANCHO_COLUMNA_DERECHA).padBottom(SEPARACION_COLUMNAS).row();
        derecha.add(panelBotones).width(ANCHO_COLUMNA_DERECHA);

        Table cabecera = new Table();
        cabecera.add(titular("MULTIJUGADOR  ·  RED LOCAL", "SALA DE ESPERA")).left().expandX();
        cabecera.add(textoDeRed).right().bottom();
        contenido.add(cabecera).growX().padBottom(10).row();
        Table cuerpo = new Table();
        cuerpo.add(izquierda).width(ANCHO_COLUMNA_IZQUIERDA).top().padRight(SEPARACION_COLUMNAS);
        cuerpo.add(derecha).width(ANCHO_COLUMNA_DERECHA).top();
        contenido.add(cuerpo).row();
        Table pie = new Table();
        pie.add(mensajeError).left().expandX();
        pie.add(new Label("ESC: SALIR DE LA PARTIDA", skin, "etiqueta")).right();
        contenido.add(pie).growX().height(24).padTop(6);

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
            return "Tu IP: " + direcciones + "   ·   Puerto " + Config.PUERTO_TCP;
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

    /** CUENTA_REGRESIVA trae lo que hace falta para correr; la carrera la simula el servidor y acá solo se dibuja. */
    private void empezarCarrera(Mensaje mensaje) {
        navego = true;
        juego.irA(PantallaCarrera.desde(juego, Protocolo.leerCuenta(mensaje)));
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
        if (listaJugadores != null) {
            desplazamientoLista = listaJugadores.getScrollY();
        }
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
        if (lobby.torneo() && verCalendario) {
            armarCalendario(lobby);
        } else {
            armarPilotos(lobby);
        }
        armarBotones(lobby);
    }

    /** Un texto de ancho fijo: si no entra, se corta con puntos suspensivos en lugar de pisar al de al lado. */
    private Label etiqueta(String texto, String estilo) {
        Label label = new Label(texto, skin, estilo);
        label.setEllipsis(true);
        return label;
    }

    /**
     * La lista de autos de la sala, en el orden de la grilla de largada, con un contador y desplazamiento si no entran todos.
     * Con la grilla a mano, el host sube y baja a cada auto con las flechas o la sortea.
     */
    private void armarJugadores(EstadoLobby lobby) {
        boolean armaElHost = anfitrion && lobby.modoGrilla() == ModoGrilla.MANUAL;
        Table cabecera = new Table();
        cabecera.add(etiqueta("AUTOS  " + lobby.jugadores().size() + " / " + lobby.capacidad() + "   ·   PERSONAS  "
            + lobby.cantidadDePersonas() + " / " + Math.min(lobby.capacidad(), Config.MAX_PERSONAS), "etiqueta")).left().expandX();
        if (armaElHost) {
            cabecera.add(boton("SORTEAR GRILLA", cliente::sortearGrilla)).size(ANCHO_SORTEO, ALTO_BOTON_BOT);
        }
        panelJugadores.add(cabecera).width(ANCHO_DENTRO_IZQUIERDA).padBottom(2).row();
        panelJugadores.add(etiqueta(textoDeGrilla(lobby), "apagado")).width(ANCHO_DENTRO_IZQUIERDA).left().padBottom(4).row();
        Table lista = new Table();
        lista.top();
        List<JugadorLobby> jugadores = lobby.jugadores();
        for (int lugar = 0; lugar < jugadores.size(); lugar++) {
            JugadorLobby jugador = jugadores.get(lugar);
            lista.add(new Image(recursos.color(Paleta.colorDePiloto(jugador.piloto())))).size(ANCHO_FRANJA, 20).padRight(10);
            lista.add(etiqueta(String.valueOf(lugar + 1), "etiqueta")).width(ANCHO_LUGAR).height(ALTO_FILA_JUGADOR).left();
            String texto = jugador.esBot() ? jugador.nombre() : jugador.nombre()
                + (jugador.id() == cliente.miId() ? " (vos)" : "") + (jugador.id() == lobby.idHost() ? " [host]" : "");
            // Una sola línea por jugador: con dos, el piloto de uno se pisaba con el nombre del siguiente.
            lista.add(etiqueta(texto, "default")).width(armaElHost ? ANCHO_NOMBRE_CON_FLECHAS : ANCHO_NOMBRE_JUGADOR - ANCHO_LUGAR)
                .height(ALTO_FILA_JUGADOR).left();
            lista.add(etiqueta(Catalogo.piloto(jugador.piloto()).codigo(), "suave")).width(ANCHO_PILOTO_JUGADOR).height(ALTO_FILA_JUGADOR).left();
            if (armaElHost) {
                Table flechas = new Table();
                TextButton sube = boton("^", () -> cliente.moverEnGrilla(jugador.id(), -1));
                sube.setDisabled(lugar == 0);
                TextButton baja = boton("v", () -> cliente.moverEnGrilla(jugador.id(), 1));
                baja.setDisabled(lugar == jugadores.size() - 1);
                flechas.add(sube).size(ANCHO_FLECHA_GRILLA, ALTO_BOTON_BOT).padRight(2);
                flechas.add(baja).size(ANCHO_FLECHA_GRILLA, ALTO_BOTON_BOT);
                lista.add(flechas).padRight(6);
            }
            if (jugador.esBot()) {
                lista.add(controlDeBot(jugador)).right().expandX().padRight(PADDING_BARRA).row();
            } else {
                lista.add(jugador.listo() ? new Label("LISTO", skin, "seccion") : new Label("ESPERANDO", skin, "etiqueta"))
                    .right().expandX().padRight(PADDING_BARRA).row();
            }
        }
        ScrollPane desplazable = new ScrollPane(lista, skin);
        desplazable.setScrollingDisabled(true, false);
        desplazable.setFadeScrollBars(false);
        desplazable.setOverscroll(false, false);
        panelJugadores.add(desplazable).width(ANCHO_DENTRO_IZQUIERDA).height(ALTO_LISTA_JUGADORES).top();
        // Con la lista ya armada se recupera dónde estaba; la altura se calcula recién al hacer el layout.
        desplazable.setSize(ANCHO_DENTRO_IZQUIERDA, ALTO_LISTA_JUGADORES);
        desplazable.layout();
        desplazable.setScrollY(desplazamientoLista);
        desplazable.updateVisualScroll();
        this.listaJugadores = desplazable;
    }

    /** Una línea que dice de dónde sale el orden de largada, según el modo de la grilla. */
    private String textoDeGrilla(EstadoLobby lobby) {
        switch (lobby.modoGrilla()) {
            case CLASIFICACION:
                return "Largada: el orden de la clasificación";
            case AL_AZAR:
                return "Largada: se sortea antes de cada carrera";
            default:
                return anfitrion ? "Largada: la de esta lista. Subí y bajá con ^ y v, o sorteá" : "Largada: la de esta lista (la arma el anfitrión)";
        }
    }

    /** El nivel de un bot. El host lo cambia tocándolo y saca al bot con la X. */
    private Table controlDeBot(JugadorLobby bot) {
        NivelBot nivel = NivelBot.desde(bot.nivelBot());
        Table control = new Table();
        if (anfitrion && nivel != null) {
            NivelBot siguiente = NivelBot.values()[(nivel.ordinal() + 1) % NivelBot.values().length];
            control.add(boton(nivel.nombre(), () -> cliente.cambiarNivelBot(bot.id(), siguiente))).size(ANCHO_NIVEL_BOT, ALTO_BOTON_BOT).padRight(3);
            control.add(boton("X", () -> cliente.quitarBot(bot.id()))).size(ANCHO_QUITAR_BOT, ALTO_BOTON_BOT);
        } else {
            control.add(new Label("BOT " + (nivel != null ? nivel.nombre() : ""), skin, "etiqueta"));
        }
        return control;
    }

    /** Modo, circuito, vueltas y clasificación: el host los cambia con las flechas; los demás los ven. */
    private void armarCarrera(EstadoLobby lobby) {
        if (lobby.torneo()) {
            armarResumenCalendario(lobby);
        } else {
            armarCircuito(lobby);
        }
        panelCarrera.add(selector("VUELTAS", String.valueOf(lobby.vueltas()),
            () -> cliente.elegirVueltas(Math.max(Config.MIN_VUELTAS, lobby.vueltas() - 1)),
            () -> cliente.elegirVueltas(Math.min(Config.MAX_VUELTAS, lobby.vueltas() + 1)))).width(ANCHO_DENTRO_IZQUIERDA)
            .padBottom(4).row();
        panelCarrera.add(selector("GRILLA", lobby.modoGrilla().nombre(),
            () -> cliente.elegirGrilla(lobby.modoGrilla().anterior()), () -> cliente.elegirGrilla(lobby.modoGrilla().siguiente())))
            .width(ANCHO_DENTRO_IZQUIERDA).padBottom(4).row();
        Runnable cambiarModo = () -> cliente.elegirTorneo(!lobby.torneo());
        panelCarrera.add(selector("MODO", lobby.torneo() ? "TORNEO" : "CARRERA", cambiarModo, cambiarModo))
            .width(ANCHO_DENTRO_IZQUIERDA).row();
        if (!anfitrion) {
            panelCarrera.add(etiqueta("Lo elige el anfitrión", "apagado")).width(ANCHO_DENTRO_IZQUIERDA).left().padTop(6);
        }
    }

    /** Carrera única: el circuito elegido, con sus datos y las flechas para cambiarlo. */
    private void armarCircuito(EstadoLobby lobby) {
        InfoCircuito info = Catalogo.circuito(lobby.circuito());
        panelCarrera.add(etiqueta(info.gp() + " - " + info.ciudad(), "subtitulo")).width(ANCHO_DENTRO_IZQUIERDA).left().row();
        panelCarrera.add(etiqueta(String.valueOf(info.km()).replace('.', ',') + " km  ·  " + info.curvas() + " curvas", "suave"))
            .width(ANCHO_DENTRO_IZQUIERDA).left().padBottom(8).row();

        int totalCircuitos = Catalogo.CIRCUITOS.size();
        panelCarrera.add(selector("CIRCUITO", (lobby.circuito() + 1) + " / " + totalCircuitos,
            () -> cliente.elegirCircuito(Math.floorMod(lobby.circuito() - 1, totalCircuitos)),
            () -> cliente.elegirCircuito(Math.floorMod(lobby.circuito() + 1, totalCircuitos)))).width(ANCHO_DENTRO_IZQUIERDA)
            .padBottom(4).row();
    }

    /** Torneo: cuántas carreras son, cuáles, y el botón que muestra el calendario en la columna derecha. */
    private void armarResumenCalendario(EstadoLobby lobby) {
        int carreras = lobby.calendario().size();
        panelCarrera.add(etiqueta("Torneo de " + carreras + (carreras == 1 ? " carrera" : " carreras"), "subtitulo"))
            .width(ANCHO_DENTRO_IZQUIERDA).left().row();
        StringJoiner nombres = new StringJoiner(" · ");
        for (int circuito : lobby.calendario()) {
            nombres.add(Catalogo.circuito(circuito).gpCorto());
        }
        panelCarrera.add(etiqueta(nombres.toString(), "suave")).width(ANCHO_DENTRO_IZQUIERDA).left().padBottom(8).row();
        Table fila = new Table();
        fila.add(etiqueta("CALENDARIO", "etiqueta")).width(ANCHO_ETIQUETA_SELECTOR).left();
        String texto = verCalendario ? "ELEGIR PILOTO" : anfitrion ? "ARMAR CALENDARIO" : "VER CALENDARIO";
        fila.add(boton(texto, () -> mostrarCalendario(!verCalendario))).growX().height(ALTO_SELECTOR);
        panelCarrera.add(fila).width(ANCHO_DENTRO_IZQUIERDA).padBottom(4).row();
    }

    /** Cambia lo que hace tocar un piloto de la grilla (solo el host): sumar un bot o elegir el propio. Lo usan las capturas. */
    public void modoAgregarBots(boolean agregar) {
        agregandoBots = agregar && anfitrion;
        reconstruir();
    }

    /** Cambia la columna derecha entre los pilotos y el calendario del torneo. */
    public void mostrarCalendario(boolean mostrar) {
        verCalendario = mostrar;
        reconstruir();
    }

    /** Las carreras en orden y, para el host, todos los circuitos: tocar uno lo suma al final, tocar una carrera la saca. */
    private void armarCalendario(EstadoLobby lobby) {
        panelPilotos.add(etiqueta("CALENDARIO" + (anfitrion ? "  ·  TOCÁ UNA CARRERA PARA SACARLA" : ""), "etiqueta"))
            .colspan(COLUMNAS_CALENDARIO).left().padBottom(6).row();
        List<Integer> calendario = lobby.calendario();
        for (int i = 0; i < calendario.size(); i++) {
            int lugar = i;
            String texto = (i + 1) + ". " + Catalogo.circuito(calendario.get(i)).gpCorto();
            Actor celda;
            if (anfitrion) {
                TextButton carrera = botonChico(texto, ANCHO_CELDA_CALENDARIO, () -> cliente.quitarCircuito(lugar));
                carrera.setDisabled(calendario.size() == 1);
                celda = carrera;
            } else {
                celda = etiqueta(texto, "suave");
            }
            agregarCeldaCalendario(celda, i);
        }
        panelPilotos.row();
        if (!anfitrion) {
            return;
        }
        boolean hayLugar = calendario.size() < Config.MAX_CARRERAS_TORNEO;
        panelPilotos.add(etiqueta("CIRCUITOS  ·  TOCÁ UNO PARA SUMARLO; SE PUEDEN REPETIR", "etiqueta"))
            .colspan(COLUMNAS_CALENDARIO).left().padTop(12).padBottom(6).row();
        for (int i = 0; i < Catalogo.CIRCUITOS.size(); i++) {
            int circuito = i;
            TextButton agregar = botonChico(Catalogo.circuito(i).gpCorto(), ANCHO_CELDA_CALENDARIO, () -> cliente.agregarCircuito(circuito));
            agregar.setDisabled(!hayLugar);
            agregarCeldaCalendario(agregar, i);
        }
    }

    private void agregarCeldaCalendario(Actor celda, int indice) {
        panelPilotos.add(celda).width(ANCHO_CELDA_CALENDARIO).height(ALTO_FILA_CALENDARIO).pad(SEPARACION_CALENDARIO / 2f).left();
        if ((indice + 1) % COLUMNAS_CALENDARIO == 0) {
            panelPilotos.row();
        }
    }

    /** Una fila "ETIQUETA  <  valor  >"; solo el host tiene las flechas. */
    private Table selector(String nombre, String valor, Runnable menos, Runnable mas) {
        Table fila = new Table();
        fila.add(etiqueta(nombre, "etiqueta")).width(ANCHO_ETIQUETA_SELECTOR).left();
        if (anfitrion) {
            fila.add(boton("<", menos)).width(ANCHO_FLECHA).height(ALTO_SELECTOR);
            fila.add(new Label(valor, skin, "numeros")).expandX();
            fila.add(boton(">", mas)).width(ANCHO_FLECHA).height(ALTO_SELECTOR);
        } else {
            fila.add(new Label(valor, skin, "numeros")).left().expandX();
        }
        return fila;
    }

    /**
     * La grilla de los 22 pilotos, una fila por escudería. Cada piloto lo tiene un solo auto, pero los dos de una
     * escudería pueden correr juntos. El host tiene dos modos: elegir su propio piloto o sumar bots tocando el piloto
     * que quiere; los demás solo eligen el suyo.
     */
    private void armarPilotos(EstadoLobby lobby) {
        JugadorLobby yo = lobby.jugador(cliente.miId());
        if (anfitrion) {
            Table modos = new Table();
            modos.add(botonModo("ELEGIR MI PILOTO", !agregandoBots, () -> cambiarModoBots(false))).width(ANCHO_PESTANIA).height(ALTO_SELECTOR)
                .padRight(4);
            modos.add(botonModo("AGREGAR BOTS", agregandoBots, () -> cambiarModoBots(true))).width(ANCHO_PESTANIA).height(ALTO_SELECTOR)
                .padRight(12);
            if (agregandoBots) {
                NivelBot siguiente = NivelBot.values()[(nivelNuevoBot.ordinal() + 1) % NivelBot.values().length];
                modos.add(etiqueta("NIVEL", "etiqueta")).padRight(6);
                modos.add(boton(nivelNuevoBot.nombre(), () -> {
                    nivelNuevoBot = siguiente;
                    reconstruir();
                })).size(ANCHO_NIVEL_BOT, ALTO_SELECTOR);
            }
            panelPilotos.add(modos).colspan(4).left().padBottom(8).row();
        } else {
            panelPilotos.add(etiqueta("ELEGÍ TU PILOTO", "etiqueta")).colspan(4).left().padBottom(8).row();
        }
        if (anfitrion && agregandoBots) {
            panelPilotos.add(etiqueta(lobby.hayLugarParaBot() ? "TOCÁ UN PILOTO LIBRE PARA SUMARLO COMO BOT; TOCÁ UN BOT PARA SACARLO"
                : "LA SALA ESTÁ LLENA: SACÁ UN BOT PARA SUMAR OTRO", "apagado")).colspan(4).left().padBottom(6).row();
        }
        for (int escuderia = 0; escuderia < Catalogo.ESCUDERIAS.size(); escuderia++) {
            panelPilotos.add(new Image(recursos.color(Paleta.colorDeEscuderia(escuderia)))).size(ANCHO_FRANJA, ALTO_FILA_PILOTO - 8)
                .padRight(8);
            panelPilotos.add(etiqueta(Catalogo.ESCUDERIAS.get(escuderia).nombre(), "suave")).width(ANCHO_ESCUDERIA)
                .height(ALTO_FILA_PILOTO).left();
            for (int k = 0; k < 2; k++) {
                panelPilotos.add(botonPiloto(lobby, yo, escuderia * 2 + k)).width(ANCHO_BOTON_PILOTO + 2 * BORDE_PILOTO)
                    .height(ALTO_FILA_PILOTO);
            }
            panelPilotos.row();
        }
    }

    private TextButton botonModo(String texto, boolean activo, Runnable accion) {
        TextButton boton = activo ? botonPrimario(texto, accion) : boton(texto, accion);
        return boton;
    }

    private void cambiarModoBots(boolean agregar) {
        agregandoBots = agregar;
        reconstruir();
    }

    /**
     * Un botón con el color de la escudería y el apellido del piloto, más quién lo tiene (BOT o el nombre de la persona).
     * El propio lleva un borde. Qué hace al tocarlo depende del modo: elegirlo para uno (si lo tiene un bot, se
     * intercambian), o, en el modo de agregar bots, sumar un bot con ese piloto o sacar el que ya lo tiene.
     */
    private Table botonPiloto(EstadoLobby lobby, JugadorLobby yo, int piloto) {
        int escuderia = Catalogo.piloto(piloto).escuderia();
        JugadorLobby duenio = lobby.duenioDelPiloto(piloto);
        boolean esMio = duenio != null && yo != null && duenio.id() == yo.id();
        boolean deOtraPersona = duenio != null && !duenio.esBot() && !esMio;
        boolean modoBots = anfitrion && agregandoBots;

        Color color = Paleta.colorDeEscuderia(escuderia);
        TextButtonStyle estilo = new TextButtonStyle(recursos.color(color), recursos.color(color.cpy().mul(0.7f, 0.7f, 0.7f, 1f)),
            null, skin.getFont("chico"));
        estilo.over = recursos.color(color.cpy().lerp(Color.WHITE, 0.3f));
        estilo.disabled = recursos.color(Paleta.SUPERFICIE_ALTA);
        estilo.fontColor = Paleta.textoSobre(color);
        estilo.disabledFontColor = Paleta.TEXTO_APAGADO;

        String texto = Catalogo.piloto(piloto).apellido();
        if (duenio != null) {
            texto += "  ·  " + (duenio.esBot() ? "BOT" : esMio ? "VOS" : duenio.nombre());
        }
        TextButton boton = new TextButton(texto, estilo);
        boton.getLabel().setEllipsis(true);
        boton.getLabelCell().width(ANCHO_BOTON_PILOTO - 16);
        boolean desactivado = deOtraPersona || (modoBots && (esMio || (duenio == null && !lobby.hayLugarParaBot())));
        boton.setDisabled(desactivado);
        boton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent evento, Actor actor) {
                if (modoBots) {
                    if (duenio == null) {
                        cliente.agregarBot(nivelNuevoBot, piloto);
                    } else if (duenio.esBot()) {
                        cliente.quitarBot(duenio.id());
                    }
                } else if (!esMio) {
                    cliente.elegirPiloto(piloto);
                }
            }
        });

        Table marco = new Table();
        if (esMio) {
            marco.setBackground(recursos.color(Paleta.PRIMARIO));
        }
        marco.add(boton).width(ANCHO_BOTON_PILOTO).height(ALTO_FILA_PILOTO - 2 * BORDE_PILOTO).pad(BORDE_PILOTO);
        return marco;
    }

    private void armarBotones(EstadoLobby lobby) {
        JugadorLobby yo = lobby.jugador(cliente.miId());
        boolean estoyListo = yo != null && yo.listo();
        Runnable cambiarListo = () -> cliente.marcarListo(!estoyListo);
        TextButton botonListo = estoyListo || anfitrion
            ? boton(estoyListo ? "CANCELAR LISTO" : "ESTOY LISTO", cambiarListo)
            : botonPrimario("ESTOY LISTO", cambiarListo);
        if (!anfitrion) {
            panelBotones.add(botonListo).width(ANCHO_COLUMNA_DERECHA).height(ALTO_BOTON);
            return;
        }
        float ancho = (ANCHO_COLUMNA_DERECHA - 8f) / 2f;
        TextButton botonIniciar = botonPrimario(lobby.torneo() ? "INICIAR TORNEO" : "INICIAR CARRERA", cliente::iniciar);
        botonIniciar.setDisabled(!lobby.sePuedeIniciar());
        panelBotones.add(botonListo).width(ancho).height(ALTO_BOTON).padRight(8);
        panelBotones.add(botonIniciar).width(ancho).height(ALTO_BOTON).row();
        if (!lobby.sePuedeIniciar()) {
            panelBotones.add(etiqueta("Hacen falta " + Config.MIN_JUGADORES + " autos y todos listos", "apagado"))
                .colspan(2).width(ANCHO_COLUMNA_DERECHA).left().padTop(6);
        }
    }
}
