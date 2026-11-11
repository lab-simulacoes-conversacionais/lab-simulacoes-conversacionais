package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenarioDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.PaginaDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Biblioteca de cenários aprovados (RF-12). O professor vê a biblioteca aprovada da escola, e o
 * curador também; o administrador não usa conteúdo pedagógico (RN-37).
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class ConsultaDaBiblioteca implements BibliotecaDeCenarios {

    private final Cenarios cenarios;

    ConsultaDaBiblioteca(Cenarios cenarios) {
        this.cenarios = cenarios;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDaBiblioteca listar(int pagina, int tamanho) {
        var pedida = new Pagina(pagina, tamanho);
        TrechoDaBiblioteca trecho = cenarios.listarAprovados(pedida);
        return new PaginaDaBiblioteca(
                trecho.itens().stream().map(ConsultaDaBiblioteca::paraApi).toList(),
                pedida.numero(),
                pedida.tamanho(),
                trecho.temProxima());
    }

    private static CenarioDaBiblioteca paraApi(CenarioNaBiblioteca item) {
        VersaoDoCenario versao = item.versao();
        ConteudoDoCenario conteudo = versao.conteudo();
        VinculoCurricular curriculo = conteudo.curriculo();
        return new CenarioDaBiblioteca(
                item.cenarioId(),
                versao.id(),
                versao.numero(),
                conteudo.titulo(),
                curriculo.componente(),
                curriculo.etapa(),
                curriculo.ano(),
                curriculo.habilidades(),
                conteudo.faixaEtaria(),
                conteudo.duracaoPrevistaMin(),
                versao.aprovacao().em());
    }
}
