package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.ComandaItem;
import com.example.Project.Teza.model.Produs;
import com.example.Project.Teza.service.CampanieMktgService;
import com.example.Project.Teza.service.ComandaService;
import com.example.Project.Teza.service.FurnizorService;
import com.example.Project.Teza.service.ImportCsvService;
import com.example.Project.Teza.service.ProdusService;
import com.example.Project.Teza.exception.AplicatieException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/comenzi")
@RequiredArgsConstructor
public class ComandaController {

    private final ComandaService       comandaService;
    private final ImportCsvService     importCsvService;
    private final ProdusService        produsService;
    private final FurnizorService      furnizorService;
    private final CampanieMktgService  campanieMktgService;

    @GetMapping
    public String lista(Model model) {
        List<Comanda> comenzi = comandaService.toate();
        model.addAttribute("comenzi",       comenzi);
        model.addAttribute("nrNoi",         comandaService.numaraDupaStatus(Comanda.StatusComanda.NOU));
        model.addAttribute("nrConfirmate",  comandaService.numaraDupaStatus(Comanda.StatusComanda.CONFIRMAT));
        model.addAttribute("nrExpediate",   comandaService.numaraDupaStatus(Comanda.StatusComanda.EXPEDIAT));
        model.addAttribute("nrLivrate",     comandaService.numaraDupaStatus(Comanda.StatusComanda.LIVRAT));
        model.addAttribute("nrFinalizate",  comandaService.numaraDupaStatus(Comanda.StatusComanda.FINALIZAT));
        model.addAttribute("nrNeplatite",   comandaService.numaraNeplatite());
        model.addAttribute("statusuri",     Comanda.StatusComanda.values());
        model.addAttribute("categorii",     Produs.Categorie.values());
        model.addAttribute("furnizori",     furnizorService.toti());
        model.addAttribute("unitatiMasura", Produs.UnitateMasura.values());
        model.addAttribute("campanii",      campanieMktgService.toateCampaniile());
        double total = 0;
        for (Comanda c : comenzi) total += c.calculeazaTotal();
        model.addAttribute("totalVanzari", String.format("%.2f", total));
        return "comenzi";
    }

    @PostMapping("/{id}/confirma-plata")
    public String confirmaPlata(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ramburs") String metoda,
            @RequestParam(required = false) Double sumaIncasata,
            RedirectAttributes ra) {
        comandaService.confirmaPlata(id, metoda, sumaIncasata);
        ra.addFlashAttribute("importSucces", "[OK] Plata confirmata cu succes!");
        return "redirect:/comenzi";
    }

    @PostMapping("/import-csv")
    public String importCsv(
            @RequestParam("file")      MultipartFile file,
            @RequestParam("platforma") String platforma,
            RedirectAttributes redirect) {
        if (file.isEmpty()) {
            redirect.addFlashAttribute("importEroare", "Niciun fisier selectat.");
            return "redirect:/comenzi";
        }
        try {
            ImportCsvService.RezultatImport r = importCsvService.importa(file, platforma);
            String mesaj = "[OK] " + r.importate() + " comenzi importate din " + platforma;
            if (r.sarite() > 0) mesaj += " (" + r.sarite() + " randuri sarite)";
            redirect.addFlashAttribute("importSucces", mesaj);
            if (!r.erori().isEmpty()) redirect.addFlashAttribute("importErori", r.erori());
        } catch (AplicatieException e) {
            redirect.addFlashAttribute("importEroare", "[!] " + e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("importEroare", "Eroare la importul fisierului CSV. Verifica formatul coloanelor si incearca din nou.");
        }
        return "redirect:/comenzi";
    }

    @PostMapping("/adauga")
    public String adauga(
            @RequestParam String numeClient, @RequestParam String telefon,
            @RequestParam String adresa,     @RequestParam String sursa,
            @RequestParam String codPostal,
            @RequestParam Comanda.StatusPlata statusPlata,
            @RequestParam(required = false) String codPromotional,
            @RequestParam(required = false) String campanie,
            @RequestParam(required = false) String note,
            @RequestParam List<String>              idProdus,
            @RequestParam List<Integer>             cantitate,
            @RequestParam List<Double>              pretUnitar,
            @RequestParam List<ComandaItem.CotaTVA> cotaTVA,
            RedirectAttributes ra) {

        if (codPostal == null || codPostal.isBlank()) {
            ra.addFlashAttribute("importEroare", "[!] Codul postal este obligatoriu - este necesar pentru generarea AWB-ului la curier.");
            return "redirect:/comenzi";
        }

        Comanda comanda = new Comanda();
        comanda.setNumeClient(numeClient); comanda.setTelefon(telefon);
        comanda.setAdresa(adresa);         comanda.setSursa(sursa);
        comanda.setCodPostal(codPostal.trim());
        comanda.setStatusPlata(statusPlata);
        comanda.setCodPromotional(codPromotional);
        comanda.setCampanie(campanie);     comanda.setNote(note);

        List<ComandaItem> items = new ArrayList<>();
        for (int i = 0; i < idProdus.size(); i++) {
            if (idProdus.get(i) != null && !idProdus.get(i).isBlank()) {
                ComandaItem item = new ComandaItem();
                item.setComanda(comanda); item.setIdProdus(idProdus.get(i));
                item.setCantitate(cantitate.get(i)); item.setPretUnitar(pretUnitar.get(i));
                item.setCotaTVA(cotaTVA.get(i));
                items.add(item);
            }
        }
        comanda.setItems(items);
        comandaService.salveaza(comanda);
        return "redirect:/comenzi";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        Comanda comanda = comandaService.gasesteDupaId(id);
        model.addAttribute("comanda", comanda);
        model.addAttribute("statusuriDisponibile", comanda.getStatus().paginaUrmatoare());
        model.addAttribute("campanii", campanieMktgService.toateCampaniile());
        return "comenzi-edit";
    }

    @PostMapping("/edit/{id}")
    public String edit(
            @PathVariable Long id,
            @RequestParam String numeClient, @RequestParam String telefon,
            @RequestParam String adresa,     @RequestParam String sursa,
            @RequestParam(required = false) String codPostal,
            @RequestParam Comanda.StatusPlata  statusPlata,
            @RequestParam(required = false) String codPromotional,
            @RequestParam(required = false) String campanie,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) List<String>              idProdus,
            @RequestParam(required = false) List<Integer>             cantitate,
            @RequestParam(required = false) List<Double>              pretUnitar,
            @RequestParam(required = false) List<ComandaItem.CotaTVA> cotaTVA) {

        Comanda comanda = comandaService.gasesteDupaId(id);
        if (comanda.getStatus() == Comanda.StatusComanda.FINALIZAT
                || comanda.getStatus() == Comanda.StatusComanda.ANULAT) {
            return "redirect:/comenzi";
        }
        comanda.setNumeClient(numeClient); comanda.setTelefon(telefon);
        comanda.setAdresa(adresa);         comanda.setSursa(sursa);
        if (codPostal != null && !codPostal.isBlank()) comanda.setCodPostal(codPostal.trim());
        comanda.setStatusPlata(statusPlata);
        comanda.setCodPromotional(codPromotional);
        comanda.setCampanie(campanie);     comanda.setNote(note);

        comanda.getItems().clear();
        if (idProdus != null) {
            for (int i = 0; i < idProdus.size(); i++) {
                if (idProdus.get(i) != null && !idProdus.get(i).isBlank()) {
                    ComandaItem item = new ComandaItem();
                    item.setComanda(comanda); item.setIdProdus(idProdus.get(i));
                    item.setCantitate(cantitate != null && i < cantitate.size() ? cantitate.get(i) : 1);
                    item.setPretUnitar(pretUnitar != null && i < pretUnitar.size() ? pretUnitar.get(i) : 0.0);
                    item.setCotaTVA(cotaTVA != null && i < cotaTVA.size() ? cotaTVA.get(i) : ComandaItem.CotaTVA.TVA_21);
                    comanda.getItems().add(item);
                }
            }
        }
        comandaService.salveaza(comanda);
        return "redirect:/comenzi";
    }

    @PostMapping("/status/{id}")
    public String actualizeazaStatus(
            @PathVariable Long id,
            @RequestParam Comanda.StatusComanda status,
            RedirectAttributes ra) {
        try {
            comandaService.actualizeazaStatus(id, status);
        } catch (AplicatieException e) {
            ra.addFlashAttribute("importEroare", "[!] " + e.getMessage());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("importEroare", "Schimbarea statusului a esuat. Incearca din nou.");
        }
        return "redirect:/comenzi";
    }

    @PostMapping("/sterge/{id}")
    public String sterge(@PathVariable Long id) {
        comandaService.sterge(id);
        return "redirect:/comenzi";
    }

    @PostMapping("/sterge-selectate")
    public String stergeSelectate(@RequestParam(required = false) List<Long> ids) {
        if (ids != null) ids.forEach(comandaService::sterge);
        return "redirect:/comenzi";
    }
}