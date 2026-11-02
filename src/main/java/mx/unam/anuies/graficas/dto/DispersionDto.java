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
public class DispersionDto {

    private String titulo;
    private String nombreArchivo;
    private String etiquetaNombre;
    private String etiquetaX;
    private String etiquetaY;
    private String etiquetaGrupo;
    /** true cuando la línea de título contiene "log -coordenadas" (escala logarítmica en las ordenadas). */
    private boolean escalaLogY;
    /** Grupos ordenados de menor a mayor según el valor mínimo de X de sus puntos. */
    private List<String> grupos;
    private List<PuntoDispersionDto> puntos;
    private List<String> excluidos;
}
