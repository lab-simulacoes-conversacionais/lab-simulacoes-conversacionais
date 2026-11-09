package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * O que precisa estar preenchido para o cenário ir à curadoria (RN-02 e RN-03). O título não entra
 * aqui porque é exigido desde o rascunho, e a duração informada já nasce entre 10 e 30 minutos.
 */
public enum PendenciaDoCenario {
    COMPONENTE("componente curricular", c -> c.curriculo().componente() == null),
    ETAPA("etapa", c -> c.curriculo().etapa() == null),
    ANO("ano", c -> c.curriculo().ano() == null),
    HABILIDADE_BNCC("ao menos uma habilidade da BNCC", c -> c.curriculo().habilidades().isEmpty()),
    PERSONAGEM("personagem", c -> c.roteiro().personagem() == null),
    PAPEL_DA_IA("papel da IA", c -> c.roteiro().papelDaIa() == null),
    CONTEXTO("contexto", c -> c.roteiro().contexto() == null),
    OBJETIVO("ao menos um objetivo de aprendizagem", c -> c.roteiro().objetivos().isEmpty()),
    LIMITES_DE_ASSUNTO("limites de assunto", c -> c.roteiro().limitesDeAssunto().isEmpty()),
    FONTE("ao menos uma fonte de referência", c -> c.roteiro().fontes().isEmpty()),
    FAIXA_ETARIA("faixa etária", c -> c.faixaEtaria() == null),
    DURACAO_PREVISTA("duração prevista", c -> c.duracaoPrevistaMin() == null);

    private final String descricao;
    private final Predicate<ConteudoDoCenario> falta;

    PendenciaDoCenario(String descricao, Predicate<ConteudoDoCenario> falta) {
        this.descricao = descricao;
        this.falta = falta;
    }

    public String descricao() {
        return descricao;
    }

    static List<PendenciaDoCenario> de(ConteudoDoCenario conteudo) {
        return Arrays.stream(values()).filter(pendencia -> pendencia.falta.test(conteudo)).toList();
    }
}
