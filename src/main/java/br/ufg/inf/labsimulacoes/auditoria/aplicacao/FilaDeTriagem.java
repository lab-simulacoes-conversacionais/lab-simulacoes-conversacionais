package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoPendente;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacoes;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fila do curador para triar as sinalizações (RF-24), com as pendências fora do prazo de 2 dias úteis em
 * destaque (RN-24).
 *
 * <ul>
 *   <li>O curador vê a fila inteira, com a pergunta, a resposta e a justificativa (RN-37).
 *   <li>O curador e o administrador veem o resumo do prazo, sem os textos: o administrador acompanha as
 *       pendências atrasadas, mas não o conteúdo pedagógico (RN-24, RN-37).
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class FilaDeTriagem {

    private final Sinalizacoes sinalizacoes;
    private final RelogioDaAuditoria relogio;
    private final ZoneId fuso;

    FilaDeTriagem(
            Sinalizacoes sinalizacoes, RelogioDaAuditoria relogio, @Value("${auditoria.triagem.fuso}") ZoneId fuso) {
        this.sinalizacoes = sinalizacoes;
        this.relogio = relogio;
        this.fuso = fuso;
    }

    /** As sinalizações pendentes, da mais antiga, cada uma com o prazo e se está em destaque. */
    @PreAuthorize("hasRole('CURADOR')")
    public List<ItemDaFila> pendentes() {
        Instant agora = relogio.agora();
        return sinalizacoes.pendentes().stream()
                .map(pendente -> new ItemDaFila(
                        pendente, pendente.limiteDaTriagem(fuso), pendente.foraDoPrazo(agora, fuso)))
                .toList();
    }

    /** Quantas sinalizações aguardam a triagem e quais passaram do prazo, da mais antiga. */
    @PreAuthorize("hasAnyRole('CURADOR', 'ADMINISTRADOR')")
    public ResumoDoPrazo resumoDoPrazo() {
        Instant agora = relogio.agora();
        List<SinalizacaoPendente> pendentes = sinalizacoes.pendentes();
        return new ResumoDoPrazo(
                pendentes.size(),
                pendentes.stream()
                        .filter(pendente -> pendente.foraDoPrazo(agora, fuso))
                        .map(pendente -> new PendenciaForaDoPrazo(
                                pendente.id(), pendente.tipo(), pendente.criadaEm(), pendente.limiteDaTriagem(fuso)))
                        .toList());
    }

    /**
     * Uma sinalização da fila.
     *
     * @param limite até quando o curador tem para triar
     * @param foraDoPrazo se passou do limite e fica em destaque
     */
    public record ItemDaFila(SinalizacaoPendente sinalizacao, Instant limite, boolean foraDoPrazo) {}

    /**
     * O resumo do prazo, para o destaque do curador e do administrador.
     *
     * @param pendentes quantas sinalizações aguardam a triagem
     * @param foraDoPrazo as que passaram dos 2 dias úteis, da mais antiga
     */
    public record ResumoDoPrazo(int pendentes, List<PendenciaForaDoPrazo> foraDoPrazo) {

        public ResumoDoPrazo {
            foraDoPrazo = List.copyOf(foraDoPrazo);
        }

        public boolean temAtrasadas() {
            return !foraDoPrazo.isEmpty();
        }
    }

    /** Uma pendência atrasada, sem a pergunta, a resposta nem a justificativa. */
    public record PendenciaForaDoPrazo(long sinalizacaoId, TipoDeErro tipo, Instant criadaEm, Instant limite) {}
}
