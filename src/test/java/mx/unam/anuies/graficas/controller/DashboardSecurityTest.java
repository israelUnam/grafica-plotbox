package mx.unam.anuies.graficas.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/graficas-sec-test-datos",
        "app.security.allowed-emails=juan.perez@unam.mx,admin@unam.mx"
})
class DashboardSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void usuarioNoAutenticadoEsRedirigidoALogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void paginaDeLoginEsPublica() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tablero de Gráficas Box Plot")))
                .andExpect(content().string(containsString("Iniciar sesión con Google")))
                .andExpect(content().string(containsString("/oauth2/authorization/google")));
    }

    @Test
    void paginaDeLoginMuestraErrorCuandoHayParametro() throws Exception {
        mockMvc.perform(get("/login?error=true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Acceso denegado")));
    }

    @Test
    void paginaDeLoginMuestraMensajeDeLogout() throws Exception {
        mockMvc.perform(get("/login?logout=true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ha cerrado sesión correctamente.")));
    }

    @Test
    void rutaDeAutorizacionGoogleIniciaRedireccionOAuth2() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("accounts.google.com/o/oauth2/v2/auth")));
    }

    @Test
    void recursosEstaticosSonPublicos() throws Exception {
        mockMvc.perform(get("/css/dashboard.css"))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioAutenticadoAccedeAlDashboardYVeSuPerfil() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()
                .attributes(attrs -> {
                    attrs.put("name", "Juan Perez");
                    attrs.put("email", "juan.perez@unam.mx");
                    attrs.put("picture", "https://example.com/avatar.jpg");
                    attrs.put("sub", "123456789");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Juan Perez")))
                .andExpect(content().string(containsString("juan.perez@unam.mx")))
                .andExpect(content().string(containsString("Salir")));
    }

    @Test
    void usuarioAutenticadoAccedeASeccionDeDispersion() throws Exception {
        mockMvc.perform(get("/dispersion").with(oauth2Login()
                .attributes(attrs -> {
                    attrs.put("name", "Juan Perez");
                    attrs.put("email", "juan.perez@unam.mx");
                    attrs.put("sub", "123456789");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tablero de diagramas de dispersión")))
                .andExpect(content().string(containsString("Diagramas de caja")))
                .andExpect(content().string(containsString("Juan Perez")));
    }

    @Test
    void usuarioNoAutenticadoNoAccedeADispersion() throws Exception {
        mockMvc.perform(get("/dispersion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void usuarioAutenticadoEnLoginEsRedirigidoAlDashboard() throws Exception {
        mockMvc.perform(get("/login").with(oauth2Login()
                .attributes(attrs -> {
                    attrs.put("name", "Juan Perez");
                    attrs.put("email", "juan.perez@unam.mx");
                    attrs.put("sub", "123456789");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
