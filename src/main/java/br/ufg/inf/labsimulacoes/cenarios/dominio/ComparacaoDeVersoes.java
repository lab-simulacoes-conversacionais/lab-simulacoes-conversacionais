package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Duas versões do mesmo cenário lado a lado, campo a campo (RF-11). A comparação parte do conteúdo
 * gravado em cada versão (o roteiro em JSONB e o vínculo com a BNCC), e não de um histórico de
 * alterações.
 *
 * @param antiga a versão de número menor, qualquer que seja a ordem pedida
 * @param nova a versão de número maior
 * @param campos todos os campos, na ordem de {@link CampoDoCenario}, mudados ou não
 */
public record ComparacaoDeVersoes(VersaoDoCenario antiga, VersaoDoCenario nova, List<DiferencaDoCampo> campos) {

    public ComparacaoDeVersoes {
        Objects.requireNonNull(antiga, "antiga");
        Objects.requireNonNull(nova, "nova");
        campos = List.copyOf(campos);
    }

    /** Compara na ordem das versões: a de número menor fica sempre como a antiga. */
    public static ComparacaoDeVersoes entre(VersaoDoCenario uma, VersaoDoCenario outra) {
        VersaoDoCenario antiga = uma.numero() <= outra.numero() ? uma : outra;
        VersaoDoCenario nova = antiga == uma ? outra : uma;
        List<DiferencaDoCampo> campos = Arrays.stream(CampoDoCenario.values())
                .map(campo -> DiferencaDoCampo.entre(
                        campo, campo.valoresDe(antiga.conteudo()), campo.valoresDe(nova.conteudo())))
                .toList();
        return new ComparacaoDeVersoes(antiga, nova, campos);
    }

    /** Só os campos que mudaram, para a tela destacar. */
    public List<DiferencaDoCampo> diferencas() {
        return campos.stream().filter(DiferencaDoCampo::mudou).toList();
    }

    public boolean iguais() {
        return diferencas().isEmpty();
    }
}
