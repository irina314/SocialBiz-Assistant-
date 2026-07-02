package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Client;
import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.repository.ClientRepository;
import com.example.Project.Teza.repository.ComandaRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ComandaRepository comandaRepository;
    private final UtilizatorService utilizatorService;

    public ClientService(ClientRepository clientRepository,
                         ComandaRepository comandaRepository,
                         UtilizatorService utilizatorService) {
        this.clientRepository = clientRepository;
        this.comandaRepository = comandaRepository;
        this.utilizatorService = utilizatorService;
    }

    public Client gasesteDupaId(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Client", id));
    }

    public Client editeaza(Long id, String nume, String telefon, String adresa) {
        Client c = gasesteDupaId(id);
        c.setNume(nume);
        c.setTelefon(telefon);
        c.setAdresa(adresa);
        return clientRepository.save(c);
    }

    public void sterge(Long id) {
        clientRepository.deleteById(id);
    }

    public Client salveazaSauActualizeaza(String nume, String telefon, String adresa) {
        Long uid = utilizatorService.getIdUtilizatorCurent();
        return clientRepository.findByTelefonAndUtilizatorId(telefon, uid)
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setNume(nume);
                    c.setTelefon(telefon);
                    c.setAdresa(adresa);
                    c.setUtilizator(utilizatorService.getUtilizatorCurent());
                    return clientRepository.save(c);
                });
    }

    public List<Client> toti() {
        return clientRepository.findByUtilizatorId(
                utilizatorService.getIdUtilizatorCurent());
    }

    public List<Client> dupaLuna(LocalDateTime dela) {
        return clientRepository.findByUtilizatorIdAndDataAdaugareAfter(
                utilizatorService.getIdUtilizatorCurent(), dela);
    }

    public boolean esteInactiv(Client client) {
        Long uid = utilizatorService.getIdUtilizatorCurent();
        LocalDateTime prag = LocalDateTime.now().minusDays(20);
        return comandaRepository.findByUtilizatorId(uid).stream()
                .filter(c -> c.getTelefon() != null
                        && c.getTelefon().equals(client.getTelefon()))
                .noneMatch(c -> c.getData() != null && c.getData().isAfter(prag));
    }

    public double totalIncasat(List<Client> clienti) {
        Long uid = utilizatorService.getIdUtilizatorCurent();
        return comandaRepository.findByUtilizatorId(uid).stream()
                .filter(c -> c.getStatus() == Comanda.StatusComanda.LIVRAT
                        || c.getStatus() == Comanda.StatusComanda.FINALIZAT)
                .mapToDouble(this::calculeazaFaraTVA)
                .sum();
    }

    public double totalDeIncasat(List<Client> clienti) {
        Long uid = utilizatorService.getIdUtilizatorCurent();
        return comandaRepository.findByUtilizatorId(uid).stream()
                .filter(c -> c.getStatus() == Comanda.StatusComanda.NOU
                        || c.getStatus() == Comanda.StatusComanda.CONFIRMAT
                        || c.getStatus() == Comanda.StatusComanda.EXPEDIAT)
                .mapToDouble(this::calculeazaFaraTVA)
                .sum();
    }

    private double calculeazaFaraTVA(Comanda comanda) {
        if (comanda.getItems() == null) return 0;
        return comanda.getItems().stream()
                .mapToDouble(item -> {
                    if (item.getPretUnitar() == null || item.getCantitate() == null) return 0;
                    return item.getCantitate() * item.getPretUnitar();
                })
                .sum();
    }

    public long numarInactivi(List<Client> clienti) {
        return clienti.stream().filter(this::esteInactiv).count();
    }
}