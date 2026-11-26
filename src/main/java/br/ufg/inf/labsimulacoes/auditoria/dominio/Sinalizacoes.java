package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** As sinalizações de erro gravadas (RF-23) e a triagem delas (RF-24). */
public interface Sinalizacoes {

    /**
     * Grava a sinalização pendente.
     *
     * @return o id da sinalização, ou vazio se a mesma pessoa já tinha sinalizado a mesma resposta; nada é
     *     gravado nesse caso
     */
    Optional<Long> registrar(Sinalizacao sinalizacao);

    /** Os ids das respostas da sessão que a pessoa já sinalizou, para a tela não oferecer de novo. */
    Set<Long> respostasSinalizadasPor(long sessaoId, long sinalizadoPorId);

    /**
     * Uma página das sinalizações que aguardam a triagem, da mais antiga para a mais recente (RF-24).
     *
     * @param depoisDe de onde a página continua; nulo na primeira
     * @param tamanho quantas sinalizações no máximo, de 1 a {@value PaginaDaFila#TAMANHO_MAXIMO}
     */
    PaginaDaFila pendentes(CursorDaFila depoisDe, int tamanho);

    /** As pendentes, só com o id, o tipo e quando foram criadas, para o resumo do prazo (RN-24, RN-37). */
    List<PendenciaDaTriagem> pendenciasSemTexto();

    /**
     * Lê a sinalização para a triagem e a trava até o fim da transação: com dois curadores ao mesmo tempo, o
     * segundo espera e já a encontra triada.
     *
     * @return a sinalização, pendente ou já triada, ou vazio se ela não existe
     */
    Optional<SinalizacaoATriar> paraTriar(long id);

    /**
     * Grava a decisão do curador, se a sinalização ainda estiver pendente. A regra de triar uma vez só é da
     * {@link SinalizacaoATriar}; esta condição é só a guarda contra a concorrência.
     *
     * @return se a decisão foi gravada
     */
    boolean triar(Triagem triagem);
}
