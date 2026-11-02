package mx.unam.anuies.graficas.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import mx.unam.anuies.graficas.service.CsvStorageService;
import mx.unam.anuies.graficas.service.DispersionStorageService;

@Controller
public class DashboardController {

    private final CsvStorageService csvStorageService;
    private final DispersionStorageService dispersionStorageService;

    public DashboardController(CsvStorageService csvStorageService,
            DispersionStorageService dispersionStorageService) {
        this.csvStorageService = csvStorageService;
        this.dispersionStorageService = dispersionStorageService;
    }

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal OAuth2User user) {
        model.addAttribute("archivos", csvStorageService.listar());
        agregarUsuario(model, user);
        return "index";
    }

    @GetMapping("/dispersion")
    public String dispersion(Model model, @AuthenticationPrincipal OAuth2User user) {
        model.addAttribute("archivos", dispersionStorageService.listar());
        agregarUsuario(model, user);
        return "dispersion";
    }

    private void agregarUsuario(Model model, OAuth2User user) {
        if (user == null) {
            return;
        }
        String nombre = user.getAttribute("name");
        String email = user.getAttribute("email");
        String foto = user.getAttribute("picture");

        model.addAttribute("usuarioNombre", nombre != null ? nombre : email);
        model.addAttribute("usuarioEmail", email);
        model.addAttribute("usuarioFoto", foto);
    }
}
