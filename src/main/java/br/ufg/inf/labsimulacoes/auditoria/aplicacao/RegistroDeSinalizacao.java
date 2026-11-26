package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import br.ufg.inf.labsimulacoes.acesso.UsuarioLogado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.ForaDoPrazoDeSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.GravidadePercebida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PerfilDaSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.PrazoDaSinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RespostaNaoEncontrada;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacao;
import br.ufg.inf.labsimulacoes.auditoria.dominio.SinalizacaoRepetida;
import br.ufg.inf.labsimulacoes.auditoria.dominio.Sinalizacoes;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TipoDeErro;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TrechoSinalizado;
import br.ufg.inf.labsimulacoes.auditoria.dominio.TrechoSinalizado.MetadadosCopiados;
import br.ufg.inf.labsimulacoes.eventos.SinalizacaoRegistrada;
import br.ufg.inf.labsimulacoes.ia.BloqueioDeDadosPessoais;
import br.ufg.inf.labsimulacoes.ia.OrigemDasRespostas;
import br.ufg.inf.labsimulacoes.sessoes.TrechoParaSinalizar;
import br.ufg.inf.labsimulacoes.sessoes.TrechosDasSessoes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Professor e curador sinalizam uma resposta da IA como possível erro (RF-23, RN-21).
 *
 * <ul>
 *   <li>O professor sinaliza só respostas das próprias sessões, durante a sessão ou até 7 dias depois.
 *       A sessão de outro professor é tratada como inexistente (RN-37).
 *   <li>O curador sinaliza qualquer resposta, a qualquer momento.
 *   <li>Quem tem os dois perfis sinaliza como professor na própria sessão, dentro do prazo, e como curador
 *       nos outros casos.
 * </ul>
 *
 * <p>A sinalização copia a pergunta, a resposta, os dados da sessão e os metadados da resposta, para
 * continuar completa depois da retenção (RN-33, Stack, seção 6). A justificativa, digitada à mão, passa
 * pelo bloqueio de dados pessoais (RNF-01); a pergunta e a resposta copiadas não, porque a sinalização
 * não pode ser recusada pelo conteúdo que ela denuncia.
 *
 * <p>Publica {@link SinalizacaoRegistrada}, sem os textos. Até a RF-25, nada reage a ela: uma sinalização
 * grave ainda não suspende o cenário (RN-23a).
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class RegistroDeSinalizacao {

    public static final String METRICA = "lab.auditoria.sinalizacao";

    private static final Logger LOG = LoggerFactory.getLogger(RegistroDeSinalizacao.class);
    private static final String PERFIL_PROFESSOR = "ROLE_PROFESSOR";
    private static final String PERFIL_CURADOR = "ROLE_CURADOR";

    private final TrechosDasSessoes trechos;
    private final OrigemDasRespostas origens;
    private final BloqueioDeDadosPessoais dadosPessoais;
    private final Sinalizacoes sinalizacoes;
    private final UsuarioLogado usuarioLogado;
    private final RelogioDaAuditoria relogio;
    private final ApplicationEventPublisher eventos;
    private final MeterRegistry metricas;

    RegistroDeSinalizacao(
            TrechosDasSessoes trechos,
            OrigemDasRespostas origens,
            BloqueioDeDadosPessoais dadosPessoais,
            Sinalizacoes sinalizacoes,
            UsuarioLogado usuarioLogado,
            RelogioDaAuditoria relogio,
            ApplicationEventPublisher eventos,
            MeterRegistry metricas) {
        this.trechos = trechos;
        this.origens = origens;
        this.dadosPessoais = dadosPessoais;
        this.sinalizacoes = sinalizacoes;
        this.usuarioLogado = usuarioLogado;
        this.relogio = relogio;
        this.eventos = eventos;
        this.metricas = metricas;
    }

    /**
     * @return o id da sinalização gravada
     * @throws RespostaNaoEncontrada se a mensagem não existir, não for uma resposta da IA ou for de uma
     *     sessão de outro professor, para quem não é curador
     * @throws ForaDoPrazoDeSinalizacao se o professor passou dos 7 dias e não é curador
     * @throws IllegalArgumentException se faltar o tipo, a gravidade ou a justificativa, ou se a
     *     justificativa passar de {@value Sinalizacao#TAMANHO_MAXIMO_DA_JUSTIFICATIVA} caracteres
     * @throws br.ufg.inf.labsimulacoes.ia.TextoComDadoPessoal se a justificativa tiver e-mail, telefone, CPF
     *     ou endereço; nada é gravado
     * @throws SinalizacaoRepetida se a pessoa já sinalizou esta resposta
     */
    @Transactional
    public long sinalizar(long mensagemId, TipoDeErro tipo, GravidadePercebida gravidade, String justificativa) {
        TrechoParaSinalizar trecho = trechos.resposta(mensagemId).orElseThrow(() -> new RespostaNaoEncontrada(mensagemId));
        long quem = usuarioLogado.id();
        Instant agora = relogio.agora();
        PerfilDaSinalizacao perfil = perfil(trecho, quem, agora);

        var sinalizacao = new Sinalizacao(copia(trecho), tipo, gravidade, justificativa, quem, perfil, agora);
        dadosPessoais.exigirSemDadoPessoal(sinalizacao.justificativa());
        long id = sinalizacoes.registrar(sinalizacao).orElseThrow(() -> new SinalizacaoRepetida(mensagemId));

        eventos.publishEvent(new SinalizacaoRegistrada(
                id, trecho.sessaoId(), trecho.versaoId(), tipo.name(), gravidade.name(), perfil.name(), quem, agora));
        Counter.builder(METRICA)
                .description("Respostas da IA sinalizadas como possível erro (RF-23)")
                .tag("tipo", tipo.name().toLowerCase(Locale.ROOT))
                .tag("perfil", perfil.name().toLowerCase(Locale.ROOT))
                .register(metricas)
                .increment();
        LOG.info("Sinalização {} registrada na sessão {} ({}, {})", id, trecho.sessaoId(), tipo, perfil);
        return id;
    }

    /**
     * Como professor na própria sessão, dentro do prazo; senão, como curador, se tiver o perfil. Para quem
     * só é professor, a sessão de outro professor não existe, e a própria fora do prazo é recusada.
     */
    private static PerfilDaSinalizacao perfil(TrechoParaSinalizar trecho, long quem, Instant agora) {
        boolean dono = trecho.professorId() == quem;
        Instant base = PrazoDaSinalizacao.base(trecho.sessaoIniciadaEm(), trecho.sessaoEncerradaEm());
        boolean noPrazo = PrazoDaSinalizacao.permite(base, agora);
        if (dono && noPrazo && tem(PERFIL_PROFESSOR)) {
            return PerfilDaSinalizacao.PROFESSOR;
        }
        if (tem(PERFIL_CURADOR)) {
            return PerfilDaSinalizacao.CURADOR;
        }
        if (!dono) {
            throw new RespostaNaoEncontrada(trecho.mensagemId());
        }
        throw new ForaDoPrazoDeSinalizacao(trecho.sessaoId(), PrazoDaSinalizacao.limite(base));
    }

    /** O que a sinalização guarda da conversa, com a origem da resposta copiada dos metadados. */
    private TrechoSinalizado copia(TrechoParaSinalizar trecho) {
        Optional<MetadadosCopiados> metadados = Optional.ofNullable(trecho.respostaIaMetaId())
                .flatMap(origens::daResposta)
                .map(origem -> new MetadadosCopiados(origem.provedor(), origem.modelo(), origem.versaoPrompt()));
        return new TrechoSinalizado(
                trecho.sessaoId(),
                trecho.sessaoIniciadaEm(),
                trecho.professorId(),
                trecho.versaoId(),
                trecho.mensagemId(),
                metadados.isPresent() ? trecho.respostaIaMetaId() : null,
                metadados.orElse(null),
                trecho.pergunta(),
                trecho.resposta(),
                trecho.respondidaEm());
    }

    private static boolean tem(String perfil) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null
                && autenticacao.getAuthorities().stream().anyMatch(dado -> perfil.equals(dado.getAuthority()));
    }
}
