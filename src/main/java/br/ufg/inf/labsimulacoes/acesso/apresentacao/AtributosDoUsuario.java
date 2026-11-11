package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Coloca o e-mail e os perfis de quem está logado no modelo de todas as telas, para o cabeçalho base
 * ({@code topo(email, perfis)}) e a navegação por perfil (RN-36, RN-37) não precisarem ser remontados
 * em cada controller das telas seguintes (#21 a #24).
 *
 * <p>O perfil vem dos papéis do Spring Security, sem consultar o banco, para a apresentação não falar
 * com a infraestrutura. Em telas abertas, como o login, não há usuário logado e nada é adicionado.
 */
@ControllerAdvice
class AtributosDoUsuario {

    @ModelAttribute
    void usuarioLogado(Authentication autenticacao, Model modelo) {
        if (!estaLogado(autenticacao)) {
            return;
        }
        boolean professor = temPapel(autenticacao, "ROLE_PROFESSOR");
        boolean curador = temPapel(autenticacao, "ROLE_CURADOR");
        boolean administrador = temPapel(autenticacao, "ROLE_ADMINISTRADOR");
        modelo.addAttribute("email", autenticacao.getName());
        modelo.addAttribute("professor", professor);
        modelo.addAttribute("curador", curador);
        modelo.addAttribute("administrador", administrador);
        modelo.addAttribute("perfis", nomesDosPerfis(professor, curador, administrador));
    }

    private static boolean estaLogado(Authentication autenticacao) {
        return autenticacao != null
                && autenticacao.isAuthenticated()
                && !(autenticacao instanceof AnonymousAuthenticationToken);
    }

    private static boolean temPapel(Authentication autenticacao, String papel) {
        return autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(papel::equals);
    }

    private static List<String> nomesDosPerfis(boolean professor, boolean curador, boolean administrador) {
        var nomes = new ArrayList<String>();
        if (professor) {
            nomes.add("Professor");
        }
        if (curador) {
            nomes.add("Curador Pedagógico");
        }
        if (administrador) {
            nomes.add("Administrador");
        }
        return nomes;
    }
}

