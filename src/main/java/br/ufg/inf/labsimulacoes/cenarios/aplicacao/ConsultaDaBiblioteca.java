package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.BibliotecaDeCenarios;
import br.ufg.inf.labsimulacoes.cenarios.CenarioDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.FiltroDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.FiltrosDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.PaginaDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscaNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.OpcoesDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.util.Objects;
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
    public PaginaDaBiblioteca listar(FiltroDaBiblioteca filtro, int pagina, int tamanho) {
        Objects.requireNonNull(filtro, "filtro");
        var pedida = new Pagina(pagina, tamanho);
        var busca = new BuscaNaBiblioteca(filtro.texto(), filtro.componente(), filtro.ano(), filtro.habilidade());
        TrechoDaBiblioteca trecho = cenarios.listarAprovados(busca, pedida);
        return new PaginaDaBiblioteca(
                trecho.itens().stream().map(ConsultaDaBiblioteca::paraApi).toList(),
                pedida.numero(),
                pedida.tamanho(),
                trecho.temProxima());
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDaBiblioteca listar(int pagina, int tamanho) {
        return listar(FiltroDaBiblioteca.NENHUM, pagina, tamanho);
    }

    @Override
    @Transactional(readOnly = true)
    public FiltrosDaBiblioteca filtrosDisponiveis() {
        OpcoesDaBiblioteca opcoes = cenarios.opcoesDaBiblioteca();
        return new FiltrosDaBiblioteca(opcoes.componentes(), opcoes.anos(), opcoes.habilidades());
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
