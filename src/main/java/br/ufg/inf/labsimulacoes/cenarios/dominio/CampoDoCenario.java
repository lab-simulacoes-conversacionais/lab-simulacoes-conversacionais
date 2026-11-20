package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.List;
import java.util.function.Function;

/**
 * Os campos de uma versão do cenário que a comparação mostra (RF-11), na ordem do editor (RF-06). Cada
 * campo vira uma lista de textos: os de um valor só viram uma lista de um item, ou vazia se não foram
 * informados. Assim os textos e as listas são comparados do mesmo jeito.
 */
public enum CampoDoCenario {
    TITULO("Título", conteudo -> umItem(conteudo.titulo())),
    PERSONAGEM("Personagem", conteudo -> umItem(conteudo.roteiro().personagem())),
    PAPEL_DA_IA("Papel da IA", conteudo -> umItem(conteudo.roteiro().papelDaIa())),
    CONTEXTO("Contexto", conteudo -> umItem(conteudo.roteiro().contexto())),
    OBJETIVOS("Objetivos de aprendizagem", conteudo -> conteudo.roteiro().objetivos()),
    LIMITES_DE_ASSUNTO("Limites de assunto", conteudo -> conteudo.roteiro().limitesDeAssunto()),
    FONTES("Fontes", conteudo -> conteudo.roteiro().fontes()),
    PERGUNTAS_DISPARADORAS("Perguntas disparadoras", conteudo -> conteudo.roteiro().perguntasDisparadoras()),
    COMPONENTE("Componente curricular", conteudo -> umItem(conteudo.curriculo().componente())),
    ETAPA("Etapa", conteudo -> umItem(conteudo.curriculo().etapa())),
    ANO("Ano", conteudo -> umItem(conteudo.curriculo().ano())),
    HABILIDADES("Habilidades BNCC", conteudo -> conteudo.curriculo().habilidades()),
    FAIXA_ETARIA("Faixa etária", conteudo -> umItem(conteudo.faixaEtaria())),
    DURACAO_PREVISTA("Duração prevista (min)", conteudo -> umItem(conteudo.duracaoPrevistaMin()));

    private final String rotulo;
    private final Function<ConteudoDoCenario, List<String>> valores;

    CampoDoCenario(String rotulo, Function<ConteudoDoCenario, List<String>> valores) {
        this.rotulo = rotulo;
        this.valores = valores;
    }

    /** Como o campo aparece na tela. */
    public String rotulo() {
        return rotulo;
    }

    List<String> valoresDe(ConteudoDoCenario conteudo) {
        return valores.apply(conteudo);
    }

    private static List<String> umItem(Object valor) {
        return valor == null ? List.of() : List.of(valor.toString());
    }
}
