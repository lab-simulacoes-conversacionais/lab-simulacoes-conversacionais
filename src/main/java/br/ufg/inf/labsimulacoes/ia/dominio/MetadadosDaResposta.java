package br.ufg.inf.labsimulacoes.ia.dominio;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rastro de uma resposta da IA (RNF-10, RNF-13): quem respondeu, com quais parâmetros e qual prompt,
 * quanto consumiu e quanto demorou. Fica em resposta_ia_meta, que não depende das conversas e por isso
 * sobrevive à exclusão delas (RN-33).
 *
 * @param id nulo enquanto não foi gravado
 * @param parametros opções enviadas ao modelo, como o esforço de raciocínio
 * @param versaoPrompt versão do template de prompt usado
 * @param custo em dólares; nulo quando o provedor não informou os tokens
 * @param latenciaMs do pedido ao fim da resposta
 */
public record MetadadosDaResposta(
        Long id,
        String provedor,
        String modelo,
        Map<String, Object> parametros,
        String versaoPrompt,
        UsoDoModelo uso,
        BigDecimal custo,
        int latenciaMs) {

    public MetadadosDaResposta {
        provedor = preenchido(provedor, "provedor");
        modelo = preenchido(modelo, "modelo");
        versaoPrompt = preenchido(versaoPrompt, "versão do prompt");
        parametros = parametros == null ? Map.of() : semNulos(parametros);
        uso = uso == null ? UsoDoModelo.desconhecido() : uso;
        if (custo != null && custo.signum() < 0) {
            throw new IllegalArgumentException("O custo não pode ser negativo: " + custo);
        }
        if (latenciaMs < 0) {
            throw new IllegalArgumentException("A latência não pode ser negativa: " + latenciaMs);
        }
    }

    /** Monta os metadados de uma chamada recém-concluída, com o custo calculado pela tabela de preços. */
    public static MetadadosDaResposta daChamada(
            String provedor,
            String modelo,
            Map<String, Object> parametros,
            String versaoPrompt,
            UsoDoModelo uso,
            TabelaDePrecos precos,
            Duration latencia) {
        return new MetadadosDaResposta(null, provedor, modelo, parametros, versaoPrompt, uso,
                precos.custoDe(uso), Math.toIntExact(latencia.toMillis()));
    }

    public MetadadosDaResposta comId(long novoId) {
        return new MetadadosDaResposta(novoId, provedor, modelo, parametros, versaoPrompt, uso, custo, latenciaMs);
    }

    private static String preenchido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório");
        }
        return valor.strip();
    }

    /** Parâmetro sem valor não foi enviado ao modelo, então não entra no rastro. */
    private static Map<String, Object> semNulos(Map<String, Object> parametros) {
        Map<String, Object> copia = new LinkedHashMap<>();
        parametros.forEach((nome, valor) -> {
            if (valor != null) {
                copia.put(nome, valor);
            }
        });
        return Collections.unmodifiableMap(copia);
    }
}
