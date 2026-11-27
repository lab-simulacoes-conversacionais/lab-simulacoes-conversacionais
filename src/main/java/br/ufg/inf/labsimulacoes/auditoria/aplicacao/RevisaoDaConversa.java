package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.ConversaNaoEncontrada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PrazoDaSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacoes;
import br.ufg.inf.labsimulacoes.sessoes.MensagemDaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.SessaoParaRevisao;
import br.ufg.inf.labsimulacoes.sessoes.TrechosDasSessoes;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A conversa de uma sessão já gravada, só para leitura, com o que a pessoa ainda pode sinalizar (RF-23).
 * Serve para o professor sinalizar depois da aula, nos 7 dias do RN-21. O professor vê só as próprias
 * sessões; a de outro professor é tratada como inexistente (RN-37).
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
@Transactional(readOnly = true)
public class RevisaoDaConversa {

    private final TrechosDasSessoes trechos;
    private final Sinalizacoes sinalizacoes;
    private final UsuarioLogado usuarioLogado;
    private final RelogioDaAuditoria relogio;

    RevisaoDaConversa(
            TrechosDasSessoes trechos, Sinalizacoes sinalizacoes, UsuarioLogado usuarioLogado,
            RelogioDaAuditoria relogio) {
        this.trechos = trechos;
        this.sinalizacoes = sinalizacoes;
        this.usuarioLogado = usuarioLogado;
        this.relogio = relogio;
    }

    /** As sessões do professor logado que ainda estão no prazo para sinalizar, da mais recente. */
    public List<SessaoRecente> sessoesRecentes() {
        Instant agora = relogio.agora();
        return trechos.doProfessorDesde(usuarioLogado.id(), agora.minus(PrazoDaSinalizacao.JANELA)).stream()
                .map(sessao -> new SessaoRecente(sessao, limite(sessao)))
                .toList();
    }

    /**
     * @throws ConversaNaoEncontrada se a sessão não existir ou for de outro professor, para quem não é
     *     curador
     */
    public Revisao revisao(long sessaoId) {
        SessaoParaRevisao sessao = trechos.sessao(sessaoId).orElseThrow(() -> new ConversaNaoEncontrada(sessaoId));
        long quem = usuarioLogado.id();
        boolean dono = sessao.professorId() == quem;
        if (!QuemSinaliza.podeVer(dono)) {
            throw new ConversaNaoEncontrada(sessaoId);
        }
        Instant base = PrazoDaSinalizacao.base(sessao.iniciadaEm(), sessao.encerradaEm());
        boolean podeSinalizar = QuemSinaliza.perfil(dono, base, relogio.agora()).isPresent();
        Set<Long> jaSinalizadas = sinalizacoes.respostasSinalizadasPor(sessaoId, quem);
        List<Item> itens = trechos.mensagens(sessaoId).stream()
                .map(mensagem -> new Item(mensagem, situacao(mensagem, podeSinalizar, jaSinalizadas)))
                .toList();
        return new Revisao(sessao, itens, PrazoDaSinalizacao.limite(base), podeSinalizar);
    }

    /**
     * A resposta mais recente do personagem, para o professor sinalizar durante a aula, no modo projeção
     * (RF-23). Vem do registro da sessão, então continua a mesma depois de um reinício da tela (RN-19).
     *
     * @return vazio se o personagem ainda não respondeu
     * @throws ConversaNaoEncontrada nos mesmos casos de {@link #revisao}
     */
    public Optional<Item> ultimaResposta(long sessaoId) {
        return revisao(sessaoId).itens().reversed().stream()
                .filter(item -> item.mensagem().daIa())
                .findFirst();
    }

    /**
     * Uma resposta da sessão, para a tela mostrar de novo o que está sendo sinalizado depois de uma recusa.
     *
     * @return vazio se a mensagem não for uma resposta desta sessão
     * @throws ConversaNaoEncontrada nos mesmos casos de {@link #revisao}
     */
    public Optional<Item> resposta(long sessaoId, long mensagemId) {
        return revisao(sessaoId).itens().stream()
                .filter(item -> item.mensagem().daIa() && item.mensagem().id() == mensagemId)
                .findFirst();
    }

    private static Situacao situacao(MensagemDaRevisao mensagem, boolean podeSinalizar, Set<Long> jaSinalizadas) {
        if (mensagem.daTurma()) {
            return Situacao.PERGUNTA;
        }
        if (jaSinalizadas.contains(mensagem.id())) {
            return Situacao.SINALIZADA;
        }
        return podeSinalizar ? Situacao.PODE_SINALIZAR : Situacao.FORA_DO_PRAZO;
    }

    private static Instant limite(SessaoParaRevisao sessao) {
        return PrazoDaSinalizacao.limite(PrazoDaSinalizacao.base(sessao.iniciadaEm(), sessao.encerradaEm()));
    }

    /** Uma sessão no prazo, com o último momento para sinalizar. */
    public record SessaoRecente(SessaoParaRevisao sessao, Instant limite) {}

    /**
     * A conversa inteira, em ordem.
     *
     * @param limite o último momento em que o professor dono da sessão pode sinalizar
     * @param podeSinalizar se a pessoa logada ainda pode sinalizar nesta sessão
     */
    public record Revisao(SessaoParaRevisao sessao, List<Item> itens, Instant limite, boolean podeSinalizar) {

        public Revisao {
            itens = List.copyOf(itens);
        }
    }

    /** Uma mensagem da conversa e o que a pessoa pode fazer com ela. */
    public record Item(MensagemDaRevisao mensagem, Situacao situacao) {}

    /** O que a tela oferece para cada mensagem. */
    public enum Situacao {
        /** Pergunta da turma: não se sinaliza. */
        PERGUNTA,
        /** Resposta da IA que a pessoa ainda pode sinalizar. */
        PODE_SINALIZAR,
        /** Resposta da IA que a pessoa já sinalizou. */
        SINALIZADA,
        /** Resposta da IA, mas o prazo da pessoa terminou. */
        FORA_DO_PRAZO
    }
}
