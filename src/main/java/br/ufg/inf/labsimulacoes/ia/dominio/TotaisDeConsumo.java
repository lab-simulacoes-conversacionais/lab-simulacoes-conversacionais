package br.ufg.inf.labsimulacoes.ia.dominio;

import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import java.time.Instant;

/** Porta de leitura dos totais de consumo nos metadados das respostas (RNF-13). */
public interface TotaisDeConsumo {

    /** @param inicio incluído; @param fim excluído */
    ResumoDoConsumo entre(Instant inicio, Instant fim);

    ResumoDoConsumo daReferencia(FinalidadeDoConsumo finalidade, long referenciaId);
}
