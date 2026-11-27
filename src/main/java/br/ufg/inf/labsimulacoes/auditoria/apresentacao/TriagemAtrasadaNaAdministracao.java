package br.ufg.inf.labsimulacoes.auditoria.apresentacao;

import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem;
import br.ufg.inf.labsimulacoes.auditoria.aplicacao.FilaDeTriagem.ResumoDoPrazo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * RN-24: as sinalizações que passaram do prazo de 2 dias úteis ficam em destaque também para o
 * administrador. As telas de administração e a do log mostram o aviso no topo ({@code painelAdmin} em
 * fragmentos/base.html), só com a contagem e a data da mais antiga, sem os textos (RN-37). O curador vê o
 * destaque na própria fila. Uma falha na consulta não derruba a tela.
 *
 * <p>Só consulta num GET fora do HTMX em /admin, que só o administrador abre, ou em /auditoria, do curador e
 * do administrador: um POST que só redireciona e um fragmento não usam o aviso.
 */
@ControllerAdvice
class TriagemAtrasadaNaAdministracao {

    static final String ATRIBUTO = "triagemAtrasada";

    private static final Logger LOG = LoggerFactory.getLogger(TriagemAtrasadaNaAdministracao.class);

    private final FilaDeTriagem fila;
    private final DatasNaTela datas;

    TriagemAtrasadaNaAdministracao(FilaDeTriagem fila, DatasNaTela datas) {
        this.fila = fila;
        this.datas = datas;
    }

    @ModelAttribute
    void triagemAtrasada(HttpServletRequest requisicao, Model modelo) {
        if (!mostraOAviso(requisicao)) {
            return;
        }
        try {
            ResumoDoPrazo resumo = fila.resumoDoPrazo();
            if (resumo.temAtrasadas()) {
                modelo.addAttribute(ATRIBUTO, new Atraso(
                        resumo.foraDoPrazo().size(),
                        datas.dia(resumo.foraDoPrazo().getFirst().criadaEm())));
            }
        } catch (RuntimeException e) {
            LOG.warn("Não deu para conferir as sinalizações fora do prazo para a tela de administração", e);
        }
    }

    static boolean mostraOAviso(HttpServletRequest requisicao) {
        String caminho = requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        boolean daAdministracao = caminho.equals("/admin") || caminho.startsWith("/admin/")
                || caminho.startsWith("/auditoria/");
        return daAdministracao
                && HttpMethod.GET.matches(requisicao.getMethod())
                && requisicao.getHeader("HX-Request") == null;
    }

    /**
     * @param quantas sinalizações fora do prazo
     * @param maisAntiga o dia em que a mais antiga foi aberta, como "14 jun"
     */
    record Atraso(int quantas, String maisAntiga) {}
}
