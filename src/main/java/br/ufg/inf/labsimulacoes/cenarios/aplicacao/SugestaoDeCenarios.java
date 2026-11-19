package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.PedidoDeSugestaoDeCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RoteiroDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VinculoCurricular;
import br.ufg.inf.labsimulacoes.ia.AdaptadorDeIa;
import br.ufg.inf.labsimulacoes.ia.PedidoDeSugestao;
import br.ufg.inf.labsimulacoes.ia.RoteiroSugerido;
import br.ufg.inf.labsimulacoes.ia.SugestaoDeCenario;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Sugestão de cenário pela IA (RF-07). O resultado entra como um cenário novo, em Rascunho e marcado
 * "Sugerido por IA", com quem pediu como autor; daí ele ajusta e envia à curadoria como qualquer
 * rascunho (RN-04, RN-05). Este caso de uso só cria cenários: nunca altera, aprova ou publica um que já
 * existe. A sugestão de quiz fica para a #46, que depende do quiz do cenário (#45).
 *
 * <p>Não abre transação: a geração leva segundos, e a gravação do rascunho tem a própria. Se o provedor
 * falhar, nada é gravado e a {@link br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa} chega a quem chamou.
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class SugestaoDeCenarios {

    private final AdaptadorDeIa adaptador;
    private final Cenarios cenarios;
    private final Habilidades habilidades;
    private final UsuarioLogado usuarioLogado;

    SugestaoDeCenarios(
            AdaptadorDeIa adaptador, Cenarios cenarios, Habilidades habilidades, UsuarioLogado usuarioLogado) {
        this.adaptador = adaptador;
        this.cenarios = cenarios;
        this.habilidades = habilidades;
        this.usuarioLogado = usuarioLogado;
    }

    /**
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadesForaDoCatalogo antes de chamar o
     *     provedor, para um código errado não gastar tokens
     * @throws br.ufg.inf.labsimulacoes.ia.FalhaNoProvedorDeIa se o provedor falhar ou devolver algo que
     *     não vira um roteiro
     */
    public Cenario sugerir(PedidoDeSugestaoDeCenario pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("O pedido de sugestão é obrigatório");
        }
        habilidades.exigirNoCatalogo(pedido.curriculo().habilidades());
        long quemPediu = usuarioLogado.id();

        SugestaoDeCenario sugestao = adaptador.sugerirCenario(paraOAdaptador(pedido));

        RoteiroSugerido roteiro = sugestao.roteiro();
        var conteudo = pedido.conteudoSugerido(
                roteiro.titulo(), paraORoteiro(roteiro), roteiro.faixaEtaria(), roteiro.duracaoPrevistaMin());
        return cenarios.salvar(Cenario.sugeridoPelaIa(quemPediu, conteudo, sugestao.metadadosId()));
    }

    private static PedidoDeSugestao paraOAdaptador(PedidoDeSugestaoDeCenario pedido) {
        VinculoCurricular curriculo = pedido.curriculo();
        return new PedidoDeSugestao(
                pedido.tema(), curriculo.componente(), curriculo.etapa(), curriculo.ano(), curriculo.habilidades());
    }

    private static RoteiroDoCenario paraORoteiro(RoteiroSugerido roteiro) {
        return new RoteiroDoCenario(
                roteiro.personagem(),
                roteiro.papelDaIa(),
                roteiro.contexto(),
                roteiro.objetivos(),
                roteiro.limitesDeAssunto(),
                roteiro.fontes(),
                roteiro.perguntasDisparadoras());
    }
}
