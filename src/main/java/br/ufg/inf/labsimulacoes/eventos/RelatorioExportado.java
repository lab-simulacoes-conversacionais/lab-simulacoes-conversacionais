package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;
import java.util.Map;

/**
 * Um relatório agregado foi exportado (RF-32). Vai para o log de auditoria (RN-35).
 *
 * @param usuarioId quem exportou
 * @param formato {@code CSV} ou {@code PDF}
 * @param filtros recorte do relatório, como sessão, cenário, turma ou período; vazio quando não há
 * @param ocorridoEm momento da exportação
 */
public record RelatorioExportado(long usuarioId, String formato, Map<String, String> filtros, Instant ocorridoEm) {}
