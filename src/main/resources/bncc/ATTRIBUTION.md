## Créditos dos dados da BNCC

O catálogo de habilidades usa dados do dataset "bncc.dev: dados abertos e verificados da Base Nacional Comum Curricular", publicado pela Profy (Profy Tecnologias Educacionais Ltda) sob a licença CC BY 4.0. Os dados originais vêm das planilhas oficiais do MEC.

- Fonte: https://github.com/bncc-dev/bncc-dados (site: https://bncc.dev)
- Versão usada: dados-2026.08, publicada em 2026-10-15 (commit d70016185a3cb8b1f146f50592a2a6502f58edde)
- DOI da versão: 10.5281/zenodo.23403987
- Licença dos dados (CC BY 4.0): https://github.com/bncc-dev/bncc-dados/blob/main/dados/LICENSE.md
- Arquivos usados: dados/bncc-2018/ensino-fundamental.json e dados/bncc-2018/estrutura.json, copiados sem alteração para src/main/resources/bncc/
- Modificações: o sistema importa apenas as habilidades vigentes e somente os campos código, texto, componente e anos. O identificador do componente é trocado pelo nome (ex.: ef-comp-hi vira História) e a lista de anos é gravada como texto (ex.: "6,7"). O dataset original não foi alterado.
