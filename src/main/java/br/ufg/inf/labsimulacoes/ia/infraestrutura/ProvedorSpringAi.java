package br.ufg.inf.labsimulacoes.ia.infraestrutura;

import br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.ResultadoDaModeracao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import br.ufg.inf.labsimulacoes.ia.dominio.Geracao;
import br.ufg.inf.labsimulacoes.ia.dominio.MensagemDoPrompt;
import br.ufg.inf.labsimulacoes.ia.dominio.PromptMontado;
import br.ufg.inf.labsimulacoes.ia.dominio.ProvedorDeIa;
import br.ufg.inf.labsimulacoes.ia.dominio.UsoDoModelo;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provedor de IA pelo Spring AI: ChatClient para as falas e as sugestões, e o modelo de moderação do
 * provedor (Stack, seção 5). O modelo e as opções vêm da configuração spring.ai.*, e trocar de provedor
 * é trocar o starter e essa configuração (RNF-02).
 *
 * <p>Nenhuma mensagem de erro repete o pedido nem a chave: a causa original fica na exceção.
 */
@Component
class ProvedorSpringAi implements ProvedorDeIa {

    private final ChatModel modeloDeConversa;
    private final ChatClient chat;
    private final ModerationModel moderacao;
    private final String nome;

    /**
     * Uma variável IA_MODELO definida mas vazia vence o padrão do application.properties e deixa o modelo
     * em branco. Sem esta conferência, a aplicação subiria e cada pergunta falharia com HTTP 400.
     */
    ProvedorSpringAi(ChatModel modeloDeConversa, ModerationModel moderacao, @Value("${ia.provedor}") String nome) {
        ChatOptions padrao = modeloDeConversa.getDefaultOptions();
        if (padrao != null && (padrao.getModel() == null || padrao.getModel().isBlank())) {
            throw new IllegalStateException("O modelo de conversa está vazio: confira a variável IA_MODELO, que não"
                    + " pode estar definida sem valor, e a propriedade spring.ai.openai.chat.options.model");
        }
        this.modeloDeConversa = modeloDeConversa;
        this.chat = ChatClient.create(modeloDeConversa);
        this.moderacao = moderacao;
        this.nome = nome;
    }

    @Override
    public String nome() {
        return nome;
    }

    /**
     * O streaming é lido na thread de quem chama. Um erro do provedor vem do iterador e vira
     * {@link FalhaNoProvedorDeIa}; um erro de {@code aoReceberTrecho} sai do corpo do laço sem mudança,
     * e fechar o stream cancela a requisição ao provedor.
     */
    @Override
    public Geracao<String> conversar(PromptMontado prompt, Consumer<String> aoReceberTrecho) {
        StringBuilder texto = new StringBuilder();
        String modelo = null;
        UsoDoModelo uso = UsoDoModelo.desconhecido();
        try (Stream<ChatResponse> respostas = doProvedor(() -> chat.prompt(paraPrompt(prompt))
                .stream()
                .chatResponse()
                .toStream())) {
            Iterator<ChatResponse> iterador = respostas.iterator();
            while (doProvedor(iterador::hasNext)) {
                ChatResponse resposta = doProvedor(iterador::next);
                String trecho = textoDe(resposta);
                if (!trecho.isEmpty()) {
                    texto.append(trecho);
                    aoReceberTrecho.accept(trecho);
                }
                modelo = modeloDe(resposta, modelo);
                uso = usoDe(resposta, uso);
            }
        }
        return new Geracao<>(texto.toString(), modeloOuPadrao(modelo), parametros(), uso);
    }

    @Override
    public Geracao<RoteiroSugerido> sugerirRoteiro(PromptMontado prompt) {
        ResponseEntity<ChatResponse, RoteiroSugerido> resposta = doProvedor(() -> chat.prompt(paraPrompt(prompt))
                .call()
                .responseEntity(RoteiroSugerido.class));
        if (resposta.entity() == null) {
            throw new FalhaNoProvedorDeIa("O provedor de IA não devolveu um roteiro", null);
        }
        ChatResponse metadados = resposta.response();
        return new Geracao<>(resposta.entity(), modeloOuPadrao(modeloDe(metadados, null)), parametros(),
                usoDe(metadados, UsoDoModelo.desconhecido()));
    }

    @Override
    public ResultadoDaModeracao moderar(String texto) {
        Moderation resultado = doProvedor(() -> moderacao.call(new ModerationPrompt(texto)).getResult().getOutput());
        List<ModerationResult> resultados = resultado.getResults() == null ? List.of() : resultado.getResults();
        boolean sinalizado = resultados.stream().anyMatch(ModerationResult::isFlagged);
        List<String> categorias = resultados.stream()
                .map(ModerationResult::getCategories)
                .filter(Objects::nonNull)
                .flatMap(ProvedorSpringAi::categoriasSinalizadas)
                .distinct()
                .toList();
        return new ResultadoDaModeracao(sinalizado, categorias);
    }

    private static Prompt paraPrompt(PromptMontado prompt) {
        List<Message> mensagens = new ArrayList<>();
        mensagens.add(new SystemMessage(prompt.instrucoes()));
        for (MensagemDoPrompt mensagem : prompt.mensagens()) {
            mensagens.add(switch (mensagem.papel()) {
                case USUARIO -> new UserMessage(mensagem.texto());
                case ASSISTENTE -> new AssistantMessage(mensagem.texto());
            });
        }
        return new Prompt(mensagens);
    }

    private static String textoDe(ChatResponse resposta) {
        if (resposta == null) {
            return "";
        }
        Generation geracao = resposta.getResult();
        if (geracao == null || geracao.getOutput() == null || geracao.getOutput().getText() == null) {
            return "";
        }
        return geracao.getOutput().getText();
    }

    /** O último trecho com modelo informado vence, e o primeiro já costuma trazê-lo. */
    private static String modeloDe(ChatResponse resposta, String atual) {
        ChatResponseMetadata metadados = resposta == null ? null : resposta.getMetadata();
        String modelo = metadados == null ? null : metadados.getModel();
        return modelo == null || modelo.isBlank() ? atual : modelo;
    }

    /**
     * No streaming, os tokens chegam só no último trecho (stream-usage). Os outros trazem uso vazio,
     * com zero, que não pode apagar o que já foi lido.
     */
    private static UsoDoModelo usoDe(ChatResponse resposta, UsoDoModelo atual) {
        ChatResponseMetadata metadados = resposta == null ? null : resposta.getMetadata();
        Usage uso = metadados == null ? null : metadados.getUsage();
        if (uso == null || uso.getTotalTokens() == null || uso.getTotalTokens() == 0) {
            return atual;
        }
        return new UsoDoModelo(uso.getPromptTokens(), uso.getCompletionTokens());
    }

    private String modeloOuPadrao(String modelo) {
        if (modelo != null) {
            return modelo;
        }
        ChatOptions padrao = modeloDeConversa.getDefaultOptions();
        return padrao == null || padrao.getModel() == null ? "desconhecido" : padrao.getModel();
    }

    /** Opções enviadas ao modelo, com os nomes da API do provedor. O modelo tem coluna própria. */
    private Map<String, Object> parametros() {
        Map<String, Object> parametros = new LinkedHashMap<>();
        ChatOptions opcoes = modeloDeConversa.getDefaultOptions();
        if (opcoes == null) {
            return parametros;
        }
        colocar(parametros, "temperature", opcoes.getTemperature());
        colocar(parametros, "top_p", opcoes.getTopP());
        colocar(parametros, "max_tokens", opcoes.getMaxTokens());
        if (opcoes instanceof OpenAiChatOptions openAi) {
            colocar(parametros, "max_completion_tokens", openAi.getMaxCompletionTokens());
            colocar(parametros, "reasoning_effort", openAi.getReasoningEffort());
            colocar(parametros, "verbosity", openAi.getVerbosity());
            colocar(parametros, "service_tier", openAi.getServiceTier());
        }
        return parametros;
    }

    private static void colocar(Map<String, Object> parametros, String nome, Object valor) {
        if (valor != null) {
            parametros.put(nome, valor);
        }
    }

    /**
     * Nomes das categorias da OpenAI, os mesmos que aparecem no painel do provedor. São só as 11 que o
     * Spring AI lê da resposta da OpenAI: as outras de {@link Categories} são de outros provedores, e a
     * OpenAI nunca as marca. Categorias novas da OpenAI, como illicit, não chegam, então um texto pode
     * vir sinalizado sem nenhuma categoria.
     */
    private static Stream<String> categoriasSinalizadas(Categories categorias) {
        Map<String, Predicate<Categories>> todas = new LinkedHashMap<>();
        todas.put("sexual", Categories::isSexual);
        todas.put("sexual/minors", Categories::isSexualMinors);
        todas.put("hate", Categories::isHate);
        todas.put("hate/threatening", Categories::isHateThreatening);
        todas.put("harassment", Categories::isHarassment);
        todas.put("harassment/threatening", Categories::isHarassmentThreatening);
        todas.put("self-harm", Categories::isSelfHarm);
        todas.put("self-harm/intent", Categories::isSelfHarmIntent);
        todas.put("self-harm/instructions", Categories::isSelfHarmInstructions);
        todas.put("violence", Categories::isViolence);
        todas.put("violence/graphic", Categories::isViolenceGraphic);
        return todas.entrySet().stream().filter(c -> c.getValue().test(categorias)).map(Map.Entry::getKey);
    }

    private static <T> T doProvedor(Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (FalhaNoProvedorDeIa e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FalhaNoProvedorDeIa("O provedor de IA falhou: " + e.getClass().getSimpleName(), e);
        }
    }
}
