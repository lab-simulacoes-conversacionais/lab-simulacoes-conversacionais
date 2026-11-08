package br.ufg.inf.labsimulacoes.eventos;

import java.time.Instant;
import java.util.Set;

/**
 * Os perfis de um usuário mudaram, inclusive ao ser cadastrado (RN-35).
 *
 * @param usuarioId quem teve os perfis alterados
 * @param alteradoPorId quem fez a alteração
 * @param perfisAnteriores vazio quando o usuário acabou de ser cadastrado
 * @param perfisNovos perfis depois da alteração
 * @param ocorridoEm momento da alteração
 */
public record PerfilAlterado(
        long usuarioId,
        long alteradoPorId,
        Set<String> perfisAnteriores,
        Set<String> perfisNovos,
        Instant ocorridoEm) {}
