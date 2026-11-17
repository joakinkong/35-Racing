package ar.edu.et35.racing.juego;

public record InfoCircuito(String archivo, String gp, String nombre, String ciudad, String pais, float km, int curvas) {
    private static final String[] PREFIJOS_GP = {"GP de los ", "GP de "};

    public String gpCorto() {
        for (String prefijo : PREFIJOS_GP) {
            if (gp.startsWith(prefijo)) {
                return gp.substring(prefijo.length());
            }
        }
        return gp;
    }
}
