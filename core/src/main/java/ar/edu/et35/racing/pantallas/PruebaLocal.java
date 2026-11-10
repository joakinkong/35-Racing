package ar.edu.et35.racing.pantallas;

import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.juego.Catalogo;
import ar.edu.et35.racing.juego.InfoCircuito;
import ar.edu.et35.racing.juego.NivelBot;
import ar.edu.et35.racing.juego.PartidaLocal;
import ar.edu.et35.racing.juego.Piloto;
import ar.edu.et35.racing.util.Config;
import ar.edu.et35.racing.util.Paleta;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import java.util.List;

/**
 * Prueba local (sin red): se elige el circuito entre los del catálogo, si juegan 1 o 2 personas en el mismo teclado, con
 * qué piloto corre cada una y cuántos bots hay en pista y de qué nivel. Se corre con CORRER o ENTER; las flechas izquierda
 * y derecha cambian el circuito.
 */
public class PruebaLocal extends PantallaBase {
    private static final float ANCHO_COLUMNA = 620f;
    private static final float ANCHO_FLECHA = 56f;
    private static final float ANCHO_POSICION = 90f;
    private static final float ALTO_BOTON = 48f;
    private static final float ALTO_SELECTOR = 36f;
    private static final float ANCHO_ETIQUETA = 190f;
    private static final float ANCHO_VALOR = 300f;

    private final Label gp = new Label("", skin, "subtitulo");
    private final Label datos = new Label("", skin, "suave");
    private final Label posicion = new Label("", skin, "numeros");
    private final Table panelPartida = tarjeta();
    private final Label teclas = new Label("", skin, "etiqueta");
    private int indice;
    private int jugadores;
    private int pilotoJ1;
    private int pilotoJ2;
    private int bots;
    private NivelBot nivel;

    public PruebaLocal(Main juego) {
        this(juego, Config.CIRCUITO);
    }

    /** @param archivo circuito elegido al abrir la pantalla (nombre del archivo); si no existe, el primero del catálogo */
    public PruebaLocal(Main juego, String archivo) {
        this(juego, archivo, null);
    }

    /**
     * @param archivo circuito elegido al abrir la pantalla (nombre del archivo); si no existe, el primero del catálogo
     * @param partida lo elegido la vez anterior, para no tener que cargarlo de nuevo al volver de la carrera; null = lo de siempre
     */
    public PruebaLocal(Main juego, String archivo, PartidaLocal partida) {
        super(juego);
        indice = Math.max(0, Catalogo.indiceDeCircuito(archivo));
        PartidaLocal inicial = partida != null ? partida : new PartidaLocal(List.of(0, 2), 0, NivelBot.MEDIO);
        jugadores = inicial.jugadores();
        pilotoJ1 = inicial.pilotosJugadores().get(0);
        pilotoJ2 = inicial.jugadores() > 1 ? inicial.pilotosJugadores().get(1) : (pilotoJ1 == 2 ? 4 : 2);
        bots = inicial.bots();
        nivel = inicial.nivel();
        posicion.setAlignment(Align.center);

        Table circuito = tarjeta(Paleta.SECUNDARIO);
        circuito.add(new Label("CIRCUITO", skin, "etiqueta")).colspan(2).left().padBottom(8).row();
        circuito.add(gp).left().expandX().padBottom(2);
        circuito.add(selector()).right().padLeft(12f).row();
        circuito.add(datos).colspan(2).left();

        Table botones = new Table();
        botones.add(botonPrimario("CORRER", this::correr)).growX().uniformX().height(ALTO_BOTON).padRight(8);
        botones.add(boton("VOLVER", this::volver)).growX().uniformX().height(ALTO_BOTON);

        contenido.add(titular("SIN RED", "PRUEBA LOCAL")).left().padBottom(16).row();
        contenido.add(circuito).width(ANCHO_COLUMNA).padBottom(12).row();
        contenido.add(panelPartida).width(ANCHO_COLUMNA).padBottom(12).row();
        contenido.add(botones).width(ANCHO_COLUMNA).padBottom(14).row();
        contenido.add(teclas).left();
        actualizarTextos();
        armarPartida();
    }

    private Table selector() {
        int total = Catalogo.CIRCUITOS.size();
        Table fila = new Table();
        fila.add(boton("<", () -> cambiar(Math.floorMod(indice - 1, total)))).size(ANCHO_FLECHA, ALTO_BOTON);
        fila.add(posicion).width(ANCHO_POSICION);
        fila.add(boton(">", () -> cambiar(Math.floorMod(indice + 1, total)))).size(ANCHO_FLECHA, ALTO_BOTON);
        return fila;
    }

    /** Una fila "ETIQUETA  <  valor  >" de la tarjeta de la partida. */
    private Table fila(String nombre, String valor, Runnable menos, Runnable mas) {
        Table fila = new Table();
        fila.add(new Label(nombre, skin, "etiqueta")).width(ANCHO_ETIQUETA).left();
        fila.add(boton("<", menos)).size(ANCHO_FLECHA, ALTO_SELECTOR);
        Label etiqueta = new Label(valor, skin, "default");
        etiqueta.setAlignment(Align.center);
        etiqueta.setEllipsis(true);
        fila.add(etiqueta).width(ANCHO_VALOR);
        fila.add(boton(">", mas)).size(ANCHO_FLECHA, ALTO_SELECTOR);
        return fila;
    }

    /** Vuelve a armar la tarjeta con jugadores, pilotos, bots y nivel; se llama al cambiar cualquiera de ellos. */
    private void armarPartida() {
        panelPartida.clearChildren();
        panelPartida.add(new Label("PARTIDA", skin, "etiqueta")).left().padBottom(8).row();
        Runnable alternarJugadores = () -> {
            jugadores = jugadores == 1 ? 2 : 1;
            bots = Math.min(bots, PartidaLocal.maxBots(jugadores));
            armarPartida();
        };
        panelPartida.add(fila("JUGADORES", jugadores == 1 ? "1 JUGADOR" : "2 JUGADORES", alternarJugadores, alternarJugadores))
            .left().padBottom(4).row();
        panelPartida.add(fila(jugadores == 1 ? "TU PILOTO" : "PILOTO J1", nombrePiloto(pilotoJ1),
            () -> {
                pilotoJ1 = siguientePiloto(pilotoJ1, -1, jugadores == 2 ? pilotoJ2 : -1);
                armarPartida();
            }, () -> {
                pilotoJ1 = siguientePiloto(pilotoJ1, 1, jugadores == 2 ? pilotoJ2 : -1);
                armarPartida();
            })).left().padBottom(4).row();
        if (jugadores == 2) {
            panelPartida.add(fila("PILOTO J2", nombrePiloto(pilotoJ2),
                () -> {
                    pilotoJ2 = siguientePiloto(pilotoJ2, -1, pilotoJ1);
                    armarPartida();
                }, () -> {
                    pilotoJ2 = siguientePiloto(pilotoJ2, 1, pilotoJ1);
                    armarPartida();
                })).left().padBottom(4).row();
        }
        int maximo = PartidaLocal.maxBots(jugadores);
        panelPartida.add(fila("BOTS EN PISTA", bots + "  (de 0 a " + maximo + ")",
            () -> {
                bots = Math.max(0, bots - 1);
                armarPartida();
            }, () -> {
                bots = Math.min(maximo, bots + 1);
                armarPartida();
            })).left().padBottom(4).row();
        Runnable siguienteNivel = () -> {
            nivel = NivelBot.values()[(nivel.ordinal() + 1) % NivelBot.values().length];
            armarPartida();
        };
        Runnable anteriorNivel = () -> {
            nivel = NivelBot.values()[Math.floorMod(nivel.ordinal() - 1, NivelBot.values().length)];
            armarPartida();
        };
        panelPartida.add(fila("NIVEL DE LOS BOTS", nivel.nombre(), anteriorNivel, siguienteNivel)).left();
        teclas.setText(jugadores == 1 ? "FLECHAS O WASD" : "J1: WASD   ·   J2: FLECHAS");
    }

    private static String nombrePiloto(int piloto) {
        Piloto p = Catalogo.piloto(piloto);
        return p.apellido() + "  ·  " + Catalogo.ESCUDERIAS.get(p.escuderia()).nombre();
    }

    /** El piloto siguiente (o anterior) en el catálogo, salteando el que ya usa el otro jugador. */
    private static int siguientePiloto(int actual, int paso, int ocupado) {
        int total = Catalogo.PILOTOS.size();
        int nuevo = Math.floorMod(actual + paso, total);
        return nuevo == ocupado ? Math.floorMod(nuevo + paso, total) : nuevo;
    }

    private void cambiar(int nuevo) {
        indice = nuevo;
        actualizarTextos();
    }

    private void actualizarTextos() {
        InfoCircuito info = Catalogo.circuito(indice);
        gp.setText(info.gp() + " - " + info.ciudad());
        datos.setText(info.nombre() + "  ·  " + String.valueOf(info.km()).replace('.', ',') + " km  ·  " + info.curvas() + " curvas");
        posicion.setText((indice + 1) + " / " + Catalogo.CIRCUITOS.size());
    }

    private void correr() {
        List<Integer> pilotos = jugadores == 1 ? List.of(pilotoJ1) : List.of(pilotoJ1, pilotoJ2);
        juego.irA(new PantallaCarrera(juego, new PartidaLocal(pilotos, bots, nivel), Catalogo.circuito(indice).archivo()));
    }

    @Override
    protected void actualizar(float delta) {
        int total = Catalogo.CIRCUITOS.size();
        if (Gdx.input.isKeyJustPressed(Keys.LEFT)) {
            cambiar(Math.floorMod(indice - 1, total));
        } else if (Gdx.input.isKeyJustPressed(Keys.RIGHT)) {
            cambiar(Math.floorMod(indice + 1, total));
        } else if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            correr();
        }
    }

    @Override
    protected void volver() {
        juego.irA(new MenuPrincipal(juego));
    }
}
