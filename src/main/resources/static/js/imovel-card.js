/**
 * Card de exibição de imóvel.
 *
 * Recebe um ImovelResponse e devolve o HTML do card. As "props" são os campos
 * do DTO; nada aqui inventa dado que a API não tenha enviado.
 */
(function (global) {
  'use strict';

  /**
   * Devolve só o nome da via, sem o número da residência.
   *
   * A API já envia `logradouro` separado de `numero`, então normalmente não há
   * nada a remover. Esta função existe para o caso de alguém ter digitado o
   * número dentro do campo da rua, algo comum em cadastro manual.
   *
   * Remove apenas um número em posição de sufixo — precedido de vírgula, de
   * hífen ou de "nº" —, nunca um número no meio do nome. É o que preserva
   * "Rua 7 de Setembro" e "Avenida 9 de Julho", que um regex ingênuo
   * mutilaria.
   */
  function apenasLogradouro(via) {
    if (!via) { return ''; }
    return String(via)
      // Após vírgula: "Rua das Gaivotas, 120".
      .replace(/\s*,\s*(n[ºo°.]?\s*)?\d+\s*[A-Za-z]?\s*$/i, '')
      // Após hífen, mas só com espaços em volta. Sem eles o hífen pertence ao
      // nome da via, como em "Rodovia SC-401".
      .replace(/\s+[-–]\s+(n[ºo°.]?\s*)?\d+\s*[A-Za-z]?\s*$/i, '')
      // Marcado explicitamente com "nº".
      .replace(/\s+n[ºo°.]\s*\d+\s*$/i, '')
      .trim();
  }

  function icone(caminho) {
    return '<svg class="w-3.5 h-3.5 text-slate-400 shrink-0" fill="none" stroke="currentColor" ' +
           'stroke-width="1.8" viewBox="0 0 24 24" aria-hidden="true">' + caminho + '</svg>';
  }

  var ICONES = {
    area: icone('<path stroke-linecap="round" stroke-linejoin="round" d="M4 8V4h4M16 4h4v4M20 16v4h-4M8 20H4v-4"/>'),
    quartos: icone('<path stroke-linecap="round" stroke-linejoin="round" d="M3 18v-6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v6M3 18h18M3 18v2M21 18v2M7 10V8a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v2"/>'),
    banheiros: icone('<path stroke-linecap="round" stroke-linejoin="round" d="M4 12h16v3a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4v-3ZM7 12V6a2 2 0 0 1 4 0"/>'),
    garagem: icone('<path stroke-linecap="round" stroke-linejoin="round" d="M5 17h14M6 17v-4l1.5-4h9L18 13v4M7.5 13h9M8 20v-3M16 20v-3"/>')
  };

  function atributo(chave, valor, descricao) {
    return '<div class="flex items-center gap-1.5" title="' + descricao + '">' +
             ICONES[chave] +
             '<span class="text-xs font-semibold text-slate-700">' + valor + '</span>' +
           '</div>';
  }

  function escapar(texto) {
    var div = document.createElement('div');
    div.textContent = texto == null ? '' : String(texto);
    return div.innerHTML;
  }

  /** Monta o card. `imovel` é um ImovelResponse vindo de /api/imoveis. */
  function card(imovel) {
    var via = apenasLogradouro(imovel.logradouro);
    var localidade = [imovel.bairro, imovel.cidade].filter(Boolean).join(' • ');

    var atributos = '';
    if (imovel.metragemQuadrada != null) {
      atributos += atributo('area', escapar(imovel.metragemQuadrada) + ' m²', 'Área útil');
    }
    if (imovel.numeroQuartos != null) {
      atributos += atributo('quartos', escapar(imovel.numeroQuartos), 'Quartos');
    }
    if (imovel.numeroBanheiros != null) {
      atributos += atributo('banheiros', escapar(imovel.numeroBanheiros), 'Banheiros');
    }
    // Renderização condicional: nulo e zero não entram no DOM.
    if (imovel.vagasGaragem != null && imovel.vagasGaragem > 0) {
      atributos += atributo('garagem', escapar(imovel.vagasGaragem), 'Vagas de garagem');
    }

    return '' +
      '<article class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">' +
        '<div class="bg-slate-200 h-32 flex items-center justify-center text-slate-400 font-bold text-sm px-3 text-center">' +
          escapar(imovel.titulo) +
        '</div>' +
        '<div class="p-4 flex-1 flex flex-col">' +
          '<div class="flex items-start justify-between gap-2">' +
            '<span class="text-[10px] uppercase tracking-wide font-bold text-blue-800 bg-blue-50 px-2 py-0.5 rounded">' +
              escapar(imovel.tipoImovelRotulo || '—') +
            '</span>' +
            (imovel.ativo
              ? '<span class="text-[10px] bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded font-semibold">Disponível</span>'
              : '<span class="text-[10px] bg-slate-200 text-slate-600 px-2 py-0.5 rounded font-semibold">Inativo</span>') +
          '</div>' +

          '<h3 class="font-bold text-slate-800 mt-2 leading-snug">' + escapar(via || imovel.titulo) + '</h3>' +
          '<p class="text-[11px] text-slate-500 mt-0.5">' + escapar(localidade) + '</p>' +

          '<div class="flex flex-wrap items-center gap-x-4 gap-y-2 mt-3 pt-3 border-t border-slate-100">' +
            atributos +
          '</div>' +
        '</div>' +
      '</article>';
  }

  global.ImovelCard = { card: card, apenasLogradouro: apenasLogradouro };
})(window);
