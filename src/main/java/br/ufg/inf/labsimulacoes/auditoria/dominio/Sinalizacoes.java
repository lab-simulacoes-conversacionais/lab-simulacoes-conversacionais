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

    /** As sinalizações que aguardam a triagem, da mais antiga para a mais recente (RF-24). */
    List<SinalizacaoPendente> pendentes();

    /**
     * Grava a decisão do curador, se a sinalização ainda estiver pendente. Com dois curadores ao mesmo tempo,
     * só a primeira triagem vale.
     *
     * @return a origem da sinalização triada, ou vazio se ela não existe ou já foi triada; nada é gravado
     *     nesse caso
     */
    Optional<OrigemDaSinalizacao> triar(Triagem triagem);

    /** Se a sinalização existe, pendente ou já triada. */
    boolean existe(long id);
}
