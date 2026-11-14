package br.ufg.inf.labsimulacoes.cenarios.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Cenário de simulação (RF-06, RF-08). Cada transição confere as regras antes de mudar o estado, para
 * que cada regra tenha um teste próprio, sem banco nem tela (Stack, seção 5).
 *
 * <p>Na E1 o ciclo é Rascunho, Em curadoria e Aprovado; a devolução volta a Rascunho (Figura 3 dos
 * requisitos). A aprovação é só registrada, sem checklist (RN-06).
 *
 * @param versao a versão mais recente
 * @param ultimaDevolucao a devolução mais recente desta versão, para o autor saber o que mudar
 *     (RN-07); {@code null} se ela nunca foi devolvida
 * @param sugestaoDaIa metadados da geração que sugeriu o cenário (RN-04, RNF-10); {@code null} se ele
 *     foi criado do zero
 */
public record Cenario(
        Long id, long autorId, StatusDoCenario status, VersaoDoCenario versao, Devolucao ultimaDevolucao,
        Long sugestaoDaIa) {

    public Cenario {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(versao, "versao");
    }

    /** Cenário criado do zero. */
    public Cenario(Long id, long autorId, StatusDoCenario status, VersaoDoCenario versao, Devolucao ultimaDevolucao) {
        this(id, autorId, status, versao, ultimaDevolucao, null);
    }

    /** Cenário criado do zero cuja versão nunca foi devolvida. */
    public Cenario(Long id, long autorId, StatusDoCenario status, VersaoDoCenario versao) {
        this(id, autorId, status, versao, null);
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
                null, geracao);
    }

    /** RN-04: o curador confere os fatos de um cenário sugerido pela IA com as fontes. */
    public boolean sugeridoPorIa() {
        return sugestaoDaIa != null;
    }

    public boolean temAutor(long usuarioId) {
        return autorId == usuarioId;
    }

    /** Mantém o número da versão: só o que já saiu do rascunho vira histórico. */
    public Cenario alterarRascunho(ConteudoDoCenario conteudo) {
        exigirRascunho();
        return new Cenario(id, autorId, status, versao.comConteudo(conteudo), ultimaDevolucao, sugestaoDaIa);
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
        return new Cenario(
                id, autorId, StatusDoCenario.EM_CURADORIA, versao.enviada(), ultimaDevolucao, sugestaoDaIa);
    }

    /**
     * A versão em curadoria passa a ser a aprovada vigente (RN-08) e o cenário pode ir para a sala
     * (RN-01).
     *
     * @throws CenarioForaDeCuradoria se a versão não estiver em curadoria
     * @throws DecisaoDeCuradoriaNaoPermitida se quem aprova não for curador (RN-05) ou for o autor do
     *     cenário ou da versão, mesmo tendo também o perfil Curador (RN-36)
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
                id, autorId, StatusDoCenario.APROVADO, versao.aprovada(aprovacao), ultimaDevolucao, sugestaoDaIa);
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
        return new Cenario(id, autorId, StatusDoCenario.RASCUNHO, versao.devolvida(), devolucao, sugestaoDaIa);
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
