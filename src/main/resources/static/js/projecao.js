// Modo projeção (RF-15, RF-17, RF-20). A pergunta, a resposta e os controles vão pelo HTMX; aqui ficam os
// atalhos de teclado (RNF-05), o cronômetro, o descarte da resposta parcial no bloqueio ou na falha
// (contrato do ConversaController), a atualização do painel depois de cada resposta e as perguntas
// disparadoras que o professor leva ao campo da pergunta (RF-18).
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
    // Perguntas disparadoras (RF-18): o clique só preenche o campo, para o professor ajustar e enviar.
    const disparadora = evento.target.closest("[data-disparadora]");
    if (disparadora && !campo.disabled) {
      campo.value = disparadora.textContent.trim();
      campo.focus();
      campo.setSelectionRange(campo.value.length, campo.value.length);
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
      // RF-20, RNF-04: a pergunta continua pendente. Troca só esta resposta por uma nova, que abre o
      // canal outra vez, sem recarregar a página.
      tentar.addEventListener("click", () => tentarDeNovo(resposta, tentar));
      resposta.append(tentar);
    }
  });
  document.body.addEventListener("htmx:sseClose", avisarMudanca);

  // Ao abrir o canal, inclusive quando o navegador reconecta sozinho no meio da resposta, a resposta
  // começa do zero: o servidor manda a resposta inteira de novo, e o trecho já exibido não se repete.
  document.body.addEventListener("htmx:sseOpen", (evento) => {
    const resposta = evento.target.closest(".mensagem-ia");
    if (!resposta) return;
    resposta.querySelector(".mensagem-texto")?.replaceChildren();
    resposta.querySelector(".aviso")?.replaceChildren();
    resposta.querySelector("[data-tentar]")?.remove();
    resposta.classList.remove("mensagem-descartada");
  });

  const avisar = (texto) => {
    const aviso = document.createElement("p");
    aviso.className = "aviso";
    aviso.setAttribute("role", "alert");
    aviso.textContent = texto;
    avisos.replaceChildren(aviso);
  };
  const SEM_CONEXAO = "Sem conexão com o servidor. Confira a internet e tente de novo: "
    + "a pergunta digitada continua no campo, e a sessão não foi alterada.";

  // Pede a resposta nova ao servidor. Se ele recusar (a sessão pausou ou encerrou, por exemplo), mostra
  // o aviso dele; se a conexão cair, mostra o aviso de queda. Nos dois casos o botão volta a funcionar,
  // sem recarregar a página. Só recarrega quando o login expirou, para levar à tela de entrada.
  const tentarDeNovo = async (resposta, tentar) => {
    tentar.disabled = true;
    try {
      const pedido = await fetch(resposta.dataset.reabrir, { headers: { "HX-Request": "true" } });
      if (pedido.redirected) {
        window.location.reload();
        return;
      }
      const corpo = await pedido.text();
      if (!pedido.ok) {
        // As recusas da conversa vêm como o fragmento do aviso (HX-Retarget); outras, só com o texto padrão.
        if (pedido.headers.get("HX-Retarget")) avisos.innerHTML = corpo;
        else avisar("Não foi possível tentar de novo agora. Confira a sessão e tente outra vez.");
        tentar.disabled = false;
        return;
      }
      avisos.replaceChildren();
      const modelo = document.createElement("template");
      modelo.innerHTML = corpo.trim();
      const nova = modelo.content.firstElementChild;
      resposta.replaceWith(nova);
      htmx.process(nova);
    } catch {
      avisar(SEM_CONEXAO);
      tentar.disabled = false;
    }
  };

  // ---------------------------------------------------------------------------------------------
  // Queda de conexão (RF-20): o pedido não chegou ao servidor. Nada foi gravado, a pergunta digitada
  // continua no campo e o professor pode enviar de novo quando a conexão voltar.
  // ---------------------------------------------------------------------------------------------
  document.body.addEventListener("htmx:sendError", () => avisar(SEM_CONEXAO));

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
