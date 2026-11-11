package br.ufg.inf.labsimulacoes.ia.dominio;

import br.ufg.inf.labsimulacoes.ia.CenarioDaConversa;
import br.ufg.inf.labsimulacoes.ia.MensagemDaConversa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeFala;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Montador com os templates versionados (RNF-11), sem nada do provedor (RNF-02).
 *
 * <p>A mensagem de sistema é só o template preenchido com os dados da versão do cenário. Depois dela
 * vêm o histórico, cada fala com o próprio papel, e por último a pergunta da turma, numa mensagem de
 * usuário separada. Nada do que a turma digita entra nas instruções; os dados do cenário entram porque
 * passaram pela curadoria (RNF-09).
 *
 * <p>Com o histórico vazio, no início da sessão ou depois de reiniciar, a seção {@code abertura} entra
 * nas instruções, para a primeira fala apresentar o personagem como simulação (RN-14).
 */
public final class MontadorComTemplates implements MontadorDePrompt {

    public static final String SECAO_INSTRUCOES = "instrucoes";
    public static final String SECAO_ABERTURA = "abertura";

    /** Marcadores que o template do personagem pode usar. */
    static final List<String> MARCADORES_DO_PERSONAGEM = List.of(
            "titulo", "personagem", "papel_da_ia", "contexto", "objetivos", "limites_de_assunto", "fontes",
            "componente", "ano", "habilidades", "faixa_etaria");

    private final TemplateDePrompt personagem;
    private final TemplateDePrompt sugestao;

    /**
     * Confere os templates na criação, para um marcador desconhecido ou uma seção ausente impedirem a
     * aplicação de subir em vez de falhar em sala.
     *
     * @throws IllegalStateException se um template não servir
     */
    public MontadorComTemplates(TemplateDePrompt personagem, TemplateDePrompt sugestao) {
        if (personagem == null || sugestao == null) {
            throw new IllegalArgumentException("Os templates do personagem e da sugestão são obrigatórios");
        }
        this.personagem = personagem;
        this.sugestao = sugestao;
        Map<String, String> qualquerValor = new LinkedHashMap<>();
        MARCADORES_DO_PERSONAGEM.forEach(nome -> qualquerValor.put(nome, ""));
        preencher(personagem, SECAO_INSTRUCOES, qualquerValor);
        preencher(personagem, SECAO_ABERTURA, qualquerValor);
        preencher(sugestao, SECAO_INSTRUCOES, Map.of());
    }

    /**
     * @throws IllegalArgumentException se faltar ao cenário um campo que o template usa
     */
    @Override
    public PromptMontado daFala(PedidoDeFala pedido) {
        Map<String, String> valores = valoresDo(pedido.cenario());
        String instrucoes = preencher(personagem, SECAO_INSTRUCOES, valores);
        if (pedido.historico().isEmpty()) {
            instrucoes = instrucoes + "\n\n" + preencher(personagem, SECAO_ABERTURA, valores);
        }

        List<MensagemDoPrompt> mensagens = new ArrayList<>();
        for (MensagemDaConversa mensagem : pedido.historico()) {
            mensagens.add(switch (mensagem.autor()) {
                case TURMA -> MensagemDoPrompt.doUsuario(mensagem.texto());
                case PERSONAGEM -> MensagemDoPrompt.doAssistente(mensagem.texto());
            });
        }
        mensagens.add(MensagemDoPrompt.doUsuario(pedido.pergunta()));
        return new PromptMontado(personagem.versao(), instrucoes, mensagens);
    }

    @Override
    public PromptMontado daSugestao(PedidoDeSugestao pedido) {
        StringBuilder texto = new StringBuilder("Tema: ").append(pedido.tema());
        linha(texto, "Componente curricular", pedido.componente());
        linha(texto, "Etapa", pedido.etapa());
        linha(texto, "Ano", pedido.ano() == null ? null : pedido.ano() + "º ano");
        linha(texto, "Habilidades BNCC", pedido.habilidades().isEmpty() ? null : String.join(", ", pedido.habilidades()));
        return new PromptMontado(sugestao.versao(), preencher(sugestao, SECAO_INSTRUCOES, Map.of()),
                List.of(MensagemDoPrompt.doUsuario(texto.toString())));
    }

    private static String preencher(TemplateDePrompt template, String secao, Map<String, String> valores) {
        return PreenchimentoDeTemplate.preencher(
                "template " + template.versao() + ", seção " + secao, template.secao(secao), valores);
    }

    /** RN-02 e RN-03: num cenário aprovado, todos estes campos existem. */
    private Map<String, String> valoresDo(CenarioDaConversa cenario) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("titulo", cenario.titulo());
        valores.put("personagem", cenario.personagem());
        valores.put("papel_da_ia", exigir(cenario.papelDaIa(), "papel da IA"));
        valores.put("contexto", exigir(cenario.contexto(), "contexto"));
        valores.put("objetivos", itens(cenario.objetivos(), "objetivos"));
        valores.put("limites_de_assunto", itens(cenario.limitesDeAssunto(), "limites de assunto"));
        valores.put("fontes", itens(cenario.fontes(), "fontes"));
        valores.put("componente", exigir(cenario.componente(), "componente"));
        valores.put("ano", exigir(cenario.ano() == null ? null : cenario.ano().toString(), "ano"));
        valores.put("habilidades", String.join(", ", exigirItens(cenario.habilidades(), "habilidades")));
        valores.put("faixa_etaria", exigir(cenario.faixaEtaria(), "faixa etária"));
        return valores;
    }

    private String exigir(String valor, String campo) {
        if (valor == null) {
            throw campoAusente(campo);
        }
        return valor;
    }

    private List<String> exigirItens(List<String> itens, String campo) {
        if (itens.isEmpty()) {
            throw campoAusente(campo);
        }
        return itens;
    }

    private IllegalArgumentException campoAusente(String campo) {
        return new IllegalArgumentException("O cenário da conversa está sem o campo " + campo
                + ", exigido pelo template " + personagem.versao());
    }

    private String itens(List<String> itens, String campo) {
        return String.join("\n", exigirItens(itens, campo).stream().map(item -> "- " + item).toList());
    }

    private static void linha(StringBuilder texto, String rotulo, String valor) {
        if (valor != null) {
            texto.append('\n').append(rotulo).append(": ").append(valor);
        }
    }
}
