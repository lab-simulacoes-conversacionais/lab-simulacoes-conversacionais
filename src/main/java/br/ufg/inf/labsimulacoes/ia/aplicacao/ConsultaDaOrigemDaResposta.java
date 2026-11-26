package br.ufg.inf.labsimulacoes.ia.aplicacao;

import br.ufg.inf.labsimulacoes.ia.OrigemDaResposta;
import br.ufg.inf.labsimulacoes.ia.OrigemDasRespostas;
import br.ufg.inf.labsimulacoes.ia.dominio.RegistroDeMetadados;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * A origem de uma resposta, lida dos metadados gravados (RNF-10). Expõe só o provedor, o modelo e a
 * versão do prompt: tokens, custo e parâmetros não saem do adaptador. Professor e curador são os perfis
 * que sinalizam uma resposta (RN-21).
 */
@Service
@PreAuthorize("hasAnyRole('PROFESSOR', 'CURADOR')")
class ConsultaDaOrigemDaResposta implements OrigemDasRespostas {

    private final RegistroDeMetadados registro;

    ConsultaDaOrigemDaResposta(RegistroDeMetadados registro) {
        this.registro = registro;
    }

    @Override
    public Optional<OrigemDaResposta> daResposta(long respostaIaMetaId) {
        return registro.buscar(respostaIaMetaId)
                .map(metadados -> new OrigemDaResposta(
                        metadados.provedor(), metadados.modelo(), metadados.versaoPrompt()));
    }
}
