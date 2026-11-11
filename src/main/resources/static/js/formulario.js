// Formulários com resumo ao lado (protótipo): o resumo acompanha o que é digitado, e o botão de envio
// só fica ativo com os campos obrigatórios preenchidos. Sem JavaScript, o formulário funciona igual e a
// validação fica com o navegador e o servidor.
document.querySelectorAll("form[data-resumo]").forEach((formulario) => {
  const enviar = formulario.querySelector("[data-enviar]");
  const ajuda = formulario.querySelector("[data-ajuda]");
  const grupo = formulario.querySelector("[data-exige-um]");

  const valoresDe = (nome) =>
    [...formulario.elements]
      .filter((campo) => campo.name === nome)
      .filter((campo) => (campo.type === "checkbox" || campo.type === "radio" ? campo.checked : campo.value.trim()))
      .map((campo) => campo.dataset.rotulo || campo.value.trim());

  const atualizar = () => {
    formulario.querySelectorAll("[data-resumo-de]").forEach((resumo) => {
      const nomes = resumo.dataset.resumoDe.split(" ");
      const valores = nomes.flatMap(valoresDe);
      const completo = "todos" in resumo.dataset ? nomes.every((nome) => valoresDe(nome).length) : valores.length;
      resumo.textContent = completo ? valores.join(resumo.dataset.separador || ", ") : resumo.dataset.vazio;
    });
    const grupoPreenchido = !grupo || formulario.querySelector(`input[name="${grupo.dataset.exigeUm}"]:checked`);
    const valido = formulario.checkValidity() && Boolean(grupoPreenchido);
    if (enviar) enviar.disabled = !valido;
    if (ajuda) ajuda.hidden = valido;
  };

  formulario.addEventListener("input", atualizar);
  formulario.addEventListener("change", atualizar);
  atualizar();
});
