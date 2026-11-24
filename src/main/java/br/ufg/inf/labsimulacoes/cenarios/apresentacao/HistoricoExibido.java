package br.ufg.inf.labsimulacoes.cenarios.apresentacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDaVersao;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNoHistorico;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * O histórico de versões de um cenário pronto para a tela (RF-11), na fila do curador e na página de
 * versões: as versões da mais nova para a mais antiga, o que o autor pode fazer com elas e o par que a
 * comparação mostra quando ninguém escolheu outro.
 *
 * @param versoes da mais nova para a mais antiga
 * @param podeCriarVersao o autor pode abrir uma versão nova a partir da vigente (RN-08)
 * @param novaPadrao a versão mais nova; {@code null} se o cenário só tem uma versão
 * @param antigaPadrao a última aprovada antes da mais nova, que é a que está ou esteve em sala, ou a
 *     anterior a ela, se nenhuma foi aprovada; {@code null} se o cenário só tem uma versão
 */
record HistoricoExibido(List<VersaoExibida> versoes, boolean podeCriarVersao, Integer novaPadrao, Integer antigaPadrao) {

    /** As datas aparecem no horário da escola, e não no do servidor. */
    private static final DateTimeFormatter DATA_E_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    HistoricoExibido {
        versoes = List.copyOf(versoes);
    }

    /**
     * @param historico como {@code VersoesDoCenario.historico} devolve: da mais nova para a mais antiga
     * @param souAutor quem vê é o autor do cenário, o único que cria e restaura versões
     */
    static HistoricoExibido de(List<VersaoNoHistorico> historico, Cenario cenario, boolean souAutor) {
        boolean podeCriar = souAutor && aceitaVersaoNova(cenario);
        int ultima = cenario.versao().numero();
        List<VersaoExibida> versoes = historico.stream()
                .map(item -> VersaoExibida.de(item, podeCriar && item.versao().numero() != ultima))
                .toList();
        if (historico.size() < 2) {
            return new HistoricoExibido(versoes, podeCriar, null, null);
        }
        int nova = historico.getFirst().versao().numero();
        int antiga = historico.stream()
                .skip(1)
                .filter(item -> item.versao().status() == StatusDaVersao.APROVADA)
                .findFirst()
                .orElse(historico.get(1))
                .versao()
                .numero();
        return new HistoricoExibido(versoes, podeCriar, nova, antiga);
    }

    public boolean temComparacao() {
        return novaPadrao != null;
    }

    /**
     * Como no domínio ({@code Cenario.novaVersao}): só um cenário aprovado ou suspenso, com a versão mais
     * nova já aprovada, abre uma versão nova. Rascunho e em curadoria são editados na própria versão.
     */
    private static boolean aceitaVersaoNova(Cenario cenario) {
        boolean temVigente = cenario.status() == StatusDoCenario.APROVADO || cenario.status() == StatusDoCenario.SUSPENSO;
        return temVigente && cenario.versao().status() == StatusDaVersao.APROVADA;
    }

    /**
     * Uma linha do histórico.
     *
     * @param vigente a versão que vale em sala agora (RN-08)
     * @param restauravel o autor pode trazê-la de volta como versão nova: só versões aprovadas que não
     *     são a mais nova (RF-11)
     */
    record VersaoExibida(int numero, StatusDaVersao status, String criadaEm, boolean vigente, boolean restauravel) {

        static VersaoExibida de(VersaoNoHistorico item, boolean podeRestaurarAlguma) {
            StatusDaVersao status = item.versao().status();
            return new VersaoExibida(
                    item.versao().numero(),
                    status,
                    DATA_E_HORA.format(item.criadaEm()),
                    item.vigente(),
                    podeRestaurarAlguma && status == StatusDaVersao.APROVADA);
        }

        public String rotulo() {
            if (vigente) {
                return "Vigente";
            }
            return switch (status) {
                case RASCUNHO -> "Rascunho";
                case EM_CURADORIA -> "Em curadoria";
                case APROVADA -> "Aprovada";
            };
        }

        /** Classe do selo, nas cores do protótipo. */
        public String classe() {
            return status == StatusDaVersao.APROVADA ? (vigente ? "badge-positive" : "badge-neutral") : "badge-pending";
        }

        /** Ícone do selo, como no protótipo. */
        public String icone() {
            return switch (status) {
                case RASCUNHO -> "•";
                case EM_CURADORIA -> "◷";
                case APROVADA -> "✓";
            };
        }
    }
}
