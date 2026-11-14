// Tela de login: as formas da marca acompanham o mouse em profundidades diferentes (parallax) e uma
// luz suave segue o cursor. O formulário não se move, para não atrapalhar quem digita.
// Só com mouse e sem o pedido de movimento reduzido do sistema; sem isto, a tela fica estática.
(() => {
  const tela = document.querySelector(".login-page");
  const podeAnimar = window.matchMedia("(pointer: fine) and (prefers-reduced-motion: no-preference)");
  if (!tela || !podeAnimar.matches) return;

  let quadro = 0;
  let x = 0;
  let y = 0;

  const aplicar = () => {
    quadro = 0;
    const largura = window.innerWidth;
    const altura = window.innerHeight;
    tela.style.setProperty("--mx", ((x / largura) * 2 - 1).toFixed(3));
    tela.style.setProperty("--my", ((y / altura) * 2 - 1).toFixed(3));
    tela.style.setProperty("--x", `${x}px`);
    tela.style.setProperty("--y", `${y}px`);
  };

  // Um cálculo por quadro, mesmo com muitos eventos do mouse.
  tela.addEventListener("pointermove", (evento) => {
    x = evento.clientX;
    y = evento.clientY;
    tela.classList.add("interativo");
    if (!quadro) quadro = requestAnimationFrame(aplicar);
  });

  // Ao sair da janela, as formas voltam devagar ao lugar.
  tela.addEventListener("pointerleave", () => {
    tela.classList.remove("interativo");
    tela.style.setProperty("--mx", "0");
    tela.style.setProperty("--my", "0");
  });
})();
