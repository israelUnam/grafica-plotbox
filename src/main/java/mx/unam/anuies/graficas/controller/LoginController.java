package mx.unam.anuies.graficas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import mx.unam.anuies.graficas.security.CustomAuthenticationFailureHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(
            HttpServletRequest request,
            Authentication authentication,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {

        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }

        if (error != null) {
            String errorMessage = "Acceso denegado: Su cuenta de Google no se encuentra en la lista de correos autorizados.";
            String errorDetail = null;

            HttpSession session = request.getSession(false);
            if (session != null) {
                String errorCode = (String) session.getAttribute(CustomAuthenticationFailureHandler.AUTH_ERROR_CODE_SESSION_ATTR);
                String errorDesc = (String) session.getAttribute(CustomAuthenticationFailureHandler.AUTH_ERROR_DESC_SESSION_ATTR);
                Object exObj = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);

                if ("email_no_autorizado".equalsIgnoreCase(errorCode)) {
                    errorMessage = "Acceso denegado: Su cuenta de Google no se encuentra en la lista de correos autorizados.";
                    errorDetail = errorDesc;
                } else if (errorCode != null && !errorCode.isBlank()) {
                    errorMessage = "Error de autenticación con Google (" + errorCode + ")";
                    errorDetail = errorDesc != null ? errorDesc : "Ocurrió un error en el proveedor de autenticación Google OAuth2.";
                } else if (exObj instanceof AuthenticationException authEx) {
                    errorMessage = "Error de autenticación: " + authEx.getMessage();
                }

                session.removeAttribute(CustomAuthenticationFailureHandler.AUTH_ERROR_CODE_SESSION_ATTR);
                session.removeAttribute(CustomAuthenticationFailureHandler.AUTH_ERROR_DESC_SESSION_ATTR);
                session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            }

            model.addAttribute("error", errorMessage);
            model.addAttribute("errorDetail", errorDetail);
        }

        if (logout != null) {
            model.addAttribute("mensaje", "Ha cerrado sesión correctamente.");
        }

        return "login";
    }
}
