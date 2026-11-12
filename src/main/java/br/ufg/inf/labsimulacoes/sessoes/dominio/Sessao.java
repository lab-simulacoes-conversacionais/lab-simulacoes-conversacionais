package br.ufg.inf.labsimulacoes.sessoes.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Sessão coletiva em sala (RF-14, RF-17). Fica ligada ao professor, à turma e à versão do cenário,
 * nunca ao cenário genérico (RN-10, Stack, seção 6). Cada controle é um método que confere o estado
 * antes de mudar, para que cada regra tenha um teste próprio, sem banco nem tela.
 *
 * @param id {@code null} enquanto não foi gravada
 * @param versaoId versão aprovada vigente no início da sessão (RN-01)
 * @param limiteDePerguntas quantas perguntas da turma a sessão aceita, lido dos parâmetros no início e
 *     guardado, para uma alteração do administrador não mudar uma aula em andamento (RN-18)
 * @param motivoDaPausa presente só quando a sessão está pausada
 * @param encerradaEm presente só quando a sessão está encerrada
 */
public record Sessao(
        Long id,
        long professorId,
        long turmaId,
        long versaoId,
        int limiteDePerguntas,
        StatusDaSessao status,
        MotivoDaPausa motivoDaPausa,
        Instant iniciadaEm,
        Instant encerradaEm) {

    public Sessao {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(iniciadaEm, "iniciadaEm");
        if (limiteDePerguntas < 1) {
            throw new IllegalArgumentException("A sessão precisa aceitar ao menos uma pergunta: " + limiteDePerguntas);
        }
        if ((status == StatusDaSessao.PAUSADA) != (motivoDaPausa != null)) {
            throw new IllegalArgumentException("Só a sessão pausada tem motivo de pausa, e ela sempre tem");
        }
        if ((status == StatusDaSessao.ENCERRADA) != (encerradaEm != null)) {
            throw new IllegalArgumentException("Só a sessão encerrada tem data de encerramento, e ela sempre tem");
        }
    }

    /** A sessão começa em andamento (RF-14). */
    public static Sessao iniciar(long professorId, long turmaId, long versaoId, int limiteDePerguntas, Instant em) {
        return new Sessao(
                null, professorId, turmaId, versaoId, limiteDePerguntas, StatusDaSessao.EM_ANDAMENTO, null, em, null);
    }

    public Sessao comId(long novoId) {
        return new Sessao(
                novoId, professorId, turmaId, versaoId, limiteDePerguntas, status, motivoDaPausa, iniciadaEm, encerradaEm);
    }

    /** RN-10: a sessão é do professor que a iniciou, e só ele a controla. */
    public boolean conduzidaPor(long usuarioId) {
        return professorId == usuarioId;
    }

    /**
     * Confere se a turma pode fazer mais uma pergunta: só com a sessão em andamento, depois de a anterior
     * ser respondida e até o limite guardado no início (RN-18). Reiniciar não zera a contagem (RN-19).
     *
     * @param estado o estado atual da sessão, com as perguntas feitas e a tela
     * @throws SessaoEncerrada se a sessão já terminou (RN-19)
     * @throws SessaoForaDoEstado se ela estiver pausada
     * @throws PerguntaSemResposta se a pergunta anterior ainda espera a resposta
     * @throws LimiteDePerguntasAtingido se a turma já fez todas as perguntas (RN-18)
     */
    public void aceitarPergunta(EstadoDaSessao estado) {
        exigir(StatusDaSessao.EM_ANDAMENTO, "enviar pergunta para");
        if (estado.perguntaPendente().isPresent()) {
            throw new PerguntaSemResposta(id);
        }
        if (perguntasRestantes(estado.perguntasDaTurma()) == 0) {
            throw new LimiteDePerguntasAtingido(id, limiteDePerguntas);
        }
    }

    /** RN-18: quantas perguntas a turma ainda pode fazer; zero quando é hora de sugerir o quiz. */
    public int perguntasRestantes(int perguntasFeitas) {
        return Math.max(0, limiteDePerguntas - perguntasFeitas);
    }

    /**
     * O personagem só responde com a sessão em andamento: depois de uma pausa, quem decide continuar é o
     * professor (RN-17, RN-20).
     *
     * @throws SessaoEncerrada se a sessão já terminou (RN-19)
     * @throws SessaoForaDoEstado se ela estiver pausada
     */
    public void aceitarResposta() {
        exigir(StatusDaSessao.EM_ANDAMENTO, "responder na");
    }

    /**
     * @throws SessaoEncerrada se a sessão já terminou (RN-19)
     * @throws SessaoForaDoEstado se ela já estiver pausada
     */
    public MudancaNaSessao pausar(MotivoDaPausa motivo, Instant em) {
        Objects.requireNonNull(motivo, "motivo");
        exigir(StatusDaSessao.EM_ANDAMENTO, "pausar");
        return new MudancaNaSessao(comStatus(StatusDaSessao.PAUSADA, motivo, null), EventoDaSessao.pausa(motivo, em));
    }

    /**
     * O filtro de conteúdo bloqueou uma pergunta ou uma frase da resposta (RN-20). A sessão em andamento
     * pausa com o motivo {@link MotivoDaPausa#CONTEUDO_BLOQUEADO}, e quem retoma é o professor. A que já
     * foi pausada ou encerrada enquanto a pergunta era moderada ou a resposta chegava fica como está: o
     * caso vai ao curador do mesmo jeito.
     *
     * @return a pausa, ou vazio se a sessão não estava em andamento
     */
    public Optional<MudancaNaSessao> bloquearConteudo(Instant em) {
        return status == StatusDaSessao.EM_ANDAMENTO
                ? Optional.of(pausar(MotivoDaPausa.CONTEUDO_BLOQUEADO, em))
                : Optional.empty();
    }

    /**
     * Vale para qualquer motivo de pausa: depois de um bloqueio, do limite de 50 minutos ou de uma
     * suspensão, quem decide continuar é o professor (RN-17, RN-20, RN-25).
     *
     * @throws SessaoEncerrada se a sessão já terminou (RN-19)
     * @throws SessaoForaDoEstado se ela não estiver pausada
     */
    public MudancaNaSessao retomar(Instant em) {
        exigir(StatusDaSessao.PAUSADA, "retomar");
        return new MudancaNaSessao(comStatus(StatusDaSessao.EM_ANDAMENTO, null, null), EventoDaSessao.retomada(em));
    }

    /**
     * Não muda o estado: marca o ponto de corte a partir do qual a tela e o histórico enviado à IA
     * recomeçam. O que já aconteceu continua no registro da sessão (RN-19).
     *
     * @throws SessaoEncerrada se a sessão já terminou
     */
    public MudancaNaSessao reiniciar(Instant em) {
        exigirAberta("reiniciar");
        return new MudancaNaSessao(this, EventoDaSessao.reinicio(em));
    }

    /**
     * Encerra a sessão, pausada ou não. Encerrada não pode ser reaberta: para continuar o tema,
     * inicia-se outra sessão (RN-19).
     *
     * @throws SessaoEncerrada se ela já tiver sido encerrada
     */
    public Sessao encerrar(Instant em) {
        Objects.requireNonNull(em, "em");
        exigirAberta("encerrar");
        return comStatus(StatusDaSessao.ENCERRADA, null, em);
    }

    private void exigir(StatusDaSessao esperado, String acao) {
        exigirAberta(acao);
        if (status != esperado) {
            throw new SessaoForaDoEstado(id, acao, status);
        }
    }

    private void exigirAberta(String acao) {
        if (status == StatusDaSessao.ENCERRADA) {
            throw new SessaoEncerrada(id, acao);
        }
    }

    private Sessao comStatus(StatusDaSessao novo, MotivoDaPausa motivo, Instant encerramento) {
        return new Sessao(id, professorId, turmaId, versaoId, limiteDePerguntas, novo, motivo, iniciadaEm, encerramento);
    }
}
