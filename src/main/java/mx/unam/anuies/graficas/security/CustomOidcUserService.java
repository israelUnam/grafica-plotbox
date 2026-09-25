package mx.unam.anuies.graficas.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final EmailAuthorizationService emailAuthorizationService;

    public CustomOidcUserService(EmailAuthorizationService emailAuthorizationService) {
        this.emailAuthorizationService = emailAuthorizationService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();
        String sub = oidcUser.getSubject();

        System.out.println(String.format("[OIDC/GOOGLE] Usuario autenticado recibido de Google: email='%s', nombre='%s', sub='%s'",
                email, name, sub));

        if (!emailAuthorizationService.isEmailAllowed(email)) {
            emailAuthorizationService.registrarIntentoNoAutorizado(email, name, sub);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_no_autorizado",
                            String.format("El correo '%s' no figura en la lista de correos autorizados (app.security.allowed-emails).", email),
                            null));
        }

        emailAuthorizationService.registrarAccesoExitoso(email, name, sub);
        return oidcUser;
    }
}
