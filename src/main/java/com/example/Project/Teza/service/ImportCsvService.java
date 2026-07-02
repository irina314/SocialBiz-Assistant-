package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.ComandaItem;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportCsvService {

    private final ComandaService comandaService;

    public record RezultatImport(int importate, int sarite, List<String> erori) {}

    // --- Entry point ----------------------------------------------------------

    public RezultatImport importa(MultipartFile file, String platforma) throws Exception {
        return switch (platforma.toUpperCase()) {
            case "SHOPIFY"     -> importaShopify(file);
            case "WOOCOMMERCE" -> importaWooCommerce(file);
            default -> throw new DateInvalideException(
                    "Platforma '" + platforma + "' nu este suportata pentru import.",
                    "Platformele disponibile sunt: Shopify, WooCommerce. Verifica sursa fisierului CSV.");
        };
    }

    // --- Shopify --------------------------------------------------------------
    //
    // Coloane relevante din exportul Shopify Orders CSV:
    //   Name, Email, Financial Status, Fulfillment Status,
    //   Lineitem name, Lineitem quantity, Lineitem price,
    //   Shipping Name, Shipping Phone, Shipping Address1,
    //   Shipping City, Shipping Zip, Discount Code
    //
    // Un singur order poate aparea pe mai multe randuri (cate un rand per produs).
    // Il identificam dupa coloana "Name" (ex: #1001).

    private RezultatImport importaShopify(MultipartFile file) throws Exception {
        int importate = 0, sarite = 0;
        List<String> erori = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            String ultimulOrderId = null;
            Comanda comandaCurenta = null;

            for (CSVRecord row : parser) {
                try {
                    String orderId = csv(row, "Name");
                    if (orderId.isBlank()) continue;

                    // Rand nou pentru acelasi order - adauga doar produsul
                    if (orderId.equals(ultimulOrderId) && comandaCurenta != null) {
                        adaugaItemShopify(comandaCurenta, row);
                        continue;
                    }

                    // Salveaza order-ul anterior daca exista
                    if (comandaCurenta != null) {
                        comandaService.salveaza(comandaCurenta);
                        importate++;
                    }

                    // Creeaza order nou
                    comandaCurenta = new Comanda();
                    comandaCurenta.setSursa("SHOPIFY");
                    comandaCurenta.setNumeClient(csv(row, "Shipping Name"));
                    comandaCurenta.setTelefon(csv(row, "Shipping Phone"));
                    comandaCurenta.setAdresa(construiesteAdresa(
                            csv(row, "Shipping Address1"),
                            csv(row, "Shipping City"),
                            csv(row, "Shipping Zip")));
                    comandaCurenta.setCodPromotional(csv(row, "Discount Code"));
                    comandaCurenta.setStatusPlata(
                            "paid".equalsIgnoreCase(csv(row, "Financial Status"))
                                    ? Comanda.StatusPlata.PLATIT_ONLINE
                                    : Comanda.StatusPlata.RAMBURS);

                    adaugaItemShopify(comandaCurenta, row);
                    ultimulOrderId = orderId;

                } catch (Exception e) {
                    erori.add("Rand " + row.getRecordNumber() + ": " + e.getMessage());
                    sarite++;
                }
            }

            // Salveaza ultimul order
            if (comandaCurenta != null) {
                comandaService.salveaza(comandaCurenta);
                importate++;
            }
        }

        return new RezultatImport(importate, sarite, erori);
    }

    private void adaugaItemShopify(Comanda comanda, CSVRecord row) {
        String numeProdusSauSku = csv(row, "Lineitem name");
        if (numeProdusSauSku.isBlank()) return;

        ComandaItem item = new ComandaItem();
        item.setComanda(comanda);
        item.setIdProdus(numeProdusSauSku);
        item.setCantitate(parseIntSafe(csv(row, "Lineitem quantity"), 1));
        item.setPretUnitar(parseDoubleSafe(csv(row, "Lineitem price"), 0.0));
        item.setCotaTVA(ComandaItem.CotaTVA.TVA_21); // Shopify nu exporta TVA
        comanda.getItems().add(item);
    }

    // --- WooCommerce ----------------------------------------------------------
    //
    // Coloane relevante din exportul WooCommerce Orders CSV (plugin standard):
    //   Order ID, Order Status, Billing First Name, Billing Last Name,
    //   Billing Phone, Billing Address 1, Billing City, Billing Postcode,
    //   Payment Method, Coupon Code,
    //   Item #{n} Name, Item #{n} Quantity, Item #{n} Total
    //
    // WooCommerce exporta un order per rand, cu produsele inline
    // (Item #1 Name, Item #2 Name, etc.) pana la maxim ~10 produse.

    private RezultatImport importaWooCommerce(MultipartFile file) throws Exception {
        int importate = 0, sarite = 0;
        List<String> erori = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord row : parser) {
                try {
                    String orderId = csv(row, "Order ID");
                    if (orderId.isBlank()) continue;

                    Comanda comanda = new Comanda();
                    comanda.setSursa("WOOCOMMERCE");

                    String prenume = csv(row, "Billing First Name");
                    String nume    = csv(row, "Billing Last Name");
                    comanda.setNumeClient((prenume + " " + nume).trim());
                    comanda.setTelefon(csv(row, "Billing Phone"));
                    comanda.setAdresa(construiesteAdresa(
                            csv(row, "Billing Address 1"),
                            csv(row, "Billing City"),
                            csv(row, "Billing Postcode")));
                    comanda.setCodPromotional(csv(row, "Coupon Code"));

                    String metodaPlata = csv(row, "Payment Method").toLowerCase();
                    comanda.setStatusPlata(
                            metodaPlata.contains("card") || metodaPlata.contains("stripe") || metodaPlata.contains("paypal")
                                    ? Comanda.StatusPlata.PLATIT_ONLINE
                                    : Comanda.StatusPlata.RAMBURS);

                    // Parcurge Item #1 ... Item #20
                    for (int i = 1; i <= 20; i++) {
                        String numeItem = csvOpt(row, "Item #" + i + " Name");
                        if (numeItem == null || numeItem.isBlank()) break;

                        ComandaItem item = new ComandaItem();
                        item.setComanda(comanda);
                        item.setIdProdus(numeItem);
                        item.setCantitate(parseIntSafe(csvOpt(row, "Item #" + i + " Quantity"), 1));
                        item.setPretUnitar(parseDoubleSafe(csvOpt(row, "Item #" + i + " Total"), 0.0));
                        item.setCotaTVA(ComandaItem.CotaTVA.TVA_21);
                        comanda.getItems().add(item);
                    }

                    if (comanda.getItems().isEmpty()) { sarite++; continue; }

                    comandaService.salveaza(comanda);
                    importate++;

                } catch (Exception e) {
                    erori.add("Rand " + row.getRecordNumber() + ": " + e.getMessage());
                    sarite++;
                }
            }
        }

        return new RezultatImport(importate, sarite, erori);
    }

    // --- Utilitati ------------------------------------------------------------

    private String csv(CSVRecord row, String coloana) {
        return row.isMapped(coloana) ? row.get(coloana).trim() : "";
    }

    /** Returneaza null daca coloana nu exista (pentru coloane optionale) */
    private String csvOpt(CSVRecord row, String coloana) {
        return row.isMapped(coloana) ? row.get(coloana).trim() : null;
    }

    private String construiesteAdresa(String strada, String oras, String cod) {
        return List.of(strada, oras, cod).stream()
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private int parseIntSafe(String val, int fallback) {
        try { return val != null ? Integer.parseInt(val) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }

    private double parseDoubleSafe(String val, double fallback) {
        try { return val != null ? Double.parseDouble(val.replace(",", ".")) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }
}