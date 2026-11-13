package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O que a tela de projeção e o painel do professor mostram (RF-15, RF-17): o cenário, a conversa desde
 * o último reinício, o estado da sessão, o tempo e as perguntas da turma.
 *
 * @param mensagens o que a tela mostra, da mais antiga para a mais recente
 * @param pendente a tela abre o canal da resposta ao carregar, porque a última pergunta ainda espera a
 *     resposta do personagem (RNF-04)
 * @param decorridoSegundos tempo de conversa, sem as pausas, no momento da leitura
 */
record TelaDaSessao(
        long id,
        String titulo,
        String cabecalho,
        String personagem,
        String turma,
        StatusDaSessao status,
        MotivoDaPausa motivoDaPausa,
        int perguntas,
        int limiteDePerguntas,
        long decorridoSegundos,
        Integer previstoMinutos,
        List<MensagemExibida> mensagens,
        boolean pendente) {

    static TelaDaSessao de(Sessao sessao, EstadoDaSessao estado, VersaoAprovada versao, String turma, Instant agora) {
        return new TelaDaSessao(
                sessao.id(),
                versao.titulo(),
                cabecalho(versao),
                versao.personagem(),
                turma,
                estado.status(),
                estado.motivoDaPausa(),
                estado.perguntasDaTurma(),
                sessao.limiteDePerguntas(),
                decorrido(estado, agora).toSeconds(),
                versao.duracaoPrevistaMin(),
                estado.ultimasMensagens(),
                estado.status() != StatusDaSessao.ENCERRADA && estado.perguntaPendente().isPresent());
    }

    public boolean pausada() {
        return status == StatusDaSessao.PAUSADA;
    }

    public boolean noLimite() {
        return perguntas >= limiteDePerguntas;
    }

    /** A pausa automática tem o seu aviso; a manual não precisa explicar nada. */
    public String avisoDaPausa() {
        if (motivoDaPausa == null) {
            return null;
        }
        return switch (motivoDaPausa) {
            case MANUAL -> null;
            case CONTEUDO_BLOQUEADO -> "A sessão foi pausada porque um conteúdo foi bloqueado pelo filtro. "
                    + "O caso foi encaminhado à curadoria. Retome para continuar.";
            case LIMITE_50_MINUTOS -> "A sessão chegou a 50 minutos e foi pausada. Retome se quiser continuar.";
            case CENARIO_SUSPENSO -> "O cenário foi suspenso pela curadoria e a sessão foi pausada. "
                    + "Encerre a sessão e escolha outro cenário.";
        };
    }

    /** Em minutos e segundos, como 18:42; passa de uma hora como 62:05, que a sessão nunca deve chegar. */
    public String decorridoFormatado() {
        return String.format(Locale.ROOT, "%02d:%02d", decorridoSegundos / 60, decorridoSegundos % 60);
    }

    /** Quanto do tempo previsto já passou, de 0 a 100, para a barra do painel. */
    public int progresso() {
        if (previstoMinutos == null || previstoMinutos == 0) {
            return 0;
        }
        return (int) Math.min(100, decorridoSegundos * 100 / (previstoMinutos * 60L));
    }

    public boolean daTurma(MensagemExibida mensagem) {
        return mensagem.autoria() == MensagemExibida.Autoria.TURMA;
    }

    private static Duration decorrido(EstadoDaSessao estado, Instant agora) {
        Instant fim = estado.pausadaDesde() != null ? estado.pausadaDesde() : agora;
        Duration total = Duration.between(estado.iniciadaEm(), fim).minus(estado.tempoEmPausa());
        return total.isNegative() ? Duration.ZERO : total;
    }

    /** Como no protótipo: "HISTÓRIA · 8º ANO · EF08HI03". */
    private static String cabecalho(VersaoAprovada versao) {
        var partes = new ArrayList<String>();
        if (versao.componente() != null) {
            partes.add(versao.componente());
        }
        if (versao.ano() != null) {
            partes.add(versao.ano() + "º ano");
        }
        partes.addAll(versao.habilidades());
        return String.join(" · ", partes).toUpperCase(Locale.of("pt", "BR"));
    }
}
