// Os olhinhos do robozinho da logo (o "ó" de LABORATÓRIO) acompanham o mouse pela tela; quando o cursor sai
// da janela, voltam para o centro. Só com mouse e sem o pedido de movimento reduzido do sistema.
(() => {
  const robos = [...document.querySelectorAll(".marca-ia")];
  if (!robos.length) return;
  if (!window.matchMedia("(pointer: fine) and (prefers-reduced-motion: no-preference)").matches) return;

  // Centro dos olhos e quanto eles podem andar sem sair do visor, nas unidades do viewBox (0 0 16 18).
  const OLHOS_X = 8 / 16;
  const OLHOS_Y = 11.35 / 18;
  const ALCANCE_X = 1.5;
  const ALCANCE_Y = 0.75;

  let cursor = null;
  let quadro = 0;

  const desenhar = () => {
    quadro = 0;
    for (const robo of robos) {
      const olhar = robo.querySelector(".marca-ia-olhar");
      const area = robo.getBoundingClientRect();
      if (!cursor || !area.width) {
        olhar.style.translate = "";
        continue;
      }
      const dx = cursor.x - (area.left + area.width * OLHOS_X);
      const dy = cursor.y - (area.top + area.height * OLHOS_Y);
      const distancia = Math.hypot(dx, dy) || 1;
      const forca = Math.min(1, distancia / 70);
      olhar.style.translate = `${(dx / distancia * forca * ALCANCE_X).toFixed(2)}px ${(dy / distancia * forca * ALCANCE_Y).toFixed(2)}px`;
    }
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
