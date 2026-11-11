package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versão aprovada vigente para os outros módulos (RN-01). Quem inicia sessão é o professor (RF-14);
 * o curador também lê, porque vê todos os cenários (RN-37).
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class ConsultaDaVersaoVigente implements CenariosAprovados {

    private final Cenarios cenarios;

    ConsultaDaVersaoVigente(Cenarios cenarios) {
        this.cenarios = cenarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VersaoAprovada> versaoVigente(long cenarioId) {
        return cenarios.versaoVigente(cenarioId).map(versao -> paraApi(cenarioId, versao));
    }

    private static VersaoAprovada paraApi(long cenarioId, VersaoDoCenario versao) {
        ConteudoDoCenario conteudo = versao.conteudo();
        RoteiroDoCenario roteiro = conteudo.roteiro();
        VinculoCurricular curriculo = conteudo.curriculo();
        return new VersaoAprovada(
                cenarioId,
                versao.id(),
                versao.numero(),
                conteudo.titulo(),
                roteiro.personagem(),
                roteiro.papelDaIa(),
                roteiro.contexto(),
                semNulo(roteiro.objetivos()),
                semNulo(roteiro.limitesDeAssunto()),
                semNulo(roteiro.fontes()),
                semNulo(roteiro.perguntasDisparadoras()),
                curriculo.componente(),
                curriculo.etapa(),
                curriculo.ano(),
                semNulo(curriculo.habilidades()),
                conteudo.faixaEtaria(),
                conteudo.duracaoPrevistaMin(),
                versao.aprovacao().em());
    }

    private static List<String> semNulo(List<String> lista) {
        return lista == null ? List.of() : lista;
    }
}
