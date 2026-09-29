package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.juego.FotoAuto;
import ar.edu.et35.racing.juego.FotoCarrera;
import ar.edu.et35.racing.juego.FuenteCarrera;
import ar.edu.et35.racing.juego.SimulacionLocal;
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
import ar.edu.et35.racing.vista.VistaAuto;
import ar.edu.et35.racing.vista.VistaCircuito;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.maps.tiled.TiledMap;
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
    private static final Color FONDO_HUD = new Color(0f, 0f, 0f, 0.55f);
    private static final float ANCHO_FRANJA_COLOR = 4f;
    private static final float ANCHO_DIVISOR = 2f;
    private static final float SEGUNDOS_AVISO = 3f;

    /** Teclas de un esquema de control. */
    private record Controles(int acelerar, int frenar, int izquierda, int derecha) {
    }

    private static final Controles WASD = new Controles(Keys.W, Keys.S, Keys.A, Keys.D);
    private static final Controles FLECHAS = new Controles(Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT);

    private final String rutaMapa;
    private final TiledMap mapa;
    private final Circuito circuito;
    private final FuenteCarrera fuente;
    /** La misma fuente si la carrera es en red; null en la prueba local. */
    private final CarreraEnRed red;
    private final List<Integer> idsLocales;
    private final List<Controles[]> controlesLocales = new ArrayList<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();

    // Solo se usa para saber dónde queda la zona de juego (con las barras del FitViewport) dentro de la ventana.
    private final FitViewport viewportMundo = new FitViewport(Config.ANCHO_VIRTUAL, Config.ALTO_VIRTUAL);
    // Una cámara por jugador local, siempre con el mismo zoom. Con 2 jugadores la pantalla queda dividida en dos mitades.
    private final List<OrthographicCamera> camaras = new ArrayList<>();
    private final List<CamaraSeguimiento> seguimientos = new ArrayList<>();
    private final Image divisor = new Image(recursos.color(Paleta.BLANCO));
    private final VistaCircuito vistaCircuito;
    private final VistaAuto vistaAuto = new VistaAuto();

    private final List<PanelHud> paneles = new ArrayList<>();
    private final Label cartel = new Label("", skin, "titulo");
    private final Label subcartel = new Label("", skin);
    private final Label aviso = new Label("", skin);
    private final Label indicadorPerdida = new Label("", skin, "rojo");

    private float tiempoTerminada;
    private float tiempoAviso;
    private boolean salio;

    /** Prueba local: 1 jugador (flechas o WASD) o 2 en el mismo teclado (J1 con WASD y J2 con flechas). */
    public PantallaCarrera(Main juego, int jugadoresLocales) {
        this(juego, Recursos.rutaCircuito(Config.CIRCUITO), null, jugadoresLocales, 0);
    }

    /**
     * Carrera en red, al recibir CUENTA_REGRESIVA en el lobby. Usa el lobby del cliente para saber nombres, colores y
     * el orden de la grilla.
     */
    public PantallaCarrera(Main juego, String nombreCircuito, int vueltas) {
        this(juego, Recursos.rutaCircuito(nombreCircuito), juego.sesion(), 1, vueltas);
    }

    private PantallaCarrera(Main juego, String rutaMapa, SesionRed sesion, int jugadoresLocales, int vueltasRed) {
        super(juego, false);
        if (jugadoresLocales < 1 || jugadoresLocales > MAX_JUGADORES_LOCALES) {
            throw new IllegalArgumentException("Jugadores locales: de 1 a " + MAX_JUGADORES_LOCALES);
        }
        this.rutaMapa = rutaMapa;
        mapa = recursos.cargarMapa(rutaMapa);
        circuito = CargadorCircuito.cargar(mapa, Config.MAX_JUGADORES);
        if (sesion != null) {
            ClientePartida cliente = sesion.cliente();
            cliente.descartarEstadosPendientes();
            red = new CarreraEnRed(cliente, cliente.lobby(), vueltasRed);
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
            OrthographicCamera camara = new OrthographicCamera(Config.ANCHO_VIRTUAL / (float) idsLocales.size(), Config.ALTO_VIRTUAL);
            camaras.add(camara);
            seguimientos.add(new CamaraSeguimiento(camara, circuito, largada.x, largada.y, Config.ZOOM_CAMARA));
        }
        vistaCircuito = new VistaCircuito(mapa, recursos.batch);
        armarInterfaz();
    }

    private void armarInterfaz() {
        raiz.top().pad(6);
        for (int id : idsLocales) {
            PanelHud panel = new PanelHud(id);
            paneles.add(panel);
            raiz.add(panel.tabla).growX().pad(2);
        }
        raiz.row();
        raiz.add(indicadorPerdida).colspan(idsLocales.size()).left().padLeft(4).row();
        // Los avisos van arriba y no en el centro, que es donde la cámara pone al auto propio.
        raiz.add(aviso).colspan(idsLocales.size()).padTop(4).row();
        Table ayuda = new Table();
        ayuda.setBackground(recursos.color(FONDO_HUD));
        ayuda.pad(3);
        String teclas = idsLocales.size() == 1 ? "Flechas o WASD" : "J1: WASD   J2: flechas";
        if (red != null && Config.DEBUG_RED) {
            teclas += "   F8: pérdida UDP simulada";
        }
        ayuda.add(new Label(teclas + "  -  ESC: menú", skin, "gris"));
        raiz.add(ayuda).colspan(idsLocales.size()).expand().bottom().left().padBottom(2);

        // Cartel central (cuenta regresiva, ¡YA!, fin). El Table no captura clics: solo lo hacen sus hijos.
        cartel.setAlignment(Align.center);
        subcartel.setAlignment(Align.center);
        Table centro = new Table();
        centro.setFillParent(true);
        centro.add(cartel).row();
        centro.add(subcartel);
        escena.addActor(centro);

        divisor.setBounds(Config.ANCHO_VIRTUAL / 2f - ANCHO_DIVISOR / 2f, 0f, ANCHO_DIVISOR, Config.ALTO_VIRTUAL);
        divisor.setTouchable(Touchable.disabled);
        divisor.setVisible(idsLocales.size() > 1);
        escena.addActor(divisor);
    }

    private Color colorDe(int id) {
        return Paleta.COLORES_AUTOS[Math.floorMod(fuente.color(id), Paleta.COLORES_AUTOS.length)];
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
                    red.fijarResultados(Protocolo.leerResultados(mensaje));
                    break;
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
        if (foto == null) {
            return;
        }
        // Los autos vienen ordenados por posición: se dibujan del último al primero para que el puntero quede arriba.
        List<FotoAuto> autos = foto.autos();
        for (int i = autos.size() - 1; i >= 0; i--) {
            FotoAuto a = autos.get(i);
            if (!a.desconectado()) {
                vistaAuto.dibujar(a.x(), a.y(), a.angulo(), colorDe(a.id()), camara);
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
        tiempoAviso -= delta;
        if (tiempoAviso <= 0f) {
            aviso.setText("");
        }
        float perdida = PerdidaSimulada.fraccion();
        indicadorPerdida.setText(red != null && perdida > 0f ? "PÉRDIDA UDP SIMULADA " + Math.round(perdida * 100) + " %" : "");

        subcartel.setText("");
        boolean terminada = fuente.resultados() != null || (foto != null && foto.estado() == EstadoCarrera.TERMINADA);
        if (terminada) {
            cartel.setText("CARRERA FINALIZADA");
            tiempoTerminada += delta;
            // En red, los resultados llegan por TCP: se espera a tenerlos antes de cambiar de pantalla.
            if (tiempoTerminada >= Config.SEGUNDOS_FIN_A_RESULTADOS && fuente.resultados() != null && !salio) {
                irA(new Resultados(juego, fuente.resultados()));
            }
        } else if (foto == null) {
            cartel.setText("");
            subcartel.setText("Esperando al servidor...");
        } else if (foto.estado() == EstadoCarrera.CUENTA_REGRESIVA) {
            cartel.setText(String.valueOf(Math.max(1, foto.segundosCuentaRegresiva())));
        } else {
            cartel.setText(foto.mostrarYa() ? "¡YA!" : "");
            if (foto.cierreActivo()) {
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
        irA(new MenuPrincipal(juego));
    }

    @Override
    public void dispose() {
        vistaCircuito.dispose();
        vistaAuto.dispose();
        recursos.liberar(rutaMapa);
        super.dispose();
    }

    /** Panel del HUD de un jugador: franja con el color de su auto, vuelta, posición, tiempo actual y mejor vuelta. */
    private class PanelHud {
        private final int id;
        private final Table tabla = new Table();
        private final Label cabecera = new Label("", skin);
        private final Label tiempos = new Label("", skin, "gris");

        PanelHud(int id) {
            this.id = id;
            tabla.setBackground(recursos.color(FONDO_HUD));
            tabla.pad(4);
            tabla.add(new Image(recursos.color(colorDe(id)))).width(ANCHO_FRANJA_COLOR).growY().padRight(6);
            Table textos = new Table();
            textos.add(cabecera).left().row();
            textos.add(tiempos).left();
            tabla.add(textos).left().expandX();
        }

        void actualizar(FotoCarrera foto) {
            FotoAuto yo = foto != null ? foto.auto(id) : null;
            int vueltas = fuente.vueltasTotales();
            if (yo == null) {
                cabecera.setText(fuente.nombre(id) + "   VUELTA 1/" + vueltas);
                tiempos.setText("TIEMPO " + Tiempo.formato(0f) + "   MEJOR " + Tiempo.formato(Float.NaN));
                return;
            }
            int vuelta = Math.min(yo.vueltas() + 1, vueltas);
            String vueltaTexto = yo.terminado() ? "FIN" : "VUELTA " + vuelta + "/" + vueltas;
            cabecera.setText(fuente.nombre(id) + "   " + vueltaTexto + "   POS " + yo.posicion() + "/" + foto.autosEnCarrera());
            tiempos.setText((yo.terminado() ? "TOTAL " : "TIEMPO ") + Tiempo.formato(yo.tiempo())
                + "   MEJOR " + Tiempo.formato(yo.mejorVuelta()));
        }
    }
}
