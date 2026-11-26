package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * O que a sinalização copia da conversa no momento em que é criada (Stack, seção 6): a pergunta e a
 * resposta inteiras, os dados da sessão e os metadados da resposta. As referências podem ficar nulas
 * depois, quando a retenção apaga a mensagem, os metadados ou a sessão (RN-33); as cópias continuam.
 *
 * @param sessaoId a sessão; nula só depois da retenção
 * @param mensagemId a resposta da IA sinalizada; nula só depois da retenção
 * @param respostaIaMetaId os metadados da resposta; nulo numa resposta antiga gravada sem eles e depois
 *     da retenção
 * @param metadados cópia do provedor, do modelo e da versão do prompt; nula só quando a resposta não tinha
 *     metadados
 */
public record TrechoSinalizado(
        Long sessaoId,
        Instant sessaoIniciadaEm,
        long sessaoProfessorId,
        long versaoId,
        Long mensagemId,
        Long respostaIaMetaId,
        MetadadosCopiados metadados,
        String pergunta,
        String resposta,
        Instant respondidaEm) {

    public TrechoSinalizado {
        Objects.requireNonNull(sessaoIniciadaEm, "sessaoIniciadaEm");
        Objects.requireNonNull(pergunta, "pergunta");
        Objects.requireNonNull(resposta, "resposta");
        Objects.requireNonNull(respondidaEm, "respondidaEm");
        // Depois da retenção sobra a cópia sem a referência; o contrário nunca acontece.
        if (respostaIaMetaId != null && metadados == null) {
            throw new IllegalArgumentException("A referência aos metadados vem com a cópia deles");
        }
    }

    /**
     * Provedor, modelo e versão do prompt da resposta, copiados para a sinalização continuar completa
     * depois que a retenção apagar os metadados (RNF-10).
     */
    public record MetadadosCopiados(String provedor, String modelo, String versaoPrompt) {

        public MetadadosCopiados {
            Objects.requireNonNull(provedor, "provedor");
            Objects.requireNonNull(modelo, "modelo");
            Objects.requireNonNull(versaoPrompt, "versaoPrompt");
        }
    }
}
