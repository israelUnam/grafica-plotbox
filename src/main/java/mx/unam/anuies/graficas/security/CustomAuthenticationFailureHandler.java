package mx.unam.anuies.graficas.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationFailureHandler.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String AUTH_ERROR_CODE_SESSION_ATTR = "OAUTH2_AUTH_ERROR_CODE";
    public static final String AUTH_ERROR_DESC_SESSION_ATTR = "OAUTH2_AUTH_ERROR_DESC";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String errorCode = "auth_error";
        String errorDescription = exception.getMessage();
        String errorUri = "N/D";

        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error oauth2Error = oauth2Exception.getError();
            if (oauth2Error != null) {
                errorCode = oauth2Error.getErrorCode() != null ? oauth2Error.getErrorCode() : errorCode;
                errorDescription = oauth2Error.getDescription() != null ? oauth2Error.getDescription() : errorDescription;
                errorUri = oauth2Error.getUri() != null ? oauth2Error.getUri() : errorUri;
            }
        }

        String requestUrl = request.getRequestURL() != null ? request.getRequestURL().toString() : "N/D";
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String hostHeader = request.getHeader("Host");
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");

        StringBuilder sb = new StringBuilder();
        sb.append("\n================================================================================\n");
        sb.append(" [FALLO DE AUTENTICACIÓN GOOGLE OAUTH2 EN EL SERVIDOR]\n");
        sb.append(" Fecha/Hora:           ").append(timestamp).append("\n");
        sb.append(" Excepción:            ").append(exception.getClass().getName()).append("\n");
        sb.append(" Código de Error:      ").append(errorCode).append("\n");
        sb.append(" Detalle/Mensaje:      ").append(errorDescription).append("\n");
        sb.append(" URI de Error Google:  ").append(errorUri).append("\n");
        sb.append(" URL de Retorno:       ").append(requestUrl).append(queryString).append("\n");
        sb.append(" Host Header:          ").append(hostHeader).append("\n");
        if (forwardedHost != null || forwardedProto != null) {
            sb.append(" Proxy Headers:        X-Forwarded-Proto=").append(forwardedProto)
              .append(", X-Forwarded-Host=").append(forwardedHost).append("\n");
        }

        if ("redirect_uri_mismatch".equalsIgnoreCase(errorCode)) {
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append(" DIAGNÓSTICO:\n");
            sb.append("  Google rechazó la redirección porque la URI de retorno no coincide con las 'URIs de redireccionamiento autorizadas' en Google Cloud Console.\n");
            sb.append(" SOLUCIÓN:\n");
            sb.append("  1. Entra a Google Cloud Console > Credenciales > Tu Client ID OAuth 2.0.\n");
            sb.append("  2. Agrega la URL exacta de tu servidor, por ejemplo:\n");
            sb.append("     http://<IP_O_DOMINIO_DEL_SERVIDOR>:17006/login/oauth2/code/google\n");
            sb.append("     o https://<DOMINIO>/login/oauth2/code/google si usas HTTPS/Nginx.\n");
        } else if ("email_no_autorizado".equalsIgnoreCase(errorCode)) {
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append(" DIAGNÓSTICO: El correo de Google autenticado no figura en app.security.allowed-emails.\n");
        } else if ("invalid_id_token".equalsIgnoreCase(errorCode)
                || (errorDescription != null && errorDescription.toLowerCase().contains("iat"))) {
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append(" DIAGNÓSTICO:\n");
            sb.append("  El ID Token de Google fue rechazado por el claim iat (hora de emisión).\n");
            sb.append("  Esto casi siempre indica que el reloj del servidor está desfasado respecto a la hora real/UTC.\n");
            sb.append(" SOLUCIÓN:\n");
            sb.append("  1. Sincroniza el reloj del servidor con NTP (timedatectl / chrony / ntpd).\n");
            sb.append("  2. Verifica: date -u y compáralo con https://time.is/UTC\n");
            sb.append("  3. La app ya tolera hasta 1 hora de desfase en la validación del ID Token.\n");
        } else {
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append(" DIAGNÓSTICO/AYUDA:\n");
            sb.append("  Si el error es de conexión o timeout, verifica que el servidor tenga acceso a https://accounts.google.com y https://oauth2.googleapis.com (puertos/proxy).\n");
        }
        sb.append("================================================================================\n");

        String logOutput = sb.toString();
        log.error("{}", logOutput, exception);
        System.err.println(logOutput);

        // Guardar detalles en la sesión para que LoginController los muestre al usuario en pantalla
        HttpSession session = request.getSession(true);
        session.setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, exception);
        session.setAttribute(AUTH_ERROR_CODE_SESSION_ATTR, errorCode);
        session.setAttribute(AUTH_ERROR_DESC_SESSION_ATTR, errorDescription);

        response.sendRedirect(request.getContextPath() + "/login?error=true");
    }
}
