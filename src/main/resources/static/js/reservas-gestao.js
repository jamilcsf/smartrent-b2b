/**
 * Gestão de reservas dentro do painel do gestor (antes a página reservas.html).
 *
 * Preço e dados do imóvel vêm do snapshot gravado pelo servidor na criação da
 * reserva: o total NÃO é digitado aqui, e alterar o preço do imóvel depois
 * não muda reservas existentes. Imóveis fora do ar (em edição, aguardando
 * republicação, em pré-publicação) não aparecem para novas reservas.
 */
(function (global) {
  'use strict';

  var R = { raiz: null, anuncios: [], reservas: [], editando: null, params: {}, montado: false };

  function $(id) { return document.getElementById(id); }
  function esc(t) { return UI.escapar(t); }
  function moeda(v) {
    return 'R$ ' + Number(v || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function imoveisDisponiveis() {
    return R.anuncios.filter(function (a) { return a.noCatalogo; });
  }

  function montar() {
    R.raiz.innerHTML =
      '<div class="grid grid-cols-1 lg:grid-cols-3 gap-6">' +
        '<div class="bg-white border border-slate-200 p-5 rounded-2xl shadow-sm lg:col-span-1 space-y-4 self-start">' +
          '<h3 id="rg-titulo" class="text-sm font-bold text-slate-900">Cadastrar nova reserva</h3>' +
          '<form id="rg-form" class="space-y-3" novalidate>' +
            '<div><label class="block text-xs font-semibold text-slate-700 mb-1.5" for="rg-imovel">Imóvel</label>' +
              '<select id="rg-imovel" class="rg-campo"></select>' +
              '<p class="text-[10px] text-slate-400 mt-1">Só aparecem imóveis que estão no catálogo. Anúncio fora do ar não recebe novas reservas.</p></div>' +
            '<div class="grid grid-cols-2 gap-3">' +
              '<div><label class="block text-xs font-semibold text-slate-700 mb-1.5" for="rg-checkin">Check-in</label><input type="date" id="rg-checkin" class="rg-campo"></div>' +
              '<div><label class="block text-xs font-semibold text-slate-700 mb-1.5" for="rg-checkout">Check-out</label><input type="date" id="rg-checkout" class="rg-campo"></div>' +
            '</div>' +
            '<div><label class="block text-xs font-semibold text-slate-700 mb-1.5" for="rg-nome">Nome do hóspede</label><input type="text" id="rg-nome" class="rg-campo" maxlength="120"></div>' +
            '<div><label class="block text-xs font-semibold text-slate-700 mb-1.5" for="rg-email">E-mail do hóspede</label><input type="email" id="rg-email" class="rg-campo" maxlength="150"></div>' +
            '<div><label class="block text-xs font-semibold text-slate-700 mb-1.5" for="rg-status">Status</label>' +
              '<select id="rg-status" class="rg-campo"><option value="CONFIRMADA">CONFIRMADA</option><option value="PENDENTE">PENDENTE</option><option value="CANCELADA">CANCELADA</option></select></div>' +
            '<div id="rg-total" class="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600"></div>' +
            '<p id="rg-erro" class="hidden p-2.5 rounded-xl bg-rose-50 border border-rose-200 text-xs font-semibold text-rose-700"></p>' +
            '<div class="space-y-2">' +
              '<button type="submit" id="rg-salvar" class="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2.5 rounded-xl text-xs">Salvar reserva</button>' +
              '<button type="button" id="rg-cancelar" class="hidden w-full bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold py-2.5 rounded-xl text-xs border border-slate-300">Cancelar edição</button>' +
            '</div>' +
          '</form>' +
        '</div>' +
        '<div class="bg-white border border-slate-200 p-5 rounded-2xl shadow-sm lg:col-span-2 space-y-4">' +
          '<div class="flex items-center justify-between border-b border-slate-100 pb-3">' +
            '<div><h3 class="text-sm font-bold text-slate-900">Reservas dos meus imóveis</h3>' +
            '<p class="text-xs text-slate-500">O valor de cada reserva é o preço combinado na criação, mesmo que o preço do imóvel mude depois.</p></div>' +
            '<button type="button" id="rg-atualizar" class="text-xs bg-slate-50 hover:bg-slate-100 text-slate-700 px-3 py-2 rounded-xl font-semibold border border-slate-200">Atualizar</button>' +
          '</div>' +
          '<div class="overflow-x-auto"><table class="w-full text-xs text-left"><thead class="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider border-b border-slate-200"><tr>' +
            '<th class="p-3">ID</th><th class="p-3">Imóvel / Hóspede</th><th class="p-3">Período</th><th class="p-3">Valor</th><th class="p-3">Status</th><th class="p-3 text-center">Ações</th>' +
          '</tr></thead><tbody id="rg-tabela" class="divide-y divide-slate-100 font-medium text-slate-700"></tbody></table></div>' +
        '</div>' +
      '</div>' +
      '<style>.rg-campo{width:100%;background:#f8fafc;border:1px solid #cbd5e1;border-radius:.75rem;padding:.6rem .75rem;font-size:.75rem;color:#1e293b}' +
      '.rg-campo:focus{outline:none;box-shadow:0 0 0 2px #3b82f6;border-color:#3b82f6}</style>';

    $('rg-form').addEventListener('submit', salvar);
    $('rg-cancelar').addEventListener('click', limpar);
    $('rg-atualizar').addEventListener('click', carregarReservas);
    ['rg-imovel', 'rg-checkin', 'rg-checkout'].forEach(function (id) { $(id).addEventListener('change', atualizarTotal); });
    $('rg-tabela').addEventListener('click', aoClicarTabela);
    R.montado = true;
  }

  function preencherImoveis(valorAtual) {
    var sel = $('rg-imovel');
    var lista = imoveisDisponiveis();
    if (lista.length === 0 && !R.editando) {
      sel.innerHTML = '<option value="">Nenhum imóvel no catálogo</option>';
      sel.disabled = true;
      return;
    }
    sel.disabled = !!R.editando;
    sel.innerHTML = lista.map(function (a) {
      return '<option value="' + a.id + '">' + String(a.id).padStart(2, '0') + ' - ' + esc(a.dados.titulo) + '</option>';
    }).join('');
    if (R.editando) {
      // Reserva de imóvel que saiu do ar continua editável; o imóvel não muda.
      if (!lista.some(function (a) { return a.id === R.editando.imovelId; })) {
        sel.insertAdjacentHTML('beforeend', '<option value="' + R.editando.imovelId + '">' + esc(R.editando.imovelTitulo) + '</option>');
      }
      sel.value = R.editando.imovelId;
    } else if (valorAtual && lista.some(function (a) { return String(a.id) === String(valorAtual); })) {
      sel.value = valorAtual;
    }
  }

  /** O total é do servidor. Aqui só se estima o valor, para a pessoa saber o que será gravado. */
  function atualizarTotal() {
    var caixa = $('rg-total');
    if (R.editando) {
      var r = R.editando;
      caixa.innerHTML = 'Diária do contrato: <b>' + moeda(r.precoDiaria) + '</b> (congelada na criação). ' +
        'Total atual: <b>' + moeda(r.valorTotal) + '</b>. Se mudar as datas, o total é recalculado com esta diária.';
      return;
    }
    var id = Number($('rg-imovel').value);
    var a = R.anuncios.filter(function (x) { return x.id === id; })[0];
    var ini = $('rg-checkin').value, fim = $('rg-checkout').value;
    var noites = ini && fim ? Math.round((new Date(fim) - new Date(ini)) / 86400000) : 0;
    if (!a || !a.dados.valorDiaria || noites <= 0) {
      caixa.innerText = 'O total é calculado pelo servidor com o preço do imóvel no momento em que a reserva é criada.';
      return;
    }
    caixa.innerHTML = 'Estimativa: ' + noites + ' diária(s) × ' + moeda(a.dados.valorDiaria) + ' = <b>' + moeda(noites * a.dados.valorDiaria) +
      '</b>. O preço fica congelado nesta reserva.';
  }

  function mostrarErro(msg) {
    var p = $('rg-erro');
    p.innerText = msg || '';
    p.classList.toggle('hidden', !msg);
  }

  function limpar() {
    R.editando = null;
    $('rg-titulo').innerText = 'Cadastrar nova reserva';
    $('rg-salvar').innerText = 'Salvar reserva';
    $('rg-cancelar').classList.add('hidden');
    $('rg-form').reset();
    mostrarErro('');
    preencherImoveis();
    atualizarTotal();
  }

  async function salvar(evento) {
    evento.preventDefault();
    mostrarErro('');
    var corpo = {
      imovelId: Number($('rg-imovel').value),
      hospedeNome: $('rg-nome').value.trim(),
      hospedeEmail: $('rg-email').value.trim(),
      dataCheckin: $('rg-checkin').value,
      dataCheckout: $('rg-checkout').value,
      status: $('rg-status').value
    };
    if (!corpo.imovelId) { return mostrarErro('Selecione um imóvel.'); }
    if (!corpo.dataCheckin || !corpo.dataCheckout) { return mostrarErro('Informe as datas de check-in e check-out.'); }
    if (corpo.dataCheckout <= corpo.dataCheckin) { return mostrarErro('A data de check-out deve ser posterior à de check-in.'); }
    if (!corpo.hospedeNome || !corpo.hospedeEmail) { return mostrarErro('Informe o nome e o e-mail do hóspede.'); }
    try {
      if (R.editando) { await Api.put('/api/reservas/' + R.editando.id, corpo); }
      else { await Api.post('/api/reservas', corpo); }
      UI.toast('Reserva ' + (R.editando ? 'atualizada' : 'criada') + ' com sucesso!');
      limpar();
      await carregarReservas();
    } catch (e) { mostrarErro(e.message); }
  }

  async function carregarReservas() {
    var tbody = $('rg-tabela');
    try {
      R.reservas = await Api.get('/api/reservas');
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="6" class="p-8 text-center text-rose-600">Não foi possível carregar as reservas: ' + esc(e.message) + '</td></tr>';
      return;
    }
    if (R.reservas.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="p-8 text-center text-slate-400 font-normal">Nenhuma reserva nos seus imóveis.</td></tr>';
      return;
    }
    tbody.innerHTML = R.reservas.map(function (r) {
      var cls = r.status === 'PENDENTE' ? 'bg-amber-50 text-amber-700 border-amber-200'
        : (r.status === 'CANCELADA' ? 'bg-rose-50 text-rose-700 border-rose-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200');
      return '<tr class="hover:bg-slate-50/80">' +
        '<td class="p-3 font-bold text-slate-900">#' + r.id + '</td>' +
        '<td class="p-3"><span class="text-slate-800">' + esc(r.imovelTitulo) + '</span><br><span class="text-[10px] font-normal text-slate-500">' + esc(r.hospedeNome) + '</span>' +
          (r.imovelCaracteristicas ? '<br><span class="text-[10px] font-normal text-slate-400">' + esc(r.imovelCaracteristicas) + '</span>' : '') + '</td>' +
        '<td class="p-3 text-slate-600">' + esc(r.dataCheckin) + ' até ' + esc(r.dataCheckout) + '</td>' +
        '<td class="p-3 font-bold text-slate-900">' + moeda(r.valorTotal) +
          '<br><span class="text-[10px] font-normal text-slate-500">' + r.numeroDiarias + ' × ' + moeda(r.precoDiaria) + ' (preço da reserva)</span></td>' +
        '<td class="p-3"><span class="px-2.5 py-1 rounded-full text-[10px] font-bold border ' + cls + '">' + esc(r.status) + '</span></td>' +
        '<td class="p-3 text-center space-x-1">' +
          '<button type="button" data-rg="editar" data-id="' + r.id + '" class="bg-blue-50 hover:bg-blue-100 text-blue-700 border border-blue-200 px-2.5 py-1 rounded-lg text-xs font-semibold">Editar</button>' +
          '<button type="button" data-rg="excluir" data-id="' + r.id + '" class="bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 px-2.5 py-1 rounded-lg text-xs font-semibold">Excluir</button>' +
        '</td></tr>';
    }).join('');
  }

  async function aoClicarTabela(e) {
    var b = e.target.closest('[data-rg]');
    if (!b) { return; }
    var r = R.reservas.filter(function (x) { return x.id === Number(b.dataset.id); })[0];
    if (!r) { return; }
    if (b.dataset.rg === 'editar') {
      R.editando = r;
      preencherImoveis();
      $('rg-checkin').value = r.dataCheckin;
      $('rg-checkout').value = r.dataCheckout;
      $('rg-nome').value = r.hospedeNome || '';
      $('rg-email').value = r.hospedeEmail || '';
      $('rg-status').value = r.status;
      $('rg-titulo').innerText = 'Editar reserva #' + r.id;
      $('rg-salvar').innerText = 'Atualizar reserva';
      $('rg-cancelar').classList.remove('hidden');
      atualizarTotal();
      $('rg-form').scrollIntoView({ behavior: 'smooth', block: 'start' });
    } else {
      var ok = await UI.confirmar({ titulo: 'Excluir reserva?', mensagem: 'A reserva #' + r.id + ' será excluída.', confirmarTexto: 'Excluir', perigo: true });
      if (!ok) { return; }
      try { await Api.del('/api/reservas/' + r.id); UI.toast('Reserva excluída.'); await carregarReservas(); }
      catch (er) { UI.toast(er.message, 'erro'); }
    }
  }

  global.ReservasGestao = {
    mostrar: function (raiz, anuncios, params) {
      R.raiz = raiz;
      R.anuncios = anuncios;
      if (!R.montado) {
        montar();
        R.params = params || {};
        preencherImoveis(R.params.imovelId);
        if (R.params.checkin) { $('rg-checkin').value = R.params.checkin; }
        if (R.params.checkout) { $('rg-checkout').value = R.params.checkout; }
        atualizarTotal();
      }
      carregarReservas();
    },
    atualizarImoveis: function (anuncios) {
      R.anuncios = anuncios;
      if (R.montado && !R.editando) { preencherImoveis($('rg-imovel').value); atualizarTotal(); }
    }
  };
})(window);
