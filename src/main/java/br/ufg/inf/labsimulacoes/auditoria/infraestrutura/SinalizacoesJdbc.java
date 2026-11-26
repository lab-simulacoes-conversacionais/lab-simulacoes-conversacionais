package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacoes;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TrechoSinalizado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sinalizações na tabela sinalizacao, com os textos e os metadados copiados. */
@Repository
class SinalizacoesJdbc implements Sinalizacoes {

    /**
     * Uma sinalização repetida não grava nada nem aborta a transação; a restrição única garante isso
     * também com dois pedidos ao mesmo tempo.
     */
    private static final String REGISTRAR = """
            INSERT INTO sinalizacao (sessao_id, sessao_iniciada_em, sessao_professor_id, cenario_versao_id,
                                     mensagem_id, resposta_ia_meta_id, metadados_da_resposta, pergunta, resposta,
                                     respondida_em, tipo, gravidade_percebida, justificativa, sinalizado_por_id,
                                     perfil, status, criada_em)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uq_sinalizacao_mensagem_por_pessoa DO NOTHING
            RETURNING id""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    SinalizacoesJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Long> registrar(Sinalizacao sinalizacao) {
        TrechoSinalizado trecho = sinalizacao.trecho();
        return jdbc.queryForList(REGISTRAR, Long.class,
                        trecho.sessaoId(),
                        momento(trecho.sessaoIniciadaEm()),
                        trecho.sessaoProfessorId(),
                        trecho.versaoId(),
                        trecho.mensagemId(),
                        trecho.respostaIaMetaId(),
                        paraJson(trecho.metadados()),
                        trecho.pergunta(),
                        trecho.resposta(),
                        momento(trecho.respondidaEm()),
                        sinalizacao.tipo().name(),
                        sinalizacao.gravidade().name(),
                        sinalizacao.justificativa(),
                        sinalizacao.sinalizadoPorId(),
                        sinalizacao.perfil().name(),
                        Sinalizacao.PENDENTE,
                        momento(sinalizacao.criadaEm()))
                .stream()
                .findFirst();
    }

    @Override
    public Set<Long> respostasSinalizadasPor(long sessaoId, long sinalizadoPorId) {
        return Set.copyOf(jdbc.queryForList(
                """
                SELECT mensagem_id FROM sinalizacao
                WHERE sessao_id = ? AND sinalizado_por_id = ? AND mensagem_id IS NOT NULL""",
                Long.class, sessaoId, sinalizadoPorId));
    }

    /** As chaves são os nomes do registro: provedor, modelo e versaoPrompt. */
    private String paraJson(TrechoSinalizado.MetadadosCopiados metadados) {
        if (metadados == null) {
            return null;
        }
        try {
            return json.writeValueAsString(metadados);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Metadados da resposta que não viram JSON", e);
        }
    }

    private static OffsetDateTime momento(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
