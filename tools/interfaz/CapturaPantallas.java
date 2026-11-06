import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.juego.ResultadoJugador;
import ar.edu.et35.racing.pantallas.Lobby;
import ar.edu.et35.racing.pantallas.MenuPrincipal;
import ar.edu.et35.racing.pantallas.PantallaCarrera;
import ar.edu.et35.racing.pantallas.PruebaLocal;
import ar.edu.et35.racing.pantallas.Resultados;
import ar.edu.et35.racing.pantallas.UnirsePartida;
import ar.edu.et35.racing.red.PaquetesUdp;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.red.servidor.ServidorPartida;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Recursos;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Programa de verificación (fuera del juego): abre el juego real en una ventana oculta, recorre las pantallas y guarda
 * una captura de cada una. La sala y la carrera en red usan el servidor y una partida llena de clientes de verdad, en los puertos del
 * juego, así que no tiene que haber otra partida abierta en la PC. Ver tools/interfaz/LEEME.md.
 */
public class CapturaPantallas extends Main {
    private record Paso(Runnable accion, int cuadrosDeEspera, String captura) {
    }

    private static final String[] NOMBRES = {"Ana", "Beto", "Caro", "Dani", "Eli", "Fede", "Gala"};
    /** La carrera de prueba corre en el circuito más angosto, que es donde menos lugar hay para la grilla. */
    private static final String CIRCUITO_ANGOSTO = "monaco";

    private final String salida;
    private final Deque<Paso> pasos = new ArrayDeque<>();
    private Paso actual;
    private int cuadros;
    private final List<ClientePartida> invitados = new ArrayList<>();
    private final Map<ClientePartida, Integer> estadosRecibidos = new HashMap<>();
    private final Map<ClientePartida, Integer> autosRecibidos = new HashMap<>();
    private final EntradaAuto aFondo = new EntradaAuto();
    private ClientePartida sobrante;
    private String respuestaAlSobrante = "(sin respuesta)";
    private boolean invitadosManejando;
    private boolean listo;

    CapturaPantallas(String salida) {
        this.salida = salida;
    }

    /** Con un segundo argumento "clasificacion" o "torneo", solo corre esa partida. */
    private static boolean soloClasificacion;
    private static boolean soloTorneo;

    public static void main(String[] args) {
        soloClasificacion = args.length > 1 && args[1].equals("clasificacion");
        soloTorneo = args.length > 1 && args[1].equals("torneo");
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("captura");
        c.setInitialVisible(false);
        // A 60 cuadros por segundo, para que las esperas de abajo sean tiempo real (60 cuadros = 1 s) también con la ventana oculta.
        c.useVsync(false);
        c.setForegroundFPS(60);
        c.setIdleFPS(60);
        c.setBackBufferConfig(8, 8, 8, 8, 16, 0, Config.MUESTRAS_ANTIALIASING);
        c.setWindowedMode(Config.VENTANA_ANCHO, Config.VENTANA_ALTO);
        new Lwjgl3Application(new CapturaPantallas(args[0]), c);
    }

    @Override
    public void create() {
        super.create();
        if (soloClasificacion) {
            pasosDeClasificacion();
            listo = true;
            return;
        }
        if (soloTorneo) {
            pasosDeTorneo();
            listo = true;
            return;
        }
        paso(() -> { }, "menu");
        paso(() -> irA(new MenuPrincipal(this, "La partida se cerró: el anfitrión salió de la partida")), "menu_aviso");
        paso(() -> irA(new UnirsePartida(this, true)), "crear");
        paso(() -> irA(new UnirsePartida(this, false)), "unirse");
        paso(() -> irA(new PruebaLocal(this, "interlagos")), "prueba_local");
        pasos.add(new Paso(this::crearSala, 40, null));
        pasos.add(new Paso(() -> sesion().cliente().marcarListo(true), 40, "lobby_host"));
        pasos.add(new Paso(this::probarSobrante, 40, null));
        pasos.add(new Paso(() -> invitados.forEach(i -> i.marcarListo(true)), 40, "lobby_todos_listos"));
        pasos.add(new Paso(() -> {
            sesion().cliente().elegirCircuito(Catalogo.indiceDeCircuito(CIRCUITO_ANGOSTO));
            aFondo.acelerar = true;
            invitadosManejando = true;
        }, 40, null));
        pasos.add(new Paso(() -> sesion().cliente().iniciar(), 60, "carrera_cuenta"));
        pasos.add(new Paso(this::informarRed, 300, "carrera_red"));
        pasos.add(new Paso(this::mostrarResultados, 30, "resultados"));
        paso(() -> {
            cerrarSesion();
            irA(new Resultados(this, resultadosDePrueba()));
        }, "resultados_local");
        pasos.add(new Paso(() -> irA(new PantallaCarrera(this, 2, "monza")), 330, "carrera_dividida"));
        for (String circuito : new String[] {"spa_francorchamps", "suzuka", "las_vegas", "hungaroring"}) {
            pasos.add(new Paso(() -> irA(new PantallaCarrera(this, 1, circuito)), 30, "mapa_" + circuito));
        }
        pasos.add(new Paso(this::crearSalaConBots, 40, null));
        pasos.add(new Paso(() -> {
            sesion().cliente().agregarBot(NivelBot.FACIL);
            sesion().cliente().agregarBot(NivelBot.FACIL);
            sesion().cliente().agregarBot(NivelBot.MEDIO);
            sesion().cliente().agregarBot(NivelBot.MEDIO);
            sesion().cliente().agregarBot(NivelBot.MEDIO);
            sesion().cliente().agregarBot(NivelBot.DIFICIL);
            sesion().cliente().agregarBot(NivelBot.DIFICIL);
            sesion().cliente().marcarListo(true);
            sesion().cliente().elegirVueltas(Config.MIN_VUELTAS);
            sesion().cliente().elegirCircuito(Catalogo.indiceDeCircuito("spielberg"));
        }, 40, "bots_sala"));
        pasos.add(new Paso(() -> sesion().cliente().iniciar(), 60 * 20, "bots_carrera"));
        pasos.add(new Paso(() -> { }, 60 * 12, "bots_carrera2"));
        pasos.add(new Paso(() -> { }, 60 * 55, "bots_resultados"));
        listo = true;
    }

    /** Una persona contra 7 bots: sala, vuelta de clasificación, grilla y largada de la carrera. */
    private void pasosDeClasificacion() {
        pasos.add(new Paso(this::crearSalaConBots, 40, null));
        pasos.add(new Paso(() -> {
            for (NivelBot nivel : new NivelBot[] {NivelBot.DIFICIL, NivelBot.MEDIO, NivelBot.FACIL, NivelBot.DIFICIL, NivelBot.MEDIO, NivelBot.FACIL, NivelBot.MEDIO}) {
                sesion().cliente().agregarBot(nivel);
            }
            sesion().cliente().marcarListo(true);
            sesion().cliente().elegirVueltas(Config.MIN_VUELTAS);
            sesion().cliente().elegirClasificacion(true);
            sesion().cliente().elegirCircuito(Catalogo.indiceDeCircuito("spielberg"));
        }, 40, "clasif_sala"));
        pasos.add(new Paso(() -> sesion().cliente().iniciar(), 60, "clasif_cuenta"));
        pasos.add(new Paso(() -> { }, 60 * 15, "clasif_vuelta"));
        pasos.add(new Paso(() -> { }, 60 * 99, "clasif_grilla"));
        pasos.add(new Paso(() -> { }, 60 * 12, "clasif_largada"));
    }

    /** Una persona contra 7 bots, un torneo de 2 carreras: la sala, el calendario y los resultados de cada carrera. */
    private void pasosDeTorneo() {
        pasos.add(new Paso(this::crearSalaConBots, 40, null));
        pasos.add(new Paso(() -> {
            for (NivelBot nivel : new NivelBot[] {NivelBot.DIFICIL, NivelBot.MEDIO, NivelBot.FACIL, NivelBot.DIFICIL, NivelBot.MEDIO, NivelBot.FACIL, NivelBot.MEDIO}) {
                sesion().cliente().agregarBot(nivel);
            }
            sesion().cliente().marcarListo(true);
            sesion().cliente().elegirVueltas(Config.MIN_VUELTAS);
            sesion().cliente().elegirCircuito(Catalogo.indiceDeCircuito("spielberg"));
            sesion().cliente().elegirTorneo(true);
            sesion().cliente().agregarCircuito(Catalogo.indiceDeCircuito("monaco"));
        }, 40, "torneo_sala"));
        pasos.add(new Paso(() -> {
            for (String circuito : new String[] {"monza", "spa_francorchamps", "mexico_city", "zandvoort"}) {
                sesion().cliente().agregarCircuito(Catalogo.indiceDeCircuito(circuito));
            }
            ((Lobby) getScreen()).mostrarCalendario(true);
        }, 40, "torneo_calendario"));
        pasos.add(new Paso(() -> {
            for (int lugar = 5; lugar >= 2; lugar--) {
                sesion().cliente().quitarCircuito(lugar);
            }
        }, 40, null));
        pasos.add(new Paso(() -> sesion().cliente().iniciar(), 60, "torneo_cuenta"));
        pasos.add(new Paso(() -> { }, 60 * 100, "torneo_resultados1"));
        pasos.add(new Paso(() -> sesion().cliente().siguienteCarrera(), 60, "torneo_cuenta2"));
        pasos.add(new Paso(() -> { }, 60 * 110, "torneo_final"));
    }

    private void paso(Runnable accion, String captura) {
        pasos.add(new Paso(accion, 10, captura));
    }

    private void crearSala() {
        try {
            ServidorPartida servidor = ServidorPartida.crear(Config.PUERTO_TCP, Config.PUERTO_UDP, this::cargarCircuito);
            ClientePartida host = new ClientePartida("Joaquín");
            host.conectar("127.0.0.1", servidor.puerto());
            setSesion(new SesionRed(servidor, host));
            irA(new Lobby(this));
            for (int i = 0; i < Config.MAX_JUGADORES - 1; i++) {
                ClientePartida invitado = new ClientePartida(NOMBRES[i]);
                invitado.conectar("127.0.0.1", servidor.puerto());
                invitados.add(invitado);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Una sala nueva, sin clientes de prueba: el host y lo que agregue después. */
    private void crearSalaConBots() {
        try {
            for (ClientePartida invitado : invitados) {
                invitado.cerrar();
            }
            invitados.clear();
            invitadosManejando = false;
            ServidorPartida servidor = ServidorPartida.crear(Config.PUERTO_TCP, Config.PUERTO_UDP, this::cargarCircuito);
            ClientePartida host = new ClientePartida("Joaquín");
            host.conectar("127.0.0.1", servidor.puerto());
            setSesion(new SesionRed(servidor, host));
            irA(new Lobby(this));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un jugador de más: el servidor tiene que rechazarlo con PARTIDA_LLENA. */
    private void probarSobrante() {
        sobrante = new ClientePartida("Sobrante");
        sobrante.conectar("127.0.0.1", Config.PUERTO_TCP);
    }

    /** Se llama cuando faltan 5 s para la captura de la carrera; lo que imprime sale al terminar ese paso. */
    private void informarRed() {
        Gdx.app.postRunnable(() -> System.out.println("VERIFICACION sobrante: " + respuestaAlSobrante));
    }

    private Circuito cargarCircuito(String archivo) throws Exception {
        CompletableFuture<Circuito> futuro = new CompletableFuture<>();
        Gdx.app.postRunnable(() -> futuro.complete(
            CargadorCircuito.cargar(Gdx.files.internal(Recursos.rutaCircuito(archivo)), Config.MAX_JUGADORES)));
        return futuro.get(Config.TIMEOUT_CARGA_CIRCUITO_S, TimeUnit.SECONDS);
    }

    private void mostrarResultados() {
        irA(new Resultados(this, resultadosDePrueba()));
    }

    private List<ResultadoJugador> resultadosDePrueba() {
        int host = sesion() != null ? sesion().cliente().miId() : 0;
        return List.of(
            new ResultadoJugador(1, host, "Joaquín", 186.42f, 60.91f),
            new ResultadoJugador(2, host + 1, "Ana", 188.07f, 61.35f),
            new ResultadoJugador(3, host + 2, "Beto", 190.21f, 61.88f),
            new ResultadoJugador(4, host + 3, "Caro", 191.76f, 62.10f),
            new ResultadoJugador(5, host + 4, "Dani", 193.48f, 62.74f),
            new ResultadoJugador(6, host + 5, "Eli", 195.02f, 63.05f),
            new ResultadoJugador(7, host + 6, "Fede", 197.33f, 63.61f),
            new ResultadoJugador(8, host + 7, "Gala", 199.90f, 64.27f));
    }

    @Override
    public void render() {
        super.render();
        if (!listo) {
            return;
        }
        atenderInvitados();
        if (actual == null) {
            actual = pasos.poll();
            if (actual == null) {
                Gdx.app.exit();
                return;
            }
            cuadros = 0;
            actual.accion().run();
            return;
        }
        if (++cuadros >= actual.cuadrosDeEspera()) {
            if (actual.captura() != null) {
                capturar(actual.captura());
            }
            actual = null;
        }
    }

    /** Los invitados hacen lo mínimo de un cliente de verdad: mantener viva la conexión y, en carrera, mandar sus teclas. */
    private void atenderInvitados() {
        for (ClientePartida invitado : invitados) {
            invitado.actualizar();
            while (invitado.sondear() != null) {
                // lo que llega por TCP no le importa a este programa
            }
            if (invitadosManejando) {
                invitado.enviarEntrada(aFondo);
            }
            PaquetesUdp.Estado estado;
            while ((estado = invitado.sondearEstado()) != null) {
                estadosRecibidos.merge(invitado, 1, Integer::sum);
                autosRecibidos.put(invitado, estado.foto().autos().size());
            }
        }
        if (sobrante != null) {
            sobrante.actualizar();
            Protocolo.Mensaje mensaje;
            while ((mensaje = sobrante.sondear()) != null) {
                if (mensaje.es(Protocolo.ERROR)) {
                    respuestaAlSobrante = mensaje.campo(0);
                }
            }
        }
    }

    private void capturar(String nombre) {
        int ancho = Gdx.graphics.getBackBufferWidth();
        int alto = Gdx.graphics.getBackBufferHeight();
        Gdx.gl.glFinish();
        Pixmap leido = Pixmap.createFromFrameBuffer(0, 0, ancho, alto);
        // El framebuffer viene con el origen abajo: se da vuelta para que la imagen salga derecha.
        // El canal alfa de la ventana no se ve en pantalla, pero en un PNG aclararía los paneles semitransparentes.
        java.nio.ByteBuffer pixeles = leido.getPixels();
        for (int i = 3; i < pixeles.limit(); i += 4) {
            pixeles.put(i, (byte) 0xFF);
        }
        Pixmap derecha = new Pixmap(ancho, alto, leido.getFormat());
        for (int y = 0; y < alto; y++) {
            derecha.drawPixmap(leido, 0, y, 0, alto - 1 - y, ancho, 1);
        }
        PixmapIO.writePNG(Gdx.files.absolute(salida + "/" + nombre + ".png"), derecha);
        leido.dispose();
        derecha.dispose();
    }

    @Override
    public void dispose() {
        for (ClientePartida invitado : invitados) {
            System.out.println("VERIFICACION " + invitado.nombre() + ": " + estadosRecibidos.getOrDefault(invitado, 0)
                + " estados, último con " + autosRecibidos.getOrDefault(invitado, 0) + " autos");
            invitado.cerrar();
        }
        if (sobrante != null) {
            sobrante.cerrar();
        }
        super.dispose();
    }
}
