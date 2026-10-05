/**
 * SmartChat: lista de conversas à esquerda, conversa à direita (no celular, duas
 * telas com botão de voltar). O front só renderiza: o filtro de conteúdo roda no
 * servidor, e o texto que chega aqui já vem com os trechos sensíveis trocados por
 * marcadores sobre texto de preenchimento. O borrão é desenhado sobre esse
 * preenchimento (nunca sobre o texto real) e não há como "revelar" o trecho.
 * Tempo quase real por SSE (ticket de uso único), com polling como alternativa.
 */
(function (global) {
  'use strict';

  var ABRE = '', FECHA = '';
  var ROTULO_CAT = { T: 'Telefone ocultado', E: 'E-mail ocultado', L: 'Link externo ocultado', S: 'Conteúdo impróprio ocultado', O: 'Conteúdo ofensivo ocultado' };
  var AVISO_FILTRO = 'Alguns trechos foram ocultados por conterem telefone, link externo ou conteúdo impróprio. Use o SmartChat para tratar tudo sobre a reserva.';
  var AVISO_FRAUDE = 'Esta mensagem menciona pagamento ou contato fora da plataforma. Pagamentos feitos por fora não têm proteção.';
  var STATUS_RESERVA = { PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada', CONCLUIDA: 'Concluída', CANCELADA_COM_REEMBOLSO: 'Cancelada', CANCELADA_SEM_REEMBOLSO: 'Cancelada', CANCELADA_PELO_GESTOR: 'Cancelada' };
  var MOTIVOS = [
    ['ASSEDIO_OFENSAS', 'Assédio ou ofensas'], ['SPAM', 'Spam'], ['TENTATIVA_DE_GOLPE', 'Tentativa de golpe'],
    ['CONTEUDO_IMPROPRIO', 'Conteúdo impróprio'], ['CONTATO_EXTERNO', 'Tentativa de levar a conversa para fora da plataforma'], ['OUTRO', 'Outro']];

  var S = { conversas: [], atual: null, mensagens: [], ultimoId: 0, filtro: '', es: null, poll: null, reconexao: null, vista: 'lista', imoveisFiltro: {} };

  function $(id) { return document.getElementById(id); }
  function esc(t) { return UI.escapar(t); }

  // ------------------------------------------------------------------ apresentação

  /** "14:05" em horário de Brasília. */
  function hora(iso) { return UI.horaMinuto(UI.instante(iso)); }

  function diaDe(iso) { return UI.hojeBrasilia(UI.instante(iso)); }

  function rotuloDia(iso) {
    var d = diaDe(iso), hoje = UI.hojeBrasilia(), ontem = UI.hojeBrasilia(Date.now() - 86400000);
    if (d === hoje) { return 'Hoje'; }
    if (d === ontem) { return 'Ontem'; }
    var p = d.split('-');
    return p[2] + '/' + p[1] + '/' + p[0];
  }

  function horaOuDia(iso) { return diaDe(iso) === UI.hojeBrasilia() ? hora(iso) : rotuloDia(iso); }

  /** Escapa o texto e troca cada marcador por um borrão (sem expor nada além do preenchimento). */
  function renderTexto(t) {
    var re = new RegExp(ABRE + '(.)\\uE001([^' + FECHA + ']*)' + FECHA, 'g');
    var out = '', ult = 0, m;
    while ((m = re.exec(t)) !== null) {
      out += esc(t.slice(ult, m.index));
      var rotulo = ROTULO_CAT[m[1]] || 'Conteúdo ocultado';
      out += '<span class="borrao" role="img" aria-label="' + esc(rotulo) + '" title="' + esc(rotulo) + '">' + esc(m[2]) + '</span>';
      ult = m.index + m[0].length;
    }
    return (out + esc(t.slice(ult))).replace(/\n/g, '<br>');
  }

  function chipReserva(r) {
    if (!r) { return ''; }
    var p1 = r.dataCheckin.split('-'), p2 = r.dataCheckout.split('-');
    return (STATUS_RESERVA[r.status] || r.status) + ' · ' + p1[2] + '/' + p1[1] + ' a ' + p2[2] + '/' + p2[1];
  }

  function classeReserva(r) {
    if (!r) { return ''; }
    return /^CANCELADA/.test(r.status) ? 'bg-rose-50 text-rose-700 border-rose-200'
      : r.status === 'PENDENTE' ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200';
  }

  /** Conteudo do avatar: foto quando existir (URL do proprio servidor), senao as iniciais. */
  function avatarConteudo(i) {
    if (i.fotoUrl && /^\/api\/perfil\/foto\//.test(i.fotoUrl)) {
      return '<img src="' + esc(i.fotoUrl) + '" alt="" class="w-full h-full object-cover" onerror="this.replaceWith(document.createTextNode(this.dataset.i))" data-i="' + esc(i.iniciais) + '">';
    }
    return esc(i.iniciais);
  }

  // ------------------------------------------------------------------ lista

  function itemLista(c) {
    var ativa = S.atual && S.atual.id === c.id;
    return '<li><button type="button" data-conversa="' + c.id + '" class="w-full text-left flex gap-3 p-3 hover:bg-slate-50 ' + (ativa ? 'bg-blue-50' : '') + '">' +
      '<span class="w-11 h-11 rounded-full bg-blue-100 text-blue-700 font-bold text-sm flex items-center justify-center shrink-0 overflow-hidden">' + avatarConteudo(c.interlocutor) + '</span>' +
      '<span class="min-w-0 flex-1">' +
        '<span class="flex items-baseline justify-between gap-2"><span class="font-bold text-sm text-slate-900 truncate">' + esc(c.interlocutor.nome) + '</span>' +
          '<span class="text-[10px] ' + (c.naoLidas ? 'text-blue-700 font-bold' : 'text-slate-400') + ' shrink-0">' + (c.ultimaMensagemEm ? horaOuDia(c.ultimaMensagemEm) : '') + '</span></span>' +
        '<span class="block text-[10px] text-slate-500 truncate">' + esc(c.imovel.codigo) + ' · ' + esc(c.imovel.titulo) + '</span>' +
        '<span class="flex items-center justify-between gap-2 mt-0.5"><span class="text-xs text-slate-500 truncate">' + esc(c.ultimaMensagem || 'Sem mensagens ainda') + '</span>' +
          (c.naoLidas ? '<span class="min-w-[1.25rem] h-5 px-1.5 rounded-full bg-blue-600 text-white text-[10px] font-bold flex items-center justify-center" aria-label="' + c.naoLidas + ' não lidas">' + c.naoLidas + '</span>' : '') + '</span>' +
      '</span></button></li>';
  }

  function renderLista() {
    $('listaCarregando').classList.add('hidden');
    var lista = S.conversas;
    $('listaVazia').classList.toggle('hidden', lista.length > 0);
    if (!lista.length) {
      $('listaVazia').innerText = Auth.isGestor() ? 'Nenhuma conversa nos seus imóveis ainda.' : 'Você ainda não iniciou nenhuma conversa.';
    }
    $('lista').innerHTML = lista.map(itemLista).join('');
    if (Auth.isGestor()) {
      var sel = $('selImovel'), atual = sel.value;
      var ids = {};
      S.conversas.forEach(function (c) { ids[c.imovel.id] = c.imovel; });
      Object.keys(S.imoveisFiltro).forEach(function (k) { ids[k] = S.imoveisFiltro[k]; });
      S.imoveisFiltro = ids;
      sel.innerHTML = '<option value="">Todos os imóveis</option>' + Object.keys(ids).sort().map(function (k) {
        return '<option value="' + k + '">' + esc(ids[k].codigo) + ' — ' + esc(ids[k].titulo) + '</option>'; }).join('');
      sel.value = atual;
      $('filtroImovel').classList.remove('hidden');
    }
  }

  async function carregarLista() {
    try {
      S.conversas = await Api.get('/api/smartchat/conversas' + (S.filtro ? '?imovelId=' + S.filtro : ''));
      $('listaErro').classList.add('hidden');
      renderLista();
      if (S.atual) { var c = S.conversas.filter(function (x) { return x.id === S.atual.id; })[0]; if (c) { S.atual = c; } }
    } catch (e) {
      $('listaCarregando').classList.add('hidden');
      $('listaErro').innerText = 'Não foi possível carregar as conversas: ' + e.message;
      $('listaErro').classList.remove('hidden');
    }
  }

  // ------------------------------------------------------------------ conversa

  function mostrarVista(v) {
    S.vista = v;
    var mobile = global.innerWidth < 768;
    $('colLista').classList.toggle('hidden', mobile && v !== 'lista');
    $('colConversa').classList.toggle('hidden', mobile && v === 'lista');
    $('colConversa').classList.toggle('flex', !(mobile && v === 'lista'));
    var perfil = $('colPerfil');
    var aberto = v === 'perfil' || (!mobile && S.perfilAberto);
    perfil.classList.toggle('hidden', !aberto);
    perfil.classList.toggle('flex', aberto);
  }

  function cabecalho() {
    var c = S.atual;
    $('cabAvatar').innerHTML = avatarConteudo(c.interlocutor);
    $('cabNome').innerText = c.interlocutor.nome;
    $('cabImovel').innerText = c.imovel.codigo + ' · ' + c.imovel.titulo;
    var chip = $('cabReserva'), mob = $('cabReservaMobile');
    if (c.reserva) {
      chip.className = 'hidden sm:inline-block text-[10px] font-bold px-2.5 py-1 rounded-full border ' + classeReserva(c.reserva);
      chip.innerText = chipReserva(c.reserva);
      chip.classList.remove('hidden');
      mob.innerText = 'Reserva: ' + chipReserva(c.reserva);
      mob.classList.remove('hidden');
    } else { chip.classList.add('hidden'); mob.classList.add('hidden'); }
  }

  function bolha(m, anterior) {
    var html = '';
    if (!anterior || diaDe(anterior.criadaEm) !== diaDe(m.criadaEm)) {
      html += '<div class="text-center my-2"><span class="text-[10px] font-semibold text-slate-500 bg-white border border-slate-200 rounded-full px-3 py-1">' + rotuloDia(m.criadaEm) + '</span></div>';
    }
    if (m.tipo === 'SISTEMA') {
      return html + '<div class="flex justify-center"><div class="max-w-[85%] bg-amber-50 border border-amber-200 text-amber-900 rounded-xl px-3 py-2 text-xs text-center">' +
        '<p class="text-[10px] font-bold uppercase tracking-wide text-amber-700 mb-0.5">Mensagem da plataforma</p>' + renderTexto(m.texto) +
        '<p class="text-[10px] text-amber-700/70 mt-1">' + hora(m.criadaEm) + '</p></div></div>';
    }
    var minha = m.minha;
    return html + '<div class="flex ' + (minha ? 'justify-end' : 'justify-start') + '"><div class="max-w-[80%]">' +
      '<div class="rounded-2xl px-3.5 py-2 text-sm leading-snug break-words ' + (minha ? 'bg-blue-600 text-white rounded-br-md' : 'bg-white border border-slate-200 text-slate-800 rounded-bl-md') + '">' +
        renderTexto(m.texto) +
        '<span class="block text-[10px] mt-1 text-right ' + (minha ? 'text-blue-100' : 'text-slate-400') + '">' + hora(m.criadaEm) +
          (minha ? ' <span aria-label="' + (m.lida ? 'Lida' : 'Enviada') + '">' + (m.lida ? '✓✓' : '✓') + '</span>' : '') + '</span></div>' +
      (minha && m.ocorrencias > 0 ? '<p class="text-[10px] italic text-slate-500 mt-1 text-right">' + esc(AVISO_FILTRO) + '</p>' : '') +
      (!minha && m.suspeitaFraude ? '<p role="note" class="mt-1 text-[11px] font-semibold text-red-800 bg-red-50 border border-red-200 rounded-lg px-2.5 py-1.5">' + esc(AVISO_FRAUDE) + '</p>' : '') +
      '</div></div>';
  }

  function renderMensagens(rolar) {
    var caixa = $('mensagens');
    var noFim = caixa.scrollHeight - caixa.scrollTop - caixa.clientHeight < 80;
    caixa.innerHTML = S.mensagens.length
      ? S.mensagens.map(function (m, i) { return bolha(m, S.mensagens[i - 1]); }).join('')
      : '<p class="text-center text-xs text-slate-400 mt-8">Nenhuma mensagem ainda. Diga olá!</p>';
    if (rolar || noFim) { caixa.scrollTop = caixa.scrollHeight; }
  }

  async function carregarMensagens(todas) {
    if (!S.atual) { return; }
    var id = S.atual.id;
    try {
      var novas = await Api.get('/api/smartchat/conversas/' + id + '/mensagens?depoisDe=' + (todas ? 0 : S.ultimoId));
      if (!S.atual || S.atual.id !== id) { return; }
      S.mensagens = todas ? novas : S.mensagens.concat(novas);
      if (S.mensagens.length) { S.ultimoId = S.mensagens[S.mensagens.length - 1].id; }
      renderMensagens(todas);
      if (novas.some(function (m) { return !m.minha; })) { marcarLidas(); }
    } catch (e) {
      UI.toast('Não foi possível carregar as mensagens: ' + e.message, 'erro');
    }
  }

  async function marcarLidas() {
    if (!S.atual) { return; }
    try {
      await Api.post('/api/smartchat/conversas/' + S.atual.id + '/lidas', {});
      var c = S.conversas.filter(function (x) { return x.id === S.atual.id; })[0];
      if (c && c.naoLidas) { c.naoLidas = 0; renderLista(); }
      if (global.Navegacao) { Navegacao.atualizarContador(); }
    } catch (e) { /* silencioso: tenta de novo na próxima atualização */ }
  }

  async function abrirConversa(id) {
    var c = S.conversas.filter(function (x) { return x.id === id; })[0];
    if (!c) {
      try { c = await Api.get('/api/smartchat/conversas/' + id); } catch (e) { UI.toast(e.message, 'erro'); return; }
    }
    S.atual = c; S.mensagens = []; S.ultimoId = 0; S.perfilAberto = false;
    $('semConversa').classList.add('hidden');
    $('conversa').classList.remove('hidden');
    cabecalho();
    renderLista();
    mostrarVista('conversa');
    try { var u = new URL(location.href); u.searchParams.delete('imovel'); u.searchParams.delete('reserva'); u.searchParams.set('conversa', id); history.replaceState(null, '', u.toString()); } catch (e) { /* url opcional */ }
    await carregarMensagens(true);
    $('texto').focus();
  }

  // ------------------------------------------------------------------ envio

  async function enviar(ev) {
    ev.preventDefault();
    var campo = $('texto'), texto = campo.value.trim();
    if (!texto || !S.atual) { return; }
    var btn = $('btnEnviar');
    btn.disabled = true;
    try {
      var r = await Api.post('/api/smartchat/conversas/' + S.atual.id + '/mensagens', { texto: texto });
      campo.value = '';
      campo.style.height = 'auto';
      atualizarContador();
      S.mensagens.push(r.mensagem);
      S.ultimoId = r.mensagem.id;
      renderMensagens(true);
      var aviso = $('avisoEnvio');
      if (r.aviso) { aviso.innerText = r.aviso; aviso.classList.remove('hidden'); } else { aviso.classList.add('hidden'); }
      carregarLista();
    } catch (e) {
      UI.toast(e.message, 'erro');
    } finally { btn.disabled = false; campo.focus(); }
  }

  function atualizarContador() {
    var n = $('texto').value.length;
    $('contador').innerText = n > 800 ? n + '/1000' : '';
  }

  // ------------------------------------------------------------------ perfil, denúncia e bloqueio

  async function abrirPerfil() {
    if (!S.atual) { return; }
    var corpo = $('perfilCorpo');
    corpo.innerHTML = '<p class="text-xs text-slate-500">Carregando...</p>';
    S.perfilAberto = true;
    mostrarVista('perfil');
    try {
      var p = await Api.get('/api/smartchat/conversas/' + S.atual.id + '/perfil');
      corpo.innerHTML =
        '<div class="flex flex-col items-center text-center">' +
          '<span class="w-16 h-16 rounded-full bg-blue-100 text-blue-700 font-bold text-xl flex items-center justify-center overflow-hidden">' + avatarConteudo(p.interlocutor) + '</span>' +
          '<p class="font-bold text-slate-900 mt-2">' + esc(p.interlocutor.nome) + '</p>' +
          '<p class="text-xs text-slate-500">' + esc(p.interlocutor.papel) + '</p></div>' +
        '<dl class="text-xs space-y-2">' +
          '<div><dt class="text-slate-500">Imóvel</dt><dd class="font-semibold">' + esc(p.imovel.codigo) + ' · ' + esc(p.imovel.titulo) + '</dd></div>' +
          (p.reserva ? '<div><dt class="text-slate-500">Reserva</dt><dd class="font-semibold">' + esc(chipReserva(p.reserva)) + ' · ' + p.reserva.numeroHospedes + ' hóspede(s)</dd></div>' : '') +
        '</dl>' +
        '<p class="text-[10px] text-slate-400">Por segurança, e-mail, telefone e outros contatos não são exibidos. Use o SmartChat para tratar tudo sobre a reserva.</p>' +
        '<div class="space-y-2 pt-2 border-t border-slate-100">' +
          '<button type="button" data-perfil="denunciar" class="w-full border border-rose-300 text-rose-700 hover:bg-rose-50 font-bold py-2 rounded-xl text-xs">Denunciar</button>' +
          '<button type="button" data-perfil="bloquear" class="w-full border border-slate-300 text-slate-700 hover:bg-slate-50 font-bold py-2 rounded-xl text-xs">Bloquear</button></div>';
    } catch (e) { corpo.innerHTML = '<p class="text-xs text-rose-700 font-semibold">' + esc(e.message) + '</p>'; }
  }

  function fecharPerfil() { S.perfilAberto = false; mostrarVista('conversa'); }

  function denunciar() {
    var anexaveis = S.mensagens.filter(function (m) { return m.tipo === 'NORMAL' && !m.minha; }).slice(-10);
    var m = UI.modal({
      titulo: 'Denunciar ' + S.atual.interlocutor.nome, largura: 'max-w-lg',
      corpo: '<label class="block mb-3"><span class="text-xs font-semibold text-slate-700">Motivo</span>' +
        '<select id="dn-motivo" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"><option value="">Escolha...</option>' +
        MOTIVOS.map(function (x) { return '<option value="' + x[0] + '">' + esc(x[1]) + '</option>'; }).join('') + '</select></label>' +
        '<label class="block mb-3"><span class="text-xs font-semibold text-slate-700">Descrição <span class="font-normal text-slate-400">(opcional)</span></span>' +
        '<textarea id="dn-desc" rows="3" maxlength="1000" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"></textarea></label>' +
        (anexaveis.length ? '<fieldset class="mb-2"><legend class="text-xs font-semibold text-slate-700 mb-1">Anexar mensagens (opcional)</legend><div class="space-y-1 max-h-40 overflow-y-auto">' +
          anexaveis.map(function (x) {
            return '<label class="flex items-start gap-2 text-xs"><input type="checkbox" class="dn-msg mt-0.5" value="' + x.id + '"><span>' + esc(hora(x.criadaEm)) + ' — ' + renderTexto(x.texto).replace(/<br>/g, ' ') + '</span></label>'; }).join('') + '</div></fieldset>' : '') +
        '<p class="text-[10px] text-slate-400">A denúncia fica registrada para análise e não é visível para a outra pessoa.</p>' +
        '<p id="dn-erro" class="hidden mt-2 text-xs font-semibold text-rose-700"></p>',
      botoes: [
        { texto: 'Cancelar', classe: UI.BTN_NEUTRO, aoClicar: function (fechar) { fechar(); } },
        { texto: 'Enviar denúncia', classe: UI.BTN_PERIGO, aoClicar: async function (fechar, el, btn) {
            var motivo = el.querySelector('#dn-motivo').value, erro = el.querySelector('#dn-erro');
            if (!motivo) { erro.innerText = 'Escolha o motivo da denúncia.'; erro.classList.remove('hidden'); return; }
            btn.disabled = true;
            try {
              var ids = Array.prototype.slice.call(el.querySelectorAll('.dn-msg:checked')).map(function (c) { return Number(c.value); });
              var r = await Api.post('/api/smartchat/conversas/' + S.atual.id + '/denuncias', { motivo: motivo, descricao: el.querySelector('#dn-desc').value.trim() || null, mensagensIds: ids });
              fechar();
              UI.toast(r.mensagem);
            } catch (e) { erro.innerText = e.message; erro.classList.remove('hidden'); btn.disabled = false; }
          } }
      ]
    });
    m.el.addEventListener('click', function (e) { if (e.target === m.el) { m.fechar(); } });
  }

  async function bloquear() {
    var ok = await UI.confirmar({
      titulo: 'Bloquear ' + S.atual.interlocutor.nome + '?',
      corpoHtml: '<p class="text-sm">Sua solicitação de bloqueio será registrada para análise.</p>' +
        '<p class="text-xs text-slate-500 mt-2">Nesta versão o registro ainda não impede o envio ou o recebimento de mensagens nesta conversa.</p>',
      confirmarTexto: 'Registrar solicitação'
    });
    if (!ok) { return; }
    try {
      var r = await Api.post('/api/smartchat/conversas/' + S.atual.id + '/bloqueio', {});
      UI.toast(r.mensagem);
    } catch (e) { UI.toast(e.message, 'erro'); }
  }

  // ------------------------------------------------------------------ tempo real

  function aoEvento(e) {
    var d = {};
    try { d = JSON.parse(e.data); } catch (x) { /* evento sem dados */ }
    carregarLista();
    if (S.atual && d.conversaId === S.atual.id) { carregarMensagens(false); }
    if (global.Navegacao) { Navegacao.atualizarContador(); }
  }

  function aoLida(e) {
    var d = {};
    try { d = JSON.parse(e.data); } catch (x) { /* ignora */ }
    if (S.atual && d.conversaId === S.atual.id) { carregarMensagens(true); }
  }

  function iniciarPolling() {
    if (S.poll) { return; }
    S.poll = setInterval(function () {
      carregarLista();
      if (S.atual) { carregarMensagens(true); }
    }, 5000);
  }

  function pararPolling() { if (S.poll) { clearInterval(S.poll); S.poll = null; } }

  async function conectar() {
    clearTimeout(S.reconexao);
    try {
      var t = await Api.post('/api/smartchat/stream-ticket', {});
      if (S.es) { S.es.close(); }
      var es = new EventSource('/api/smartchat/stream?ticket=' + encodeURIComponent(t.ticket));
      S.es = es;
      es.addEventListener('pronto', function () { pararPolling(); });
      es.addEventListener('mensagem', aoEvento);
      es.addEventListener('lida', aoLida);
      es.onerror = function () { es.close(); iniciarPolling(); S.reconexao = setTimeout(conectar, 15000); };
    } catch (e) {
      iniciarPolling();
      S.reconexao = setTimeout(conectar, 15000);
    }
  }

  // ------------------------------------------------------------------ início

  async function abrirPeloEndereco() {
    var p = new URLSearchParams(location.search);
    try {
      if (p.get('imovel')) {
        var c1 = await Api.post('/api/smartchat/conversas/por-imovel/' + Number(p.get('imovel')), {});
        await carregarLista();
        return abrirConversa(c1.id);
      }
      if (p.get('reserva')) {
        var c2 = await Api.post('/api/smartchat/conversas/por-reserva/' + Number(p.get('reserva')), {});
        await carregarLista();
        return abrirConversa(c2.id);
      }
    } catch (e) { UI.toast(e.message, 'erro'); }
    if (p.get('conversa')) { return abrirConversa(Number(p.get('conversa'))); }
    mostrarVista('lista');
  }

  async function iniciar() {
    if (!global.Auth || !Auth.isAuthenticated()) {
      location.replace('/login.html?redirectTo=' + encodeURIComponent(location.pathname + location.search));
      return;
    }
    $('lista').addEventListener('click', function (e) {
      var b = e.target.closest('[data-conversa]');
      if (b) { abrirConversa(Number(b.dataset.conversa)); }
    });
    $('selImovel').addEventListener('change', function () { S.filtro = this.value; carregarLista(); });
    $('btnVoltar').addEventListener('click', function () { S.atual = null; $('conversa').classList.add('hidden'); $('semConversa').classList.remove('hidden'); mostrarVista('lista'); renderLista(); });
    $('btnPerfil').addEventListener('click', abrirPerfil);
    $('btnFecharPerfil').addEventListener('click', fecharPerfil);
    $('perfilCorpo').addEventListener('click', function (e) {
      var b = e.target.closest('[data-perfil]');
      if (!b) { return; }
      if (b.dataset.perfil === 'denunciar') { denunciar(); } else { bloquear(); }
    });
    $('form').addEventListener('submit', enviar);
    var campo = $('texto');
    campo.addEventListener('keydown', function (e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('form').requestSubmit(); } });
    campo.addEventListener('input', function () { campo.style.height = 'auto'; campo.style.height = Math.min(campo.scrollHeight, 128) + 'px'; atualizarContador(); });
    global.addEventListener('resize', function () { mostrarVista(S.vista); });

    await carregarLista();
    await abrirPeloEndereco();
    conectar();
    if (global.lucide) { global.lucide.createIcons(); }
  }

  document.addEventListener('DOMContentLoaded', iniciar);
})(window);
