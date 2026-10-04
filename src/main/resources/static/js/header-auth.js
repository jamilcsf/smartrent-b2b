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
        '<a href="/login.html?redirectTo=' + encodeURIComponent(Auth.rotaAtual()) + '" ' +
        'class="text-xs font-semibold text-slate-600 hover:text-slate-900 px-2">Entrar</a>' +
        '<a href="/cadastro.html" class="ml-1 text-xs font-bold bg-blue-600 hover:bg-blue-700 ' +
        'text-white px-3 py-1.5 rounded-lg transition">Criar conta</a>';
      return;
    }

    var u = estado.user || {};
    area.textContent = '';

    var caixa = document.createElement('div');
    caixa.className = 'flex items-center gap-2';

    // Avatar + nome levam ao perfil. Tudo entra por textContent/atributos: o nome
    // e escolhido pelo usuario e nunca pode virar HTML.
    var link = document.createElement('a');
    link.id = 'linkPerfil';
    link.href = '/perfil.html';
    link.setAttribute('aria-label', 'Abrir meu perfil');
    link.title = 'Abrir meu perfil';
    link.className = 'flex items-center gap-2 rounded-lg px-1.5 py-1 hover:bg-slate-100 ' +
      'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 transition';

    link.appendChild(avatar(u));

    var nome = document.createElement('span');
    nome.className = 'text-xs font-semibold text-slate-700';
    nome.textContent = u.nome || 'Minha conta';
    link.appendChild(nome);
    caixa.appendChild(link);

    var sair = document.createElement('button');
    sair.type = 'button';
    sair.id = 'btnSair';
    sair.className = 'ml-2 text-xs font-semibold text-slate-500 hover:text-slate-900 underline underline-offset-2';
    sair.textContent = 'Sair';
    sair.addEventListener('click', function () { Auth.logout(); });
    caixa.appendChild(sair);

    area.appendChild(caixa);
  }

  /** Foto quando existir (com queda para as iniciais se a imagem falhar); senao, as iniciais. */
  function avatar(u) {
    var caixa = document.createElement('span');
    caixa.className = 'w-7 h-7 rounded-full bg-blue-100 text-blue-700 text-[10px] font-bold ' +
      'flex items-center justify-center overflow-hidden shrink-0';
    function comoIniciais() { caixa.textContent = iniciais(u.nome); }
    if (u.fotoUrl && /^\/api\/perfil\/foto\//.test(u.fotoUrl)) {
      var img = document.createElement('img');
      img.src = u.fotoUrl;
      img.alt = '';
      img.className = 'w-full h-full object-cover';
      img.addEventListener('error', comoIniciais);
      caixa.appendChild(img);
    } else {
      comoIniciais();
    }
    return caixa;
  }

  /**
   * Itens de navegação marcados com data-somente-gestor só aparecem para
   * gestores. É conveniência de interface: o servidor é quem nega o acesso.
   */
  function ajustarNavegacao(estado) {
    var gestor = global.Auth && Auth.isGestor();
    document.querySelectorAll('[data-somente-gestor]').forEach(function (el) {
      el.classList.toggle('hidden', !gestor);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (global.Auth) {
      Auth.onChange(function (estado) {
        render(estado);
        ajustarNavegacao(estado);
      });
    }
  });
})(window);
