package ar.edu.et35.racing.vista;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Disposable;

/** Dibuja el mapa de Tiled. No es dueña del TiledMap ni del Batch: los libera quien los creó. */
public class VistaCircuito implements Disposable {
    private final OrthogonalTiledMapRenderer renderizador;

    public VistaCircuito(TiledMap mapa, Batch batch) {
        this.renderizador = new OrthogonalTiledMapRenderer(mapa, batch);
    }

    public void dibujar(OrthographicCamera camara) {
        renderizador.setView(camara);
        renderizador.render();
    }

    @Override
    public void dispose() {
        renderizador.dispose();
    }
}
