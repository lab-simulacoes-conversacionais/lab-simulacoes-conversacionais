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

    static TesteNaoPermitido versaoMudou(Long cenarioId) {
        return new TesteNaoPermitido(
                "A versão do cenário " + cenarioId + " mudou durante o teste; a resposta foi descartada");
    }
}
