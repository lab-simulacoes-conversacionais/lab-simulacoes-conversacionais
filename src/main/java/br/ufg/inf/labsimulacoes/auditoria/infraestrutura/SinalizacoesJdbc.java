package br.ufg.inf.labsimulacoes.auditoria.infraestrutura;

import br.ufg.inf.labsimulacoes.auditoria.dominio.CursorDaFila;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadePercebida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PaginaDaFila;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PendenciaDaTriagem;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PerfilDaSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoATriar;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoPendente;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacoes;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TrechoSinalizado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Triagem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

    /** A trava faz a segunda triagem simultânea esperar a primeira e ler o status já triado. */
    private static final String PARA_TRIAR = """
            SELECT id, status, cenario_versao_id, gravidade_percebida, perfil, criada_em
            FROM sinalizacao
            WHERE id = ?
            FOR UPDATE""";

    /** Guarda contra a concorrência: só a pendente é alterada. */
    private static final String TRIAR = """
            UPDATE sinalizacao SET status = ?, gravidade_final = ?, triada_por_id = ?, triada_em = ?
            WHERE id = ? AND status = 'PENDENTE'""";

    private static final String PRIMEIRA_PAGINA = """
            SELECT id, sessao_id, cenario_versao_id, pergunta, resposta, tipo, gravidade_percebida, justificativa,
                   perfil, sinalizado_por_id, criada_em
            FROM sinalizacao
            WHERE status = 'PENDENTE'
            ORDER BY criada_em, id
            LIMIT ?""";

    private static final String PAGINA_SEGUINTE = """
            SELECT id, sessao_id, cenario_versao_id, pergunta, resposta, tipo, gravidade_percebida, justificativa,
                   perfil, sinalizado_por_id, criada_em
            FROM sinalizacao
            WHERE status = 'PENDENTE' AND (criada_em, id) > (?, ?)
            ORDER BY criada_em, id
            LIMIT ?""";

    private static final String PENDENCIAS_SEM_TEXTO = """
            SELECT id, tipo, criada_em
            FROM sinalizacao
            WHERE status = 'PENDENTE'
            ORDER BY criada_em, id""";

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

    @Override
    public PaginaDaFila pendentes(CursorDaFila depoisDe, int tamanho) {
        PaginaDaFila.exigirTamanho(tamanho);
        List<SinalizacaoPendente> lidas = depoisDe == null
                ? jdbc.query(PRIMEIRA_PAGINA, SinalizacoesJdbc::paraPendente, tamanho + 1)
                : jdbc.query(PAGINA_SEGUINTE, SinalizacoesJdbc::paraPendente,
                        momento(depoisDe.criadaEm()), depoisDe.id(), tamanho + 1);
        return PaginaDaFila.lidaComUmaAMais(lidas, tamanho);
    }

    private static SinalizacaoPendente paraPendente(ResultSet linha, int n) throws SQLException {
        return new SinalizacaoPendente(
                linha.getLong("id"),
                linha.getObject("sessao_id", Long.class),
                linha.getLong("cenario_versao_id"),
                linha.getString("pergunta"),
                linha.getString("resposta"),
                TipoDeErro.valueOf(linha.getString("tipo")),
                GravidadePercebida.valueOf(linha.getString("gravidade_percebida")),
                linha.getString("justificativa"),
                PerfilDaSinalizacao.valueOf(linha.getString("perfil")),
                linha.getLong("sinalizado_por_id"),
                linha.getObject("criada_em", OffsetDateTime.class).toInstant());
    }

    @Override
    public List<PendenciaDaTriagem> pendenciasSemTexto() {
        return jdbc.query(PENDENCIAS_SEM_TEXTO, (linha, n) -> new PendenciaDaTriagem(
                linha.getLong("id"),
                TipoDeErro.valueOf(linha.getString("tipo")),
                linha.getObject("criada_em", OffsetDateTime.class).toInstant()));
    }

    @Override
    public Optional<SinalizacaoATriar> paraTriar(long id) {
        return jdbc.query(PARA_TRIAR, (linha, n) -> new SinalizacaoATriar(
                                linha.getLong("id"),
                                Sinalizacao.PENDENTE.equals(linha.getString("status")),
                                linha.getLong("cenario_versao_id"),
                                GravidadePercebida.valueOf(linha.getString("gravidade_percebida")),
                                PerfilDaSinalizacao.valueOf(linha.getString("perfil")),
                                linha.getObject("criada_em", OffsetDateTime.class).toInstant()),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public boolean triar(Triagem triagem) {
        return jdbc.update(TRIAR,
                        triagem.decisao().name(),
                        triagem.gravidade() == null ? null : triagem.gravidade().name(),
                        triagem.triadaPorId(),
                        momento(triagem.triadaEm()),
                        triagem.sinalizacaoId())
                == 1;
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
