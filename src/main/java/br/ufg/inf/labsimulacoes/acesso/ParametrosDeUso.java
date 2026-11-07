package br.ufg.inf.labsimulacoes.acesso;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parâmetros definidos pelo administrador, para os outros módulos (RF-04).
 *
 * <p>Cada chamada lê o valor atual no banco, sem cache. Quem usa um parâmetro durante a sessão deve
 * lê-lo uma vez, ao iniciá-la, e guardar o valor na sessão: assim, uma alteração do administrador
 * vale para as próximas sessões e não muda as regras de uma aula em andamento.
 */
public interface ParametrosDeUso {

    /** Quantas perguntas da turma cada sessão aceita (RN-18). O padrão é 40. */
    int limiteDePerguntasPorSessao();

    /**
     * Custo de IA por dia, em dólares, acima do qual o administrador é avisado (RNF-13). Vazio
     * enquanto o administrador não definir um valor, e então não há aviso.
     */
    Optional<BigDecimal> custoDiarioMaximoUsd();
}
