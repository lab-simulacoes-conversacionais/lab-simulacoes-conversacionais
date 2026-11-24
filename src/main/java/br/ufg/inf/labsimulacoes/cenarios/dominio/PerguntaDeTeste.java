package br.ufg.inf.labsimulacoes.cenarios.dominio;

import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import java.util.Objects;

/**
 * Uma pergunta que o curador fez ao personagem no modo de teste, antes de aprovar (RF-09, RN-06). Não é
 * sessão de turma: não tem turma, não vira mensagem e não entra nas métricas.
 *
 * <p>A pergunta nasce {@link Situacao#PENDENTE}, reservando a vaga do teto antes de chamar a IA, e a
 * mesma pergunta recebe o resultado depois: concluída, pergunta bloqueada, resposta bloqueada ou falha.
 * Toda pergunta da rodada ocupa uma vaga do teto (RNF-13), porque qualquer tentativa gasta algo. Para o
 * RN-06 só contam as que testaram o personagem ({@link #conta()}).
 *
 * <p>Passa pelo mesmo filtro da sala (RN-20), mas o bloqueio não pausa nada nem abre caso na fila do
 * curador, porque quem testa já é o curador. A resposta bloqueada não é guardada.
 *
 * @param id {@code null} enquanto não foi reservada
 * @param versaoId a versão em curadoria que foi testada
 * @param curadorId quem testou
 * @param resposta o que o personagem respondeu; só existe na situação {@link Situacao#CONCLUIDA}
 * @param respostaIaMetaId metadados da geração (RNF-10); {@code null} enquanto pendente, na pergunta
 *     bloqueada, que não chega ao modelo, e na falha antes de o modelo responder
 * @param rodadaFechada se uma devolução já fechou a rodada desta pergunta, mesmo que ela tenha sido
 *     concluída depois; uma pergunta de rodada fechada não conta nem ocupa vaga na rodada nova
 */
public record PerguntaDeTeste(
        Long id, long versaoId, long curadorId, String pergunta, Situacao situacao, String resposta,
        Long respostaIaMetaId, boolean rodadaFechada) {

    /** O mesmo limite da pergunta da sala, definido na porta do adaptador de IA. */
    public static final int TAMANHO_MAXIMO_DA_PERGUNTA = PedidoDeFala.TAMANHO_MAXIMO_DA_PERGUNTA;

    /** Em que pé está a pergunta. */
    public enum Situacao {
        /** Reservada: a IA ainda não terminou. Uma pendente presa por queda da aplicação só ocupa uma vaga. */
        PENDENTE,
        /** A resposta passou pelo filtro e foi exibida ao curador. */
        CONCLUIDA,
        /** O filtro bloqueou a pergunta, que não chegou ao modelo. */
        PERGUNTA_BLOQUEADA,
        /** O filtro bloqueou a resposta, que não foi exibida nem guardada. */
        RESPOSTA_BLOQUEADA,
        /** O provedor ou a moderação falharam; com os metadados, se a resposta já tinha sido gerada. */
        FALHA
    }

    public PerguntaDeTeste {
        pergunta = textoDaPergunta(pergunta);
        Objects.requireNonNull(situacao, "situacao");
        resposta = resposta == null || resposta.isBlank() ? null : resposta.strip();
        if ((situacao == Situacao.CONCLUIDA) != (resposta != null)) {
            throw new IllegalArgumentException("Só a resposta exibida é guardada, e ela não pode ser vazia");
        }
        if ((situacao == Situacao.PENDENTE || situacao == Situacao.PERGUNTA_BLOQUEADA) && respostaIaMetaId != null) {
            throw new IllegalArgumentException("A pergunta " + situacao + " não tem metadados de resposta");
        }
    }

    /**
     * A pergunta como o curador digitou, sem espaços nas pontas, para recusar antes de qualquer outra
     * coisa.
     *
     * @throws IllegalArgumentException se estiver vazia ou passar de {@value #TAMANHO_MAXIMO_DA_PERGUNTA}
     *     caracteres
     */
    public static String textoDaPergunta(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Digite a pergunta de teste");
        }
        String pergunta = texto.strip();
        if (pergunta.length() > TAMANHO_MAXIMO_DA_PERGUNTA) {
            throw new IllegalArgumentException(
                    "A pergunta de teste tem no máximo " + TAMANHO_MAXIMO_DA_PERGUNTA + " caracteres");
        }
        return pergunta;
    }

    /** A reserva da vaga, antes de chamar a IA. */
    public static PerguntaDeTeste reservada(long versaoId, long curadorId, String pergunta) {
        return new PerguntaDeTeste(null, versaoId, curadorId, pergunta, Situacao.PENDENTE, null, null, false);
    }

    /**
     * A versão que o curador pode testar: a que está em curadoria, de um cenário que ele não criou e numa
     * versão que ele não escreveu, pela mesma regra de quem aprova (RN-05, RN-36).
     *
     * @return o id da versão em curadoria
     * @throws TesteNaoPermitido se a versão não estiver em curadoria ou se o curador for o autor
     */
    public static long versaoTestavel(Cenario cenario, long curadorId) {
        Objects.requireNonNull(cenario, "cenario");
        if (cenario.temAutor(curadorId) || cenario.versao().criadaPorId() == curadorId) {
            throw TesteNaoPermitido.peloAutor(cenario.id());
        }
        if (!cenario.versao().emCuradoria()) {
            throw TesteNaoPermitido.foraDeCuradoria(cenario.id(), cenario.versao().status());
        }
        return cenario.versao().id();
    }

    public PerguntaDeTeste concluida(String textoDaResposta, long metadadosId) {
        return resultado(Situacao.CONCLUIDA, textoDaResposta, metadadosId);
    }

    public PerguntaDeTeste comPerguntaBloqueada() {
        return resultado(Situacao.PERGUNTA_BLOQUEADA, null, null);
    }

    public PerguntaDeTeste comRespostaBloqueada(long metadadosId) {
        return resultado(Situacao.RESPOSTA_BLOQUEADA, null, metadadosId);
    }

    /** @param metadadosId os metadados da resposta, se ela já tinha sido gerada; senão {@code null} */
    public PerguntaDeTeste comFalha(Long metadadosId) {
        return resultado(Situacao.FALHA, null, metadadosId);
    }

    public PerguntaDeTeste comId(long novoId) {
        return new PerguntaDeTeste(novoId, versaoId, curadorId, pergunta, situacao, resposta, respostaIaMetaId,
                rodadaFechada);
    }

    /**
     * RN-06: contam as perguntas que testaram o personagem, a concluída e a de resposta bloqueada, numa
     * rodada em aberto. Pendentes, falhas e perguntas bloqueadas não contam.
     */
    public boolean conta() {
        return !rodadaFechada && (situacao == Situacao.CONCLUIDA || situacao == Situacao.RESPOSTA_BLOQUEADA);
    }

    private PerguntaDeTeste resultado(Situacao nova, String textoDaResposta, Long metadadosId) {
        if (situacao != Situacao.PENDENTE) {
            throw new IllegalStateException("A pergunta de teste " + id + " já tem resultado: " + situacao);
        }
        return new PerguntaDeTeste(id, versaoId, curadorId, pergunta, nova, textoDaResposta, metadadosId, rodadaFechada);
    }
}
