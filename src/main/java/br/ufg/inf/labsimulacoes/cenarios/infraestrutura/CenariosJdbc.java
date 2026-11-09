package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tabelas cenario, cenario_versao e versao_habilidade com SQL direto: elas se referenciam em ciclo, e
 * o cenário sempre é lido junto com só uma das suas versões, o que em JPA daria mais trabalho.
 */
@Repository
class CenariosJdbc implements Cenarios {

    private static final String CENARIO_COM_ULTIMA_VERSAO = """
            SELECT c.id, c.autor_id, c.status, v.id AS versao_id, v.numero, v.status AS versao_status,
                   v.titulo, v.conteudo::text AS conteudo, v.componente, v.etapa, v.ano, v.faixa_etaria,
                   v.duracao_prevista_min, v.criado_por_id,
                   ARRAY(SELECT h.habilidade_codigo FROM versao_habilidade h
                         WHERE h.cenario_versao_id = v.id ORDER BY h.habilidade_codigo) AS habilidades
            FROM cenario c
            JOIN cenario_versao v ON v.cenario_id = c.id
            WHERE c.id = ?
            ORDER BY v.numero DESC
            LIMIT 1""";

    private static final String INSERIR_VERSAO = """
            INSERT INTO cenario_versao (cenario_id, numero, status, titulo, conteudo, componente, etapa, ano,
                                        faixa_etaria, duracao_prevista_min, criado_por_id)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
            RETURNING id""";

    private static final String ATUALIZAR_VERSAO = """
            UPDATE cenario_versao
            SET status = ?, titulo = ?, conteudo = ?::jsonb, componente = ?, etapa = ?, ano = ?,
                faixa_etaria = ?, duracao_prevista_min = ?
            WHERE id = ?""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    CenariosJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Cenario> buscar(long id) {
        return consultar(CENARIO_COM_ULTIMA_VERSAO, id);
    }

    @Override
    public Optional<Cenario> buscarParaAlterar(long id) {
        return consultar(CENARIO_COM_ULTIMA_VERSAO + " FOR UPDATE OF c", id);
    }

    /** A versão e suas habilidades precisam ser gravadas juntas. */
    @Override
    @Transactional
    public Cenario salvar(Cenario cenario) {
        return cenario.id() == null ? inserir(cenario) : atualizar(cenario);
    }

    private Optional<Cenario> consultar(String sql, long id) {
        return jdbc.query(sql, this::paraCenario, id).stream().findFirst();
    }

    private Cenario inserir(Cenario cenario) {
        Long id = jdbc.queryForObject(
                "INSERT INTO cenario (autor_id, status) VALUES (?, ?) RETURNING id",
                Long.class,
                cenario.autorId(),
                cenario.status().name());
        VersaoDoCenario versao = cenario.versao();
        ConteudoDoCenario conteudo = versao.conteudo();
        VinculoCurricular curriculo = conteudo.curriculo();
        Long versaoId = jdbc.queryForObject(
                INSERIR_VERSAO,
                Long.class,
                id,
                versao.numero(),
                versao.status().name(),
                conteudo.titulo(),
                paraJson(conteudo.roteiro()),
                curriculo.componente(),
                curriculo.etapa(),
                anoComoTexto(curriculo),
                conteudo.faixaEtaria(),
                conteudo.duracaoPrevistaMin(),
                versao.criadaPorId());
        inserirHabilidades(versaoId, curriculo.habilidades());
        var versaoSalva = new VersaoDoCenario(
                versaoId, versao.numero(), versao.status(), conteudo, versao.criadaPorId());
        return new Cenario(id, cenario.autorId(), cenario.status(), versaoSalva);
    }

    private Cenario atualizar(Cenario cenario) {
        jdbc.update(
                "UPDATE cenario SET status = ?, atualizado_em = now() WHERE id = ?",
                cenario.status().name(),
                cenario.id());
        VersaoDoCenario versao = cenario.versao();
        ConteudoDoCenario conteudo = versao.conteudo();
        VinculoCurricular curriculo = conteudo.curriculo();
        jdbc.update(
                ATUALIZAR_VERSAO,
                versao.status().name(),
                conteudo.titulo(),
                paraJson(conteudo.roteiro()),
                curriculo.componente(),
                curriculo.etapa(),
                anoComoTexto(curriculo),
                conteudo.faixaEtaria(),
                conteudo.duracaoPrevistaMin(),
                versao.id());
        jdbc.update("DELETE FROM versao_habilidade WHERE cenario_versao_id = ?", versao.id());
        inserirHabilidades(versao.id(), curriculo.habilidades());
        return cenario;
    }

    private void inserirHabilidades(long versaoId, List<String> codigos) {
        List<Object[]> linhas = codigos.stream().map(codigo -> new Object[] {versaoId, codigo}).toList();
        jdbc.batchUpdate(
                "INSERT INTO versao_habilidade (cenario_versao_id, habilidade_codigo) VALUES (?, ?)", linhas);
    }

    private static String anoComoTexto(VinculoCurricular curriculo) {
        return curriculo.ano() == null ? null : curriculo.ano().toString();
    }

    private Cenario paraCenario(ResultSet linha, int numero) throws SQLException {
        String ano = linha.getString("ano");
        var curriculo = new VinculoCurricular(
                linha.getString("componente"),
                linha.getString("etapa"),
                ano == null ? null : Integer.valueOf(ano),
                List.of((String[]) linha.getArray("habilidades").getArray()));
        var conteudo = new ConteudoDoCenario(
                linha.getString("titulo"),
                deJson(linha.getString("conteudo")),
                curriculo,
                linha.getString("faixa_etaria"),
                linha.getObject("duracao_prevista_min", Integer.class));
        var versao = new VersaoDoCenario(
                linha.getLong("versao_id"),
                linha.getInt("numero"),
                StatusDaVersao.valueOf(linha.getString("versao_status")),
                conteudo,
                linha.getLong("criado_por_id"));
        return new Cenario(
                linha.getLong("id"),
                linha.getLong("autor_id"),
                StatusDoCenario.valueOf(linha.getString("status")),
                versao);
    }

    private String paraJson(RoteiroDoCenario roteiro) {
        try {
            return json.writeValueAsString(RoteiroJson.de(roteiro));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Roteiro do cenário que não vira JSON", e);
        }
    }

    private RoteiroDoCenario deJson(String conteudo) {
        try {
            return json.readValue(conteudo, RoteiroJson.class).paraDominio();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Roteiro do cenário com JSON inválido", e);
        }
    }

    /**
     * Formato gravado no JSONB. É separado do domínio porque versões aprovadas nunca mudam (RN-08):
     * renomear um campo do domínio não pode impedir a leitura delas.
     */
    record RoteiroJson(
            String personagem,
            String papelDaIa,
            String contexto,
            List<String> objetivos,
            List<String> limitesDeAssunto,
            List<String> fontes,
            List<String> perguntasDisparadoras) {

        static RoteiroJson de(RoteiroDoCenario roteiro) {
            return new RoteiroJson(
                    roteiro.personagem(),
                    roteiro.papelDaIa(),
                    roteiro.contexto(),
                    roteiro.objetivos(),
                    roteiro.limitesDeAssunto(),
                    roteiro.fontes(),
                    roteiro.perguntasDisparadoras());
        }

        RoteiroDoCenario paraDominio() {
            return new RoteiroDoCenario(
                    personagem, papelDaIa, contexto, objetivos, limitesDeAssunto, fontes, perguntasDisparadoras);
        }
    }
}
