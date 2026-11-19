package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Objects;

/**
 * Uma pergunta que o curador fez ao personagem no modo de teste, antes de aprovar (RF-09, RN-06). Não é
 * sessão de turma: não tem turma, não vira mensagem e não entra nas métricas.
 *
 * <p>Passa pelo mesmo filtro da sala (RN-20), mas o bloqueio não pausa nada nem abre caso na fila do
 * curador, porque quem testa já é o curador. A pergunta bloqueada fica registrada sem resposta e não
 * conta para aprovar; a resposta bloqueada não é guardada, mas a pergunta conta, porque testou os
 * limites do cenário e consumiu tokens.
 *
 * @param id {@code null} enquanto não foi gravada
 * @param versaoId a versão em curadoria que foi testada
 * @param curadorId quem testou
 * @param resposta o que o personagem respondeu; só existe no desfecho {@link Desfecho#EXIBIDA}
 * @param respostaIaMetaId metadados da geração (RNF-10); {@code null} na pergunta bloqueada, que não
 *     chega ao modelo
 */
public record PerguntaDeTeste(
        Long id, long versaoId, long curadorId, String pergunta, Desfecho desfecho, String resposta,
        Long respostaIaMetaId) {

    /** O que aconteceu com a pergunta. */
    public enum Desfecho {
        /** A resposta passou pelo filtro e foi exibida ao curador. */
        EXIBIDA,
        /** O filtro bloqueou a pergunta, que não chegou ao modelo. */
        PERGUNTA_BLOQUEADA,
        /** O filtro bloqueou a resposta, que não foi exibida nem guardada. */
        RESPOSTA_BLOQUEADA
    }

    public PerguntaDeTeste {
        if (pergunta == null || pergunta.isBlank()) {
            throw new IllegalArgumentException("A pergunta de teste é obrigatória");
        }
        pergunta = pergunta.strip();
        Objects.requireNonNull(desfecho, "desfecho");
        resposta = resposta == null || resposta.isBlank() ? null : resposta.strip();
        if ((desfecho == Desfecho.EXIBIDA) != (resposta != null)) {
            throw new IllegalArgumentException("Só a resposta exibida é guardada, e ela não pode ser vazia");
        }
        if (desfecho == Desfecho.PERGUNTA_BLOQUEADA && respostaIaMetaId != null) {
            throw new IllegalArgumentException("A pergunta bloqueada não chega ao modelo, então não tem metadados");
        }
    }

    public static PerguntaDeTeste exibida(
            long versaoId, long curadorId, String pergunta, String resposta, long respostaIaMetaId) {
        return new PerguntaDeTeste(null, versaoId, curadorId, pergunta, Desfecho.EXIBIDA, resposta, respostaIaMetaId);
    }

    public static PerguntaDeTeste comPerguntaBloqueada(long versaoId, long curadorId, String pergunta) {
        return new PerguntaDeTeste(null, versaoId, curadorId, pergunta, Desfecho.PERGUNTA_BLOQUEADA, null, null);
    }

    public static PerguntaDeTeste comRespostaBloqueada(
            long versaoId, long curadorId, String pergunta, long respostaIaMetaId) {
        return new PerguntaDeTeste(
                null, versaoId, curadorId, pergunta, Desfecho.RESPOSTA_BLOQUEADA, null, respostaIaMetaId);
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

    /**
     * Na hora de gravar, a versão ainda é a testada: em curadoria, com o mesmo id e o mesmo conteúdo. A
     * devolução volta a mesma versão a rascunho (RN-07), então só o id não basta: devolvida, editada e
     * reenviada durante a resposta, ela teria o mesmo id e outro conteúdo.
     *
     * @throws TesteNaoPermitido se a versão saiu da curadoria ou mudou desde a pergunta
     */
    public static void exigirAindaTestavel(
            Cenario atual, long curadorId, long versaoId, ConteudoDoCenario conteudoTestado) {
        if (versaoTestavel(atual, curadorId) != versaoId || !atual.versao().conteudo().equals(conteudoTestado)) {
            throw TesteNaoPermitido.versaoMudou(atual.id());
        }
    }

    public PerguntaDeTeste comId(long novoId) {
        return new PerguntaDeTeste(novoId, versaoId, curadorId, pergunta, desfecho, resposta, respostaIaMetaId);
    }

    /** RN-06: a pergunta bloqueada não conta entre as perguntas de teste para aprovar. */
    public boolean conta() {
        return desfecho != Desfecho.PERGUNTA_BLOQUEADA;
    }
}
