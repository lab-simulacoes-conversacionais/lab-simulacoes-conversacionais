package br.ufg.inf.labsimulacoes.ia.aplicacao;

import br.ufg.inf.labsimulacoes.ia.BloqueioDeDadosPessoais;
import br.ufg.inf.labsimulacoes.ia.DadosPessoais;
import br.ufg.inf.labsimulacoes.ia.TextoComDadoPessoal;
import br.ufg.inf.labsimulacoes.ia.TipoDeDadoPessoal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Bloqueio de dados pessoais por expressões regulares (RN-13, Stack, seção 2). A métrica
 * {@value #METRICA} conta os bloqueios com o tipo na tag {@value #TIPO}; usuário, sessão e texto
 * nunca viram tag.
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class BloqueioPorPadroes implements BloqueioDeDadosPessoais {

    public static final String METRICA = "lab.privacidade.bloqueio";
    public static final String TIPO = "tipo";

    private final MeterRegistry metricas;

    BloqueioPorPadroes(MeterRegistry metricas) {
        this.metricas = metricas;
    }

    @Override
    public void exigirSemDadoPessoal(String texto) {
        exigir(texto, metricas);
    }

    /** A mesma conferência, para a segunda barreira na porta do adaptador. */
    static void exigir(String texto, MeterRegistry metricas) {
        Optional<TipoDeDadoPessoal> encontrado = DadosPessoais.procurar(texto);
        if (encontrado.isPresent()) {
            TipoDeDadoPessoal tipo = encontrado.get();
            Counter.builder(METRICA)
                    .description("Textos digitados bloqueados por ter um dado pessoal (RN-13)")
                    .tag(TIPO, tipo.tag())
                    .register(metricas)
                    .increment();
            throw new TextoComDadoPessoal(tipo);
        }
    }
}
