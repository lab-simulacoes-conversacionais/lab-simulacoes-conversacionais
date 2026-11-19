package br.ufg.inf.labsimulacoes.cenarios.dominio;

/** O curador não pode testar este cenário agora (RF-09, RN-05, RN-36). */
public class TesteNaoPermitido extends RuntimeException {

    private TesteNaoPermitido(String mensagem) {
        super(mensagem);
    }

    static TesteNaoPermitido peloAutor(Long cenarioId) {
        return new TesteNaoPermitido(
                "O cenário " + cenarioId + " é seu: quem testa antes de aprovar é outro curador (RN-05, RN-36)");
    }

    static TesteNaoPermitido foraDeCuradoria(Long cenarioId, StatusDaVersao status) {
        return new TesteNaoPermitido(
                "O cenário " + cenarioId + " só pode ser testado em curadoria, e sua versão está em " + status);
    }

    /**
     * A devolução fechou a rodada enquanto a resposta era gerada. A pergunta fica guardada na rodada
     * fechada, e a resposta não é mostrada como se fosse da rodada nova.
     */
    public static TesteNaoPermitido rodadaFechada(long versaoId) {
        return new TesteNaoPermitido("A versão " + versaoId + " foi devolvida durante a resposta: a pergunta"
                + " ficou na rodada de teste anterior e não conta para a aprovação. Pergunte de novo.");
    }
}
