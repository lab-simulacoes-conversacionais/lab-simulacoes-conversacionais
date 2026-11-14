package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Regras de acesso às rotas (RF-01, RNF-09). Tudo exige login, menos a tela de login e a verificação
 * de saúde, usada pelo healthcheck do Docker.
 *
 * <p>A proteção CSRF fica ligada no padrão do Spring Security: o token é guardado na sessão e aceito
 * no campo {@code _csrf} ou no cabeçalho {@code X-CSRF-TOKEN}, que é o usado nas requisições HTMX.
 *
 * <p>As rotas só exigem login. O perfil é verificado em cada caso de uso, com {@code @PreAuthorize}
 * (Stack, seção 7).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class ConfiguracaoDeSeguranca {

    @Bean
    SecurityFilterChain filtroDeSeguranca(HttpSecurity http, UserDetailsService usuarios) throws Exception {
        var sessaoExpirada = new SessaoExpirada();
        return http
                .addFilterBefore(new SincronizacaoDePerfis(usuarios), AuthorizationFilter.class)
                .authorizeHttpRequests(rotas -> rotas
                        // Sem isto, um erro como o 403 do CSRF viraria redirecionamento para o login.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        // Fonte única de liberação: o matcher Ant de "/login" cobre a página, o
                        // processamento (POST) e as variações de query (?error, ?logout, ?expirada),
                        // o que o permitAll() por URL exata do formLogin não faria.
                        .requestMatchers("/login", "/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.*")
                        .permitAll()
                        // Os casos de uso já exigem o perfil, mas os formulários vazios não chamam
                        // nenhum; assim nenhuma tela da administração abre para outro perfil (RN-37).
                        .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                        // Pelo mesmo motivo: o administrador não edita conteúdo pedagógico (RN-37).
                        .requestMatchers("/cenarios/**", "/biblioteca/**").hasAnyRole("PROFESSOR", "CURADOR")
                        // Aprovar e devolver é só do curador (RN-05).
                        .requestMatchers("/curadoria/**").hasRole("CURADOR")
                        // Só o professor conduz sessões em sala (RN-10).
                        .requestMatchers("/sessoes/**").hasRole("PROFESSOR")
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha"))
                .logout(logout -> logout.deleteCookies("JSESSIONID"))
                // Com a estratégia definida, o Spring também a usa quando o token CSRF sumiu junto com
                // a sessão expirada, em vez de responder 403.
                .sessionManagement(sessao -> sessao.invalidSessionStrategy(sessaoExpirada))
                .exceptionHandling(erros -> erros
                        .defaultAuthenticationEntryPointFor(sessaoExpirada, SessaoExpirada::ehRequisicaoHtmx))
                .build();
    }
}
