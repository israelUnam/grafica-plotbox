package mx.unam.anuies.graficas.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAuthorizationServiceTest {

    @Test
    void permiteCualquierCorreoSiConfiguracionEstaVacia() {
        EmailAuthorizationService service = new EmailAuthorizationService("");
        assertThat(service.isEmailAllowed("cualquiera@gmail.com")).isTrue();
        assertThat(service.isEmailAllowed("otro@unam.mx")).isTrue();
    }

    @Test
    void permiteSoloCorreosEnListaDeFormaInsensibleAMayusculasYEspacios() {
        EmailAuthorizationService service = new EmailAuthorizationService(
                "  usuario1@unam.mx , USUARIO2@GMAIL.COM, admin@institucion.gob.mx ");

        assertThat(service.isEmailAllowed("usuario1@unam.mx")).isTrue();
        assertThat(service.isEmailAllowed("USUARIO1@UNAM.MX")).isTrue();
        assertThat(service.isEmailAllowed("usuario2@gmail.com")).isTrue();
        assertThat(service.isEmailAllowed("admin@institucion.gob.mx")).isTrue();

        assertThat(service.isEmailAllowed("noautorizado@gmail.com")).isFalse();
        assertThat(service.isEmailAllowed("")).isFalse();
        assertThat(service.isEmailAllowed(null)).isFalse();
    }

    @Test
    void registraIntentoNoAutorizadoSinErrores() {
        EmailAuthorizationService service = new EmailAuthorizationService("admin@unam.mx");
        service.registrarIntentoNoAutorizado("intruso@test.com", "Intruso Demo", "987654321");
        // Verifica que se ejecute el log y la salida a consola sin excepciones
        assertThat(service.isEmailAllowed("intruso@test.com")).isFalse();
    }

    @Test
    void registraAccesoExitosoSinErrores() {
        EmailAuthorizationService service = new EmailAuthorizationService("usuario.autorizado@unam.mx");
        service.registrarAccesoExitoso("usuario.autorizado@unam.mx", "Usuario Autorizado", "123456789");
        assertThat(service.isEmailAllowed("usuario.autorizado@unam.mx")).isTrue();
    }
}
