package br.ufg.inf.labsimulacoes.cenarios.aplicacao;

import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.CenarioNaoEncontrado;
import br.ufg.inf.labsimulacoes.cenarios.dominio.Cenarios;
import br.ufg.inf.labsimulacoes.cenarios.dominio.ComparacaoDeVersoes;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoDoCenario;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNaoEncontrada;
import br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNoHistorico;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Histórico, comparação e restauração de versões de um cenário (RF-11, RN-08).
 *
 * <p>O histórico e a comparação seguem o RN-37: o professor vê os próprios cenários, e o curador vê
 * todos. Criar uma versão nova, para editar um cenário aprovado ou restaurar uma versão anterior, é do
 * autor, como a edição do rascunho. A versão nova passa pela curadoria de sempre, e a vigente continua
 * valendo em sala até ela ser aprovada. O administrador não usa conteúdo pedagógico.
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
public class VersoesDoCenario {

    private final Cenarios cenarios;
    private final RevisorLogado revisorLogado;

    VersoesDoCenario(Cenarios cenarios, RevisorLogado revisorLogado) {
        this.cenarios = cenarios;
        this.revisorLogado = revisorLogado;
    }

    /**
     * Todas as versões, da mais nova para a mais antiga, com a vigente marcada. Versões aprovadas nunca
     * somem do histórico (RN-08).
     *
     * @throws CenarioNaoEncontrado se o cenário não existir
     * @throws AccessDeniedException se for de outro autor e quem pede não for curador (RN-37)
     */
    @Transactional(readOnly = true)
    public List<VersaoNoHistorico> historico(long cenarioId) {
        exigirQuePossaVer(cenarioId);
        return cenarios.historico(cenarioId);
    }

    /**
     * Duas versões lado a lado, campo a campo. A de número menor fica sempre como a antiga.
     *
     * @throws VersaoNaoEncontrada se o cenário não tiver alguma das versões
     * @throws CenarioNaoEncontrado se o cenário não existir
     * @throws AccessDeniedException se for de outro autor e quem pede não for curador (RN-37)
     */
    @Transactional(readOnly = true)
    public ComparacaoDeVersoes comparar(long cenarioId, int umaVersao, int outraVersao) {
        exigirQuePossaVer(cenarioId);
        return ComparacaoDeVersoes.entre(versao(cenarioId, umaVersao), versao(cenarioId, outraVersao));
    }

    /**
     * Editar um cenário aprovado ou suspenso: cria a versão seguinte em rascunho, com o conteúdo da
     * vigente, que o autor então altera e envia à curadoria (RN-08).
     *
     * @throws AccessDeniedException se quem pede não for o autor, antes de qualquer outra conferência
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNaoPermitida se o cenário não tiver versão
     *     vigente ou se já houver uma versão em rascunho ou em curadoria
     */
    @Transactional
    public Cenario criarNovaVersao(long cenarioId) {
        long autor = exigirAutor(cenarioId);
        return cenarios.salvar(paraAlterar(cenarioId).novaVersao(autor));
    }

    /**
     * Restaura uma versão aprovada anterior como a versão seguinte, em rascunho. A versão antiga não
     * muda, e a vigente continua valendo em sala até a restaurada ser aprovada (RF-11, RN-08).
     *
     * @throws AccessDeniedException se quem pede não for o autor, antes de qualquer outra conferência
     * @throws VersaoNaoEncontrada se o cenário não tiver a versão pedida
     * @throws br.ufg.inf.labsimulacoes.cenarios.dominio.VersaoNaoPermitida nos casos de
     *     {@link #criarNovaVersao}, se a versão pedida não tiver sido aprovada ou se ela já for a última
     *     aprovada
     */
    @Transactional
    public Cenario restaurar(long cenarioId, int numero) {
        long autor = exigirAutor(cenarioId);
        Cenario cenario = paraAlterar(cenarioId);
        return cenarios.salvar(cenario.restaurar(autor, versao(cenarioId, numero)));
    }

    /**
     * Confere a autoria antes de travar o cenário e de procurar a versão. Assim quem não é o autor recebe
     * sempre a mesma recusa, sem descobrir quais versões existem nem segurar a linha do cenário (RN-37).
     *
     * @return o id de quem pede, que é o autor
     */
    private long exigirAutor(long cenarioId) {
        Cenario cenario = cenarios.buscar(cenarioId).orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
        long quem = revisorLogado.atual().id();
        if (!cenario.temAutor(quem)) {
            throw new AccessDeniedException("Só o autor cria versões do cenário");
        }
        return quem;
    }

    /** Segura o cenário até o fim da transação, para duas versões novas não pegarem o mesmo número. */
    private Cenario paraAlterar(long cenarioId) {
        return cenarios.buscarParaAlterar(cenarioId).orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
    }

    private VersaoDoCenario versao(long cenarioId, int numero) {
        return cenarios.versao(cenarioId, numero).orElseThrow(() -> new VersaoNaoEncontrada(cenarioId, numero));
    }

    private void exigirQuePossaVer(long cenarioId) {
        Cenario cenario = cenarios.buscar(cenarioId).orElseThrow(() -> new CenarioNaoEncontrado(cenarioId));
        if (!cenario.podeSerVistoPor(revisorLogado.atual())) {
            throw new AccessDeniedException("O professor vê o histórico só dos próprios cenários (RN-37)");
        }
    }
}
