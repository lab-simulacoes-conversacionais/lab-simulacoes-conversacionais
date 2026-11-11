package br.ufg.inf.labsimulacoes.ia.dominio;

import java.util.Map;

/**
 * O que o provedor gerou e como: o conteúdo e o que vai para os metadados da resposta.
 *
 * @param modelo o modelo que de fato respondeu, que pode trazer a data da versão
 * @param parametros opções enviadas ao modelo, como o esforço de raciocínio
 */
public record Geracao<T>(T conteudo, String modelo, Map<String, Object> parametros, UsoDoModelo uso) {

    public Geracao {
        parametros = parametros == null ? Map.of() : Map.copyOf(parametros);
        uso = uso == null ? UsoDoModelo.desconhecido() : uso;
    }
}
