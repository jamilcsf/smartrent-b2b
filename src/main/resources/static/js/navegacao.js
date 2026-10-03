/**
 * Menu principal, montado a partir do perfil de quem esta logado.
 *
 * Visitante e cliente veem "Imoveis"; o gestor ve tambem "Dashboard" (estatisticas)
 * e "Painel do Gestor" (operacao). A aba "SmartChat" aparece sempre para o gestor
 * e, para o cliente, so depois da primeira interacao (flag guardada no backend:
 * usuario.smartchatLiberado). E conveniencia de interface: o servidor e quem
 * nega o acesso. Cada pagina so precisa de <nav id="navPrincipal">.
 */
(function (global) {
  'use strict';

  var BASE = 'flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition ';
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
    if (estado.isAuthenticated && (gestor || u.smartchatLiberado)) {
      lista.push({ href: '/smartchat.html', icone: 'message-circle', texto: 'SmartChat', id: 'navSmartChat' });
    }
    return lista;
  }

  function montar(estado) {
    var nav = document.getElementById('navPrincipal');
    if (!nav) { return; }
    var atual = location.pathname;
    nav.innerHTML = itens(estado).map(function (i) {
      var ativo = atual === i.href || (i.href === '/imoveis.html' && /^\/imoveis\//.test(atual));
      return '<a href="' + i.href + '"' + (i.id ? ' id="' + i.id + '"' : '') + ' class="' + (ativo ? ATIVO : INATIVO) + '">' +
        '<i data-lucide="' + i.icone + '" class="w-4 h-4"></i>' +
        '<span class="hidden sm:inline">' + i.texto + '</span>' +
        (i.id ? '<span data-nao-lidas class="hidden min-w-[1.1rem] h-[1.1rem] px-1 rounded-full bg-rose-600 text-white text-[10px] font-bold items-center justify-center"></span>' : '') +
        '</a>';
    }).join('');
    if (global.lucide) { global.lucide.createIcons(); }
    document.dispatchEvent(new CustomEvent('navegacao:pronta'));
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (global.Auth) { Auth.onChange(montar); } else { montar({ user: null }); }
  });
})(window);
