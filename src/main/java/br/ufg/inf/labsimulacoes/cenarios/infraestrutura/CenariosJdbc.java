package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Aprovacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscaNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Devolucao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.OpcoesDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDaBiblioteca;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDoAcervo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoAprovadaDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNoHistorico;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private static final String COLUNAS_DO_CENARIO = "c.id, c.autor_id, c.status, c.sugerido_por_ia, "
            + "c.sugestao_ia_meta_id, " + COLUNAS_DA_VERSAO + """
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
              AND (CAST(? AS text) IS NULL OR v.status = ?)
            ORDER BY v.titulo, c.id
            LIMIT ? OFFSET ?""";

    /**
     * RN-37: o autor vê os próprios cenários, em qualquer status, com a versão mais recente e a última
     * devolução, como em {@link #ACERVO}. Os mais novos primeiro, porque é o que o autor está editando;
     * o id é único, então a ordem não muda entre as páginas.
     */
    private static final String DO_AUTOR = "SELECT " + COLUNAS_DO_CENARIO + """

            FROM cenario c
            JOIN LATERAL (
                SELECT * FROM cenario_versao
                WHERE cenario_id = c.id
                ORDER BY numero DESC
                LIMIT 1) v ON true
            """ + ULTIMA_DEVOLUCAO + """

            WHERE c.autor_id = ?
            ORDER BY c.id DESC
            LIMIT ? OFFSET ?""";

    /** RN-01: só cenário Aprovado. Suspenso e arquivado guardam a versão vigente, mas não vão à sala. */
    private static final String APROVADOS_COM_VERSAO_VIGENTE = """
            FROM cenario c
            JOIN cenario_versao v ON v.id = c.versao_vigente_id
            WHERE c.status = 'APROVADO' AND v.status = 'APROVADA'""";

    private static final String VERSAO_VIGENTE = """
            SELECT %s
            %s AND c.id = ?""".formatted(COLUNAS_DA_VERSAO, APROVADOS_COM_VERSAO_VIGENTE);

    /** RN-08: a versão aprovada não muda mais, então não importa se ainda é a vigente. */
    private static final String VERSAO_APROVADA = "SELECT v.cenario_id, " + COLUNAS_DA_VERSAO + """

            FROM cenario_versao v
            WHERE v.id = ? AND v.status = 'APROVADA'""";

    /**
     * A regra de {@link BuscaNaBiblioteca#semAcento} no banco, com a mesma lista de letras: junta o acento
     * separado da letra (NFC), passa para minúsculas e tira o acento. Antes e depois do texto da coluna.
     */
    private static final String SEM_ACENTO_ANTES = "translate(lower(normalize(";
    private static final String SEM_ACENTO_DEPOIS = ", NFC)), '" + BuscaNaBiblioteca.COM_ACENTO + "', '"
            + BuscaNaBiblioteca.SEM_ACENTO + "')";

    /**
     * Onde aparece o tema do cenário (RF-12), em minúsculas e sem acento. Os objetivos são uma lista em
     * JSON, então entram como texto, um depois do outro, e não como o JSON cru com aspas e colchetes.
     */
    private static final String TEXTO_DO_CENARIO = SEM_ACENTO_ANTES + "concat_ws(' ', v.titulo, v.componente, "
            + "v.conteudo->>'personagem', v.conteudo->>'contexto', "
            + "(SELECT string_agg(objetivo, ' ') FROM jsonb_array_elements_text(v.conteudo->'objetivos') objetivo))"
            + SEM_ACENTO_DEPOIS;

    /**
     * RF-12, mesmo critério da versão vigente. Cada filtro nulo é ignorado, e os preenchidos se
     * combinam. O id desempata títulos iguais, para que a ordem não mude de uma consulta para a outra e
     * nenhuma página repita ou pule cenário.
     */
    private static final String BIBLIOTECA = "SELECT c.id AS cenario_id, " + COLUNAS_DA_VERSAO + "\n"
            + APROVADOS_COM_VERSAO_VIGENTE + "\n"
            + "  AND (CAST(? AS text) IS NULL OR " + TEXTO_DO_CENARIO + " LIKE ? ESCAPE '\\')\n"
            + "  AND (CAST(? AS text) IS NULL OR " + SEM_ACENTO_ANTES + "v.componente" + SEM_ACENTO_DEPOIS + " = ?)\n"
            + "  AND (CAST(? AS text) IS NULL OR v.ano = ?)\n"
            + "  AND (CAST(? AS text) IS NULL OR EXISTS (SELECT 1 FROM versao_habilidade h\n"
            + "       WHERE h.cenario_versao_id = v.id AND h.habilidade_codigo = ?))\n"
            + "ORDER BY v.titulo, c.id\n"
            + "LIMIT ? OFFSET ?";

    private static final String COMPONENTES_DA_BIBLIOTECA = "SELECT DISTINCT v.componente\n"
            + APROVADOS_COM_VERSAO_VIGENTE + " AND v.componente IS NOT NULL ORDER BY v.componente";

    private static final String ANOS_DA_BIBLIOTECA = "SELECT DISTINCT v.ano\n"
            + APROVADOS_COM_VERSAO_VIGENTE + " AND v.ano IS NOT NULL";

    private static final String HABILIDADES_DA_BIBLIOTECA = """
            SELECT DISTINCT h.habilidade_codigo
            FROM cenario c
            JOIN cenario_versao v ON v.id = c.versao_vigente_id
            JOIN versao_habilidade h ON h.cenario_versao_id = v.id
            WHERE c.status = 'APROVADO' AND v.status = 'APROVADA'
            ORDER BY h.habilidade_codigo""";

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

    /**
     * RF-11: da mais nova para a mais antiga. Vigente é a versão que vale em sala agora: a última
     * aprovada, e só se o cenário estiver Aprovado (suspenso e arquivado não vão para a sala, RN-01).
     */
    private static final String HISTORICO = "SELECT " + COLUNAS_DA_VERSAO + """
            ,
            v.criado_em AS versao_criada_em,
            v.id = c.versao_vigente_id AND c.status = 'APROVADO' AS vigente
            FROM cenario_versao v
            JOIN cenario c ON c.id = v.cenario_id
            WHERE v.cenario_id = ?
            ORDER BY v.numero DESC""";

    /** A última versão aprovada de cada cenário pedido, que fica em versao_vigente_id (RN-08). */
    private static final String ULTIMAS_APROVADAS = "SELECT c.id AS cenario_id, " + COLUNAS_DA_VERSAO + """

            FROM cenario c
            JOIN cenario_versao v ON v.id = c.versao_vigente_id
            WHERE c.id = ANY(CAST(? AS bigint[]))""";

    private static final String VERSAO_PELO_NUMERO = "SELECT " + COLUNAS_DA_VERSAO + """

            FROM cenario_versao v
            WHERE v.cenario_id = ? AND v.numero = ?""";

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

    @Override
    public Optional<VersaoAprovadaDoCenario> versaoAprovada(long versaoId) {
        return jdbc.query(
                        VERSAO_APROVADA,
                        (linha, numero) -> new VersaoAprovadaDoCenario(linha.getLong("cenario_id"), paraVersao(linha)),
                        versaoId)
                .stream()
                .findFirst();
    }

    /** Pede um item a mais que a página, só para saber se existe a próxima, sem contar a tabela. */
    @Override
    public TrechoDaBiblioteca listarAprovados(BuscaNaBiblioteca busca, Pagina pagina) {
        String trecho = busca.texto() == null ? null : "%" + semCuringas(busca.texto()) + "%";
        String ano = busca.ano() == null ? null : busca.ano().toString();
        List<CenarioNaBiblioteca> lidos = jdbc.query(
                BIBLIOTECA,
                (linha, numero) -> new CenarioNaBiblioteca(linha.getLong("cenario_id"), paraVersao(linha)),
                trecho, trecho,
                busca.componenteSemAcento(), busca.componenteSemAcento(),
                ano, ano,
                busca.habilidade(), busca.habilidade(),
                pagina.tamanho() + 1,
                pagina.deslocamento());
        boolean temProxima = lidos.size() > pagina.tamanho();
        return new TrechoDaBiblioteca(temProxima ? lidos.subList(0, pagina.tamanho()) : lidos, temProxima);
    }

    @Override
    public OpcoesDaBiblioteca opcoesDaBiblioteca() {
        List<Integer> anos = jdbc.queryForList(ANOS_DA_BIBLIOTECA, String.class).stream()
                .map(Integer::valueOf)
                .sorted()
                .toList();
        return new OpcoesDaBiblioteca(
                jdbc.queryForList(COMPONENTES_DA_BIBLIOTECA, String.class),
                anos,
                jdbc.queryForList(HABILIDADES_DA_BIBLIOTECA, String.class));
    }

    /** O que a pessoa digitou é procurado ao pé da letra: % e _ não viram curingas do LIKE. */
    private static String semCuringas(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Como na biblioteca, pede um item a mais só para saber se existe a próxima página. */
    @Override
    public TrechoDoAcervo listarTodos(StatusDoCenario status, Pagina pagina) {
        // Em curadoria filtra pela versão: a versão nova de um cenário Aprovado também espera o curador (RN-08).
        boolean pelaVersao = status == StatusDoCenario.EM_CURADORIA;
        String doCenario = status == null || pelaVersao ? null : status.name();
        String daVersao = pelaVersao ? StatusDaVersao.EM_CURADORIA.name() : null;
        List<Cenario> lidos = jdbc.query(
                ACERVO, this::paraCenario, doCenario, doCenario, daVersao, daVersao,
                pagina.tamanho() + 1, pagina.deslocamento());
        return trechoDoAcervo(lidos, pagina);
    }

    @Override
    public TrechoDoAcervo listarDoAutor(long autorId, Pagina pagina) {
        List<Cenario> lidos = jdbc.query(
                DO_AUTOR, this::paraCenario, autorId, pagina.tamanho() + 1, pagina.deslocamento());
        return trechoDoAcervo(lidos, pagina);
    }

    /**
     * Corta o item a mais e lê, numa consulta só, a última versão aprovada dos cenários da página em que
     * ela não é a mais recente (RN-08).
     */
    private TrechoDoAcervo trechoDoAcervo(List<Cenario> lidos, Pagina pagina) {
        boolean temProxima = lidos.size() > pagina.tamanho();
        List<Cenario> itens = temProxima ? lidos.subList(0, pagina.tamanho()) : lidos;
        List<Cenario> comVersaoNova = itens.stream()
                .filter(cenario -> cenario.versao().status() != StatusDaVersao.APROVADA)
                .toList();
        if (comVersaoNova.isEmpty()) {
            return new TrechoDoAcervo(itens, temProxima);
        }
        String ids = comVersaoNova.stream()
                .map(cenario -> cenario.id().toString())
                .collect(Collectors.joining(",", "{", "}"));
        Map<Long, VersaoDoCenario> ultimasAprovadas = new HashMap<>();
        jdbc.query(ULTIMAS_APROVADAS, (ResultSet linha) -> {
            ultimasAprovadas.put(linha.getLong("cenario_id"), paraVersao(linha));
        }, ids);
        return new TrechoDoAcervo(itens, ultimasAprovadas, temProxima);
    }

    @Override
    public List<VersaoNoHistorico> historico(long cenarioId) {
        return jdbc.query(
                HISTORICO,
                (linha, numero) -> new VersaoNoHistorico(
                        paraVersao(linha), instante(linha, "versao_criada_em"), linha.getBoolean("vigente")),
                cenarioId);
    }

    @Override
    public Optional<VersaoDoCenario> versao(long cenarioId, int numero) {
        return jdbc.query(VERSAO_PELO_NUMERO, (linha, n) -> paraVersao(linha), cenarioId, numero).stream().findFirst();
    }

    private Optional<Cenario> consultar(String sql, long id) {
        return jdbc.query(sql, this::paraCenario, id).stream().findFirst();
    }

    private Cenario inserir(Cenario cenario) {
        Long id = jdbc.queryForObject(
                """
                INSERT INTO cenario (autor_id, status, sugerido_por_ia, sugestao_ia_meta_id)
                VALUES (?, ?, ?, ?) RETURNING id""",
                Long.class,
                cenario.autorId(),
                cenario.status().name(),
                cenario.sugeridoPorIa(),
                cenario.sugestaoDaIa());
        return new Cenario(id, cenario.autorId(), cenario.status(), inserirVersao(id, cenario.versao()), null,
                cenario.sugeridoPorIa(), cenario.sugestaoDaIa());
    }

    /** Só para versões que ainda não foram aprovadas: a aprovação é gravada por {@link #atualizarVersao}. */
    private VersaoDoCenario inserirVersao(long cenarioId, VersaoDoCenario versao) {
        ConteudoDoCenario conteudo = versao.conteudo();
        VinculoCurricular curriculo = conteudo.curriculo();
        Long versaoId = jdbc.queryForObject(
                INSERIR_VERSAO,
                Long.class,
                cenarioId,
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
        return new VersaoDoCenario(versaoId, versao.numero(), versao.status(), conteudo, versao.criadaPorId());
    }

    private Cenario atualizar(Cenario cenario) {
        VersaoDoCenario versao = cenario.versao();
        jdbc.update(
                "UPDATE cenario SET status = ?, atualizado_em = now() WHERE id = ?",
                cenario.status().name(),
                cenario.id());
        // RF-11, RN-08: a versão nova de um cenário que já tem vigente.
        if (versao.id() == null) {
            versao = inserirVersao(cenario.id(), versao);
        } else {
            atualizarVersao(versao);
        }
        if (versao.status() == StatusDaVersao.APROVADA) {
            jdbc.update("UPDATE cenario SET versao_vigente_id = ? WHERE id = ?", versao.id(), cenario.id());
        }
        Devolucao devolucao = cenario.ultimaDevolucao();
        if (devolucao != null && devolucao.id() == null) {
            devolucao = devolucao.comId(inserirDevolucao(versao.id(), devolucao));
        }
        return new Cenario(
                cenario.id(), cenario.autorId(), cenario.status(), versao, devolucao, cenario.sugeridoPorIa(),
                cenario.sugestaoDaIa());
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
                paraDevolucao(linha),
                linha.getBoolean("sugerido_por_ia"),
                linha.getObject("sugestao_ia_meta_id", Long.class));
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
