package mx.unam.anuies.graficas.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ErrorResponseDto {

    String error;
    String mensaje;
    Instant timestamp;
}
