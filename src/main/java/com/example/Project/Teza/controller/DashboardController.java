package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.Produs;
import com.example.Project.Teza.model.Utilizator;
import com.example.Project.Teza.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ComandaService      comandaService;
    private final ProdusService       produsService;
    private final ClientService       clientService;
    private final UtilizatorService   utilizatorService;
    private final MarketingService    marketingService;
    private final FacturaFurnizorService facturaFurnizorService;
    private final ReceptieService     receptieService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Comanda> toateComenzi = comandaService.toate();
        LocalDateTime acum = LocalDateTime.now();
        LocalDateTime luna = acum.withDayOfMonth(1).toLocalDate().atStartOfDay();

        // -- Cifre comenzi ----------------------------------------------------
        long comenziNoi = toateComenzi.stream()
                .filter(c -> c.getStatus() == Comanda.StatusComanda.NOU).count();
        long inProcesare = toateComenzi.stream()
                .filter(c -> c.getStatus() == Comanda.StatusComanda.CONFIRMAT
                        || c.getStatus() == Comanda.StatusComanda.EXPEDIAT).count();
        double deIncasat = toateComenzi.stream()
                .filter(c -> c.getStatus() == Comanda.StatusComanda.NOU
                        || c.getStatus() == Comanda.StatusComanda.CONFIRMAT
                        || c.getStatus() == Comanda.StatusComanda.EXPEDIAT)
                .mapToDouble(Comanda::calculeazaTotal).sum();
        double venitLuna = toateComenzi.stream()
                .filter(c -> c.getData() != null && c.getData().isAfter(luna))
                .filter(c -> c.getStatus() != Comanda.StatusComanda.ANULAT)
                .mapToDouble(Comanda::calculeazaTotal).sum();
        long comenziLuna = toateComenzi.stream()
                .filter(c -> c.getData() != null && c.getData().isAfter(luna)).count();

        // -- Al 4-lea cadran - datorii furnizori ------------------------------
        double totalDatorii = facturaFurnizorService.totalDatorii();
        long nrFacturiScadente = facturaFurnizorService.numarFacturiScadente();
        long nrAvizeFaraFactura = receptieService.numarAvizeFaraFactura();

        // Facturi care scad in 3 zile si in 7 zile
        var scadente3zile = facturaFurnizorService.scadenteInCurand(3);
        var scadente7zile = facturaFurnizorService.scadenteInCurand(7);
        // Cele care scad intre 4-7 zile (nu sunt deja in 3 zile)
        var scadente4_7zile = scadente7zile.stream()
                .filter(f -> FacturaFurnizorService.zilePanaLaScadenta(f) > 3)
                .toList();

        // -- Alerte -----------------------------------------------------------
        List<Map<String, String>> alerte = new ArrayList<>();

        if (comenziNoi > 0) {
            alerte.add(Map.of(
                    "tip",     "urgent",
                    "icon",    "🔔",
                    "titlu",   comenziNoi + (comenziNoi == 1 ? " comanda noua" : " comenzi noi") + " nepreluate",
                    "actiune", "Proceseaza comenzile",
                    "link",    "/comenzi"
            ));
        }

        List<Produs> subStoc = produsService.produseSubStocMinim();
        if (!subStoc.isEmpty()) {
            String produse = subStoc.stream().map(Produs::getNume)
                    .limit(2).collect(Collectors.joining(", "))
                    + (subStoc.size() > 2 ? " +" + (subStoc.size()-2) + " altele" : "");
            alerte.add(Map.of(
                    "tip",     "atentie",
                    "icon",    "📦",
                    "titlu",   "Stoc critic: " + produse,
                    "actiune", "Verifica stocul",
                    "link",    "/produse"
            ));
        }

        // Alerta receptii fara factura
        if (nrAvizeFaraFactura > 0) {
            alerte.add(Map.of(
                    "tip",     "atentie",
                    "icon",    "🧾",
                    "titlu",   nrAvizeFaraFactura + (nrAvizeFaraFactura == 1
                            ? " receptie pe aviz fara factura inregistrata"
                            : " receptii pe aviz fara factura inregistrata"),
                    "actiune", "Vezi furnizori",
                    "link",    "/furnizori"
            ));
        }

        // Alerta facturi scadente (deja expirate)
        if (nrFacturiScadente > 0) {
            alerte.add(Map.of(
                    "tip",     "urgent",
                    "icon",    "🔴",
                    "titlu",   nrFacturiScadente + (nrFacturiScadente == 1
                            ? " factura furnizor scadenta neplatita"
                            : " facturi furnizori scadente neplatite"),
                    "actiune", "Plateste acum",
                    "link",    "/furnizori"
            ));
        }

        // Alerta preventiva - scadente in 3 zile
        if (!scadente3zile.isEmpty()) {
            long nr = scadente3zile.size();
            double totalSc3 = scadente3zile.stream()
                    .mapToDouble(f -> f.getRest()).sum();
            alerte.add(Map.of(
                    "tip",     "atentie",
                    "icon",    "⚠️",
                    "titlu",   nr + (nr == 1 ? " factura scade" : " facturi scad")
                            + " in 3 zile - " + String.format("%.2f", totalSc3) + " RON de platit",
                    "actiune", "Programeaza plata",
                    "link",    "/furnizori"
            ));
        }

        // Alerta informativa - scadente in 4-7 zile
        if (!scadente4_7zile.isEmpty()) {
            long nr = scadente4_7zile.size();
            double totalSc7 = scadente4_7zile.stream()
                    .mapToDouble(f -> f.getRest()).sum();
            alerte.add(Map.of(
                    "tip",     "info",
                    "icon",    "📅",
                    "titlu",   nr + (nr == 1 ? " factura scade" : " facturi scad")
                            + " in 7 zile - " + String.format("%.2f", totalSc7) + " RON",
                    "actiune", "Vezi furnizori",
                    "link",    "/furnizori"
            ));
        }

        double roas = marketingService.roas();
        if (roas > 0 && roas < 1.0) {
            alerte.add(Map.of(
                    "tip",     "atentie",
                    "icon",    "📉",
                    "titlu",   "ROAS " + String.format("%.1f", roas) + "x - cheltuiesti mai mult pe reclame decat castigi",
                    "actiune", "Vezi campanii",
                    "link",    "/analiza-marketing"
            ));
        }

        var totiClientii = clientService.toti();
        long inactivi = clientService.numarInactivi(totiClientii);
        if (inactivi > 3) {
            alerte.add(Map.of(
                    "tip",     "info",
                    "icon",    "👥",
                    "titlu",   inactivi + " clienti inactivi 20+ zile fara comanda noua",
                    "actiune", "Vezi segmentare RFM",
                    "link",    "/clienti?view=rfm"
            ));
        }

        // -- Grafic 7 zile ----------------------------------------------------
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<String> zile7Label  = new ArrayList<>();
        List<Double> zile7Valori = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime start = acum.toLocalDate().minusDays(i).atStartOfDay();
            LocalDateTime end   = start.plusDays(1);
            double val = toateComenzi.stream()
                    .filter(c -> c.getData() != null
                            && c.getData().isAfter(start) && c.getData().isBefore(end)
                            && c.getStatus() != Comanda.StatusComanda.ANULAT)
                    .mapToDouble(Comanda::calculeazaTotal).sum();
            zile7Label.add(start.format(fmt));
            zile7Valori.add(Math.round(val * 100.0) / 100.0);
        }

        // -- Comenzi recente ---------------------------------------------------
        List<Comanda> recente = toateComenzi.stream()
                .filter(c -> c.getData() != null)
                .sorted(Comparator.comparing(Comanda::getData).reversed())
                .limit(6).collect(Collectors.toList());

        // -- Stare generala ----------------------------------------------------
        String stareGenerala;
        String stareClasa;
        if (comenziNoi > 5 || !subStoc.isEmpty() || nrFacturiScadente > 0) {
            stareGenerala = "Necesita atentie";
            stareClasa    = "atentie";
        } else if (venitLuna > 0 && roas >= 1.0) {
            stareGenerala = "Totul merge bine";
            stareClasa    = "bine";
        } else {
            stareGenerala = "Activitate redusa";
            stareClasa    = "redus";
        }

        Utilizator u = utilizatorService.getUtilizatorCurent();
        String numeAfisat = (u.getNumeAfacere() != null && !u.getNumeAfacere().isBlank())
                ? u.getNumeAfacere() : u.getEmail();
        int ora = acum.getHour();
        String salut = ora < 12 ? "Buna dimineata" : ora < 18 ? "Buna ziua" : "Buna seara";

        model.addAttribute("salut",             salut);
        model.addAttribute("numeUtilizator",    numeAfisat);
        model.addAttribute("stareGenerala",     stareGenerala);
        model.addAttribute("stareClasa",        stareClasa);
        model.addAttribute("alerte",            alerte);
        model.addAttribute("comenziNoi",        comenziNoi);
        model.addAttribute("inProcesare",       inProcesare);
        model.addAttribute("deIncasat",         String.format("%.2f", deIncasat));
        model.addAttribute("venitLuna",         String.format("%.2f", venitLuna));
        model.addAttribute("comenziLuna",       comenziLuna);
        model.addAttribute("nrAlerte",          subStoc.size());
        model.addAttribute("totalDatorii",       String.format("%.2f", totalDatorii));
        model.addAttribute("nrFacturiScadente",  nrFacturiScadente);
        model.addAttribute("nrAvizeFaraFactura", nrAvizeFaraFactura);
        model.addAttribute("nrScadente3zile",    scadente3zile.size());
        model.addAttribute("nrScadente7zile",    scadente7zile.size());
        model.addAttribute("scadente3zile",      scadente3zile);
        model.addAttribute("scadente7zile",      scadente7zile);
        model.addAttribute("zile7Label",        zile7Label);
        model.addAttribute("zile7Valori",       zile7Valori);
        model.addAttribute("recente",           recente);

        return "dashboard";
    }
}