package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.juego.FuenteCarrera;
import ar.edu.et35.racing.juego.NivelCruce;
import ar.edu.et35.racing.juego.SimulacionLocal;
import ar.edu.et35.racing.juego.ZonaCruce;
import ar.edu.et35.racing.red.EstadoTorneo;
import ar.edu.et35.racing.red.PerdidaSimulada;
import ar.edu.et35.racing.red.Protocolo;
import ar.edu.et35.racing.red.Protocolo.Mensaje;
import ar.edu.et35.racing.red.SesionRed;
import ar.edu.et35.racing.red.cliente.CarreraEnRed;
import ar.edu.et35.racing.red.cliente.ClientePartida;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.util.Recursos;
import ar.edu.et35.racing.util.Tiempo;
import ar.edu.et35.racing.vista.CamaraSeguimiento;
import ar.edu.et35.racing.vista.MiniMapa;
import ar.edu.et35.racing.vista.TorreHud;
import ar.edu.et35.racing.vista.VistaAuto;
import ar.edu.et35.racing.vista.VistaCircuito;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pantalla de la carrera. Lee el teclado, se lo pasa a una {@link FuenteCarrera} y dibuja la foto que ella devuelve.
 * La fuente puede ser una {@link SimulacionLocal} (prueba local: la carrera se simula en esta PC) o una
 * {@link CarreraEnRed} (la carrera la simula el servidor y acá solo se dibuja). Por eso la misma pantalla sirve
 * para las dos y la prueba local sigue andando sin red.
 */
public class PantallaCarrera extends PantallaBase {
    private static final int MAX_JUGADORES_LOCALES = 2;
    private static final float ANCHO_FRANJA_COLOR = 5f;
    private static final float ANCHO_DIVISOR = 2f;
    private static final float SEGUNDOS_AVISO = 3f;
    private static final float MARGEN_HUD = 16f;
    private static final float ESCALA_CARTEL_FIN = 0.7f;
    private static final float ANCHO_PANEL = 320f;
    /** Lo que ocupan arriba el margen y el panel de los jugadores, antes de que empiece la torre de tiempos. */
    private static final float DESDE_ARRIBA_TORRE = 94f;

    /** Teclas de un esquema de control. */
    private record Controles(int acelerar, int frenar, int izquierda, int derecha) {
    }

    private static final Controles WASD = new Controles(Keys.W, Keys.S, Keys.A, Keys.D);
    private static final Controles FLECHAS = new Controles(Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT);

    /** Nombre del archivo del circuito; en la prueba local sirve para volver a la pantalla de elección con ese circuito. */
    private final String nombreCircuito;
    /** En la clasificación solo se ven los autos de esta PC: en la pista y en el HUD, cada uno corre solo. */
    private final boolean clasificacion;
    /** "CARRERA 2 DE 5" en un torneo; vacío si no es un torneo. */
    private final String textoTorneo;
    private final Circuito circuito;
    private final FuenteCarrera fuente;
    /** La misma fuente si la carrera es en red; null en la prueba local. */
    private final CarreraEnRed red;
    private final List<Integer> idsLocales;
    private final List<Controles[]> controlesLocales = new ArrayList<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();

    // Solo se usa para saber dónde queda la zona de juego (con las barras del FitViewport) dentro de la ventana.
    private final FitViewport viewportMundo = new FitViewport(Config.ANCHO_MUNDO, Config.ALTO_MUNDO);
    // Una cámara por jugador local, siempre con el mismo zoom. Con 2 jugadores la pantalla queda dividida en dos mitades.
    private final List<OrthographicCamera> camaras = new ArrayList<>();
    private final List<CamaraSeguimiento> seguimientos = new ArrayList<>();
    private final Image divisor = new Image(recursos.color(Paleta.FONDO));
    private final VistaCircuito vistaCircuito;
    private final VistaAuto vistaAuto = new VistaAuto();
    /** En qué nivel de un cruce va cada auto: el cliente lo calcula con las posiciones que recibe. */
    private final Map<Integer, NivelCruce> nivelesCruce = new HashMap<>();

    private final List<PanelHud> paneles = new ArrayList<>();
    private final TorreHud torre;
    private final MiniMapa miniMapa;
    private final Label cartel = new Label("", skin, "cartel");
    private final Label subcartel = new Label("", skin, "subtitulo");
    private final Label aviso = new Label("", skin);
    private final Label indicadorPerdida = new Label("", skin, "alerta");
    private final Label alertaRed = new Label("", skin, "alerta");

    private float tiempoTerminada;
    private float tiempoAviso;
    private boolean salio;
    private float sinEstados;
    private int estadosVistos;

    /**
     * Prueba local en el circuito indicado (nombre del archivo): 1 jugador (flechas o WASD) o 2 en el mismo teclado
     * (J1 con WASD y J2 con flechas).
     */
    public PantallaCarrera(Main juego, int jugadoresLocales, String nombreCircuito) {
        this(juego, nombreCircuito, null, jugadoresLocales, null);
    }

    /**
     * Carrera o clasificación en red, al recibir CUENTA_REGRESIVA. Usa el lobby del cliente para saber nombres y colores.
     */
    public static PantallaCarrera desde(Main juego, Protocolo.Cuenta cuenta) {
        return new PantallaCarrera(juego, cuenta.circuito(), juego.sesion(), 1, cuenta);
    }

    private PantallaCarrera(Main juego, String nombreCircuito, SesionRed sesion, int jugadoresLocales, Protocolo.Cuenta cuenta) {
        super(juego, false);
        if (jugadoresLocales < 1 || jugadoresLocales > MAX_JUGADORES_LOCALES) {
            throw new IllegalArgumentException("Jugadores locales: de 1 a " + MAX_JUGADORES_LOCALES);
        }
        this.nombreCircuito = nombreCircuito;
        this.clasificacion = cuenta != null && cuenta.clasificacion();
        EstadoTorneo torneo = sesion != null ? sesion.cliente().torneo() : null;
        this.textoTorneo = torneo != null ? "CARRERA " + torneo.carrera() + " DE " + torneo.total() : "";
        circuito = CargadorCircuito.cargar(Gdx.files.internal(Recursos.rutaCircuito(nombreCircuito)), Config.MAX_JUGADORES);
        if (sesion != null) {
            ClientePartida cliente = sesion.cliente();
            cliente.descartarEstadosPendientes();
            red = new CarreraEnRed(cliente, cliente.lobby(), cuenta.vueltas(), cuenta.grilla());
            fuente = red;
        } else {
            red = null;
            fuente = new SimulacionLocal(circuito, jugadoresLocales);
        }
        idsLocales = fuente.idsLocales();

        for (int i = 0; i < idsLocales.size(); i++) {
            int id = idsLocales.get(i);
            controlesLocales.add(idsLocales.size() == 1 ? new Controles[] {WASD, FLECHAS}
                : new Controles[] {i == 0 ? WASD : FLECHAS});
            entradas.put(id, new EntradaAuto());
            // Hasta que llegue la primera foto, la cámara mira el lugar de largada del auto.
            Vector2 largada = circuito.posicionesLargada().get(fuente.lugarDeLargada(id));
            OrthographicCamera camara = new OrthographicCamera(Config.ANCHO_MUNDO / (float) idsLocales.size(), Config.ALTO_MUNDO);
            camaras.add(camara);
            seguimientos.add(new CamaraSeguimiento(camara, circuito, largada.x, largada.y, Config.ZOOM_CAMARA));
        }
        vistaCircuito = new VistaCircuito(circuito, skin.getRegion("blanco"));
        torre = new TorreHud(recursos, fuente, clasificacion);
        miniMapa = new MiniMapa(circuito, fuente, skin.getRegion("blanco"), skin.getRegion("punto"), clasificacion);
        armarInterfaz();
    }

    private void armarInterfaz() {
        raiz.top().pad(MARGEN_HUD);
        for (int id : idsLocales) {
            PanelHud panel = new PanelHud(id);
            paneles.add(panel);
            raiz.add(panel.tabla).width(ANCHO_PANEL).left().expandX().uniformX();
        }
        raiz.row();
        raiz.add(indicadorPerdida).colspan(idsLocales.size()).left().padLeft(4).row();
        raiz.add(alertaRed).colspan(idsLocales.size()).padTop(2).row();
        // Los avisos van arriba y no en el centro, que es donde la cámara pone al auto propio.
        raiz.add(aviso).colspan(idsLocales.size()).padTop(4).row();
        Table ayuda = new Table();
        ayuda.setBackground(recursos.color(Paleta.FONDO_HUD));
        ayuda.pad(5f, 10f, 5f, 10f);
        String teclas = idsLocales.size() == 1 ? "Flechas o WASD" : "J1: WASD   J2: flechas";
        if (red != null && Config.DEBUG_RED) {
            teclas += "   F8: pérdida UDP simulada";
        }
        ayuda.add(new Label(teclas + "  ·  ESC: menú", skin, "suave"));
        raiz.add(ayuda).colspan(idsLocales.size()).expand().bottom().left();

        // Cartel central (cuenta regresiva, ¡YA!, fin). El Table no captura clics: solo lo hacen sus hijos.
        cartel.setAlignment(Align.center);
        subcartel.setAlignment(Align.center);
        Table centro = new Table();
        centro.setFillParent(true);
        centro.add(cartel).row();
        centro.add(subcartel);
        escena.addActor(centro);

        // La torre de tiempos va arriba a la izquierda, debajo del panel de cada jugador. El Table no captura clics.
        torre.setTouchable(Touchable.disabled);
        escena.addActor(torre);

        // Arriba a la derecha: no se cruza con el panel de ningún jugador, ni con la pantalla dividida.
        miniMapa.setPosition(Config.ANCHO_INTERFAZ - MARGEN_HUD - miniMapa.getWidth(),
            Config.ALTO_INTERFAZ - MARGEN_HUD - miniMapa.getHeight());
        escena.addActor(miniMapa);

        divisor.setBounds(Config.ANCHO_INTERFAZ / 2f - ANCHO_DIVISOR / 2f, 0f, ANCHO_DIVISOR, Config.ALTO_INTERFAZ);
        divisor.setTouchable(Touchable.disabled);
        divisor.setVisible(idsLocales.size() > 1);
        escena.addActor(divisor);
    }

    private Color colorDe(int id) {
        return Paleta.colorDePiloto(fuente.piloto(id));
    }

    // ------------------------------------------------------------------ red (TCP)

    /**
     * En red, procesa lo que llegó por TCP: los resultados, quién abandonó, la revancha (vuelta al lobby) y el
     * cierre de la partida. Lo que llega por UDP lo procesa la {@link CarreraEnRed} al avanzar.
     */
    @Override
    protected void actualizar(float delta) {
        if (red == null || salio) {
            return;
        }
        SesionRed sesion = juego.sesion();
        if (sesion == null) {
            irA(new MenuPrincipal(juego));
            return;
        }
        if (Config.DEBUG_RED && Gdx.input.isKeyJustPressed(Keys.F8)) {
            PerdidaSimulada.siguienteNivel();
            mostrarAviso("Pérdida UDP simulada: " + Math.round(PerdidaSimulada.fraccion() * 100) + " %");
        }
        ClientePartida cliente = sesion.cliente();
        cliente.actualizar();
        Mensaje mensaje;
        while ((mensaje = cliente.sondear()) != null) {
            switch (mensaje.nombre()) {
                case Protocolo.RESULTADOS:
                case Protocolo.GRILLA:
                    red.fijarResultados(Protocolo.leerResultados(mensaje));
                    break;
                case Protocolo.CUENTA_REGRESIVA:
                    // Solo si esta pantalla se atrasó tanto que la grilla ya pasó entera.
                    irA(desde(juego, Protocolo.leerCuenta(mensaje)));
                    return;
                case Protocolo.SALIO:
                    mostrarAviso(red.nombre(mensaje.entero(0, -1)) + " abandonó la carrera");
                    break;
                case Protocolo.LOBBY:
                    // El host pidió revancha antes de que esta pantalla pasara a los resultados.
                    irA(new Lobby(juego));
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

    /**
     * En red, si dejan de llegar estados UDP: a AVISO_RED_INESTABLE segundos se avisa, y a TIMEOUT_RED se vuelve al
     * menú. Si nunca llegó ninguno, lo más probable es que un firewall bloquee el UDP y el mensaje lo dice. No se
     * controla una vez terminada la carrera: el servidor deja de mandar estados.
     */
    private void controlarDatosDeRed(FotoCarrera foto, float delta) {
        if (red == null || salio) {
            return;
        }
        boolean terminada = fuente.resultados() != null || (foto != null && foto.estado() == EstadoCarrera.TERMINADA);
        if (red.estadosRecibidos() != estadosVistos || terminada) {
            estadosVistos = red.estadosRecibidos();
            sinEstados = 0f;
            alertaRed.setText("");
            return;
        }
        // Se limita el delta: si la ventana se congela un rato, los estados atrasados llegan enseguida y no es una falla.
        sinEstados += Math.min(delta, 0.25f);
        if (sinEstados >= Config.TIMEOUT_RED) {
            String motivo = estadosVistos == 0
                ? "No llegan datos por UDP: revisá el firewall de la PC del host (puerto " + Config.PUERTO_UDP + ")"
                : "Se cortó la conexión con la carrera: no llegan datos hace " + Math.round(Config.TIMEOUT_RED) + " s";
            irA(new MenuPrincipal(juego, motivo));
        } else if (sinEstados >= Config.AVISO_RED_INESTABLE) {
            alertaRed.setText("Conexión inestable...");
        }
    }

    private void mostrarAviso(String texto) {
        aviso.setText(texto);
        tiempoAviso = SEGUNDOS_AVISO;
    }

    private void irA(PantallaBase siguiente) {
        salio = true;
        juego.irA(siguiente);
    }

    // ------------------------------------------------------------------ cuadro

    @Override
    protected void dibujarFondo(float delta) {
        leerEntradas();
        fuente.avanzar(delta, entradas);
        FotoCarrera foto = fuente.foto();
        controlarDatosDeRed(foto, delta);

        for (int i = 0; i < camaras.size(); i++) {
            FotoAuto propio = foto != null ? foto.auto(idsLocales.get(i)) : null;
            if (propio != null) {
                seguimientos.get(i).actualizar(propio.x(), propio.y(), propio.vx(), propio.vy(), delta);
            }
        }

        // La zona de juego se reparte en tantas franjas verticales como jugadores locales.
        viewportMundo.apply();
        int x = viewportMundo.getScreenX();
        int y = viewportMundo.getScreenY();
        int ancho = viewportMundo.getScreenWidth();
        int alto = viewportMundo.getScreenHeight();
        int n = camaras.size();
        for (int i = 0; i < n; i++) {
            int desde = ancho * i / n;
            int hasta = ancho * (i + 1) / n;
            HdpiUtils.glViewport(x + desde, y, hasta - desde, alto);
            dibujarMundo(camaras.get(i), foto);
        }

        actualizarInterfaz(foto, delta);
    }

    private void dibujarMundo(OrthographicCamera camara, FotoCarrera foto) {
        vistaCircuito.dibujar(camara);
        if (foto != null) {
            dibujarAutos(camara, foto, true);
        }
        vistaCircuito.dibujarPuentes(camara);
        if (foto != null) {
            dibujarAutos(camara, foto, false);
        }
    }

    /** Dibuja los autos que van por debajo de un puente, o todos los demás. */
    private void dibujarAutos(OrthographicCamera camara, FotoCarrera foto, boolean losDeAbajo) {
        // Los autos vienen ordenados por posición: se dibujan del último al primero para que el puntero quede arriba.
        List<FotoAuto> autos = foto.autos();
        for (int i = autos.size() - 1; i >= 0; i--) {
            FotoAuto a = autos.get(i);
            NivelCruce nivel = ZonaCruce.nivel(circuito.zonasCruce(),
                nivelesCruce.getOrDefault(a.id(), NivelCruce.FUERA), a.x(), a.y());
            nivelesCruce.put(a.id(), nivel);
            if ((nivel == NivelCruce.ABAJO) == losDeAbajo && !a.desconectado()
                && (!clasificacion || idsLocales.contains(a.id()))) {
                vistaAuto.dibujar(a.x(), a.y(), a.angulo(), Paleta.autoDePiloto(fuente.piloto(a.id())),
                    Paleta.detalleDePiloto(fuente.piloto(a.id())), camara);
            }
        }
    }

    private void leerEntradas() {
        for (int i = 0; i < idsLocales.size(); i++) {
            EntradaAuto entrada = entradas.get(idsLocales.get(i));
            entrada.acelerar = false;
            entrada.frenar = false;
            int izquierda = 0;
            int derecha = 0;
            for (Controles teclas : controlesLocales.get(i)) {
                entrada.acelerar |= Gdx.input.isKeyPressed(teclas.acelerar());
                entrada.frenar |= Gdx.input.isKeyPressed(teclas.frenar());
                izquierda |= Gdx.input.isKeyPressed(teclas.izquierda()) ? 1 : 0;
                derecha |= Gdx.input.isKeyPressed(teclas.derecha()) ? 1 : 0;
            }
            entrada.giro = izquierda - derecha;
        }
    }

    private void actualizarInterfaz(FotoCarrera foto, float delta) {
        for (PanelHud panel : paneles) {
            panel.actualizar(foto);
        }
        torre.actualizar(foto);
        torre.setPosition(MARGEN_HUD, Config.ALTO_INTERFAZ - DESDE_ARRIBA_TORRE - torre.getHeight());
        miniMapa.actualizar(foto);
        tiempoAviso -= delta;
        if (tiempoAviso <= 0f) {
            aviso.setText("");
        }
        float perdida = PerdidaSimulada.fraccion();
        indicadorPerdida.setText(red != null && perdida > 0f ? "PÉRDIDA UDP SIMULADA " + Math.round(perdida * 100) + " %" : "");

        subcartel.setText("");
        boolean terminada = fuente.resultados() != null || (foto != null && foto.estado() == EstadoCarrera.TERMINADA);
        if (terminada) {
            // Más chico que la cuenta regresiva: si no, este texto largo se pisa con la torre de tiempos.
            cartel.setFontScale(ESCALA_CARTEL_FIN);
            cartel.setText(clasificacion ? "FIN DE LA CLASIFICACIÓN" : "CARRERA FINALIZADA");
            tiempoTerminada += delta;
            // En red, los resultados llegan por TCP: se espera a tenerlos antes de cambiar de pantalla.
            if (tiempoTerminada >= Config.SEGUNDOS_FIN_A_RESULTADOS && fuente.resultados() != null && !salio) {
                irA(clasificacion ? new PantallaGrilla(juego, fuente.resultados()) : new Resultados(juego, fuente.resultados()));
            }
        } else if (foto == null) {
            cartel.setText("");
            subcartel.setText("Esperando al servidor...");
        } else if (foto.estado() == EstadoCarrera.CUENTA_REGRESIVA) {
            cartel.setText(String.valueOf(Math.max(1, foto.segundosCuentaRegresiva())));
            String detalle = clasificacion ? "CLASIFICACIÓN: UNA VUELTA" : "";
            subcartel.setText(textoTorneo.isEmpty() || detalle.isEmpty() ? textoTorneo + detalle : textoTorneo + "  ·  " + detalle);
        } else {
            cartel.setText(foto.mostrarYa() ? "¡YA!" : "");
            FotoAuto propio = foto.auto(idsLocales.get(0));
            if (clasificacion && propio != null && propio.terminado()) {
                subcartel.setText("TU VUELTA: " + Tiempo.formato(propio.tiempo()) + "   ESPERANDO A LOS DEMÁS");
            } else if (foto.cierreActivo()) {
                subcartel.setText("CIERRE EN " + (int) Math.ceil(foto.cierreRestante()) + " s");
            }
        }
    }

    @Override
    public void resize(int ancho, int alto) {
        viewportMundo.update(ancho, alto, false);
        super.resize(ancho, alto);
    }

    @Override
    protected void volver() {
        // En la prueba local se vuelve a elegir circuito; en red, al menú (que además cierra la sesión).
        irA(red == null ? new PruebaLocal(juego, nombreCircuito) : new MenuPrincipal(juego));
    }

    @Override
    public void dispose() {
        vistaCircuito.dispose();
        vistaAuto.dispose();
        super.dispose();
    }

    /** Panel del HUD de un jugador: franja con el color de su auto, vuelta, posición, tiempo actual y mejor vuelta. */
    private class PanelHud {
        private final int id;
        private final String codigo;
        private final Table tabla = new Table();
        private final Label cabecera = new Label("", skin, "subtitulo");
        private final Label tiempos = new Label("", skin, "numeros");

        PanelHud(int id) {
            this.id = id;
            tabla.setBackground(recursos.color(Paleta.FONDO_HUD));
            tabla.pad(8f, 10f, 8f, 10f);
            tiempos.setColor(Paleta.TEXTO_SUAVE);
            tabla.add(new Image(recursos.color(colorDe(id)))).width(ANCHO_FRANJA_COLOR).growY().padRight(10);
            codigo = Catalogo.piloto(fuente.piloto(id)).codigo();
            Table textos = new Table();
            textos.add(cabecera).left().row();
            textos.add(tiempos).left();
            tabla.add(textos).left().expandX();
        }

        void actualizar(FotoCarrera foto) {
            FotoAuto yo = foto != null ? foto.auto(id) : null;
            int vueltas = fuente.vueltasTotales();
            // La posición y la mejor vuelta de todos están en la torre de tiempos: acá solo lo propio de este jugador.
            if (yo == null) {
                cabecera.setText(fuente.nombre(id) + " [" + codigo + "]");
                tiempos.setText("VUELTA 1/" + vueltas + "   TIEMPO " + Tiempo.formato(0f));
                return;
            }
            int vuelta = Math.min(yo.vueltas() + 1, vueltas);
            cabecera.setText(fuente.nombre(id) + " [" + codigo + "]");
            tiempos.setText((yo.terminado() ? "FIN   TOTAL " : "VUELTA " + vuelta + "/" + vueltas + "   TIEMPO ")
                + Tiempo.formato(yo.tiempo()));
        }
    }
}
