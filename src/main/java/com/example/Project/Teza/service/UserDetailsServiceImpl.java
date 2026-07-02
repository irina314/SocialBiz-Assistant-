package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Utilizator;
import com.example.Project.Teza.repository.UtilizatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UtilizatorRepository utilizatorRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utilizator utilizator = utilizatorRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User negasit: " + email));

        return User.builder()
                .username(utilizator.getEmail())
                .password(utilizator.getParola())
                .roles("USER")
                .build();
    }
}