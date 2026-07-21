package swp.se1889.g1.rice_store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import swp.se1889.g1.rice_store.service.Iservice.UserService;

@Configuration
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserService userService) {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(userService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        return daoAuthenticationProvider;
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String role = authentication.getAuthorities().toString();

            if (role.contains("ROLE_ADMIN")) {
                response.sendRedirect("/admin/manage-owner");
            } else if (role.contains("ROLE_EMPLOYEE")) {
                response.sendRedirect("/employee/home");
            } else if (role.contains("ROLE_OWNER")) {
                response.sendRedirect("owner/store");
            } else {
                response.sendRedirect("/home");
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(configurer -> configurer
                        .requestMatchers("/register", "/login", "/assets/**", "/forgotPassword/**").permitAll()
                        .requestMatchers("/mock-latency", "/research/**").permitAll()
                        .requestMatchers("/shifts/**").hasAnyRole("OWNER", "EMPLOYEE")
                        .requestMatchers("/users/**").permitAll()
                        .requestMatchers("/restore/**").permitAll()
                        .requestMatchers("/api/owner/**").permitAll()
                        .requestMatchers("owner/**").hasRole("OWNER")
                        .requestMatchers("admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(customAuthenticationSuccessHandler())
                        .permitAll())
                .logout(logout -> logout
                        .permitAll()
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login"))
                .exceptionHandling(configurer -> configurer.accessDeniedPage("/login"));
        return http.build();
    }
}
