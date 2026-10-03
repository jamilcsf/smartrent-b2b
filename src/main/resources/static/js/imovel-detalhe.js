/**
 * Página de detalhe do imóvel como componente.
 *
 * É usada em dois lugares com o MESMO código: na página pública do anúncio
 * (imovel.html) e no "Preview do anúncio" do cadastro, alimentada pelos dados
 * ainda não salvos do formulário. É o que garante que o preview mostre
 * exatamente o que o catálogo vai mostrar.
 *
 * Recebe um objeto no formato do ImovelResponse da API:
 *   { id, titulo, descricao, tipoImovelRotulo, logradouro, bairro, cidade,
 *     metragemQuadrada, numeroQuartos, numeroBanheiros, vagasGaragem,
 *     capacidadeHospedes, valorDiariaBase, comodidades, ativo,
 *     midias: [{tipo, url, miniaturaUrl, capa, duracaoSegundos}],
 *     whatsappLink }   // whatsappLink só vem para quem está logado
 */
(function (global) {
  'use strict';

  var PANNELLUM_CSS = 'https://cdnjs.cloudflare.com/ajax/libs/pannellum/2.5.6/pannellum.css';
  var PANNELLUM_JS = 'https://cdnjs.cloudflare.com/ajax/libs/pannellum/2.5.6/pannellum.js';
  var pannellumPronto = null;

  function escapar(texto) {
    var d = document.createElement('div');
    d.textContent = texto == null ? '' : String(texto);
    // innerHTML não escapa aspas; sem isso o texto fecharia um atributo ("...") e injetaria outro.
    return d.innerHTML.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function moeda(valor) {
    return 'R$ ' + Number(valor || 0).toLocaleString('pt-BR',
      { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /** O viewer 360 só é baixado quando alguém abre uma foto 360. */
  function carregarPannellum() {
    if (global.pannellum) { return Promise.resolve(); }
    if (pannellumPronto) { return pannellumPronto; }
    pannellumPronto = new Promise(function (resolve, reject) {
      var css = document.createElement('link');
      css.rel = 'stylesheet';
      css.href = PANNELLUM_CSS;
      document.head.appendChild(css);
      var js = document.createElement('script');
      js.src = PANNELLUM_JS;
      js.onload = resolve;
      js.onerror = function () { pannellumPronto = null; reject(new Error('viewer 360 indisponível')); };
      document.head.appendChild(js);
    });
    return pannellumPronto;
  }

  function atributo(icone, valor, rotulo) {
    return '<div class="flex items-center gap-2">' +
             '<i data-lucide="' + icone + '" class="w-4 h-4 text-slate-400"></i>' +
             '<div><div class="text-sm font-bold text-slate-800 leading-none">' + escapar(valor) + '</div>' +
             '<div class="text-[10px] text-slate-500 mt-0.5">' + rotulo + '</div></div>' +
           '</div>';
  }

  function rotuloTipo(imovel) {
    return imovel.tipoImovelRotulo || '—';
  }

  /** Miniatura na faixa da galeria: foto, 360 (selo) ou vídeo (ícone). */
  function miniatura(m, indice) {
    var base = 'galeria-item shrink-0 w-20 h-16 rounded-lg overflow-hidden border-2 border-transparent ' +
               'bg-slate-200 relative focus:outline-none focus:ring-2 focus:ring-blue-500';
    var conteudo;
    if (m.tipo === 'VIDEO') {
      conteudo = '<div class="w-full h-full flex items-center justify-center bg-slate-800 text-white">' +
                   '<i data-lucide="play" class="w-5 h-5"></i></div>';
    } else {
      conteudo = '<img src="' + escapar(m.miniaturaUrl || m.url) + '" alt="" class="w-full h-full object-cover">' +
        (m.tipo === 'FOTO_360'
          ? '<span class="absolute bottom-0.5 left-0.5 text-[9px] font-bold bg-black/70 text-white px-1 rounded">360°</span>'
          : '');
    }
    return '<button type="button" class="' + base + '" data-indice="' + indice + '" ' +
           'aria-label="Mídia ' + (indice + 1) + '">' + conteudo + '</button>';
  }

  function galeria(midias) {
    if (!midias.length) {
      return '<div class="h-64 sm:h-80 rounded-2xl bg-gradient-to-br from-slate-200 to-slate-300 flex flex-col ' +
             'items-center justify-center text-slate-500 border border-slate-200">' +
             '<i data-lucide="image-off" class="w-8 h-8 mb-2 text-slate-400"></i>' +
             '<span class="text-xs font-semibold">Fotos ainda não cadastradas</span></div>';
    }
    return '<div id="galeriaPrincipal" class="h-64 sm:h-[26rem] rounded-2xl bg-slate-900 border border-slate-200 ' +
           'overflow-hidden relative"></div>' +
           '<div id="galeriaFaixa" class="flex gap-2 overflow-x-auto mt-3 pb-1">' +
             midias.map(miniatura).join('') +
           '</div>';
  }

  function blocoContato(imovel, opcoes) {
    // O link só chega nesta função se o servidor o enviou: visitantes anônimos não o recebem.
    if (imovel.whatsappLink && /^https:\/\//.test(imovel.whatsappLink)) {
      return '<a href="' + escapar(imovel.whatsappLink) + '" target="_blank" rel="noopener noreferrer" ' +
             'id="btnWhatsapp" class="mt-3 flex items-center justify-center gap-2 w-full bg-emerald-600 ' +
             'hover:bg-emerald-700 text-white font-bold py-2.5 rounded-xl text-sm transition">' +
             '<i data-lucide="message-circle" class="w-4 h-4"></i> Falar com o anfitrião no WhatsApp</a>' +
             (opcoes.preview
               ? '<p class="text-[10px] text-slate-400 text-center mt-1.5">Este botão aparece só para clientes logados.</p>'
               : '');
    }
    if (!opcoes.logado && !opcoes.preview) {
      var destino = '/login.html?redirectTo=' + encodeURIComponent(location.pathname + location.search);
      return '<a href="' + destino + '" class="mt-3 block text-center w-full border border-blue-600 text-blue-700 ' +
             'hover:bg-blue-50 font-bold py-2.5 rounded-xl text-sm transition">Entre para falar com o anfitrião</a>';
    }
    return '';
  }

  /** HTML completo do detalhe. Depois de inserir no DOM, chame {@link ativar}. */
  function montar(imovel, opcoes) {
    opcoes = opcoes || {};
    var midias = imovel.midias || [];
    var via = global.ImovelCard ? ImovelCard.apenasLogradouro(imovel.logradouro) : (imovel.logradouro || '');
    var local = [imovel.bairro, imovel.cidade].filter(Boolean).join(' • ');

    var attrs = '';
    if (imovel.metragemQuadrada != null && imovel.metragemQuadrada !== '') {
      attrs += atributo('scaling', imovel.metragemQuadrada + ' m²', 'Área útil');
    }
    if (imovel.numeroQuartos != null && imovel.numeroQuartos !== '') {
      attrs += atributo('bed-double', imovel.numeroQuartos, 'Quartos');
    }
    if (imovel.numeroBanheiros != null && imovel.numeroBanheiros !== '') {
      attrs += atributo('bath', imovel.numeroBanheiros, 'Banheiros');
    }
    if (imovel.vagasGaragem != null && imovel.vagasGaragem > 0) {
      attrs += atributo('car-front', imovel.vagasGaragem, 'Vagas de garagem');
    }
    if (imovel.capacidadeHospedes != null && imovel.capacidadeHospedes !== '') {
      attrs += atributo('users', imovel.capacidadeHospedes, 'Hóspedes');
    }

    var comodidades = imovel.comodidades || [];
    var blocoComodidades = comodidades.length === 0 ? '' :
      '<section class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">' +
        '<h3 class="text-sm font-bold text-slate-800 mb-4">Comodidades</h3>' +
        '<div class="grid grid-cols-2 sm:grid-cols-3 gap-3">' +
          comodidades.map(function (c) {
            return '<div class="flex items-center gap-2 text-xs text-slate-700">' +
                   '<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-600"></i>' + escapar(c) + '</div>';
          }).join('') +
        '</div></section>';

    var temPreco = imovel.valorDiariaBase != null && imovel.valorDiariaBase !== '';
    var botaoReserva = '';
    if (opcoes.preview) {
      botaoReserva = '<p class="mt-4 text-[11px] text-center text-slate-500 bg-slate-50 border border-slate-200 ' +
                     'rounded-lg p-2">Pré-visualização: as ações ficam ativas no anúncio publicado.</p>';
    } else if (opcoes.gestor) {
      botaoReserva = '<button type="button" id="btnReservar" class="w-full mt-4 bg-blue-600 hover:bg-blue-700 ' +
                     'text-white font-bold py-2.5 rounded-xl shadow-sm text-sm transition">Registrar reserva</button>';
    }

    return '' +
      '<section class="mb-6">' + galeria(midias) + '</section>' +
      '<div class="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-6 items-start">' +
        '<div class="space-y-5">' +
          '<section class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">' +
            '<div class="flex flex-wrap items-center gap-2 mb-3">' +
              '<span class="text-[10px] uppercase tracking-wide font-bold text-blue-800 bg-blue-50 px-2.5 py-1 rounded">' +
                escapar(rotuloTipo(imovel)) + '</span>' +
              '<span class="text-[10px] font-semibold px-2.5 py-1 rounded ' +
                (imovel.ativo === false ? 'bg-slate-200 text-slate-600' : 'bg-emerald-100 text-emerald-700') + '">' +
                (imovel.ativo === false ? 'Inativo' : 'Disponível') + '</span>' +
            '</div>' +
            '<h2 class="text-2xl font-bold text-slate-900 leading-tight">' + escapar(imovel.titulo || 'Título do anúncio') + '</h2>' +
            '<p class="flex items-center gap-1.5 text-sm text-slate-500 mt-1.5">' +
              '<i data-lucide="map-pin" class="w-4 h-4 text-slate-400"></i>' +
              '<span>' + escapar([via, local].filter(Boolean).join(', ')) + '</span></p>' +
            '<div class="flex flex-wrap items-center gap-x-6 gap-y-3 mt-5 pt-5 border-t border-slate-100">' + attrs + '</div>' +
          '</section>' +
          '<section class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">' +
            '<h3 class="text-sm font-bold text-slate-800 mb-2">Descrição do imóvel</h3>' +
            '<p class="text-sm text-slate-600 leading-relaxed whitespace-pre-line">' +
              escapar(imovel.descricao || 'Este imóvel ainda não possui descrição cadastrada.') + '</p>' +
          '</section>' +
          blocoComodidades +
        '</div>' +
        '<aside class="lg:sticky lg:top-24"><div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">' +
          '<div class="flex items-baseline gap-1.5">' +
            '<span class="text-2xl font-extrabold text-slate-900">' + (temPreco ? moeda(imovel.valorDiariaBase) : 'Preço a definir') + '</span>' +
            (temPreco ? '<span class="text-xs text-slate-400 font-medium">/ noite</span>' : '') + '</div>' +
          '<p class="text-[11px] text-slate-500 mt-1">Diária base • até ' + escapar(imovel.capacidadeHospedes || '?') + ' hóspedes</p>' +
          '<div class="grid grid-cols-2 gap-2 mt-4">' +
            '<label class="block"><span class="text-[10px] font-semibold text-slate-500">Check-in</span>' +
              '<input type="date" id="dataCheckin" class="w-full mt-1 p-2 border border-slate-300 rounded-lg text-xs ' +
              'focus:outline-none focus:ring-2 focus:ring-blue-500"></label>' +
            '<label class="block"><span class="text-[10px] font-semibold text-slate-500">Check-out</span>' +
              '<input type="date" id="dataCheckout" class="w-full mt-1 p-2 border border-slate-300 rounded-lg text-xs ' +
              'focus:outline-none focus:ring-2 focus:ring-blue-500"></label>' +
          '</div>' +
          '<div id="resumo" class="hidden mt-4 pt-4 border-t border-slate-100 space-y-1.5">' +
            '<div class="flex justify-between text-xs text-slate-600"><span id="resumoLinha"></span>' +
              '<span id="resumoSubtotal" class="font-semibold text-slate-800"></span></div>' +
            '<div class="flex justify-between text-sm font-bold text-slate-900 pt-1.5 border-t border-slate-100">' +
              '<span>Total</span><span id="resumoTotal"></span></div>' +
          '</div>' +
          '<p id="avisoSidebar" class="text-[10px] text-slate-400 text-center mt-2">Escolha as datas para ver o total.</p>' +
          botaoReserva +
          blocoContato(imovel, opcoes) +
        '</div></aside>' +
      '</div>';
  }

  /** Liga a galeria, o viewer 360 e o cálculo do total. `raiz` é o elemento onde o HTML foi inserido. */
  function ativar(raiz, imovel, opcoes) {
    opcoes = opcoes || {};
    var midias = imovel.midias || [];
    var principal = raiz.querySelector('#galeriaPrincipal');
    var viewer = null;

    function limparViewer() {
      if (viewer && viewer.destroy) { try { viewer.destroy(); } catch (e) { /* ignorado */ } }
      viewer = null;
    }

    function mostrar(indice) {
      var m = midias[indice];
      if (!m || !principal) { return; }
      limparViewer();
      raiz.querySelectorAll('.galeria-item').forEach(function (b) {
        b.classList.toggle('border-blue-600', Number(b.dataset.indice) === indice);
        b.classList.toggle('border-transparent', Number(b.dataset.indice) !== indice);
      });

      if (m.tipo === 'VIDEO') {
        principal.innerHTML = '<video controls preload="metadata" class="w-full h-full bg-black" src="' +
          escapar(m.url) + '"></video>';
      } else if (m.tipo === 'FOTO_360') {
        principal.innerHTML = '<div id="visor360" class="w-full h-full"></div>' +
          '<span class="absolute top-3 left-3 text-[10px] font-bold bg-black/70 text-white px-2 py-1 rounded z-10">' +
          '360° • arraste para olhar ao redor</span>';
        carregarPannellum().then(function () {
          viewer = global.pannellum.viewer('visor360', {
            type: 'equirectangular', panorama: m.url, autoLoad: true, showControls: true, compass: false
          });
        }).catch(function () {
          principal.innerHTML = '<img src="' + escapar(m.url) + '" alt="" class="w-full h-full object-contain">';
        });
      } else {
        principal.innerHTML = '<img src="' + escapar(m.url) + '" alt="' + escapar(imovel.titulo) +
          '" class="w-full h-full object-contain">';
      }
    }

    raiz.querySelectorAll('.galeria-item').forEach(function (b) {
      b.addEventListener('click', function () { mostrar(Number(b.dataset.indice)); });
    });
    if (midias.length) {
      var capa = midias.findIndex(function (m) { return m.capa; });
      mostrar(capa >= 0 ? capa : 0);
    }

    function recalcular() {
      var ini = raiz.querySelector('#dataCheckin').value;
      var fim = raiz.querySelector('#dataCheckout').value;
      var resumo = raiz.querySelector('#resumo');
      var aviso = raiz.querySelector('#avisoSidebar');
      var preco = Number(imovel.valorDiariaBase);
      if (!ini || !fim || isNaN(preco) || imovel.valorDiariaBase == null) { resumo.classList.add('hidden'); return; }
      var noites = Math.round((new Date(fim) - new Date(ini)) / 86400000);
      if (noites <= 0) {
        resumo.classList.add('hidden');
        aviso.innerText = 'O check-out deve ser posterior ao check-in.';
        aviso.className = 'text-[10px] text-rose-600 text-center mt-2 font-semibold';
        return;
      }
      raiz.querySelector('#resumoLinha').innerText = moeda(preco) + ' x ' + noites + (noites === 1 ? ' noite' : ' noites');
      raiz.querySelector('#resumoSubtotal').innerText = moeda(preco * noites);
      raiz.querySelector('#resumoTotal').innerText = moeda(preco * noites);
      resumo.classList.remove('hidden');
      aviso.innerText = 'Valor sem taxas adicionais.';
      aviso.className = 'text-[10px] text-slate-400 text-center mt-2';
    }
    raiz.querySelector('#dataCheckin').addEventListener('change', recalcular);
    raiz.querySelector('#dataCheckout').addEventListener('change', recalcular);

    var btn = raiz.querySelector('#btnReservar');
    if (btn && opcoes.aoRegistrarReserva) {
      btn.addEventListener('click', function () {
        opcoes.aoRegistrarReserva({
          checkin: raiz.querySelector('#dataCheckin').value,
          checkout: raiz.querySelector('#dataCheckout').value
        });
      });
    }

    if (global.lucide) { global.lucide.createIcons(); }
    return { destruir: limparViewer };
  }

  global.ImovelDetalhe = { montar: montar, ativar: ativar, escapar: escapar, moeda: moeda };
})(window);
