// Modo projeção (RF-15, RF-17). A pergunta, a resposta e os controles vão pelo HTMX; aqui ficam os
// atalhos de teclado (RNF-05), o cronômetro, o descarte da resposta parcial no bloqueio ou na falha
// (contrato do ConversaController) e a atualização do painel depois de cada resposta.
(() => {
  const tela = document.querySelector("[data-projecao]");
  if (!tela) return;
  const conversa = () => document.getElementById("conversa");
  const formulario = tela.querySelector("form[data-pergunta]");
  const campo = formulario.querySelector("input[name='texto']");
  const enviar = formulario.querySelector("button[type='submit']");
  const avisos = document.getElementById("aviso-da-sessao");
  const encerramento = tela.querySelector("dialog[data-encerramento]");
  const avisarMudanca = () => document.body.dispatchEvent(new Event("sessao-mudou"));

  // ---------------------------------------------------------------------------------------------
  // Atalhos (RNF-05): P pausa ou retoma, R reinicia, E encerra. Não valem enquanto se digita.
  // ---------------------------------------------------------------------------------------------
  document.addEventListener("keydown", (evento) => {
    if (evento.ctrlKey || evento.metaKey || evento.altKey || evento.repeat) return;
    if (evento.target.closest("input, textarea, select, [contenteditable]") || encerramento.open) return;
    const tecla = evento.key.toLowerCase();
    const botao = document.querySelector(`#painel [data-atalho="${tecla}"]`);
    if (!botao || botao.disabled) return;
    evento.preventDefault();
    botao.click();
  });

  // ---------------------------------------------------------------------------------------------
  // Encerrar: confirmação, como no protótipo.
  // ---------------------------------------------------------------------------------------------
  tela.addEventListener("click", (evento) => {
    if (evento.target.closest("[data-abrir-encerramento]")) encerramento.showModal();
    if (evento.target.closest("[data-fechar]")) encerramento.close();
    const alternar = evento.target.closest("[data-alternar-painel]");
    if (alternar) {
      const oculto = tela.classList.toggle("panel-hidden");
      alternar.textContent = oculto ? "Mostrar painel" : "Ocultar painel";
      alternar.setAttribute("aria-expanded", String(!oculto));
    }
  });
  encerramento.addEventListener("click", (evento) => { if (evento.target === encerramento) encerramento.close(); });

  // ---------------------------------------------------------------------------------------------
  // Pergunta: depois de aceita, limpa o campo e os avisos e atualiza a contagem do painel.
  // ---------------------------------------------------------------------------------------------
  formulario.addEventListener("htmx:beforeRequest", () => { avisos.replaceChildren(); });
  formulario.addEventListener("htmx:afterRequest", (evento) => {
    if (!evento.detail.successful) return;
    formulario.reset();
    campo.focus();
    avisarMudanca();
  });

  // ---------------------------------------------------------------------------------------------
  // Resposta pelo SSE. No bloqueio ou na falha, a resposta parcial some e fica só o aviso neutro
  // (RN-20, RNF-04). Ao fechar o canal, o painel é atualizado: o bloqueio pausa a sessão.
  // ---------------------------------------------------------------------------------------------
  document.body.addEventListener("htmx:sseBeforeMessage", (evento) => {
    const tipo = evento.detail.type;
    if (tipo !== "bloqueio" && tipo !== "falha") return;
    const resposta = evento.target.closest(".mensagem-ia");
    resposta?.querySelector(".mensagem-texto")?.replaceChildren();
    resposta?.classList.add("mensagem-descartada");
    if (tipo === "falha" && resposta && !resposta.querySelector("[data-tentar]")) {
      const tentar = document.createElement("button");
      tentar.type = "button";
      tentar.className = "btn btn-secondary";
      tentar.dataset.tentar = "";
      tentar.textContent = "Tentar de novo";
      // A pergunta continua pendente; a tela recarregada abre o canal outra vez (RNF-04).
      tentar.addEventListener("click", () => window.location.reload());
      resposta.append(tentar);
    }
  });
  document.body.addEventListener("htmx:sseClose", avisarMudanca);

  // ---------------------------------------------------------------------------------------------
  // Painel: o campo da pergunta acompanha o estado (pausada ou no limite) e o cronômetro corre.
  // ---------------------------------------------------------------------------------------------
  let base = 0;
  let desde = Date.now();
  let rodando = false;

  const lerPainel = () => {
    const painel = document.getElementById("painel");
    const pode = painel.dataset.podePerguntar === "true";
    campo.disabled = !pode;
    enviar.disabled = !pode;
    const cronometro = painel.querySelector("[data-cronometro]");
    base = Number(cronometro.dataset.decorrido) || 0;
    rodando = cronometro.dataset.rodando === "true";
    desde = Date.now();
  };

  const formatar = (segundos) =>
    `${String(Math.floor(segundos / 60)).padStart(2, "0")}:${String(segundos % 60).padStart(2, "0")}`;

  setInterval(() => {
    if (!rodando) return;
    const tempo = document.querySelector("#painel [data-tempo]");
    if (tempo) tempo.textContent = formatar(base + Math.floor((Date.now() - desde) / 1000));
  }, 1000);

  // Com hx-swap="outerHTML", o evento nem sempre sai do painel novo; ler de novo é barato.
  document.body.addEventListener("htmx:afterSettle", lerPainel);
  lerPainel();

  // A conversa acompanha a mensagem mais recente, inclusive enquanto a resposta chega.
  new MutationObserver(() => {
    const area = conversa();
    if (area) area.scrollTop = area.scrollHeight;
  }).observe(tela, { childList: true, subtree: true, characterData: true });
  conversa().scrollTop = conversa().scrollHeight;
})();
