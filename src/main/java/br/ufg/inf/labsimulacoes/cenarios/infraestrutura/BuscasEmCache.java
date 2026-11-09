package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.BuscasDoCatalogo;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FiltroDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Habilidades;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cache das buscas no catálogo da BNCC, no Redis (Stack, seção 5).
 *
 * <p>Fica num bean separado do repositório e do caso de uso porque o {@code @Cacheable} só funciona
 * quando o método é chamado por outro bean, através do proxy. A chave é o filtro normalizado, então
 * "ef08hi03 " e "EF08HI03" são a mesma busca. Lista vazia também fica em cache: até a próxima
 * importação, a resposta não muda.
 */
@Component
class BuscasEmCache implements BuscasDoCatalogo {

    private final Habilidades habilidades;

    BuscasEmCache(Habilidades habilidades) {
        this.habilidades = habilidades;
    }

    @Override
    @Cacheable(cacheNames = ConfiguracaoDoCache.HABILIDADES_BNCC, key = "#filtro.chave()")
    public List<HabilidadeBncc> buscar(FiltroDeHabilidades filtro) {
        return habilidades.buscar(filtro);
    }

    @Override
    @CacheEvict(cacheNames = ConfiguracaoDoCache.HABILIDADES_BNCC, allEntries = true)
    public void esquecerTudo() {
        // O @CacheEvict faz o trabalho.
    }
}
