// Fila do curador (RF-08): o diálogo de devolução, como o modal do protótipo. O botão de devolver só
// fica ativo com um comentário (RN-07); o servidor confere de novo.
document.querySelectorAll("dialog[data-devolucao]").forEach((dialogo) => {
  const comentario = dialogo.querySelector("textarea");
  const confirmar = dialogo.querySelector("[data-confirmar-devolucao]");
  const ajuda = dialogo.querySelector("[data-ajuda-comentario]");

  const atualizar = () => {
    const vazio = !comentario.value.trim();
    confirmar.disabled = vazio;
    ajuda.hidden = !vazio;
  };

  const abrir = () => {
    dialogo.showModal();
    atualizar();
    comentario.focus();
  };

  document.querySelectorAll("[data-abrir-devolucao]").forEach((botao) => botao.addEventListener("click", abrir));
  dialogo.querySelectorAll("[data-fechar]").forEach((botao) => botao.addEventListener("click", () => dialogo.close()));
  // Clicar fora da caixa fecha, como no protótipo.
  dialogo.addEventListener("click", (evento) => { if (evento.target === dialogo) dialogo.close(); });
  comentario.addEventListener("input", atualizar);

  // Volta aberto quando o servidor recusou a devolução sem comentário.
  if (dialogo.dataset.abrir === "true") abrir();
});

// Modo de teste (RF-09): a pergunta vai pelo HTMX, que troca a seção inteira com a resposta. A conversa
// rola até a pergunta mais nova ao abrir e a cada troca.
//
// Checklist de revisão (RF-10, RN-06): o aviso ao lado de "Aprovar" diz o que falta, como no protótipo,
// e o botão só fica ativo com as perguntas mínimas feitas e o checklist inteiro marcado. É só um
// adiantamento: o servidor confere tudo de novo ao aprovar.
{
  const atualizarSituacaoDaRevisao = () => {
    const situacao = document.querySelector("[data-situacao-da-revisao]");
    const aprovar = document.querySelector("[data-aprovar]");
    const contador = document.querySelector("[data-contador-do-teste]");
    // Sem o botão (quem vê é o autor) ou sem o contador (o teste ficou indisponível), vale o aviso do servidor.
    if (!situacao || !aprovar || !contador) return;

    const faltam = Number(contador.dataset.minimo) - Number(contador.dataset.contagem);
    const completo = [...document.querySelectorAll("[data-item-do-checklist]")].every((item) => item.checked);
    situacao.textContent = faltam > 1 ? `Faltam ${faltam} perguntas de teste.`
      : faltam === 1 ? "Falta 1 pergunta de teste."
      : completo ? "Revisão completa." : "Conclua todo o checklist.";
    aprovar.disabled = faltam > 0 || !completo;
  };

  const rolarConversaDoTeste = () => {
    const mensagens = document.querySelector("[data-mensagens-do-teste]");
    if (mensagens) mensagens.scrollTop = mensagens.scrollHeight;
    atualizarSituacaoDaRevisao();
  };
  document.body.addEventListener("htmx:afterSwap", rolarConversaDoTeste);
  document.querySelectorAll("[data-item-do-checklist]")
    .forEach((item) => item.addEventListener("change", atualizarSituacaoDaRevisao));
  rolarConversaDoTeste();
}
