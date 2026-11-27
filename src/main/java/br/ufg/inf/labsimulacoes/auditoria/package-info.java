/**
 * Módulo Auditoria: conteúdo bloqueado, sinalizações, triagem, regras de suspensão e log de ações críticas.
 *
 * <p>Somente os tipos deste pacote raiz formam a API do módulo. Os subpacotes de camada são
 * internos e inacessíveis aos demais módulos.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Auditoria",
        allowedDependencies = {"acesso", "cenarios", "sessoes", "ia", "eventos"})
package br.ufg.inf.labsimulacoes.auditoria;
