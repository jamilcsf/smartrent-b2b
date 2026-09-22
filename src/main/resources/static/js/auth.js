/**
 * Estado global de autenticação.
 *
 * Equivale ao que um SPA resolveria com Context API ou Zustand: uma única
 * fonte de verdade, com assinatura de mudanças, para a interface apenas
 * reagir. Como aqui cada página é um documento novo, o estado é reidratado
 * do localStorage a cada carregamento.
 */
(function (global) {
  'use strict';

  var CHAVE_TOKEN = 'smartrent.token';
  var CHAVE_USUARIO = 'smartrent.usuario';
  var ouvintes = [];

  function ler(chave) {
    try {
      return global.localStorage.getItem(chave);
    } catch (e) {
      // Navegação privada ou storage bloqueado: trata como visitante.
      return null;
    }
  }

  function gravar(chave, valor) {
    try {
      if (valor === null) {
        global.localStorage.removeItem(chave);
      } else {
        global.localStorage.setItem(chave, valor);
      }
    } catch (e) {
      /* sem persistência; a sessão vale só para esta página */
    }
  }

  function lerUsuario() {
    try {
      return JSON.parse(ler(CHAVE_USUARIO) || 'null');
    } catch (e) {
      return null;
    }
  }

  var estado = { token: ler(CHAVE_TOKEN), usuario: lerUsuario() };

  function notificar() {
    ouvintes.forEach(function (fn) {
      try {
        fn(Auth.estado());
      } catch (e) {
        console.error('Ouvinte de autenticação falhou:', e);
      }
    });
  }

  var Auth = {
    estado: function () {
      return {
        isAuthenticated: !!estado.token,
        user: estado.usuario,
        token: estado.token
      };
    },

    isAuthenticated: function () {
      return !!estado.token;
    },

    getUser: function () {
      return estado.usuario;
    },

    getToken: function () {
      return estado.token;
    },

    definirSessao: function (token, usuario) {
      estado.token = token;
      estado.usuario = usuario || null;
      gravar(CHAVE_TOKEN, token);
      gravar(CHAVE_USUARIO, usuario ? JSON.stringify(usuario) : null);
      notificar();
    },

    /** Apaga a sessão. Usado no logout e ao receber 401. */
    limparSessao: function () {
      estado.token = null;
      estado.usuario = null;
      gravar(CHAVE_TOKEN, null);
      gravar(CHAVE_USUARIO, null);
      notificar();
    },

    logout: function (destino) {
      Auth.limparSessao();
      global.location.href = destino || '/index.html';
    },

    /** Registra um ouvinte e já o chama com o estado corrente. */
    onChange: function (fn) {
      ouvintes.push(fn);
      fn(Auth.estado());
      return function () {
        ouvintes = ouvintes.filter(function (o) { return o !== fn; });
      };
    },

    /** Caminho atual, para voltar a ele depois do login. */
    rotaAtual: function () {
      return global.location.pathname + global.location.search;
    }
  };

  // Sessão encerrada em outra aba reflete aqui.
  global.addEventListener('storage', function (e) {
    if (e.key === CHAVE_TOKEN) {
      estado.token = ler(CHAVE_TOKEN);
      estado.usuario = lerUsuario();
      notificar();
    }
  });

  global.Auth = Auth;
})(window);
