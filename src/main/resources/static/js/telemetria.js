/**
 * Telemetria de uso (ADR-009): alimenta os mapas de calor e as metricas do painel de admin.
 *
 * ANONIMA por desenho. Coleta apenas: abertura de pagina, cliques (posicao e um identificador estrutural do
 * elemento: #id, data-track ou tag[href]), profundidade de rolagem e tempo ativo na pagina. NUNCA coleta texto
 * digitado, texto de elementos, URL com query/fragmento, cookies ou identificador de pessoa. O sorteio de sessao
 * vive em sessionStorage (vale por aba). Respeita "Do Not Track" / "Global Privacy Control" e nao roda dentro
 * de iframe (o proprio mapa de calor do admin) nem no painel de admin. Falha em silencio: nunca atrapalha a navegacao.
 */
(function (global) {
  'use strict';

  var doc = global.document;
  if (global.top !== global.self) { return; }
  if (global.navigator.doNotTrack === '1' || global.navigator.globalPrivacyControl) { return; }
  if (/^\/(admin|moderacao)\.html$/.test(global.location.pathname)) { return; }

  var ENDPOINT = '/api/telemetria/eventos';
  var MAX_FILA = 40;
  var INTERVALO_MS = 5000;

  var fila = [];
  var temporizador = null;
  var profundidadeMax = 0;
  var rolagemEnviada = -1;
  var ativoDesde = doc.visibilityState === 'visible' ? Date.now() : null;
  var segundosAtivos = 0;

  function sessao() {
    var chave = 'smartrent.tel';
    try {
      var atual = global.sessionStorage.getItem(chave);
      if (atual) { return atual; }
      var novo = (global.crypto && global.crypto.randomUUID) ? global.crypto.randomUUID()
        : 'a' + Math.random().toString(36).slice(2) + Date.now().toString(36);
      global.sessionStorage.setItem(chave, novo);
      return novo;
    } catch (e) {
      return 'x' + Math.random().toString(36).slice(2, 14) + Date.now().toString(36);
    }
  }
  var SESSAO = sessao();

  function dispositivo() {
    var l = global.innerWidth;
    return l < 768 ? 'MOBILE' : (l < 1024 ? 'TABLET' : 'DESKTOP');
  }

  function alturaDoc() {
    var d = doc.documentElement;
    return Math.max(d.scrollHeight, doc.body ? doc.body.scrollHeight : 0);
  }
  function larguraDoc() {
    return Math.max(doc.documentElement.clientWidth, 1);
  }

  function enviar() {
    temporizador = null;
    if (!fila.length) { return; }
    var lote = fila.splice(0, fila.length);
    var cabecalhos = { 'Content-Type': 'application/json' };
    try {
      var token = global.Auth && global.Auth.getToken();
      if (token) { cabecalhos['Authorization'] = 'Bearer ' + token; }
    } catch (e) { /* segue como visitante */ }
    try {
      // keepalive: o envio de saida (pagehide) sobrevive ao fechamento da pagina.
      global.fetch(ENDPOINT, {
        method: 'POST', headers: cabecalhos, keepalive: true,
        body: JSON.stringify({ sessaoId: SESSAO, dispositivo: dispositivo(), eventos: lote })
      }).catch(function () { /* sem telemetria; nada a fazer */ });
    } catch (e) { /* idem */ }
  }

  function registrar(evento) {
    evento.pagina = global.location.pathname;
    fila.push(evento);
    if (fila.length >= MAX_FILA) { enviar(); return; }
    if (!temporizador) { temporizador = global.setTimeout(enviar, INTERVALO_MS); }
  }

  /** Identificador estrutural do elemento clicado. Nunca o texto dele. */
  function alvoDe(el) {
    var no = el;
    for (var i = 0; no && no.nodeType === 1 && i < 6; i++, no = no.parentElement) {
      var marca = no.getAttribute('data-track');
      if (marca) { return '[data-track=' + marca + ']'; }
      if (no.id) { return '#' + no.id; }
      var tag = no.tagName.toLowerCase();
      if (tag === 'a') {
        var href = no.getAttribute('href') || '';
        if (href.charAt(0) === '/' || href.charAt(0) === '#' || /^[a-z0-9_.-]+\.html/i.test(href)) {
          return 'a[href=' + href.split('?')[0].split('#')[0] + ']';
        }
        return 'a';
      }
      if (tag === 'button' || tag === 'select' || tag === 'input' || tag === 'textarea' || tag === 'label' || tag === 'summary') {
        var nome = no.getAttribute('name');
        return nome ? tag + '[name=' + nome + ']' : tag;
      }
    }
    return el && el.tagName ? el.tagName.toLowerCase() : null;
  }

  /** Elemento fixo/grudado (cabecalho): a posicao dele e relativa a janela, nao ao documento. */
  function fixo(el) {
    var no = el;
    for (var i = 0; no && no.nodeType === 1 && i < 8; i++, no = no.parentElement) {
      var p = global.getComputedStyle(no).position;
      if (p === 'fixed' || p === 'sticky') { return true; }
    }
    return false;
  }

  doc.addEventListener('click', function (e) {
    try {
      var largura = larguraDoc();
      var y = fixo(e.target) ? e.clientY : (e.pageY != null ? e.pageY : e.clientY + global.scrollY);
      registrar({
        tipo: 'CLIQUE',
        alvo: alvoDe(e.target),
        x: Math.max(0, Math.min(1000, Math.round(((e.pageX != null ? e.pageX : e.clientX) / largura) * 1000))),
        y: Math.max(0, Math.round(y)),
        altura: alturaDoc()
      });
    } catch (err) { /* nunca quebra a pagina */ }
  }, true);

  function medirRolagem() {
    var total = alturaDoc();
    if (total <= 0) { return; }
    var visto = (global.scrollY || 0) + global.innerHeight;
    var pct = Math.min(100, Math.ceil((visto / total) * 10) * 10); // degraus de 10%
    if (pct > profundidadeMax) { profundidadeMax = pct; }
  }
  global.addEventListener('scroll', medirRolagem, { passive: true });

  function fecharCiclo() {
    medirRolagem();
    if (ativoDesde !== null) {
      segundosAtivos += (Date.now() - ativoDesde) / 1000;
      ativoDesde = null;
    }
    if (profundidadeMax > rolagemEnviada) {
      rolagemEnviada = profundidadeMax;
      registrar({ tipo: 'ROLAGEM', valor: profundidadeMax });
    }
    var s = Math.round(segundosAtivos);
    if (s > 0) {
      segundosAtivos = 0;
      registrar({ tipo: 'PERMANENCIA', valor: s });
    }
    enviar();
  }

  doc.addEventListener('visibilitychange', function () {
    if (doc.visibilityState === 'hidden') { fecharCiclo(); } else { ativoDesde = Date.now(); }
  });
  global.addEventListener('pagehide', fecharCiclo);

  registrar({ tipo: 'VISUALIZACAO', altura: alturaDoc() });
  medirRolagem();
})(window);
