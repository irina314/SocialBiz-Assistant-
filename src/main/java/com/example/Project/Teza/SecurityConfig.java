package com.example.Project.Teza;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Configurarea securitatii aplicatiei prin Spring Security.
 *
 * Mecanisme implementate:
 *  - Autentificare bazata pe formular cu pagini personalizate de login/logout
 *  - Hashing BCrypt pentru stocarea parolelor (factor cost implicit: 10)
 *  - Protectie CSRF activa pentru toate rutele, cu exceptie explicita
 *    pentru endpoint-urile SmartInput (detalii mai jos)
 *  - Restrictionarea accesului: orice cerere autentificata este izolata
 *    la nivel de utilizator prin injectia utilizator_id in sesiune
 *    (implementata in UserDetailsServiceImpl)
 *
 * Nota privind exceptia CSRF pentru SmartInput:
 *   Endpoint-urile /smart-input/extrage si /smart-input/extrage-document
 *   sunt apelate exclusiv prin cereri AJAX (fetch API) din interfata
 *   Thymeleaf autentificata. Deoarece aceste cereri nu transmit cookie-ul
 *   de sesiune in mod cross-origin (SameSite implicit Lax in browserele
 *   moderne), riscul unui atac CSRF real este minim.
 *   Exceptia permite trimiterea multipart/form-data si application/json
 *   fara overhead-ul token-ului CSRF, pastrand toate celelalte rute protejate.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CookieCsrfTokenRepository face ca token-ul CSRF sa fie disponibil si ca
        // cookie XSRF-TOKEN, pe langa atributul de request. Astfel apelurile AJAX
        // (fetch) pot citi token-ul din cookie si il pot trimite ca header X-XSRF-TOKEN
        // -> rezolva 401 la /produse/adauga-ajax si /export-curier/marcheaza-expediat
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(
                                "/smart-input/extrage",
                                "/smart-input/extrage-document"
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/css/**", "/js/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}