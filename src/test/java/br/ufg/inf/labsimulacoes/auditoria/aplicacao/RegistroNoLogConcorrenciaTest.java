package br.ufg.inf.labsimulacoes.auditoria.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufg.inf.labsimulacoes.InfraestruturaDeTeste;
import br.ufg.inf.labsimulacoes.auditoria.dominio.AcaoCritica;
import br.ufg.inf.labsimulacoes.auditoria.dominio.LogDeAuditoria;
import br.ufg.inf.labsimulacoes.auditoria.dominio.RegistroDeAuditoria;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Gravações simultâneas, cada uma na sua transação, como fazem os ouvintes de eventos. */
@SpringBootTest
@Import(InfraestruturaDeTeste.class)
class RegistroNoLogConcorrenciaTest {

    private static final int GRAVACOES = 40;

    @Autowired
    private RegistroNoLog registro;

    @Autowired
    private LogDeAuditoria log;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void gravacoesSimultaneasNaoEncadeiamNaMesmaLinha() throws Exception {
        long usuario = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Curadora', ?, 'hash') RETURNING id",
                Long.class, UUID.randomUUID() + "@escola.test");
        var largada = new CountDownLatch(1);
        var resultados = new ArrayList<Future<RegistroDeAuditoria>>();

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < GRAVACOES; i++) {
                long numero = i;
                resultados.add(executor.submit(() -> {
                    largada.await();
                    return registro.registrar(RegistroDeAuditoria.novo(
                            AcaoCritica.APROVAR_CENARIO, usuario, String.valueOf(numero), Map.of(), Instant.now()));
                }));
            }
            largada.countDown();
            for (Future<RegistroDeAuditoria> resultado : resultados) {
                resultado.get();
            }
        }

        List<RegistroDeAuditoria> todas = log.listarEmOrdem();
        int conferidas = 0;
        for (int i = 0; i < todas.size(); i++) {
            if (todas.get(i).usuarioId() == usuario) {
                String anterior = i == 0 ? null : todas.get(i - 1).hash();
                assertThat(todas.get(i).confereCom(anterior)).as("linha %d", todas.get(i).id()).isTrue();
                conferidas++;
            }
        }
        assertThat(conferidas).isEqualTo(GRAVACOES);
    }
}
