package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.FacturaFurnizor;
import com.example.Project.Teza.model.Furnizor;
import com.example.Project.Teza.model.Receptie;
import com.example.Project.Teza.service.*;
import com.example.Project.Teza.exception.AplicatieException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/furnizori")
@RequiredArgsConstructor
public class FurnizorController {

    private final FurnizorService furnizorService;
    private final ProdusService produsService;
    private final FacturaFurnizorService facturaService;
    private final ReceptieService receptieService;

    @GetMapping
    public String lista(Model model) {
        List<Furnizor> toti   = furnizorService.toti();
        List<Furnizor> activi = furnizorService.dupaStatus(Furnizor.Status.ACTIV);
        model.addAttribute("furnizori",        toti);
        model.addAttribute("categorii",        Furnizor.Categorie.values());
        model.addAttribute("totalFurnizori",   toti.size());
        model.addAttribute("furnizoriActivi",  activi.size());
        model.addAttribute("totalDatorii",     String.format("%.2f", facturaService.totalDatorii()));
        model.addAttribute("nrScadente",       facturaService.numarFacturiScadente());
        model.addAttribute("nrFaraFactura",    receptieService.numarAvizeFaraFactura());
        return "furnizori";
    }

    @GetMapping("/{id}/detalii")
    public String detalii(@PathVariable Long id, Model model) {
        Furnizor furnizor = furnizorService.gasesteDupaId(id);
        List<Receptie> receptii = receptieService.pentruFurnizor(id);
        long avizeFaraFactura = receptii.stream()
                .filter(r -> r.getTipDocument() == Receptie.TipDocument.AVIZ
                        && r.getStatusFacturare() == Receptie.StatusFacturare.NEFACTURATA)
                .count();
        model.addAttribute("furnizor",         furnizor);
        model.addAttribute("produse",          produsService.dupaFurnizor(id));
        model.addAttribute("facturi",          facturaService.pentruFurnizor(id));
        model.addAttribute("receptii",         receptii);
        model.addAttribute("datorie",          String.format("%.2f", facturaService.totalDatoriiCatreFurnizor(id)));
        model.addAttribute("avizeFaraFactura", avizeFaraFactura);
        model.addAttribute("toateProdusele",   produsService.toate());
        return "furnizor-detalii";
    }

    @PostMapping("/{furnizorId}/receptie")
    public String receptie(
            @PathVariable Long furnizorId,
            @RequestParam String tipDocument,
            @RequestParam String numarDocument,
            @RequestParam(required = false) String dataScadenta,
            @RequestParam(required = false) String note,
            @RequestParam List<Long>    produsId,
            @RequestParam List<Integer> cantitate,
            @RequestParam List<Double>  pretAchizitie,
            RedirectAttributes ra) {

        Receptie r = receptieService.salveazaReceptie(
                furnizorId, tipDocument, numarDocument,
                dataScadenta, note, produsId, cantitate, pretAchizitie);

        String msg = "Receptie inregistrata! Stocurile au fost actualizate.";
        if (r.getTipDocument() == Receptie.TipDocument.AVIZ) {
            msg += " Nu uita sa inregistrezi factura cand o primesti.";
        } else {
            msg += " Datoria catre furnizor a fost creata automat.";
        }
        ra.addFlashAttribute("success", msg);
        return "redirect:/furnizori/" + furnizorId + "/detalii";
    }

    @PostMapping("/receptie-rapida")
    public String receptieRapida(
            @RequestParam Long furnizorId,
            @RequestParam String tipDocument,
            @RequestParam String numarDocument,
            @RequestParam List<Long>    produsId,
            @RequestParam List<Integer> cantitate,
            @RequestParam List<Double>  pretAchizitie,
            RedirectAttributes ra) {

        Receptie r = receptieService.salveazaReceptie(
                furnizorId, tipDocument, numarDocument,
                null, null, produsId, cantitate, pretAchizitie);

        String msg = "Stocuri actualizate!";
        if (r.getTipDocument() == Receptie.TipDocument.AVIZ) {
            msg += " Nu uita sa inregistrezi factura la furnizori cand o primesti.";
        } else {
            msg += " Datoria catre furnizor a fost creata. Vezi detalii in modulul Furnizori.";
        }
        ra.addFlashAttribute("success", msg);
        return "redirect:/produse";
    }

    @PostMapping("/receptie/{receptieId}/ataseaza-factura")
    public String ataseazaFactura(
            @PathVariable Long receptieId,
            @RequestParam String numarFactura,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFactura,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataScadenta,
            RedirectAttributes ra) {

        // Citim furnizorId INAINTE de a apela serviciul, direct din repository
        // pentru a evita LazyInitializationException pe getFurnizor() dupa commit
        Long furnizorId;
        try {
            furnizorId = receptieService.getFurnizorId(receptieId);
        } catch (AplicatieException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/furnizori";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", "Receptia nu a putut fi gasita.");
            return "redirect:/furnizori";
        }

        try {
            receptieService.ataseazaFactura(receptieId, numarFactura, dataFactura, dataScadenta);
            ra.addFlashAttribute("success",
                    "Factura " + numarFactura + " inregistrata! Datoria catre furnizor a fost creata.");
        } catch (AplicatieException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", "Eroare la inregistrarea facturii.");
        }
        return "redirect:/furnizori/" + furnizorId + "/detalii";
    }

    @PostMapping("/{furnizorId}/facturi/adauga")
    public String adaugaFactura(
            @PathVariable Long furnizorId,
            @ModelAttribute FacturaFurnizor factura,
            RedirectAttributes ra) {
        factura.setFurnizor(furnizorService.gasesteDupaId(furnizorId));
        facturaService.salveaza(factura);
        ra.addFlashAttribute("success", "Factura inregistrata!");
        return "redirect:/furnizori/" + furnizorId + "/detalii";
    }

    @PostMapping("/facturi/{facturaId}/plateste")
    public String plateste(
            @PathVariable Long facturaId,
            @RequestParam double suma,
            @RequestParam(required = false) String tipDocumentPlata,
            @RequestParam(required = false) String numarDocumentPlata,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataPlata,
            RedirectAttributes ra) {

        if (suma <= 0) {
            ra.addFlashAttribute("error", "Suma platita trebuie sa fie mai mare decat 0.");
            return "redirect:/furnizori";
        }

        // Citim furnizorId INAINTE de confirmaPlata() pentru a evita
        // LazyInitializationException pe factura.getFurnizor().getId() dupa commit
        Long furnizorId;
        try {
            furnizorId = facturaService.getFurnizorId(facturaId);
        } catch (AplicatieException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/furnizori";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", "Factura nu a fost gasita.");
            return "redirect:/furnizori";
        }

        try {
            facturaService.confirmaPlata(
                    facturaId, suma, tipDocumentPlata, numarDocumentPlata, dataPlata);
            ra.addFlashAttribute("success",
                    String.format("Plata de %.2f RON confirmata! Document: %s nr. %s",
                            suma, tipDocumentPlata, numarDocumentPlata));
        } catch (AplicatieException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", "Eroare la confirmarea platii.");
        }
        return "redirect:/furnizori/" + furnizorId + "/detalii";
    }

    /**
     * Stornare factura - logica ERP corecta.
     * Nu se sterge fizic. Se anuleaza datoria prin metoda storneaza() din service.
     * Disponibil doar pentru facturi cu status NEPLATITA.
     */
    @PostMapping("/facturi/{facturaId}/storno")
    public String stornoFactura(@PathVariable Long facturaId, RedirectAttributes ra) {
        Long furnizorId;
        try {
            furnizorId = facturaService.getFurnizorId(facturaId);
        } catch (AplicatieException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/furnizori";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", "Factura nu a fost gasita.");
            return "redirect:/furnizori";
        }
        try {
            facturaService.storneaza(facturaId);
            ra.addFlashAttribute("success", "Factura a fost stornata. Datoria aferenta a fost anulata.");
        } catch (AplicatieException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", "Eroare la stornare.");
        }
        return "redirect:/furnizori/" + furnizorId + "/detalii";
    }

    @PostMapping("/facturi/{facturaId}/sterge")
    public String stergeFactura(@PathVariable Long facturaId, RedirectAttributes ra) {
        FacturaFurnizor factura = facturaService.gasesteDupaId(facturaId);
        Long furnizorId = facturaService.getFurnizorId(facturaId);
        facturaService.sterge(facturaId);
        ra.addFlashAttribute("success", "Factura stearsa.");
        return "redirect:/furnizori/" + furnizorId + "/detalii";
    }


    // --- Adauga furnizor rapid (AJAX din modal produse/receptie) -------------

    @PostMapping("/adauga-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adaugaAjax(
            @RequestParam String nume,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) String persoanaContact,
            @RequestParam(required = false) String telefon,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String adresa,
            @RequestParam(required = false) String cui,
            @RequestParam(required = false) String nrRegCom,
            @RequestParam(required = false) String banca,
            @RequestParam(required = false) String contBancar,
            @RequestParam(required = false) String note) {

        try {
            Furnizor furnizor = new Furnizor();
            furnizor.setNume(nume);
            if (categorie != null && !categorie.isBlank()) {
                furnizor.setCategorie(Furnizor.Categorie.valueOf(categorie));
            }
            furnizor.setPersoanaContact(persoanaContact);
            furnizor.setTelefon(telefon);
            furnizor.setEmail(email);
            furnizor.setAdresa(adresa);
            furnizor.setCui(cui);
            furnizor.setNrRegCom(nrRegCom);
            furnizor.setBanca(banca);
            furnizor.setContBancar(contBancar);
            furnizor.setNote(note);
            Furnizor salvat = furnizorService.salveaza(furnizor);

            return ResponseEntity.ok(Map.of(
                    "id",   salvat.getId(),
                    "nume", salvat.getNume()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", "Eroare: " + e.getMessage()));
        }
    }

    @PostMapping("/adauga")
    public String adauga(@ModelAttribute Furnizor furnizor) {
        furnizorService.salveaza(furnizor);
        return "redirect:/furnizori";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("furnizor",  furnizorService.gasesteDupaId(id));
        model.addAttribute("categorii", Furnizor.Categorie.values());
        return "furnizori-edit";
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id,
                       @ModelAttribute Furnizor furnizor,
                       @RequestParam(name = "statusHidden", defaultValue = "INACTIV") String statusHidden) {
        Furnizor existent = furnizorService.gasesteDupaId(id);
        furnizor.setId(id);
        furnizor.setUtilizator(existent.getUtilizator());
        furnizor.setDataAdaugare(existent.getDataAdaugare());
        furnizor.setStatus(Furnizor.Status.valueOf(statusHidden));
        furnizorService.salveaza(furnizor);
        return "redirect:/furnizori";
    }

    @PostMapping("/sterge/{id}")
    public String sterge(@PathVariable Long id) {
        furnizorService.sterge(id);
        return "redirect:/furnizori";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Long id) {
        Furnizor f = furnizorService.gasesteDupaId(id);
        f.setStatus(f.getStatus() == Furnizor.Status.ACTIV
                ? Furnizor.Status.INACTIV : Furnizor.Status.ACTIV);
        furnizorService.salveaza(f);
        return "redirect:/furnizori";
    }
}