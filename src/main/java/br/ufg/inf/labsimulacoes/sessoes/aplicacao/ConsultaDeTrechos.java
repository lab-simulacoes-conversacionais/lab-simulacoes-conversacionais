package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.TurmaDaEscola;
import br.ufg.inf.labsimulacoes.acesso.TurmasDaEscola;
import br.ufg.inf.labsimulacoes.cenarios.CenariosAprovados;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.MensagemDaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.SessaoParaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.TrechoParaSinalizar;
import br.ufg.inf.labsimulacoes.sessoes.TrechosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.ConversasGravadas;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As conversas gravadas, para a sinalização de erro e a revisão (RF-23). Não confere de quem é a sessão:
 * quem chama decide, porque o curador vê todas (RN-37).
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
@Transactional(readOnly = true)
class ConsultaDeTrechos implements TrechosDasSessoes {

    private final Sessoes sessoes;
    private final ConversasGravadas conversas;
    private final CenariosAprovados aprovados;
    private final TurmasDaEscola turmas;

    ConsultaDeTrechos(
            Sessoes sessoes, ConversasGravadas conversas, CenariosAprovados aprovados, TurmasDaEscola turmas) {
        this.sessoes = sessoes;
        this.conversas = conversas;
        this.aprovados = aprovados;
        this.turmas = turmas;
    }

    @Override
    public Optional<SessaoParaRevisao> sessao(long sessaoId) {
        return sessoes.buscar(sessaoId).map(this::paraRevisao);
    }

    @Override
    public List<MensagemDaRevisao> mensagens(long sessaoId) {
        return conversas.mensagens(sessaoId);
    }

    @Override
    public Optional<TrechoParaSinalizar> resposta(long mensagemId) {
        return conversas.resposta(mensagemId);
    }

    @Override
    public List<SessaoParaRevisao> doProfessorDesde(long professorId, Instant desde) {
        Objects.requireNonNull(desde, "desde");
        return conversas.sessoesDoProfessorDesde(professorId, desde).stream()
                .map(sessoes::buscar)
                .flatMap(Optional::stream)
                .map(this::paraRevisao)
                .toList();
    }

    /** O título vem da versão com que a sessão começou (RN-08), e a turma, do cadastro. */
    private SessaoParaRevisao paraRevisao(Sessao sessao) {
        String titulo = aprovados.versaoAprovada(sessao.versaoId()).map(VersaoAprovada::titulo).orElse("Cenário");
        String turma = turmas.buscar(sessao.turmaId()).map(TurmaDaEscola::rotulo).orElse("Turma removida");
        return new SessaoParaRevisao(
                sessao.id(), sessao.professorId(), sessao.versaoId(), titulo, turma, sessao.iniciadaEm(),
                sessao.encerradaEm());
    }
}
