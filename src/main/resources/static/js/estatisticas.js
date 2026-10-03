/**
 * Dashboard do gestor: so estatisticas. Os numeros vem prontos do servidor
 * (GET /api/gestor/estatisticas), ja filtrados pelo gestor autenticado; aqui
 * so ha apresentacao. Sem botao de acao operacional.
 */
(function (global) {
  'use strict';

  var ROTULO = {
    PRE_PUBLICACAO_SEM_PRECO: 'Pré-publicação (sem preço)', PRE_PUBLICACAO_AGUARDANDO: 'Pré-publicação (aguardando)',
    PRONTO_PARA_PUBLICAR: 'Pronto para publicar', PUBLICADO: 'Publicado', EM_EDICAO: 'Em edição',
    REPUBLICACAO_AGENDADA: 'Republicação agendada'
  };
  var graficos = {};

  function $(id) { return document.getElementById(id); }
  function moeda(v) { return 'R$ ' + Number(v || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function rotuloMes(ym) {
    var p = ym.split('-');
    return ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'][Number(p[1]) - 1] + '/' + p[0].slice(2);
  }

  function cartao(titulo, valor, nota) {
    return '<div class="bg-white border border-slate-200 rounded-2xl shadow-sm p-4">' +
      '<p class="text-[11px] font-semibold text-slate-500">' + titulo + '</p>' +
      '<p class="text-xl font-extrabold text-slate-900 mt-1">' + valor + '</p>' +
      (nota ? '<p class="text-[10px] text-slate-400 mt-0.5">' + nota + '</p>' : '') + '</div>';
  }

  function grafico(id, tipo, rotulos, series) {
    if (graficos[id]) { graficos[id].destroy(); }
    graficos[id] = new Chart($(id), {
      type: tipo,
      data: { labels: rotulos, datasets: series },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: series.length > 1 } },
                 scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });
  }

  function render(e) {
    var pct = (e.taxaOcupacao * 100).toFixed(1).replace('.', ',') + '%';
    $('cartoes').innerHTML =
      cartao('Imóveis', e.totalImoveis) +
      cartao('Reservas no período', e.totalReservas, e.reservasCanceladas + ' cancelada(s)') +
      cartao('Taxa de ocupação', pct, e.noitesOcupadas + ' de ' + e.noitesDisponiveis + ' noites') +
      cartao('Noites bloqueadas', e.noitesBloqueadas, 'fora do cálculo de ocupação') +
      cartao('Receita bruta', moeda(e.receitaBruta)) +
      cartao('Reembolsos', moeda(e.reembolsos)) +
      cartao('Receita líquida', moeda(e.receitaLiquida)) +
      cartao('Ticket médio', moeda(e.ticketMedio));

    var rotulos = e.meses.map(function (m) { return rotuloMes(m.mes); });
    grafico('grReservas', 'bar', rotulos, [
      { label: 'Reservas', data: e.meses.map(function (m) { return m.reservas; }), backgroundColor: '#2563eb' },
      { label: 'Canceladas', data: e.meses.map(function (m) { return m.canceladas; }), backgroundColor: '#f43f5e' }]);
    grafico('grReceita', 'line', rotulos, [
      { label: 'Receita líquida', data: e.meses.map(function (m) { return m.receitaLiquida; }), borderColor: '#059669', backgroundColor: '#05966933', fill: true, tension: 0.25 }]);
    grafico('grNoites', 'bar', rotulos, [
      { label: 'Noites ocupadas', data: e.meses.map(function (m) { return m.noitesOcupadas; }), backgroundColor: '#64748b' }]);

    $('listaStatus').innerHTML = Object.keys(e.imoveisPorStatus).map(function (s) {
      return '<li class="flex justify-between py-2"><span class="text-slate-600">' + (ROTULO[s] || s) + '</span>' +
             '<b class="text-slate-900">' + e.imoveisPorStatus[s] + '</b></li>';
    }).join('');
  }

  async function carregar() {
    $('carregando').classList.remove('hidden');
    $('erroPagina').classList.add('hidden');
    try {
      var e = await Api.get('/api/gestor/estatisticas?meses=' + $('selMeses').value);
      render(e);
      $('conteudo').classList.remove('hidden');
    } catch (err) {
      $('erroPagina').innerText = 'Não foi possível carregar as estatísticas: ' + err.message;
      $('erroPagina').classList.remove('hidden');
    } finally {
      $('carregando').classList.add('hidden');
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    // Conveniencia de interface; quem protege os dados e o servidor (403).
    if (!global.Auth || !Auth.isGestor()) { location.replace('/imoveis.html'); return; }
    $('selMeses').addEventListener('change', carregar);
    carregar();
  });
})(window);
