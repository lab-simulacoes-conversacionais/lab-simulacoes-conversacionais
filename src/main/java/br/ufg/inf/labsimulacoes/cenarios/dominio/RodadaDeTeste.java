package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.Objects;

/**
 * A rodada em aberto de um curador numa versão em curadoria (RF-09), como a tela do modo de teste mostra:
 * as perguntas desde a última devolução e as duas contagens, a do RN-06 e a do teto (RNF-13).
 *
 * @param personagem quem responde, como está na versão testada
 * @param perguntas da mais antiga para a mais nova, em qualquer situação
 * @param teto quantas perguntas cabem na rodada ({@link LimiteDoTeste})
 */
public record RodadaDeTeste(String personagem, List<PerguntaDeTeste> perguntas, int teto) {

    public RodadaDeTeste {
        perguntas = List.copyOf(Objects.requireNonNull(perguntas, "perguntas"));
    }

    /** RN-06: as que testaram o personagem, concluídas ou com a resposta bloqueada. */
    public int contam() {
        return (int) perguntas.stream().filter(PerguntaDeTeste::conta).count();
    }

    /** Quantas o RN-06 exige para aprovar. */
    public int minimo() {
        return PerguntasDeTeste.MINIMO_PARA_APROVAR;
    }

    /** Quantas ainda faltam para o mínimo do RN-06; zero quando já chegou. */
    public int faltam() {
        return Math.max(0, minimo() - contam());
    }

    /** RNF-13: todas ocupam vaga do teto, porque qualquer tentativa gasta algo do provedor. */
    public int ocupadas() {
        return perguntas.size();
    }

    public boolean noTeto() {
        return ocupadas() >= teto;
    }
}
