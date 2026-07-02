package com.example.Project.Teza.controller;

import com.example.Project.Teza.model.Produs;
import com.example.Project.Teza.service.CampanieMktgService;
import com.example.Project.Teza.service.SmartInputService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/smart-input")
@RequiredArgsConstructor
public class SmartInputController {

    private final SmartInputService smartInputService;
    private final CampanieMktgService campanieMktgService;

    @GetMapping
    public String pagina(Model model) {
        model.addAttribute("categorii", Produs.Categorie.values());
        model.addAttribute("campanii", campanieMktgService.toateCampaniile());
        return "smart-input";
    }

    @PostMapping("/extrage")
    @ResponseBody
    public ResponseEntity<SmartInputService.ExtrasDate> extrage(
            @RequestParam String mesaj,
            @RequestParam(defaultValue = "INSTAGRAM") String sursa) {

        if (mesaj == null || mesaj.trim().isEmpty()) {
            SmartInputService.ExtrasDate eroare = new SmartInputService.ExtrasDate();
            eroare.setSuccess(false);
            eroare.setEroare("Mesajul nu poate fi gol!");
            return ResponseEntity.badRequest().body(eroare);
        }

        SmartInputService.ExtrasDate rezultat = smartInputService.extrageDate(mesaj, sursa);
        return ResponseEntity.ok(rezultat);
    }

    @PostMapping(value = "/extrage-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<SmartInputService.ExtrasDate> extrageDinDocument(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            SmartInputService.ExtrasDate eroare = new SmartInputService.ExtrasDate();
            eroare.setSuccess(false);
            eroare.setEroare("Fisierul incarcat este gol sau lipseste!");
            return ResponseEntity.badRequest().body(eroare);
        }

        try {
            SmartInputService.ExtrasDate rezultat = smartInputService.extrageDinDocument(file);
            return ResponseEntity.ok(rezultat);

        } catch (Exception e) {
            SmartInputService.ExtrasDate eroare = new SmartInputService.ExtrasDate();
            eroare.setSuccess(false);
            eroare.setEroare("Eroare la procesarea fisierului de catre AI: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(eroare);
        }
    }
}