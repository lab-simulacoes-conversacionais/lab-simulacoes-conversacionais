package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência do log de ações críticas. A implementação fica na infraestrutura. Não há
 * operação de alterar nem de apagar (RN-35).
 */
public interface LogDeAuditoria {

    /**
     * Impede outras gravações no log até o fim da transação e devolve o hash da última linha, vazio
     * se o log estiver vazio ou se a última linha não tiver hash. Sem a trava, duas gravações ao
     * mesmo tempo encadeariam na mesma linha anterior. Precisa ser chamado dentro de uma transação.
     */
    Optional<String> travarEBuscarUltimoHash();

    /** Devolve a linha com o id gerado. */
    RegistroDeAuditoria gravar(RegistroDeAuditoria registro);

    /** Todas as linhas, na ordem em que foram gravadas, para conferir a cadeia de hashes. */
    List<RegistroDeAuditoria> listarEmOrdem();

    /**
     * Uma página das linhas que atendem ao filtro, da ação mais recente para a mais antiga, pela data
     * em que a ação ocorreu. Linhas com a mesma data saem da gravada por último para a primeira.
     */
    List<RegistroDeAuditoria> consultar(FiltroDoLog filtro);
}
