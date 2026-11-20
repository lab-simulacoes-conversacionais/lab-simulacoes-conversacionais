/**
 * Módulo Métricas: indicadores por sessão, cenário e turma, comparação com sessões de controle, painel e exportação.
 * Também confere o custo diário da IA e alerta o administrador (RNF-13), lendo o consumo pela API do
 * Adaptador de IA e o limite pela API do Acesso.
 *
 * <p>Somente os tipos deste pacote raiz formam a API do módulo. Os subpacotes de camada são
 * internos e inacessíveis aos demais módulos.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Métricas",
        allowedDependencies = {"acesso", "ia", "eventos"})
package br.ufg.inf.labsimulacoes.metricas;
