package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Furnizor;
import com.example.Project.Teza.repository.FurnizorRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FurnizorService {

    private final FurnizorRepository furnizorRepository;
    private final UtilizatorService utilizatorService;

    public List<Furnizor> toti() {
        return furnizorRepository.findByUtilizatorId(utilizatorService.getIdUtilizatorCurent());
    }

    public List<Furnizor> dupaStatus(Furnizor.Status status) {
        return furnizorRepository.findByUtilizatorIdAndStatus(
                utilizatorService.getIdUtilizatorCurent(), status);
    }

    public Furnizor salveaza(Furnizor furnizor) {
        if (furnizor.getUtilizator() == null) {
            furnizor.setUtilizator(utilizatorService.getUtilizatorCurent());
        }
        return furnizorRepository.save(furnizor);
    }

    public Furnizor gasesteDupaId(Long id) {
        Furnizor f = furnizorRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Furnizor", id));
        if (!f.getUtilizator().getId().equals(utilizatorService.getIdUtilizatorCurent()))
            throw new AccesInterzisException("furnizor");
        return f;
    }

    public void sterge(Long id) {
        gasesteDupaId(id);
        furnizorRepository.deleteById(id);
    }

    public long numarActivi() {
        return dupaStatus(Furnizor.Status.ACTIV).size();
    }
}