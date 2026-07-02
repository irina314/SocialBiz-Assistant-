package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Utilizator;
import com.example.Project.Teza.repository.UtilizatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UtilizatorRepository utilizatorRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (error != null) model.addAttribute("eroare", "Email sau parola incorecta!");
        if (logout != null) model.addAttribute("mesaj", "Te-ai deconectat cu succes.");
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String parola,
                           @RequestParam String numeAfacere,
                           @RequestParam(required = false) String telefon,
                           Model model) {

        if (utilizatorRepository.findByEmail(email).isPresent()) {
            model.addAttribute("eroare", "Exista deja un cont cu acest email!");
            return "register";
        }

        Utilizator utilizator = new Utilizator();
        utilizator.setEmail(email);
        utilizator.setParola(passwordEncoder.encode(parola));
        utilizator.setNumeAfacere(numeAfacere);
        utilizator.setTelefon(telefon);
        utilizatorRepository.save(utilizator);

        return "redirect:/login?registered=true";
    }
}