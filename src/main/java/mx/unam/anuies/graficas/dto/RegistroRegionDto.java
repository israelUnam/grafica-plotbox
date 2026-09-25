package mx.unam.anuies.graficas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRegionDto {

    private String region;
    private Integer universidades;
    private Double media;
    private Double mediana;
    private Double maximo;
    private Double minimo;
    private Double q1;
    private Double q3;
}
