package mx.unam.anuies.graficas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PuntoDispersionDto {

    private String nombre;
    private Double x;
    private Double y;
    private String grupo;
}
