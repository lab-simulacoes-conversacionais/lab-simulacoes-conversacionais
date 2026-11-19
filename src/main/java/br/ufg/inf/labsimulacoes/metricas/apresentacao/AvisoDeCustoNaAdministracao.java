package br.ufg.inf.labsimulacoes.metricas.apresentacao;

import br.ufg.inf.labsimulacoes.metricas.aplicacao.ConsumoDaIaNaAdministracao;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * RNF-13: enquanto houver alerta de custo hoje, todas as telas de administração mostram o aviso no topo
 * ({@code painelAdmin} em fragmentos/base.html), com o custo calculado na hora. Só nas telas em /admin,
 * que só o administrador abre; as outras não fazem a consulta. Uma falha na consulta não derruba a tela.
 */
@ControllerAdvice
class AvisoDeCustoNaAdministracao {

    static final String ATRIBUTO = "avisoDeCusto";

    private static final Logger LOG = LoggerFactory.getLogger(AvisoDeCustoNaAdministracao.class);

    private final ConsumoDaIaNaAdministracao consumo;

    AvisoDeCustoNaAdministracao(ConsumoDaIaNaAdministracao consumo) {
        this.consumo = consumo;
    }

    @ModelAttribute
    void avisoDeCusto(HttpServletRequest requisicao, Model modelo) {
        String caminho = requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        if (!caminho.equals("/admin") && !caminho.startsWith("/admin/")) {
            return;
        }
        try {
            consumo.avisoDeHoje().ifPresent(aviso -> modelo.addAttribute(ATRIBUTO, aviso));
        } catch (RuntimeException e) {
            LOG.warn("Não deu para conferir o alerta de custo da IA para a tela de administração", e);
        }
    }
}
