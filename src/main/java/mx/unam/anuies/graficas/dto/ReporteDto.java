package mx.unam.anuies.graficas.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReporteDto {

    private String titulo;
    private String nombreArchivo;
    private String nombreCategoria;
    /** true cuando la 2ª celda de la línea de título contiene "dec" (escala lineal sin nota). */
    private boolean escalaDecimal;
    private List<RegistroRegionDto> registros;
}
