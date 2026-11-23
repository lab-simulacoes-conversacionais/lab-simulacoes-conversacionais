// Diálogo de confirmação, como o modal do protótipo: o botão com data-abrir-dialogo="id" abre o
// <dialog data-dialogo id="id">. Fecha pelo "×", pelo "Cancelar", pela tecla Esc e clicando fora da caixa.
document.querySelectorAll("dialog[data-dialogo]").forEach((dialogo) => {
  document.querySelectorAll(`[data-abrir-dialogo="${dialogo.id}"]`)
    .forEach((botao) => botao.addEventListener("click", () => dialogo.showModal()));
  dialogo.querySelectorAll("[data-fechar]").forEach((botao) => botao.addEventListener("click", () => dialogo.close()));
  dialogo.addEventListener("click", (evento) => { if (evento.target === dialogo) dialogo.close(); });
});
