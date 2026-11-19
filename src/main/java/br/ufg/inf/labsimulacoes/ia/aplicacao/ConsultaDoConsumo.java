package br.ufg.inf.labsimulacoes.ia.aplicacao;

import br.ufg.inf.labsimulacoes.ia.ConsumoDaIa;
import br.ufg.inf.labsimulacoes.ia.FinalidadeDoConsumo;
import br.ufg.inf.labsimulacoes.ia.ResumoDoConsumo;
import br.ufg.inf.labsimulacoes.ia.dominio.TotaisDeConsumo;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do consumo da IA pelos outros módulos (RNF-13). É de pacote e não confere perfil, como a
 * leitura dos parâmetros no módulo de acesso: a conferência agendada do custo do dia roda sem login, e a
 * tela que mostra os totais confere o perfil do administrador.
 */
@Service
class ConsultaDoConsumo implements ConsumoDaIa {

    private final TotaisDeConsumo totais;

    ConsultaDoConsumo(TotaisDeConsumo totais) {
        this.totais = totais;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumoDoConsumo custoEntre(Instant inicio, Instant fim) {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O início precisa vir antes do fim: " + inicio + " e " + fim);
        }
        return totais.entre(inicio, fim);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumoDoConsumo daReferencia(FinalidadeDoConsumo finalidade, long referenciaId) {
        Objects.requireNonNull(finalidade, "finalidade");
        return totais.daReferencia(finalidade, referenciaId);
    }
}
