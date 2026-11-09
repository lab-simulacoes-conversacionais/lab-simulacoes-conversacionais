package br.ufg.inf.labsimulacoes.acesso.infraestrutura;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TurmaRepositorioJpa extends JpaRepository<TurmaEntidade, Long> {

    List<TurmaEntidade> findAllByOrderByEscolaAscAnoSerieAscTurnoAscRotuloAsc();
}
