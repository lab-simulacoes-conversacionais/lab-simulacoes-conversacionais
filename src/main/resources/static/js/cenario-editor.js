// Editor de cenário (RF-06), como no protótipo: uma etapa por vez, habilidades da BNCC vinculadas a
// partir da busca e duração mostrada ao lado do controle. A busca e a lista "Pronto para curadoria?"
// vêm do servidor pelo HTMX. Sem JavaScript, as quatro etapas aparecem juntas e o formulário funciona.
document.querySelectorAll("form[data-editor-cenario]").forEach((formulario) => {
  const etapas = [...formulario.querySelectorAll("[data-etapa]")];
  const botoesDasEtapas = [...formulario.querySelectorAll("[data-etapas] [data-ir-para]")];
  const navegacao = formulario.querySelector("[data-navegacao]");
  const anterior = navegacao.querySelector("[data-anterior]");
  const proxima = navegacao.querySelector("[data-proxima]");
  const ultima = navegacao.querySelector("[data-ultima]");
  const lista = formulario.querySelector("[data-habilidades]");
  const semHabilidade = formulario.querySelector("[data-sem-habilidade]");
  const modelo = formulario.querySelector("template[data-modelo-habilidade]");
  const titulo = document.querySelector("[data-titulo-do-cenario]");
  let atual = 1;

  const mostrar = (numero, campo, focar = true) => {
    atual = Math.min(Math.max(numero, 1), etapas.length);
    etapas.forEach((etapa) => { etapa.hidden = Number(etapa.dataset.etapa) !== atual; });
    botoesDasEtapas.forEach((botao) => {
      const ativa = Number(botao.dataset.irPara) === atual;
      botao.classList.toggle("active", ativa);
      botao.setAttribute("aria-current", ativa ? "step" : "false");
    });
    anterior.disabled = atual === 1;
    proxima.hidden = atual === etapas.length;
    ultima.hidden = atual !== etapas.length;
    if (!focar) return;
    // Leva o foco ao campo pedido ou ao título da etapa, para o leitor de tela acompanhar a troca.
    const alvo = campo && document.getElementById(campo);
    (alvo || etapas[atual - 1].querySelector("h2")).focus();
  };

  // Com JavaScript, mostra a navegação entre as etapas e começa pela primeira, sem mover o foco.
  formulario.querySelector("[data-etapas]").hidden = false;
  navegacao.hidden = false;
  etapas.forEach((etapa) => etapa.querySelector("h2").setAttribute("tabindex", "-1"));
  mostrar(1, null, false);
  anterior.addEventListener("click", () => mostrar(atual - 1));
  proxima.addEventListener("click", () => mostrar(atual + 1));

  // Botões das etapas e itens de "Pronto para curadoria?", que é trocado pelo HTMX: por isso a delegação.
  formulario.addEventListener("click", (evento) => {
    const link = evento.target.closest("[data-ir-para]");
    if (link) {
      evento.preventDefault();
      const campo = link.getAttribute("href")?.slice(1);
      mostrar(Number(link.dataset.irPara), campo);
      return;
    }
    const vincular = evento.target.closest("[data-vincular]");
    if (vincular) {
      adicionar(vincular.dataset.vincular, vincular.dataset.descricao);
      vincular.textContent = "Vinculada";
      vincular.disabled = true;
      return;
    }
    const remover = evento.target.closest("[data-remover]");
    if (remover) {
      remover.closest("li").remove();
      alterouHabilidades();
    }
  });

  // O navegador recusa o envio com o título vazio; leva o autor até a etapa do campo.
  formulario.addEventListener("invalid", (evento) => {
    const etapa = evento.target.closest("[data-etapa]");
    if (etapa && etapa.hidden) mostrar(Number(etapa.dataset.etapa));
  }, true);

  // Enter na busca da BNCC busca, em vez de enviar o formulário.
  formulario.querySelector("#bncc-busca").addEventListener("keydown", (evento) => {
    if (evento.key === "Enter") evento.preventDefault();
  });

  const adicionar = (codigo, descricao) => {
    if (lista.querySelector(`[data-habilidade="${CSS.escape(codigo)}"]`)) return;
    const item = modelo.content.firstElementChild.cloneNode(true);
    item.dataset.habilidade = codigo;
    item.querySelector("input").value = codigo;
    item.querySelector("strong").textContent = codigo;
    item.querySelector("span").textContent = descricao;
    item.querySelector("[data-remover]").setAttribute("aria-label", `Remover ${codigo}`);
    lista.append(item);
    alterouHabilidades();
  };

  // Campo oculto não dispara "change": avisa o formulário, para o HTMX atualizar o que falta.
  const alterouHabilidades = () => {
    semHabilidade.hidden = lista.children.length > 0;
    formulario.dispatchEvent(new Event("change", { bubbles: true }));
  };

  const duracao = formulario.querySelector("#duracaoPrevistaMin");
  const saida = formulario.querySelector("[data-duracao]");
  duracao.addEventListener("input", () => { saida.textContent = duracao.value; });

  formulario.querySelector("#titulo").addEventListener("input", (evento) => {
    titulo.textContent = evento.target.value.trim() || titulo.dataset.vazio;
  });
});
