package ar.edu.et35.racing.juego;

public record Piloto(String nombre, String codigo, int numero, String pais, int escuderia) {

    public String apellido() {
        String[] partes = nombre.split(" ");
        boolean termina = partes.length > 2 && partes[partes.length - 1].equals("Jr.");
        return partes[termina ? partes.length - 2 : partes.length - 1];
    }
}
