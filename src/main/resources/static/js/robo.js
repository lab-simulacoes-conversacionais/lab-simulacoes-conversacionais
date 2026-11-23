// Robô guardado para outro uso (fora da tela de login por enquanto): fica parado e só os olhos dele
// acompanham o mouse pela tela; quando o cursor sai da janela ou encosta nele, fecha os olhinhos de alegria.
// O markup dele está comentado em login.html. Só com mouse e sem o pedido de movimento reduzido do sistema.
(() => {
  const robo = document.querySelector("[data-robo]");
  if (!robo) return;
  const olhar = robo.querySelector("[data-robo-olhar]");
  robo.classList.add("feliz");
  if (!window.matchMedia("(pointer: fine) and (prefers-reduced-motion: no-preference)").matches) return;

  // Centro do visor, em fração do tamanho do robô (ver o viewBox do rosto em login.html).
  const VISOR_X = 0.619;
  const VISOR_Y = 0.274;

  let cursor = null;
  let quadro = 0;

  const desenhar = () => {
    quadro = 0;
    if (!cursor) {
      olhar.setAttribute("transform", "translate(0 0)");
      robo.classList.add("feliz");
      return;
    }
    const area = robo.getBoundingClientRect();
    const dx = cursor.x - (area.left + area.width * VISOR_X);
    const dy = cursor.y - (area.top + area.height * VISOR_Y);
    const distancia = Math.hypot(dx, dy) || 1;
    const forca = Math.min(1, distancia / 220);
    olhar.setAttribute("transform", `translate(${(dx / distancia * forca * 26).toFixed(1)} ${(dy / distancia * forca * 18).toFixed(1)})`);
    const tocando = cursor.x >= area.left && cursor.x <= area.right && cursor.y >= area.top && cursor.y <= area.bottom;
    robo.classList.toggle("feliz", tocando);
  };
  const agendar = () => { if (!quadro) quadro = requestAnimationFrame(desenhar); };

  document.addEventListener("pointermove", (evento) => {
    cursor = { x: evento.clientX, y: evento.clientY };
    agendar();
  });
  document.documentElement.addEventListener("pointerleave", () => {
    cursor = null;
    agendar();
  });
})();
