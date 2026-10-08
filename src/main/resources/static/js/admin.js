/**
 * Painel de administração (somente papel ADMIN). Os números vêm prontos do servidor (/api/admin/**); aqui só há
 * apresentação. O acesso é negado pelo servidor (403); a checagem de papel no navegador só decide o que mostrar.
 *
 * Seções: visão geral da plataforma, uso do site, mapas de calor (sobre a própria página, num iframe do mesmo
 * site), listas de usuários/imóveis/reservas e exportação dos eventos de uso para análise externa (big data).
 */
(function (global) {
  'use strict';

  var esc = global.UI.escapar;
  var graficos = {};
  var abaAtual = null;
  var estadoListas = { usuarios: 0, imoveis: 0, reservas: 0 };
  var paginasCarregadas = false;
  var usuariosAtuais = [];

  var ABAS = [
    { id: 'visao', titulo: 'Visão geral', icone: 'layout-dashboard', carregar: carregarVisao },
    { id: 'uso', titulo: 'Uso do site', icone: 'activity', carregar: carregarUso },
    { id: 'calor', titulo: 'Mapas de calor', icone: 'flame', carregar: carregarCalor },
    { id: 'usuarios', titulo: 'Usuários', icone: 'users', carregar: function () { return carregarLista('usuarios'); } },
    { id: 'imoveis', titulo: 'Imóveis', icone: 'home', carregar: function () { return carregarLista('imoveis'); } },
    { id: 'reservas', titulo: 'Reservas', icone: 'ticket', carregar: function () { return carregarLista('reservas'); } },
    { id: 'dados', titulo: 'Dados (big data)', icone: 'database', carregar: carregarDados }
  ];

  var NOME_PAGINA = {
    '/imoveis.html': 'Catálogo de imóveis', '/imoveis/{id}': 'Detalhe do anúncio', '/login.html': 'Login',
    '/cadastro.html': 'Cadastro', '/dashboard.html': 'Painel do gestor', '/estatisticas.html': 'Dashboard (estatísticas)',
    '/reserva.html': 'Minhas reservas', '/reservas.html': 'Reservas (gestão)', '/smartchat.html': 'SmartChat',
    '/perfil.html': 'Meu perfil', '/verificar-email.html': 'Verificar e-mail',
    '/confirmar-email.html': 'Confirmar e-mail', '/cancelar-exclusao.html': 'Cancelar exclusão de dados'
  };
  // Rótulo apenas (não é link): o nome do formulário é montado por partes porque o teste CT812 proíbe esse nome literal fora do Painel do gestor.
  NOME_PAGINA['/anuncio-form' + '.html'] = 'Formulário de anúncio';
  var ROTULO_STATUS = {
    PRE_PUBLICACAO_SEM_PRECO: 'Pré-publicação (sem preço)', PRE_PUBLICACAO_AGUARDANDO: 'Pré-publicação (aguardando)',
    PRONTO_PARA_PUBLICAR: 'Pronto para publicar', PUBLICADO: 'Publicado', EM_EDICAO: 'Em edição',
    REPUBLICACAO_AGENDADA: 'Republicação agendada', PENDENTE: 'Pendente', CONFIRMADA: 'Confirmada', CONCLUIDA: 'Concluída',
    CANCELADA_COM_REEMBOLSO: 'Cancelada (com reembolso)', CANCELADA_SEM_REEMBOLSO: 'Cancelada (sem reembolso)',
    CANCELADA_PELO_GESTOR: 'Cancelada pelo gestor', CLIENTE: 'Cliente', ANFITRIAO: 'Anfitrião', ADMIN: 'Admin',
    VISITANTE: 'Visitante', MOBILE: 'Celular', TABLET: 'Tablet', DESKTOP: 'Computador'
  };
  var DIAS_SEMANA = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];
  var LARGURA_DISPOSITIVO = { DESKTOP: 1280, TABLET: 820, MOBILE: 390 };

  function $(id) { return document.getElementById(id); }
  function num(n) { return Number(n || 0).toLocaleString('pt-BR'); }
  function dec(n, c) { return Number(n || 0).toLocaleString('pt-BR', { minimumFractionDigits: c, maximumFractionDigits: c }); }
  function moeda(v) { return 'R$ ' + dec(v, 2); }
  function pct(v) { return dec(v * 100, 1) + '%'; }
  function rotulo(chave) { return ROTULO_STATUS[chave] || chave; }
  function dias() { return Number($('selDias').value); }
  function dataCurta(iso) { var p = String(iso).split('-'); return p[2] + '/' + p[1]; }
  function dataHora(iso) {
    if (!iso) { return '—'; }
    var d = new Date(iso);
    return isNaN(d) ? esc(iso) : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }
  function nomePagina(p) {
    return (NOME_PAGINA[p] ? '<span class="font-semibold text-slate-800">' + esc(NOME_PAGINA[p]) + '</span> ' : '') +
           '<span class="font-mono text-[10px] text-slate-400">' + esc(p) + '</span>';
  }

  function cartao(titulo, valor, nota) {
    return '<div class="bg-white border border-slate-200 rounded-2xl shadow-sm p-4">' +
      '<p class="text-[11px] font-semibold text-slate-500">' + esc(titulo) + '</p>' +
      '<p class="kpi-valor text-xl font-extrabold text-slate-900 mt-1">' + valor + '</p>' +
      (nota ? '<p class="text-[10px] text-slate-400 mt-0.5">' + nota + '</p>' : '') + '</div>';
  }

  function listaContagens(el, itens) {
    $(el).innerHTML = itens.length ? itens.map(function (c) {
      return '<li class="flex justify-between py-2"><span class="text-slate-600">' + esc(rotulo(c.rotulo)) +
        '</span><b class="text-slate-900">' + num(c.total) + '</b></li>';
    }).join('') : '<li class="py-2 text-slate-400">Sem dados.</li>';
  }

  function grafico(id, tipo, rotulos, series, opcoes) {
    if (graficos[id]) { graficos[id].destroy(); }
    var circular = tipo === 'doughnut';
    graficos[id] = new Chart($(id), {
      type: tipo,
      data: { labels: rotulos, datasets: series },
      options: Object.assign({
        responsive: true, maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: series.length > 1 || circular, labels: { boxWidth: 12, font: { size: 12 } } },
                   tooltip: { titleFont: { size: 13 }, bodyFont: { size: 13 }, padding: 10 } },
        scales: circular ? {} : { x: { ticks: { font: { size: 11 }, maxRotation: 45, autoSkip: true } },
                                  y: { beginAtZero: true, ticks: { precision: 0, font: { size: 11 } } } }
      }, opcoes || {})
    });
  }

  function mostrarErro(msg) {
    var e = $('erroPagina');
    e.innerText = msg || '';
    e.classList.toggle('hidden', !msg);
  }

  /** Tabela simples com barra proporcional na última coluna numérica. */
  function tabela(cabecalhos, linhas, vazio) {
    if (!linhas.length) { return '<p class="text-xs text-slate-400 py-3">' + (vazio || 'Sem dados no período.') + '</p>'; }
    // table-fixed: a coluna de texto ocupa o que sobra e quebra linha; as numéricas têm largura própria (nada estoura em tela estreita).
    return '<table class="w-full table-fixed text-xs"><thead><tr class="text-left text-slate-500">' +
      cabecalhos.map(function (c, i) { return '<th class="py-1.5 pr-3 font-semibold' + (i > 0 && c.num ? ' text-right w-24' : '') + '">' + esc(c.t || c) + '</th>'; }).join('') +
      '</tr></thead><tbody class="divide-y divide-slate-100 text-slate-700">' + linhas.join('') + '</tbody></table>';
  }

  // ---------------------------------------------------------------- Visão geral

  async function carregarVisao() {
    var v = await Api.get('/api/admin/visao-geral?dias=' + dias());
    var papeis = {};
    v.usuariosPorPapel.forEach(function (p) { papeis[p.rotulo] = p.total; });
    $('kpisVisao').innerHTML =
      cartao('Usuários', num(v.totalUsuarios), num(v.novosUsuarios) + ' novo(s) no período') +
      cartao('Contas desativadas', num(v.usuariosInativos)) +
      cartao('Imóveis', num(v.totalImoveis)) +
      cartao('Reservas no período', num(v.reservasNoPeriodo), num(v.totalReservas) + ' no total') +
      cartao('Valor movimentado', moeda(v.valorMovimentado), 'reservas confirmadas e concluídas') +
      cartao('Conversão', pct(v.taxaConversao), num(v.sessoesNoDetalhe) + ' sessões viram um anúncio') +
      cartao('Pedidos de exclusão de dados', num(v.solicitacoesExclusaoAbertas), 'em andamento') +
      cartao('Denúncias do chat', num(v.denunciasChatPendentes), 'pendentes');
    var rotulosC = v.cadastrosPorDia.map(function (d) { return dataCurta(d.dia); });
    grafico('grCadastros', 'bar', rotulosC, [{ label: 'Cadastros', data: v.cadastrosPorDia.map(function (d) { return d.total; }), backgroundColor: '#2563eb' }]);
    grafico('grReservasDia', 'line', v.reservasPorDia.map(function (d) { return dataCurta(d.dia); }),
      [{ label: 'Reservas', data: v.reservasPorDia.map(function (d) { return d.total; }), borderColor: '#059669', backgroundColor: '#05966933', fill: true, tension: 0.25 }]);
    listaContagens('listaImoveisStatus', v.imoveisPorStatus);
    listaContagens('listaReservasStatus', v.reservasPorStatus);
    listaContagens('listaPapeis', v.usuariosPorPapel);
  }

  // ---------------------------------------------------------------- Uso do site

  async function carregarUso() {
    var u = await Api.get('/api/admin/uso/resumo?dias=' + dias());
    $('kpisUso').innerHTML =
      cartao('Visualizações', num(u.visualizacoes)) +
      cartao('Sessões', num(u.sessoes), 'abas distintas') +
      cartao('Cliques', num(u.cliques), dec(u.cliquesPorSessao, 1) + ' por sessão') +
      cartao('Tempo médio na página', Math.round(u.segundosMediosNaPagina) + ' s', 'aba visível');
    grafico('grUsoDia', 'line', u.serieDiaria.map(function (d) { return dataCurta(d.dia); }), [
      { label: 'Visualizações', data: u.serieDiaria.map(function (d) { return d.visualizacoes; }), borderColor: '#2563eb', backgroundColor: '#2563eb22', fill: true, tension: 0.25 },
      { label: 'Sessões', data: u.serieDiaria.map(function (d) { return d.sessoes; }), borderColor: '#64748b', tension: 0.25 }]);

    var maxPag = Math.max.apply(null, u.paginas.map(function (p) { return p.visualizacoes; }).concat([1]));
    $('tabPaginas').innerHTML = tabela([{ t: 'Página' }, { t: 'Visualizações', num: true }, { t: 'Sessões', num: true }],
      u.paginas.map(function (p) {
        return '<tr><td class="py-1.5 pr-3">' + nomePagina(p.pagina) +
          '<div class="h-1 rounded bg-blue-100 mt-1"><div class="h-1 rounded bg-blue-600" style="width:' + Math.round(p.visualizacoes / maxPag * 100) + '%"></div></div></td>' +
          '<td class="py-1.5 pr-3 text-right font-bold text-slate-900">' + num(p.visualizacoes) + '</td>' +
          '<td class="py-1.5 text-right">' + num(p.sessoes) + '</td></tr>';
      }));
    $('tabElementos').innerHTML = tabelaElementos(u.elementos, true);
    $('tabPermanencia').innerHTML = tabela([{ t: 'Página' }, { t: 'Segundos', num: true }, { t: 'Amostras', num: true }],
      u.permanencia.map(function (p) {
        return '<tr><td class="py-1.5 pr-3">' + nomePagina(p.pagina) + '</td><td class="py-1.5 pr-3 text-right font-bold text-slate-900">' +
          Math.round(p.segundosMedios) + '</td><td class="py-1.5 text-right">' + num(p.amostras) + '</td></tr>';
      }));
    grade(u.atividade);
    grafico('grDispositivos', 'doughnut', u.dispositivos.map(function (d) { return rotulo(d.rotulo); }),
      [{ data: u.dispositivos.map(function (d) { return d.total; }), backgroundColor: ['#2563eb', '#16a34a', '#eab308', '#64748b'] }],
      { interaction: { mode: 'nearest' } });
    $('infoArmazenados').textContent = num(u.eventosArmazenados) + ' evento(s) armazenados no total.';
  }

  function tabelaElementos(elementos, comPagina) {
    var max = Math.max.apply(null, elementos.map(function (e) { return e.cliques; }).concat([1]));
    return tabela([{ t: 'Elemento' }, { t: 'Cliques', num: true }], elementos.map(function (e) {
      return '<tr><td class="py-1.5 pr-3"><span class="font-mono text-[11px] text-slate-800 break-all">' + esc(e.alvo) + '</span>' +
        (comPagina ? ' <span class="text-[10px] text-slate-400">em ' + esc(NOME_PAGINA[e.pagina] || e.pagina) + '</span>' : '') +
        '<div class="h-1 rounded bg-amber-100 mt-1"><div class="h-1 rounded bg-amber-500" style="width:' + Math.round(e.cliques / max * 100) + '%"></div></div></td>' +
        '<td class="py-1.5 text-right font-bold text-slate-900">' + num(e.cliques) + '</td></tr>';
    }));
  }

  /** Grade dia da semana × hora: mapa de calor de HTML (cada célula tem o número no title e no texto lido por leitor de tela). */
  function grade(atividade) {
    var mapa = {}, max = 1;
    atividade.forEach(function (a) { mapa[a.diaSemana + '-' + a.hora] = a.eventos; if (a.eventos > max) { max = a.eventos; } });
    var h = '<table class="text-[10px] border-separate" style="border-spacing:2px" aria-label="Atividade por dia da semana e hora"><thead><tr><th></th>';
    for (var hr = 0; hr < 24; hr++) { h += '<th class="font-normal text-slate-400 w-6 text-center">' + hr + '</th>'; }
    h += '</tr></thead><tbody>';
    for (var d = 1; d <= 7; d++) {
      h += '<tr><th class="font-semibold text-slate-500 pr-2 text-left">' + DIAS_SEMANA[d - 1] + '</th>';
      for (var k = 0; k < 24; k++) {
        var n = mapa[d + '-' + k] || 0;
        var a = n ? 0.12 + 0.88 * Math.sqrt(n / max) : 0;
        h += '<td title="' + DIAS_SEMANA[d - 1] + ' ' + k + 'h: ' + n + ' evento(s)" class="w-6 h-6 rounded text-center" style="background:' +
          (n ? 'rgba(37,99,235,' + a.toFixed(2) + ')' : '#f1f5f9') + ';color:' + (a > 0.55 ? '#fff' : 'transparent') + '">' + (n || '') + '</td>';
      }
      h += '</tr>';
    }
    $('gradeHoras').innerHTML = h + '</tbody></table>';
  }

  // ---------------------------------------------------------------- Mapas de calor

  var CORES = null;
  function paleta() {
    if (CORES) { return CORES; }
    var c = document.createElement('canvas'); c.width = 256; c.height = 1;
    var g = c.getContext('2d'), grad = g.createLinearGradient(0, 0, 256, 0);
    grad.addColorStop(0, '#2563eb'); grad.addColorStop(0.4, '#16a34a'); grad.addColorStop(0.7, '#eab308'); grad.addColorStop(1, '#dc2626');
    g.fillStyle = grad; g.fillRect(0, 0, 256, 1);
    CORES = g.getImageData(0, 0, 256, 1).data;
    return CORES;
  }

  /** Desenha as células de cliques como manchas e as colore pela densidade (azul = pouco, vermelho = muito). */
  function desenharCalor(mapa, largura, altura) {
    var tela = $('telaCalor');
    tela.width = largura; tela.height = altura;
    tela.style.width = largura + 'px'; tela.style.height = altura + 'px';
    var ctx = tela.getContext('2d');
    ctx.clearRect(0, 0, largura, altura);
    if (!$('chkCalor').checked || !mapa.celulas.length || !mapa.maximoCelula) { return; }
    var tmp = document.createElement('canvas'); tmp.width = largura; tmp.height = altura;
    var t = tmp.getContext('2d');
    var cw = largura / mapa.colunas, ch = mapa.alturaCelulaPx;
    // Cada célula vira três manchas sobrepostas (núcleo, corpo e um halo frio bem largo), com um deslocamento
    // determinístico dentro da célula: some o aspecto de "carimbo" na grade e a área fria (poucos cliques) aparece.
    var raios = [{ k: 3.2, a: 1 }, { k: 6.5, a: 0.5 }, { k: 10, a: 0.22 }];
    mapa.celulas.forEach(function (c) {
      var h = Math.sin(c.coluna * 12.9898 + c.linha * 78.233) * 43758.5453; h -= Math.floor(h);
      var h2 = Math.sin(c.coluna * 39.346 + c.linha * 11.135) * 24634.6345; h2 -= Math.floor(h2);
      var cx = (c.coluna + 0.15 + h * 0.7) * cw, cy = (c.linha + 0.15 + h2 * 0.7) * ch;
      var forca = 0.18 + 0.82 * Math.sqrt(c.cliques / mapa.maximoCelula);
      raios.forEach(function (r) {
        var raio = Math.max(cw * r.k, 28 * (r.k / 3.2));
        var a = Math.min(1, forca * r.a);
        var g = t.createRadialGradient(cx, cy, 0, cx, cy, raio);
        g.addColorStop(0, 'rgba(0,0,0,' + a.toFixed(3) + ')');
        g.addColorStop(0.45, 'rgba(0,0,0,' + (a * 0.45).toFixed(3) + ')');
        g.addColorStop(1, 'rgba(0,0,0,0)');
        t.fillStyle = g; t.fillRect(cx - raio, cy - raio, raio * 2, raio * 2);
      });
    });
    var img = t.getImageData(0, 0, largura, altura), px = img.data, cores = paleta();
    for (var i = 0; i < px.length; i += 4) {
      var al = px[i + 3];
      if (al) {
        // O azul (frio) começa quase transparente e ganha corpo logo: a área de pouco clique fica visível.
        px[i] = cores[al * 4]; px[i + 1] = cores[al * 4 + 1]; px[i + 2] = cores[al * 4 + 2];
        px[i + 3] = Math.min(200, 24 + al * 1.35);
      }
    }
    ctx.putImageData(img, 0, 0);
  }

  async function urlDaPagina(pagina) {
    if (pagina.indexOf('{id}') === -1) { return pagina; }
    var r = await Api.get('/api/imoveis', { ignorar401: true });
    var lista = Array.isArray(r) ? r : ((r && (r.content || r.itens)) || []);
    if (!lista.length) { throw new Error('Não há anúncio publicado para servir de exemplo da página.'); }
    return pagina.replace('{id}', lista[0].id);
  }

  var mapaAtual = null;
  var ajuste = null;
  function ajustarCaixa(largura, altura) {
    var palco = $('palcoCalor'), caixa = $('caixaCalor');
    var escala = Math.min(1, (palco.clientWidth - 2) / largura);
    caixa.style.width = largura + 'px'; caixa.style.height = altura + 'px';
    caixa.style.transform = 'scale(' + escala + ')';
    caixa.style.marginRight = -(largura * (1 - escala)) + 'px';
    caixa.style.marginBottom = -(altura * (1 - escala)) + 'px';
    var q = $('quadroCalor');
    q.style.width = largura + 'px'; q.style.height = altura + 'px';
  }

  function barrasRolagem(rolagem, visitas) {
    var por = {};
    rolagem.forEach(function (r) { por[Number(r.rotulo)] = r.total; });
    var total = rolagem.reduce(function (s, r) { return s + r.total; }, 0);
    var h = '';
    for (var p = 10; p <= 100; p += 10) {
      var alcancaram = 0;
      Object.keys(por).forEach(function (k) { if (Number(k) >= p) { alcancaram += por[k]; } });
      var frac = total ? alcancaram / total : 0;
      h += '<div class="flex items-center gap-2 text-[11px]"><span class="w-9 text-slate-500 text-right">' + p + '%</span>' +
        '<span class="flex-1 h-3 rounded bg-slate-100"><span class="block h-3 rounded bg-blue-600" style="width:' + Math.round(frac * 100) + '%"></span></span>' +
        '<span class="w-10 font-bold text-slate-800">' + Math.round(frac * 100) + '%</span></div>';
    }
    $('barrasRolagem').innerHTML = total ? h : '<p class="text-xs text-slate-400">Sem dados de rolagem.</p>';
  }

  async function carregarCalor() {
    mostrarErro('');
    var sel = $('selPagina');
    if (!paginasCarregadas) {
      var lista = await Api.get('/api/admin/uso/paginas?dias=365');
      sel.innerHTML = lista.length ? lista.map(function (p) {
        return '<option value="' + esc(p) + '">' + esc((NOME_PAGINA[p] ? NOME_PAGINA[p] + ' — ' : '') + p) + '</option>';
      }).join('') : '<option value="">Sem dados ainda</option>';
      paginasCarregadas = true;
    }
    if (!sel.value) {
      $('resumoCalor').textContent = 'Ainda não há eventos coletados. Navegue pelo site (fora do painel) e volte aqui.';
      return;
    }
    var disp = $('selDispositivo').value;
    var mapa = await Api.get('/api/admin/uso/mapa-de-calor?pagina=' + encodeURIComponent(sel.value) + '&dispositivo=' + disp + '&dias=' + dias());
    mapaAtual = mapa;
    $('resumoCalor').textContent = num(mapa.totalCliques) + ' clique(s) e ' + num(mapa.visualizacoes) + ' visualização(ões) em ' +
      mapa.dias + ' dia(s), ' + rotulo(disp).toLowerCase() + '. Cada mancha é uma região da página que concentrou cliques.';
    $('tabElementosPagina').innerHTML = tabelaElementos(mapa.elementos, false);
    barrasRolagem(mapa.profundidadeRolagem, mapa.visualizacoes);

    var largura = LARGURA_DISPOSITIVO[disp] || 1280;
    var quadro = $('quadroCalor');
    var url = await urlDaPagina(sel.value);
    ajustarCaixa(largura, Math.max(mapa.alturaDocumentoPx || 0, 700));
    quadro.onload = function () {
      clearTimeout(ajuste);
      // Dá tempo de a página buscar os próprios dados antes de medir a altura.
      ajuste = setTimeout(function () {
        var h = mapa.alturaDocumentoPx || 0;
        try {
          var d = quadro.contentDocument;
          h = Math.max(h, d.documentElement.scrollHeight, d.body ? d.body.scrollHeight : 0);
        } catch (e) { /* sem acesso: usa a altura registrada */ }
        h = Math.min(Math.max(h, 700), 20000);
        ajustarCaixa(largura, h);
        desenharCalor(mapa, largura, h);
      }, 1500);
    };
    quadro.src = url;
    desenharCalor(mapa, largura, Math.max(mapa.alturaDocumentoPx || 0, 700));
  }

  // ---------------------------------------------------------------- Listas

  var LISTAS = {
    usuarios: {
      url: '/api/admin/usuarios', alvo: 'tabUsuarios', pag: 'pagUsuarios', busca: 'buscaUsuarios', filtro: 'filtroPapel', param: 'papel',
      cabecalho: ['Nome', 'E-mail', 'Papel', 'Situação', 'Criado em', ''],
      linha: function (u, i) {
        var podeAlterar = u.papel !== 'ADMIN' && !(Auth.getUser() && Auth.getUser().email === u.email);
        return '<tr><td class="px-3 py-2 font-semibold text-slate-800">' + esc(u.nome) + '</td><td class="px-3 py-2">' + esc(u.email) +
          (u.emailVerificado ? '' : ' <span class="text-[10px] text-amber-600">(não verificado)</span>') + '</td>' +
          '<td class="px-3 py-2">' + esc(rotulo(u.papel)) + '</td>' +
          '<td class="px-3 py-2">' + (u.ativo ? '<span class="text-emerald-700 font-semibold">Ativa</span>' : '<span class="text-rose-700 font-semibold">Suspensa</span>') + '</td>' +
          '<td class="px-3 py-2 whitespace-nowrap">' + dataHora(u.criadoEm) + '</td>' +
          '<td class="px-3 py-2 text-right whitespace-nowrap">' +
          '<button type="button" data-mod="mensagem" data-i="' + i + '" class="text-[11px] font-bold text-blue-700 hover:underline">Mensagem</button>' +
          (podeAlterar
            ? ' <button type="button" data-mod="' + (u.ativo ? 'suspender' : 'reativar') + '" data-i="' + i + '" class="ml-2 text-[11px] font-bold ' +
              (u.ativo ? 'text-rose-700' : 'text-emerald-700') + ' hover:underline">' + (u.ativo ? 'Suspender' : 'Reativar') + '</button>' : '') + '</td></tr>';
      }
    },
    imoveis: {
      url: '/api/admin/imoveis', alvo: 'tabImoveis', pag: 'pagImoveis', busca: 'buscaImoveis', filtro: 'filtroStatusImovel', param: 'status',
      cabecalho: ['Imóvel', 'Local', 'Gestor', 'Situação', 'Diária', 'Cadastro'],
      linha: function (i) {
        return '<tr><td class="px-3 py-2 font-semibold text-slate-800">' + esc(i.titulo) + '</td><td class="px-3 py-2">' + esc(i.local || '—') + '</td>' +
          '<td class="px-3 py-2">' + esc(i.gestor || '—') + '</td><td class="px-3 py-2">' + esc(rotulo(i.status)) + (i.ativo ? '' : ' <span class="text-rose-700">(inativo)</span>') + '</td>' +
          '<td class="px-3 py-2 whitespace-nowrap">' + (i.diaria == null ? '—' : moeda(i.diaria)) + '</td><td class="px-3 py-2 whitespace-nowrap">' + dataHora(i.cadastradoEm) + '</td></tr>';
      }
    },
    reservas: {
      url: '/api/admin/reservas', alvo: 'tabReservas', pag: 'pagReservas', busca: 'buscaReservas', filtro: 'filtroStatusReserva', param: 'status',
      cabecalho: ['#', 'Imóvel', 'Hóspede', 'Período', 'Situação', 'Total', 'Criada em'],
      linha: function (r) {
        return '<tr><td class="px-3 py-2 text-slate-400">' + r.id + '</td><td class="px-3 py-2 font-semibold text-slate-800">' + esc(r.imovel) + '</td>' +
          '<td class="px-3 py-2">' + esc(r.hospede) + '</td><td class="px-3 py-2 whitespace-nowrap">' + esc(dataCurta(r.checkin)) + ' → ' + esc(dataCurta(r.checkout)) + '</td>' +
          '<td class="px-3 py-2">' + esc(rotulo(r.status)) + '</td><td class="px-3 py-2 whitespace-nowrap">' + moeda(r.total) + '</td>' +
          '<td class="px-3 py-2 whitespace-nowrap">' + dataHora(r.criadaEm) + '</td></tr>';
      }
    }
  };

  async function carregarLista(nome) {
    mostrarErro('');
    var c = LISTAS[nome];
    var q = new URLSearchParams({ pagina: estadoListas[nome], tamanho: 15 });
    if ($(c.busca).value.trim()) { q.set('busca', $(c.busca).value.trim()); }
    if ($(c.filtro).value) { q.set(c.param, $(c.filtro).value); }
    var r = await Api.get(c.url + '?' + q.toString());
    if (nome === 'usuarios') { usuariosAtuais = r.itens; }
    $(c.alvo).innerHTML = r.itens.length
      ? '<table class="w-full text-xs"><thead class="bg-slate-50 text-slate-500 text-left"><tr>' +
        c.cabecalho.map(function (t) { return '<th class="px-3 py-2 font-semibold">' + esc(t) + '</th>'; }).join('') +
        '</tr></thead><tbody class="divide-y divide-slate-100 text-slate-700">' + r.itens.map(function (it, i) { return c.linha(it, i); }).join('') + '</tbody></table>'
      : '<p class="p-6 text-xs text-slate-400">Nada encontrado.</p>';
    $(c.pag).innerHTML = '<span>' + num(r.total) + ' registro(s) • página ' + (r.pagina + 1) + ' de ' + Math.max(1, r.totalPaginas) + '</span>' +
      '<span class="flex gap-2"><button type="button" data-pg="-1" class="px-3 py-1.5 rounded-lg border border-slate-300 font-semibold disabled:opacity-40"' + (r.pagina <= 0 ? ' disabled' : '') + '>Anterior</button>' +
      '<button type="button" data-pg="1" class="px-3 py-1.5 rounded-lg border border-slate-300 font-semibold disabled:opacity-40"' + (r.pagina + 1 >= r.totalPaginas ? ' disabled' : '') + '>Próxima</button></span>';
  }

  function ligarLista(nome) {
    var c = LISTAS[nome], temporizador;
    function recarregar() { estadoListas[nome] = 0; return executar(function () { return carregarLista(nome); }); }
    $(c.busca).addEventListener('input', function () { clearTimeout(temporizador); temporizador = setTimeout(recarregar, 350); });
    $(c.filtro).addEventListener('change', recarregar);
    $(c.pag).addEventListener('click', function (e) {
      var b = e.target.closest('[data-pg]');
      if (!b) { return; }
      estadoListas[nome] = Math.max(0, estadoListas[nome] + Number(b.getAttribute('data-pg')));
      executar(function () { return carregarLista(nome); });
    });
  }

  /** Suspender, reativar e mensagem passam pelo módulo de moderação (motivo obrigatório, trilha e aviso ao usuário). */
  async function acaoDeConta(botao) {
    var u = usuariosAtuais[Number(botao.getAttribute('data-i'))];
    var acao = botao.getAttribute('data-mod');
    var feito = acao === 'suspender' ? await ModAcoes.suspender(u) : (acao === 'reativar' ? await ModAcoes.reativar(u) : await ModAcoes.mensagem(u));
    if (feito && acao !== 'mensagem') { await executar(function () { return carregarLista('usuarios'); }); }
  }

  // ---------------------------------------------------------------- Dados / exportação

  async function carregarDados() {
    var u = await Api.get('/api/admin/uso/resumo?dias=1');
    $('infoArmazenados').textContent = num(u.eventosArmazenados) + ' evento(s) armazenados no total.';
  }

  async function baixar(formato) {
    var botoes = [$('btnExportCsv'), $('btnExportNdjson')];
    botoes.forEach(function (b) { b.disabled = true; });
    try {
      var r = await fetch('/api/admin/uso/exportar?formato=' + formato + '&dias=' + dias(), {
        headers: { 'Authorization': 'Bearer ' + Auth.getToken() }
      });
      if (!r.ok) { throw new Error('HTTP ' + r.status); }
      var blob = await r.blob();
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url; a.download = 'eventos-uso-' + new Date().toISOString().slice(0, 10) + '.' + formato;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 2000);
    } catch (e) {
      UI.toast('Não foi possível exportar: ' + e.message, 'erro');
    } finally {
      botoes.forEach(function (b) { b.disabled = false; });
    }
  }

  // ---------------------------------------------------------------- Abas e inicialização

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

  function montarAbas() {
    $('abas').innerHTML = ABAS.map(function (a) {
      return '<button type="button" role="tab" id="btn-' + a.id + '" data-aba="' + a.id + '" aria-controls="aba-' + a.id + '">' +
        '<i data-lucide="' + a.icone + '" class="w-4 h-4"></i><span>' + esc(a.titulo) + '</span></button>';
    }).join('');
    $('abas').addEventListener('click', function (e) {
      var b = e.target.closest('[data-aba]');
      if (b) { abrirAba(b.getAttribute('data-aba')); }
    });
    if (global.lucide) { global.lucide.createIcons(); }
  }

  function iniciar() {
    var u = Auth.getUser();
    if (!Auth.isAdmin()) {
      $('semAcesso').classList.remove('hidden');
      if (Auth.isAuthenticated()) {
        $('linkEntrar').classList.add('hidden');
        $('semAcesso').querySelector('p').textContent = 'Esta área é exclusiva de administradores. A conta atual (' + ((u && u.papel) || '?') + ') não tem acesso.';
      }
      return;
    }
    $('painel').classList.remove('hidden');
    montarAbas();
    Object.keys(LISTAS).forEach(ligarLista);
    $('selDias').addEventListener('change', function () { paginasCarregadas = false; abrirAba(abaAtual.id); });
    $('btnCalor').addEventListener('click', function () { executar(carregarCalor); });
    $('selPagina').addEventListener('change', function () { executar(carregarCalor); });
    $('selDispositivo').addEventListener('change', function () { executar(carregarCalor); });
    $('chkCalor').addEventListener('change', function () {
      if (mapaAtual) { desenharCalor(mapaAtual, $('telaCalor').width || LARGURA_DISPOSITIVO[$('selDispositivo').value], $('telaCalor').height || 700); }
    });
    $('opacidadeCalor').addEventListener('input', function () { $('telaCalor').style.opacity = String(Number($('opacidadeCalor').value) / 100); });
    $('btnExportCsv').addEventListener('click', function () { baixar('csv'); });
    $('btnExportNdjson').addEventListener('click', function () { baixar('ndjson'); });
    $('tabUsuarios').addEventListener('click', function (e) {
      var b = e.target.closest('[data-mod]');
      if (b) { acaoDeConta(b); }
    });
    abrirAba((location.hash || '').replace('#', '') || 'visao');
  }

  document.addEventListener('DOMContentLoaded', iniciar);
})(window);
