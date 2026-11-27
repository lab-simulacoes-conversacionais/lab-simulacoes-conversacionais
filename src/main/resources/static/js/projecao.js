// Modo projeção (RF-15, RF-17, RF-20). A pergunta, a resposta e os controles vão pelo HTMX; aqui ficam os
// atalhos de teclado (RNF-05), o cronômetro, o descarte da resposta parcial no bloqueio ou na falha
// (contrato do ConversaController), a atualização do painel depois de cada resposta, as perguntas
// disparadoras que o professor leva ao campo da pergunta (RF-18) e o modal de sinalizar erro (RF-23).
//
// #43: o que a turma não deve ver vai para o painel do professor, e não para a área projetada. Na conversa
// fica só um aviso neutro; o aviso detalhado e o "Tentar de novo" ficam na área de avisos do painel.
(() => {
  const tela = document.querySelector("[data-projecao]");
  if (!tela) return;
  const conversa = () => document.getElementById("conversa");
  const formulario = tela.querySelector("form[data-pergunta]");
  const campo = formulario.querySelector("input[name='texto']");
  const enviar = formulario.querySelector("button[type='submit']");
  // Fica no painel com hx-preserve: é o mesmo elemento depois de cada atualização do painel.
  const avisos = document.getElementById("aviso-da-sessao");
  const encerramento = tela.querySelector("dialog[data-encerramento]");
  const sinalizacao = tela.querySelector("dialog[data-sinalizacao]");
  const conteudoDaSinalizacao = sinalizacao.querySelector("#sinalizacao-conteudo");
  const alternador = tela.querySelector("[data-alternar-painel]");
  const avisarMudanca = () => document.body.dispatchEvent(new Event("sessao-mudou"));

  // ---------------------------------------------------------------------------------------------
  // Atalhos (RNF-05): P pausa ou retoma, R reinicia, S sinaliza erro, E encerra. Não valem enquanto se
  // digita nem com um modal aberto.
  // ---------------------------------------------------------------------------------------------
  document.addEventListener("keydown", (evento) => {
    if (evento.ctrlKey || evento.metaKey || evento.altKey || evento.repeat) return;
    if (evento.target.closest("input, textarea, select, [contenteditable]") || tela.querySelector("dialog[open]")) return;
    const tecla = evento.key.toLowerCase();
    const botao = document.querySelector(`#painel [data-atalho="${tecla}"]`);
    if (!botao || botao.disabled) return;
    evento.preventDefault();
    botao.click();
  });

  // ---------------------------------------------------------------------------------------------
  // Modais, como no protótipo: encerrar pede confirmação; sinalizar erro (RF-23) abre o formulário da
  // última resposta, que o HTMX do botão traz do servidor enquanto o modal mostra que está carregando.
  // ---------------------------------------------------------------------------------------------
  const CARREGANDO = conteudoDaSinalizacao.innerHTML;
  tela.addEventListener("click", (evento) => {
    if (evento.target.closest("[data-abrir-encerramento]")) encerramento.showModal();
    if (evento.target.closest("[data-abrir-sinalizacao]")) {
      conteudoDaSinalizacao.innerHTML = CARREGANDO;
      sinalizacao.showModal();
    }
    evento.target.closest("[data-fechar]")?.closest("dialog")?.close();
    if (evento.target.closest("[data-alternar-painel]")) {
      tela.classList.toggle("panel-hidden");
      conferirAvisos();
    }
    // Perguntas disparadoras (RF-18): o clique só preenche o campo, para o professor ajustar e enviar.
    const disparadora = evento.target.closest("[data-disparadora]");
    if (disparadora && !campo.disabled) {
      campo.value = disparadora.textContent.trim();
      campo.focus();
      campo.setSelectionRange(campo.value.length, campo.value.length);
    }
  });
  for (const modal of [encerramento, sinalizacao]) {
    modal.addEventListener("click", (evento) => { if (evento.target === modal) modal.close(); });
  }

  // ---------------------------------------------------------------------------------------------
  // Pergunta: depois de aceita, limpa o campo e os avisos e atualiza a contagem do painel.
  // ---------------------------------------------------------------------------------------------
  formulario.addEventListener("htmx:beforeRequest", () => {
    limparAvisos();
    campo.removeAttribute("aria-invalid");
  });
  // O campo fica marcado enquanto o texto recusado (dado pessoal, longo demais ou vazio) não muda; o
  // motivo fica no painel, porque a turma vê o campo.
  campo.addEventListener("input", () => campo.removeAttribute("aria-invalid"));
  // Uma pergunta recusada (400, 409, 422 e 503) volta com o aviso e o HX-Retarget para a área de avisos.
  // O HTMX 2 não troca respostas de erro por padrão, então o aviso só aparece com este ajuste. O evento é
  // disparado no alvo original (#conversa), e detail.elt é esse alvo, não o formulário; quem pediu está em
  // requestConfig.elt, que o HTMX 2.0.4 preenche ao disparar htmx:configRequest no formulário. Só as recusas
  // vêm com o HX-Retarget para a área de avisos; um 500 sem ele continua tratado como erro.
  // A troca do HTMX apagaria a área inteira, com o "Tentar de novo" de uma falha (#43); por isso o aviso da
  // recusa entra pelo avisarProfessor, que preserva o da falha.
  document.body.addEventListener("htmx:beforeSwap", (evento) => {
    const { requestConfig, xhr, target } = evento.detail;
    if (requestConfig?.elt !== formulario || xhr.status < 400 || target !== avisos) return;
    evento.detail.shouldSwap = false;
    evento.detail.isError = false;
    avisarProfessor(textoDe(xhr.responseText));
  });
  // Com isError = false o HTMX marca a recusa como "successful"; o status separa, para o texto ficar no campo.
  formulario.addEventListener("htmx:afterRequest", (evento) => {
    const status = evento.detail.xhr.status;
    if (status === 400 || status === 422) campo.setAttribute("aria-invalid", "true");
    if (!evento.detail.successful || evento.detail.xhr.status >= 400) return;
    formulario.reset();
    campo.focus();
    avisarMudanca();
  });

  // ---------------------------------------------------------------------------------------------
  // Avisos ao professor (#43), na área do painel. O mais recente substitui o anterior, menos o aviso da
  // falha da resposta: ele leva o "Tentar de novo" e fica até a resposta chegar, porque a pergunta continua
  // pendente e é o único jeito de pedi-la de novo sem recarregar a página. Uma pergunta nova enquanto isso é
  // recusada (409), e a recusa aparece junto com ele.
  // ---------------------------------------------------------------------------------------------
  const limparAvisos = () => {
    for (const aviso of [...avisos.children]) {
      if (!aviso.matches("[data-aviso-da-resposta]")) aviso.remove();
    }
  };

  /** @param daResposta o aviso da falha da resposta, que substitui inclusive o da falha anterior */
  const avisarProfessor = (texto, daResposta = false) => {
    const aviso = document.createElement("div");
    aviso.className = "aviso";
    aviso.setAttribute("role", "alert");
    const paragrafo = document.createElement("p");
    paragrafo.textContent = texto;
    aviso.append(paragrafo);
    if (daResposta) {
      aviso.dataset.avisoDaResposta = "";
      avisos.replaceChildren(aviso);
    } else {
      limparAvisos();
      avisos.prepend(aviso);
    }
    return aviso;
  };

  // Os dados do evento e as recusas chegam como HTML escapado; o aviso usa só o texto.
  const textoDe = (html) => new DOMParser().parseFromString(html, "text/html").body.textContent.trim();

  // O aviso neutro vem dos modelos da página (sessoes/conversa.html), para o texto ficar em um lugar só.
  const avisoNeutro = (tipo) =>
    tela.querySelector(`template[data-modelo="${tipo}"]`).content.firstElementChild.cloneNode(true);

  // ---------------------------------------------------------------------------------------------
  // Resposta pelo SSE. No bloqueio, na falha ou no dado pessoal da pendente, a resposta parcial some
  // (RN-20, RNF-04, RN-13). A conversa projetada mostra só o aviso neutro, e o aviso do evento vai para o
  // painel (#43). No bloqueio, o aviso do painel é o da pausa automática, que volta com o painel quando o
  // canal fecha. Na falha, o "Tentar de novo" fica junto do aviso, no painel.
  // ---------------------------------------------------------------------------------------------
  const AVISOS_DA_RESPOSTA = ["bloqueio", "falha", "dado-pessoal"];
  document.body.addEventListener("htmx:sseBeforeMessage", (evento) => {
    const tipo = evento.detail.type;
    if (!AVISOS_DA_RESPOSTA.includes(tipo)) return;
    const resposta = evento.target.closest(".mensagem-ia");
    if (!resposta) return;
    // Sem a troca do HTMX, o aviso detalhado não entra na área projetada.
    evento.preventDefault();
    resposta.querySelector(".mensagem-texto")?.replaceChildren();
    resposta.classList.add("mensagem-descartada");
    resposta.querySelector("[data-aviso-neutro]")?.remove();
    resposta.append(avisoNeutro(tipo));
    if (tipo === "bloqueio") return;
    const aviso = avisarProfessor(textoDe(evento.detail.data), tipo === "falha");
    if (tipo === "falha") {
      const tentar = document.createElement("button");
      tentar.type = "button";
      tentar.className = "btn btn-secondary";
      tentar.dataset.tentar = "";
      tentar.textContent = "Tentar de novo";
      // RF-20, RNF-04: a pergunta continua pendente. Troca só esta resposta por uma nova, que abre o
      // canal outra vez, sem recarregar a página.
      tentar.addEventListener("click", () => tentarDeNovo(resposta, aviso, tentar));
      aviso.append(tentar);
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
    resposta.querySelector("[data-aviso-neutro]")?.remove();
    resposta.classList.remove("mensagem-descartada");
    avisos.querySelectorAll("[data-aviso-da-resposta]").forEach((aviso) => aviso.remove());
  });

  const SEM_CONEXAO = "Sem conexão com o servidor. Confira a internet e tente de novo: "
    + "a pergunta digitada continua no campo, e a sessão não foi alterada.";

  // Pede a resposta nova ao servidor. Se ele recusar (a sessão pausou ou encerrou, por exemplo), mostra
  // o aviso dele no lugar do aviso da falha; se a conexão cair, mostra o aviso de queda. Nos dois casos o
  // botão continua no painel e volta a funcionar, sem recarregar a página. Só recarrega quando o login
  // expirou, para levar à tela de entrada.
  const tentarDeNovo = async (resposta, aviso, tentar) => {
    tentar.disabled = true;
    const recusar = (texto) => {
      aviso.querySelector("p").textContent = texto;
      tentar.disabled = false;
    };
    try {
      const pedido = await fetch(resposta.dataset.reabrir, { headers: { "HX-Request": "true" } });
      if (pedido.redirected) {
        window.location.reload();
        return;
      }
      const corpo = await pedido.text();
      if (!pedido.ok) {
        // As recusas da conversa vêm como o fragmento do aviso (HX-Retarget); outras, só com o texto padrão.
        recusar(pedido.headers.get("HX-Retarget")
          ? textoDe(corpo)
          : "Não foi possível tentar de novo agora. Confira a sessão e tente outra vez.");
        return;
      }
      // A pergunta voltou a ser respondida: a falha e as recusas de enquanto ela esperava não valem mais.
      avisos.replaceChildren();
      const modelo = document.createElement("template");
      modelo.innerHTML = corpo.trim();
      const nova = modelo.content.firstElementChild;
      resposta.replaceWith(nova);
      htmx.process(nova);
    } catch {
      recusar(SEM_CONEXAO);
    }
  };

  // ---------------------------------------------------------------------------------------------
  // Queda de conexão (RF-20): o pedido não chegou ao servidor. Nada foi gravado, a pergunta digitada
  // continua no campo e o professor pode enviar de novo quando a conexão voltar.
  // ---------------------------------------------------------------------------------------------
  document.body.addEventListener("htmx:sendError", (evento) => {
    if (!erroNoModal(evento, "Sem conexão com o servidor. Confira a internet e tente de novo.")) {
      avisarProfessor(SEM_CONEXAO);
    }
  });

  // ---------------------------------------------------------------------------------------------
  // Falhas do modal de sinalizar (#43). O HTMX 2 não troca respostas 4xx e 5xx, então, sem isto, o modal
  // ficaria em "Carregando…" ou o envio não daria retorno. Se o carregamento falhar, o modal mostra o erro e
  // o botão de fechar; se o envio falhar, o aviso aparece acima do formulário, que continua preenchido. Com
  // o login expirado, o servidor redireciona para a entrada: a página recarrega, em vez de mostrar a tela
  // de login dentro do modal.
  // ---------------------------------------------------------------------------------------------
  const doModal = (evento) => evento.detail.elt?.closest?.("[data-abrir-sinalizacao], #sinalizacao");

  const ENVIO_FALHOU = "Não foi possível enviar a sinalização agora. O que você preencheu continua aqui: "
    + "tente enviar de novo em instantes.";

  /** @param texto o aviso quando o modal nem carregou; no envio, o aviso é o ENVIO_FALHOU */
  const erroNoModal = (evento, texto) => {
    if (!doModal(evento)) return false;
    const erro = document.createElement("p");
    erro.className = "field-error";
    erro.setAttribute("role", "alert");
    erro.dataset.erroDoModal = "";
    const formulario = conteudoDaSinalizacao.querySelector("form .modal-body");
    if (formulario && !evento.detail.elt.matches("[data-abrir-sinalizacao]")) {
      erro.textContent = ENVIO_FALHOU;
      formulario.querySelector("[data-erro-do-modal]")?.remove();
      formulario.prepend(erro);
      return true;
    }
    erro.textContent = texto;
    const corpo = document.createElement("div");
    corpo.className = "modal-body";
    corpo.append(erro);
    const rodape = document.createElement("div");
    rodape.className = "modal-footer";
    const fechar = document.createElement("button");
    fechar.type = "button";
    fechar.className = "btn btn-primary";
    fechar.dataset.fechar = "";
    fechar.textContent = "Fechar";
    rodape.append(fechar);
    conteudoDaSinalizacao.replaceChildren(corpo, rodape);
    return true;
  };

  document.body.addEventListener("htmx:responseError", (evento) => {
    erroNoModal(evento, "Não foi possível falar com o servidor agora. Feche e tente de novo em instantes.");
  });
  document.body.addEventListener("htmx:beforeSwap", (evento) => {
    if (doModal(evento) && evento.detail.xhr.responseURL.includes("/login")) {
      evento.detail.shouldSwap = false;
      window.location.reload();
    }
  });

  // ---------------------------------------------------------------------------------------------
  // Painel oculto (#43): ao projetar, o professor pode ocultar o painel, e com ele os avisos. O botão
  // "Mostrar painel" ganha um ponto quando chega um aviso que o professor ainda não viu, sem dizer qual.
  // ---------------------------------------------------------------------------------------------
  let avisosVistos = new Set();
  const textosDosAvisos = () => [...document.querySelectorAll("#painel .aviso")]
    .filter((aviso) => !aviso.hidden && aviso.textContent.trim())
    .map((aviso) => aviso.textContent.trim());

  const conferirAvisos = () => {
    const oculto = tela.classList.contains("panel-hidden");
    const textos = textosDosAvisos();
    if (!oculto) avisosVistos = new Set(textos);
    const novo = oculto && textos.some((texto) => !avisosVistos.has(texto));
    alternador.textContent = oculto ? "Mostrar painel" : "Ocultar painel";
    alternador.setAttribute("aria-expanded", String(!oculto));
    alternador.classList.toggle("tem-aviso", novo);
    if (novo) {
      const marca = document.createElement("span");
      marca.className = "sr-only";
      marca.textContent = " (aviso novo)";
      alternador.append(marca);
    }
  };

  // ---------------------------------------------------------------------------------------------
  // Painel: o campo da pergunta acompanha o estado (pausada ou no limite) e o cronômetro corre.
  // ---------------------------------------------------------------------------------------------
  let base = 0;
  let desde = Date.now();
  let rodando = false;
  let previsto = 0;
  let limite = 0;
  // Quando o painel foi pedido por causa do limite; um pedido que falhou é repetido depois de um tempo.
  let limitePedidoEm = 0;
  const INTERVALO_DO_PEDIDO = 10000;

  // O painel lido por último. Outras trocas do HTMX (o modal de sinalizar, a pergunta, a resposta)
  // também disparam o afterSettle, e reler o mesmo painel faria o cronômetro voltar ao tempo que o
  // servidor mandou quando o painel chegou.
  let painelLido = null;

  const lerPainel = () => {
    const painel = document.getElementById("painel");
    if (painel === painelLido) {
      conferirAvisos();
      return;
    }
    painelLido = painel;
    const pode = painel.dataset.podePerguntar === "true";
    campo.disabled = !pode;
    enviar.disabled = !pode;
    const cronometro = painel.querySelector("[data-cronometro]");
    base = Number(cronometro.dataset.decorrido) || 0;
    rodando = cronometro.dataset.rodando === "true";
    previsto = Number(cronometro.dataset.previsto) || 0;
    limite = Number(cronometro.dataset.limite) || 0;
    desde = Date.now();
    conferirAvisos();
  };

  const formatar = (segundos) =>
    `${String(Math.floor(segundos / 60)).padStart(2, "0")}:${String(segundos % 60).padStart(2, "0")}`;

  // RF-19, RN-17: o cronômetro corre na tela. No tempo previsto, só mostra o aviso, sem interromper a
  // conversa. Aos 50 minutos, pede o painel de novo: o servidor confere o tempo e pausa a sessão. Se o
  // pedido falhar e o painel continuar rodando, pede outra vez a cada 10 segundos, até a pausa chegar.
  setInterval(() => {
    if (!rodando) return;
    const decorrido = base + Math.floor((Date.now() - desde) / 1000);
    const tempo = document.querySelector("#painel [data-tempo]");
    if (tempo) tempo.textContent = formatar(decorrido);
    const avisoDoPrevisto = document.querySelector("#painel [data-aviso-previsto]");
    if (avisoDoPrevisto?.hidden && previsto > 0 && decorrido >= previsto) {
      avisoDoPrevisto.hidden = false;
      conferirAvisos();
    }
    if (limite > 0 && decorrido >= limite && Date.now() - limitePedidoEm >= INTERVALO_DO_PEDIDO) {
      limitePedidoEm = Date.now();
      avisarMudanca();
    }
  }, 1000);

  // Com hx-swap="outerHTML", o evento nem sempre sai do painel novo; por isso escuta a página toda.
  document.body.addEventListener("htmx:afterSettle", lerPainel);
  lerPainel();

  // Um aviso novo na área do painel, vindo do servidor (recusa) ou daqui (falha), também marca o botão.
  new MutationObserver(conferirAvisos).observe(avisos, { childList: true, subtree: true });

  // A conversa acompanha a mensagem mais recente, inclusive enquanto a resposta chega.
  new MutationObserver(() => {
    const area = conversa();
    if (area) area.scrollTop = area.scrollHeight;
  }).observe(tela, { childList: true, subtree: true, characterData: true });
  conversa().scrollTop = conversa().scrollHeight;
})();
