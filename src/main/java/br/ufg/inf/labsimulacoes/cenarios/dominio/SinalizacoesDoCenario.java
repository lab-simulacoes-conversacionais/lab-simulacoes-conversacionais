package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência das sinalizações que Cenários recebe da Auditoria por evento (RF-25). A
 * implementação fica na infraestrutura.
 *
 * <p>O Spring Modulith pode entregar o mesmo evento de novo, e a triagem pode chegar antes do registro
 * quando os eventos pendentes são reprocessados. Por isso as duas gravações são idempotentes: a mesma
 * sinalização é guardada uma vez só, e a triagem gravada não é desfeita nem trocada.
 */
public interface SinalizacoesDoCenario {

    /**
     * Guarda a sinalização pendente. Se ela já estiver guardada, pendente ou triada, nada muda.
     *
     * @param pendente com a situação {@link SituacaoDaSinalizacao#PENDENTE}
     * @return se foi guardada agora
     * @throws IllegalArgumentException se a sinalização já vier triada
     */
    boolean registrar(SinalizacaoDaVersao pendente);

    /**
     * Guarda a triagem. Se o registro ainda não tiver chegado, guarda a sinalização já triada, com o
     * {@code recebidaEm} dela; se a sinalização já tiver sido triada, nada muda.
     *
     * @param triada confirmada ou descartada
     * @return se a triagem foi guardada agora
     * @throws IllegalArgumentException se a sinalização vier pendente
     */
    boolean triar(SinalizacaoDaVersao triada);

    /** As sinalizações de uma versão, na ordem em que foram feitas. */
    List<SinalizacaoDaVersao> daVersao(long versaoId);

    /** O cenário de uma versão, em qualquer status. Vazio se a versão não existir. */
    Optional<Long> cenarioDaVersao(long versaoId);

    /**
     * A versão aprovada vigente do cenário, qualquer que seja o status dele: o suspenso e o arquivado
     * continuam com a vigente que tinham. Vazio se o cenário não existir ou nunca tiver sido aprovado.
     */
    Optional<Long> versaoVigente(long cenarioId);
}
