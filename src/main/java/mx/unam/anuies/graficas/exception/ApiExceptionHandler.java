package mx.unam.anuies.graficas.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import mx.unam.anuies.graficas.dto.ErrorResponseDto;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidCsvException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidCsv(InvalidCsvException ex) {
        return build(HttpStatus.BAD_REQUEST, "archivo_invalido", ex.getMessage());
    }

    @ExceptionHandler(CsvNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(CsvNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "archivo_no_encontrado", ex.getMessage());
    }

    @ExceptionHandler(CsvStorageException.class)
    public ResponseEntity<ErrorResponseDto> handleStorage(CsvStorageException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error_almacenamiento", ex.getMessage());
    }

    @ExceptionHandler({ MaxUploadSizeExceededException.class, MultipartException.class })
    public ResponseEntity<ErrorResponseDto> handleUploadSize(Exception ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "archivo_demasiado_grande",
                "El archivo supera el tamaño máximo permitido de 10 MB.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "recurso_no_encontrado", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error_interno",
                "Ocurrió un error inesperado al procesar la solicitud.");
    }

    private ResponseEntity<ErrorResponseDto> build(HttpStatus status, String error, String mensaje) {
        return ResponseEntity.status(status).body(ErrorResponseDto.builder()
                .error(error)
                .mensaje(mensaje)
                .timestamp(Instant.now())
                .build());
    }
}
