package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.CatalogoBncc;
import br.ufg.inf.labsimulacoes.cenarios.ConsultaAoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.HabilidadeDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscasDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FiltroDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Busca no catálogo da BNCC (RF-05). Qualquer usuário autenticado pode buscar, porque o catálogo é
 * dado público de referência: o professor e o curador o usam ao vincular habilidades (RN-02).
 *
 * <p>O perfil é conferido antes do cache, que fica no {@link BuscasDoCatalogo}.
 */
@Service
@PreAuthorize("isAuthenticated()")
public class BuscaNoCatalogo implements CatalogoBncc {

    private final BuscasDoCatalogo buscas;
    private final int limitePadrao;

    BuscaNoCatalogo(BuscasDoCatalogo buscas, @Value("${bncc.busca.limite-padrao:50}") int limitePadrao) {
        if (limitePadrao < 1 || limitePadrao > FiltroDeHabilidades.LIMITE_MAXIMO) {
            throw new IllegalArgumentException(
                    "bncc.busca.limite-padrao precisa estar entre 1 e " + FiltroDeHabilidades.LIMITE_MAXIMO);
        }
        this.buscas = buscas;
        this.limitePadrao = limitePadrao;
    }

    @Override
    public List<HabilidadeDoCatalogo> buscar(ConsultaAoCatalogo consulta) {
        var pedida = consulta == null ? ConsultaAoCatalogo.todas() : consulta;
        var filtro = new FiltroDeHabilidades(
                pedida.codigo(),
                pedida.descricao(),
                pedida.etapa(),
                pedida.ano(),
                pedida.componente(),
                pedida.limite() == null ? limitePadrao : pedida.limite());
        return buscas.buscar(filtro).stream().map(BuscaNoCatalogo::paraApi).toList();
    }

    private static HabilidadeDoCatalogo paraApi(HabilidadeBncc habilidade) {
        return new HabilidadeDoCatalogo(
                habilidade.codigo(),
                habilidade.descricao(),
                habilidade.etapa(),
                habilidade.anos(),
                habilidade.componente());
    }
}
