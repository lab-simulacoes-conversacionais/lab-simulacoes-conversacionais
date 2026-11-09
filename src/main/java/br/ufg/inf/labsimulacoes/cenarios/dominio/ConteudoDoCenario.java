package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;

/**
 * O que o autor informa em uma versão do cenário (RF-06).
 *
 * <p>O rascunho pode estar incompleto, mas precisa de título para ser identificado na lista. A
 * duração, quando informada, fica sempre entre 10 e 30 minutos (RN-17).
 *
 * @param duracaoPrevistaMin em minutos
 */
public record ConteudoDoCenario(
        String titulo,
        RoteiroDoCenario roteiro,
        VinculoCurricular curriculo,
        String faixaEtaria,
        Integer duracaoPrevistaMin) {

    public static final int DURACAO_MINIMA_MIN = 10;
    public static final int DURACAO_MAXIMA_MIN = 30;

    public ConteudoDoCenario {
        titulo = Textos.opcional(titulo);
        if (titulo == null) {
            throw new IllegalArgumentException("O título do cenário é obrigatório");
        }
        roteiro = roteiro == null ? RoteiroDoCenario.vazio() : roteiro;
        curriculo = curriculo == null ? VinculoCurricular.vazio() : curriculo;
        faixaEtaria = Textos.opcional(faixaEtaria);
        if (duracaoPrevistaMin != null
                && (duracaoPrevistaMin < DURACAO_MINIMA_MIN || duracaoPrevistaMin > DURACAO_MAXIMA_MIN)) {
            throw new IllegalArgumentException("A duração prevista precisa estar entre "
                    + DURACAO_MINIMA_MIN + " e " + DURACAO_MAXIMA_MIN + " minutos");
        }
    }

    /** O que ainda falta para ir à curadoria (RN-02, RN-03). Vazio quando o conteúdo está completo. */
    public List<PendenciaDoCenario> pendencias() {
        return PendenciaDoCenario.de(this);
    }
}
