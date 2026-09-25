package mx.unam.anuies.graficas.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final EmailAuthorizationService emailAuthorizationService;

    public CustomOAuth2UserService(EmailAuthorizationService emailAuthorizationService) {
        this.emailAuthorizationService = emailAuthorizationService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String sub = oauth2User.getAttribute("sub");

        System.out.println(String.format("[OAUTH2/GOOGLE] Atributos recibidos de Google: email='%s', name='%s', sub='%s'",
                email, name, sub));

        if (!emailAuthorizationService.isEmailAllowed(email)) {
            emailAuthorizationService.registrarIntentoNoAutorizado(email, name, sub);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_no_autorizado",
                            String.format("El correo '%s' no figura en la lista de correos autorizados (app.security.allowed-emails).", email),
                            null));
        }

        emailAuthorizationService.registrarAccesoExitoso(email, name, sub);
        return oauth2User;
    }
}
