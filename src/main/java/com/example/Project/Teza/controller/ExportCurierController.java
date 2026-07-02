package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.ExportLinieCurier;
import com.example.Project.Teza.model.Utilizator;
import com.example.Project.Teza.service.ComandaService;
import com.example.Project.Teza.service.ExportCurierService;
import com.example.Project.Teza.service.UtilizatorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/export-curier")
@RequiredArgsConstructor
public class ExportCurierController {

    private final ComandaService      comandaService;
    private final ExportCurierService exportCurierService;
    private final UtilizatorService   utilizatorService;

    @GetMapping
    public String pagina(Model model) {
        List<Comanda> pentruExport = comandaService.pentruExport();

        // Continut implicit per comanda (din produsele reale), folosit ca valoare
        // pre-completata in formular, editabila inainte de export.
        Map<Long, String> continutImplicit = new HashMap<>();
        for (Comanda c : pentruExport) {
            continutImplicit.put(c.getId(), exportCurierService.continutImplicit(c));
        }

        model.addAttribute("nrConfirmate", pentruExport.size());
        model.addAttribute("nrExpediate",  comandaService.numaraDupaStatus(Comanda.StatusComanda.EXPEDIAT));
        model.addAttribute("pentruExport", pentruExport);
        model.addAttribute("totalExport",  pentruExport.size());
        model.addAttribute("continutImplicit", continutImplicit);
        return "export-curier";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) List<Long> ids,
            @RequestParam(defaultValue = "FAN_COURIER") String curier,
            HttpServletRequest request) {

        if (ids == null || ids.isEmpty()) return ResponseEntity.badRequest().build();

        List<Comanda> comenzi = comandaService.pentruExport().stream()
                .filter(c -> ids.contains(c.getId()))
                .toList();

        if (comenzi.isEmpty()) return ResponseEntity.badRequest().build();

        // Citim valorile editate manual per-comanda din formular: cp_<id>, gr_<id>,
        // cl_<id>, ct_<id> (cod postal, greutate, colete, continut). Cod postal e
        // precompletat din campul real al comenzii (codPostal e obligatoriu la
        // inregistrare), dar poate fi suprascris manual la export daca e nevoie
        // de o adresa de livrare diferita pentru aceasta expediere.
        List<ExportLinieCurier> linii = comenzi.stream().map(c -> {
            ExportLinieCurier l = new ExportLinieCurier();
            l.setComanda(c);
            l.setCodPostal(parametru(request, "cp_" + c.getId(), nvl(c.getCodPostal())));
            l.setGreutateKg(parseDoubleSigur(parametru(request, "gr_" + c.getId(), "1"), 1.0));
            l.setNrColete(parseIntSigur(parametru(request, "cl_" + c.getId(), "1"), 1));
            l.setContinut(parametru(request, "ct_" + c.getId(), exportCurierService.continutImplicit(c)));
            return l;
        }).toList();

        // Fan Courier (SelfAWB) importa exclusiv fisiere .csv - restul curierilor
        // accepta .xlsx generat cu formatare.
        if ("FAN_COURIER".equals(curier)) {
            Utilizator firma = utilizatorService.getUtilizatorCurent();
            byte[] bytes = exportCurierService.genereazaCsvFanCourier(linii, firma);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export_fan_courier.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(bytes);
        }

        byte[] bytes = exportCurierService.genereazaExcel(linii, curier);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export_" + curier.toLowerCase() + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/marcheaza-expediat")
    @ResponseBody
    public ResponseEntity<String> marcheazaExpediat(@RequestParam List<Long> ids) {
        try {
            comandaService.marcheazaExpediat(ids);
            return ResponseEntity.ok("{\"ok\":true}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"ok\":false,\"eroare\":\"" + e.getMessage() + "\"}");
        }
    }

    private String parametru(HttpServletRequest request, String nume, String implicit) {
        String v = request.getParameter(nume);
        return (v == null || v.isBlank()) ? implicit : v;
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private double parseDoubleSigur(String s, double implicit) {
        try { return Double.parseDouble(s.replace(",", ".")); }
        catch (Exception e) { return implicit; }
    }

    private int parseIntSigur(String s, int implicit) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return implicit; }
    }
}