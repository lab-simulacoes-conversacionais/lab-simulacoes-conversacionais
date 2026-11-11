package br.ufg.inf.labsimulacoes.sessoes.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadosDasSessoes;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MudancaNaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessoes;

import br.ufg.inf.labsimulacoes.sessoes.dominio.TurmaComSessaoEmAndamento;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausar, retomar, reiniciar e encerrar a sessão a qualquer momento (RF-17). Só o professor que a
 * iniciou a controla (RN-10, Professor no controle).
 *
 * <p>Cada controle grava a sessão e o evento no PostgreSQL e atualiza o estado quente no Redis depois
 * da confirmação. Se a chave do Redis tiver sumido ou estiver desatualizada, o estado é refeito do
 * banco antes de receber a mudança (RNF-04).
 */
@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class ControlesDaSessao {

    private final Sessoes sessoes;
    private final EstadosDasSessoes estados;
    private final UsuarioLogado usuarioLogado;
    private final Relogio relogio;

    ControlesDaSessao(Sessoes sessoes, EstadosDasSessoes estados, UsuarioLogado usuarioLogado, Relogio relogio) {
        this.sessoes = sessoes;
        this.estados = estados;
        this.usuarioLogado = usuarioLogado;
        this.relogio = relogio;
    }

    /** O que a tela de projeção mostra; vem do Redis e, se a chave tiver sumido, do banco (RNF-04). */
    @Transactional(readOnly = true)
    public EstadoDaSessao estado(long id) {
        return estadoAtual(doProfessorLogado(sessoes.buscar(id), id));
    }

    /**
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado se já estiver pausada
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada (RN-19)
     */
    @Transactional
    public EstadoDaSessao pausar(long id) {
        return mudar(id, sessao -> sessao.pausar(MotivoDaPausa.MANUAL, relogio.agora()));
    }

    /**
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoForaDoEstado se não estiver pausada
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada (RN-19)
     * @throws TurmaComSessaoEmAndamento se, durante a pausa, outra sessão tiver começado na turma (RN-10)
     */
    @Transactional
    public EstadoDaSessao retomar(long id) {
        return mudar(id, sessao -> {
            if (sessoes.temSessaoEmAndamento(sessao.turmaId())) {
                throw new TurmaComSessaoEmAndamento(sessao.turmaId());
            }
            return sessao.retomar(relogio.agora());
        });
    }

    /**
     * Limpa a tela; perguntas e respostas continuam no registro da sessão (RN-19).
     *
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada
     */
    @Transactional
    public EstadoDaSessao reiniciar(long id) {
        return mudar(id, sessao -> sessao.reiniciar(relogio.agora()));
    }

    /**
     * Encerrada não pode ser reaberta (RN-19).
     *
     * @throws br.ufg.inf.labsimulacoes.sessoes.dominio.SessaoEncerrada se já estiver encerrada
     */
    @Transactional
    public EstadoDaSessao encerrar(long id) {
        Sessao sessao = doProfessorLogado(sessoes.buscarParaAlterar(id), id);
        EstadoDaSessao antes = estadoAtual(sessao);
        Sessao encerrada = sessoes.salvar(sessao.encerrar(relogio.agora()));
        EstadoDaSessao depois = antes.encerrar(encerrada.encerradaEm());
        estados.gravar(depois);
        return depois;
    }

    private EstadoDaSessao mudar(long id, Function<Sessao, MudancaNaSessao> controle) {
        Sessao sessao = doProfessorLogado(sessoes.buscarParaAlterar(id), id);
        EstadoDaSessao antes = estadoAtual(sessao);
        MudancaNaSessao mudanca = controle.apply(sessao);
        sessoes.salvar(mudanca.sessao());
        sessoes.registrar(id, mudanca.evento());
        EstadoDaSessao depois = antes.aplicar(mudanca.evento());
        estados.gravar(depois);
        return depois;
    }

    /**
     * O estado do Redis, se ele concordar com o banco. Uma chave que discorda, porque uma gravação no
     * Redis falhou depois da confirmação, é descartada e o estado é refeito do banco.
     */
    private EstadoDaSessao estadoAtual(Sessao sessao) {
        return estados.buscar(sessao.id())
                .filter(estado -> concorda(estado, sessao))
                .orElseGet(() -> reconstruir(sessao.id()));
    }

    private EstadoDaSessao reconstruir(long id) {
        EstadoDaSessao estado = EstadoDaSessao.reconstruir(sessoes
                .historico(id, EstadoDaSessao.MENSAGENS_GUARDADAS)
                .orElseThrow(() -> new SessaoNaoEncontrada(id)));
        estados.gravar(estado);
        return estado;
    }

    private static boolean concorda(EstadoDaSessao estado, Sessao sessao) {
        return estado.status() == sessao.status() && estado.motivoDaPausa() == sessao.motivoDaPausa();
    }

    /** RN-10: a sessão é ligada ao professor que a iniciou, e só ele a vê e controla. */
    private Sessao doProfessorLogado(Optional<Sessao> encontrada, long id) {
        Sessao sessao = encontrada.orElseThrow(() -> new SessaoNaoEncontrada(id));
        if (!sessao.conduzidaPor(usuarioLogado.id())) {
            throw new AccessDeniedException("Só o professor que iniciou a sessão a controla");
        }
        return sessao;
    }
}
