package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * O que o curador conferiu antes de aprovar (RF-10, RN-06): os itens do checklist que marcou e quantas
 * perguntas de teste fez ao personagem na versão em curadoria.
 *
 * @param marcados os itens do checklist marcados pelo curador
 * @param perguntasDeTeste perguntas da rodada em aberto do curador que testaram o personagem, contadas
 *     por {@link PerguntasDeTeste#contar}
 */
public record ConferenciaDaCuradoria(Set<ItemDoChecklist> marcados, int perguntasDeTeste) {

    public ConferenciaDaCuradoria {
        marcados = Set.copyOf(marcados);
        if (perguntasDeTeste < 0) {
            throw new IllegalArgumentException("O número de perguntas de teste não pode ser negativo");
        }
    }

    public static ConferenciaDaCuradoria de(Collection<ItemDoChecklist> marcados, int perguntasDeTeste) {
        return new ConferenciaDaCuradoria(Set.copyOf(marcados), perguntasDeTeste);
    }

    /** Os itens do checklist que ainda não foram marcados, na ordem do checklist. */
    public List<ItemDoChecklist> itensFaltando() {
        return Arrays.stream(ItemDoChecklist.values()).filter(item -> !marcados.contains(item)).toList();
    }

    /** Quantas perguntas de teste ainda faltam para o mínimo do RN-06. */
    public int perguntasFaltando() {
        return Math.max(0, PerguntasDeTeste.MINIMO_PARA_APROVAR - perguntasDeTeste);
    }

    public boolean completa() {
        return itensFaltando().isEmpty() && perguntasFaltando() == 0;
    }
}
