package mx.unam.anuies.graficas.exception;

public class CsvStorageException extends RuntimeException {

    public CsvStorageException(String message) {
        super(message);
    }

    public CsvStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
