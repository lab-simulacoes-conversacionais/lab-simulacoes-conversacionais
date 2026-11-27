package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaReativacao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.MotivoDaSuspensao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.RegrasDeGravidade;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacaoDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.SinalizacoesDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.infraestrutura.EventosDeCenarios;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suspensão e reativação automáticas do cenário pelas regras de gravidade (RF-25, RN-23), a cada sinalização
 * ou triagem que chega da Auditoria por evento.
 *
 * <p>Em cada uma, nesta ordem: acha o cenário pela versão, trava o cenário até o fim da transação, guarda a
 * sinalização, refaz as regras com as sinalizações da versão vigente e só então muda o status. A trava vem
 * antes da gravação para que duas sinalizações do mesmo cenário ao mesmo tempo sejam decididas uma depois da
 * outra, e a segunda já veja a primeira. A aprovação de versão nova trava o mesmo cenário.
 *
 * <p>O evento {@code CenarioSuspenso} ou {@code CenarioReativado} só sai quando o status muda; por isso, a
 * mesma sinalização entregue de novo não gera outro evento nem outra linha no log (RN-35).
 *
 * <p>Não tem usuário logado nem confere perfil: quem chama é o ouvinte dos eventos, que roda depois da
 * transação da Auditoria. Eventos, log e métricas levam só ids e códigos (RNF-01).
 */
@Component
class SuspensaoAutomatica {

    private static final Logger LOG = LoggerFactory.getLogger(SuspensaoAutomatica.class);

    private final Cenarios cenarios;
    private final SinalizacoesDoCenario sinalizacoes;
    private final EventosDeCenarios eventos;
    private final ContagemDaSuspensao contagem;

    SuspensaoAutomatica(
            Cenarios cenarios,
            SinalizacoesDoCenario sinalizacoes,
            EventosDeCenarios eventos,
            ContagemDaSuspensao contagem) {
        this.cenarios = cenarios;
        this.sinalizacoes = sinalizacoes;
        this.eventos = eventos;
        this.contagem = contagem;
    }

    /**
     * Uma sinalização nova. Só ela pode suspender pelo RN-23a, e quem fica como responsável é quem sinalizou.
     * Nunca reativa: uma sinalização a mais não tira motivo de suspensão.
     *
     * @param pendente com o momento da sinalização em {@code recebidaEm}
     * @param sinalizadoPorId quem sinalizou
     */
    @Transactional
    public void registrar(SinalizacaoDaVersao pendente, long sinalizadoPorId) {
        decidir(pendente, sinalizacoes::registrar, sinalizadoPorId, pendente.recebidaEm(), Optional.empty());
    }

    /**
     * A triagem do curador. Ela suspende pelo RN-23b ou RN-23c, ou reativa quando tira o último motivo: o
     * descarte (RN-23d) ou a confirmação da grave do professor com gravidade menor. Nos dois casos, o
     * responsável é o curador que triou.
     *
     * @param triada confirmada ou descartada; se o registro ainda não tiver chegado, {@code recebidaEm} é o
     *     momento da triagem
     */
    @Transactional
    public void triar(SinalizacaoDaVersao triada) {
        decidir(triada, sinalizacoes::triar, triada.triadaPorId(), triada.triadaEm(),
                Optional.of(MotivoDaReativacao.pelaTriagem(triada)));
    }

    private void decidir(
            SinalizacaoDaVersao sinalizacao,
            Predicate<SinalizacaoDaVersao> gravar,
            long responsavelId,
            Instant momento,
            Optional<MotivoDaReativacao> reativacao) {
        long versaoId = sinalizacao.versaoId();
        Optional<Long> doCenario = sinalizacoes.cenarioDaVersao(versaoId);
        if (doCenario.isEmpty()) {
            // Sem a versão não há onde guardar; o evento termina para não ficar pendente para sempre.
            ignorar(sinalizacao, null, "VERSAO_DESCONHECIDA");
            return;
        }
        long cenarioId = doCenario.get();
        Cenario cenario = cenarios.buscarParaAlterar(cenarioId).orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
        gravar.test(sinalizacao);

        if (!sinalizacoes.versaoVigente(cenarioId).map(vigente -> vigente == versaoId).orElse(false)) {
            ignorar(sinalizacao, cenarioId, "VERSAO_NAO_VIGENTE");
            return;
        }
        StatusDoCenario status = cenario.status();
        if (status != StatusDoCenario.APROVADO && status != StatusDoCenario.SUSPENSO) {
            ignorar(sinalizacao, cenarioId, "CENARIO_" + status.name());
            return;
        }

        Optional<MotivoDaSuspensao> motivo = RegrasDeGravidade.motivo(sinalizacoes.daVersao(versaoId));
        if (status == StatusDoCenario.APROVADO && motivo.isPresent()) {
            Cenario suspenso = cenarios.salvar(cenario.suspender());
            eventos.cenarioSuspenso(suspenso, versaoId, responsavelId, motivo.get(), momento);
            contagem.suspenso(cenarioId, versaoId, sinalizacao.sinalizacaoId(), motivo.get());
        } else if (status == StatusDoCenario.SUSPENSO && motivo.isEmpty() && reativacao.isPresent()) {
            Cenario reativado = cenarios.salvar(cenario.reativar());
            eventos.cenarioReativado(reativado, versaoId, responsavelId, reativacao.get(), momento);
            contagem.reativado(cenarioId, versaoId, sinalizacao.sinalizacaoId(), reativacao.get());
        }
    }

    private static void ignorar(SinalizacaoDaVersao sinalizacao, Long cenarioId, String motivo) {
        LOG.info("Sinalização {} ignorada na suspensão: cenário {}, versão {}, motivo {}",
                sinalizacao.sinalizacaoId(), cenarioId, sinalizacao.versaoId(), motivo);
    }
}
