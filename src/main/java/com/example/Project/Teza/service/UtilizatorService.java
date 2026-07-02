package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Utilizator;
import com.example.Project.Teza.repository.UtilizatorRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UtilizatorService {

    private final UtilizatorRepository utilizatorRepository;

    public Utilizator getUtilizatorCurent() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return utilizatorRepository.findByEmail(email)
                .orElseThrow(() -> new ResursaNegasitaException("Utilizatorul autentificat nu a fost gasit. Incearca sa te deconectezi si sa te autentifici din nou."));
    }

    public Long getIdUtilizatorCurent() {
        return getUtilizatorCurent().getId();
    }
}