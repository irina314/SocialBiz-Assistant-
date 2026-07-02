package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Produs;
import com.example.Project.Teza.service.FurnizorService;
import com.example.Project.Teza.service.ProdusService;
import com.example.Project.Teza.exception.AplicatieException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/produse")
@RequiredArgsConstructor
public class ProdusController {

    private final ProdusService produsService;
    private final FurnizorService furnizorService;

    @GetMapping
    public String lista(Model model) {
        List<Produs> produse = produsService.toate();
        model.addAttribute("produse",       produse);
        model.addAttribute("categorii",     Produs.Categorie.values());
        model.addAttribute("unitatiMasura", Produs.UnitateMasura.values());
        model.addAttribute("furnizori",     furnizorService.toti());
        model.addAttribute("nrAlerte",      produsService.numarProduseSubStocMinim());
        model.addAttribute("totalProduse",  produse.size());
        model.addAttribute("produseAlerta", produsService.produseSubStocMinim());
        return "produse";
    }

    // Autocomplete - returneaza JSON dupa minim 3 caractere
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam(defaultValue = "") String q) {

        if (q.trim().length() < 3) {
            return ResponseEntity.ok(List.of());
        }
        String query = q.trim().toLowerCase();
        List<Map<String, Object>> rezultate = produsService.toate().stream()
                .filter(p -> (p.getNume() != null && p.getNume().toLowerCase().contains(query))
                        || (p.getCodSKU() != null && p.getCodSKU().toLowerCase().contains(query)))
                .limit(10)
                .map(p -> Map.<String, Object>of(
                        "id",     p.getId(),
                        "nume",   p.getNume() != null ? p.getNume() : "",
                        "codSKU", p.getCodSKU() != null ? p.getCodSKU() : "",
                        "stoc",   p.getStoc() != null ? p.getStoc() : 0,
                        "pret",   p.getPret() != null ? p.getPret() : 0.0
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(rezultate);
    }

    /**
     * Adauga produs nou prin form submit standard.
     * C1: Stocul initial este intotdeauna 0 - se formeaza exclusiv prin receptii.
     * C3: Verifica unicitatea SKU per utilizator inainte de salvare.
     */
    @PostMapping("/adauga")
    public String adauga(@ModelAttribute Produs produs, RedirectAttributes ra) {
        // C3 - validare unicitate SKU
        String sku = produs.getCodSKU();
        if (sku != null && !sku.isBlank() && produsService.existaSkuPentruUtilizator(sku)) {
            ra.addFlashAttribute("errorSku",
                    "Codul SKU \"" + sku + "\" este deja folosit de alt produs. Alege un cod unic.");
            ra.addFlashAttribute("produsForm", produs);
            return "redirect:/produse";
        }
        // C1 - stocul initial = 0, indiferent de ce a trimis formularul
        produs.setStoc(0);
        produsService.salveaza(produs);
        ra.addFlashAttribute("successProdus", "Produsul a fost adaugat. Stocul va fi actualizat la prima receptie.");
        return "redirect:/produse";
    }

    /**
     * Adauga produs rapid din modal receptie - returneaza JSON cu produsul salvat.
     * C1: Ignora campul stoc din request - stocul initial este intotdeauna 0.
     * C3: Verifica unicitatea SKU si returneaza eroare JSON daca exista deja.
     */
    @PostMapping("/adauga-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adaugaAjax(
            @RequestParam String nume,
            @RequestParam String codSKU,
            @RequestParam Double pret,
            @RequestParam(defaultValue = "5") Integer stocMinim,
            @RequestParam(defaultValue = "ALTELE") String categorie) {

        try {
            // C3 - validare unicitate SKU
            if (produsService.existaSkuPentruUtilizator(codSKU)) {
                return ResponseEntity.ok(Map.of(
                        "errorSku", "Codul SKU \"" + codSKU + "\" este deja folosit. Alege un cod unic."
                ));
            }
            Produs produs = new Produs();
            produs.setNume(nume);
            produs.setCodSKU(codSKU);
            produs.setPret(pret);
            produs.setStocMinim(stocMinim);
            produs.setStoc(0); // C1: stoc initial = 0 intotdeauna
            produs.setCategorie(Produs.Categorie.valueOf(categorie));
            Produs salvat = produsService.salveaza(produs);

            return ResponseEntity.ok(Map.of(
                    "id",     salvat.getId(),
                    "nume",   salvat.getNume(),
                    "codSKU", salvat.getCodSKU(),
                    "pret",   salvat.getPret(),
                    "stoc",   salvat.getStoc()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", "Eroare: " + e.getMessage()));
        }
    }

    /**
     * C3 - endpoint AJAX pentru verificare SKU in timp real (folosit de modul standalone).
     * Returneaza {"exista": true/false, "mesaj": "..."}.
     */
    @PostMapping("/verifica-sku")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verificaSku(@RequestParam String codSKU) {
        boolean exista = produsService.existaSkuPentruUtilizator(codSKU);
        if (exista) {
            return ResponseEntity.ok(Map.of(
                    "exista", true,
                    "mesaj", "Codul SKU \"" + codSKU + "\" este deja folosit de alt produs. Alege un cod unic."
            ));
        }
        return ResponseEntity.ok(Map.of("exista", false));
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("produs",        produsService.gasesteDupaId(id));
        model.addAttribute("categorii",     Produs.Categorie.values());
        model.addAttribute("unitatiMasura", Produs.UnitateMasura.values());
        // Nu mai trimitem lista de furnizori - furnizorul nu e editabil din aceasta pagina
        return "produse-edit";
    }

    /**
     * Salveaza modificarile unui produs existent.
     * C2: Stocul si furnizorul sunt protejate - valorile din formular sunt ignorate,
     *     se pastreaza cele din baza de date.
     * C3: Verifica unicitatea SKU, cu exceptia produsului curent (SKU propriu este permis).
     */
    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id, @ModelAttribute Produs produs, RedirectAttributes ra) {
        Produs existent = produsService.gasesteDupaId(id);

        // C3 - validare unicitate SKU (permite pastrarea aceluiasi SKU)
        String skuNou = produs.getCodSKU();
        if (skuNou != null && !skuNou.isBlank()
                && !skuNou.equalsIgnoreCase(existent.getCodSKU())
                && produsService.existaSkuPentruUtilizator(skuNou)) {
            ra.addFlashAttribute("errorSku",
                    "Codul SKU \"" + skuNou + "\" este deja folosit de alt produs.");
            return "redirect:/produse/edit/" + id;
        }

        produs.setId(id);
        produs.setUtilizator(existent.getUtilizator());
        // C2: stocul si furnizorul se pastreaza din baza de date - nu sunt editabile
        produs.setStoc(existent.getStoc());
        produs.setFurnizor(existent.getFurnizor());
        produsService.salveaza(produs);
        ra.addFlashAttribute("successProdus", "Produs actualizat cu succes.");
        return "redirect:/produse";
    }

    @PostMapping("/dezactiveaza/{id}")
    public String dezactiveaza(@PathVariable Long id, RedirectAttributes ra) {
        try {
            produsService.dezactiveaza(id);
            ra.addFlashAttribute("successProdus", "Produsul a fost dezactivat. Ramane in istoric dar nu mai apare in comenzi noi.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("errorProdus", ex.getMessage());
        }
        return "redirect:/produse";
    }

    @PostMapping("/reactiveaza/{id}")
    public String reactiveaza(@PathVariable Long id, RedirectAttributes ra) {
        produsService.reactiveaza(id);
        ra.addFlashAttribute("successProdus", "Produsul a fost reactivat.");
        return "redirect:/produse";
    }

    @PostMapping("/sterge-selectate")
    public String dezactiveazaSelectate(@RequestParam(required = false) List<Long> ids,
                                        RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) return "redirect:/produse";
        int ok = 0; int skip = 0;
        for (Long id : ids) {
            try { produsService.dezactiveaza(id); ok++; }
            catch (RuntimeException e) { skip++; }        }
        String msg = ok + " produs(e) dezactivate.";
        if (skip > 0) msg += " " + skip + " produs(e) au stoc > 0 si nu au putut fi dezactivate.";
        ra.addFlashAttribute("successProdus", msg);
        return "redirect:/produse";
    }
}