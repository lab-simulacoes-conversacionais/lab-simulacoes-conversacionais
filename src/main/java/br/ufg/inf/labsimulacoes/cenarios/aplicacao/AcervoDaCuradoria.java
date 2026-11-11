package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Pagina;
import br.ufg.inf.labsimulacoes.cenarios.dominio.StatusDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.TrechoDoAcervo;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O curador vê todos os cenários, de todos os autores e em qualquer status (RN-37). A biblioteca
 * (RF-12) mostra só os aprovados; aqui o curador acha também o que espera decisão (RF-08). O
 * professor vê só os próprios cenários, e o administrador não usa conteúdo pedagógico.
 */
@Service
@PreAuthorize("hasRole('CURADOR')")
public class AcervoDaCuradoria {

    private final Cenarios cenarios;

    AcervoDaCuradoria(Cenarios cenarios) {
        this.cenarios = cenarios;
    }

    /**
     * Todos os cenários, em ordem de título, cada um com a versão mais recente.
     *
     * @param pagina a partir de 0
     * @param tamanho de 1 a {@link Pagina#TAMANHO_MAXIMO}
     * @throws IllegalArgumentException se a página for negativa ou o tamanho estiver fora da faixa
     */
    @Transactional(readOnly = true)
    public TrechoDoAcervo listar(int pagina, int tamanho) {
        return cenarios.listarTodos(null, new Pagina(pagina, tamanho));
    }

    /**
     * Só os cenários com o status pedido, por exemplo {@link StatusDoCenario#EM_CURADORIA} para ver o
     * que espera aprovação ou devolução.
     *
     * @throws IllegalArgumentException se a página for negativa ou o tamanho estiver fora da faixa
     */
    @Transactional(readOnly = true)
    public TrechoDoAcervo listar(StatusDoCenario status, int pagina, int tamanho) {
        Objects.requireNonNull(status, "status");
        return cenarios.listarTodos(status, new Pagina(pagina, tamanho));
    }
}
