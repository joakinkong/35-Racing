package ar.edu.et35.racing.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import ar.edu.et35.racing.Main;
import ar.edu.et35.racing.util.Config;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return;
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new Main(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("35 Racing");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);

        configuration.setBackBufferConfig(8, 8, 8, 8, 16, 0, Config.MUESTRAS_ANTIALIASING);

        configuration.setWindowedMode(Config.VENTANA_ANCHO, Config.VENTANA_ALTO);
        configuration.setResizable(true);
        configuration.setWindowSizeLimits(Config.ANCHO_MUNDO, Config.ALTO_MUNDO, -1, -1);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");

        return configuration;
    }
}