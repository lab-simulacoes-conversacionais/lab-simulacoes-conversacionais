package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.ia.aplicacao.ChamadasAoProvedor;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.ControlesDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.EtapasDaConversa;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.InicioDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.apresentacao.Navegador.EventoSse;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.HtmlUtils;

/**
 * Spike da issue #19: mede, contra a OpenAI de verdade, o tempo do envio da pergunta até a primeira
 * frase aprovada pela moderação chegar à tela, para decidir se a moderação por frase cumpre o RNF-03
 * (Stack, seção 5, passo 7). Critério: 95% das 30 perguntas de referência em até 3 s.
 *
 * <p>A aplicação sobe inteira, com PostgreSQL e Redis em containers. As perguntas vão pelo HTMX e a
 * resposta é lida no canal SSE, com requisições HTTP reais e o login da professora, como na tela. As
 * observações que a aplicação já registra (HTTP, banco, Redis, SSE, moderação, geração e agrupamento de
 * frases) separam o tempo em etapas, só para explicar o resultado (issue #80).
 *
 * <p>Cada execução anota a estratégia de latência que está sendo medida ({@code -Dspike.estrategia}) e
 * se compara com a linha de base das execuções 02 a 04, sem estratégia. A medição original da issue #19
 * ({@code resultados01.md}) aparece só como referência.
 *
 * <p>Gasta tokens e leva alguns minutos, por isso só roda quando pedido e com a chave no terminal; no CI
 * e no {@code verify} comum fica desligado. Como rodar e o que fica de fora estão no README da pasta
 * {@code docs/spikes/primeira-frase-aprovada}, onde o relatório é gravado.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.ai.openai.api-key=${OPENAI_API_KEY}", "lab.teste.contexto=spike-primeira-frase"})
@Import({InfraestruturaDeTeste.class, PrimeiraFraseAprovadaComOpenAiTest.Cronometragem.class})
@EnabledIfSystemProperty(named = "ia.spike-primeira-frase", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class PrimeiraFraseAprovadaComOpenAiTest {

    static final Path PASTA = Path.of("docs/spikes/primeira-frase-aprovada");
    static final int PERGUNTAS_DE_REFERENCIA = 30;
    private static final Pattern RELATORIO_NUMERADO = Pattern.compile("resultados(\\d+)\\.md");

    /** RNF-03: primeiro trecho em até 3 s e resposta completa em até 10 s, em 95% das interações. */
    static final double META_PRIMEIRA_FRASE_S = 3.0;

    static final double META_TOTAL_S = 10.0;
    static final double PROPORCAO_EXIGIDA = 0.95;

    /** Sem a propriedade, a execução é uma nova linha de base, com os mesmos parâmetros da original. */
    static final String ESTRATEGIA_PADRAO = "nenhuma (linha de base, com os mesmos parâmetros do resultados01)";

    private static final String SENHA = "uma senha de teste";
    private static final Cronometro CRONOMETRO = new Cronometro();

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastroDeUsuarios cadastro;

    @Autowired
    private InicioDeSessao inicio;

    @Autowired
    private ControlesDaSessao controles;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment ambiente;

    private String professora;
    private Navegador navegador;

    @Test
    void medeAPrimeiraFraseAprovadaNasPerguntasDeReferencia() throws Exception {
        List<String> perguntas = perguntasDeReferencia();
        long intervaloS = Long.getLong("spike.intervalo-s", 10);
        var dados = new DadosDeSessao(jdbc);
        professora = UUID.randomUUID() + "@escola.test";
        cadastro.cadastrar("Professora", professora, SENHA, Set.of(Perfil.PROFESSOR));
        navegador = new Navegador(porta);
        navegador.entrar(professora, SENHA);

        // Aquecimento numa sessão à parte, para a medição não pegar a primeira chamada da JVM. Se ele não
        // chega à tela, a chave, o crédito ou o modelo estão errados, e medir as 30 não diria nada.
        Medicao aquecimento = medir(novaSessao(dados), 0, "Oi! Tudo bem?");
        assertThat(aquecimento.resultado)
                .as("aquecimento (confira a OPENAI_API_KEY e o log do provedor de IA)")
                .isEqualTo(Resultado.EXIBIDA);

        long sessao = novaSessao(dados);
        List<Medicao> medicoes = new ArrayList<>();
        for (int i = 0; i < perguntas.size(); i++) {
            if (i > 0) {
                Thread.sleep(intervaloS * 1000);
            }
            Medicao medicao = medir(sessao, i + 1, perguntas.get(i));
            System.out.println(medicao.resumo());
            medicoes.add(medicao);
        }

        String estrategia = System.getProperty("spike.estrategia", ESTRATEGIA_PADRAO).strip();
        String relatorio = relatorio(medicoes, aquecimento, intervaloS, sessao, estrategia);
        // CREATE_NEW: nunca sobrescreve uma execução anterior, nem se duas rodarem ao mesmo tempo.
        Path arquivo = proximoRelatorio();
        Files.writeString(arquivo, relatorio, StandardOpenOption.CREATE_NEW);
        System.out.printf("%n%s%nRelatório gravado em %s%n", relatorio, arquivo);
        assertThat(medicoes).hasSize(PERGUNTAS_DE_REFERENCIA);
    }

    /**
     * Cada execução ganha o próprio arquivo, numerado depois do maior que já existe: resultados01.md,
     * resultados02.md e assim por diante. Assim dá para comparar estratégias de latência sem perder as
     * medições anteriores, mesmo que alguma seja apagada.
     */
    static Path proximoRelatorio() throws IOException {
        int maior = 0;
        try (Stream<Path> arquivos = Files.list(PASTA)) {
            for (Path arquivo : arquivos.toList()) {
                Matcher numero = RELATORIO_NUMERADO.matcher(arquivo.getFileName().toString());
                if (numero.matches()) {
                    maior = Math.max(maior, Integer.parseInt(numero.group(1)));
                }
            }
        }
        return PASTA.resolve(String.format("resultados%02d.md", maior + 1));
    }

    private List<String> perguntasDeReferencia() throws IOException {
        List<String> perguntas = Files.readAllLines(PASTA.resolve("perguntas.txt")).stream()
                .map(String::strip)
                .filter(linha -> !linha.isEmpty() && !linha.startsWith("#"))
                .toList();
        assertThat(perguntas).as("perguntas de referência").hasSize(PERGUNTAS_DE_REFERENCIA);
        return perguntas;
    }

    private long novaSessao(DadosDeSessao dados) {
        long cenario = dados.cenarioAprovado(dados.idDe(dados.usuario("PROFESSOR")), dados.idDe(dados.usuario("CURADOR")))
                .cenarioId();
        long turma = dados.turma();
        return comoProfessora(() -> inicio.iniciar(turma, cenario).id());
    }

    /**
     * Envia a pergunta e lê a resposta como a tela: o fragmento do HTMX volta e só então o canal SSE é
     * aberto. Se a pergunta ou a resposta forem bloqueadas, a sessão é retomada para seguir a medição.
     *
     * @throws IllegalStateException se a pergunta for recusada por outro motivo, como a moderação fora do
     *     ar: o relatório não é gravado, porque não houve medição
     */
    private Medicao medir(long sessao, int numero, String pergunta) throws Exception {
        Rodada rodada = CRONOMETRO.nova();
        long inicioDoEnvio = System.nanoTime();
        var envio = navegador.perguntar(sessao, pergunta);
        long fimDoEnvio = System.nanoTime();
        var medicao = new Medicao(numero, pergunta, inicioDoEnvio, fimDoEnvio, rodada);
        if (envio.statusCode() != 200) {
            if (!retomarSePausada(sessao)) {
                throw new IllegalStateException(String.format(
                        "Pergunta %d recusada com HTTP %d: %s", numero, envio.statusCode(), envio.body().strip()));
            }
            medicao.resultado = Resultado.PERGUNTA_BLOQUEADA;
            medicao.detalhe = "HTTP " + envio.statusCode();
            return medicao;
        }

        int status = navegador.resposta(sessao, medicao::receber);
        if (status != 200) {
            throw new IllegalStateException(String.format("Canal da pergunta %d recusado com HTTP %d", numero, status));
        }
        medicao.resultado = medicao.resultadoDoCanal();
        if (medicao.resultado == Resultado.RESPOSTA_BLOQUEADA) {
            retomarSePausada(sessao);
        } else if (medicao.resultado == Resultado.FALHA) {
            // A pergunta continua pendente; a tela tentaria de novo, e a próxima pergunta precisa disso.
            List<EventoSse> nova = new ArrayList<>();
            navegador.resposta(sessao, nova::add);
            assertThat(nova).as("segunda tentativa da pergunta %d", numero)
                    .noneMatch(evento -> evento.nome().equals("falha"));
        }
        return medicao;
    }

    /** @return se a sessão estava pausada pelo filtro de conteúdo */
    private boolean retomarSePausada(long sessao) {
        return comoProfessora(() -> {
            if (controles.estado(sessao).status() != StatusDaSessao.PAUSADA) {
                return false;
            }
            controles.retomar(sessao);
            return true;
        });
    }

    private <T> T comoProfessora(Supplier<T> acao) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(professora, null, "ROLE_PROFESSOR"));
        try {
            return acao.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Relatório
    // ---------------------------------------------------------------------------------------------

    private String relatorio(
            List<Medicao> medicoes, Medicao aquecimento, long intervaloS, long sessao, String estrategia) {
        // Resposta bloqueada antes da primeira frase não aparece por regra (RN-20): fica fora da conta.
        // Falha do provedor no meio da resposta conta como acima da meta: a turma ficou sem resposta.
        List<Medicao> consideradas = medicoes.stream()
                .filter(m -> m.primeiraFraseNaTela() != null || !m.resultado.bloqueio())
                .toList();
        long dentroDaMeta = consideradas.stream()
                .filter(m -> m.primeiraFraseNaTela() != null && m.primeiraFraseNaTela() <= META_PRIMEIRA_FRASE_S)
                .count();
        double proporcao = consideradas.isEmpty() ? 0 : (double) dentroDaMeta / consideradas.size();
        boolean atingido = !consideradas.isEmpty() && proporcao >= PROPORCAO_EXIGIDA;
        List<Double> totais = valores(medicoes, Medicao::total);
        long totaisDentro = totais.stream().filter(t -> t <= META_TOTAL_S).count();
        Map<String, Object> consumo = jdbc.queryForMap(
                """
                SELECT count(*) AS respostas, sum(r.tokens_entrada) AS entrada, sum(r.tokens_saida) AS saida,
                       sum(r.custo) AS custo
                FROM mensagem m JOIN resposta_ia_meta r ON r.id = m.resposta_ia_meta_id
                WHERE m.sessao_id = ?""",
                sessao);

        var md = new StringBuilder();
        md.append("# Resultados: primeira frase aprovada na tela (issues #19 e #80)\n\n");
        md.append(String.format(Locale.ROOT,
                "Gerado em %s. Modelo `%s`, esforço de raciocínio `%s`, nível de serviço `%s`, moderação `%s`. "
                        + "%d perguntas de referência numa sessão só, com %d s entre uma resposta e a próxima "
                        + "pergunta. Rodado em Java %s, %s.%n%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                ambiente.getProperty("spring.ai.openai.chat.options.model"),
                ambiente.getProperty("spring.ai.openai.chat.options.reasoning-effort"),
                ambiente.getProperty("spring.ai.openai.chat.options.service-tier", "padrão do projeto"),
                ambiente.getProperty("spring.ai.openai.moderation.options.model"),
                medicoes.size(), intervaloS, System.getProperty("java.version"), System.getProperty("os.name")));
        md.append("**Estratégia:** ").append(estrategia).append("\n\n");

        md.append("## Critério (RN-20 e RNF-03)\n\n");
        md.append(String.format(Locale.ROOT,
                "- **Primeira frase aprovada na tela em até %.0f s:** %d de %d respostas (%.1f%%). "
                        + "A meta é %.0f%%.%n",
                META_PRIMEIRA_FRASE_S, dentroDaMeta, consideradas.size(), proporcao * 100, PROPORCAO_EXIGIDA * 100));
        md.append(String.format(Locale.ROOT,
                "- **Resposta completa em até %.0f s:** %d de %d respostas exibidas.%n",
                META_TOTAL_S, totaisDentro, totais.size()));
        int foraDaConta = medicoes.size() - consideradas.size();
        if (foraDaConta > 0) {
            md.append(String.format(Locale.ROOT,
                    "- %d resposta(s) bloqueada(s) antes da primeira frase ficaram fora da conta (RN-20).%n",
                    foraDaConta));
        }
        md.append(atingido
                ? "- **Resultado: critério atingido nesta execução.** O RNF-03 pede o resultado em mais de uma "
                        + "execução, em horários diferentes (issue #80).\n\n"
                : "- **Resultado: critério não atingido nesta execução.**\n\n");

        md.append("## Comparação com a linha de base\n\n");
        md.append("A linha de base junta as 90 respostas dos `resultados02.md` a `resultados04.md`, sem nenhuma "
                + "estratégia e com o código da issue #80 já instrumentado. A diferença é contra ela. A medição "
                + "original (`resultados01.md`, issue #19) fica só como referência: rodou de madrugada, com o "
                + "provedor mais rápido, e não serve para medir o ganho de uma estratégia. Em segundos; na "
                + "diferença, negativo é mais rápido.\n\n");
        md.append("| Indicador | Original (01) | Linha de base (02 a 04) | Esta execução | Diferença |\n"
                + "| --- | ---: | ---: | ---: | ---: |\n");
        md.append(String.format(Locale.ROOT,
                "| 1ª frase na tela em até 3 s | %.1f%% | %.1f%% | %.1f%% | %+.1f p.p. |%n",
                Original.PROPORCAO * 100, LinhaDeBase.PROPORCAO * 100, proporcao * 100,
                (proporcao - LinhaDeBase.PROPORCAO) * 100));
        Estatistica naTela = Estatistica.de(valores(medicoes, Medicao::primeiraFraseNaTela));
        Estatistica primeiroTrecho = Estatistica.de(valores(medicoes, Medicao::primeiroTrechoDoModelo));
        Estatistica espera = Estatistica.de(valores(medicoes, Medicao::esperaDoPrimeiroTrecho));
        comparar(md, "1ª frase na tela, média", Original.NA_TELA_MEDIA, LinhaDeBase.NA_TELA_MEDIA, naTela,
                Estatistica::media);
        comparar(md, "1ª frase na tela, p95", Original.NA_TELA_P95, LinhaDeBase.NA_TELA_P95, naTela, Estatistica::p95);
        comparar(md, "1ª frase na tela, máximo", Original.NA_TELA_MAXIMO, LinhaDeBase.NA_TELA_MAXIMO, naTela,
                Estatistica::maximo);
        comparar(md, "Espera do provedor até o 1º trecho, média", null, LinhaDeBase.ESPERA_MEDIA, espera,
                Estatistica::media);
        comparar(md, "Espera do provedor até o 1º trecho, p95", null, LinhaDeBase.ESPERA_P95, espera,
                Estatistica::p95);
        comparar(md, "1º trecho do modelo, média", Original.PRIMEIRO_TRECHO_MEDIA, LinhaDeBase.PRIMEIRO_TRECHO_MEDIA,
                primeiroTrecho, Estatistica::media);
        comparar(md, "1º trecho do modelo, p95", Original.PRIMEIRO_TRECHO_P95, LinhaDeBase.PRIMEIRO_TRECHO_P95,
                primeiroTrecho, Estatistica::p95);
        comparar(md, "Envio da pergunta, média", Original.ENVIO_MEDIA, LinhaDeBase.ENVIO_MEDIA,
                Estatistica.de(valores(medicoes, Medicao::envio)), Estatistica::media);
        comparar(md, "Moderação da pergunta, média", Original.MODERACAO_DA_PERGUNTA_MEDIA,
                LinhaDeBase.MODERACAO_DA_PERGUNTA_MEDIA,
                Estatistica.de(valores(medicoes, Medicao::moderacaoDaPergunta)), Estatistica::media);
        comparar(md, "Moderação da 1ª frase, média", Original.MODERACAO_DA_FRASE_MEDIA,
                LinhaDeBase.MODERACAO_DA_FRASE_MEDIA,
                Estatistica.de(valores(medicoes, Medicao::moderacaoDaPrimeiraFrase)), Estatistica::media);
        comparar(md, "Resposta completa, média", Original.TOTAL_MEDIA, LinhaDeBase.TOTAL_MEDIA,
                Estatistica.de(totais), Estatistica::media);

        md.append("\n## Tempo por etapa\n\n");
        md.append("Em segundos. O p95 usa o método do posto mais próximo: com 30 respostas, é a 29ª mais rápida. "
                + "Os **instantes** contam a partir do clique em enviar (início da requisição HTMX da pergunta); as "
                + "**durações** são o tempo de uma etapa só. As etapas do meio vêm das observações da aplicação e "
                + "só explicam onde o tempo foi gasto.\n\n");
        md.append("| Etapa | Média | Mediana | p95 | Máximo |\n| --- | ---: | ---: | ---: | ---: |\n");
        md.append("| _Pergunta (requisição HTMX)_ | | | | |\n");
        linha(md, "Requisição da pergunta no servidor (duração, HTTP)", valores(medicoes, Medicao::requisicaoDaPergunta));
        linha(md, "Conferência da sessão no banco (duração)", valores(medicoes, Medicao::conferenciaDaSessao));
        linha(md, "Moderação da pergunta (duração)", valores(medicoes, Medicao::moderacaoDaPergunta));
        linha(md, "Gravação da pergunta no banco (duração)", valores(medicoes, Medicao::gravacaoDaPerguntaNoBanco));
        linha(md, "Gravação da pergunta no Redis (duração)", valores(medicoes, Medicao::gravacaoDaPerguntaNoRedis));
        linha(md, "Envio da pergunta: fragmento do HTMX de volta (instante)", valores(medicoes, Medicao::envio));
        md.append("| _Resposta (canal SSE)_ | | | | |\n");
        linha(md, "Abertura do canal: do fragmento ao pedido do SSE no servidor (duração)",
                valores(medicoes, Medicao::aberturaDoCanal));
        linha(md, "Espera da thread da resposta (duração)", valores(medicoes, Medicao::esperaDaThread));
        linha(md, "Preparo da resposta: sessão, estado e cenário (duração)", valores(medicoes, Medicao::preparoDaResposta));
        linha(md, "Montagem do prompt (duração)", valores(medicoes, Medicao::montagemDoPrompt));
        linha(md, "Espera do provedor até o 1º trecho (duração)", valores(medicoes, Medicao::esperaDoPrimeiroTrecho));
        linha(md, "Primeiro trecho do modelo (instante)", valores(medicoes, Medicao::primeiroTrechoDoModelo));
        linha(md, "Agrupamento de frases até a 1ª frase (duração somada)",
                valores(medicoes, Medicao::agrupamentoAtePrimeiraFrase));
        linha(md, "Primeira frase completa (instante)", valores(medicoes, Medicao::primeiraFraseCompleta));
        linha(md, "Moderação da primeira frase (duração)", valores(medicoes, Medicao::moderacaoDaPrimeiraFrase));
        linha(md, "Envio da primeira frase pelo SSE (duração)", valores(medicoes, Medicao::envioDaPrimeiraFrase));
        linha(md, "**Primeira frase aprovada na tela (instante)**", valores(medicoes, Medicao::primeiraFraseNaTela));
        linha(md, "Resposta completa: evento `fim` (instante)", totais);
        md.append(String.format(Locale.ROOT,
                "%nAquecimento, fora da conta (sessão à parte, primeira chamada da aplicação): primeira frase na "
                        + "tela em %s s.%n%n",
                formatar(aquecimento.primeiraFraseNaTela())));

        md.append(String.format(Locale.ROOT,
                "Consumo das %s respostas gravadas: %s tokens de entrada, %s de saída, US$ %s.%n%n",
                consumo.get("respostas"), consumo.get("entrada"), consumo.get("saida"), consumo.get("custo")));

        md.append("## Por pergunta\n\n");
        md.append("Instantes a partir do clique em enviar, menos a abertura do canal e a espera do 1º trecho, que "
                + "são durações.\n\n");
        md.append("| # | Pergunta | Envio | Abertura do canal | Espera do 1º trecho | 1º trecho do modelo "
                + "| 1ª frase completa | **1ª frase na tela** | Total | Frases | Palavras | Resultado |\n");
        md.append("| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (Medicao m : medicoes) {
            md.append(String.format(Locale.ROOT, "| %d | %s | %s | %s | %s | %s | %s | %s | %s | %d | %d | %s |%n",
                    m.numero, m.pergunta.replace("|", "\\|"), formatar(m.envio()), formatar(m.aberturaDoCanal()),
                    formatar(m.esperaDoPrimeiroTrecho()), formatar(m.primeiroTrechoDoModelo()),
                    formatar(m.primeiraFraseCompleta()), marcarAcima(m.primeiraFraseNaTela()), formatar(m.total()),
                    m.frases.size(), m.palavras(), m.descricao()));
        }

        md.append("\n## Respostas exibidas\n\n");
        md.append("Para conferir o corte das frases. Cada frase aprovada, na ordem, separada por ` ‖ `.\n\n");
        for (Medicao m : medicoes) {
            md.append(String.format("%d. **%s**%n   %s%n", m.numero, m.pergunta,
                    m.frases.isEmpty() ? "_(nada exibido)_" : String.join(" ‖ ", m.frases)));
        }
        return md.toString();
    }

    /**
     * A medição original, do {@code resultados01.md} (issue #19), copiada aqui para cada execução se
     * comparar com ela mesmo que o arquivo mude de formato.
     */
    static final class Original {

        static final double PROPORCAO = 28.0 / 30;
        static final double NA_TELA_MEDIA = 2.28;
        static final double NA_TELA_P95 = 3.07;
        static final double NA_TELA_MAXIMO = 3.16;
        static final double PRIMEIRO_TRECHO_MEDIA = 1.66;
        static final double PRIMEIRO_TRECHO_P95 = 2.38;
        static final double ENVIO_MEDIA = 0.44;
        static final double MODERACAO_DA_PERGUNTA_MEDIA = 0.38;
        static final double MODERACAO_DA_FRASE_MEDIA = 0.30;
        static final double TOTAL_MEDIA = 3.19;

        private Original() {}
    }

    /**
     * A linha de base da issue #80: as 90 respostas dos {@code resultados02.md} a {@code resultados04.md},
     * juntas. Média, p95 (posto mais próximo, a 86ª de 90) e máximo saem da tabela por pergunta; as médias
     * das moderações, que não estão nela, são a média das três execuções, todas com 30 respostas.
     */
    static final class LinhaDeBase {

        static final double PROPORCAO = 59.0 / 90;
        static final double NA_TELA_MEDIA = 2.82;
        static final double NA_TELA_P95 = 3.85;
        static final double NA_TELA_MAXIMO = 4.69;
        static final double ESPERA_MEDIA = 1.66;
        static final double ESPERA_P95 = 2.65;
        static final double PRIMEIRO_TRECHO_MEDIA = 2.10;
        static final double PRIMEIRO_TRECHO_P95 = 3.12;
        static final double ENVIO_MEDIA = 0.41;
        static final double MODERACAO_DA_PERGUNTA_MEDIA = 0.36;
        static final double MODERACAO_DA_FRASE_MEDIA = 0.34;
        static final double TOTAL_MEDIA = 3.92;

        private LinhaDeBase() {}
    }

    /** Uma linha da comparação; sem o valor da original (etapa que ela não media), a coluna fica com "–". */
    private static void comparar(
            StringBuilder md,
            String indicador,
            Double original,
            double linhaDeBase,
            Estatistica atual,
            Function<Estatistica, Double> valor) {
        String daOriginal = original == null ? "–" : String.format(Locale.ROOT, "%.2f", original);
        if (atual == null) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %.2f | – | – |%n", indicador, daOriginal, linhaDeBase));
            return;
        }
        double agora = valor.apply(atual);
        md.append(String.format(Locale.ROOT, "| %s | %s | %.2f | %.2f | %+.2f |%n",
                indicador, daOriginal, linhaDeBase, agora, agora - linhaDeBase));
    }

    /** Média, mediana, p95 e máximo de uma etapa. */
    record Estatistica(double media, double mediana, double p95, double maximo) {

        /** @return nulo se não houver valores */
        static Estatistica de(List<Double> valores) {
            if (valores.isEmpty()) {
                return null;
            }
            List<Double> ordenados = valores.stream().sorted().toList();
            int n = ordenados.size();
            double media = valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double mediana = n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
            return new Estatistica(media, mediana, percentil95(ordenados), ordenados.get(n - 1));
        }
    }

    private static List<Double> valores(List<Medicao> medicoes, Function<Medicao, Double> etapa) {
        return medicoes.stream().map(etapa).filter(v -> v != null).toList();
    }

    private static void linha(StringBuilder md, String etapa, List<Double> valores) {
        Estatistica e = Estatistica.de(valores);
        if (e == null) {
            md.append("| ").append(etapa).append(" | – | – | – | – |\n");
            return;
        }
        md.append(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %.3f | %.3f |%n",
                etapa, e.media(), e.mediana(), e.p95(), e.maximo()));
    }

    /** Posto mais próximo: o menor valor que deixa pelo menos 95% das amostras abaixo ou iguais a ele. */
    static double percentil95(List<Double> ordenados) {
        return ordenados.get((int) Math.ceil(0.95 * ordenados.size()) - 1);
    }

    private static String formatar(Double segundos) {
        return segundos == null ? "–" : String.format(Locale.ROOT, "%.2f", segundos);
    }

    private static String marcarAcima(Double segundos) {
        return segundos != null && segundos > META_PRIMEIRA_FRASE_S ? "**" + formatar(segundos) + "**" : formatar(segundos);
    }

    // ---------------------------------------------------------------------------------------------
    // Medição
    // ---------------------------------------------------------------------------------------------

    enum Resultado {
        EXIBIDA("exibida"),
        PERGUNTA_BLOQUEADA("pergunta bloqueada"),
        RESPOSTA_BLOQUEADA("resposta bloqueada"),
        FALHA("falha");

        private final String descricao;

        Resultado(String descricao) {
            this.descricao = descricao;
        }

        boolean bloqueio() {
            return this == PERGUNTA_BLOQUEADA || this == RESPOSTA_BLOQUEADA;
        }
    }

    /**
     * Chaves das observações na rodada: o nome da observação e o valor da etapa. A da requisição HTTP não
     * tem etapa: a primeira da rodada é a da pergunta, e a segunda, a do canal SSE.
     */
    static final String HTTP = "http";

    static final String CONFERENCIA = etapa(EtapasDaConversa.Etapa.CONFERENCIA_DA_SESSAO);
    static final String GRAVACAO_DA_PERGUNTA = etapa(EtapasDaConversa.Etapa.GRAVACAO_DA_PERGUNTA);
    static final String ESPERA_DA_THREAD = etapa(EtapasDaConversa.Etapa.ESPERA_DA_THREAD);
    static final String PREPARO = etapa(EtapasDaConversa.Etapa.PREPARO_DA_RESPOSTA);
    static final String AGRUPAMENTO = etapa(EtapasDaConversa.Etapa.AGRUPAMENTO_DE_FRASES);
    static final String ENVIO_SSE_TRECHO = etapa(EtapasDaConversa.Etapa.ENVIO_SSE_TRECHO);
    static final String MODERACAO = ChamadasAoProvedor.OBSERVACAO + ":moderacao";
    static final String MONTAGEM_DO_PROMPT = ChamadasAoProvedor.OBSERVACAO + ":montagem-do-prompt";
    static final String ESPERA_DO_PRIMEIRO_TRECHO = ChamadasAoProvedor.OBSERVACAO + ":espera-do-primeiro-trecho";
    static final String REDIS_GRAVAR = "lab.sessoes.redis:gravar";

    private static String etapa(EtapasDaConversa.Etapa etapa) {
        return EtapasDaConversa.OBSERVACAO + ":" + etapa.valor();
    }

    /** Uma pergunta: os instantes vêm de {@link System#nanoTime()}, na mesma JVM do servidor. */
    private static final class Medicao {

        final int numero;
        final String pergunta;
        final long inicio;
        final long fimDoEnvio;
        final Rodada rodada;
        final List<String> frases = new ArrayList<>();
        Long primeiraFrase;
        Long fim;
        boolean bloqueio;
        boolean falha;
        Resultado resultado;
        String detalhe;

        Medicao(int numero, String pergunta, long inicio, long fimDoEnvio, Rodada rodada) {
            this.numero = numero;
            this.pergunta = pergunta;
            this.inicio = inicio;
            this.fimDoEnvio = fimDoEnvio;
            this.rodada = rodada;
        }

        void receber(EventoSse evento) {
            switch (evento.nome()) {
                case "trecho" -> {
                    if (primeiraFrase == null) {
                        primeiraFrase = evento.chegada();
                    }
                    frases.add(HtmlUtils.htmlUnescape(evento.dados()).strip());
                }
                case "bloqueio" -> bloqueio = true;
                case "falha" -> falha = true;
                case "fim" -> fim = evento.chegada();
                default -> {}
            }
        }

        Resultado resultadoDoCanal() {
            if (bloqueio) {
                detalhe = frases.isEmpty() ? null : "depois de " + frases.size() + " frase(s)";
                return Resultado.RESPOSTA_BLOQUEADA;
            }
            return falha ? Resultado.FALHA : Resultado.EXIBIDA;
        }

        Double envio() {
            return segundos(fimDoEnvio);
        }

        Double requisicaoDaPergunta() {
            return duracao(rodada.enesima(HTTP, 0));
        }

        Double conferenciaDaSessao() {
            return duracao(rodada.enesima(CONFERENCIA, 0));
        }

        Double moderacaoDaPergunta() {
            return duracao(rodada.enesima(MODERACAO, 0));
        }

        /** A transação da pergunta, sem o Redis gravado logo depois da confirmação. */
        Double gravacaoDaPerguntaNoBanco() {
            Double transacao = duracao(rodada.enesima(GRAVACAO_DA_PERGUNTA, 0));
            Double redis = gravacaoDaPerguntaNoRedis();
            return transacao == null ? null : transacao - (redis == null ? 0 : redis);
        }

        Double gravacaoDaPerguntaNoRedis() {
            return rodada.enesima(GRAVACAO_DA_PERGUNTA, 0)
                    .map(transacao -> rodada.soma(REDIS_GRAVAR, r -> transacao.contem(r)))
                    .orElse(null);
        }

        /** Do fragmento de volta no cliente até o servidor receber o pedido do canal. */
        Double aberturaDoCanal() {
            return rodada.enesima(HTTP, 1).map(canal -> (canal.inicio() - fimDoEnvio) / 1e9).orElse(null);
        }

        Double esperaDaThread() {
            return duracao(rodada.enesima(ESPERA_DA_THREAD, 0));
        }

        Double preparoDaResposta() {
            return duracao(rodada.enesima(PREPARO, 0));
        }

        Double montagemDoPrompt() {
            return duracao(rodada.enesima(MONTAGEM_DO_PROMPT, 0));
        }

        /** Do pedido ao provedor até o primeiro trecho chegar. */
        Double esperaDoPrimeiroTrecho() {
            return duracao(rodada.enesima(ESPERA_DO_PRIMEIRO_TRECHO, 0));
        }

        Double primeiroTrechoDoModelo() {
            return rodada.enesima(ESPERA_DO_PRIMEIRO_TRECHO, 0).map(espera -> segundos(espera.fim())).orElse(null);
        }

        Double agrupamentoAtePrimeiraFrase() {
            return rodada.enesima(MODERACAO, 1)
                    .map(frase -> rodada.soma(AGRUPAMENTO, a -> a.inicio() <= frase.inicio()))
                    .orElse(null);
        }

        /** O agrupador fechou a primeira frase quando a moderação dela começou. */
        Double primeiraFraseCompleta() {
            return rodada.enesima(MODERACAO, 1).map(m -> segundos(m.inicio())).orElse(null);
        }

        Double moderacaoDaPrimeiraFrase() {
            return duracao(rodada.enesima(MODERACAO, 1));
        }

        Double envioDaPrimeiraFrase() {
            return duracao(rodada.enesima(ENVIO_SSE_TRECHO, 0));
        }

        Double primeiraFraseNaTela() {
            return segundos(primeiraFrase);
        }

        /** Só vale para a resposta exibida: depois de um bloqueio, o canal fecha antes do fim do modelo. */
        Double total() {
            return resultado == Resultado.EXIBIDA ? segundos(fim) : null;
        }

        int palavras() {
            String texto = String.join(" ", frases).strip();
            return texto.isEmpty() ? 0 : texto.split("\\s+").length;
        }

        String descricao() {
            return resultado.descricao + (detalhe == null ? "" : " (" + detalhe + ")");
        }

        String resumo() {
            return String.format(Locale.ROOT,
                    "[%2d] 1ª frase na tela: %s s | espera do 1º trecho: %s s | total: %s s | %s | %s",
                    numero, formatar(primeiraFraseNaTela()), formatar(esperaDoPrimeiroTrecho()), formatar(total()),
                    descricao(), pergunta);
        }

        private Double segundos(Long instante) {
            return instante == null ? null : (instante - inicio) / 1e9;
        }

        private static Double duracao(Optional<Registro> registro) {
            return registro.map(Registro::segundos).orElse(null);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Cronômetro pelas observações da aplicação
    // ---------------------------------------------------------------------------------------------

    /** Uma observação que terminou, com os instantes de {@link System#nanoTime()}. */
    record Registro(String chave, long inicio, long fim) {

        double segundos() {
            return (fim - inicio) / 1e9;
        }

        boolean contem(Registro outro) {
            return outro.inicio() >= inicio && outro.fim() <= fim;
        }
    }

    /** As observações de uma pergunta, da requisição da pergunta ao fim da resposta. */
    static final class Rodada {

        private final List<Registro> registros = Collections.synchronizedList(new ArrayList<>());

        void registrar(Registro registro) {
            registros.add(registro);
        }

        /** A observação de ordem {@code ordem} com esta chave, pela ordem de início. */
        Optional<Registro> enesima(String chave, int ordem) {
            synchronized (registros) {
                return registros.stream()
                        .filter(r -> r.chave().equals(chave))
                        .sorted(Comparator.comparingLong(Registro::inicio))
                        .skip(ordem)
                        .findFirst();
            }
        }

        double soma(String chave, Predicate<Registro> filtro) {
            synchronized (registros) {
                return registros.stream()
                        .filter(r -> r.chave().equals(chave) && filtro.test(r))
                        .mapToDouble(Registro::segundos)
                        .sum();
            }
        }
    }

    /**
     * Recebe as observações da aplicação. Cada uma guarda a rodada vigente quando começa: assim, o resto
     * de uma resposta bloqueada, que o servidor continua consumindo, não entra na medição da pergunta
     * seguinte. Não muda nada do que a aplicação faz.
     */
    static final class Cronometro implements ObservationHandler<Observation.Context> {

        private static final String RODADA = Cronometro.class.getName() + ".rodada";
        private static final String INICIO = Cronometro.class.getName() + ".inicio";

        private volatile Rodada atual = new Rodada();

        Rodada nova() {
            atual = new Rodada();
            return atual;
        }

        @Override
        public void onStart(Observation.Context contexto) {
            contexto.put(RODADA, atual);
            contexto.put(INICIO, System.nanoTime());
        }

        @Override
        public void onStop(Observation.Context contexto) {
            long fim = System.nanoTime();
            Rodada rodada = contexto.get(RODADA);
            Long inicio = contexto.get(INICIO);
            String chave = chave(contexto);
            if (rodada != null && inicio != null && chave != null) {
                rodada.registrar(new Registro(chave, inicio, fim));
            }
        }

        /** @return nulo para as observações que o spike não usa */
        private static String chave(Observation.Context contexto) {
            String nome = contexto.getName();
            if ("http.server.requests".equals(nome)) {
                return HTTP;
            }
            if (nome == null || !nome.startsWith("lab.")) {
                return null;
            }
            return contexto.getLowCardinalityKeyValues().stream()
                    .findFirst()
                    .map(KeyValue::getValue)
                    .map(valor -> nome + ":" + valor)
                    .orElse(null);
        }

        @Override
        public boolean supportsContext(Observation.Context contexto) {
            return true;
        }
    }

    /** O cronômetro entra como mais um handler das observações da aplicação. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Cronometragem {

        @Bean
        Cronometro cronometro() {
            return CRONOMETRO;
        }
    }
}
