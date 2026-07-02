package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Client;
import com.example.Project.Teza.service.ClientService;
import com.example.Project.Teza.service.RfmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clienti")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final RfmService rfmService;

    @GetMapping
    public String lista(
            @RequestParam(required = false, defaultValue = "toate") String perioada,
            @RequestParam(required = false, defaultValue = "lista") String view,
            Model model) {

        List<Client> clienti;
        LocalDateTime acum = LocalDateTime.now();

        switch (perioada) {
            case "luna" -> clienti = clientService.dupaLuna(
                    acum.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0));
            default -> clienti = clientService.toti();
        }

        List<Long> inactivi = clienti.stream()
                .filter(clientService::esteInactiv)
                .map(Client::getId)
                .toList();

        // RFM
        List<RfmService.RfmScore> rfmScores = rfmService.calculeazaRFM();
        Map<String, Long> rezumatSegmente = rfmService.rezumatSegmente();

        long nrVip = rezumatSegmente.getOrDefault("VIP", 0L);
        long nrFidel = rezumatSegmente.getOrDefault("Fidel", 0L);
        long nrPromitator = rezumatSegmente.getOrDefault("Promitator", 0L);
        long nrLaRisc = rezumatSegmente.getOrDefault("La Risc", 0L);
        long nrInactiv = rezumatSegmente.getOrDefault("Inactiv", 0L);

        // CLV total estimat
        double clvTotal = rfmScores.stream().mapToDouble(r -> r.clv).sum();

        model.addAttribute("clienti", clienti);
        model.addAttribute("perioada", perioada);
        model.addAttribute("view", view);
        model.addAttribute("inactivi", inactivi);
        model.addAttribute("totalClienti", clienti.size());
        model.addAttribute("nrInactivi", inactivi.size());
        model.addAttribute("totalIncasat",
                String.format("%.2f", clientService.totalIncasat(clienti)));
        model.addAttribute("totalDeIncasat",
                String.format("%.2f", clientService.totalDeIncasat(clienti)));
        // RFM attributes
        model.addAttribute("rfmScores", rfmScores);
        model.addAttribute("nrVip", nrVip);
        model.addAttribute("nrFidel", nrFidel);
        model.addAttribute("nrPromitator", nrPromitator);
        model.addAttribute("nrLaRisc", nrLaRisc);
        model.addAttribute("nrInactiv", nrInactiv);
        model.addAttribute("clvTotal", String.format("%.2f", clvTotal));
        model.addAttribute("totalRfm", rfmScores.size());

        return "clienti";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("client", clientService.gasesteDupaId(id));
        return "clienti";  // modal handled in same page
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id,
                       @RequestParam String nume,
                       @RequestParam String telefon,
                       @RequestParam String adresa,
                       org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        clientService.editeaza(id, nume, telefon, adresa);
        ra.addFlashAttribute("successMsg", "[OK] Client actualizat cu succes!");
        return "redirect:/clienti";
    }

    @PostMapping("/sterge/{id}")
    public String sterge(@PathVariable Long id,
                         org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        clientService.sterge(id);
        ra.addFlashAttribute("successMsg", "[OK] Client sters cu succes!");
        return "redirect:/clienti";
    }
}