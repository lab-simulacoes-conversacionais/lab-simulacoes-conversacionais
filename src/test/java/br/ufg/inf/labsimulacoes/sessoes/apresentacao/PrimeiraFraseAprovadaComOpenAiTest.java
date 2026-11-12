package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.acesso.aplicacao.CadastroDeUsuarios;
import br.ufg.inf.labsimulacoes.acesso.dominio.Perfil;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import br.ufg.inf.labsimulacoes.ia.RespostaDoPersonagem;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.SugestaoDeCenario;
import br.ufg.inf.labsimulacoes.sessoes.DadosDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.ControlesDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.aplicacao.InicioDeSessao;
import br.ufg.inf.labsimulacoes.sessoes.apresentacao.Navegador.EventoSse;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
 * resposta é lida no canal SSE, com requisições HTTP reais e o login da professora, como na tela. Um
 * cronômetro em volta do adaptador de IA separa o tempo em etapas, só para explicar o resultado.
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

    /** RNF-03: primeiro trecho em até 3 s e resposta completa em até 10 s, em 95% das interações. */
    static final double META_PRIMEIRA_FRASE_S = 3.0;

    static final double META_TOTAL_S = 10.0;
    static final double PROPORCAO_EXIGIDA = 0.95;

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

        // Aquecimento numa sessão à parte, para a medição não pegar a primeira chamada da JVM.
        Medicao aquecimento = medir(novaSessao(dados), 0, "Oi! Tudo bem?");

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

        String relatorio = relatorio(medicoes, aquecimento, intervaloS, sessao);
        Files.writeString(PASTA.resolve("resultados.md"), relatorio);
        System.out.printf("%n%s%n", relatorio);
        assertThat(medicoes).hasSize(PERGUNTAS_DE_REFERENCIA);
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
     */
    private Medicao medir(long sessao, int numero, String pergunta) throws Exception {
        Rodada rodada = CRONOMETRO.nova();
        long inicioDoEnvio = System.nanoTime();
        var envio = navegador.perguntar(sessao, pergunta);
        long fimDoEnvio = System.nanoTime();
        var medicao = new Medicao(numero, pergunta, inicioDoEnvio, fimDoEnvio, rodada);
        if (envio.statusCode() != 200) {
            medicao.resultado = retomarSePausada(sessao) ? Resultado.PERGUNTA_BLOQUEADA : Resultado.RECUSADA;
            medicao.detalhe = "HTTP " + envio.statusCode();
            return medicao;
        }

        int status = navegador.resposta(sessao, medicao::receber);
        medicao.resultado = medicao.resultadoDoCanal(status);
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

    private String relatorio(List<Medicao> medicoes, Medicao aquecimento, long intervaloS, long sessao) {
        // Resposta bloqueada antes da primeira frase não aparece por regra (RN-20): fica fora da conta.
        // Falha ou recusa conta como acima da meta, porque a turma ficou sem resposta.
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
        md.append("# Resultados: primeira frase aprovada na tela (issue #19)\n\n");
        md.append(String.format(Locale.ROOT,
                "Gerado em %s. Modelo `%s`, esforço de raciocínio `%s`, moderação `%s`. %d perguntas de "
                        + "referência numa sessão só, com %d s entre uma resposta e a próxima pergunta. Rodado em "
                        + "Java %s, %s.%n%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                ambiente.getProperty("spring.ai.openai.chat.options.model"),
                ambiente.getProperty("spring.ai.openai.chat.options.reasoning-effort"),
                ambiente.getProperty("spring.ai.openai.moderation.options.model"),
                medicoes.size(), intervaloS, System.getProperty("java.version"), System.getProperty("os.name")));

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
                ? "- **Resultado: critério atingido.** A moderação por frase continua (Stack, seção 5, passo 7).\n\n"
                : "- **Resultado: critério não atingido.** Pela Stack, seção 5, a moderação passa a ser da resposta "
                        + "inteira, com o aviso \"personagem pensando\", e o grupo leva ao PO a revisão do RNF-03.\n\n");

        md.append("## Tempo por etapa\n\n");
        md.append("Em segundos, a partir do clique em enviar (início da requisição HTMX da pergunta). O p95 usa o "
                + "método do posto mais próximo: com 30 respostas, é a 29ª mais rápida. As etapas do meio vêm do "
                + "cronômetro em volta do adaptador de IA e só explicam onde o tempo foi gasto.\n\n");
        md.append("| Etapa | Média | Mediana | p95 | Máximo |\n| --- | ---: | ---: | ---: | ---: |\n");
        linha(md, "Envio da pergunta (fragmento do HTMX de volta)", valores(medicoes, Medicao::envio));
        linha(md, "Moderação da pergunta (duração)", valores(medicoes, Medicao::moderacaoDaPergunta));
        linha(md, "Primeiro trecho do modelo", valores(medicoes, Medicao::primeiroTrechoDoModelo));
        linha(md, "Primeira frase completa", valores(medicoes, Medicao::primeiraFraseCompleta));
        linha(md, "Moderação da primeira frase (duração)", valores(medicoes, Medicao::moderacaoDaPrimeiraFrase));
        linha(md, "**Primeira frase aprovada na tela**", valores(medicoes, Medicao::primeiraFraseNaTela));
        linha(md, "Resposta completa (evento `fim`)", totais);
        md.append(String.format(Locale.ROOT,
                "%nAquecimento, fora da conta (sessão à parte, primeira chamada da aplicação): primeira frase na "
                        + "tela em %s s.%n%n",
                formatar(aquecimento.primeiraFraseNaTela())));

        md.append(String.format(Locale.ROOT,
                "Consumo das %s respostas gravadas: %s tokens de entrada, %s de saída, US$ %s.%n%n",
                consumo.get("respostas"), consumo.get("entrada"), consumo.get("saida"), consumo.get("custo")));

        md.append("## Por pergunta\n\n");
        md.append("| # | Pergunta | Envio | 1º trecho do modelo | 1ª frase completa | **1ª frase na tela** | Total "
                + "| Frases | Palavras | Resultado |\n");
        md.append("| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (Medicao m : medicoes) {
            md.append(String.format(Locale.ROOT, "| %d | %s | %s | %s | %s | %s | %s | %d | %d | %s |%n",
                    m.numero, m.pergunta.replace("|", "\\|"), formatar(m.envio()), formatar(m.primeiroTrechoDoModelo()),
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

    private static List<Double> valores(List<Medicao> medicoes, Function<Medicao, Double> etapa) {
        return medicoes.stream().map(etapa).filter(v -> v != null).toList();
    }

    private static void linha(StringBuilder md, String etapa, List<Double> valores) {
        if (valores.isEmpty()) {
            md.append("| ").append(etapa).append(" | – | – | – | – |\n");
            return;
        }
        List<Double> ordenados = valores.stream().sorted().toList();
        double media = valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        int n = ordenados.size();
        double mediana = n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
        md.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %.2f |%n",
                etapa, media, mediana, p95(ordenados), ordenados.get(n - 1)));
    }

    /** Posto mais próximo: o menor valor que deixa pelo menos 95% das amostras abaixo ou iguais a ele. */
    static double p95(List<Double> ordenados) {
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
        FALHA("falha"),
        RECUSADA("recusada");

        private final String descricao;

        Resultado(String descricao) {
            this.descricao = descricao;
        }

        boolean bloqueio() {
            return this == PERGUNTA_BLOQUEADA || this == RESPOSTA_BLOQUEADA;
        }
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

        Resultado resultadoDoCanal(int status) {
            if (status != 200) {
                detalhe = "HTTP " + status;
                return Resultado.RECUSADA;
            }
            if (bloqueio) {
                detalhe = frases.isEmpty() ? null : "depois de " + frases.size() + " frase(s)";
                return Resultado.RESPOSTA_BLOQUEADA;
            }
            return falha ? Resultado.FALHA : Resultado.EXIBIDA;
        }

        Double envio() {
            return segundos(fimDoEnvio);
        }

        Double moderacaoDaPergunta() {
            return rodada.moderacao(0).map(m -> (m[1] - m[0]) / 1e9).orElse(null);
        }

        Double primeiroTrechoDoModelo() {
            return segundos(rodada.primeiroTrecho);
        }

        /** O agrupador fechou a primeira frase quando a moderação dela começou. */
        Double primeiraFraseCompleta() {
            return rodada.moderacao(1).map(m -> segundos(m[0])).orElse(null);
        }

        Double moderacaoDaPrimeiraFrase() {
            return rodada.moderacao(1).map(m -> (m[1] - m[0]) / 1e9).orElse(null);
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
            return String.format(Locale.ROOT, "[%2d] 1ª frase na tela: %s s | total: %s s | %s | %s",
                    numero, formatar(primeiraFraseNaTela()), formatar(total()), descricao(), pergunta);
        }

        private Double segundos(Long instante) {
            return instante == null ? null : (instante - inicio) / 1e9;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Cronômetro em volta do adaptador
    // ---------------------------------------------------------------------------------------------

    /** As chamadas ao adaptador de uma pergunta: a moderação da pergunta vem primeiro, depois as frases. */
    static final class Rodada {

        private final List<long[]> moderacoes = Collections.synchronizedList(new ArrayList<>());
        private volatile Long primeiroTrecho;

        void moderou(long inicio, long fim) {
            moderacoes.add(new long[] {inicio, fim});
        }

        void recebeuTrecho(long em) {
            if (primeiroTrecho == null) {
                primeiroTrecho = em;
            }
        }

        Optional<long[]> moderacao(int ordem) {
            synchronized (moderacoes) {
                return moderacoes.stream()
                        .sorted(Comparator.comparingLong(m -> m[0]))
                        .skip(ordem)
                        .findFirst();
            }
        }
    }

    /**
     * Cada chamada guarda a rodada vigente quando começa. Assim, o resto de uma resposta bloqueada, que o
     * servidor continua consumindo, não entra na medição da pergunta seguinte.
     */
    static final class Cronometro {

        private volatile Rodada atual = new Rodada();

        Rodada nova() {
            atual = new Rodada();
            return atual;
        }

        Rodada atual() {
            return atual;
        }
    }

    /** Envolve o adaptador já com a checagem de perfil; não muda nada do que ele faz. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Cronometragem {

        @Bean
        static BeanPostProcessor cronometrarAdaptador() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String nome) {
                    return bean instanceof AdaptadorDeIa adaptador ? new AdaptadorCronometrado(adaptador) : bean;
                }
            };
        }
    }

    private record AdaptadorCronometrado(AdaptadorDeIa delegado) implements AdaptadorDeIa {

        @Override
        public RespostaDoPersonagem responder(PedidoDeFala pedido, Consumer<String> aoReceberTrecho) {
            Rodada rodada = CRONOMETRO.atual();
            return delegado.responder(pedido, trecho -> {
                rodada.recebeuTrecho(System.nanoTime());
                aoReceberTrecho.accept(trecho);
            });
        }

        @Override
        public SugestaoDeCenario sugerirCenario(PedidoDeSugestao pedido) {
            return delegado.sugerirCenario(pedido);
        }

        @Override
        public ResultadoDaModeracao moderar(String texto) {
            Rodada rodada = CRONOMETRO.atual();
            long inicio = System.nanoTime();
            try {
                return delegado.moderar(texto);
            } finally {
                rodada.moderou(inicio, System.nanoTime());
            }
        }
    }
}
