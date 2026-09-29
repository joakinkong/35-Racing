package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Auto;
import ar.edu.et35.racing.juego.CargadorCircuito;
import ar.edu.et35.racing.juego.Circuito;
import ar.edu.et35.racing.juego.EntradaAuto;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import ar.edu.et35.racing.vista.CamaraSeguimiento;
import ar.edu.et35.racing.vista.VistaAuto;
import ar.edu.et35.racing.vista.VistaCircuito;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Carrera de un jugador en el circuito. La simulación corre a paso fijo (1 / TICKS_POR_SEGUNDO) con un
 * acumulador, y el dibujo va desacoplado: interpola entre el último tick y el siguiente.
 */
public class Carrera extends PantallaBase {
    private static final String RUTA_CIRCUITO = "circuitos/circuito1.tmx";
    private static final float PASO = 1f / Config.TICKS_POR_SEGUNDO;
    /** Tope de tiempo por cuadro, para que un tirón (por ejemplo al mover la ventana) no dispare cientos de ticks. */
    private static final float MAX_TIEMPO_CUADRO = 0.25f;
    /** Solo para mostrar en el HUD: convierte px/s del juego a "km/h". */
    private static final float FACTOR_KMH = 0.9f;
    private static final Color FONDO_HUD = new Color(0f, 0f, 0f, 0.55f);

    private final TiledMap mapa;
    private final Circuito circuito;
    private final Auto auto;
    private final EntradaAuto entrada = new EntradaAuto();

    private final OrthographicCamera camaraMundo = new OrthographicCamera();
    private final FitViewport viewportMundo = new FitViewport(Config.ANCHO_VIRTUAL, Config.ALTO_VIRTUAL, camaraMundo);
    private final CamaraSeguimiento seguimiento;
    private final VistaCircuito vistaCircuito;
    private final VistaAuto vistaAuto = new VistaAuto();
    private final Label etiquetaVelocidad;

    private float acumulador;

    public Carrera(Main juego) {
        super(juego, false);

        mapa = recursos.cargarMapa(RUTA_CIRCUITO);
        circuito = CargadorCircuito.cargar(mapa, Config.MAX_JUGADORES);
        auto = new Auto(circuito.posicionesLargada().get(0).x, circuito.posicionesLargada().get(0).y,
            circuito.anguloLargada());
        seguimiento = new CamaraSeguimiento(camaraMundo, circuito, auto);
        vistaCircuito = new VistaCircuito(mapa, recursos.batch);

        etiquetaVelocidad = new Label("", skin);
        armarInterfaz();
    }

    private void armarInterfaz() {
        Table hud = new Table();
        hud.setBackground(recursos.color(FONDO_HUD));
        hud.pad(4);
        hud.add(etiquetaVelocidad).left().expandX();
        hud.add(new Label("ESC: menú", skin, "gris")).padRight(10);
        // Botón provisorio para llegar a Resultados hasta que exista la lógica de carrera.
        hud.add(boton("FINALIZAR", () -> juego.irA(new Resultados(juego)))).width(90).height(20);

        raiz.top().pad(6);
        raiz.add(hud).growX();
    }

    @Override
    protected void dibujarFondo(float delta) {
        leerEntrada();
        acumulador += Math.min(delta, MAX_TIEMPO_CUADRO);
        while (acumulador >= PASO) {
            auto.actualizar(entrada, circuito, PASO);
            acumulador -= PASO;
        }
        float alfa = acumulador / PASO;

        seguimiento.actualizar(auto, alfa, delta);
        viewportMundo.apply();
        vistaCircuito.dibujar(camaraMundo);
        vistaAuto.dibujar(auto, alfa, Paleta.ROJO, camaraMundo);

        etiquetaVelocidad.setText(Math.round(auto.rapidez() * FACTOR_KMH) + " km/h");
    }

    private void leerEntrada() {
        entrada.acelerar = Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.W);
        entrada.frenar = Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.S);
        int izquierda = Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A) ? 1 : 0;
        int derecha = Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D) ? 1 : 0;
        entrada.giro = izquierda - derecha;
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
}
