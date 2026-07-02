package com.example.Project.Teza.service;

import com.example.Project.Teza.model.CampanieMktg;
import com.example.Project.Teza.model.Comanda;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketingService {

    private final CampanieMktgService campanieMktgService;
    private final ComandaService comandaService;
    private final InfluencerService influencerService;
    private final ReturService returService;

    // --- Campanii (delegat catre CampanieMktgService) -----------------

    public List<CampanieMktg> toateCampaniile() {
        return campanieMktgService.toateCampaniile();
    }

    public CampanieMktg salveazaCampanie(CampanieMktg campanie) {
        return campanieMktgService.salveazaCampanie(campanie);
    }

    public CampanieMktg gasesteDupaId(Long id) {
        return campanieMktgService.gasesteDupaId(id);
    }

    public void stergeCampanie(Long id) {
        campanieMktgService.stergeCampanie(id);
    }

    /**
     * Gaseste campania a carei cod promo corespunde (case-insensitive, fara spatii)
     * codului dat. Returneaza empty daca nu exista nicio campanie cu acel cod
     * sau daca codul e null/gol.
     */
    public Optional<CampanieMktg> gasesteDupaCodPromo(String codPromo) {
        return campanieMktgService.gasesteDupaCodPromo(codPromo);
    }

    public long numarCampaniiActive() {
        return campanieMktgService.numarCampaniiActive();
    }

    // --- Analytics calculate din comenzi -----------------------------

    public double venitTotal() {
        return comenziFinalizate().stream().mapToDouble(Comanda::calculeazaTotal).sum();
    }

    public double bugetTotalCheltuit() {
        return toateCampaniile().stream()
                .mapToDouble(c -> c.getBugetCheltuit() != null ? c.getBugetCheltuit() : 0.0)
                .sum();
    }

    /**
     * Codurile promo configurate pe campaniile de marketing (set, pentru comparare rapida).
     * Doar comenzile care folosesc unul dintre aceste coduri sunt considerate
     * "atribuite" unei campanii si intra in calculul ROAS.
     */
    private Set<String> coduriPromoCampanii() {
        return toateCampaniile().stream()
                .map(CampanieMktg::getCodPromo)
                .filter(cod -> cod != null && !cod.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Venit generat strict de comenzile atribuite unei campanii de marketing
     * (codul promotional al comenzii corespunde codului promo al unei campanii existente).
     * Spre diferenta de venitTotal(), aici NU se includ comenzile fara cod promo
     * sau cu un cod care nu apartine niciunei campanii.
     */
    public double venitAtribuitCampaniilor() {
        Set<String> coduri = coduriPromoCampanii();
        if (coduri.isEmpty()) return 0.0;
        return comenziFinalizate().stream()
                .filter(c -> c.getCodPromotional() != null && coduri.contains(c.getCodPromotional()))
                .mapToDouble(Comanda::calculeazaTotal)
                .sum();
    }

    /**
     * ROAS aparent = Venit (atribuit campaniilor) / Buget reclame.
     * Nu scade costurile operationale (influenceri, logistica etc.).
     * comenzile fara atribuire de campanie nu trebuie sa infleze artificial ROAS-ul.
     * Returneaza 0 daca buget=0.
     */
    public double roas() {
        double buget = bugetTotalCheltuit();
        if (buget == 0) return 0.0;
        return venitAtribuitCampaniilor() / buget;
    }

    /**
     * ROAS real = (Venit atribuit campaniilor - Valoare Retururi - Cost Logistic Retur) / Buget publicitar.
     * Scade valoarea retururilor aprobate si costul logistic al procesarii lor din venitul atribuit campaniilor inainte de a imparti la buget.
     * Aceasta este rentabilitatea reala a campaniilor, fara a ignora retururile.
     * Returneaza 0 daca buget=0.
     */
    public double roasReal() {
        double buget = bugetTotalCheltuit();
        if (buget == 0) return 0.0;
        double rambursari   = returService.totalRambursari();
        double costLogistic = returService.totalCostLogisticRetururi();
        double venitNet = venitAtribuitCampaniilor() - rambursari - costLogistic;
        return venitNet / buget;
    }

    /**
     * Suma totala a comisioanelor datorate influencerilor,
     * calculata pe baza % configurat per influencer si veniturle generate de codul lor promo.
     */
    public double costInfluenceriTotal() {
        return influencerService.comisionTotalDatorat();
    }

    /** Label status pentru ROAS (aparent sau real) */
    public static String roasStatusLabel(double roasValue) {
        if (roasValue >= 2.0) return "Bun";
        if (roasValue >= 1.0) return "Ok";
        return "Sub Prag";
    }

    /** Sursa cu cele mai multe comenzi finalizate */
    public String sursaPrincipala() {
        return comenziFinalizate().stream()
                .filter(c -> c.getSursa() != null && !c.getSursa().isBlank())
                .collect(Collectors.groupingBy(Comanda::getSursa, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
    }

    /** % din venit generat de sursa principala */
    public double procentSursaPrincipala() {
        String sursa = sursaPrincipala();
        if (sursa.equals("-")) return 0.0;
        double totalSursa = comenziFinalizate().stream()
                .filter(c -> sursa.equals(c.getSursa()))
                .mapToDouble(Comanda::calculeazaTotal).sum();
        double total = venitTotal();
        return total == 0 ? 0.0 : totalSursa / total * 100.0;
    }

    /** Venit grupat pe sursa -> pentru grafic */
    public Map<String, Double> venitPeSursa() {
        Map<String, Double> map = new LinkedHashMap<>();
        comenziFinalizate().stream()
                .filter(c -> c.getSursa() != null && !c.getSursa().isBlank())
                .forEach(c -> map.merge(c.getSursa(), c.calculeazaTotal(), Double::sum));
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Nr. comenzi grupat pe sursa */
    public Map<String, Long> comenziPeSursa() {
        return comenziFinalizate().stream()
                .filter(c -> c.getSursa() != null && !c.getSursa().isBlank())
                .collect(Collectors.groupingBy(Comanda::getSursa, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Top coduri promo dupa utilizari */
    public List<Map<String, Object>> topCoduriPromo() {
        Map<String, Long> utilizari = comenziFinalizate().stream()
                .filter(c -> c.getCodPromotional() != null && !c.getCodPromotional().isBlank())
                .collect(Collectors.groupingBy(Comanda::getCodPromotional, Collectors.counting()));

        Map<String, Double> venituri = comenziFinalizate().stream()
                .filter(c -> c.getCodPromotional() != null && !c.getCodPromotional().isBlank())
                .collect(Collectors.groupingBy(Comanda::getCodPromotional,
                        Collectors.summingDouble(Comanda::calculeazaTotal)));

        return utilizari.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cod", e.getKey());
                    row.put("utilizari", e.getValue());
                    row.put("venit", String.format("%.2f", venituri.getOrDefault(e.getKey(), 0.0)));
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** Venit lunar (ultimele 6 luni) pentru graficul de trend */
    public Map<String, Double> venitLunar() {
        Map<String, Double> map = new LinkedHashMap<>();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (int i = 5; i >= 0; i--) {
            java.time.LocalDateTime luna = now.minusMonths(i);
            String key = luna.getMonth().getDisplayName(
                    java.time.format.TextStyle.SHORT, new Locale("ro")) + " " + luna.getYear();
            map.put(key, 0.0);
        }
        comenziFinalizate().forEach(c -> {
            if (c.getData() == null) return;
            java.time.LocalDateTime d = c.getData();
            if (d.isAfter(now.minusMonths(6))) {
                String key = d.getMonth().getDisplayName(
                        java.time.format.TextStyle.SHORT, new Locale("ro")) + " " + d.getYear();
                map.merge(key, c.calculeazaTotal(), Double::sum);
            }
        });
        return map;
    }

    private List<Comanda> comenziFinalizate() {
        return comenziFinalizate(comandaService.toate());
    }

    private List<Comanda> comenziFinalizate(List<Comanda> toate) {
        return toate.stream()
                .filter(c -> c.getStatus() == Comanda.StatusComanda.FINALIZAT)
                .collect(Collectors.toList());
    }
}