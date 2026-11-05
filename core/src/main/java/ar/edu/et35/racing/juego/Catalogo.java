package ar.edu.et35.racing.juego;

import java.util.List;

/**
 * Escuderías, pilotos y circuitos de la temporada 2026. Es la única fuente de estos datos: la red solo manda
 * índices (de piloto y de circuito) y cada PC los traduce con este catálogo, así que todas tienen que tener la misma
 * versión (si cambia el orden de las listas, sube {@code Config.VERSION_PROTOCOLO}).
 *
 * <p>Los pilotos están ordenados por escudería, de a dos: el piloto {@code i} es de la escudería {@code i / 2}.
 */
public final class Catalogo {
    private Catalogo() {
    }

    public static final List<Escuderia> ESCUDERIAS = List.of(
        new Escuderia("Mercedes", 0x27F4D2, 0x9AA0A6),
        new Escuderia("Red Bull", 0x3671C6, 0xDB0A40),
        new Escuderia("Ferrari", 0xE8002D, 0xFFEB00),
        new Escuderia("McLaren", 0xFF8700, 0x47C7FC),
        new Escuderia("Alpine", 0x0093CC, 0xFF87BC),
        new Escuderia("Aston Martin", 0x006F62, 0xCEDC00),
        new Escuderia("Williams", 0x64C4FF, 0x041E42),
        new Escuderia("Racing Bulls", 0x6692FF, 0xFFFFFF),
        new Escuderia("Audi", 0x900000, 0xDDDDDD),
        new Escuderia("Haas", 0xB6BABD, 0xE6002B),
        new Escuderia("Cadillac", 0xE8E8E8, 0x1A1A1A));

    public static final List<Piloto> PILOTOS = List.of(
        new Piloto("George Russell", "RUS", 63, "gb", 0),
        new Piloto("Kimi Antonelli", "ANT", 12, "it", 0),
        new Piloto("Max Verstappen", "VER", 1, "nl", 1),
        new Piloto("Isack Hadjar", "HAD", 6, "fr", 1),
        new Piloto("Charles Leclerc", "LEC", 16, "mc", 2),
        new Piloto("Lewis Hamilton", "HAM", 44, "gb", 2),
        new Piloto("Lando Norris", "NOR", 4, "gb", 3),
        new Piloto("Oscar Piastri", "PIA", 81, "au", 3),
        new Piloto("Pierre Gasly", "GAS", 10, "fr", 4),
        new Piloto("Franco Colapinto", "COL", 43, "ar", 4),
        new Piloto("Fernando Alonso", "ALO", 14, "es", 5),
        new Piloto("Lance Stroll", "STR", 18, "ca", 5),
        new Piloto("Alex Albon", "ALB", 23, "th", 6),
        new Piloto("Carlos Sainz Jr.", "SAI", 55, "es", 6),
        new Piloto("Liam Lawson", "LAW", 30, "nz", 7),
        new Piloto("Arvid Lindblad", "LIN", 41, "gb", 7),
        new Piloto("Nico Hülkenberg", "HUL", 27, "de", 8),
        new Piloto("Gabriel Bortoleto", "BOR", 5, "br", 8),
        new Piloto("Oliver Bearman", "BEA", 87, "gb", 9),
        new Piloto("Esteban Ocon", "OCO", 31, "fr", 9),
        new Piloto("Sergio Pérez", "PER", 11, "mx", 10),
        new Piloto("Valtteri Bottas", "BOT", 77, "fi", 10));

    /** Los 24 circuitos, en orden de calendario. */
    public static final List<InfoCircuito> CIRCUITOS = List.of(
        new InfoCircuito("melbourne", "GP de Australia", "Albert Park Circuit", "Melbourne", "au", 5.278f, 14),
        new InfoCircuito("shanghai", "GP de China", "Shanghai International Circuit", "Shanghái", "cn", 5.451f, 16),
        new InfoCircuito("suzuka", "GP de Japón", "Suzuka Circuit", "Suzuka", "jp", 5.807f, 18),
        new InfoCircuito("bahrain", "GP de Baréin", "Bahrain International Circuit", "Sakhir", "bh", 5.412f, 15),
        new InfoCircuito("jeddah", "GP de Arabia Saudita", "Jeddah Corniche Circuit", "Yeda", "sa", 6.174f, 27),
        new InfoCircuito("miami", "GP de Miami", "Miami International Autodrome", "Miami", "us", 5.412f, 19),
        new InfoCircuito("montreal", "GP de Canadá", "Circuit Gilles Villeneuve", "Montreal", "ca", 4.361f, 14),
        new InfoCircuito("monaco", "GP de Mónaco", "Circuit de Monaco", "Montecarlo", "mc", 3.337f, 19),
        new InfoCircuito("catalunya", "GP de España", "Circuit de Barcelona-Catalunya", "Montmeló", "es", 4.657f, 14),
        new InfoCircuito("spielberg", "GP de Austria", "Red Bull Ring", "Spielberg", "at", 4.318f, 10),
        new InfoCircuito("silverstone", "GP de Gran Bretaña", "Silverstone Circuit", "Silverstone", "gb", 5.891f, 18),
        new InfoCircuito("spa_francorchamps", "GP de Bélgica", "Circuit de Spa-Francorchamps", "Stavelot", "be", 7.004f, 19),
        new InfoCircuito("hungaroring", "GP de Hungría", "Hungaroring", "Budapest", "hu", 4.381f, 14),
        new InfoCircuito("zandvoort", "GP de los Países Bajos", "Circuit Zandvoort", "Zandvoort", "nl", 4.259f, 14),
        new InfoCircuito("monza", "GP de Italia", "Autodromo Nazionale Monza", "Monza", "it", 5.793f, 11),
        new InfoCircuito("madring", "GP de Madrid", "Madrid Street Circuit", "Madrid", "es", 5.473f, 20),
        new InfoCircuito("baku", "GP de Azerbaiyán", "Baku City Circuit", "Bakú", "az", 6.003f, 20),
        new InfoCircuito("marina_bay", "GP de Singapur", "Marina Bay Street Circuit", "Singapur", "sg", 4.940f, 19),
        new InfoCircuito("austin", "GP de Estados Unidos", "Circuit of the Americas", "Austin", "us", 5.513f, 20),
        new InfoCircuito("mexico_city", "GP de México", "Autódromo Hermanos Rodríguez", "Ciudad de México", "mx", 4.304f, 17),
        new InfoCircuito("interlagos", "GP de Brasil", "Interlagos Circuit", "São Paulo", "br", 4.309f, 15),
        new InfoCircuito("las_vegas", "GP de Las Vegas", "Las Vegas Strip Circuit", "Las Vegas", "us", 6.201f, 17),
        new InfoCircuito("lusail", "GP de Catar", "Lusail International Circuit", "Lusail", "qa", 5.419f, 16),
        new InfoCircuito("yas_marina", "GP de Abu Dabi", "Yas Marina Circuit", "Abu Dabi", "ae", 5.281f, 16));

    public static boolean pilotoValido(int piloto) {
        return piloto >= 0 && piloto < PILOTOS.size();
    }

    public static boolean circuitoValido(int circuito) {
        return circuito >= 0 && circuito < CIRCUITOS.size();
    }

    /** Un índice fuera de rango se trata como el 0, para que un dato roto no tire el juego. */
    public static Piloto piloto(int piloto) {
        return PILOTOS.get(pilotoValido(piloto) ? piloto : 0);
    }

    public static Escuderia escuderiaDe(int piloto) {
        return ESCUDERIAS.get(piloto(piloto).escuderia());
    }

    public static InfoCircuito circuito(int circuito) {
        return CIRCUITOS.get(circuitoValido(circuito) ? circuito : 0);
    }

    /** Índice del circuito con ese nombre de archivo, o -1 si no existe. */
    public static int indiceDeCircuito(String archivo) {
        for (int i = 0; i < CIRCUITOS.size(); i++) {
            if (CIRCUITOS.get(i).archivo().equals(archivo)) {
                return i;
            }
        }
        return -1;
    }
}
