/**
 * Área de moderação (somente ADMIN): denúncias, decisões automáticas do sistema, contas suspensas, avisos enviados e
 * histórico de ações. Os dados vêm prontos de /api/admin/moderacao/**; o servidor autoriza e valida. Todo texto de usuário
 * entra escapado (esc) e as ações que mudam algo exigem justificativa.
 */
(function (global) {
  'use strict';

  var esc = global.UI.escapar;
  var CAMPO = 'w-full border border-slate-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500';
  var graficos = {};
  var estado = { denuncias: 0, alertas: 0, contas: 0, avisos: 0, historico: 0 };
  var abaAtual = null;

  var ROTULO = {
    PENDENTE: 'Pendente', PROCEDENTE: 'Procedente', IMPROCEDENTE: 'Improcedente', ABERTO: 'Em aberto', REVISADO: 'Confirmado',
    DESCARTADO: 'Descartado', CLIENTE: 'Cliente', ANFITRIAO: 'Anfitrião', ADMIN: 'Admin',
    ASSEDIO_OFENSAS: 'Assédio ou ofensas', SPAM: 'Spam', TENTATIVA_DE_GOLPE: 'Tentativa de golpe',
    CONTEUDO_IMPROPRIO: 'Conteúdo impróprio', CONTATO_EXTERNO: 'Contato externo', OUTRO: 'Outro',
    ENVIO_EM_MASSA: 'Envio em massa', SUSPEITA_FRAUDE: 'Suspeita de fraude',
    SUSPENSAO: 'Suspensão', REATIVACAO: 'Reativação', MENSAGEM: 'Mensagem enviada', DENUNCIA_PROCEDENTE: 'Denúncia procedente',
    DENUNCIA_IMPROCEDENTE: 'Denúncia improcedente', DENUNCIA_ABERTA: 'Denúncia aberta', ALERTA_REVISADO: 'Alerta confirmado',
    ALERTA_DESCARTADO: 'Alerta descartado'
  };
  var COR_STATUS = {
    PENDENTE: 'bg-amber-100 text-amber-800', PROCEDENTE: 'bg-rose-100 text-rose-800', IMPROCEDENTE: 'bg-slate-100 text-slate-700',
    ABERTO: 'bg-amber-100 text-amber-800', REVISADO: 'bg-blue-100 text-blue-800', DESCARTADO: 'bg-slate-100 text-slate-700'
  };

  var ABAS = [
    { id: 'denuncias', titulo: 'Denúncias', icone: 'flag', carregar: function () { return carregarLista('denuncias'); } },
    { id: 'auto', titulo: 'Decisões automáticas', icone: 'bot', carregar: carregarAuto },
    { id: 'contas', titulo: 'Contas suspensas', icone: 'user-x', carregar: function () { return carregarLista('contas'); } },
    { id: 'avisos', titulo: 'Avisos enviados', icone: 'send', carregar: function () { return carregarLista('avisos'); } },
    { id: 'historico', titulo: 'Histórico', icone: 'history', carregar: function () { return carregarLista('historico'); } }
  ];

  function $(id) { return document.getElementById(id); }
  function num(n) { return Number(n || 0).toLocaleString('pt-BR'); }
  function rot(c) { return ROTULO[c] || c; }
  function quando(iso) {
    if (!iso) { return '—'; }
    var d = new Date(iso);
    return isNaN(d) ? esc(iso) : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }
  function selo(status) {
    return '<span class="inline-block px-2 py-0.5 rounded-full text-[10px] font-bold ' + (COR_STATUS[status] || 'bg-slate-100 text-slate-700') + '">' + esc(rot(status)) + '</span>';
  }
  function pessoa(u) {
    return '<span class="font-semibold text-slate-800">' + esc(u.nome) + '</span> <span class="text-[10px] text-slate-400">' + esc(rot(u.papel)) +
      (u.ativo ? '' : ' • suspensa') + '</span>';
  }
  function mostrarErro(msg) { var e = $('erroPagina'); e.innerText = msg || ''; e.classList.toggle('hidden', !msg); }
  function cartao(titulo, valor, nota) {
    return '<div class="bg-white border border-slate-200 rounded-2xl shadow-sm p-4"><p class="text-[11px] font-semibold text-slate-500">' + esc(titulo) +
      '</p><p class="kpi-valor text-xl font-extrabold text-slate-900 mt-1">' + valor + '</p>' + (nota ? '<p class="text-[10px] text-slate-400 mt-0.5">' + nota + '</p>' : '') + '</div>';
  }
  function tabela(cabecalho, linhas, vazio) {
    if (!linhas.length) { return '<p class="p-6 text-xs text-slate-400">' + (vazio || 'Nada por aqui.') + '</p>'; }
    return '<table class="w-full text-xs"><thead class="bg-slate-50 text-slate-500 text-left"><tr>' +
      cabecalho.map(function (t) { return '<th class="px-3 py-2 font-semibold">' + esc(t) + '</th>'; }).join('') +
      '</tr></thead><tbody class="divide-y divide-slate-100 text-slate-700 align-top">' + linhas.join('') + '</tbody></table>';
  }
  function paginador(el, r) {
    $(el).innerHTML = '<span>' + num(r.total) + ' registro(s) • página ' + (r.pagina + 1) + ' de ' + Math.max(1, r.totalPaginas) + '</span>' +
      '<span class="flex gap-2"><button type="button" data-pg="-1" class="px-3 py-1.5 rounded-lg border border-slate-300 font-semibold disabled:opacity-40"' + (r.pagina <= 0 ? ' disabled' : '') + '>Anterior</button>' +
      '<button type="button" data-pg="1" class="px-3 py-1.5 rounded-lg border border-slate-300 font-semibold disabled:opacity-40"' + (r.pagina + 1 >= r.totalPaginas ? ' disabled' : '') + '>Próxima</button></span>';
  }
  var BTN = 'text-[11px] font-bold hover:underline ';

  // ------------------------------------------------------------ listas paginadas

  var LISTAS = {
    denuncias: {
      pag: 'pagDenuncias', alvo: 'tabDenuncias',
      url: function () { return '/api/admin/moderacao/denuncias?tamanho=15&pagina=' + estado.denuncias + '&status=' + encodeURIComponent($('filtroDenuncia').value); },
      linhas: function (r) {
        return tabela(['#', 'Motivo', 'Denunciante → denunciado', 'Quando', 'Situação', 'Contra o denunciado', ''], r.itens.map(function (d) {
          return '<tr><td class="px-3 py-2 text-slate-400">' + d.id + '</td><td class="px-3 py-2 font-semibold">' + esc(rot(d.motivo)) +
            (d.descricao ? '<div class="font-normal text-slate-500 max-w-xs">' + esc(d.descricao) + '</div>' : '') + '</td>' +
            '<td class="px-3 py-2">' + pessoa(d.denunciante) + ' <span class="text-slate-400">→</span> ' + pessoa(d.denunciado) + '</td>' +
            '<td class="px-3 py-2 whitespace-nowrap">' + quando(d.criadaEm) + '</td><td class="px-3 py-2">' + selo(d.status) + '</td>' +
            '<td class="px-3 py-2">' + num(d.denunciasContra) + ' denúncia(s)</td>' +
            '<td class="px-3 py-2 text-right"><button type="button" data-denuncia="' + d.id + '" class="' + BTN + 'text-blue-700">' + (d.status === 'PENDENTE' ? 'Analisar' : 'Ver') + '</button></td></tr>';
        }), 'Nenhuma denúncia nesta situação.');
      }
    },
    contas: {
      pag: 'pagContas', alvo: 'tabContas',
      url: function () { return '/api/admin/moderacao/contas-suspensas?tamanho=15&pagina=' + estado.contas; },
      linhas: function (r) {
        return tabela(['Conta', 'Motivo da suspensão', 'Por', 'Desde', ''], r.itens.map(function (c, i) {
          return '<tr><td class="px-3 py-2">' + pessoa(c.usuario) + '<div class="text-[10px] text-slate-400">' + esc(c.usuario.email) + '</div></td>' +
            '<td class="px-3 py-2 max-w-sm">' + esc(c.motivo || '—') + '</td><td class="px-3 py-2">' + esc(c.por || '—') + '</td>' +
            '<td class="px-3 py-2 whitespace-nowrap">' + quando(c.desde) + '</td><td class="px-3 py-2 text-right whitespace-nowrap">' +
            '<button type="button" data-i="' + i + '" data-acao="reativar" class="' + BTN + 'text-emerald-700">Reativar</button> ' +
            '<button type="button" data-i="' + i + '" data-acao="mensagem" class="' + BTN + 'text-blue-700 ml-2">Mensagem</button></td></tr>';
        }), 'Nenhuma conta suspensa.');
      }
    },
    avisos: {
      pag: 'pagAvisos', alvo: 'tabAvisos',
      url: function () { return '/api/admin/moderacao/comunicados?tamanho=15&pagina=' + estado.avisos; },
      linhas: function (r) {
        return tabela(['Quando', 'Para', 'Como', 'Assunto e mensagem', 'Enviado por', 'Lido'], r.itens.map(function (c) {
          return '<tr><td class="px-3 py-2 whitespace-nowrap">' + quando(c.criadoEm) + '</td><td class="px-3 py-2">' + pessoa(c.destinatario) + '</td>' +
            '<td class="px-3 py-2">' + esc(c.nivel) + '</td><td class="px-3 py-2 max-w-md"><b>' + esc(c.assunto) + '</b><div class="text-slate-500 whitespace-pre-line">' + esc(c.texto) + '</div></td>' +
            '<td class="px-3 py-2">' + esc(c.admin) + '</td><td class="px-3 py-2">' + (c.lidoEm ? quando(c.lidoEm) : '<span class="text-amber-700">Não lido</span>') + '</td></tr>';
        }), 'Nenhum aviso enviado ainda.');
      }
    },
    historico: {
      pag: 'pagHistorico', alvo: 'tabHistorico',
      url: function () { return '/api/admin/moderacao/acoes?tamanho=20&pagina=' + estado.historico; },
      linhas: function (r) {
        return tabela(['Quando', 'Ação', 'Conta', 'Por', 'Justificativa', 'Ref.'], r.itens.map(function (a) {
          return '<tr><td class="px-3 py-2 whitespace-nowrap">' + quando(a.criadoEm) + '</td><td class="px-3 py-2 font-semibold">' + esc(rot(a.tipo)) + '</td>' +
            '<td class="px-3 py-2">' + pessoa(a.usuario) + '</td><td class="px-3 py-2">' + esc(a.admin) + '</td><td class="px-3 py-2 max-w-md">' + esc(a.motivo || '—') + '</td>' +
            '<td class="px-3 py-2 text-slate-400">' + (a.denunciaId ? 'denúncia #' + a.denunciaId : (a.alertaId ? 'alerta #' + a.alertaId : '')) + '</td></tr>';
        }), 'Nenhuma ação registrada ainda.');
      }
    }
  };
  var dadosContas = [];

  async function carregarLista(nome) {
    var c = LISTAS[nome];
    var r = await Api.get(c.url());
    if (nome === 'contas') { dadosContas = r.itens.map(function (x) { return x.usuario; }); }
    $(c.alvo).innerHTML = c.linhas(r);
    paginador(c.pag, r);
  }

  function ligarPaginacao(nome) {
    var c = LISTAS[nome];
    $(c.pag).addEventListener('click', function (e) {
      var b = e.target.closest('[data-pg]');
      if (!b) { return; }
      estado[nome] = Math.max(0, estado[nome] + Number(b.getAttribute('data-pg')));
      executar(function () { return carregarLista(nome); });
    });
  }

  // ------------------------------------------------------------ denúncias: detalhe e decisão

  async function abrirDenuncia(id) {
    var d = await Api.get('/api/admin/moderacao/denuncias/' + id);
    var r = d.resumo;
    var pendente = r.status === 'PENDENTE';
    var evid = d.evidencias.length ? d.evidencias.map(function (m) {
      return '<li class="py-2"><div class="text-[10px] text-slate-400">' + esc(m.autor) + ' • ' + quando(m.criadaEm) + '</div><div class="whitespace-pre-line">' + esc(m.texto) + '</div></li>';
    }).join('') : '<li class="py-2 text-slate-400">A pessoa não anexou mensagens. Evidências do chat só aparecem quando anexadas pelo denunciante.</li>';
    var alertas = d.alertasDoDenunciado.length ? d.alertasDoDenunciado.map(function (a) {
      return '<li class="py-1">' + esc(rot(a.tipo)) + ' ' + selo(a.status) + ' <span class="text-slate-400">' + quando(a.criadoEm) + '</span></li>';
    }).join('') : '<li class="py-1 text-slate-400">Nenhum alerta automático.</li>';
    var hist = d.historicoDoDenunciado.length ? d.historicoDoDenunciado.map(function (a) {
      return '<li class="py-1">' + esc(rot(a.tipo)) + ' <span class="text-slate-400">' + quando(a.criadoEm) + ' • ' + esc(a.admin) + '</span>' + (a.motivo ? '<div class="text-slate-500">' + esc(a.motivo) + '</div>' : '') + '</li>';
    }).join('') : '<li class="py-1 text-slate-400">Sem ações anteriores.</li>';

    var corpo =
      '<div class="space-y-4 text-xs">' +
      '<div class="grid sm:grid-cols-2 gap-3"><div><p class="text-slate-400">Denunciante</p>' + pessoa(r.denunciante) + '<div class="text-[10px] text-slate-400">' + esc(r.denunciante.email) + '</div></div>' +
      '<div><p class="text-slate-400">Denunciado</p>' + pessoa(r.denunciado) + '<div class="text-[10px] text-slate-400">' + esc(r.denunciado.email) + '</div></div></div>' +
      '<div><p class="text-slate-400">Motivo</p><b>' + esc(rot(r.motivo)) + '</b> ' + selo(r.status) + ' <span class="text-slate-400">• anúncio: ' + esc(d.imovel) + ' • ' + quando(r.criadaEm) + '</span>' +
      (r.descricao ? '<div class="mt-1 whitespace-pre-line bg-slate-50 border border-slate-200 rounded-lg p-2">' + esc(r.descricao) + '</div>' : '') + '</div>' +
      '<div><p class="text-slate-400">Mensagens anexadas (como o destinatário as viu, com trechos ocultados)</p><ul class="divide-y divide-slate-100 bg-slate-50 border border-slate-200 rounded-lg px-2">' + evid + '</ul></div>' +
      '<div class="grid sm:grid-cols-2 gap-3"><div><p class="text-slate-400">Contexto do denunciado</p><p>' + num(r.denunciasContra) + ' denúncia(s) no total, ' + num(d.denunciasProcedentesContra) + ' procedente(s).</p>' +
      '<ul class="mt-1">' + alertas + '</ul></div><div><p class="text-slate-400">Ações anteriores sobre a conta</p><ul>' + hist + '</ul></div></div>' +
      (d.decisao ? '<div class="p-3 rounded-lg bg-slate-50 border border-slate-200"><p class="text-slate-400">Decisão</p><b>' + esc(rot(d.decisao.tipo)) + '</b> por ' + esc(d.decisao.admin) + ' em ' + quando(d.decisao.quando) +
        '<div class="whitespace-pre-line">' + esc(d.decisao.nota || '') + '</div></div>' : '') +
      (pendente ? '<fieldset class="border border-slate-200 rounded-lg p-3 space-y-2"><legend class="px-1 font-bold text-slate-700">Decisão</legend>' +
        '<div class="flex gap-4"><label class="flex items-center gap-1.5"><input type="radio" name="dec" value="PROCEDENTE" data-dec> Procedente</label>' +
        '<label class="flex items-center gap-1.5"><input type="radio" name="dec" value="IMPROCEDENTE" data-dec> Improcedente</label></div>' +
        '<label class="block font-semibold text-slate-700">Justificativa (fica no histórico)<textarea data-nota rows="3" maxlength="400" class="mt-1 ' + CAMPO + '"></textarea></label>' +
        '<label class="flex items-center gap-2"><input type="checkbox" data-suspender disabled> Suspender a conta do denunciado <span class="text-slate-400">(só se procedente)</span></label>' +
        '<label class="flex items-center gap-2"><input type="checkbox" data-av-denunciante checked> Avisar o denunciante do resultado</label>' +
        '<label class="flex items-center gap-2"><input type="checkbox" data-av-denunciado disabled> Avisar o denunciado <span class="text-slate-400">(só se procedente)</span></label></fieldset>' : '') +
      '<p data-erro class="hidden text-rose-700 font-semibold" role="alert"></p></div>';

    var botoes = [{ texto: 'Fechar', classe: 'px-4 py-2 rounded-xl text-xs font-bold border border-slate-300 text-slate-700 hover:bg-slate-50', aoClicar: function (fechar) { fechar(); } },
      { texto: 'Mensagem ao denunciado', classe: 'px-4 py-2 rounded-xl text-xs font-bold border border-blue-300 text-blue-700 hover:bg-blue-50',
        aoClicar: function () { ModAcoes.mensagem(r.denunciado, { nivel: 'MODERACAO' }); } }];
    if (pendente) {
      botoes.push({ texto: 'Registrar decisão', classe: 'px-4 py-2 rounded-xl text-xs font-bold text-white bg-slate-900 hover:bg-slate-700',
        aoClicar: async function (fechar, el) {
          var erro = el.querySelector('[data-erro]');
          var marcada = el.querySelector('[data-dec]:checked');
          var nota = el.querySelector('[data-nota]').value.trim();
          var msg = !marcada ? 'Escolha procedente ou improcedente.' : (nota.length < 5 ? 'Escreva a justificativa (mínimo de 5 caracteres).' : null);
          if (msg) { erro.textContent = msg; erro.classList.remove('hidden'); return; }
          var corpoReq = { decisao: marcada.value, nota: nota, suspenderDenunciado: el.querySelector('[data-suspender]').checked,
                           avisarDenunciante: el.querySelector('[data-av-denunciante]').checked, avisarDenunciado: el.querySelector('[data-av-denunciado]').checked };
          try {
            await Api.post('/api/admin/moderacao/denuncias/' + id + '/decisao', corpoReq);
            fechar();
            UI.toast('Decisão registrada.');
            executar(function () { return carregarLista('denuncias'); });
          } catch (e) { erro.textContent = e.message; erro.classList.remove('hidden'); }
        } });
    }
    var m = UI.modal({ titulo: 'Denúncia #' + id, largura: 'max-w-3xl', corpo: corpo, botoes: botoes });
    m.el.querySelectorAll('[data-dec]').forEach(function (rd) {
      rd.addEventListener('change', function () {
        var proc = m.el.querySelector('[data-dec]:checked').value === 'PROCEDENTE';
        m.el.querySelector('[data-suspender]').disabled = !proc;
        m.el.querySelector('[data-av-denunciado]').disabled = !proc;
        if (!proc) { m.el.querySelector('[data-suspender]').checked = false; m.el.querySelector('[data-av-denunciado]').checked = false; }
      });
    });
  }

  // ------------------------------------------------------------ decisões automáticas

  function grafico(id, tipo, rotulos, series) {
    if (graficos[id]) { graficos[id].destroy(); }
    graficos[id] = new Chart($(id), { type: tipo, data: { labels: rotulos, datasets: series },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } },
                 scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } } });
  }

  var alertasAtuais = [];
  async function carregarAuto() {
    var q = '?tamanho=10&pagina=' + estado.alertas + '&dias=' + $('diasAuto').value + '&status=' + encodeURIComponent($('filtroAlerta').value);
    var r = await Api.get('/api/admin/moderacao/automatizadas' + q);
    var f = r.filtro;
    var pct = f.mensagens ? (f.comAcaoDoFiltro / f.mensagens * 100).toFixed(1).replace('.', ',') + '%' : '0%';
    $('kpisAuto').innerHTML =
      cartao('Alertas em aberto', num(r.alertasAbertos), 'aguardando revisão de um admin') +
      cartao('Restrições de envio ativas', num(r.limitesAtivos), 'nesta página de alertas') +
      cartao('Mensagens analisadas', num(f.mensagens), 'últimos ' + f.dias + ' dias') +
      cartao('Tratadas pelo filtro', num(f.comAcaoDoFiltro), pct + ' das mensagens');
    alertasAtuais = r.alertas;
    $('tabAlertas').innerHTML = tabela(['Alerta', 'Conta', 'O que o sistema decidiu', 'Situação', ''], r.alertas.map(function (a, i) {
      return '<tr><td class="px-3 py-2"><b>' + esc(rot(a.tipo)) + '</b> <span class="text-slate-400">#' + a.id + '</span><div class="text-slate-500 max-w-xs">' + esc(a.explicacao) + '</div>' +
        '<div class="text-[10px] text-slate-400">' + quando(a.criadoEm) + '</div></td><td class="px-3 py-2">' + pessoa(a.usuario) + '</td>' +
        '<td class="px-3 py-2 max-w-xs"><span class="' + (a.efeitoAtivo ? 'text-amber-800 font-semibold' : 'text-slate-600') + '">' + esc(a.efeito) + '</span></td>' +
        '<td class="px-3 py-2">' + selo(a.status) + (a.revisao ? '<div class="text-[10px] text-slate-500 mt-1 max-w-[12rem]">' + esc(a.revisao.admin) + ': ' + esc(a.revisao.nota || '') + '</div>' : '') + '</td>' +
        '<td class="px-3 py-2 text-right whitespace-nowrap">' + (a.status === 'ABERTO'
          ? '<button type="button" data-alerta="' + i + '" data-dec="REVISADO" class="' + BTN + 'text-blue-700">Confirmar</button> ' +
            '<button type="button" data-alerta="' + i + '" data-dec="DESCARTADO" class="' + BTN + 'text-slate-700 ml-2">Descartar</button> ' : '') +
        '<button type="button" data-alerta="' + i + '" data-dec="MENSAGEM" class="' + BTN + 'text-blue-700 ml-2">Mensagem</button> ' +
        (a.usuario.ativo && a.usuario.papel !== 'ADMIN' ? '<button type="button" data-alerta="' + i + '" data-dec="SUSPENDER" class="' + BTN + 'text-rose-700 ml-2">Suspender</button>' : '') + '</td></tr>';
    }), 'Nenhum alerta nesta situação.');
    $('pagAlertas').innerHTML = '<span>Página ' + (estado.alertas + 1) + '</span><span class="flex gap-2">' +
      '<button type="button" data-pg="-1" class="px-3 py-1.5 rounded-lg border border-slate-300 font-semibold disabled:opacity-40"' + (estado.alertas <= 0 ? ' disabled' : '') + '>Anterior</button>' +
      '<button type="button" data-pg="1" class="px-3 py-1.5 rounded-lg border border-slate-300 font-semibold disabled:opacity-40"' + (r.alertas.length < 10 ? ' disabled' : '') + '>Próxima</button></span>';

    grafico('grFiltroDia', 'bar', f.porDia.map(function (d) { var p = d.dia.split('-'); return p[2] + '/' + p[1]; }),
      [{ data: f.porDia.map(function (d) { return d.total; }), backgroundColor: '#2563eb' }]);
    var max = Math.max.apply(null, f.porCategoria.map(function (c) { return c.total; }).concat([1]));
    $('barrasCategorias').innerHTML = f.porCategoria.map(function (c) {
      return '<div class="flex items-center gap-2 text-[11px]"><span class="w-44 text-slate-600 truncate" title="' + esc(c.rotulo) + '">' + esc(c.rotulo) + '</span>' +
        '<span class="flex-1 h-3 rounded bg-slate-100"><span class="block h-3 rounded bg-amber-500" style="width:' + Math.round(c.total / max * 100) + '%"></span></span><b class="w-10 text-right">' + num(c.total) + '</b></div>';
    }).join('');
    $('tabAutores').innerHTML = f.autoresMaisFiltrados.length ? tabela(['Usuário', 'Mensagens tratadas', ''], f.autoresMaisFiltrados.map(function (a, i) {
      return '<tr><td class="px-3 py-1.5 font-semibold">' + esc(a.nome) + '</td><td class="px-3 py-1.5">' + num(a.total) +
        '</td><td class="px-3 py-1.5 text-right"><button type="button" data-autor="' + i + '" class="' + BTN + 'text-blue-700">Mensagem</button></td></tr>';
    }), '') : '<p class="text-xs text-slate-400">Nenhuma mensagem tratada no período.</p>';
    autoresAtuais = f.autoresMaisFiltrados;
  }
  var autoresAtuais = [];

  async function decidirAlerta(a, decisao) {
    var tit = decisao === 'DESCARTADO' ? 'Descartar alerta (falso positivo)' : 'Confirmar alerta';
    var dados = await ModAcoes.formulario({
      titulo: tit, confirmarTexto: decisao === 'DESCARTADO' ? 'Descartar' : 'Confirmar',
      corpo: '<p class="mb-3">' + (decisao === 'DESCARTADO'
        ? 'Descartar remove a restrição de envio que o sistema aplicou a ' + esc(a.usuario.nome) + '.'
        : 'Confirmar mantém a restrição de envio até o fim do prazo e marca o alerta como analisado.') + '</p>' +
        '<label class="block text-xs font-semibold text-slate-700">Justificativa (fica no histórico)<textarea data-nota rows="3" maxlength="400" class="mt-1 ' + CAMPO + '"></textarea></label>',
      coletar: function (el) { return el.querySelector('[data-nota]').value.trim(); },
      validar: function (v) { return v.length < 5 ? 'Escreva a justificativa (mínimo de 5 caracteres).' : null; }
    });
    if (dados === null) { return; }
    await executar(async function () {
      await Api.post('/api/admin/moderacao/alertas/' + a.id + '/revisao', { decisao: decisao, nota: dados });
      UI.toast('Alerta ' + (decisao === 'DESCARTADO' ? 'descartado.' : 'confirmado.'));
      await carregarAuto();
    });
  }

  // ------------------------------------------------------------ abas e início

  async function executar(fn) {
    mostrarErro('');
    try { await fn(); } catch (err) {
      if (err && err.status === 403) { mostrarErro('Seu usuário não tem permissão de administrador.'); return; }
      mostrarErro('Não foi possível carregar: ' + (err && err.message ? err.message : err));
    }
  }

  function abrirAba(id) {
    var aba = ABAS.filter(function (a) { return a.id === id; })[0] || ABAS[0];
    abaAtual = aba;
    ABAS.forEach(function (a) {
      var ativa = a === aba;
      $('aba-' + a.id).classList.toggle('hidden', !ativa);
      var b = $('btn-' + a.id);
      b.setAttribute('aria-selected', ativa ? 'true' : 'false');
      b.className = 'flex items-center gap-1.5 px-3 py-2 text-xs font-semibold whitespace-nowrap border-b-2 -mb-px ' +
        (ativa ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-800');
    });
    try { history.replaceState(null, '', '#' + aba.id); } catch (e) { /* sem histórico */ }
    return executar(aba.carregar);
  }

  function recarregarAtual() { return abrirAba(abaAtual.id); }

  function iniciar() {
    if (!Auth.isAdmin()) {
      $('semAcesso').classList.remove('hidden');
      if (Auth.isAuthenticated()) {
        $('linkEntrar').classList.add('hidden');
        $('semAcesso').querySelector('p').textContent = 'Esta área é exclusiva de administradores.';
      }
      return;
    }
    $('painel').classList.remove('hidden');
    $('abas').innerHTML = ABAS.map(function (a) {
      return '<button type="button" role="tab" id="btn-' + a.id + '" data-aba="' + a.id + '" aria-controls="aba-' + a.id + '"><i data-lucide="' + a.icone + '" class="w-4 h-4"></i><span>' + esc(a.titulo) + '</span></button>';
    }).join('');
    if (global.lucide) { global.lucide.createIcons(); }
    $('abas').addEventListener('click', function (e) { var b = e.target.closest('[data-aba]'); if (b) { abrirAba(b.getAttribute('data-aba')); } });
    Object.keys(LISTAS).forEach(ligarPaginacao);
    $('filtroDenuncia').addEventListener('change', function () { estado.denuncias = 0; executar(function () { return carregarLista('denuncias'); }); });
    $('filtroAlerta').addEventListener('change', function () { estado.alertas = 0; executar(carregarAuto); });
    $('diasAuto').addEventListener('change', function () { executar(carregarAuto); });
    $('pagAlertas').addEventListener('click', function (e) {
      var b = e.target.closest('[data-pg]');
      if (b) { estado.alertas = Math.max(0, estado.alertas + Number(b.getAttribute('data-pg'))); executar(carregarAuto); }
    });
    $('tabDenuncias').addEventListener('click', function (e) {
      var b = e.target.closest('[data-denuncia]');
      if (b) { executar(function () { return abrirDenuncia(b.getAttribute('data-denuncia')); }); }
    });
    $('tabAlertas').addEventListener('click', async function (e) {
      var b = e.target.closest('[data-alerta]');
      if (!b) { return; }
      var a = alertasAtuais[Number(b.getAttribute('data-alerta'))], dec = b.getAttribute('data-dec');
      if (dec === 'MENSAGEM') { await ModAcoes.mensagem(a.usuario, { nivel: 'MODERACAO' }); }
      else if (dec === 'SUSPENDER') { if (await ModAcoes.suspender(a.usuario)) { executar(carregarAuto); } }
      else { await decidirAlerta(a, dec); }
    });
    $('tabAutores').addEventListener('click', function (e) {
      var b = e.target.closest('[data-autor]');
      if (!b) { return; }
      var a = autoresAtuais[Number(b.getAttribute('data-autor'))];
      ModAcoes.mensagem({ id: a.usuarioId, nome: a.nome, email: '', papel: '' }, { nivel: 'MODERACAO' });
    });
    $('tabContas').addEventListener('click', async function (e) {
      var b = e.target.closest('[data-acao]');
      if (!b) { return; }
      var u = dadosContas[Number(b.getAttribute('data-i'))];
      if (b.getAttribute('data-acao') === 'reativar') { if (await ModAcoes.reativar(u)) { recarregarAtual(); } }
      else { await ModAcoes.mensagem(u, { nivel: 'MODERACAO' }); }
    });
    $('btnMensagem').addEventListener('click', async function () {
      var u = await ModAcoes.escolherUsuario('Enviar mensagem a quem?');
      if (u && await ModAcoes.mensagem(u)) { if (abaAtual.id === 'avisos' || abaAtual.id === 'historico') { recarregarAtual(); } }
    });
    $('btnSuspender').addEventListener('click', async function () {
      var u = await ModAcoes.escolherUsuario('Suspender qual conta?');
      if (!u) { return; }
      if (u.papel === 'ADMIN') { UI.toast('Contas de administrador não podem ser suspensas por aqui.', 'erro'); return; }
      if (!u.ativo) { UI.toast('Esta conta já está suspensa.', 'aviso'); return; }
      if (await ModAcoes.suspender(u)) { recarregarAtual(); }
    });
    abrirAba((location.hash || '').replace('#', '') || 'denuncias');
  }

  document.addEventListener('DOMContentLoaded', iniciar);
})(window);
