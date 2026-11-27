package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.GravidadeDoErro;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PerfilDeQuemSinalizou;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacaoDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacoesDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SituacaoDaSinalizacao;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tabela sinalizacao_do_cenario (RF-25). O cenário é lido da própria versão na gravação, para as duas
 * colunas nunca discordarem; uma versão que não existe deixa o cenário nulo, e o banco recusa.
 */
@Repository
class SinalizacoesDoCenarioJdbc implements SinalizacoesDoCenario {

    private static final String COLUNAS = """
            sinalizacao_id, cenario_versao_id, perfil, gravidade_percebida, decisao, gravidade_final, triada_por_id,
            recebida_em, triada_em""";

    private static final String INSERIR = """
            INSERT INTO sinalizacao_do_cenario (%s, cenario_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT cenario_id FROM cenario_versao WHERE id = ?))
            """.formatted(COLUNAS);

    private static final String REGISTRAR = INSERIR + "ON CONFLICT (sinalizacao_id) DO NOTHING";

    /** A triagem só entra na sinalização ainda pendente; o momento em que ela foi recebida não muda. */
    private static final String TRIAR = INSERIR + """
            ON CONFLICT (sinalizacao_id) DO UPDATE
               SET decisao = EXCLUDED.decisao,
                   gravidade_final = EXCLUDED.gravidade_final,
                   triada_por_id = EXCLUDED.triada_por_id,
                   triada_em = EXCLUDED.triada_em
             WHERE sinalizacao_do_cenario.decisao IS NULL""";

    private final JdbcTemplate jdbc;

    SinalizacoesDoCenarioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean registrar(SinalizacaoDaVersao pendente) {
        if (!pendente.pendente()) {
            throw new IllegalArgumentException("A sinalização " + pendente.sinalizacaoId() + " já vem triada");
        }
        return gravar(REGISTRAR, pendente);
    }

    @Override
    public boolean triar(SinalizacaoDaVersao triada) {
        if (triada.pendente()) {
            throw new IllegalArgumentException("A sinalização " + triada.sinalizacaoId() + " ainda não foi triada");
        }
        return gravar(TRIAR, triada);
    }

    @Override
    public List<SinalizacaoDaVersao> daVersao(long versaoId) {
        return jdbc.query(
                "SELECT " + COLUNAS + " FROM sinalizacao_do_cenario WHERE cenario_versao_id = ? ORDER BY sinalizacao_id",
                SinalizacoesDoCenarioJdbc::paraSinalizacao,
                versaoId);
    }

    @Override
    public Optional<Long> cenarioDaVersao(long versaoId) {
        return jdbc.queryForList("SELECT cenario_id FROM cenario_versao WHERE id = ?", Long.class, versaoId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Long> versaoVigente(long cenarioId) {
        return jdbc.queryForList(
                        "SELECT versao_vigente_id FROM cenario WHERE id = ? AND versao_vigente_id IS NOT NULL",
                        Long.class,
                        cenarioId)
                .stream()
                .findFirst();
    }

    private boolean gravar(String sql, SinalizacaoDaVersao sinalizacao) {
        return jdbc.update(
                        sql,
                        sinalizacao.sinalizacaoId(),
                        sinalizacao.versaoId(),
                        sinalizacao.perfil().name(),
                        sinalizacao.gravidadePercebida().name(),
                        decisao(sinalizacao.situacao()),
                        nome(sinalizacao.gravidadeFinal()),
                        sinalizacao.triadaPorId(),
                        utc(sinalizacao.recebidaEm()),
                        sinalizacao.triadaEm() == null ? null : utc(sinalizacao.triadaEm()),
                        sinalizacao.versaoId())
                > 0;
    }

    private static SinalizacaoDaVersao paraSinalizacao(ResultSet linha, int numero) throws SQLException {
        String decisao = linha.getString("decisao");
        String gravidadeFinal = linha.getString("gravidade_final");
        Long triadaPorId = linha.getObject("triada_por_id", Long.class);
        return new SinalizacaoDaVersao(
                linha.getLong("sinalizacao_id"),
                linha.getLong("cenario_versao_id"),
                PerfilDeQuemSinalizou.valueOf(linha.getString("perfil")),
                GravidadeDoErro.valueOf(linha.getString("gravidade_percebida")),
                decisao == null ? SituacaoDaSinalizacao.PENDENTE : SituacaoDaSinalizacao.valueOf(decisao),
                gravidadeFinal == null ? null : GravidadeDoErro.valueOf(gravidadeFinal),
                triadaPorId,
                instante(linha, "recebida_em"),
                instante(linha, "triada_em"));
    }

    /** A pendente não tem decisão no banco; as outras situações têm o mesmo nome da coluna. */
    private static String decisao(SituacaoDaSinalizacao situacao) {
        return situacao == SituacaoDaSinalizacao.PENDENTE ? null : situacao.name();
    }

    private static String nome(GravidadeDoErro gravidade) {
        return gravidade == null ? null : gravidade.name();
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        OffsetDateTime valor = linha.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
