/**
 * "Minhas reservas" do cliente: situação, valores do snapshot, política,
 * pagamento pendente, cancelamento com simulação e acesso ao SmartChat.
 * Nada é calculado aqui: reembolso, prazo e regra vêm do servidor.
 */
(function (global) {
  'use strict';

  var ROTULO = { PENDENTE: 'Aguardando pagamento', CONFIRMADA: 'Confirmada', CONCLUIDA: 'Concluída',
    CANCELADA_COM_REEMBOLSO: 'Cancelada com reembolso', CANCELADA_SEM_REEMBOLSO: 'Cancelada', CANCELADA_PELO_GESTOR: 'Cancelada pelo gestor' };
  var REEMBOLSO = { NAO_APLICAVEL: 'Sem reembolso', PENDENTE: 'Reembolso em processamento', PROCESSADO: 'Reembolso processado', FALHA: 'Reembolso será tentado novamente' };
  var estado = { reservas: [], destaque: null, relogio: Date.now };

  function $(id) { return document.getElementById(id); }
  function esc(t) { return UI.escapar(t); }
  function moeda(v) { return 'R$ ' + Number(v || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function br(s) { var p = String(s).split('-'); return p[2] + '/' + p[1] + '/' + p[0]; }

  function chip(status) {
    var cancelada = /^CANCELADA/.test(status);
    var cls = status === 'PENDENTE' ? 'bg-amber-50 text-amber-700 border-amber-200'
      : cancelada ? 'bg-rose-50 text-rose-700 border-rose-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200';
    return '<span class="px-2.5 py-1 rounded-full text-[10px] font-bold border ' + cls + '">' + (ROTULO[status] || status) + '</span>';
  }

  function cartao(r) {
    var pol = r.politica;
    var c = r.cancelamento;
    var info = '';
    if (r.status === 'PENDENTE') {
      info = r.podePagar
        ? '<p class="text-[11px] text-amber-800">Pague até <b>' + UI.dataHora(r.expiraEm) + '</b> (<span data-alvo="' + esc(r.expiraEm) + '"></span> restantes) ou as datas serão liberadas.</p>'
        : '<p class="text-[11px] text-rose-700 font-semibold">O prazo de pagamento expirou.</p>';
    } else if (r.status === 'CONFIRMADA') {
      info = pol.semDireitoAReembolso
        ? '<p class="text-[11px] text-rose-700 font-semibold">Esta reserva não tem direito a reembolso em caso de cancelamento.</p>'
        : '<p class="text-[11px] text-emerald-700">Cancelamento com reembolso integral até <b>' + UI.dataHora(pol.reembolsoIntegralAte) + '</b>.</p>';
    } else if (c) {
      info = '<div class="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-0.5">' +
        '<p><b>' + (REEMBOLSO[c.reembolsoStatus] || c.reembolsoStatus) + '</b>' + (Number(c.reembolsoValor) > 0 ? ': ' + moeda(c.reembolsoValor) : '') + '</p>' +
        (c.prazoReembolso ? '<p class="text-slate-500">' + esc(c.prazoReembolso) + '</p>' : '') +
        '<p class="text-slate-400">Cancelada em ' + UI.dataHora(c.canceladaEm) + ' por ' + ({ CLIENTE: 'você', GESTOR: 'o gestor', SISTEMA: 'falta de pagamento' }[c.canceladaPor] || '—') +
        (c.motivo ? '. Motivo: ' + esc(c.motivo) : '') + '</p></div>';
    }
    return '<article class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 space-y-3 ' + (estado.destaque === r.id ? 'ring-2 ring-blue-500' : '') + '" id="reserva-' + r.id + '">' +
      '<div class="flex flex-wrap items-start justify-between gap-2">' +
        '<div><h3 class="font-bold text-slate-900"><a class="hover:underline" href="/imoveis/' + r.imovelId + '">' + esc(r.imovelTitulo) + '</a></h3>' +
        '<p class="text-xs text-slate-500">' + esc(r.imovelCodigo) + (r.imovelEndereco ? ' • ' + esc(r.imovelEndereco) : '') + '</p></div>' + chip(r.status) + '</div>' +
      '<dl class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">' +
        '<div><dt class="text-slate-500">Check-in</dt><dd class="font-semibold">' + br(r.dataCheckin) + ' <span class="font-normal text-slate-400">' + esc(pol.horarioCheckin) + '</span></dd></div>' +
        '<div><dt class="text-slate-500">Check-out</dt><dd class="font-semibold">' + br(r.dataCheckout) + '</dd></div>' +
        '<div><dt class="text-slate-500">Diárias / hóspedes</dt><dd class="font-semibold">' + r.numeroDiarias + ' / ' + r.numeroHospedes + '</dd></div>' +
        '<div><dt class="text-slate-500">Total</dt><dd class="font-bold text-slate-900">' + moeda(r.total) + '</dd></div>' +
      '</dl>' +
      '<p class="text-[11px] text-slate-500">' + moeda(r.precoDiaria) + ' x ' + r.numeroDiarias + (Number(r.taxaLimpeza) > 0 ? ' + limpeza ' + moeda(r.taxaLimpeza) : '') +
        ' • política ' + esc(pol.versao) + ' (<button type="button" data-acao="politica" class="text-blue-700 underline underline-offset-2">ver</button>)</p>' +
      info +
      '<div class="flex flex-wrap gap-2 pt-1">' +
        (r.podePagar ? '<button type="button" data-acao="pagar" data-id="' + r.id + '" class="px-4 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white">Pagar agora</button>' : '') +
        '<a href="/smartchat.html?reserva=' + r.id + '" class="px-4 py-2 rounded-xl text-xs font-bold border border-blue-600 text-blue-700 hover:bg-blue-50">Falar com o gestor</a>' +
        (r.podeCancelar ? '<button type="button" data-acao="cancelar" data-id="' + r.id + '" class="px-4 py-2 rounded-xl text-xs font-bold border border-rose-300 text-rose-700 hover:bg-rose-50">Cancelar reserva</button>' : '') +
      '</div></article>';
  }

  function render() {
    $('vazio').classList.toggle('hidden', estado.reservas.length > 0);
    $('lista').innerHTML = estado.reservas.map(cartao).join('');
    atualizarContagens();
    if (global.lucide) { global.lucide.createIcons(); }
  }

  function atualizarContagens() {
    var zerou = false;
    document.querySelectorAll('[data-alvo]').forEach(function (el) {
      var resta = UI.instante(el.dataset.alvo) - estado.relogio();
      el.innerText = UI.contagem(resta);
      if (resta <= 0) { zerou = true; }
    });
    if (zerou && !estado.recarregando) { estado.recarregando = true; setTimeout(function () { carregar().then(function () { estado.recarregando = false; }); }, 1500); }
  }

  async function carregar() {
    try {
      estado.reservas = await Api.get('/api/cliente/reservas');
      $('erroPagina').classList.add('hidden');
      render();
    } catch (e) {
      $('erroPagina').innerText = 'Não foi possível carregar as reservas: ' + e.message;
      $('erroPagina').classList.remove('hidden');
    } finally { $('carregando').classList.add('hidden'); }
  }

  /** Simulação calculada pelo servidor; a decisão final é recalculada ao confirmar. */
  async function cancelar(r) {
    var sim;
    try { sim = await Api.get('/api/cliente/reservas/' + r.id + '/simulacao-cancelamento'); }
    catch (e) { UI.toast(e.message, 'erro'); return; }
    var semReembolso = !sim.comReembolso && sim.regra !== 'PENDENTE_SEM_COBRANCA';
    var m = UI.modal({
      titulo: 'Cancelar reserva #' + r.id,
      corpo: '<div class="p-3 rounded-xl border text-sm font-semibold ' + (semReembolso ? 'bg-rose-50 border-rose-200 text-rose-800' : 'bg-emerald-50 border-emerald-200 text-emerald-800') + '">' + esc(sim.mensagem) + '</div>' +
        (semReembolso ? '<p class="mt-2 text-xs text-rose-700">O cancelamento é permitido, mas o valor pago não será devolvido.</p>' : '') +
        '<p class="mt-2 text-[11px] text-slate-500">Essa é uma simulação. Ao confirmar, o servidor aplica a política da sua reserva no momento do cancelamento. As datas são liberadas na hora.</p>' +
        '<label class="block mt-3"><span class="text-xs font-semibold text-slate-700">Motivo <span class="font-normal text-slate-400">(opcional)</span></span>' +
        '<textarea id="cc-motivo" rows="2" maxlength="300" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"></textarea></label>',
      botoes: [
        { texto: 'Manter reserva', classe: UI.BTN_NEUTRO, aoClicar: function (fechar) { fechar(); } },
        { texto: 'Confirmar cancelamento', classe: UI.BTN_PERIGO, aoClicar: async function (fechar, el, btn) {
            btn.disabled = true;
            try {
              var res = await Api.post('/api/cliente/reservas/' + r.id + '/cancelar', { motivo: el.querySelector('#cc-motivo').value.trim() || null });
              fechar();
              var c = res.cancelamento;
              UI.toast('Reserva cancelada. ' + (c && Number(c.reembolsoValor) > 0 ? 'Reembolso de ' + moeda(c.reembolsoValor) + ': ' + (REEMBOLSO[c.reembolsoStatus] || '') + '.' : 'Sem reembolso.'));
              await carregar();
            } catch (e) { UI.toast(e.message, 'erro'); btn.disabled = false; }
          } }
      ]
    });
    m.el.addEventListener('click', function (e) { if (e.target === m.el) { m.fechar(); } });
  }

  async function aoClicar(e) {
    var b = e.target.closest('[data-acao]');
    if (!b) { return; }
    var r = estado.reservas.filter(function (x) { return x.id === Number(b.dataset.id) || (b.dataset.acao === 'politica'); })[0];
    if (b.dataset.acao === 'politica') { return Reservar.verPolitica(); }
    if (!r) { return; }
    if (b.dataset.acao === 'pagar') { return Reservar.pagar(r); }
    if (b.dataset.acao === 'cancelar') { return cancelar(r); }
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!global.Auth || !Auth.isAuthenticated()) { location.replace('/login.html?redirectTo=' + encodeURIComponent(location.pathname + location.search)); return; }
    var id = Number(new URLSearchParams(location.search).get('id'));
    estado.destaque = id || null;
    $('lista').addEventListener('click', aoClicar);
    carregar().then(function () {
      var el = id && document.getElementById('reserva-' + id);
      if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
    });
    setInterval(atualizarContagens, 1000);
  });
})(window);
