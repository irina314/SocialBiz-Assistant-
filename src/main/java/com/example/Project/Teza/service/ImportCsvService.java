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

    // --- WooCommerce ------------------------------------------------------
    //
    // Coloane relevante din exportul real WooCommerce Orders CSV
    // (plugin WP All Export, "Add products as: rows"):
    //   Order Number, Order Status, First Name (Billing), Last Name (Billing),
    //   Address 1&2 (Billing), City (Billing), Postcode (Billing),
    //   Phone (Billing), Payment Method Title, Coupon Code,
    //   Item Name, Quantity (- Refund), Item Cost
    //
    // La fel ca la Shopify, WooCommerce (cu acest plugin) exporta un rand
    // per produs, iar comenzile cu mai multe produse au acelasi
    // "Order Number" repetat pe randuri consecutive.

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

            String ultimulOrderId = null;
            Comanda comandaCurenta = null;

            for (CSVRecord row : parser) {
                try {
                    String orderId = csv(row, "Order Number");
                    if (orderId.isBlank()) continue;

                    // Rand nou pentru aceeasi comanda - adauga doar produsul
                    if (orderId.equals(ultimulOrderId) && comandaCurenta != null) {
                        adaugaItemWooCommerce(comandaCurenta, row);
                        continue;
                    }

                    // Salveaza comanda anterioara daca exista
                    if (comandaCurenta != null) {
                        comandaService.salveaza(comandaCurenta);
                        importate++;
                    }

                    // Creeaza comanda noua
                    comandaCurenta = new Comanda();
                    comandaCurenta.setSursa("WOOCOMMERCE");

                    String prenume = csv(row, "First Name (Billing)");
                    String nume    = csv(row, "Last Name (Billing)");
                    comandaCurenta.setNumeClient((prenume + " " + nume).trim());
                    comandaCurenta.setTelefon(csv(row, "Phone (Billing)"));
                    comandaCurenta.setAdresa(construiesteAdresa(
                            csv(row, "Address 1&2 (Billing)"),
                            csv(row, "City (Billing)"),
                            ""));
                    comandaCurenta.setCodPostal(csv(row, "Postcode (Billing)"));
                    comandaCurenta.setCodPromotional(csv(row, "Coupon Code"));

                    String metodaPlata = csv(row, "Payment Method Title");
                    comandaCurenta.setMetodaPlata(metodaPlata);
                    String metodaPlataLower = metodaPlata.toLowerCase();
                    comandaCurenta.setStatusPlata(
                            metodaPlataLower.contains("card") || metodaPlataLower.contains("stripe")
                                    || metodaPlataLower.contains("paypal") || metodaPlataLower.contains("online")
                                    ? Comanda.StatusPlata.PLATIT_ONLINE
                                    : Comanda.StatusPlata.RAMBURS);

                    adaugaItemWooCommerce(comandaCurenta, row);
                    ultimulOrderId = orderId;

                } catch (Exception e) {
                    erori.add("Rand " + row.getRecordNumber() + ": " + e.getMessage());
                    sarite++;
                }
            }

            // Salveaza ultima comanda
            if (comandaCurenta != null) {
                comandaService.salveaza(comandaCurenta);
                importate++;
            }
        }

        return new RezultatImport(importate, sarite, erori);
    }

    private void adaugaItemWooCommerce(Comanda comanda, CSVRecord row) {
        String numeProdus = csv(row, "Item Name");
        if (numeProdus.isBlank()) return;

        ComandaItem item = new ComandaItem();
        item.setComanda(comanda);
        item.setIdProdus(numeProdus);
        item.setCantitate(parseIntSafe(csv(row, "Quantity (- Refund)"), 1));
        item.setPretUnitar(parseDoubleSafe(csv(row, "Item Cost"), 0.0));
        item.setCotaTVA(ComandaItem.CotaTVA.TVA_21); // WooCommerce nu exporta TVA per produs
        comanda.getItems().add(item);
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