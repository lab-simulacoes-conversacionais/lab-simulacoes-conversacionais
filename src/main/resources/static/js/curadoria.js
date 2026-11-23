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
{
  const rolarConversaDoTeste = () => {
    const mensagens = document.querySelector("[data-mensagens-do-teste]");
    if (mensagens) mensagens.scrollTop = mensagens.scrollHeight;
  };
  document.body.addEventListener("htmx:afterSwap", rolarConversaDoTeste);
  rolarConversaDoTeste();
}
