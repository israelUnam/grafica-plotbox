package mx.unam.anuies.graficas.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import mx.unam.anuies.graficas.service.CsvStorageService;

@Controller
public class DashboardController {

    private final CsvStorageService csvStorageService;

    public DashboardController(CsvStorageService csvStorageService) {
        this.csvStorageService = csvStorageService;
    }

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal OAuth2User user) {
        model.addAttribute("archivos", csvStorageService.listar());

        if (user != null) {
            String nombre = user.getAttribute("name");
            String email = user.getAttribute("email");
            String foto = user.getAttribute("picture");

            model.addAttribute("usuarioNombre", nombre != null ? nombre : email);
            model.addAttribute("usuarioEmail", email);
            model.addAttribute("usuarioFoto", foto);
        }

        return "index";
    }
}
