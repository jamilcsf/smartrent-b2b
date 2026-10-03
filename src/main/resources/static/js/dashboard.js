/**
 * Painel do gestor: pré-publicação (preço e publicação), anúncios (editar,
 * confirmar, descartar, contagem da republicação), reservas e notificações.
 *
 * O painel só pede e mostra. Prazos (24h, 2h, lembrete) e permissões são
 * decididos pelo servidor; os relógios aqui partem da hora que ele informa.
 */
(function (global) {
  'use strict';

  var ABAS = ['pre', 'anuncios', 'reservas'];
  var PRE = ['PRE_PUBLICACAO_SEM_PRECO', 'PRE_PUBLICACAO_AGUARDANDO', 'PRONTO_PARA_PUBLICAR'];
  var ROTULO_STATUS = {
    PRE_PUBLICACAO_SEM_PRECO: ['Sem preço', 'bg-slate-200 text-slate-700'],
    PRE_PUBLICACAO_AGUARDANDO: ['Aguardando 24h', 'bg-amber-100 text-amber-800'],
    PRONTO_PARA_PUBLICAR: ['Pronto para publicar', 'bg-emerald-100 text-emerald-800'],
    PUBLICADO: ['No ar', 'bg-emerald-100 text-emerald-800'],
    EM_EDICAO: ['Em edição', 'bg-amber-100 text-amber-900'],
    REPUBLICACAO_AGENDADA: ['Republicação agendada', 'bg-blue-100 text-blue-800']
  };

  var E = { anuncios: [], aba: 'pre', relogio: Date.now, destaque: null, recarregando: false };

  function $(id) { return document.getElementById(id); }
  function esc(t) { return UI.escapar(t); }
  function moeda(v) {
    return v == null ? '—' : 'R$ ' + Number(v).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  function prazo(horas) { return horas + (horas === 1 ? ' hora' : ' horas'); }

  function capa(a) {
    var m = (a.midias || []).filter(function (x) { return x.capa && x.estado !== 'REMOVIDA'; })[0] ||
            (a.midias || []).filter(function (x) { return x.tipo !== 'VIDEO' && x.estado !== 'REMOVIDA'; })[0];
    if (!m) {
      return '<div class="w-24 h-20 rounded-xl bg-slate-200 flex items-center justify-center text-slate-400 shrink-0"><i data-lucide="image-off" class="w-5 h-5"></i></div>';
    }
    return '<img src="' + esc(m.miniaturaUrl || m.url) + '" alt="" class="w-24 h-20 rounded-xl object-cover shrink-0 bg-slate-200">';
  }

  function selo(status) {
    var r = ROTULO_STATUS[status] || [status, 'bg-slate-200 text-slate-700'];
    return '<span class="text-[10px] font-bold px-2 py-1 rounded-full ' + r[1] + '">' + r[0] + '</span>';
  }

  function local(a) {
    var d = a.dados || {};
    return esc([d.bairro, d.cidade].filter(Boolean).join(' • '));
  }

  // ------------------------------------------------------- pré-publicação

  function cartaoPre(a) {
    var d = a.dados;
    var semPreco = a.status === 'PRE_PUBLICACAO_SEM_PRECO';
    var aguardando = a.status === 'PRE_PUBLICACAO_AGUARDANDO' && !a.podePublicar;
    var h = a.janelaPrePublicacaoHoras;
    var info = '';
    if (semPreco) {
      info = '<p class="text-[11px] text-slate-500">Defina o preço da diária. A primeira confirmação inicia a janela de ' + prazo(h) +
        ' para ajustes; alterar o valor depois <b>não reinicia</b> esse prazo.</p>';
    } else if (aguardando) {
      info = '<p class="text-[11px] text-amber-800">Janela de ' + prazo(h) + ' iniciada em ' + UI.dataHora(a.precoPrimeiraConfirmacaoEm) +
        '. Você pode ajustar o preço quantas vezes quiser. Publicação liberada em <b data-alvo="' + esc(a.prontoParaPublicarEm) + '" data-recarregar="1">…</b>.</p>';
    } else {
      info = '<p class="text-[11px] text-emerald-700">Prazo cumprido. Você ainda pode ajustar o preço antes de publicar.</p>';
    }
    return '<article class="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 ' + (E.destaque === a.id ? 'ring-2 ring-blue-500' : '') + '" id="anuncio-' + a.id + '">' +
      '<div class="flex gap-4">' + capa(a) +
        '<div class="flex-1 min-w-0">' +
          '<div class="flex flex-wrap items-center gap-2"><h3 class="font-bold text-slate-900 truncate">' + esc(d.titulo) + '</h3>' + selo(a.status) + '</div>' +
          '<p class="text-xs text-slate-500 mt-0.5">' + local(a) + ' • ' + a.totalImagens + ' imagens, ' + a.totalVideos + ' vídeos</p>' +
        '</div>' +
      '</div>' +
      '<div class="mt-4 pt-4 border-t border-slate-100 space-y-3">' +
        '<div class="flex flex-wrap items-end gap-2">' +
          '<label class="block"><span class="text-[10px] font-semibold text-slate-500">Valor da diária (R$)</span>' +
            '<input type="number" min="1" step="0.01" data-preco="' + a.id + '" value="' + (d.valorDiaria != null ? d.valorDiaria : '') + '" ' +
            'class="block mt-1 w-36 bg-slate-50 border border-slate-300 rounded-xl p-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="0,00"></label>' +
          '<button type="button" data-acao="preco" data-id="' + a.id + '" class="px-3 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white">' +
            (semPreco ? 'Confirmar preço' : 'Alterar preço') + '</button>' +
          '<a href="index.html?ia=' + a.id + '#ia-preco" class="px-3 py-2 rounded-xl text-xs font-bold border border-slate-300 hover:bg-slate-50 flex items-center gap-1.5">' +
            '<i data-lucide="sparkles" class="w-3.5 h-3.5"></i> Sugerir com IA</a>' +
        '</div>' + info +
        '<div class="flex flex-wrap gap-2">' +
          (a.podePublicar ? '<button type="button" data-acao="publicar" data-id="' + a.id + '" class="px-4 py-2 rounded-xl text-xs font-bold bg-emerald-600 hover:bg-emerald-700 text-white">Publicar</button>' : '') +
          '<a href="anuncio-form.html?id=' + a.id + '" class="px-4 py-2 rounded-xl text-xs font-bold border border-slate-300 hover:bg-slate-50">Editar cadastro</a>' +
        '</div>' +
      '</div></article>';
  }

  function renderPre() {
    var lista = E.anuncios.filter(function (a) { return PRE.indexOf(a.status) >= 0; });
    $('cntPre').innerText = lista.length;
    $('aba-pre').innerHTML = lista.length === 0
      ? vazio('Nenhum imóvel em pré-publicação.', 'Crie um anúncio para ele aparecer aqui até ser publicado.')
      : lista.map(cartaoPre).join('');
  }

  // --------------------------------------------------------------- anúncios

  function cartaoAnuncio(a) {
    var d = a.dados;
    var corpo = '', botoes = '';
    if (a.status === 'PUBLICADO') {
      corpo = '<p class="text-xs text-slate-600">No catálogo. Diária ' + moeda(d.valorDiaria) + '.</p>';
      botoes = '<a href="/imoveis/' + a.id + '" class="px-3 py-2 rounded-xl text-xs font-bold border border-slate-300 hover:bg-slate-50">Ver no catálogo</a>' +
        '<button type="button" data-acao="editar" data-id="' + a.id + '" class="px-3 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white">Editar anúncio</button>';
    } else if (a.status === 'REPUBLICACAO_AGENDADA') {
      corpo = '<p class="text-xs text-blue-800">Alteração confirmada. <b>Fora do ar</b>; volta ao catálogo em <b data-alvo="' + esc(a.republicarEm) +
        '" data-recarregar="1">…</b> (às ' + UI.dataHora(a.republicarEm) + ').</p>';
      botoes = '<button type="button" data-acao="editar" data-id="' + a.id + '" class="px-3 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white">Editar anúncio</button>';
    } else {
      var desde = new Date(a.edicaoIniciadaEm).getTime();
      var emEdicaoMs = E.relogio() - desde;
      var longo = emEdicaoMs >= 24 * 3600 * 1000;
      corpo = '<p class="text-xs text-amber-900"><b>Em edição desde ' + UI.dataHora(a.edicaoIniciadaEm) + '.</b> O anúncio está <b>fora do catálogo</b> e sem receber novas reservas.</p>' +
        (longo ? '<p class="mt-1.5 inline-flex items-center gap-1.5 text-[11px] font-bold bg-rose-100 text-rose-700 px-2.5 py-1 rounded-full">' +
          '<i data-lucide="alert-triangle" class="w-3.5 h-3.5"></i> Fora do ar há ' + UI.duracaoLonga(emEdicaoMs) + '</p>' : '');
      botoes = '<a href="anuncio-form.html?id=' + a.id + '" class="px-3 py-2 rounded-xl text-xs font-bold border border-slate-300 hover:bg-slate-50">Continuar edição</a>' +
        '<button type="button" data-acao="confirmar" data-id="' + a.id + '" class="px-3 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white">Confirmar alteração</button>' +
        '<button type="button" data-acao="descartar" data-id="' + a.id + '" class="px-3 py-2 rounded-xl text-xs font-bold bg-white border border-rose-300 text-rose-700 hover:bg-rose-50">Descartar edição</button>';
    }
    var emEdicao = a.status === 'EM_EDICAO';
    return '<article class="rounded-2xl border shadow-sm p-4 ' + (emEdicao ? 'bg-amber-50/60 border-amber-300' : 'bg-white border-slate-200') +
      (E.destaque === a.id ? ' ring-2 ring-blue-500' : '') + '" id="anuncio-' + a.id + '">' +
      '<div class="flex gap-4">' + capa(a) +
        '<div class="flex-1 min-w-0">' +
          '<div class="flex flex-wrap items-center gap-2"><h3 class="font-bold text-slate-900 truncate">' + esc(d.titulo) + '</h3>' + selo(a.status) + '</div>' +
          '<p class="text-xs text-slate-500 mt-0.5">' + local(a) + '</p>' +
          '<div class="mt-2">' + corpo + '</div>' +
        '</div>' +
      '</div>' +
      '<div class="mt-4 pt-3 border-t ' + (emEdicao ? 'border-amber-200' : 'border-slate-100') + ' flex flex-wrap gap-2">' + botoes + '</div></article>';
  }

  function renderAnuncios() {
    var lista = E.anuncios.filter(function (a) { return PRE.indexOf(a.status) < 0; });
    $('cntAnuncios').innerText = lista.length;
    $('aba-anuncios').innerHTML = lista.length === 0
      ? vazio('Nenhum anúncio publicado ainda.', 'Quando você publicar um imóvel da pré-publicação, ele aparece aqui.')
      : lista.map(cartaoAnuncio).join('');
  }

  function vazio(titulo, texto) {
    return '<div class="bg-white rounded-2xl border border-dashed border-slate-300 p-10 text-center">' +
      '<p class="font-bold text-slate-700">' + titulo + '</p><p class="text-xs text-slate-500 mt-1">' + texto + '</p></div>';
  }

  function renderTudo() {
    renderPre();
    renderAnuncios();
    if (global.lucide) { global.lucide.createIcons(); }
    atualizarContagens();
  }

  // --------------------------------------------------------------- contagens

  /** Atualiza as contagens regressivas; ao zerar, recarrega os dados uma vez (a promoção é do servidor). */
  function atualizarContagens() {
    var zerou = false;
    document.querySelectorAll('[data-alvo]').forEach(function (el) {
      var alvo = new Date(el.dataset.alvo).getTime();
      var restante = alvo - E.relogio();
      el.innerText = restante > 0 ? UI.contagem(restante) : 'agora';
      if (restante <= 0 && el.dataset.recarregar) { zerou = true; }
    });
    if (zerou && !E.recarregando) {
      E.recarregando = true;
      setTimeout(function () { carregar().then(function () { E.recarregando = false; }); }, 2000);
    }
  }

  // --------------------------------------------------------------- ações

  async function aoClicarAcao(e) {
    var b = e.target.closest('[data-acao]');
    if (!b) { return; }
    var id = Number(b.dataset.id);
    var a = E.anuncios.filter(function (x) { return x.id === id; })[0];
    if (!a) { return; }
    var acao = b.dataset.acao;

    if (acao === 'preco') {
      var campo = document.querySelector('[data-preco="' + id + '"]');
      var valor = Number(campo.value);
      if (!(valor >= 1)) { UI.toast('Informe um valor de diária válido (mínimo R$ 1,00).', 'erro'); return; }
      try {
        await Api.put('/api/gestor/imoveis/' + id + '/preco', { valor: valor, origem: 'MANUAL' });
        UI.toast('Preço confirmado.');
        await carregar();
      } catch (er) { UI.toast(er.message, 'erro'); }
      return;
    }
    var r = null;
    if (acao === 'publicar') { r = await AnuncioAcoes.publicar(a); if (r) { UI.toast('Anúncio publicado no catálogo.'); E.aba = 'anuncios'; } }
    if (acao === 'editar') {
      r = await AnuncioAcoes.iniciarEdicao(a);
      if (r) { location.href = 'anuncio-form.html?id=' + id; return; }
    }
    if (acao === 'confirmar') { r = await AnuncioAcoes.confirmarAlteracao(a); if (r) { UI.toast('Alteração confirmada. O anúncio volta ao catálogo em ' + prazo(r.republicacaoHoras) + '.'); } }
    if (acao === 'descartar') { r = await AnuncioAcoes.descartarEdicao(a); if (r) { UI.toast('Edição descartada. O anúncio voltou ao ar.'); } }
    if (r) { E.destaque = id; await carregar(); mostrarAba(E.aba); }
  }

  // ------------------------------------------------------------- notificações

  async function carregarNotificacoes() {
    try {
      var r = await Api.get('/api/gestor/notificacoes');
      var c = $('contSino');
      c.innerText = r.naoLidas > 9 ? '9+' : r.naoLidas;
      c.classList.toggle('hidden', !r.naoLidas);
      $('listaNotificacoes').innerHTML = r.itens.length === 0
        ? '<p class="p-4 text-xs text-slate-500">Nenhuma notificação.</p>'
        : r.itens.map(function (n) {
          return '<button type="button" data-notif="' + n.id + '" data-imovel="' + (n.imovelId || '') + '" class="w-full text-left px-4 py-3 hover:bg-slate-50 ' + (n.lida ? '' : 'bg-blue-50/50') + '">' +
            '<p class="text-xs font-bold text-slate-800">' + esc(n.titulo) + '</p>' +
            '<p class="text-[11px] text-slate-600 mt-0.5">' + esc(n.mensagem) + '</p>' +
            '<p class="text-[10px] text-slate-400 mt-1">' + UI.dataHora(n.criadaEm) + '</p></button>';
        }).join('');
    } catch (e) { /* o sino não deve derrubar o painel */ }
  }

  async function aoClicarNotificacao(e) {
    var b = e.target.closest('[data-notif]');
    if (!b) { return; }
    try { await Api.post('/api/gestor/notificacoes/' + b.dataset.notif + '/lida', {}); } catch (er) { /* segue */ }
    $('menuSino').classList.add('hidden');
    if (b.dataset.imovel) { E.destaque = Number(b.dataset.imovel); mostrarAba('anuncios'); rolarAteDestaque(); }
    carregarNotificacoes();
  }

  function rolarAteDestaque() {
    renderTudo();
    var el = E.destaque && $('anuncio-' + E.destaque);
    if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
  }

  // ------------------------------------------------------------------ abas

  function mostrarAba(aba) {
    if (ABAS.indexOf(aba) < 0) { aba = 'pre'; }
    E.aba = aba;
    ABAS.forEach(function (a) {
      $('aba-' + a).classList.toggle('hidden', a !== aba);
    });
    document.querySelectorAll('.aba').forEach(function (b) {
      var ativa = b.dataset.aba === aba;
      b.className = 'aba px-4 py-2.5 text-sm whitespace-nowrap -mb-px border-b-2 ' +
        (ativa ? 'font-bold border-blue-600 text-blue-700' : 'font-semibold border-transparent text-slate-500 hover:text-slate-800');
      b.setAttribute('aria-selected', String(ativa));
    });
    if (aba === 'reservas') { ReservasGestao.mostrar($('aba-reservas'), E.anuncios, parametrosDeReserva()); }
    var url = new URL(location.href);
    url.searchParams.set('aba', aba);
    history.replaceState(null, '', url.pathname + url.search);
  }

  function parametrosDeReserva() {
    var p = new URLSearchParams(location.search);
    return { imovelId: p.get('imovelId'), checkin: p.get('checkin'), checkout: p.get('checkout') };
  }

  // ------------------------------------------------------------- inicialização

  /** Impressão digital do que a tela mostra: a recarga periódica só redesenha se algo mudou (não apaga o que está sendo digitado). */
  function assinatura(lista) {
    return JSON.stringify(lista.map(function (a) {
      return [a.id, a.status, a.republicarEm, a.edicaoIniciadaEm, a.podePublicar, a.dados.valorDiaria, a.totalImagens, a.totalVideos];
    }));
  }

  async function carregar() {
    try {
      var lista = await Api.get('/api/gestor/imoveis');
      var mudou = assinatura(lista) !== assinatura(E.anuncios);
      E.anuncios = lista;
      if (lista.length) { E.relogio = UI.relogioDoServidor(lista[0].agora); }
      if (mudou || !E.desenhou) { E.desenhou = true; renderTudo(); }
      if (E.aba === 'reservas') { ReservasGestao.atualizarImoveis(E.anuncios); }
    } catch (e) {
      UI.toast('Não foi possível carregar os anúncios: ' + e.message, 'erro');
    }
  }

  function mostrarErro(html) {
    $('carregando').classList.add('hidden');
    var e = $('erroPagina');
    e.innerHTML = html;
    e.classList.remove('hidden');
  }

  async function iniciar() {
    if (!Auth.isAuthenticated()) { location.href = Api.urlDeLogin(Auth.rotaAtual()); return; }
    if (!Auth.isGestor()) {
      mostrarErro('O painel é exclusivo para gestores de imóveis. Você tem acesso ao catálogo: <a class="underline" href="/imoveis.html">ver imóveis</a>.');
      return;
    }

    var p = new URLSearchParams(location.search);
    E.destaque = p.get('imovel') ? Number(p.get('imovel')) : (p.get('novo') ? Number(p.get('novo')) : null);
    var abaPedida = p.get('aba') || (p.get('imovel') ? 'anuncios' : 'pre');

    $('carregando').classList.add('hidden');
    $('painel').classList.remove('hidden');
    await carregar();
    mostrarAba(p.get('imovelId') && !p.get('aba') ? 'reservas' : abaPedida);
    if (E.destaque) { rolarAteDestaque(); }
    if (p.get('novo')) { UI.toast('Anúncio criado! Defina o preço para iniciar a janela de pré-publicação.'); }

    document.querySelectorAll('.aba').forEach(function (b) { b.addEventListener('click', function () { mostrarAba(b.dataset.aba); }); });
    ['aba-pre', 'aba-anuncios'].forEach(function (id) { $(id).addEventListener('click', aoClicarAcao); });
    $('btnSino').addEventListener('click', function () {
      var aberto = $('menuSino').classList.toggle('hidden') === false;
      $('btnSino').setAttribute('aria-expanded', String(aberto));
    });
    $('listaNotificacoes').addEventListener('click', aoClicarNotificacao);

    carregarNotificacoes();
    setInterval(atualizarContagens, 1000);
    setInterval(function () { carregar(); carregarNotificacoes(); }, 60000);
  }

  document.addEventListener('DOMContentLoaded', iniciar);
})(window);
