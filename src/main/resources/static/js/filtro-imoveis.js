/**
 * Filtro de busca de imóveis.
 *
 * Mantém um estado unificado — `filterState` — com todas as seleções, e
 * notifica quem escuta a cada mudança. A filtragem acontece em memória sobre
 * o catálogo já carregado: enquanto ele couber numa requisição, ida ao
 * servidor a cada tecla seria desperdício.
 *
 * O campo de texto ainda passa por debounce, porque cada mudança redesenha a
 * lista de resultados.
 */
(function (global) {
  'use strict';

  var DEBOUNCE_MS = 300;

  // Rótulos e ícones dos tipos. As chaves são os valores do enum TipoImovel:
  // oferecer opção que o domínio não tem produziria filtro que nunca acha nada.
  var TIPOS = [
    { valor: 'APARTAMENTO', rotulo: 'Apartamentos', icone: 'M4 21V7l8-4 8 4v14M9 21v-6h6v6M8 11h.01M12 11h.01M16 11h.01' },
    { valor: 'CASA',        rotulo: 'Casas',        icone: 'M3 11l9-7 9 7M5 10v10h14V10M10 20v-6h4v6' },
    { valor: 'KITNET',      rotulo: 'Kitnet',       icone: 'M4 5h16v14H4zM4 12h16M10 5v7' },
    { valor: 'POUSADA',     rotulo: 'Pousadas',     icone: 'M3 20V9l9-5 9 5v11M3 20h18M8 20v-5h3v5M14 12h3v3h-3z' },
    { valor: 'CHALE',       rotulo: 'Chalés',       icone: 'M12 3L3 20h18L12 3zM12 11l-4 7h8l-4-7z' },
    { valor: 'LOFT',        rotulo: 'Lofts',        icone: 'M4 20V8l8-4 8 4v12M4 14h16M12 4v16' },
    { valor: 'OUTRO',       rotulo: 'Outros',       icone: 'M12 3l9 8h-3v10H6V11H3zM10 21v-6h4v6' }
  ];

  var FAIXAS_AREA = [
    { rotulo: 'até 50 m²',    min: null, max: 50 },
    { rotulo: '50 à 100 m²',  min: 50,   max: 100 },
    { rotulo: '100 à 150 m²', min: 100,  max: 150 },
    { rotulo: '150 à 200 m²', min: 150,  max: 200 }
  ];

  var STEPPERS = [
    { chave: 'quartos',   rotulo: 'Quartos' },
    { chave: 'banheiros', rotulo: 'Banheiros' },
    { chave: 'vagas',     rotulo: 'Vagas de garagem' }
  ];

  function estadoInicial() {
    return {
      localizacoes: [],   // termos de bairro ou rua, casados por OU
      tipos: [],          // valores de TipoImovel, casados por OU
      precoMin: null,
      precoMax: null,
      quartos: 0,         // zero significa "qualquer"
      banheiros: 0,
      vagas: 0,
      areaMin: null,
      areaMax: null
    };
  }

  // ------------------------------------------------------------------ util

  function svg(caminho, classe) {
    return '<svg class="' + classe + '" fill="none" stroke="currentColor" stroke-width="1.7" ' +
           'viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" ' +
           'stroke-linejoin="round" d="' + caminho + '"/></svg>';
  }

  function escapar(texto) {
    var d = document.createElement('div');
    d.textContent = texto == null ? '' : String(texto);
    return d.innerHTML;
  }

  function moeda(valor) {
    if (valor == null || isNaN(valor)) { return ''; }
    return Number(valor).toLocaleString('pt-BR', { minimumFractionDigits: 0 });
  }

  /** Lê um campo de moeda aceitando "1.200,50", "1200.5" ou vazio. */
  function lerNumero(texto) {
    if (texto == null) { return null; }
    var limpo = String(texto).replace(/[^\d,.-]/g, '').replace(/\./g, '').replace(',', '.');
    if (limpo === '') { return null; }
    var n = parseFloat(limpo);
    return isNaN(n) ? null : n;
  }

  function semAcento(texto) {
    return String(texto || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  }

  // ---------------------------------------------------------------- filtro

  /** Aplica `filterState` sobre a lista, casando grupos por E e itens por OU. */
  function filtrar(imoveis, e) {
    return (imoveis || []).filter(function (i) {
      if (e.localizacoes.length) {
        var alvo = semAcento((i.bairro || '') + ' ' + (i.logradouro || '') + ' ' + (i.cidade || ''));
        var bate = e.localizacoes.some(function (termo) {
          return alvo.indexOf(semAcento(termo)) !== -1;
        });
        if (!bate) { return false; }
      }

      if (e.tipos.length && e.tipos.indexOf(i.tipoImovel) === -1) { return false; }

      var preco = i.valorDiariaBase == null ? null : Number(i.valorDiariaBase);
      if (e.precoMin != null && (preco == null || preco < e.precoMin)) { return false; }
      if (e.precoMax != null && (preco == null || preco > e.precoMax)) { return false; }

      // Zero é "qualquer": não filtra. Acima disso, é mínimo.
      if (e.quartos > 0 && (i.numeroQuartos || 0) < e.quartos) { return false; }
      if (e.banheiros > 0 && (i.numeroBanheiros || 0) < e.banheiros) { return false; }
      if (e.vagas > 0 && (i.vagasGaragem || 0) < e.vagas) { return false; }

      var area = i.metragemQuadrada == null ? null : Number(i.metragemQuadrada);
      if (e.areaMin != null && (area == null || area < e.areaMin)) { return false; }
      if (e.areaMax != null && (area == null || area > e.areaMax)) { return false; }

      return true;
    });
  }

  // --------------------------------------------------------------- markup

  function secao(titulo, conteudo) {
    return '<section class="py-4 border-b border-slate-100 last:border-0">' +
             '<h4 class="text-[11px] font-bold uppercase tracking-wide text-slate-500 mb-3">' +
               titulo +
             '</h4>' + conteudo +
           '</section>';
  }

  function markup(limites) {
    var gradeTipos = TIPOS.map(function (t) {
      return '<button type="button" data-tipo="' + t.valor + '" ' +
             'class="tipo-card flex flex-col items-center justify-center gap-1 p-2 rounded-lg ' +
             'border border-slate-200 text-slate-600 hover:border-blue-400 transition text-center">' +
               svg(t.icone, 'w-5 h-5') +
               '<span class="text-[10px] font-semibold leading-tight">' + t.rotulo + '</span>' +
             '</button>';
    }).join('');

    var steppers = STEPPERS.map(function (s) {
      return '<div class="flex items-center justify-between py-1.5">' +
               '<span class="text-xs font-semibold text-slate-700">' + s.rotulo + '</span>' +
               '<div class="flex items-center gap-2">' +
                 '<button type="button" data-passo="-1" data-alvo="' + s.chave + '" ' +
                 'class="w-7 h-7 rounded-lg border border-slate-300 text-slate-600 font-bold ' +
                 'hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed">−</button>' +
                 '<span id="valor-' + s.chave + '" class="text-xs font-bold text-slate-800 ' +
                 'min-w-[56px] text-center">Qualquer</span>' +
                 '<button type="button" data-passo="1" data-alvo="' + s.chave + '" ' +
                 'class="w-7 h-7 rounded-lg border border-slate-300 text-slate-600 font-bold ' +
                 'hover:bg-slate-50">+</button>' +
               '</div>' +
             '</div>';
    }).join('');

    var pills = FAIXAS_AREA.map(function (f, idx) {
      return '<button type="button" data-faixa="' + idx + '" ' +
             'class="pill-area text-[10px] font-semibold px-2.5 py-1 rounded-full border ' +
             'border-slate-300 text-slate-600 hover:border-blue-400 transition">' +
               f.rotulo +
             '</button>';
    }).join('');

    return '' +
      '<div class="flex items-center justify-between pb-3 border-b border-slate-100">' +
        '<h3 class="text-sm font-bold text-slate-800">Filtrar imóveis</h3>' +
        '<button type="button" id="btnLimparFiltros" ' +
        'class="text-[11px] font-semibold text-blue-700 hover:underline">Limpar</button>' +
      '</div>' +

      secao('Localização',
        '<div class="relative">' +
          svg('M12 21s-7-6.2-7-11a7 7 0 1 1 14 0c0 4.8-7 11-7 11z M12 10a0.01 0.01 0 1 1 0-0.02',
              'w-4 h-4 text-slate-400 absolute left-2.5 top-2.5') +
          '<input type="text" id="filtroLocal" placeholder="Buscar bairro ou rua" ' +
          'class="w-full pl-8 pr-2 py-2 text-xs border border-slate-300 rounded-lg ' +
          'focus:outline-none focus:ring-2 focus:ring-blue-500">' +
        '</div>' +
        '<p class="text-[10px] text-slate-400 mt-1">Enter para adicionar</p>' +
        '<div id="tagsLocal" class="flex flex-wrap gap-1.5 mt-2"></div>') +

      secao('Tipo de imóvel',
        '<div id="gradeTipos" class="grid grid-cols-3 gap-1.5">' + gradeTipos + '</div>') +

      secao('Faixa de preço <span class="normal-case font-normal text-slate-400">(diária)</span>',
        '<div class="px-1 mb-3">' +
          '<div class="relative h-5">' +
            '<div class="absolute top-2 left-0 right-0 h-1 bg-slate-200 rounded"></div>' +
            '<div id="trilhaPreco" class="absolute top-2 h-1 bg-blue-500 rounded"></div>' +
            '<input type="range" id="sliderMin" class="slider-duplo" ' +
            'min="' + limites.precoMin + '" max="' + limites.precoMax + '" ' +
            'value="' + limites.precoMin + '" step="10">' +
            '<input type="range" id="sliderMax" class="slider-duplo" ' +
            'min="' + limites.precoMin + '" max="' + limites.precoMax + '" ' +
            'value="' + limites.precoMax + '" step="10">' +
          '</div>' +
        '</div>' +
        '<div class="grid grid-cols-2 gap-2">' +
          '<label class="block"><span class="text-[10px] text-slate-500">Mínimo</span>' +
            '<div class="relative">' +
              '<span class="absolute left-2 top-1.5 text-[10px] text-slate-400 font-semibold">R$</span>' +
              '<input type="text" id="precoMin" inputmode="numeric" placeholder="0" ' +
              'class="w-full pl-7 pr-2 py-1.5 text-xs border border-slate-300 rounded-lg ' +
              'focus:outline-none focus:ring-2 focus:ring-blue-500"></div></label>' +
          '<label class="block"><span class="text-[10px] text-slate-500">Máximo</span>' +
            '<div class="relative">' +
              '<span class="absolute left-2 top-1.5 text-[10px] text-slate-400 font-semibold">R$</span>' +
              '<input type="text" id="precoMax" inputmode="numeric" placeholder="sem limite" ' +
              'class="w-full pl-7 pr-2 py-1.5 text-xs border border-slate-300 rounded-lg ' +
              'focus:outline-none focus:ring-2 focus:ring-blue-500"></div></label>' +
        '</div>') +

      secao('Cômodos e vagas', steppers) +

      secao('Área',
        '<div class="grid grid-cols-2 gap-2">' +
          '<label class="block"><span class="text-[10px] text-slate-500">Mínimo</span>' +
            '<div class="relative">' +
              '<input type="text" id="areaMin" inputmode="numeric" placeholder="0" ' +
              'class="w-full pl-2 pr-8 py-1.5 text-xs border border-slate-300 rounded-lg ' +
              'focus:outline-none focus:ring-2 focus:ring-blue-500">' +
              '<span class="absolute right-2 top-1.5 text-[10px] text-slate-400 font-semibold">m²</span>' +
            '</div></label>' +
          '<label class="block"><span class="text-[10px] text-slate-500">Máximo</span>' +
            '<div class="relative">' +
              '<input type="text" id="areaMax" inputmode="numeric" placeholder="sem limite" ' +
              'class="w-full pl-2 pr-8 py-1.5 text-xs border border-slate-300 rounded-lg ' +
              'focus:outline-none focus:ring-2 focus:ring-blue-500">' +
              '<span class="absolute right-2 top-1.5 text-[10px] text-slate-400 font-semibold">m²</span>' +
            '</div></label>' +
        '</div>' +
        '<div class="flex flex-wrap gap-1.5 mt-2">' + pills + '</div>');
  }

  // ------------------------------------------------------------- instância

  /**
   * Monta o filtro dentro de `container`.
   * `aoMudar` recebe o filterState a cada alteração confirmada.
   * `limites` define o intervalo do slider, derivado do catálogo real.
   */
  function montar(container, aoMudar, limites) {
    limites = limites || { precoMin: 0, precoMax: 1000 };
    var estado = estadoInicial();
    var timer = null;

    container.innerHTML = markup(limites);

    var el = {
      local: container.querySelector('#filtroLocal'),
      tags: container.querySelector('#tagsLocal'),
      precoMin: container.querySelector('#precoMin'),
      precoMax: container.querySelector('#precoMax'),
      sliderMin: container.querySelector('#sliderMin'),
      sliderMax: container.querySelector('#sliderMax'),
      trilha: container.querySelector('#trilhaPreco'),
      areaMin: container.querySelector('#areaMin'),
      areaMax: container.querySelector('#areaMax')
    };

    function notificar() {
      aoMudar(Object.assign({}, estado));
    }

    function notificarComDebounce() {
      clearTimeout(timer);
      timer = setTimeout(notificar, DEBOUNCE_MS);
    }

    function pintarTrilha() {
      var faixa = limites.precoMax - limites.precoMin || 1;
      var a = (Number(el.sliderMin.value) - limites.precoMin) / faixa * 100;
      var b = (Number(el.sliderMax.value) - limites.precoMin) / faixa * 100;
      el.trilha.style.left = a + '%';
      el.trilha.style.width = Math.max(0, b - a) + '%';
    }

    function desenharTags() {
      el.tags.innerHTML = estado.localizacoes.map(function (t, idx) {
        return '<span class="inline-flex items-center gap-1 bg-blue-50 text-blue-800 ' +
               'text-[10px] font-semibold px-2 py-1 rounded-full border border-blue-200">' +
                 'Em ' + escapar(t) +
                 '<button type="button" data-remover="' + idx + '" ' +
                 'class="text-blue-500 hover:text-blue-900 font-bold leading-none" ' +
                 'aria-label="Remover ' + escapar(t) + '">×</button>' +
               '</span>';
      }).join('');
    }

    function desenharSteppers() {
      STEPPERS.forEach(function (s) {
        var alvo = container.querySelector('#valor-' + s.chave);
        alvo.innerText = estado[s.chave] === 0 ? 'Qualquer' : estado[s.chave] + '+';
        var menos = container.querySelector('[data-passo="-1"][data-alvo="' + s.chave + '"]');
        menos.disabled = estado[s.chave] === 0;
      });
    }

    function desenharTipos() {
      container.querySelectorAll('.tipo-card').forEach(function (b) {
        var ativo = estado.tipos.indexOf(b.dataset.tipo) !== -1;
        b.classList.toggle('border-blue-500', ativo);
        b.classList.toggle('bg-blue-50', ativo);
        b.classList.toggle('text-blue-800', ativo);
        b.classList.toggle('border-slate-200', !ativo);
      });
    }

    // --- localização --------------------------------------------------
    el.local.addEventListener('keydown', function (ev) {
      if (ev.key !== 'Enter' && ev.key !== ',') { return; }
      ev.preventDefault();
      var termo = el.local.value.trim().replace(/,$/, '');
      if (termo && estado.localizacoes.indexOf(termo) === -1) {
        estado.localizacoes.push(termo);
        el.local.value = '';
        desenharTags();
        notificar();
      }
    });

    // Digitar sem confirmar também filtra, com debounce: o termo em edição
    // entra como se fosse uma tag temporária.
    el.local.addEventListener('input', notificarComDebounce);

    el.tags.addEventListener('click', function (ev) {
      var botao = ev.target.closest('[data-remover]');
      if (!botao) { return; }
      estado.localizacoes.splice(Number(botao.dataset.remover), 1);
      desenharTags();
      notificar();
    });

    // --- tipos --------------------------------------------------------
    container.querySelector('#gradeTipos').addEventListener('click', function (ev) {
      var botao = ev.target.closest('.tipo-card');
      if (!botao) { return; }
      var pos = estado.tipos.indexOf(botao.dataset.tipo);
      if (pos === -1) { estado.tipos.push(botao.dataset.tipo); }
      else { estado.tipos.splice(pos, 1); }
      desenharTipos();
      notificar();
    });

    // --- preço --------------------------------------------------------
    function sincronizarDosSliders() {
      var a = Number(el.sliderMin.value);
      var b = Number(el.sliderMax.value);
      if (a > b) { var t = a; a = b; b = t; el.sliderMin.value = a; el.sliderMax.value = b; }
      estado.precoMin = a > limites.precoMin ? a : null;
      estado.precoMax = b < limites.precoMax ? b : null;
      el.precoMin.value = estado.precoMin == null ? '' : moeda(a);
      el.precoMax.value = estado.precoMax == null ? '' : moeda(b);
      pintarTrilha();
      notificarComDebounce();
    }
    el.sliderMin.addEventListener('input', sincronizarDosSliders);
    el.sliderMax.addEventListener('input', sincronizarDosSliders);

    function sincronizarDosCampos() {
      estado.precoMin = lerNumero(el.precoMin.value);
      estado.precoMax = lerNumero(el.precoMax.value);
      el.sliderMin.value = estado.precoMin == null ? limites.precoMin : estado.precoMin;
      el.sliderMax.value = estado.precoMax == null ? limites.precoMax : estado.precoMax;
      pintarTrilha();
      notificarComDebounce();
    }
    el.precoMin.addEventListener('input', sincronizarDosCampos);
    el.precoMax.addEventListener('input', sincronizarDosCampos);

    // --- steppers -----------------------------------------------------
    container.addEventListener('click', function (ev) {
      var botao = ev.target.closest('[data-passo]');
      if (!botao) { return; }
      var chave = botao.dataset.alvo;
      var novo = estado[chave] + Number(botao.dataset.passo);
      estado[chave] = Math.max(0, Math.min(10, novo));
      desenharSteppers();
      notificar();
    });

    // --- área ---------------------------------------------------------
    function sincronizarArea() {
      estado.areaMin = lerNumero(el.areaMin.value);
      estado.areaMax = lerNumero(el.areaMax.value);
      notificarComDebounce();
    }
    el.areaMin.addEventListener('input', sincronizarArea);
    el.areaMax.addEventListener('input', sincronizarArea);

    container.addEventListener('click', function (ev) {
      var pill = ev.target.closest('[data-faixa]');
      if (!pill) { return; }
      var f = FAIXAS_AREA[Number(pill.dataset.faixa)];
      estado.areaMin = f.min;
      estado.areaMax = f.max;
      el.areaMin.value = f.min == null ? '' : f.min;
      el.areaMax.value = f.max == null ? '' : f.max;
      container.querySelectorAll('.pill-area').forEach(function (p) {
        var ativo = p === pill;
        p.classList.toggle('border-blue-500', ativo);
        p.classList.toggle('bg-blue-50', ativo);
        p.classList.toggle('text-blue-800', ativo);
      });
      notificar();
    });

    // --- limpar -------------------------------------------------------
    container.querySelector('#btnLimparFiltros').addEventListener('click', function () {
      estado = estadoInicial();
      el.local.value = '';
      el.precoMin.value = '';
      el.precoMax.value = '';
      el.areaMin.value = '';
      el.areaMax.value = '';
      el.sliderMin.value = limites.precoMin;
      el.sliderMax.value = limites.precoMax;
      container.querySelectorAll('.pill-area').forEach(function (p) {
        p.classList.remove('border-blue-500', 'bg-blue-50', 'text-blue-800');
      });
      desenharTags();
      desenharTipos();
      desenharSteppers();
      pintarTrilha();
      notificar();
    });

    desenharTags();
    desenharTipos();
    desenharSteppers();
    pintarTrilha();

    return {
      estado: function () { return Object.assign({}, estado); },
      /** Termo ainda não confirmado conta como critério, para o filtro reagir enquanto se digita. */
      termoEmEdicao: function () { return el.local.value.trim(); }
    };
  }

  global.FiltroImoveis = {
    montar: montar,
    filtrar: filtrar,
    estadoInicial: estadoInicial,
    TIPOS: TIPOS
  };
})(window);
