// Menu da conta no cabeçalho: fecha ao clicar fora ou com Esc, como os menus dos sistemas comuns.
// Sem JavaScript, o <details> abre e fecha pelo próprio resumo.
document.querySelectorAll("details[data-menu-do-usuario]").forEach((menu) => {
  document.addEventListener("click", (evento) => {
    if (menu.open && !menu.contains(evento.target)) menu.open = false;
  });
  document.addEventListener("keydown", (evento) => {
    if (evento.key === "Escape" && menu.open) {
      menu.open = false;
      menu.querySelector("summary").focus();
    }
  });
});
