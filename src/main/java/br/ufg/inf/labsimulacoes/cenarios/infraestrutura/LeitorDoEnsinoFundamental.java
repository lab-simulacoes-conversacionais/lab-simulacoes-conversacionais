package br.ufg.inf.labsimulacoes.cenarios.infraestrutura;

import br.ufg.inf.labsimulacoes.cenarios.dominio.CatalogoInvalido;
import br.ufg.inf.labsimulacoes.cenarios.dominio.FonteDeHabilidades;
import br.ufg.inf.labsimulacoes.cenarios.dominio.HabilidadeBncc;
import br.ufg.inf.labsimulacoes.cenarios.dominio.LeituraDeHabilidades;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Leitor dos arquivos do Ensino Fundamental do dataset bncc-dados (ver bncc/ATTRIBUTION.md).
 *
 * <p>O ensino-fundamental.json traz as habilidades, e o estrutura.json, o nome de cada componente.
 * Só entram as habilidades vigentes e os campos código, texto, componente e anos. Todo o arquivo é
 * conferido antes de devolver qualquer habilidade, então um problema não deixa o catálogo pela metade.
 */
@Component
class LeitorDoEnsinoFundamental implements FonteDeHabilidades {

    private static final Logger LOG = LoggerFactory.getLogger(LeitorDoEnsinoFundamental.class);

    static final String ETAPA = "EF";
    private static final String VIGENTE = "vigente";

    private final ObjectMapper json;
    private final Resource habilidades;
    private final Resource estrutura;

    LeitorDoEnsinoFundamental(
            ObjectMapper json,
            @Value("${bncc.arquivos.ensino-fundamental:classpath:bncc/ensino-fundamental.json}") Resource habilidades,
            @Value("${bncc.arquivos.estrutura:classpath:bncc/estrutura.json}") Resource estrutura) {
        this.json = json;
        this.habilidades = habilidades;
        this.estrutura = estrutura;
    }

    @Override
    public LeituraDeHabilidades ler() {
        Map<String, String> componentes = nomesDosComponentes();
        List<Item> itens = ler(habilidades, ArquivoDeHabilidades.class).habilidades();
        if (itens == null) {
            throw new CatalogoInvalido(nome(habilidades) + " não tem a lista \"habilidades\"");
        }

        var vigentes = new ArrayList<HabilidadeBncc>();
        int ignoradas = 0;
        for (Item item : itens) {
            if (!vigente(item)) {
                ignoradas++;
                continue;
            }
            vigentes.add(habilidade(item, componentes));
        }
        LOG.info("BNCC, Ensino Fundamental: {} habilidades vigentes lidas e {} não vigentes ignoradas",
                vigentes.size(), ignoradas);
        return new LeituraDeHabilidades(ETAPA, vigentes, ignoradas);
    }

    private Map<String, String> nomesDosComponentes() {
        List<Componente> lista = ler(estrutura, Estrutura.class).componentesCurriculares();
        if (lista == null) {
            throw new CatalogoInvalido(nome(estrutura) + " não tem a lista \"componentes_curriculares\"");
        }
        var nomes = new HashMap<String, String>();
        for (Componente componente : lista) {
            if (componente.id() != null && componente.nome() != null && !componente.nome().isBlank()) {
                nomes.put(componente.id(), componente.nome().strip());
            }
        }
        return nomes;
    }

    private boolean vigente(Item item) {
        if (item.vigencia() == null || item.vigencia().status() == null) {
            throw new CatalogoInvalido("A habilidade " + item.codigo() + " de " + nome(habilidades)
                    + " não informa a vigência; nada do catálogo foi gravado");
        }
        return VIGENTE.equals(item.vigencia().status());
    }

    private HabilidadeBncc habilidade(Item item, Map<String, String> componentes) {
        String componente = componentes.get(item.componente());
        if (componente == null) {
            throw new CatalogoInvalido("O componente \"" + item.componente() + "\" da habilidade " + item.codigo()
                    + " não está em " + nome(estrutura) + "; nada do catálogo foi gravado");
        }
        try {
            return new HabilidadeBncc(item.codigo(), item.texto(), ETAPA, item.anos(), componente);
        } catch (IllegalArgumentException e) {
            throw new CatalogoInvalido("Habilidade inválida em " + nome(habilidades) + ": " + e.getMessage()
                    + "; nada do catálogo foi gravado", e);
        }
    }

    private <T> T ler(Resource arquivo, Class<T> tipo) {
        try (Reader leitor = new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8)) {
            return json.readValue(leitor, tipo);
        } catch (IOException e) {
            throw new CatalogoInvalido("Não foi possível ler " + nome(arquivo) + ": " + e.getMessage(), e);
        }
    }

    private static String nome(Resource arquivo) {
        return arquivo.getFilename();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArquivoDeHabilidades(List<Item> habilidades) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String codigo, String texto, String componente, List<Integer> anos, Vigencia vigencia) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Vigencia(String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estrutura(@JsonProperty("componentes_curriculares") List<Componente> componentesCurriculares) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Componente(String id, String nome) {}
}
