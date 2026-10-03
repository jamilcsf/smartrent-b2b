/**
 * Portal da Transparência: sugestão de preço por IA para imóveis em
 * pré-publicação, com seleção múltipla.
 *
 * A IA só sugere. Cada preço só é salvo quando o gestor clica em "Confirmar":
 * se o valor confirmado é o sugerido, a origem registrada é IA; se foi
 * ajustado à mão, é MANUAL. Falha da IA vira mensagem clara e a linha cai
 * para a edição manual do valor.
 */
(function (global) {
  'use strict';

  var PRE = ['PRE_PUBLICACAO_SEM_PRECO', 'PRE_PUBLICACAO_AGUARDANDO', 'PRONTO_PARA_PUBLICAR'];
  var ROTULO = { PRE_PUBLICACAO_SEM_PRECO: 'Sem preço', PRE_PUBLICACAO_AGUARDANDO: 'Aguardando 24h', PRONTO_PARA_PUBLICAR: 'Pronto para publicar' };

  var I = { imoveis: [], sugestoes: {} }; // sugestoes[id] = {valor, justificativa, modelo, erro}

  function $(id) { return document.getElementById(id); }
  function esc(t) { return UI.escapar(t); }
  function moeda(v) {
    return v == null ? '—' : 'R$ ' + Number(v).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function linha(a) {
    var s = I.sugestoes[a.id];
    var celulaSugestao, acao;
    if (!s) {
      celulaSugestao = '<span class="text-slate-400">—</span>';
      acao = '<button type="button" data-ia="manual" data-id="' + a.id + '" class="px-3 py-1.5 rounded-lg border border-slate-300 hover:bg-slate-50 font-semibold">Definir manualmente</button>';
    } else if (s.erro) {
      celulaSugestao = '<p class="text-rose-600 font-semibold max-w-xs">' + esc(s.erro) + '</p>' +
        '<input type="number" min="1" step="0.01" data-valor="' + a.id + '" placeholder="Valor manual" class="mt-1.5 w-32 bg-slate-50 border border-slate-300 rounded-lg p-1.5">';
      acao = '<button type="button" data-ia="confirmar" data-id="' + a.id + '" class="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-bold">Confirmar</button>';
    } else {
      celulaSugestao = '<div class="flex items-center gap-1.5"><span class="text-slate-500">R$</span>' +
        '<input type="number" min="1" step="0.01" data-valor="' + a.id + '" value="' + s.valor + '" class="w-28 bg-slate-50 border border-slate-300 rounded-lg p-1.5 font-bold text-slate-900"></div>' +
        '<p class="text-[10px] text-slate-500 mt-1 max-w-xs">' + esc(s.justificativa) + ' <span class="text-slate-400">(' + esc(s.modelo) + ')</span></p>';
      acao = '<button type="button" data-ia="confirmar" data-id="' + a.id + '" class="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-bold">Confirmar preço</button>';
    }
    return '<tr class="align-top" id="ia-linha-' + a.id + '">' +
      '<td class="p-3"><input type="checkbox" data-sel="' + a.id + '" class="accent-blue-600" aria-label="Selecionar ' + esc(a.dados.titulo) + '"></td>' +
      '<td class="p-3 font-semibold text-slate-800">' + esc(a.dados.titulo) + '<br><span class="text-[10px] font-normal text-slate-500">' + esc([a.dados.bairro, a.dados.cidade].filter(Boolean).join(' • ')) + '</span></td>' +
      '<td class="p-3 text-slate-600">' + (ROTULO[a.status] || a.status) + '</td>' +
      '<td class="p-3 text-slate-700">' + moeda(a.dados.valorDiaria) + '</td>' +
      '<td class="p-3">' + celulaSugestao + '</td>' +
      '<td class="p-3">' + acao + '</td></tr>';
  }

  function selecionados() {
    return Array.prototype.slice.call(document.querySelectorAll('[data-sel]:checked')).map(function (c) { return Number(c.dataset.sel); });
  }

  function atualizarBotao() {
    var n = selecionados().length;
    $('iaSugerir').disabled = n === 0;
    $('iaSugerir').innerText = n > 1 ? 'Sugerir preço com IA (' + n + ' imóveis)' : 'Sugerir preço com IA';
  }

  function render(marcados) {
    $('iaTabela').innerHTML = I.imoveis.map(linha).join('');
    (marcados || []).forEach(function (id) {
      var c = document.querySelector('[data-sel="' + id + '"]');
      if (c) { c.checked = true; }
    });
    atualizarBotao();
  }

  async function sugerir() {
    var ids = selecionados();
    if (!ids.length) { return; }
    var btn = $('iaSugerir');
    btn.disabled = true;
    $('iaStatus').innerText = 'Consultando a IA para ' + ids.length + ' imóvel(is)...';
    try {
      var r = await Api.post('/api/gestor/precificacao/sugestoes', { imovelIds: ids });
      r.itens.forEach(function (i) {
        I.sugestoes[i.imovelId] = { valor: i.valorSugerido, justificativa: i.justificativa, modelo: i.modelo, erro: i.erro };
      });
      var falhas = r.itens.filter(function (i) { return i.erro; }).length;
      $('iaStatus').innerText = falhas
        ? (r.itens.length - falhas) + ' sugestão(ões) pronta(s); ' + falhas + ' sem sugestão (defina o valor manualmente).'
        : 'Sugestões prontas. Revise, ajuste se quiser e confirme cada preço.';
      render(ids);
    } catch (e) {
      $('iaStatus').innerText = '';
      UI.toast(e.message, 'erro');
      atualizarBotao();
    }
  }

  async function confirmar(id) {
    var campo = document.querySelector('[data-valor="' + id + '"]');
    var valor = campo ? Number(campo.value) : NaN;
    if (!(valor >= 1)) { UI.toast('Informe um valor de diária válido (mínimo R$ 1,00).', 'erro'); return; }
    var s = I.sugestoes[id];
    var origem = s && !s.erro && Number(s.valor) === valor ? 'IA' : 'MANUAL';
    try {
      await Api.put('/api/gestor/imoveis/' + id + '/preco', { valor: valor, origem: origem });
      UI.toast('Preço confirmado' + (origem === 'IA' ? ' (sugestão da IA).' : '.'));
      delete I.sugestoes[id];
      await carregar();
    } catch (e) { UI.toast(e.message, 'erro'); }
  }

  function aoClicar(e) {
    var b = e.target.closest('[data-ia]');
    if (!b) { return; }
    var id = Number(b.dataset.id);
    if (b.dataset.ia === 'confirmar') { return confirmar(id); }
    // "Definir manualmente": abre o campo sem sugestão da IA.
    I.sugestoes[id] = { erro: 'Defina o valor da diária manualmente.' };
    render(selecionados());
  }

  async function carregar() {
    try {
      var todos = await Api.get('/api/gestor/imoveis');
      I.imoveis = todos.filter(function (a) { return PRE.indexOf(a.status) >= 0; });
      $('iaCarregando').classList.add('hidden');
      $('iaVazio').classList.toggle('hidden', I.imoveis.length > 0);
      $('iaConteudo').classList.toggle('hidden', I.imoveis.length === 0);
      render(selecionados());
    } catch (e) {
      $('iaCarregando').innerText = 'Não foi possível carregar seus imóveis: ' + e.message;
    }
  }

  function iniciar() {
    if (!global.Auth || !Auth.isGestor()) { return; }
    $('ia-preco').classList.remove('hidden');
    $('iaTabela').addEventListener('click', aoClicar);
    $('iaTabela').addEventListener('change', atualizarBotao);
    $('iaTodos').addEventListener('change', function (e) {
      document.querySelectorAll('[data-sel]').forEach(function (c) { c.checked = e.target.checked; });
      atualizarBotao();
    });
    $('iaSugerir').addEventListener('click', sugerir);
    carregar().then(function () {
      var pre = Number(new URLSearchParams(location.search).get('ia'));
      var c = pre && document.querySelector('[data-sel="' + pre + '"]');
      if (c) { c.checked = true; atualizarBotao(); $('ia-preco').scrollIntoView({ behavior: 'smooth' }); }
    });
  }

  document.addEventListener('DOMContentLoaded', iniciar);
})(window);
