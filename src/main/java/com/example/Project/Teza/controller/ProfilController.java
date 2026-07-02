package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Utilizator;
import com.example.Project.Teza.repository.UtilizatorRepository;
import com.example.Project.Teza.service.UtilizatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profil")
@RequiredArgsConstructor
public class ProfilController {

    private final UtilizatorService    utilizatorService;
    private final UtilizatorRepository utilizatorRepository;

    @GetMapping
    public String profil(Model model) {
        model.addAttribute("utilizator", utilizatorService.getUtilizatorCurent());
        return "profil";
    }

    @PostMapping("/salveaza-firma")
    public String salveazaFirma(
            @RequestParam(required = false) String numeFirma,
            @RequestParam(required = false) String cui,
            @RequestParam(required = false) String nrRegCom,
            @RequestParam(required = false) String adresaFirma,
            @RequestParam(required = false) String emailFirma,
            @RequestParam(required = false) String telefonFirma,
            @RequestParam(required = false) String banca,
            @RequestParam(required = false) String iban,
            @RequestParam(required = false) String persoanaContactFirma,
            @RequestParam(required = false) String judetFirma,
            @RequestParam(required = false) String localitateFirma,
            @RequestParam(required = false) String stradaFirma,
            @RequestParam(required = false) String numarFirma,
            @RequestParam(required = false) String codPostalFirma,
            @RequestParam(required = false) String blocFirma,
            @RequestParam(required = false) String scaraFirma,
            @RequestParam(required = false) String etajFirma,
            @RequestParam(required = false) String apartamentFirma,
            RedirectAttributes ra) {

        Utilizator u = utilizatorService.getUtilizatorCurent();
        u.setNumeFirma(numeFirma);
        u.setCui(cui);
        u.setNrRegCom(nrRegCom);
        u.setAdresaFirma(adresaFirma);
        u.setEmailFirma(emailFirma);
        u.setTelefonFirma(telefonFirma);
        u.setBanca(banca);
        u.setIban(iban);
        u.setPersoanaContactFirma(persoanaContactFirma);
        u.setJudetFirma(judetFirma);
        u.setLocalitateFirma(localitateFirma);
        u.setStradaFirma(stradaFirma);
        u.setNumarFirma(numarFirma);
        u.setCodPostalFirma(codPostalFirma);
        u.setBlocFirma(blocFirma);
        u.setScaraFirma(scaraFirma);
        u.setEtajFirma(etajFirma);
        u.setApartamentFirma(apartamentFirma);
        utilizatorRepository.save(u);

        ra.addFlashAttribute("succes", "[OK] Datele firmei au fost salvate!");
        return "redirect:/profil";
    }
}