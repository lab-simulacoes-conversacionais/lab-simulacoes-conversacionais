package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Cenário de simulação (RF-06, RF-08). Cada transição confere as regras antes de mudar o estado, para
 * que cada regra tenha um teste próprio, sem banco nem tela (Stack, seção 5).
 *
 * <p>Na E1 o ciclo é Rascunho, Em curadoria e Aprovado; a devolução volta a Rascunho (Figura 3 dos
 * requisitos). Na E2 a aprovação passa a exigir o checklist de curadoria e o teste do personagem
 * (RF-10, RN-06). O curador arquiva o aprovado ou o suspenso, que sai da biblioteca sem perder o
 * histórico (RF-13, RN-09).
 *
 * <p>Editar um cenário que já tem versão vigente gera uma versão nova em rascunho (RF-11, RN-08). Ela
 * passa pela mesma curadoria, mas o cenário continua Aprovado (ou Suspenso) enquanto isso, porque a
 * versão vigente segue valendo em sala até a nova ser aprovada. O status da versão é que diz se ela
 * está em rascunho ou em curadoria.
 *
 * @param versao a versão mais recente
 * @param ultimaDevolucao a devolução mais recente desta versão, para o autor saber o que mudar
 *     (RN-07); {@code null} se ela nunca foi devolvida
 * @param sugeridoPorIa RN-04: o curador confere os fatos de um cenário sugerido pela IA com as fontes
 * @param sugestaoDaIa metadados da geração que sugeriu o cenário (RNF-10); {@code null} se ele foi
 *     criado do zero ou se os metadados já foram apagados pela retenção, o que não tira a marca
 */
public record Cenario(
        Long id, long autorId, StatusDoCenario status, VersaoDoCenario versao, Devolucao ultimaDevolucao,
        boolean sugeridoPorIa, Long sugestaoDaIa) {

    public Cenario {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(versao, "versao");
        if (sugestaoDaIa != null && !sugeridoPorIa) {
            throw new IllegalArgumentException("Um cenário com metadados de geração precisa da marca de sugerido por IA");
        }
    }

    /**
     * Cenário criado do zero cuja versão nunca foi devolvida. Não serve para copiar um cenário que já
     * existe com outro estado: a marca de sugerido por IA se perderia (RN-04). Para isso, use o
     * construtor completo ou uma transição.
     */
    public Cenario(Long id, long autorId, StatusDoCenario status, VersaoDoCenario versao) {
        this(id, autorId, status, versao, null, false, null);
    }

    public static Cenario novo(long autorId, ConteudoDoCenario conteudo) {
        return new Cenario(null, autorId, StatusDoCenario.RASCUNHO, VersaoDoCenario.primeira(autorId, conteudo));
    }

    /**
     * RN-04: a sugestão da IA é só um ponto de partida. Entra sempre como um cenário novo, em Rascunho e
     * marcado "Sugerido por IA", com quem pediu como autor; daí segue o mesmo caminho de curadoria de
     * qualquer cenário, e quem aprova é um curador que não seja o autor (RN-05, RN-36). Não existe
     * caminho para a sugestão alterar, aprovar ou publicar um cenário que já existe.
     *
     * @param quemPediu professor ou curador que pediu a sugestão
     * @param geracao metadados da geração (RNF-10)
     */
    public static Cenario sugeridoPelaIa(long quemPediu, ConteudoDoCenario conteudo, long geracao) {
        return new Cenario(null, quemPediu, StatusDoCenario.RASCUNHO, VersaoDoCenario.primeira(quemPediu, conteudo),
                null, true, geracao);
    }

    public boolean temAutor(long usuarioId) {
        return autorId == usuarioId;
    }

    /** Mantém o número da versão: só o que já saiu do rascunho vira histórico. */
    public Cenario alterarRascunho(ConteudoDoCenario conteudo) {
        exigirRascunho();
        return new Cenario(id, autorId, status, versao.comConteudo(conteudo), ultimaDevolucao, sugeridoPorIa, sugestaoDaIa);
    }

    /**
     * RF-08: o autor envia o cenário à curadoria, com o perfil que tiver (o curador também cria
     * cenários, RF-06).
     *
     * @throws EnvioNaoPermitido se quem envia não for o autor
     * @throws CenarioIncompleto com tudo o que falta, para o autor corrigir de uma vez (RN-02, RN-03)
     */
    public Cenario enviarACuradoria(long quemEnvia) {
        if (!temAutor(quemEnvia)) {
            throw new EnvioNaoPermitido(id);
        }
        exigirRascunho();
        List<PendenciaDoCenario> pendencias = versao.conteudo().pendencias();
        if (!pendencias.isEmpty()) {
            throw new CenarioIncompleto(pendencias);
        }
        return new Cenario(id, autorId, statusEnquantoEmCuradoria(StatusDoCenario.EM_CURADORIA), versao.enviada(),
                ultimaDevolucao, sugeridoPorIa, sugestaoDaIa);
    }

    /**
     * A versão em curadoria passa a ser a aprovada vigente (RN-08) e o cenário pode ir para a sala
     * (RN-01).
     *
     * @throws CenarioForaDeCuradoria se a versão não estiver em curadoria
     * <p>A partir da E2, o curador só aprova depois de conferir o checklist inteiro e de testar o
     * personagem com pelo menos 5 perguntas (RF-10, RN-06).
     *
     * @param conferencia o checklist marcado e as perguntas de teste que o próprio curador fez
     * @throws DecisaoDeCuradoriaNaoPermitida se quem aprova não for curador (RN-05) ou for o autor do
     *     cenário ou da versão, mesmo tendo também o perfil Curador (RN-36)
     * @throws AprovacaoIncompleta com tudo o que falta do checklist e do teste (RN-06)
     */
    public Cenario aprovar(Revisor curador, Instant momento) {
        exigirCuradoria();
        exigirPerfilCurador(curador, "aprova");
        if (temAutor(curador.id()) || versao.criadaPorId() == curador.id()) {
            throw new DecisaoDeCuradoriaNaoPermitida(
                    "Ninguém aprova um cenário que criou, mesmo com o perfil Curador (RN-36)");
        }
        var aprovacao = new Aprovacao(curador.id(), momento);
        return new Cenario(
                id, autorId, StatusDoCenario.APROVADO, versao.aprovada(aprovacao), ultimaDevolucao, sugeridoPorIa, sugestaoDaIa);
    }

    public Cenario aprovar(Revisor curador, ConferenciaDaCuradoria conferencia, Instant momento) {
        exigirCuradoria();
        exigirPerfilCurador(curador, "aprova");
        if (temAutor(curador.id()) || versao.criadaPorId() == curador.id()) {
            throw new DecisaoDeCuradoriaNaoPermitida(
                    "Ninguém aprova um cenário que criou, mesmo com o perfil Curador (RN-36)");
        }
        if (!conferencia.completa()) {
            throw new AprovacaoIncompleta(conferencia);
        }
        var aprovacao = new Aprovacao(curador.id(), momento);
        return new Cenario(
                id, autorId, StatusDoCenario.APROVADO, versao.aprovada(aprovacao), ultimaDevolucao, sugeridoPorIa, sugestaoDaIa);
    }

    /**
     * A versão volta a Rascunho com o comentário do curador (RN-07).
     *
     * @throws CenarioForaDeCuradoria se a versão não estiver em curadoria
     * @throws DecisaoDeCuradoriaNaoPermitida se quem devolve não for curador (RN-05)
     * @throws ComentarioObrigatorio se o comentário estiver vazio
     */
    public Cenario devolver(Revisor curador, String comentario, Instant momento) {
        exigirCuradoria();
        exigirPerfilCurador(curador, "devolve");
        var devolucao = Devolucao.nova(curador.id(), comentario, momento);
        return new Cenario(id, autorId, statusEnquantoEmCuradoria(StatusDoCenario.RASCUNHO), versao.devolvida(),
                devolucao, sugeridoPorIa, sugestaoDaIa);
    }

    /**
     * RF-11, RN-08: o autor edita um cenário que já tem versão vigente. A versão nova começa em
     * rascunho, com o conteúdo da vigente, e o cenário continua na sala com a vigente até a nova ser
     * aprovada.
     *
     * @throws VersaoNaoPermitida se quem pede não for o autor, se o cenário não tiver versão vigente
     *     (rascunho, em curadoria ou arquivado) ou se já houver uma versão em rascunho ou em curadoria
     */
    public Cenario novaVersao(long quemPede) {
        return novaVersaoCom(quemPede, versao.conteudo());
    }

    /**
     * RF-11: uma versão aprovada anterior volta como versão nova em rascunho, com o próximo número. A
     * versão antiga não muda, e a nova passa pela curadoria como qualquer outra (RN-08).
     *
     * @param anterior a versão a restaurar, que precisa ter sido aprovada
     * @throws VersaoNaoPermitida nos mesmos casos de {@link #novaVersao}, ou se a versão a restaurar não
     *     tiver sido aprovada
     */
    public Cenario restaurar(long quemPede, VersaoDoCenario anterior) {
        exigirAutorDaVersao(quemPede);
        if (anterior.status() != StatusDaVersao.APROVADA) {
            throw new VersaoNaoPermitida("Só uma versão aprovada pode ser restaurada (RF-11), e a versão "
                    + anterior.numero() + " está em " + anterior.status());
        }
        // Restaurar a própria versão aprovada mais recente só criaria uma cópia para a curadoria revisar.
        if (anterior.numero() == versao.numero()) {
            throw new VersaoNaoPermitida("A versão " + anterior.numero()
                    + " já é a última aprovada, e não há o que restaurar");
        }
        return novaVersaoCom(quemPede, anterior.conteudo());
    }

    /**
     * RN-37: o curador vê todos os cenários, e o professor só os que criou. Vale para ver o cenário, o
     * histórico e a comparação de versões.
     */
    public boolean podeSerVistoPor(Revisor quem) {
        return quem.curador() || temAutor(quem.id());
    }

    private Cenario novaVersaoCom(long quemPede, ConteudoDoCenario conteudo) {
        exigirAutorDaVersao(quemPede);
        if (!temVersaoVigente()) {
            throw new VersaoNaoPermitida("O cenário " + id + " está " + status
                    + " e não tem versão vigente para editar: rascunho e em curadoria são editados na própria"
                    + " versão, e arquivado não muda mais (RN-09)");
        }
        if (versao.status() != StatusDaVersao.APROVADA) {
            throw new VersaoNaoPermitida("O cenário " + id + " já tem a versão " + versao.numero() + " em "
                    + versao.status() + ". Continue por ela antes de criar outra");
        }
        var nova = new VersaoDoCenario(null, versao.numero() + 1, StatusDaVersao.RASCUNHO, conteudo, quemPede);
        // A marca de sugerido por IA é do cenário, e não da versão: continua nas versões novas (RN-04).
        return new Cenario(id, autorId, status, nova, null, sugeridoPorIa, sugestaoDaIa);
    }

    /** Como a edição do rascunho, criar versões é do autor (Figura 3 dos requisitos). */
    private void exigirAutorDaVersao(long quemPede) {
        if (!temAutor(quemPede)) {
            throw new VersaoNaoPermitida("Só o autor cria versões do cenário " + id);
        }
    }

    /** Aprovado ou suspenso: tem uma versão aprovada que continua sendo a vigente (RN-08, RN-23b). */
    private boolean temVersaoVigente() {
        return status == StatusDoCenario.APROVADO || status == StatusDoCenario.SUSPENSO;
    }

    /** Com versão vigente, o cenário não muda de status enquanto a versão nova vai e volta da curadoria. */
    private StatusDoCenario statusEnquantoEmCuradoria(StatusDoCenario semVigente) {
        return temVersaoVigente() ? status : semVigente;
    }

    /**
     * RF-13: o curador tira o cenário da biblioteca. Versões, devoluções e sessões continuam guardadas
     * para consulta e auditoria (RN-09). Só se arquiva o que está na biblioteca ou foi suspenso
     * (Figura 3 dos requisitos): rascunho e em curadoria nunca chegaram a ela.
     *
     * <p>O curador pode arquivar um cenário que ele mesmo criou: o RN-36 impede só a aprovação, porque é
     * ela que põe conteúdo na sala, e o arquivamento só o tira de lá.
     *
     * @throws DecisaoDeCuradoriaNaoPermitida se quem arquiva não for curador
     * @throws ArquivamentoNaoPermitido se o cenário não estiver Aprovado nem Suspenso
     */
    public Cenario arquivar(Revisor curador) {
        exigirPerfilCurador(curador, "arquiva");
        if (status != StatusDoCenario.APROVADO && status != StatusDoCenario.SUSPENSO) {
            throw new ArquivamentoNaoPermitido(id, status);
        }
        return new Cenario(
                id, autorId, StatusDoCenario.ARQUIVADO, versao, ultimaDevolucao, sugeridoPorIa, sugestaoDaIa);
    }

    /** Olha a versão, e não o cenário, porque a edição de um aprovado gera versão nova em rascunho (RN-08). */
    private void exigirRascunho() {
        if (!versao.emRascunho()) {
            throw new CenarioForaDeRascunho(id, versao.status());
        }
    }

    private void exigirCuradoria() {
        if (!versao.emCuradoria()) {
            throw new CenarioForaDeCuradoria(id, versao.status());
        }
    }

    private static void exigirPerfilCurador(Revisor revisor, String acao) {
        if (!revisor.curador()) {
            throw new DecisaoDeCuradoriaNaoPermitida("Só o curador " + acao + " cenários (RN-05)");
        }
    }
}
