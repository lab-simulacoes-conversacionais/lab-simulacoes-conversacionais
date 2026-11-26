package br.ufg.inf.labsimulacoes.auditoria.dominio;

import java.time.Instant;
import java.util.Objects;

/** O professor passou do prazo de 7 dias para sinalizar uma resposta da própria sessão (RN-21). */
public class ForaDoPrazoDeSinalizacao extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Instant limite;

    public ForaDoPrazoDeSinalizacao(long sessaoId, Instant limite) {
        super("O prazo para sinalizar respostas da sessão " + sessaoId + " terminou em " + limite);
        this.limite = Objects.requireNonNull(limite, "limite");
    }

    /** O último momento em que ainda dava para sinalizar. */
    public Instant limite() {
        return limite;
    }
}
