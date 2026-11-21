// Tela de login. Tudo aqui é enfeite ou ajuda; sem este script, o formulário funciona igual.
// - Formulário: ✓ no e-mail válido, mostrar/ocultar senha (o olho aparece pelo CSS quando
//   há algo digitado), aviso de Caps Lock e o botão em "Entrando…" ao enviar.
// - Ilustração: as imagens se revezam a cada 8,5 s, e as bolinhas embaixo trocam na hora (o ciclo recomeça
//   a contar a partir do clique). As formas do fundo do formulário acompanham o mouse em profundidades diferentes.
// Com o pedido de movimento reduzido do sistema, nada se mexe sozinho; as bolinhas continuam funcionando.
(() => {
  const pagina = document.querySelector(".login-page");
  if (!pagina) return;
  const comMovimento = window.matchMedia("(prefers-reduced-motion: no-preference)").matches;
  const comMouse = window.matchMedia("(pointer: fine)").matches;

  /* ---------- Formulário ---------- */

  const email = document.getElementById("email");
  const campoEmail = document.querySelector("[data-campo-email]");
  if (email && campoEmail) {
    const conferir = () => campoEmail.classList.toggle("valido", email.value.trim() !== "" && email.checkValidity());
    email.addEventListener("input", conferir);
    conferir();
  }

  const senha = document.getElementById("senha");
  const olho = document.querySelector("[data-mostrar-senha]");
  const mostrarSenha = (mostrar) => {
    senha.type = mostrar ? "text" : "password";
    olho.setAttribute("aria-pressed", String(mostrar));
    olho.setAttribute("aria-label", mostrar ? "Ocultar senha" : "Mostrar senha");
  };
  olho?.addEventListener("click", () => {
    mostrarSenha(senha.type === "password");
    senha.focus();
  });
  // Apagou tudo: o olho some e a senha volta a ficar escondida.
  senha?.addEventListener("input", () => { if (olho && senha.value === "") mostrarSenha(false); });

  const caps = document.querySelector("[data-caps]");
  if (senha && caps) {
    const conferirCaps = (evento) => { if (evento.getModifierState) caps.hidden = !evento.getModifierState("CapsLock"); };
    senha.addEventListener("keydown", conferirCaps);
    senha.addEventListener("keyup", conferirCaps);
    senha.addEventListener("blur", () => { caps.hidden = true; });
  }

  const formulario = document.querySelector("[data-login-form]");
  const botao = formulario?.querySelector("[type=submit]");
  const rotulo = botao?.querySelector("[data-rotulo]");
  formulario?.addEventListener("submit", () => {
    botao.classList.add("carregando");
    botao.setAttribute("aria-busy", "true");
    rotulo.textContent = "Entrando…";
  });
  // Ao voltar para esta página pelo histórico, o botão volta ao normal.
  window.addEventListener("pageshow", (evento) => {
    if (!evento.persisted || !botao) return;
    botao.classList.remove("carregando");
    botao.removeAttribute("aria-busy");
    rotulo.textContent = "Entrar";
  });

  /* ---------- Ilustrações: revezamento automático e pelas bolinhas ---------- */

  const ilustracoes = [...document.querySelectorAll("[data-ilustracao]")];
  const grupoBolinhas = document.querySelector("[data-bolinhas]");
  if (ilustracoes.length > 1 && grupoBolinhas) {
    const bolinhas = [...grupoBolinhas.querySelectorAll("button")];
    let atual = 0, relogio = 0;
    const mostrar = (indice) => {
      atual = (indice + ilustracoes.length) % ilustracoes.length;
      ilustracoes.forEach((img, i) => img.classList.toggle("ativa", i === atual));
      bolinhas.forEach((b, i) => b.setAttribute("aria-pressed", String(i === atual)));
    };
    const programar = () => {
      clearInterval(relogio);
      if (comMovimento) relogio = setInterval(() => mostrar(atual + 1), 8500);
    };
    bolinhas.forEach((bolinha, i) => bolinha.addEventListener("click", () => { mostrar(i); programar(); }));
    grupoBolinhas.hidden = false;
    programar();
  }

  /* ---------- Profundidade: as formas do fundo acompanham o mouse ---------- */

  if (comMovimento && comMouse) {
    let quadro = 0, mx = 0, my = 0;
    const aplicar = () => {
      quadro = 0;
      pagina.style.setProperty("--mx", mx.toFixed(3));
      pagina.style.setProperty("--my", my.toFixed(3));
    };
    pagina.addEventListener("pointermove", (evento) => {
      mx = (evento.clientX / window.innerWidth) * 2 - 1;
      my = (evento.clientY / window.innerHeight) * 2 - 1;
      if (!quadro) quadro = requestAnimationFrame(aplicar);
    });
    document.documentElement.addEventListener("pointerleave", () => { mx = 0; my = 0; aplicar(); });
  }
})();
