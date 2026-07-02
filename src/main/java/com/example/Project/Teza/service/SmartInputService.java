package com.example.Project.Teza.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class SmartInputService {

    @Value("${github.models.api.key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String getSystemPrompt() {
        return "Esti un asistent avansat de extragere a datelor pentru e-commerce (Intelligent Document Processing).\n" +
                "Analizeaza textul sau imaginea (factura, chitanta, AWB, conversatie) si extrage informatiile.\n" +
                "IMPORTANT: daca clientul comanda mai multe produse diferite, returneaza TOATE produsele in lista 'produse'.\n" +
                "Returneaza DOAR un JSON valid, fara text suplimentar:\n" +
                "{\n" +
                "  \"numeClient\": \"numele complet al clientului sau null\",\n" +
                "  \"telefon\": \"10 cifre care incep cu 07, fara spatii, sau null\",\n" +
                "  \"adresa\": \"adresa completa de livrare sau null\",\n" +
                "  \"codPostal\": \"codul postal de 6 cifre - completeaza-l DOAR daca este scris explicit, cifric, in text. NU deduce si NU inventa niciodata un cod postal din numele localitatii/strazii/sectorului, chiar daca pare cunoscut - codurile postale din Romania sunt specifice strazii/segmentului si o presupunere gresita duce la AWB-uri eronate la curier. Daca nu e scris explicit cifric in text, returneaza null.\",\n" +
                "  \"produse\": [\n" +
                "    {\n" +
                "      \"produs\": \"numele/ID-ul produsului\",\n" +
                "      \"cantitate\": 1,\n" +
                "      \"cotaTVA\": \"TVA_21\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"codPromotional\": \"codul promo daca exista sau null\",\n" +
                "  \"statusPlata\": \"una din valorile: RAMBURS (plata la livrare/ramburs), PLATIT_CU_CARDUL (card, POS, online, transfer bancar, achitat), PLATIT_ONLINE (PayPal, Stripe, procesator online), PLATIT_RAMBURS (confirmat platit la livrare). Default RAMBURS daca nu e clar.\",\n" +                "  \"note\": \"orice alte observatii relevante (culori, marimi, mentiuni curier) sau null\",\n" +
                "  \"confidenta\": \"MARE daca ai gasit sigur Numele + Telefonul + Adresa, MEDIE daca lipseste Telefonul sau Numele, MICA daca ai gasit doar Produsul\"\n" +
                "}\n\n" +
                "Reguli TVA conform legislatiei romane:\n" +
                "- TVA_9: locuinte noi\n" +
                "- TVA_11: DOAR alimente/bauturi destinate consumului uman (PRODUSE_ALIMENTARE, BAUTURI), medicamente/suplimente (SANATATE_FARMACIE), HoReCa, carti/ziare (CARTI_MEDIA), bilete evenimente culturale/sportive\n" +
                "- TVA_21: orice altceva - COSMETICE_INGRIJIRE, HAINE_INCALTAMINTE, ACCESORII_MODA, BIJUTERII_CEASURI, ELECTRONICE_IT, ELECTROCASNICE, MOBILA_DECORATIUNI, UNELTE_ECHIPAMENTE, MATERIALE_CONSTRUCTII, AUTO_MOTO, SPORT_FITNESS, JUCARII_COPII, PAPETARIE_BIROU, ANIMALE_DOMESTICE, GRADINA_PLANTE, HANDMADE_ARTIZANAT, servicii, materii prime, ambalaje\n" +
                "- TVA_0: export, livrari intracomunitare\n" +
                "ATENTIE: lumanarile = TVA_21. Mierea = TVA_11 (aliment). Cosmeticele = TVA_21. Serviciile = TVA_21.\n\n";
    }

    public ExtrasDate extrageDate(String mesaj, String sursa) {
        try {
            String prompt = getSystemPrompt() +
                    "Sursa mesajului: " + sursa + "\n" +
                    "Mesaj: " + mesaj;

            String body = "{\n" +
                    "  \"model\": \"gpt-4o\",\n" +
                    "  \"messages\": [{\"role\": \"user\", \"content\": " +
                    objectMapper.writeValueAsString(prompt) +
                    "}],\n" +
                    "  \"temperature\": 0.1,\n" +
                    "  \"max_tokens\": 800\n" +
                    "}";

            return trimiteCatreAI(body);

        } catch (Exception e) {
            return genereazaEroare("Eroare la procesarea textului: " + e.getMessage());
        }
    }

    public ExtrasDate extrageDinDocument(MultipartFile file) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = file.getContentType();
            if (mimeType == null || mimeType.isEmpty()) mimeType = "image/jpeg";

            String promptText = getSystemPrompt() +
                    "Extrage datele din aceasta imagine (factura, chitanta sau conversatie de comanda).";

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-4o");
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 800);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            ArrayNode contentArray = message.putArray("content");

            ObjectNode textContent = contentArray.addObject();
            textContent.put("type", "text");
            textContent.put("text", promptText);

            ObjectNode imageContent = contentArray.addObject();
            imageContent.put("type", "image_url");
            ObjectNode imageUrl = imageContent.putObject("image_url");
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);

            return trimiteCatreAI(objectMapper.writeValueAsString(requestBody));

        } catch (Exception e) {
            return genereazaEroare("Eroare la procesarea fisierului: " + e.getMessage());
        }
    }

    private ExtrasDate trimiteCatreAI(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://models.inference.ai.azure.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonNode result = objectMapper.readTree(content);
            ExtrasDate extras = new ExtrasDate();
            extras.setNumeClient(getNullableString(result, "numeClient"));
            extras.setTelefon(getNullableString(result, "telefon"));
            extras.setAdresa(getNullableString(result, "adresa"));
            extras.setCodPostal(getNullableString(result, "codPostal"));
            extras.setCodPromotional(getNullableString(result, "codPromotional"));
            extras.setStatusPlata(getNullableString(result, "statusPlata"));
            extras.setNote(getNullableString(result, "note"));
            extras.setConfidenta(getNullableString(result, "confidenta"));

            // Parseaza lista de produse
            List<ProdusDat> produse = new ArrayList<>();
            JsonNode produseNode = result.path("produse");
            if (produseNode.isArray() && produseNode.size() > 0) {
                for (JsonNode p : produseNode) {
                    ProdusDat pd = new ProdusDat();
                    pd.setProdus(getNullableString(p, "produs"));
                    pd.setCantitate(p.path("cantitate").asInt(1));
                    String tva = getNullableString(p, "cotaTVA");
                    pd.setCotaTVA(tva != null ? tva : "TVA_21");
                    produse.add(pd);
                }
            } else {
                // fallback: campuri vechi daca AI-ul returneaza format vechi
                String produs = getNullableString(result, "produs");
                if (produs != null) {
                    ProdusDat pd = new ProdusDat();
                    pd.setProdus(produs);
                    pd.setCantitate(result.path("cantitate").asInt(1));
                    String tva = getNullableString(result, "cotaTVA");
                    pd.setCotaTVA(tva != null ? tva : "TVA_21");
                    produse.add(pd);
                }
            }
            extras.setProduse(produse);

            // Compatibilitate cu codul vechi din comenzi.html
            if (!produse.isEmpty()) {
                extras.setProdus(produse.get(0).getProdus());
                extras.setCantitate(produse.get(0).getCantitate());
                extras.setCotaTVA(produse.get(0).getCotaTVA());
            }

            extras.setSuccess(true);
            return extras;

        } else {
            return genereazaEroare("Eroare API: " + response.statusCode() + " - " + response.body());
        }
    }

    private String getNullableString(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) return null;
        String text = value.asText().trim();
        return (text.equals("null") || text.isEmpty()) ? null : text;
    }

    private ExtrasDate genereazaEroare(String mesaj) {
        ExtrasDate eroare = new ExtrasDate();
        eroare.setSuccess(false);
        eroare.setEroare(mesaj);
        return eroare;
    }

    public static class ProdusDat {
        private String produs;
        private int cantitate = 1;
        private String cotaTVA = "TVA_21";

        public String getProdus() { return produs; }
        public void setProdus(String v) { this.produs = v; }
        public int getCantitate() { return cantitate; }
        public void setCantitate(int v) { this.cantitate = v; }
        public String getCotaTVA() { return cotaTVA; }
        public void setCotaTVA(String v) { this.cotaTVA = v; }
    }

    public static class ExtrasDate {
        private String numeClient;
        private String telefon;
        private String adresa;
        private String codPostal;
        private String produs;           // compatibilitate backward
        private int cantitate = 1;       // compatibilitate backward
        private String cotaTVA;          // compatibilitate backward
        private List<ProdusDat> produse = new ArrayList<>();
        private String codPromotional;
        private String statusPlata;
        private String categorieDetectata;
        private String note;
        private String confidenta;
        private boolean success;
        private String eroare;

        public String getNumeClient() { return numeClient; }
        public void setNumeClient(String v) { this.numeClient = v; }
        public String getTelefon() { return telefon; }
        public void setTelefon(String v) { this.telefon = v; }
        public String getAdresa() { return adresa; }
        public void setAdresa(String v) { this.adresa = v; }
        public String getCodPostal() { return codPostal; }
        public void setCodPostal(String v) { this.codPostal = v; }
        public String getProdus() { return produs; }
        public void setProdus(String v) { this.produs = v; }
        public int getCantitate() { return cantitate; }
        public void setCantitate(int v) { this.cantitate = v; }
        public String getCotaTVA() { return cotaTVA; }
        public void setCotaTVA(String v) { this.cotaTVA = v; }
        public List<ProdusDat> getProduse() { return produse; }
        public void setProduse(List<ProdusDat> v) { this.produse = v; }
        public String getCodPromotional() { return codPromotional; }
        public void setCodPromotional(String v) { this.codPromotional = v; }
        public String getStatusPlata() { return statusPlata; }
        public void setStatusPlata(String v) { this.statusPlata = v; }
        public String getCategorieDetectata() { return categorieDetectata; }
        public void setCategorieDetectata(String v) { this.categorieDetectata = v; }
        public String getNote() { return note; }
        public void setNote(String v) { this.note = v; }
        public String getConfidenta() { return confidenta; }
        public void setConfidenta(String v) { this.confidenta = v; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean v) { this.success = v; }
        public String getEroare() { return eroare; }
        public void setEroare(String v) { this.eroare = v; }
    }
}