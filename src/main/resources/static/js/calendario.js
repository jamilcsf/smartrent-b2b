/**
 * Calendário do Painel do Gestor: 3 meses lado a lado, UM imóvel por vez.
 *
 * - "Hoje" e o mês atual vêm do servidor (horário de Brasília), nunca do navegador.
 * - Cinza = reserva confirmada (popover com hóspede, e-mail, datas e SmartChat);
 *   hachurado = reserva pendente; vermelho com listras e cadeado = bloqueado pelo
 *   gestor (não depende só da cor). Dia de troca (saída e entrada) é dividido na diagonal.
 * - Bloqueio: clicar na data inicial e na final, ou "Bloquear datas". Motivo e
 *   observação são internos. As regras (conflito, concorrência) são do servidor.
 * - Popover acessível: abre com hover, foco ou toque/Enter; Esc fecha.
 */
(function (global) {
  'use strict';

  var MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
  var DIAS = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'];
  var MOTIVOS = { MANUTENCAO: 'Manutenção', USO_PROPRIO: 'Uso próprio', RESERVA_EXTERNA: 'Reserva externa', OUTRO: 'Outro' };
  var CHAVE_IMOVEL = 'smartrent.calendario.imovel';

  var C = { raiz: null, anuncios: [], imovelId: null, mes: null, hoje: null, dados: { reservas: [], bloqueios: [] },
            sel: null, opcoes: {}, popFixo: null, temporizador: null, carregando: false };

  function $(id) { return document.getElementById(id); }
  function esc(t) { return UI.escapar(t); }

  // ------------------------------------------------------------ datas (strings AAAA-MM-DD)

  function pad(n) { return n < 10 ? '0' + n : String(n); }
  function iso(a, m, d) { return a + '-' + pad(m) + '-' + pad(d); }
  function partes(s) { var p = s.split('-'); return { a: +p[0], m: +p[1], d: +p[2] }; }
  function somarDias(s, n) {
    var p = partes(s), t = new Date(Date.UTC(p.a, p.m - 1, p.d + n));
    return iso(t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate());
  }
  function diasNoMes(a, m) { return new Date(Date.UTC(a, m, 0)).getUTCDate(); }
  function diaSemana(a, m, d) { return new Date(Date.UTC(a, m - 1, d)).getUTCDay(); }
  function somarMeses(ym, n) {
    var p = ym.split('-'), t = new Date(Date.UTC(+p[0], +p[1] - 1 + n, 1));
    return t.getUTCFullYear() + '-' + pad(t.getUTCMonth() + 1);
  }
  function br(s) { var p = partes(s); return pad(p.d) + '/' + pad(p.m) + '/' + p.a; }
  function noites(ini, fim) { var a = partes(ini), b = partes(fim); return Math.round((Date.UTC(b.a, b.m - 1, b.d) - Date.UTC(a.a, a.m - 1, a.d)) / 86400000) + 1; }

  // -------------------------------------------------------------------- estado do dia

  function estadoDoDia(d) {
    var e = { noite: null, saida: null, bloqueio: null };
    C.dados.reservas.forEach(function (r) {
      if (r.dataCheckin <= d && d < r.dataCheckout) { e.noite = r; }
      if (r.dataCheckout === d) { e.saida = r; }
    });
    C.dados.bloqueios.forEach(function (b) { if (b.dataInicio <= d && d <= b.dataFim) { e.bloqueio = b; } });
    return e;
  }

  var CADEADO = '<svg class="w-3 h-3 absolute top-0.5 right-0.5 text-rose-700" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>';

  function celula(a, m, d) {
    var data = iso(a, m, d), e = estadoDoDia(data);
    var passado = data < C.hoje;
    var cls = 'cal-dia relative h-9 w-full rounded-md text-[11px] font-semibold flex items-center justify-center focus:outline-none focus:ring-2 focus:ring-blue-500 ';
    var estilo = '', rotulo = pad(d) + ' de ' + MESES[m - 1] + ' de ' + a, extra = '';
    var dentroSel = C.sel && C.sel.ini && ((C.sel.fim ? (data >= C.sel.ini && data <= C.sel.fim) : data === C.sel.ini));

    if (e.bloqueio) {
      cls += 'text-rose-900 border border-rose-300 ';
      estilo = 'background: repeating-linear-gradient(45deg,#fecaca,#fecaca 4px,#fee2e2 4px,#fee2e2 8px);';
      rotulo += ', bloqueado (' + (MOTIVOS[e.bloqueio.motivo] || 'bloqueio') + ')';
      extra = CADEADO;
    } else if (e.noite && e.saida) {
      cls += 'text-white ';
      estilo = 'background: linear-gradient(135deg,#94a3b8 50%,#64748b 50%);';
      rotulo += ', dia de troca: check-out de ' + e.saida.hospedeNome + ' e check-in de ' + e.noite.hospedeNome;
    } else if (e.noite) {
      if (e.noite.status === 'PENDENTE') {
        cls += 'text-slate-700 border border-dashed border-slate-400 ';
        estilo = 'background: repeating-linear-gradient(135deg,#e2e8f0,#e2e8f0 3px,#f8fafc 3px,#f8fafc 6px);';
        rotulo += ', reserva pendente de ' + e.noite.hospedeNome;
      } else {
        cls += 'text-white bg-slate-500 ';
        rotulo += ', reservado: ' + e.noite.hospedeNome + ', ' + e.noite.numeroHospedes + ' hóspede(s)';
      }
    } else if (e.saida) {
      cls += 'text-slate-700 border border-slate-200 ';
      estilo = 'background: linear-gradient(135deg,#94a3b8 50%,#ffffff 50%);';
      rotulo += ', check-out de ' + e.saida.hospedeNome;
    } else {
      cls += (passado ? 'text-slate-300 bg-slate-50 ' : 'text-slate-700 bg-white border border-slate-200 hover:bg-blue-50 ');
      rotulo += passado ? ', data passada' : ', livre';
    }
    if (data === C.hoje) { cls += 'ring-2 ring-blue-500 '; }
    if (dentroSel) { cls += 'outline outline-2 outline-blue-600 '; estilo += 'box-shadow: inset 0 0 0 100px rgba(37,99,235,.18);'; rotulo += ', selecionado para bloqueio'; }

    var interativo = !!(e.bloqueio || e.noite || e.saida);
    return '<button type="button" class="' + cls + '" style="' + estilo + '" data-data="' + data + '" ' +
      'aria-label="' + esc(rotulo) + '"' + (interativo ? ' aria-haspopup="dialog"' : '') + (passado && !interativo ? ' aria-disabled="true"' : '') + '>' +
      d + extra + '</button>';
  }

  function bloco(ym) {
    var p = ym.split('-'), a = +p[0], m = +p[1];
    var primeiro = diaSemana(a, m, 1), total = diasNoMes(a, m);
    var html = '<div class="bg-white border border-slate-200 rounded-2xl p-3 shadow-sm">' +
      '<h4 class="text-sm font-bold text-slate-800 text-center mb-2">' + MESES[m - 1].charAt(0).toUpperCase() + MESES[m - 1].slice(1) + ' de ' + a + '</h4>' +
      '<div class="grid grid-cols-7 gap-1 text-center text-[10px] font-bold text-slate-400 mb-1" aria-hidden="true">' +
        DIAS.map(function (x) { return '<span>' + x + '</span>'; }).join('') + '</div>' +
      '<div class="grid grid-cols-7 gap-1">';
    for (var i = 0; i < primeiro; i++) { html += '<span></span>'; }
    for (var d = 1; d <= total; d++) { html += celula(a, m, d); }
    return html + '</div></div>';
  }

  // ------------------------------------------------------------------------ montagem

  function seletor() {
    return C.anuncios.map(function (a) {
      return '<option value="' + a.id + '"' + (a.id === C.imovelId ? ' selected' : '') + '>' + esc(a.codigo) + ' — ' + esc(a.dados.titulo) + '</option>';
    }).join('');
  }

  function esqueleto() {
    C.raiz.innerHTML =
      '<section class="bg-white border border-slate-200 rounded-2xl shadow-sm p-4 sm:p-5 space-y-4" aria-label="Calendário de reservas">' +
        '<div class="flex flex-col lg:flex-row lg:items-end justify-between gap-3">' +
          '<label class="block flex-1 max-w-xl"><span class="text-[11px] font-semibold text-slate-600">Imóvel</span>' +
            '<select id="cal-imovel" class="mt-1 block w-full bg-slate-50 border border-slate-300 rounded-xl p-2.5 text-xs font-semibold text-slate-800">' + seletor() + '</select></label>' +
          '<div class="flex flex-wrap items-center gap-2">' +
            '<button type="button" id="cal-ant" class="px-3 py-2 rounded-xl text-xs font-bold border border-slate-300 hover:bg-slate-50" aria-label="Voltar 3 meses">‹ 3 meses</button>' +
            '<button type="button" id="cal-hoje" class="px-3 py-2 rounded-xl text-xs font-bold border border-slate-300 hover:bg-slate-50">Hoje</button>' +
            '<button type="button" id="cal-prox" class="px-3 py-2 rounded-xl text-xs font-bold border border-slate-300 hover:bg-slate-50" aria-label="Avançar 3 meses">3 meses ›</button>' +
            '<button type="button" id="cal-bloquear" class="px-3 py-2 rounded-xl text-xs font-bold bg-rose-600 hover:bg-rose-700 text-white flex items-center gap-1.5">' + '<span aria-hidden="true">🔒</span> Bloquear datas</button>' +
          '</div>' +
        '</div>' +
        '<ul class="flex flex-wrap gap-x-5 gap-y-1.5 text-[11px] text-slate-600" aria-label="Legenda">' +
          '<li class="flex items-center gap-1.5"><span class="inline-block w-4 h-4 rounded bg-slate-500"></span> Reserva confirmada</li>' +
          '<li class="flex items-center gap-1.5"><span class="inline-block w-4 h-4 rounded border border-dashed border-slate-400" style="background: repeating-linear-gradient(135deg,#e2e8f0,#e2e8f0 3px,#f8fafc 3px,#f8fafc 6px)"></span> Reserva pendente (aguardando pagamento)</li>' +
          '<li class="flex items-center gap-1.5"><span class="inline-block w-4 h-4 rounded border border-rose-300" style="background: repeating-linear-gradient(45deg,#fecaca,#fecaca 4px,#fee2e2 4px,#fee2e2 8px)"></span> Bloqueado por você (🔒)</li>' +
          '<li class="flex items-center gap-1.5"><span class="inline-block w-4 h-4 rounded border border-slate-200 bg-white"></span> Livre</li>' +
          '<li class="flex items-center gap-1.5"><span class="inline-block w-4 h-4 rounded" style="background: linear-gradient(135deg,#94a3b8 50%,#fff 50%)"></span> Dia de troca (saída/entrada)</li>' +
        '</ul>' +
        '<p id="cal-dica" class="text-[11px] text-slate-500">Clique na data inicial e na final de um período livre para bloqueá-lo. Passe o mouse (ou toque) em uma data cinza ou vermelha para ver os detalhes. Datas em horário de Brasília.</p>' +
        '<p id="cal-erro" class="hidden p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs font-semibold text-rose-700"></p>' +
        '<div id="cal-meses" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3" aria-live="polite"></div>' +
      '</section>';
    $('cal-imovel').addEventListener('change', function () {
      C.imovelId = Number(this.value);
      guardarImovel();
      C.sel = null;
      recarregar();
    });
    $('cal-ant').addEventListener('click', function () { C.mes = somarMeses(C.mes, -3); recarregar(); });
    $('cal-prox').addEventListener('click', function () { C.mes = somarMeses(C.mes, 3); recarregar(); });
    $('cal-hoje').addEventListener('click', function () { C.mes = C.hoje.slice(0, 7); recarregar(); });
    $('cal-bloquear').addEventListener('click', function () { abrirBloqueio(C.sel && C.sel.fim ? C.sel.ini : null, C.sel && C.sel.fim ? C.sel.fim : null, true); });
    var meses = $('cal-meses');
    meses.addEventListener('click', aoClicarDia);
    meses.addEventListener('mouseover', aoEntrar);
    meses.addEventListener('mouseout', aoSair);
    meses.addEventListener('focusin', aoEntrar);
  }

  function desenhar() {
    var html = '';
    for (var i = 0; i < 3; i++) { html += bloco(somarMeses(C.mes, i)); }
    $('cal-meses').innerHTML = html;
  }

  function intervalo() {
    var ini = C.mes + '-01', fimMes = somarMeses(C.mes, 2).split('-');
    return { de: ini, ate: iso(+fimMes[0], +fimMes[1], diasNoMes(+fimMes[0], +fimMes[1])) };
  }

  function erro(msg) {
    var p = $('cal-erro');
    if (!p) { return; }
    p.innerText = msg || '';
    p.classList.toggle('hidden', !msg);
  }

  async function recarregar() {
    if (C.carregando) { return; }
    C.carregando = true;
    fecharPopover(true);
    erro('');
    try {
      var r = intervalo();
      var cal = await Api.get('/api/gestor/calendario?imovelId=' + C.imovelId + '&de=' + r.de + '&ate=' + r.ate);
      C.dados = { reservas: cal.reservas, bloqueios: cal.bloqueios };
      C.hoje = cal.hoje; // horário de Brasília, decidido pelo servidor
      desenhar();
    } catch (e) {
      erro('Não foi possível carregar o calendário: ' + e.message);
      C.dados = { reservas: [], bloqueios: [] };
      desenhar();
    } finally {
      C.carregando = false;
    }
  }

  function guardarImovel() { try { localStorage.setItem(CHAVE_IMOVEL, String(C.imovelId)); } catch (e) { /* sem armazenamento: segue sem lembrar */ } }
  function lembrado() { try { return Number(localStorage.getItem(CHAVE_IMOVEL)) || null; } catch (e) { return null; } }

  // ------------------------------------------------------------------------- popover

  function criarPopover(celulaEl, html) {
    fecharPopover(true);
    var pop = document.createElement('div');
    pop.id = 'cal-pop';
    pop.setAttribute('role', 'dialog');
    pop.setAttribute('aria-label', 'Detalhes da data');
    pop.className = 'fixed z-[80] w-72 bg-white border border-slate-200 rounded-xl shadow-xl p-3 text-xs text-slate-700';
    pop.innerHTML = html;
    document.body.appendChild(pop);
    var r = celulaEl.getBoundingClientRect(), w = pop.offsetWidth, h = pop.offsetHeight;
    var left = Math.min(Math.max(8, r.left + r.width / 2 - w / 2), window.innerWidth - w - 8);
    var top = r.bottom + 6 + h > window.innerHeight ? Math.max(8, r.top - h - 6) : r.bottom + 6;
    pop.style.left = left + 'px';
    pop.style.top = top + 'px';
    pop.addEventListener('mouseenter', function () { clearTimeout(C.temporizador); });
    pop.addEventListener('mouseleave', function () { if (!C.popFixo) { fecharPopover(false); } });
    pop.addEventListener('click', aoClicarPopover);
    return pop;
  }

  function fecharPopover(imediato) {
    clearTimeout(C.temporizador);
    var fechar = function () { var p = $('cal-pop'); if (p) { p.remove(); } C.popFixo = null; };
    if (imediato) { fechar(); } else { C.temporizador = setTimeout(function () { if (!C.popFixo) { fechar(); } }, 180); }
  }

  function htmlReserva(r, saida) {
    var pend = r.status === 'PENDENTE';
    var chat = r.chatDisponivel
      ? '<a href="/smartchat.html?reserva=' + r.id + '" class="mt-2 flex items-center justify-center gap-1.5 w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 rounded-lg text-xs">Abrir conversa no SmartChat</a>'
      : '<p class="mt-2 text-[10px] text-slate-500">Este hóspede não tem conta na plataforma: não há conversa no SmartChat.</p>';
    return '<p class="font-bold text-slate-900 text-sm">' + esc(r.hospedeNome) + '</p>' +
      '<span class="inline-block mt-0.5 px-2 py-0.5 rounded-full text-[10px] font-bold ' + (pend ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-slate-100 text-slate-700 border border-slate-200') + '">' +
        (pend ? 'Pendente (aguardando pagamento)' : (r.status === 'CONCLUIDA' ? 'Concluída' : 'Confirmada')) + '</span>' +
      (saida ? '<p class="mt-1 text-[10px] text-slate-500">Check-out neste dia.</p>' : '') +
      '<dl class="mt-2 space-y-0.5">' +
        '<div class="flex justify-between gap-2"><dt class="text-slate-500">Hóspedes</dt><dd class="font-semibold">' + r.numeroHospedes + '</dd></div>' +
        '<div class="flex justify-between gap-2"><dt class="text-slate-500">E-mail</dt><dd class="font-semibold break-all text-right">' + esc(r.hospedeEmail) + '</dd></div>' +
        '<div class="flex justify-between gap-2"><dt class="text-slate-500">Check-in</dt><dd class="font-semibold">' + br(r.dataCheckin) + '</dd></div>' +
        '<div class="flex justify-between gap-2"><dt class="text-slate-500">Check-out</dt><dd class="font-semibold">' + br(r.dataCheckout) + '</dd></div>' +
      '</dl>' + chat +
      '<button type="button" data-cal="cancelar-reserva" data-id="' + r.id + '" class="mt-2 w-full text-rose-700 hover:bg-rose-50 border border-rose-200 font-semibold py-1.5 rounded-lg">Cancelar reserva</button>';
  }

  function htmlBloqueio(b) {
    return '<p class="font-bold text-rose-800 text-sm flex items-center gap-1.5"><span aria-hidden="true">🔒</span> Bloqueado</p>' +
      '<dl class="mt-2 space-y-0.5">' +
        '<div class="flex justify-between gap-2"><dt class="text-slate-500">Período</dt><dd class="font-semibold">' + br(b.dataInicio) + ' a ' + br(b.dataFim) + '</dd></div>' +
        '<div class="flex justify-between gap-2"><dt class="text-slate-500">Motivo</dt><dd class="font-semibold">' + esc(MOTIVOS[b.motivo] || b.motivo) + '</dd></div>' +
        (b.observacao ? '<div><dt class="text-slate-500">Observação</dt><dd class="font-semibold whitespace-pre-line">' + esc(b.observacao) + '</dd></div>' : '') +
      '</dl><p class="mt-1 text-[10px] text-slate-400">Motivo e observação são internos: clientes veem apenas "indisponível".</p>' +
      '<div class="mt-2 grid grid-cols-2 gap-2">' +
        '<button type="button" data-cal="remover-parte" data-id="' + b.id + '" class="border border-slate-300 hover:bg-slate-50 font-semibold py-1.5 rounded-lg">Remover parte</button>' +
        '<button type="button" data-cal="remover-bloqueio" data-id="' + b.id + '" class="bg-rose-600 hover:bg-rose-700 text-white font-bold py-1.5 rounded-lg">Remover bloqueio</button>' +
      '</div>';
  }

  function mostrarPopover(el, fixar) {
    var data = el.dataset.data, e = estadoDoDia(data);
    var html = e.bloqueio ? htmlBloqueio(e.bloqueio) : (e.noite ? htmlReserva(e.noite, false) : (e.saida ? htmlReserva(e.saida, true) : null));
    if (!html) { return false; }
    criarPopover(el, html);
    C.popFixo = fixar ? data : null;
    return true;
  }

  function aoEntrar(ev) {
    var el = ev.target.closest('.cal-dia');
    if (!el || C.popFixo) { return; }
    clearTimeout(C.temporizador);
    mostrarPopover(el, false);
  }
  function aoSair(ev) {
    if (!ev.target.closest('.cal-dia')) { return; }
    if (!C.popFixo) { fecharPopover(false); }
  }

  async function aoClicarPopover(ev) {
    var b = ev.target.closest('[data-cal]');
    if (!b) { return; }
    var id = Number(b.dataset.id), acao = b.dataset.cal;
    fecharPopover(true);
    if (acao === 'cancelar-reserva' && C.opcoes.aoCancelarReserva) { C.opcoes.aoCancelarReserva(id); }
    var bl = C.dados.bloqueios.filter(function (x) { return x.id === id; })[0];
    if (!bl) { return; }
    if (acao === 'remover-bloqueio') { removerBloqueio(bl); }
    if (acao === 'remover-parte') { removerParte(bl); }
  }

  // ------------------------------------------------------------------- clique no dia

  function aoClicarDia(ev) {
    var el = ev.target.closest('.cal-dia');
    if (!el) { return; }
    var data = el.dataset.data, e = estadoDoDia(data);
    if (e.bloqueio || e.noite || e.saida) { // toque/clique/Enter: abre e fixa o popover
      if (C.popFixo === data) { fecharPopover(true); } else { mostrarPopover(el, true); }
      return;
    }
    fecharPopover(true);
    if (data < C.hoje) { return; }
    if (!C.sel || C.sel.fim) {
      C.sel = { ini: data, fim: null };
      desenhar();
      $('cal-dica').innerText = 'Início em ' + br(data) + '. Clique na data final do bloqueio (ou na mesma data para um dia só).';
    } else {
      var ini = C.sel.ini <= data ? C.sel.ini : data, fim = C.sel.ini <= data ? data : C.sel.ini;
      C.sel = { ini: ini, fim: fim };
      desenhar();
      abrirBloqueio(ini, fim, false);
    }
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && (C.popFixo || $('cal-pop'))) { fecharPopover(true); }
  });
  document.addEventListener('click', function (e) {
    if ($('cal-pop') && !e.target.closest('#cal-pop') && !e.target.closest('.cal-dia')) { fecharPopover(true); }
  });

  // --------------------------------------------------------------------- bloquear datas

  function abrirBloqueio(ini, fim, datasEditaveis) {
    var temDatas = ini && fim;
    var corpo =
      (datasEditaveis || !temDatas
        ? '<div class="grid grid-cols-2 gap-3 mb-3">' +
            '<label class="block"><span class="text-xs font-semibold text-slate-700">Data inicial</span><input type="date" id="bq-ini" min="' + C.hoje + '" value="' + (ini || '') + '" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"></label>' +
            '<label class="block"><span class="text-xs font-semibold text-slate-700">Data final (inclusive)</span><input type="date" id="bq-fim" min="' + C.hoje + '" value="' + (fim || '') + '" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"></label>' +
          '</div>'
        : '<p class="mb-3 p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs"><b>Período:</b> ' + br(ini) + ' a ' + br(fim) + ' (' + noites(ini, fim) + (noites(ini, fim) === 1 ? ' noite' : ' noites') + ')</p>') +
      '<label class="block mb-3"><span class="text-xs font-semibold text-slate-700">Motivo</span>' +
        '<select id="bq-motivo" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs">' +
          Object.keys(MOTIVOS).map(function (k) { return '<option value="' + k + '">' + MOTIVOS[k] + '</option>'; }).join('') + '</select></label>' +
      '<label class="block"><span class="text-xs font-semibold text-slate-700">Observação <span class="font-normal text-slate-400">(opcional)</span></span>' +
        '<textarea id="bq-obs" rows="3" maxlength="500" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"></textarea></label>' +
      '<p class="mt-2 text-[10px] text-slate-400">Motivo e observação são internos e nunca aparecem para clientes. As datas ficam indisponíveis para novas reservas.</p>' +
      '<p id="bq-erro" class="hidden mt-2 text-xs font-semibold text-rose-700"></p>';
    var m = UI.modal({
      titulo: 'Bloquear datas', corpo: corpo,
      botoes: [
        { texto: 'Cancelar', classe: UI.BTN_NEUTRO, aoClicar: function (fechar) { C.sel = null; desenhar(); fechar(); } },
        { texto: 'Bloquear', classe: UI.BTN_PERIGO, aoClicar: async function (fechar, el, btn) {
            var campoIni = el.querySelector('#bq-ini'), campoFim = el.querySelector('#bq-fim');
            var corpoReq = {
              dataInicio: campoIni ? campoIni.value : ini, dataFim: campoFim ? campoFim.value : fim,
              motivo: el.querySelector('#bq-motivo').value, observacao: el.querySelector('#bq-obs').value.trim() || null,
              apenasLivres: false, cancelarPendentes: false
            };
            var p = el.querySelector('#bq-erro');
            if (!corpoReq.dataInicio || !corpoReq.dataFim) { p.innerText = 'Informe a data inicial e a final.'; p.classList.remove('hidden'); return; }
            if (corpoReq.dataFim < corpoReq.dataInicio) { p.innerText = 'A data final não pode ser anterior à inicial.'; p.classList.remove('hidden'); return; }
            btn.disabled = true;
            try {
              var ok = await enviarBloqueio(corpoReq);
              if (ok) { fechar(); }
            } catch (e) {
              p.innerText = e.message; p.classList.remove('hidden');
            } finally { btn.disabled = false; }
          } }
      ]
    });
    m.el.addEventListener('click', function (e) { if (e.target === m.el) { C.sel = null; desenhar(); m.fechar(); } });
  }

  /** Envia o bloqueio tratando as duas confirmações que o servidor pode pedir. Devolve true se criou. */
  async function enviarBloqueio(corpo) {
    try {
      await Api.post('/api/gestor/imoveis/' + C.imovelId + '/bloqueios', corpo);
    } catch (e) {
      var codigo = e.body && e.body.codigo;
      if (codigo === 'RESERVA_CONFIRMADA_NO_PERIODO') {
        var seguir = await UI.confirmar({ titulo: 'Há reserva confirmada no período', mensagem: e.message,
          confirmarTexto: 'Bloquear só as datas livres' });
        if (!seguir) { return false; }
        corpo.apenasLivres = true;
        return enviarBloqueio(corpo);
      }
      if (codigo === 'RESERVAS_PENDENTES_NO_PERIODO') {
        var cancelar = await UI.confirmar({ titulo: 'Reservas pendentes no período', perigo: true,
          mensagem: e.message + ' Deseja cancelar essas reservas pendentes (sem cobrança) e bloquear?', confirmarTexto: 'Cancelar pendentes e bloquear' });
        if (!cancelar) { return false; }
        corpo.cancelarPendentes = true;
        return enviarBloqueio(corpo);
      }
      throw e;
    }
    UI.toast('Datas bloqueadas.');
    C.sel = null;
    await recarregar();
    if (C.opcoes.aoAlterar) { C.opcoes.aoAlterar(); }
    return true;
  }

  async function removerBloqueio(b) {
    var ok = await UI.confirmar({ titulo: 'Remover bloqueio?', perigo: true, confirmarTexto: 'Remover bloqueio',
      mensagem: 'As datas de ' + br(b.dataInicio) + ' a ' + br(b.dataFim) + ' voltam a ficar disponíveis para reservas.' });
    if (!ok) { return; }
    try {
      await Api.del('/api/gestor/imoveis/' + C.imovelId + '/bloqueios/' + b.id);
      UI.toast('Bloqueio removido.');
      await recarregar();
      if (C.opcoes.aoAlterar) { C.opcoes.aoAlterar(); }
    } catch (e) { UI.toast(e.message, 'erro'); }
  }

  function removerParte(b) {
    var m = UI.modal({
      titulo: 'Remover parte do bloqueio',
      corpo: '<p class="mb-3 text-xs">Bloqueio atual: <b>' + br(b.dataInicio) + ' a ' + br(b.dataFim) + '</b>. As datas escolhidas voltam a ficar livres e o restante continua bloqueado.</p>' +
        '<div class="grid grid-cols-2 gap-3">' +
          '<label class="block"><span class="text-xs font-semibold text-slate-700">De</span><input type="date" id="rp-de" min="' + b.dataInicio + '" max="' + b.dataFim + '" value="' + b.dataInicio + '" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"></label>' +
          '<label class="block"><span class="text-xs font-semibold text-slate-700">Até (inclusive)</span><input type="date" id="rp-ate" min="' + b.dataInicio + '" max="' + b.dataFim + '" value="' + b.dataFim + '" class="mt-1 w-full border border-slate-300 rounded-lg p-2 text-xs"></label>' +
        '</div><p id="rp-erro" class="hidden mt-2 text-xs font-semibold text-rose-700"></p>',
      botoes: [
        { texto: 'Cancelar', classe: UI.BTN_NEUTRO, aoClicar: function (fechar) { fechar(); } },
        { texto: 'Remover estas datas', classe: UI.BTN_PERIGO, aoClicar: async function (fechar, el) {
            var de = el.querySelector('#rp-de').value, ate = el.querySelector('#rp-ate').value;
            var p = el.querySelector('#rp-erro');
            if (!de || !ate || ate < de || de < b.dataInicio || ate > b.dataFim) {
              p.innerText = 'Escolha datas dentro do bloqueio (' + br(b.dataInicio) + ' a ' + br(b.dataFim) + ').'; p.classList.remove('hidden'); return;
            }
            try {
              await Api.del('/api/gestor/imoveis/' + C.imovelId + '/bloqueios/' + b.id + '?de=' + de + '&ate=' + ate);
              UI.toast('Datas liberadas.');
              fechar();
              await recarregar();
              if (C.opcoes.aoAlterar) { C.opcoes.aoAlterar(); }
            } catch (e) { p.innerText = e.message; p.classList.remove('hidden'); }
          } }
      ]
    });
    m.el.addEventListener('click', function (e) { if (e.target === m.el) { m.fechar(); } });
  }

  // ------------------------------------------------------------------------ API pública

  async function montar(raiz, anuncios, opcoes) {
    C.raiz = raiz;
    C.opcoes = opcoes || {};
    C.anuncios = anuncios;
    if (!anuncios.length) {
      raiz.innerHTML = '<div class="bg-white border border-dashed border-slate-300 rounded-2xl p-8 text-center text-sm text-slate-500">' +
        'Você ainda não tem imóveis. <a href="anuncio-form.html" class="text-blue-700 font-semibold underline">Crie um anúncio</a> para ver o calendário.</div>';
      return;
    }
    if (!C.hoje) {
      try { C.hoje = (await Api.get('/api/config/agora', { ignorar401: true })).hoje; } catch (e) { C.hoje = UI.hojeBrasilia(); }
    }
    var lembrar = lembrado();
    C.imovelId = anuncios.some(function (a) { return a.id === C.imovelId; }) ? C.imovelId
      : (anuncios.some(function (a) { return a.id === lembrar; }) ? lembrar : anuncios[0].id);
    if (!C.mes) { C.mes = C.hoje.slice(0, 7); }
    esqueleto();
    await recarregar();
  }

  /** Mantém o seletor em dia quando a lista de imóveis do painel muda, sem perder a seleção. */
  function atualizarImoveis(anuncios) {
    if (!C.raiz || !$('cal-imovel')) { return; }
    C.anuncios = anuncios;
    var sel = $('cal-imovel');
    if (sel) { sel.innerHTML = seletor(); }
  }

  /** Seleciona um imóvel por fora (links do painel) e recarrega só os dados do calendário. */
  function selecionarImovel(id) {
    if (!C.raiz || !$('cal-imovel') || !C.anuncios.some(function (a) { return a.id === id; })) { return; }
    C.imovelId = id;
    guardarImovel();
    $('cal-imovel').value = String(id);
    recarregar();
  }

  global.Calendario = { montar: montar, atualizarImoveis: atualizarImoveis, selecionarImovel: selecionarImovel,
    recarregar: function () { return C.raiz && $('cal-meses') ? recarregar() : null; } };
})(window);
