package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscasDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CatalogoInvalido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FonteDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LeituraDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ResultadoDaGravacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ResultadoDaImportacao;
import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Importação do catálogo da BNCC a partir dos arquivos oficiais (RF-05), uma fonte por etapa.
 *
 * <p>Não é um caso de uso de nenhum perfil: roda na subida, sem usuário logado, e não tem rota. Por
 * isso a classe não é pública e não tem {@code @PreAuthorize}, como a {@code AdministradorInicial}
 * do módulo Acesso.
 */
@Service
class ImportacaoDoCatalogo {

    private static final Logger LOG = LoggerFactory.getLogger(ImportacaoDoCatalogo.class);

    private final List<FonteDeHabilidades> fontes;
    private final Habilidades habilidades;
    private final BuscasDoCatalogo buscas;

    ImportacaoDoCatalogo(List<FonteDeHabilidades> fontes, Habilidades habilidades, BuscasDoCatalogo buscas) {
        this.fontes = fontes;
        this.habilidades = habilidades;
        this.buscas = buscas;
    }

    /**
     * Pode rodar quantas vezes for preciso: as habilidades são gravadas pelo código, sem duplicar, e
     * as que já estão iguais não mudam.
     *
     * @throws CatalogoInvalido se algum arquivo tiver problema; nesse caso nada é gravado
     */
    @Transactional
    public ResultadoDaImportacao importar() {
        List<LeituraDeHabilidades> leituras = fontes.stream().map(FonteDeHabilidades::ler).toList();
        List<HabilidadeBncc> todas = leituras.stream().flatMap(leitura -> leitura.habilidades().stream()).toList();
        recusarCodigosRepetidos(todas);

        ResultadoDaGravacao gravacao = habilidades.gravar(todas);
        limparCacheDepoisDeConfirmar();

        int ignoradas = leituras.stream().mapToInt(LeituraDeHabilidades::ignoradas).sum();
        var resultado = new ResultadoDaImportacao(
                gravacao.inseridas(),
                gravacao.atualizadas(),
                todas.size() - gravacao.inseridas() - gravacao.atualizadas(),
                ignoradas);
        LOG.info("Catálogo da BNCC importado: {} habilidades inseridas, {} atualizadas, {} sem mudança e {} não vigentes ignoradas",
                resultado.inseridas(), resultado.atualizadas(), resultado.inalteradas(), resultado.ignoradas());
        return resultado;
    }

    /**
     * Se o cache fosse limpo antes da confirmação, uma busca feita nesse meio-tempo leria o catálogo
     * antigo e o colocaria de volta no cache. Fora de uma transação, a gravação já está confirmada.
     */
    private void limparCacheDepoisDeConfirmar() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            buscas.esquecerTudo();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                buscas.esquecerTudo();
            }
        });
    }

    /** O upsert não aceita o mesmo código duas vezes num comando, e seria um erro nos arquivos. */
    private static void recusarCodigosRepetidos(List<HabilidadeBncc> todas) {
        var vistos = new HashSet<String>();
        for (HabilidadeBncc habilidade : todas) {
            if (!vistos.add(habilidade.codigo())) {
                throw new CatalogoInvalido("A habilidade " + habilidade.codigo()
                        + " aparece mais de uma vez nos arquivos da BNCC; nada do catálogo foi gravado");
            }
        }
    }
}
