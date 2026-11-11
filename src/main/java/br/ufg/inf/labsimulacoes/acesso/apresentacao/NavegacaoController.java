package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Telas base do sistema (RF-01, RF-02): a tela de login e o início com a navegação por perfil.
 *
 * <p>Os atalhos do início aparecem conforme os perfis de quem está logado (RN-36, RN-37). O perfil
 * vem dos papéis do Spring Security, sem consultar o banco, para a apresentação não falar com a
 * infraestrutura. As telas de cada função são entregues pelas issues seguintes (#21 a #24).
 */
@Controller
class NavegacaoController {

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String inicio(Authentication autenticacao, Model modelo) {
        boolean professor = temPapel(autenticacao, "ROLE_PROFESSOR");
        boolean curador = temPapel(autenticacao, "ROLE_CURADOR");
        boolean administrador = temPapel(autenticacao, "ROLE_ADMINISTRADOR");
        modelo.addAttribute("email", autenticacao.getName());
        modelo.addAttribute("professor", professor);
        modelo.addAttribute("curador", curador);
        modelo.addAttribute("administrador", administrador);
        modelo.addAttribute("perfis", nomesDosPerfis(professor, curador, administrador));
        return "inicio";
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

