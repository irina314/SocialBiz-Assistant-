package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.CampanieMktg;
import com.example.Project.Teza.service.MarketingService;
import com.example.Project.Teza.service.ProdusService;
import com.example.Project.Teza.service.ReturService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/analiza-marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingService marketingService;
    private final ProdusService produsService;
    private final ReturService returService;

    @GetMapping
    public String index(Model model) {
        double roas          = marketingService.roas();
        double roasReal      = marketingService.roasReal();
        double venitTotal    = marketingService.venitTotal();
        double bugetCheltuit = marketingService.bugetTotalCheltuit();
        double costInfluenceri = marketingService.costInfluenceriTotal();
        double costuriRetururi = returService.totalRambursari() + returService.totalCostLogisticRetururi();

        model.addAttribute("campanii",         marketingService.toateCampaniile());
        model.addAttribute("venitTotal",       String.format("%.2f", venitTotal));
        model.addAttribute("bugetCheltuit",    String.format("%.2f", bugetCheltuit));
        model.addAttribute("costInfluenceri",  String.format("%.2f", costInfluenceri));
        model.addAttribute("costuriRetururi",  String.format("%.2f", costuriRetururi));
        model.addAttribute("roas",             String.format("%.1f", roas));
        model.addAttribute("roasStatus",       MarketingService.roasStatusLabel(roas));
        model.addAttribute("roasReal",         String.format("%.1f", roasReal));
        model.addAttribute("roasRealStatus",   MarketingService.roasStatusLabel(roasReal));
        model.addAttribute("sursaPrincipala",  marketingService.sursaPrincipala());
        model.addAttribute("procentSursa",     String.format("%.0f", marketingService.procentSursaPrincipala()));
        model.addAttribute("nrCampaniiActive", marketingService.numarCampaniiActive());
        model.addAttribute("venitPeSursa",     marketingService.venitPeSursa());
        model.addAttribute("comenziPeSursa",   marketingService.comenziPeSursa());
        model.addAttribute("topCoduriPromo",   marketingService.topCoduriPromo());
        model.addAttribute("platforme",        CampanieMktg.Platforma.values());
        model.addAttribute("produseActive",    produsService.toateActive());

        // Profit net estimat (venit - buget reclame - comisioane influenceri)
        double profitEst = venitTotal - bugetCheltuit - costInfluenceri;
        model.addAttribute("profitEstimat", String.format("%.2f", profitEst));
        model.addAttribute("profitPozitiv", profitEst >= 0);

        // venitMaxim pentru bare sursa
        Map<String, Double> venitPeSursa = marketingService.venitPeSursa();
        double venitMaxim = venitPeSursa.values().stream()
                .max(Double::compareTo).orElse(1.0);
        model.addAttribute("venitMaxim", venitMaxim == 0 ? 1.0 : venitMaxim);

        // Date grafic lunar ca liste separate (evita probleme Thymeleaf inline)
        Map<String, Double> venitLunar = marketingService.venitLunar();
        List<String> lunarLabels  = new ArrayList<>(venitLunar.keySet());
        List<Double> lunarValori  = new ArrayList<>(venitLunar.values());
        model.addAttribute("lunarLabels", lunarLabels);
        model.addAttribute("lunarValori", lunarValori);

        return "analiza-marketing";
    }

    @GetMapping("/cod-promo/verifica")
    @ResponseBody
    public Map<String, Object> verificaCodPromo(@RequestParam(defaultValue = "") String cod) {
        if (cod == null || cod.isBlank()) {
            Map<String, Object> rezGol = new java.util.HashMap<>();
            rezGol.put("gasit", false);
            rezGol.put("gol", true);
            return rezGol;
        }
        Optional<CampanieMktg> campanieOpt = marketingService.gasesteDupaCodPromo(cod);
        Map<String, Object> rezultat = new java.util.HashMap<>();
        rezultat.put("gol", false);
        if (campanieOpt.isEmpty()) {
            rezultat.put("gasit", false);
            return rezultat;
        }
        CampanieMktg c = campanieOpt.get();
        rezultat.put("gasit", true);
        rezultat.put("numeCampanie", c.getNume());
        rezultat.put("areReducere", c.getAreReducere() != null && c.getAreReducere());
        if (c.getAreReducere() != null && c.getAreReducere()) {
            rezultat.put("tipReducere", c.getTipReducere() != null ? c.getTipReducere().name() : null);
            rezultat.put("unitateReducere", c.getTipReducere() != null ? c.getTipReducere().unitate : "");
            rezultat.put("valoareReducere", c.getValoareReducere());
            rezultat.put("aplicabilitate", c.getAplicabilitate() != null ? c.getAplicabilitate().name() : "TOATE_PRODUSELE");
            rezultat.put("produseEligibile", c.getProduseEligibile());
        }
        return rezultat;
    }

    @PostMapping("/campanie/adauga")
    public String adauga(
            @RequestParam String nume,
            @RequestParam CampanieMktg.Platforma platforma,
            @RequestParam(required = false, defaultValue = "0") Double bugetCheltuit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataStop,
            @RequestParam(required = false) String codPromo,
            @RequestParam(required = false) String note,
            @RequestParam(required = false, defaultValue = "false") Boolean areReducere,
            @RequestParam(required = false) CampanieMktg.TipReducere tipReducere,
            @RequestParam(required = false) Double valoareReducere,
            @RequestParam(required = false) CampanieMktg.AplicabilitateReducere aplicabilitate,
            @RequestParam(required = false) List<String> produseEligibile) {

        CampanieMktg c = new CampanieMktg();
        c.setNume(nume);
        c.setPlatforma(platforma);
        c.setBugetCheltuit(bugetCheltuit);
        c.setDataStart(dataStart);
        c.setDataStop(dataStop);
        c.setCodPromo(codPromo != null && !codPromo.isBlank() ? codPromo : null);
        c.setNote(note);
        populeazaReducere(c, areReducere, tipReducere, valoareReducere, aplicabilitate, produseEligibile);
        marketingService.salveazaCampanie(c);
        return "redirect:/analiza-marketing";
    }

    @GetMapping("/campanie/edit/{id}")
    @ResponseBody
    public Map<String, Object> editareDate(@PathVariable Long id) {
        CampanieMktg c = marketingService.gasesteDupaId(id);
        Map<String, Object> rez = new java.util.HashMap<>();
        rez.put("id", c.getId());
        rez.put("nume", c.getNume());
        rez.put("platforma", c.getPlatforma() != null ? c.getPlatforma().name() : null);
        rez.put("bugetCheltuit", c.getBugetCheltuit());
        rez.put("dataStart", c.getDataStart());
        rez.put("dataStop", c.getDataStop());
        rez.put("codPromo", c.getCodPromo());
        rez.put("note", c.getNote());
        rez.put("areReducere", c.getAreReducere());
        rez.put("tipReducere", c.getTipReducere() != null ? c.getTipReducere().name() : null);
        rez.put("valoareReducere", c.getValoareReducere());
        rez.put("aplicabilitate", c.getAplicabilitate() != null ? c.getAplicabilitate().name() : "TOATE_PRODUSELE");
        rez.put("produseEligibile", c.getProduseEligibile());
        return rez;
    }

    @PostMapping("/campanie/editeaza/{id}")
    public String editeaza(
            @PathVariable Long id,
            @RequestParam String nume,
            @RequestParam CampanieMktg.Platforma platforma,
            @RequestParam(required = false, defaultValue = "0") Double bugetCheltuit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataStop,
            @RequestParam(required = false) String codPromo,
            @RequestParam(required = false) String note,
            @RequestParam(required = false, defaultValue = "false") Boolean areReducere,
            @RequestParam(required = false) CampanieMktg.TipReducere tipReducere,
            @RequestParam(required = false) Double valoareReducere,
            @RequestParam(required = false) CampanieMktg.AplicabilitateReducere aplicabilitate,
            @RequestParam(required = false) List<String> produseEligibile) {

        CampanieMktg c = marketingService.gasesteDupaId(id);
        c.setNume(nume);
        c.setPlatforma(platforma);
        c.setBugetCheltuit(bugetCheltuit);
        c.setDataStart(dataStart);
        c.setDataStop(dataStop);
        c.setCodPromo(codPromo != null && !codPromo.isBlank() ? codPromo : null);
        c.setNote(note);
        populeazaReducere(c, areReducere, tipReducere, valoareReducere, aplicabilitate, produseEligibile);
        marketingService.salveazaCampanie(c);
        return "redirect:/analiza-marketing";
    }

    /** Populeaza / reseteaza campurile de reducere ale unei campanii pe baza datelor din formular. */
    private void populeazaReducere(
            CampanieMktg c, Boolean areReducere, CampanieMktg.TipReducere tipReducere,
            Double valoareReducere, CampanieMktg.AplicabilitateReducere aplicabilitate,
            List<String> produseEligibile) {

        boolean activa = areReducere != null && areReducere;
        c.setAreReducere(activa);
        if (!activa) {
            c.setTipReducere(null);
            c.setValoareReducere(null);
            c.setAplicabilitate(CampanieMktg.AplicabilitateReducere.TOATE_PRODUSELE);
            c.setProduseEligibile(new ArrayList<>());
            return;
        }
        c.setTipReducere(tipReducere != null ? tipReducere : CampanieMktg.TipReducere.PROCENT);
        c.setValoareReducere(valoareReducere);
        CampanieMktg.AplicabilitateReducere ap = aplicabilitate != null
                ? aplicabilitate : CampanieMktg.AplicabilitateReducere.TOATE_PRODUSELE;
        c.setAplicabilitate(ap);
        if (ap == CampanieMktg.AplicabilitateReducere.PRODUSE_SELECTATE) {
            c.setProduseEligibile(produseEligibile != null ? produseEligibile : new ArrayList<>());
        } else {
            c.setProduseEligibile(new ArrayList<>());
        }
    }

    @PostMapping("/campanie/sterge/{id}")
    public String sterge(@PathVariable Long id) {
        marketingService.stergeCampanie(id);
        return "redirect:/analiza-marketing";
    }

    @PostMapping("/campanie/status/{id}")
    public String actualizeazaStatus(@PathVariable Long id,
                                     @RequestParam CampanieMktg.Status status) {
        CampanieMktg c = marketingService.gasesteDupaId(id);
        c.setStatus(status);
        marketingService.salveazaCampanie(c);
        return "redirect:/analiza-marketing";
    }
}