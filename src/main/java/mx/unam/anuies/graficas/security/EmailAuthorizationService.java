package mx.unam.anuies.graficas.security;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(EmailAuthorizationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String allowedEmailsConfig;

    public EmailAuthorizationService(@Value("${app.security.allowed-emails:}") String allowedEmailsConfig) {
        this.allowedEmailsConfig = allowedEmailsConfig != null ? allowedEmailsConfig.trim() : "";
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        Set<String> set = getAllowedEmailsSet();
        StringBuilder sb = new StringBuilder();
        sb.append("\n================================================================================\n");
        sb.append(" [CONFIGURACIÓN DE SEGURIDAD - CORREOS AUTORIZADOS]\n");
        if (set.isEmpty()) {
            sb.append(" Estado: ATENCIÓN: No hay correos definidos en 'app.security.allowed-emails'.\n");
            sb.append("         Cualquier cuenta de Google autenticada podrá acceder.\n");
        } else {
            sb.append(" Correos autorizados configurados (").append(set.size()).append("):\n");
            set.forEach(e -> sb.append("   -> ").append(e).append("\n"));
        }
        sb.append("================================================================================\n");
        String out = sb.toString();
        log.info("{}", out);
        System.out.println(out);
    }

    public Set<String> getAllowedEmailsSet() {
        if (allowedEmailsConfig == null || allowedEmailsConfig.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedEmailsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public boolean isEmailAllowed(String email) {
        Set<String> allowedSet = getAllowedEmailsSet();
        if (allowedSet.isEmpty()) {
            return true;
        }

        if (email == null || email.isBlank()) {
            System.err.println("[SEGURIDAD] Rechazado: El usuario no proporcionó ningún correo desde Google.");
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        boolean allowed = allowedSet.contains(normalizedEmail);
        if (!allowed) {
            System.err.println(String.format(
                    "[SEGURIDAD] Comparación fallida: Correo recibido de Google='%s' (normalizado='%s') NO coincide con la lista autorizada=%s",
                    email, normalizedEmail, allowedSet));
        }
        return allowed;
    }

    public void registrarIntentoNoAutorizado(String email, String nombre, String sub) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String mensaje = String.format(
                "[ALERTA DE SEGURIDAD] ACCESO NO AUTORIZADO - Correo: '%s' | Nombre: '%s' | Google Sub: '%s' | Fecha: %s",
                email != null ? email : "DESCONOCIDO",
                nombre != null ? nombre : "N/D",
                sub != null ? sub : "N/D",
                timestamp
        );

        log.warn("{}", mensaje);
        System.err.println(mensaje);
    }

    public void registrarAccesoExitoso(String email, String nombre, String sub) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String mensaje = String.format(
                "[ACCESO PERMITIDO] INICIO DE SESIÓN EXITOSO - Correo: '%s' | Nombre: '%s' | Google Sub: '%s' | Fecha: %s",
                email != null ? email : "DESCONOCIDO",
                nombre != null ? nombre : "N/D",
                sub != null ? sub : "N/D",
                timestamp
        );

        log.info("{}", mensaje);
        System.out.println(mensaje);
    }
}
