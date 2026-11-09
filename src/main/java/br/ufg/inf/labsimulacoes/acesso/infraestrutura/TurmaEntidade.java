package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import br.ufg.inf.labsimulacoes.acesso.dominio.Turma;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Mapeamento da tabela turma. As anotações de JPA ficam aqui porque o domínio não usa framework. */
@Entity
@Table(name = "turma")
class TurmaEntidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String escola;

    @Column(name = "ano_serie", nullable = false)
    private String anoSerie;

    @Column(nullable = false)
    private String turno;

    @Column(nullable = false)
    private String rotulo;

    /** O criado_em fica de fora para o banco preenchê-lo e a alteração não o apagar. */
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected TurmaEntidade() {}

    private TurmaEntidade(Turma turma) {
        this.id = turma.id();
        this.escola = turma.escola();
        this.anoSerie = turma.anoSerie();
        this.turno = turma.turno();
        this.rotulo = turma.rotulo();
        this.atualizadoEm = Instant.now();
    }

    static TurmaEntidade de(Turma turma) {
        return new TurmaEntidade(turma);
    }

    Turma paraDominio() {
        return new Turma(id, escola, anoSerie, turno, rotulo);
    }
}
