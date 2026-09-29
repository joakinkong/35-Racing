package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Auto;
import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Carrera;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.juego.EstadoCarrera;
import ar.edu.et35.racing.juego.Participante;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
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
 * Pantalla de la carrera con 1 o 2 jugadores en el mismo teclado. Lee los controles, avanza la {@link Carrera}
 * a paso fijo (1 / TICKS_POR_SEGUNDO, con acumulador) y dibuja interpolando entre ticks.
 */
public class PantallaCarrera extends PantallaBase {
    private static final String RUTA_CIRCUITO = "circuitos/circuito1.tmx";
    private static final float PASO = 1f / Config.TICKS_POR_SEGUNDO;
    /** Tope de tiempo por cuadro, para que un tirón (por ejemplo al mover la ventana) no dispare cientos de ticks. */
    private static final float MAX_TIEMPO_CUADRO = 0.25f;
    private static final int MAX_JUGADORES_LOCALES = 2;
    private static final Color FONDO_HUD = new Color(0f, 0f, 0f, 0.55f);
    private static final float ANCHO_FRANJA_COLOR = 4f;
    private static final float ANCHO_DIVISOR = 2f;
    /** Se vuelve a una sola pantalla recién cuando el zoom necesario baja de esta fracción del máximo (evita parpadeos). */
    private static final float HISTERESIS_UNION = 0.8f;

    /** Teclas de un esquema de control. */
    private record Controles(int acelerar, int frenar, int izquierda, int derecha) {
    }

    private static final Controles WASD = new Controles(Keys.W, Keys.S, Keys.A, Keys.D);
    private static final Controles FLECHAS = new Controles(Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT);

    private final TiledMap mapa;
    private final Circuito circuito;
    private final Carrera carrera;
    private final List<Participante> locales = new ArrayList<>();
    private final List<Controles[]> controlesLocales = new ArrayList<>();
    private final Map<Integer, EntradaAuto> entradas = new HashMap<>();

    private final OrthographicCamera camaraMundo = new OrthographicCamera();
    private final FitViewport viewportMundo = new FitViewport(Config.ANCHO_VIRTUAL, Config.ALTO_VIRTUAL, camaraMundo);
    private final CamaraSeguimiento seguimiento;
    // Pantalla dividida (solo con 2 jugadores): una cámara por mitad, cada una sigue a su jugador.
    private final List<OrthographicCamera> camarasDivididas = new ArrayList<>();
    private final List<CamaraSeguimiento> seguimientosDivididos = new ArrayList<>();
    private final Image divisor = new Image(recursos.color(Paleta.BLANCO));
    private boolean dividido;
    private final VistaCircuito vistaCircuito;
    private final VistaAuto vistaAuto = new VistaAuto();

    private final List<PanelHud> paneles = new ArrayList<>();
    private final Label cartel = new Label("", skin, "titulo");
    private final Label subcartel = new Label("", skin);

    private float acumulador;
    private float tiempoTerminada;
    private boolean yaFueAResultados;

    /** @param jugadoresLocales 1 (flechas o WASD) o 2 (J1 con WASD y J2 con flechas). */
    public PantallaCarrera(Main juego, int jugadoresLocales) {
        super(juego, false);
        if (jugadoresLocales < 1 || jugadoresLocales > MAX_JUGADORES_LOCALES) {
            throw new IllegalArgumentException("Jugadores locales: de 1 a " + MAX_JUGADORES_LOCALES);
        }

        mapa = recursos.cargarMapa(RUTA_CIRCUITO);
        circuito = CargadorCircuito.cargar(mapa, Config.MAX_JUGADORES);
        carrera = new Carrera(circuito, Config.VUELTAS);
        for (int i = 0; i < jugadoresLocales; i++) {
            locales.add(carrera.agregarAuto(i, "Jugador " + (i + 1)));
            controlesLocales.add(jugadoresLocales == 1 ? new Controles[] {WASD, FLECHAS}
                : new Controles[] {i == 0 ? WASD : FLECHAS});
            entradas.put(i, new EntradaAuto());
        }

        seguimiento = new CamaraSeguimiento(camaraMundo, circuito, autosLocales());
        if (jugadoresLocales > 1) {
            for (Participante jugador : locales) {
                OrthographicCamera camara = new OrthographicCamera(Config.ANCHO_VIRTUAL / 2f, Config.ALTO_VIRTUAL);
                camarasDivididas.add(camara);
                CamaraSeguimiento seguimientoJugador = new CamaraSeguimiento(camara, circuito, List.of(jugador.auto()));
                // Mismo zoom que tenía la cámara compartida al dividirse, para que el corte no cambie la escala.
                seguimientoJugador.fijarZoom(CamaraSeguimiento.ZOOM_MAX);
                seguimientosDivididos.add(seguimientoJugador);
            }
        }
        vistaCircuito = new VistaCircuito(mapa, recursos.batch);
        armarInterfaz();
    }

    private void armarInterfaz() {
        raiz.top().pad(6);
        for (Participante jugador : locales) {
            PanelHud panel = new PanelHud(jugador);
            paneles.add(panel);
            raiz.add(panel.tabla).growX().pad(2);
        }
        raiz.row();
        Table ayuda = new Table();
        ayuda.setBackground(recursos.color(FONDO_HUD));
        ayuda.pad(3);
        ayuda.add(new Label(locales.size() == 1 ? "Flechas o WASD  -  ESC: menú" : "J1: WASD   J2: flechas  -  ESC: menú",
            skin, "gris"));
        raiz.add(ayuda).colspan(locales.size()).expand().bottom().left().padBottom(2);

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
        divisor.setVisible(false);
        escena.addActor(divisor);
    }

    private List<Auto> autosLocales() {
        List<Auto> autos = new ArrayList<>();
        for (Participante p : ordenLocalesPorPosicion()) {
            autos.add(p.auto());
        }
        return autos;
    }

    /** Los jugadores locales del primero al último en carrera. */
    private List<Participante> ordenLocalesPorPosicion() {
        List<Participante> orden = new ArrayList<>();
        for (Participante p : carrera.clasificacion()) {
            if (locales.contains(p)) {
                orden.add(p);
            }
        }
        return orden;
    }

    @Override
    protected void dibujarFondo(float delta) {
        leerEntradas();
        acumulador += Math.min(delta, MAX_TIEMPO_CUADRO);
        while (acumulador >= PASO) {
            carrera.actualizar(entradas, PASO);
            acumulador -= PASO;
        }
        float alfa = acumulador / PASO;

        List<Auto> autos = autosLocales();
        seguimiento.actualizar(autos, alfa, delta);
        for (int i = 0; i < seguimientosDivididos.size(); i++) {
            seguimientosDivididos.get(i).actualizar(List.of(locales.get(i).auto()), alfa, delta);
        }
        actualizarModoDividido(autos, alfa);

        viewportMundo.apply();
        if (!dividido) {
            dibujarMundo(camaraMundo, alfa);
        } else {
            // Cada jugador ve su propio auto en una mitad de la zona de juego (con las barras del FitViewport).
            int x = viewportMundo.getScreenX();
            int y = viewportMundo.getScreenY();
            int ancho = viewportMundo.getScreenWidth();
            int alto = viewportMundo.getScreenHeight();
            int mitad = ancho / 2;
            HdpiUtils.glViewport(x, y, mitad, alto);
            dibujarMundo(camarasDivididas.get(0), alfa);
            HdpiUtils.glViewport(x + mitad, y, ancho - mitad, alto);
            dibujarMundo(camarasDivididas.get(1), alfa);
        }
        divisor.setVisible(dividido);

        actualizarInterfaz(delta);
    }

    /**
     * Pasa a pantalla dividida cuando los autos locales no entran juntos ni con el zoom máximo, y vuelve a una
     * sola pantalla cuando se acercan lo suficiente.
     */
    private void actualizarModoDividido(List<Auto> autos, float alfa) {
        if (camarasDivididas.isEmpty()) {
            return;
        }
        float necesario = CamaraSeguimiento.zoomNecesario(autos, alfa);
        if (!dividido && necesario > CamaraSeguimiento.ZOOM_MAX) {
            dividido = true;
        } else if (dividido && necesario < CamaraSeguimiento.ZOOM_MAX * HISTERESIS_UNION) {
            dividido = false;
        }
    }

    private void dibujarMundo(OrthographicCamera camara, float alfa) {
        vistaCircuito.dibujar(camara);
        // Se dibujan de atrás hacia adelante en la clasificación para que el puntero quede arriba.
        List<Participante> clasificacion = carrera.clasificacion();
        for (int i = clasificacion.size() - 1; i >= 0; i--) {
            Participante p = clasificacion.get(i);
            vistaAuto.dibujar(p.auto(), alfa, Paleta.COLORES_AUTOS[p.id() % Paleta.COLORES_AUTOS.length], camara);
        }
    }

    private void leerEntradas() {
        for (int i = 0; i < locales.size(); i++) {
            EntradaAuto entrada = entradas.get(locales.get(i).id());
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

    private void actualizarInterfaz(float delta) {
        for (PanelHud panel : paneles) {
            panel.actualizar();
        }

        EstadoCarrera estado = carrera.estado();
        subcartel.setText("");
        if (estado == EstadoCarrera.CUENTA_REGRESIVA) {
            cartel.setText(String.valueOf(carrera.segundosCuentaRegresiva()));
        } else if (estado == EstadoCarrera.EN_CURSO) {
            cartel.setText(carrera.mostrarYa() ? "¡YA!" : "");
            if (carrera.cierreActivo()) {
                subcartel.setText("CIERRE EN " + (int) Math.ceil(carrera.cierreRestante()) + " s");
            }
        } else {
            cartel.setText("CARRERA FINALIZADA");
            tiempoTerminada += delta;
            if (tiempoTerminada >= Config.SEGUNDOS_FIN_A_RESULTADOS && !yaFueAResultados) {
                yaFueAResultados = true;
                juego.irA(new Resultados(juego, carrera.resultados()));
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
        juego.irA(new MenuPrincipal(juego));
    }

    @Override
    public void dispose() {
        vistaCircuito.dispose();
        vistaAuto.dispose();
        recursos.liberar(RUTA_CIRCUITO);
        super.dispose();
    }

    /** Panel del HUD de un jugador: franja con el color de su auto, vuelta, posición, tiempo actual y mejor vuelta. */
    private class PanelHud {
        private final Participante jugador;
        private final Table tabla = new Table();
        private final Label cabecera = new Label("", skin);
        private final Label tiempos = new Label("", skin, "gris");

        PanelHud(Participante jugador) {
            this.jugador = jugador;
            Color color = Paleta.COLORES_AUTOS[jugador.id() % Paleta.COLORES_AUTOS.length];
            tabla.setBackground(recursos.color(FONDO_HUD));
            tabla.pad(4);
            tabla.add(new Image(recursos.color(color))).width(ANCHO_FRANJA_COLOR).growY().padRight(6);
            Table textos = new Table();
            textos.add(cabecera).left().row();
            textos.add(tiempos).left();
            tabla.add(textos).left().expandX();
        }

        void actualizar() {
            int vuelta = Math.min(jugador.vueltas() + 1, carrera.vueltasTotales());
            String vueltaTexto = jugador.terminado() ? "FIN" : "VUELTA " + vuelta + "/" + carrera.vueltasTotales();
            cabecera.setText(jugador.nombre() + "   " + vueltaTexto + "   POS " + carrera.posicionDe(jugador.id()) + "/"
                + carrera.participantes().size());
            String actual = jugador.terminado() ? Tiempo.formato(jugador.tiempoTotal()) : Tiempo.formato(jugador.tiempoVuelta());
            tiempos.setText((jugador.terminado() ? "TOTAL " : "TIEMPO ") + actual + "   MEJOR " + Tiempo.formato(jugador.mejorVuelta()));
        }
    }
}
