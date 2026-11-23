// Pedido de sugestão de cenário pela IA (RF-07), no diálogo do editor de cenário novo: abre e fecha como
// os modais do protótipo, vincula as habilidades achadas na busca da BNCC (que vem do servidor pelo HTMX)
// e avisa enquanto a IA gera, que leva alguns segundos. Sem JavaScript, o link "Sugerir com IA" abre a
// página com o diálogo aberto e o formulário funciona igual.
document.querySelectorAll("dialog[data-sugestao]").forEach((dialogo) => {
  const formulario = dialogo.querySelector("form[data-form-sugestao]");
  const lista = dialogo.querySelector("[data-habilidades-da-sugestao]");
  const semHabilidade = dialogo.querySelector("[data-sem-habilidade]");
  const modelo = dialogo.querySelector("template[data-modelo-habilidade]");
  const busca = dialogo.querySelector("#sugestao-bncc-busca");
  const gerar = dialogo.querySelector("[data-gerar-sugestao]");
  const gerando = dialogo.querySelector("[data-gerando]");

  const abrir = () => {
    if (dialogo.open) dialogo.close();
    dialogo.showModal();
    dialogo.querySelector("#sugestao-tema").focus();
  };

  document.querySelectorAll("[data-abrir-sugestao]").forEach((botao) => {
    botao.addEventListener("click", (evento) => {
      evento.preventDefault();
      abrir();
    });
  });

  dialogo.addEventListener("click", (evento) => {
    // Clicar fora da caixa fecha, como no protótipo.
    if (evento.target === dialogo) {
      dialogo.close();
      return;
    }
    if (evento.target.closest("[data-fechar]")) {
      evento.preventDefault();
      dialogo.close();
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

  const alterouHabilidades = () => {
    semHabilidade.hidden = lista.children.length > 0;
    semHabilidade.classList.remove("field-error");
  };

  // Enter na busca da BNCC busca, em vez de pedir a sugestão.
  busca.addEventListener("keydown", (evento) => {
    if (evento.key === "Enter") evento.preventDefault();
  });

  formulario.addEventListener("submit", (evento) => {
    // O servidor recusa sem habilidade; aqui o aviso chega antes de gastar a ida até lá.
    if (lista.children.length === 0) {
      evento.preventDefault();
      semHabilidade.hidden = false;
      semHabilidade.classList.add("field-error");
      busca.focus();
      return;
    }
    gerar.disabled = true;
    gerando.hidden = false;
    formulario.setAttribute("aria-busy", "true");
  });

  // Voltando pelo histórico, o navegador pode restaurar a página como estava, com o botão travado.
  window.addEventListener("pageshow", (evento) => {
    if (!evento.persisted) return;
    gerar.disabled = false;
    gerando.hidden = true;
    formulario.removeAttribute("aria-busy");
  });

  // Volta aberto quando o servidor recusou o pedido ou o link foi aberto sem JavaScript.
  if (dialogo.open) abrir();
});
