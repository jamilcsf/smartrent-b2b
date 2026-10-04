/**
 * Aviso fixo das restricoes temporarias de conta (pedido de exclusao de dados em andamento).
 *
 * So aparece quando o servidor diz que ha restricoes ativas (usuario.restricoes, sincronizado
 * por /api/auth/me no carregamento de cada pagina). O texto e generico de proposito: nao lista
 * nem afirma nenhuma restricao em particular; o detalhe fica no perfil e quem faz valer e sempre
 * o servidor. Texto provisorio, mantido igual ao de perfil.restricao.aviso (pendente de revisao).
 */
(function (global) {
  'use strict';

  var ID = 'avisoRestricaoGlobal';
  var TEXTO = 'Há uma solicitação de exclusão de dados em análise. Algumas ações estão temporariamente indisponíveis.';

  function render(estado) {
    var restricoes = (estado.user && estado.user.restricoes) || [];
    var atual = document.getElementById(ID);
    if (!estado.isAuthenticated || !restricoes.length) {
      if (atual) { atual.remove(); }
      return;
    }
    if (atual) { return; }
    var aviso = document.createElement('div');
    aviso.id = ID;
    aviso.setAttribute('role', 'status');
    aviso.className = 'bg-amber-50 border-b border-amber-200 text-amber-900 text-xs font-semibold px-4 py-2 text-center';
    aviso.appendChild(document.createTextNode(TEXTO + ' '));
    var link = document.createElement('a');
    link.href = '/perfil.html';
    link.className = 'underline underline-offset-2';
    link.textContent = 'Ver no meu perfil';
    aviso.appendChild(link);
    var cabecalho = document.querySelector('header');
    if (cabecalho && cabecalho.parentNode) {
      cabecalho.insertAdjacentElement('afterend', aviso);
    } else {
      document.body.insertBefore(aviso, document.body.firstChild);
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (global.Auth) { global.Auth.onChange(render); }
  });
})(window);
