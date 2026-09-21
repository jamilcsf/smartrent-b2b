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
        'class="text-xs font-semibold text-white/90 hover:text-white">Entrar</a>' +
        '<a href="cadastro.html" class="ml-3 text-xs font-bold bg-white/15 hover:bg-white/25 ' +
        'text-white px-3 py-1.5 rounded-lg transition">Criar conta</a>';
      return;
    }

    var u = estado.user || {};
    area.innerHTML =
      '<div class="flex items-center gap-2">' +
        '<span class="w-7 h-7 rounded-full bg-white/20 text-white text-[10px] font-bold ' +
        'flex items-center justify-center" title="' + (u.email || '') + '">' +
          iniciais(u.nome) +
        '</span>' +
        '<span class="text-xs font-semibold text-white/90">' + (u.nome || 'Minha conta') + '</span>' +
        '<button type="button" id="btnSair" class="ml-2 text-xs font-semibold text-white/70 ' +
        'hover:text-white underline underline-offset-2">Sair</button>' +
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
