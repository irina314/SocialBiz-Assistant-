package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Influencer;
import com.example.Project.Teza.service.InfluencerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/influenceri")
@RequiredArgsConstructor
public class InfluencerController {

    private final InfluencerService influencerService;

    @GetMapping
    public String lista(Model model) {
        List<InfluencerService.InfluencerStats> stats = influencerService.statisticiToate();

        model.addAttribute("stats",               stats);
        model.addAttribute("totalInfluenceri",    stats.size());
        model.addAttribute("nrActivi",            influencerService.nrActivi());
        model.addAttribute("venitTotal",
                String.format("%.2f", influencerService.venitTotalGenerat()));
        model.addAttribute("comenziTotal",        influencerService.comenziTotaleGenerate());
        model.addAttribute("comisionTotal",
                String.format("%.2f", influencerService.comisionTotalDatorat()));
        model.addAttribute("platforme",           List.of(
                "Instagram","TikTok","Facebook","YouTube","Podcast","Blog","Altele"));

        return "influenceri";
    }

    @PostMapping("/adauga")
    public String adauga(@ModelAttribute Influencer influencer) {
        // Normalizare cod promotional - uppercase, fara spatii
        if (influencer.getCodPromotional() != null) {
            influencer.setCodPromotional(
                    influencer.getCodPromotional().trim().toUpperCase());
        }
        influencerService.salveaza(influencer);
        return "redirect:/influenceri";
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id, @ModelAttribute Influencer influencer) {
        Influencer existent = influencerService.gaseste(id);
        influencer.setId(id);
        influencer.setUtilizator(existent.getUtilizator());
        influencer.setDataAdaugare(existent.getDataAdaugare());
        if (influencer.getCodPromotional() != null) {
            influencer.setCodPromotional(
                    influencer.getCodPromotional().trim().toUpperCase());
        }
        influencerService.salveaza(influencer);
        return "redirect:/influenceri";
    }

    @PostMapping("/sterge/{id}")
    public String sterge(@PathVariable Long id) {
        influencerService.sterge(id);
        return "redirect:/influenceri";
    }

    @PostMapping("/toggle/{id}")
    public String toggle(@PathVariable Long id) {
        Influencer inf = influencerService.gaseste(id);
        inf.setStatus(inf.getStatus() == Influencer.Status.ACTIV
                ? Influencer.Status.INACTIV : Influencer.Status.ACTIV);
        influencerService.salveaza(inf);
        return "redirect:/influenceri";
    }
}