package br.ufg.inf.labsimulacoes.sessoes.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.VersaoAprovada;
import br.ufg.inf.labsimulacoes.sessoes.dominio.EstadoDaSessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MensagemExibida;
import br.ufg.inf.labsimulacoes.sessoes.dominio.MotivoDaPausa;
import br.ufg.inf.labsimulacoes.sessoes.dominio.Sessao;
import br.ufg.inf.labsimulacoes.sessoes.dominio.StatusDaSessao;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O que a tela de projeção e o painel do professor mostram (RF-15, RF-17, RF-18): o cenário, a conversa
 * desde o último reinício, o estado da sessão, o tempo, as perguntas da turma e as perguntas disparadoras.
 *
 * @param mensagens o que a tela mostra, da mais antiga para a mais recente
 * @param pendente a tela abre o canal da resposta ao carregar, porque a última pergunta ainda espera a
 *     resposta do personagem (RNF-04)
 * @param decorridoSegundos tempo de conversa, sem as pausas, no momento da leitura
 * @param perguntasDisparadoras sugestões do cenário para o professor puxar a conversa (RF-18); só o painel
 *     as mostra, nunca a área projetada. Podem faltar, porque o RN-03 não as exige
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
        boolean pendente,
        List<String> perguntasDisparadoras) {

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
                estado.tempoDeConversaAte(agora).toSeconds(),
                versao.duracaoPrevistaMin(),
                estado.ultimasMensagens(),
                estado.status() != StatusDaSessao.ENCERRADA && estado.perguntaPendente().isPresent(),
                versao.perguntasDisparadoras());
    }

    public boolean pausada() {
        return status == StatusDaSessao.PAUSADA;
    }

    public boolean noLimite() {
        return perguntas >= limiteDePerguntas;
    }

    /** O campo da pergunta fica bloqueado, e com ele as perguntas disparadoras que o preencheriam. */
    public boolean podePerguntar() {
        return !pausada() && !noLimite();
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
            case LIMITE_50_MINUTOS -> "A sessão chegou a 50 minutos de conversa, uma hora-aula, e foi pausada. "
                    + "Para continuar, retome e confirme.";
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

    /**
     * RN-17: a conversa chegou à duração prevista do cenário. O painel só avisa, sem pausar nem bloquear
     * a pergunta: quem decide continuar é o professor.
     */
    public boolean previstoAtingido() {
        return previstoMinutos != null && decorridoSegundos >= previstoSegundos();
    }

    /** Para o cronômetro da tela mostrar o aviso do previsto na hora, sem esperar o painel voltar. */
    public long previstoSegundos() {
        return previstoMinutos == null ? 0 : previstoMinutos * 60L;
    }

    /** Para o cronômetro da tela pedir o painel de novo na hora em que a sessão completa 50 minutos. */
    public long limiteSegundos() {
        return Sessao.LIMITE_DE_TEMPO.toSeconds();
    }

    /** RN-17: retomar depois dos 50 minutos pede a confirmação do professor. */
    public boolean pausadaPeloTempo() {
        return motivoDaPausa == MotivoDaPausa.LIMITE_50_MINUTOS;
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
