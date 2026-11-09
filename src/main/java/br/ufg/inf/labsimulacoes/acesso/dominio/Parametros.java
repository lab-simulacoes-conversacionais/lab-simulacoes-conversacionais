package br.ufg.inf.labsimulacoes.acesso.dominio;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parâmetros de uso definidos pelo administrador (RF-04, RN-37).
 *
 * @param limiteDePerguntasPorSessao quantas perguntas da turma cada sessão aceita (RN-18)
 * @param custoDiarioMaximoUsd custo de IA por dia, em dólares, acima do qual o administrador é
 *     avisado (RNF-13); {@code null} enquanto não for definido, e então não há aviso
 */
public record Parametros(int limiteDePerguntasPorSessao, BigDecimal custoDiarioMaximoUsd) {

    /** RN-18: mantém o ritmo da aula e o custo previsível. */
    public static final int LIMITE_DE_PERGUNTAS_PADRAO = 40;

    public Parametros {
        if (limiteDePerguntasPorSessao < 1) {
            throw new IllegalArgumentException("O limite de perguntas por sessão precisa ser de pelo menos 1");
        }
        if (custoDiarioMaximoUsd != null) {
            if (custoDiarioMaximoUsd.signum() <= 0) {
                throw new IllegalArgumentException("O custo diário máximo precisa ser maior que zero");
            }
            // Sem isto, 5.0 e 5.00 seriam valores diferentes. A escala mínima 0 evita que 50 vire 5E+1.
            BigDecimal semZeros = custoDiarioMaximoUsd.stripTrailingZeros();
            custoDiarioMaximoUsd = semZeros.scale() < 0 ? semZeros.setScale(0) : semZeros;
        }
    }

    /** Vale enquanto o administrador não muda nada. */
    public static Parametros padrao() {
        return new Parametros(LIMITE_DE_PERGUNTAS_PADRAO, null);
    }

    public Optional<BigDecimal> custoDiarioMaximo() {
        return Optional.ofNullable(custoDiarioMaximoUsd);
    }
}
