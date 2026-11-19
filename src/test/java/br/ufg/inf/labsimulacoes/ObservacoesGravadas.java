package br.ufg.inf.labsimulacoes;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registro de observações para os testes de unidade: guarda, na ordem em que terminam, cada observação
 * parada, com as chaves e o erro. Substitui o micrometer-observation-test, que não está no projeto.
 */
public final class ObservacoesGravadas implements ObservationHandler<Observation.Context> {

    /** Uma observação que terminou. */
    public record Observada(String nome, List<KeyValue> chaves, Throwable erro) {

        /** O valor de uma chave de baixa cardinalidade, ou {@code null}. */
        public String valor(String chave) {
            return chaves.stream().filter(c -> c.getKey().equals(chave)).map(KeyValue::getValue).findFirst().orElse(null);
        }
    }

    private final List<Observada> paradas = Collections.synchronizedList(new ArrayList<>());
    private final ObservationRegistry registro = ObservationRegistry.create();

    public ObservacoesGravadas() {
        registro.observationConfig().observationHandler(this);
    }

    public ObservationRegistry registro() {
        return registro;
    }

    /** As observações paradas com este nome, na ordem em que pararam. */
    public List<Observada> paradas(String nome) {
        synchronized (paradas) {
            return paradas.stream().filter(o -> o.nome().equals(nome)).toList();
        }
    }

    @Override
    public void onStop(Observation.Context contexto) {
        List<KeyValue> chaves = new ArrayList<>();
        contexto.getLowCardinalityKeyValues().forEach(chaves::add);
        paradas.add(new Observada(contexto.getName(), List.copyOf(chaves), contexto.getError()));
    }

    @Override
    public boolean supportsContext(Observation.Context contexto) {
        return true;
    }
}
