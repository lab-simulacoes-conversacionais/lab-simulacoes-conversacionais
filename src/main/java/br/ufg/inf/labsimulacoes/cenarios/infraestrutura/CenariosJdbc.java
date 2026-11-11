package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Aprovacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Devolucao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDoAcervo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tabelas cenario, cenario_versao, versao_habilidade e devolucao com SQL direto: elas se referenciam
 * em ciclo, e o cenário sempre é lido junto com só uma das suas versões, o que em JPA daria mais
 * trabalho.
 */
@Repository
class CenariosJdbc implements Cenarios {

    private static final String COLUNAS_DA_VERSAO = """
            v.id AS versao_id, v.numero, v.status AS versao_status, v.titulo, v.conteudo::text AS conteudo,
            v.componente, v.etapa, v.ano, v.faixa_etaria, v.duracao_prevista_min, v.criado_por_id,
            v.aprovado_por_id, v.aprovado_em,
            ARRAY(SELECT h.habilidade_codigo FROM versao_habilidade h
                  WHERE h.cenario_versao_id = v.id ORDER BY h.habilidade_codigo) AS habilidades""";

    /*
     * Os trechos abaixo são juntados com +, e não com formatted(), para que cada consulta seja uma
     * constante de compilação: nenhum valor entra no texto do SQL, só pelos parâmetros.
     */
    private static final String COLUNAS_DO_CENARIO = "c.id, c.autor_id, c.status, " + COLUNAS_DA_VERSAO + """
            ,
            d.id AS devolucao_id, d.curador_id AS devolucao_curador_id,
            d.comentario AS devolucao_comentario, d.criado_em AS devolucao_em""";

    /** A devolução mais recente da versão, para o autor saber o que mudar (RN-07). */
    private static final String ULTIMA_DEVOLUCAO = """
            LEFT JOIN LATERAL (
                SELECT id, curador_id, comentario, criado_em FROM devolucao
                WHERE cenario_versao_id = v.id
                ORDER BY criado_em DESC, id DESC
                LIMIT 1) d ON true""";

    private static final String CENARIO_COM_ULTIMA_VERSAO = "SELECT " + COLUNAS_DO_CENARIO + """

            FROM cenario c
            JOIN cenario_versao v ON v.cenario_id = c.id
            """ + ULTIMA_DEVOLUCAO + """

            WHERE c.id = ?
            ORDER BY v.numero DESC
            LIMIT 1""";

    /**
     * RN-37: o curador vê todos os cenários, cada um com a versão mais recente, como em
     * {@link #CENARIO_COM_ULTIMA_VERSAO}. Status nulo traz todos. Mesma ordem da biblioteca.
     */
    private static final String ACERVO = "SELECT " + COLUNAS_DO_CENARIO + """

            FROM cenario c
            JOIN LATERAL (
                SELECT * FROM cenario_versao
                WHERE cenario_id = c.id
                ORDER BY numero DESC
                LIMIT 1) v ON true
            """ + ULTIMA_DEVOLUCAO + """

            WHERE (CAST(? AS text) IS NULL OR c.status = ?)
            ORDER BY v.titulo, c.id
            LIMIT ? OFFSET ?""";

    /** RN-01: só cenário Aprovado. Suspenso e arquivado guardam a versão vigente, mas não vão à sala. */
    private static final String APROVADOS_COM_VERSAO_VIGENTE = """
            FROM cenario c
            JOIN cenario_versao v ON v.id = c.versao_vigente_id
            WHERE c.status = 'APROVADO' AND v.status = 'APROVADA'""";

    private static final String VERSAO_VIGENTE = """
            SELECT %s
            %s AND c.id = ?""".formatted(COLUNAS_DA_VERSAO, APROVADOS_COM_VERSAO_VIGENTE);

    /**
     * RF-12, mesmo critério da versão vigente. O id desempata títulos iguais, para que a ordem não
     * mude de uma consulta para a outra e nenhuma página repita ou pule cenário.
     */
    private static final String BIBLIOTECA = """
            SELECT c.id AS cenario_id, %s
            %s
            ORDER BY v.titulo, c.id
            LIMIT ? OFFSET ?""".formatted(COLUNAS_DA_VERSAO, APROVADOS_COM_VERSAO_VIGENTE);

    private static final String INSERIR_VERSAO = """
            INSERT INTO cenario_versao (cenario_id, numero, status, titulo, conteudo, componente, etapa, ano,
                                        faixa_etaria, duracao_prevista_min, criado_por_id)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
            RETURNING id""";

    /**
     * Versão aprovada é imutável, e o banco recusaria a alteração (RN-08). O filtro deixa a linha de
     * fora antes disso, para que salvar um cenário aprovado mude só o cenário.
     */
    private static final String ATUALIZAR_VERSAO = """
            UPDATE cenario_versao
            SET status = ?, titulo = ?, conteudo = ?::jsonb, componente = ?, etapa = ?, ano = ?,
                faixa_etaria = ?, duracao_prevista_min = ?, aprovado_por_id = ?, aprovado_em = ?
            WHERE id = ? AND status <> 'APROVADA'""";

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

    /** A versão, suas habilidades e a devolução precisam ser gravadas juntas. */
    @Override
    @Transactional
    public Cenario salvar(Cenario cenario) {
        return cenario.id() == null ? inserir(cenario) : atualizar(cenario);
    }

    @Override
    public Optional<VersaoDoCenario> versaoVigente(long cenarioId) {
        return jdbc.query(VERSAO_VIGENTE, (linha, numero) -> paraVersao(linha), cenarioId).stream().findFirst();
    }

    /** Pede um item a mais que a página, só para saber se existe a próxima, sem contar a tabela. */
    @Override
    public TrechoDaBiblioteca listarAprovados(Pagina pagina) {
        List<CenarioNaBiblioteca> lidos = jdbc.query(
                BIBLIOTECA,
                (linha, numero) -> new CenarioNaBiblioteca(linha.getLong("cenario_id"), paraVersao(linha)),
                pagina.tamanho() + 1,
                pagina.deslocamento());
        boolean temProxima = lidos.size() > pagina.tamanho();
        return new TrechoDaBiblioteca(temProxima ? lidos.subList(0, pagina.tamanho()) : lidos, temProxima);
    }

    /** Como na biblioteca, pede um item a mais só para saber se existe a próxima página. */
    @Override
    public TrechoDoAcervo listarTodos(StatusDoCenario status, Pagina pagina) {
        String filtro = status == null ? null : status.name();
        List<Cenario> lidos = jdbc.query(
                ACERVO, this::paraCenario, filtro, filtro, pagina.tamanho() + 1, pagina.deslocamento());
        boolean temProxima = lidos.size() > pagina.tamanho();
        return new TrechoDoAcervo(temProxima ? lidos.subList(0, pagina.tamanho()) : lidos, temProxima);
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
        VersaoDoCenario versao = cenario.versao();
        jdbc.update(
                "UPDATE cenario SET status = ?, atualizado_em = now() WHERE id = ?",
                cenario.status().name(),
                cenario.id());
        atualizarVersao(versao);
        if (versao.status() == StatusDaVersao.APROVADA) {
            jdbc.update("UPDATE cenario SET versao_vigente_id = ? WHERE id = ?", versao.id(), cenario.id());
        }
        Devolucao devolucao = cenario.ultimaDevolucao();
        if (devolucao != null && devolucao.id() == null) {
            devolucao = devolucao.comId(inserirDevolucao(versao.id(), devolucao));
        }
        return new Cenario(cenario.id(), cenario.autorId(), cenario.status(), versao, devolucao);
    }

    private void atualizarVersao(VersaoDoCenario versao) {
        ConteudoDoCenario conteudo = versao.conteudo();
        VinculoCurricular curriculo = conteudo.curriculo();
        Aprovacao aprovacao = versao.aprovacao();
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
                aprovacao == null ? null : aprovacao.curadorId(),
                aprovacao == null ? null : utc(aprovacao.em()),
                versao.id());
        // O conteúdo só muda em rascunho, então nas outras transições as habilidades continuam as mesmas.
        if (versao.status() == StatusDaVersao.RASCUNHO) {
            jdbc.update("DELETE FROM versao_habilidade WHERE cenario_versao_id = ?", versao.id());
            inserirHabilidades(versao.id(), curriculo.habilidades());
        }
    }

    private long inserirDevolucao(long versaoId, Devolucao devolucao) {
        return jdbc.queryForObject("""
                INSERT INTO devolucao (cenario_versao_id, curador_id, comentario, criado_em)
                VALUES (?, ?, ?, ?) RETURNING id""",
                Long.class,
                versaoId,
                devolucao.curadorId(),
                devolucao.comentario(),
                utc(devolucao.em()));
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
        return new Cenario(
                linha.getLong("id"),
                linha.getLong("autor_id"),
                StatusDoCenario.valueOf(linha.getString("status")),
                paraVersao(linha),
                paraDevolucao(linha));
    }

    private VersaoDoCenario paraVersao(ResultSet linha) throws SQLException {
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
        Long aprovadoPor = linha.getObject("aprovado_por_id", Long.class);
        Aprovacao aprovacao = aprovadoPor == null ? null : new Aprovacao(aprovadoPor, instante(linha, "aprovado_em"));
        return new VersaoDoCenario(
                linha.getLong("versao_id"),
                linha.getInt("numero"),
                StatusDaVersao.valueOf(linha.getString("versao_status")),
                conteudo,
                linha.getLong("criado_por_id"),
                aprovacao);
    }

    private static Devolucao paraDevolucao(ResultSet linha) throws SQLException {
        Long id = linha.getObject("devolucao_id", Long.class);
        if (id == null) {
            return null;
        }
        return new Devolucao(
                id,
                linha.getLong("devolucao_curador_id"),
                linha.getString("devolucao_comentario"),
                instante(linha, "devolucao_em"));
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        OffsetDateTime valor = linha.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
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
