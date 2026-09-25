package mx.unam.anuies.graficas.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;

import mx.unam.anuies.graficas.security.CustomOAuth2UserService;
import mx.unam.anuies.graficas.security.CustomOidcUserService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOidcUserService oidcUserService;
    private final CustomOAuth2UserService oauth2UserService;
    private final mx.unam.anuies.graficas.security.CustomAuthenticationFailureHandler authenticationFailureHandler;

    public SecurityConfig(
            CustomOidcUserService oidcUserService,
            CustomOAuth2UserService oauth2UserService,
            mx.unam.anuies.graficas.security.CustomAuthenticationFailureHandler authenticationFailureHandler) {
        this.oidcUserService = oidcUserService;
        this.oauth2UserService = oauth2UserService;
        this.authenticationFailureHandler = authenticationFailureHandler;
    }

    /**
     * Tolerancia ante desfase de reloj entre el servidor y Google.
     * Por defecto Spring solo permite 60s; si el servidor está atrasado,
     * el claim iat del ID Token se rechaza como "invalid claims".
     * Aquí se permite hasta 1 hora de desfase.
     */
    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(clientRegistration -> {
            OidcIdTokenValidator validator = new OidcIdTokenValidator(clientRegistration);
            validator.setClockSkew(Duration.ofHours(1));
            return validator;
        });
        return factory;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler(authenticationFailureHandler)
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(oidcUserService)
                    .userService(oauth2UserService)
                )
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );

        return http.build();
    }
}
