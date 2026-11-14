package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.HabilidadeDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ConteudoDoCenario;
import java.util.ArrayList;
import java.util.List;

/**
 * O conteúdo de uma versão do cenário como as telas de leitura mostram: a fila do curador, que revisa
 * a versão em curadoria (RF-08), e o detalhe da biblioteca, que mostra a aprovada vigente (RF-12).
 *
 * @param habilidades os códigos com a descrição do catálogo (RN-02)
 */
record ConteudoExibido(
        String titulo,
        String personagem,
        String papelDaIa,
        String contexto,
        List<String> objetivos,
        String componente,
        Integer ano,
        List<HabilidadeDoCatalogo> habilidades,
        List<String> limitesDeAssunto,
        List<String> fontes,
        String faixaEtaria,
        Integer duracaoPrevistaMin,
        List<String> perguntasDisparadoras,
        int numeroDaVersao) {

    static ConteudoExibido de(ConteudoDoCenario conteudo, int numeroDaVersao, DescricoesDaBncc descricoes) {
        var roteiro = conteudo.roteiro();
        var curriculo = conteudo.curriculo();
        return new ConteudoExibido(
                conteudo.titulo(),
                roteiro.personagem(),
                roteiro.papelDaIa(),
                roteiro.contexto(),
                roteiro.objetivos(),
                curriculo.componente(),
                curriculo.ano(),
                descricoes.de(curriculo.habilidades()),
                roteiro.limitesDeAssunto(),
                roteiro.fontes(),
                conteudo.faixaEtaria(),
                conteudo.duracaoPrevistaMin(),
                roteiro.perguntasDisparadoras(),
                numeroDaVersao);
    }

    static ConteudoExibido de(VersaoAprovada versao, DescricoesDaBncc descricoes) {
        return new ConteudoExibido(
                versao.titulo(),
                versao.personagem(),
                versao.papelDaIa(),
                versao.contexto(),
                versao.objetivos(),
                versao.componente(),
                versao.ano(),
                descricoes.de(versao.habilidades()),
                versao.limitesDeAssunto(),
                versao.fontes(),
                versao.faixaEtaria(),
                versao.duracaoPrevistaMin(),
                versao.perguntasDisparadoras(),
                versao.numero());
    }

    /** Linha curta para o cabeçalho: ano e códigos BNCC, como "8º ano · EF08HI03". */
    public String resumoCurricular() {
        var partes = new ArrayList<String>();
        if (ano != null) {
            partes.add(ano + "º ano");
        }
        habilidades.forEach(h -> partes.add(h.codigo()));
        return String.join(" · ", partes);
    }
}
