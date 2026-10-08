/**
 * Menu principal, montado a partir do perfil de quem esta logado.
 *
 * Visitante e cliente veem "Imoveis"; o admin ve tambem "Admin"; o gestor ve tambem "Dashboard" (estatisticas)
 * e "Painel do Gestor" (operacao). A aba "SmartChat" aparece sempre para o gestor
 * e, para o cliente, so depois da primeira interacao (flag guardada no backend:
 * usuario.smartchatLiberado). E conveniencia de interface: o servidor e quem
 * nega o acesso. Cada pagina so precisa de <nav id="navPrincipal">.
 */
(function (global) {
  'use strict';

  var BASE = 'flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition whitespace-nowrap ';
  var ATIVO = BASE + 'bg-blue-50 text-blue-700 border border-blue-200';
  var INATIVO = BASE + 'text-slate-600 hover:text-slate-900 hover:bg-slate-100';

  function itens(estado) {
    var u = estado.user || {};
    var gestor = global.Auth && Auth.isGestor();
    var lista = [{ href: '/imoveis.html', icone: 'home', texto: 'Imóveis' }];
    if (gestor) {
      lista.push({ href: '/estatisticas.html', icone: 'bar-chart-3', texto: 'Dashboard' });
      lista.push({ href: '/dashboard.html', icone: 'calendar', texto: 'Painel do Gestor' });
    }
    if (global.Auth && Auth.isAdmin()) {
      lista.push({ href: '/admin.html', icone: 'shield-check', texto: 'Admin' });
      lista.push({ href: '/moderacao.html', icone: 'gavel', texto: 'Moderação' });
    }
    if (estado.isAuthenticated && !gestor) {
      lista.push({ href: '/reserva.html', icone: 'ticket', texto: 'Minhas reservas' });
    }
    if (estado.isAuthenticated && (gestor || u.smartchatLiberado)) {
      lista.push({ href: '/smartchat.html', icone: 'message-circle', texto: 'SmartChat', id: 'navSmartChat' });
    }
    if (estado.isAuthenticated) {
      lista.push({ href: '/comunicados.html', icone: 'bell', texto: 'Avisos', id: 'navAvisos' });
    }
    return lista;
  }

  function montar(estado) {
    var nav = document.getElementById('navPrincipal');
    if (!nav) { return; }
    var atual = location.pathname;
    nav.innerHTML = itens(estado).map(function (i) {
      var ativo = atual === i.href || (i.href === '/imoveis.html' && /^\/imoveis\//.test(atual));
      return '<a href="' + i.href + '"' + (i.id ? ' id="' + i.id + '"' : '') + ' aria-label="' + i.texto + '" title="' + i.texto + '"' + (ativo ? ' aria-current="page"' : '') + ' class="' + (ativo ? ATIVO : INATIVO) + '">' +
        '<i data-lucide="' + i.icone + '" class="w-4 h-4"></i>' +
        '<span class="hidden sm:inline">' + i.texto + '</span>' +
        (i.id ? '<span data-nao-lidas class="hidden min-w-[1.1rem] h-[1.1rem] px-1 rounded-full bg-rose-600 text-white text-[10px] font-bold items-center justify-center"></span>' : '') +
        '</a>';
    }).join('');
    if (global.lucide) { global.lucide.createIcons(); }
    document.dispatchEvent(new CustomEvent('navegacao:pronta'));
    atualizarContador();
    atualizarAvisos();
  }

  /**
   * A flag da aba SmartChat vive no backend (nao no navegador): o cliente que acabou
   * de ter a primeira interacao (ou entrou em outro dispositivo) a ve assim que a
   * pagina consulta /api/auth/me.
   */
  var sincronizado = false;
  async function sincronizarUsuario() {
    if (sincronizado || !global.Auth || !Auth.isAuthenticated() || !global.Api) { return; }
    sincronizado = true;
    try {
      var eu = await Api.get('/api/auth/me', { ignorar401: true });
      var atual = Auth.getUser() || {};
      var campos = ['smartchatLiberado', 'nome', 'email', 'fotoUrl'];
      var mudou = !!eu && (campos.some(function (c) { return eu[c] !== atual[c]; })
        || JSON.stringify(eu.restricoes || []) !== JSON.stringify(atual.restricoes || []));
      if (mudou) {
        Auth.definirSessao(Auth.getToken(), Object.assign({}, atual, eu));
      }
    } catch (e) { /* segue com o que ja se sabe */ }
  }

  /** Contador de nao lidas no item SmartChat do menu (por polling leve; o chat usa SSE na propria pagina). */
  async function atualizarContador() {
    var item = document.getElementById('navSmartChat');
    if (!item || !global.Api) { return; }
    try {
      var r = await Api.get('/api/smartchat/nao-lidas', { ignorar401: true });
      var marca = item.querySelector('[data-nao-lidas]');
      if (marca) {
        marca.innerText = r.total > 99 ? '99+' : String(r.total);
        marca.classList.toggle('hidden', !r.total);
        marca.classList.toggle('flex', !!r.total);
      }
    } catch (e) { /* sem contador */ }
  }

  /** Avisos da administração não lidos (qualquer papel logado), no item "Avisos" do menu. */
  async function atualizarAvisos() {
    var item = document.getElementById('navAvisos');
    if (!item || !global.Api) { return; }
    try {
      var r = await Api.get('/api/comunicados/nao-lidos', { ignorar401: true });
      var marca = item.querySelector('[data-nao-lidas]');
      if (marca) {
        marca.innerText = r.total > 99 ? '99+' : String(r.total);
        marca.classList.toggle('hidden', !r.total);
        marca.classList.toggle('flex', !!r.total);
      }
    } catch (e) { /* sem contador */ }
  }

  global.Navegacao = { atualizarContador: atualizarContador, atualizarAvisos: atualizarAvisos };
  setInterval(function () { atualizarContador(); atualizarAvisos(); }, 30000);

  document.addEventListener('DOMContentLoaded', function () {
    if (global.Auth) { Auth.onChange(montar); sincronizarUsuario(); } else { montar({ user: null }); }
  });
})(window);
