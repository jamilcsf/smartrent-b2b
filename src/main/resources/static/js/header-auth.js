/**
 * Área de autenticação do cabeçalho.
 *
 * Não decide nada: apenas reage ao estado global, trocando "Entrar" pelo
 * nome de quem está logado. Cada página só precisa de um elemento com
 * id="areaAuth" no cabeçalho.
 */
(function (global) {
  'use strict';

  function iniciais(nome) {
    return (nome || '?').trim().split(/\s+/).slice(0, 2)
      .map(function (p) { return p.charAt(0).toUpperCase(); }).join('');
  }

  function render(estado) {
    var area = document.getElementById('areaAuth');
    if (!area) { return; }

    if (!estado.isAuthenticated) {
      // Nas próprias telas de autenticação, oferecer "Entrar" apontaria para a
      // página atual e aninharia um redirectTo dentro do outro.
      if (/\/(login|cadastro)\.html$/.test(location.pathname)) {
        area.innerHTML = '';
        return;
      }
      area.innerHTML =
        '<a href="login.html?redirectTo=' + encodeURIComponent(Auth.rotaAtual()) + '" ' +
        'class="text-xs font-semibold text-slate-600 hover:text-slate-900 px-2">Entrar</a>' +
        '<a href="cadastro.html" class="ml-1 text-xs font-bold bg-blue-600 hover:bg-blue-700 ' +
        'text-white px-3 py-1.5 rounded-lg transition">Criar conta</a>';
      return;
    }

    var u = estado.user || {};
    area.innerHTML =
      '<div class="flex items-center gap-2">' +
        '<span class="w-7 h-7 rounded-full bg-blue-100 text-blue-700 text-[10px] font-bold ' +
        'flex items-center justify-center" title="' + (u.email || '') + '">' +
          iniciais(u.nome) +
        '</span>' +
        '<span class="text-xs font-semibold text-slate-700">' + (u.nome || 'Minha conta') + '</span>' +
        '<button type="button" id="btnSair" class="ml-2 text-xs font-semibold text-slate-500 ' +
        'hover:text-slate-900 underline underline-offset-2">Sair</button>' +
      '</div>';

    var botao = document.getElementById('btnSair');
    if (botao) {
      botao.addEventListener('click', function () { Auth.logout(); });
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (global.Auth) {
      Auth.onChange(render);
    }
  });
})(window);
