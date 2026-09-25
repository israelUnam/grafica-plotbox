package mx.unam.anuies.graficas.exception;

public class CsvNotFoundException extends RuntimeException {

    public CsvNotFoundException(String nombreArchivo) {
        super("No se encontró el archivo: " + nombreArchivo);
    }
}
