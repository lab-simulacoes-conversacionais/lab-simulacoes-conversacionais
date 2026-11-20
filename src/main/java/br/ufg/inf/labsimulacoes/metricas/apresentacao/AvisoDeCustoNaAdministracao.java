package br.ufg.inf.labsimulacoes.metricas.apresentacao;

import br.ufg.inf.labsimulacoes.metricas.aplicacao.ConsumoDaIaNaAdministracao;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * RNF-13: enquanto houver alerta de custo hoje, as telas de administração mostram o aviso no topo
 * ({@code painelAdmin} em fragmentos/base.html), com o custo calculado na hora. Uma falha na consulta não
 * derruba a tela.
 *
 * <p>Só consulta quando vai haver tela para mostrar o aviso: um GET em /admin, que só o administrador
 * abre, e fora do HTMX. Um POST que só redireciona e um fragmento HTMX não usam o aviso. A aba Consumo
 * também fica de fora, porque já soma o custo do dia e põe o aviso ela mesma ({@link ConsumoController}).
 */
@ControllerAdvice
class AvisoDeCustoNaAdministracao {

    static final String ATRIBUTO = "avisoDeCusto";
    static final String CABECALHO_DO_HTMX = "HX-Request";

    private static final Logger LOG = LoggerFactory.getLogger(AvisoDeCustoNaAdministracao.class);

    private final ConsumoDaIaNaAdministracao consumo;

    AvisoDeCustoNaAdministracao(ConsumoDaIaNaAdministracao consumo) {
        this.consumo = consumo;
    }

    @ModelAttribute
    void avisoDeCusto(HttpServletRequest requisicao, Model modelo) {
        if (!mostraOAviso(requisicao)) {
            return;
        }
        try {
            consumo.avisoDeHoje().ifPresent(aviso -> modelo.addAttribute(ATRIBUTO, aviso));
        } catch (RuntimeException e) {
            LOG.warn("Não deu para conferir o alerta de custo da IA para a tela de administração", e);
        }
    }

    static boolean mostraOAviso(HttpServletRequest requisicao) {
        String caminho = requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        boolean daAdministracao = caminho.equals("/admin") || caminho.startsWith("/admin/");
        return daAdministracao
                && HttpMethod.GET.matches(requisicao.getMethod())
                && requisicao.getHeader(CABECALHO_DO_HTMX) == null
                && !(requisicao.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod metodo
                        && metodo.getBeanType().equals(ConsumoController.class));
    }
}
