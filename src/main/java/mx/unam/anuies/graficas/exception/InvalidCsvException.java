package mx.unam.anuies.graficas.exception;

public class InvalidCsvException extends RuntimeException {

    public InvalidCsvException(String message) {
        super(message);
    }

    public InvalidCsvException(String message, Throwable cause) {
        super(message, cause);
    }
}
