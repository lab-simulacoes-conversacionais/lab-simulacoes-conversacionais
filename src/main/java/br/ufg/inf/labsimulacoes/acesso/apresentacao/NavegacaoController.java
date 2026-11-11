package br.ufg.inf.labsimulacoes.acesso.apresentacao;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Telas base do sistema (RF-01, RF-02): a tela de login e o início com a navegação por perfil.
 *
 * <p>O e-mail e os perfis de quem está logado são colocados no modelo pelo {@link AtributosDoUsuario},
 * que vale para todas as telas. As telas de cada função são entregues pelas issues seguintes (#21 a #24).
 */
@Controller
class NavegacaoController {

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String inicio() {
        return "inicio";
    }
}


