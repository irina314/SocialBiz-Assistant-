package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.*;
import com.example.Project.Teza.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/retururi")
@RequiredArgsConstructor
public class ReturController {

    private final ReturService    returService;
    private final ComandaService  comandaService;
    private final MarketingService marketingService;

    @GetMapping
    public String lista(Model model) {
        List<Retur>  retururi = returService.toate();
        List<Comanda> comenzi = comandaService.toate();
        long totalComenzi    = comenzi.size();

        double venitBrut     = marketingService.venitAtribuitCampaniilor();
        double bugetReclame  = marketingService.bugetTotalCheltuit();
        double roasAparent   = marketingService.roas();
        double roasReal      = returService.roasReal(venitBrut, bugetReclame);
        double rambursari    = returService.totalRambursari();
        double diferentaROAS = Math.round((roasAparent - roasReal) * 10.0) / 10.0;

        // KPI
        model.addAttribute("retururi",       retururi);
        model.addAttribute("comenzi",        comenzi);
        model.addAttribute("nrInAsteptare",  returService.numaraDupaStatus(Retur.StatusRetur.IN_ASTEPTARE));
        model.addAttribute("nrAprobate",     returService.numaraDupaStatus(Retur.StatusRetur.APROBAT));
        model.addAttribute("nrRespinse",     returService.numaraDupaStatus(Retur.StatusRetur.RESPINS));
        model.addAttribute("totalRambursari",String.format("%.2f", rambursari));
        model.addAttribute("rataRetururi",   String.format("%.1f", returService.rataRetururi(totalComenzi)));

        // ROAS real vs aparent
        model.addAttribute("roasAparent",    String.format("%.1f", roasAparent));
        model.addAttribute("roasReal",       String.format("%.1f", roasReal));
        model.addAttribute("diferentaROAS",  String.format("%.1f", Math.abs(diferentaROAS)));
        model.addAttribute("roasDistorsionat", diferentaROAS > 0.3);
        model.addAttribute("venitBrut",      String.format("%.2f", venitBrut));
        model.addAttribute("venitNet",       String.format("%.2f", venitBrut - rambursari));

        // Impact per sursa si influencer
        model.addAttribute("impactPerSursa",       returService.impactPerSursa());
        model.addAttribute("impactPerInfluencer",  returService.impactPerInfluencer());
        model.addAttribute("motiveGrupate",        returService.motiveGrupate());

        // Motive predefinite pentru formularul de adaugare
        model.addAttribute("motiveDisponibile", List.of(
                "Produs defect / deteriorat",
                "Produs diferit fata de descriere",
                "Marime / dimensiune gresita",
                "Comanda duplicata / gresita",
                "Client s-a razgandit",
                "Livrare intarziata",
                "Alt motiv"
        ));

        return "retururi";
    }

    @GetMapping("/comanda-items/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getComandaItems(@PathVariable Long id) {
        try {
            Comanda c = comandaService.gasesteDupaId(id);
            List<Map<String, Object>> items = new ArrayList<>();
            for (ComandaItem item : c.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("idProdus",  item.getIdProdus());
                m.put("cantitate", item.getCantitate());
                m.put("pretUnitar",item.getPretUnitar());
                m.put("cotaTVA",   item.getCotaTVA() != null ? item.getCotaTVA().name() : "TVA_21");
                m.put("total",     item.calculeazaTotal());
                items.add(m);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items",           items);
            result.put("sursa",           c.getSursa());
            result.put("codPromotional",  c.getCodPromotional());
            result.put("numeClient",      c.getNumeClient());
            result.put("telefon",         c.getTelefon());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/adauga")
    public String adauga(
            @RequestParam String numeClient,
            @RequestParam String telefon,
            @RequestParam String nrComanda,
            @RequestParam String motivRetur,
            @RequestParam(required = false) Long comandaId,
            @RequestParam(required = false, defaultValue = "0") Double costLogisticRetur,
            @RequestParam(required = false) List<String>  idProdus,
            @RequestParam(required = false) List<Integer> cantitateRetur,
            @RequestParam(required = false) List<Double>  pretUnitar,
            @RequestParam(required = false) List<String>  cotaTVA) {

        Retur retur = new Retur();
        retur.setNumeClient(numeClient);
        retur.setTelefon(telefon);
        retur.setNrComanda(nrComanda);
        retur.setMotivRetur(motivRetur);
        retur.setComandaId(comandaId);
        retur.setCostLogisticRetur(costLogisticRetur != null ? costLogisticRetur : 0.0);

        // Preluam sursa si codul promo din comanda originala - esential pentru ROAS real
        if (comandaId != null) {
            try {
                Comanda c = comandaService.gasesteDupaId(comandaId);
                retur.setSursaComanda(c.getSursa());
                retur.setCodPromotionalComanda(c.getCodPromotional());
            } catch (Exception ignored) {}
        }

        if (idProdus != null) {
            for (int i = 0; i < idProdus.size(); i++) {
                if (idProdus.get(i) == null || idProdus.get(i).isBlank()) continue;
                ReturItem item = new ReturItem();
                item.setRetur(retur);
                item.setIdProdus(idProdus.get(i));
                item.setCantitate(cantitateRetur != null && i < cantitateRetur.size()
                        ? cantitateRetur.get(i) : 1);
                item.setPretUnitar(pretUnitar != null && i < pretUnitar.size()
                        ? pretUnitar.get(i) : 0.0);
                try {
                    item.setCotaTVA(ComandaItem.CotaTVA.valueOf(
                            cotaTVA != null && i < cotaTVA.size() ? cotaTVA.get(i) : "TVA_21"));
                } catch (Exception ex) {
                    item.setCotaTVA(ComandaItem.CotaTVA.TVA_21);
                }
                retur.getItems().add(item);
            }
        }

        returService.salveaza(retur);
        return "redirect:/retururi";
    }

    @PostMapping("/status/{id}")
    public String actualizeazaStatus(@PathVariable Long id,
                                     @RequestParam Retur.StatusRetur status) {
        returService.actualizeazaStatus(id, status);
        return "redirect:/retururi";
    }

    @PostMapping("/sterge/{id}")
    public String sterge(@PathVariable Long id) {
        returService.sterge(id);
        return "redirect:/retururi";
    }
}