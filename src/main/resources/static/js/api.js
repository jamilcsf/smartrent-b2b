/**
 * Wrapper de requisições — o equivalente ao interceptador do Axios.
 *
 * Duas responsabilidades:
 *
 * 1. Anexar `Authorization: Bearer <token>` SOMENTE quando há token. Uma
 *    requisição de visitante sai sem o cabeçalho e não quebra, que é o que
 *    sustenta o site público por padrão.
 *
 * 2. Tratar 401 globalmente: limpa a sessão inválida e manda para o login
 *    carregando `?redirectTo=` com a rota em que a pessoa estava, para ela
 *    voltar exatamente para onde tentou agir.
 */
(function (global) {
  'use strict';

  function urlDeLogin(rotaDestino) {
    return '/login.html?redirectTo=' + encodeURIComponent(rotaDestino);
  }

  function ApiError(mensagem, status, corpo) {
    this.name = 'ApiError';
    this.message = mensagem;
    this.status = status;
    this.body = corpo;
  }
  ApiError.prototype = Object.create(Error.prototype);

  async function request(url, opcoes) {
    opcoes = opcoes || {};
    var headers = Object.assign({}, opcoes.headers);

    if (opcoes.body && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }

    // Só anexa o cabeçalho se houver token de fato.
    var token = global.Auth ? global.Auth.getToken() : null;
    if (token) {
      headers['Authorization'] = 'Bearer ' + token;
    }

    var resposta = await fetch(url, Object.assign({}, opcoes, { headers: headers }));

    if (resposta.status === 401 && !opcoes.ignorar401) {
      if (global.Auth) {
        global.Auth.limparSessao();
      }
      global.location.href = urlDeLogin(global.Auth ? global.Auth.rotaAtual() : '/');
      throw new ApiError('Sessão expirada.', 401, null);
    }

    var corpo = null;
    var tipo = resposta.headers.get('Content-Type') || '';
    if (tipo.indexOf('application/json') !== -1) {
      corpo = await resposta.json().catch(function () { return null; });
    } else {
      corpo = await resposta.text().catch(function () { return null; });
    }

    if (!resposta.ok) {
      var msg = (corpo && corpo.erro) || (typeof corpo === 'string' && corpo) ||
                ('Falha na requisição (HTTP ' + resposta.status + ').');
      throw new ApiError(msg, resposta.status, corpo);
    }

    return corpo;
  }

  global.Api = {
    ApiError: ApiError,
    urlDeLogin: urlDeLogin,
    get: function (url, opcoes) {
      return request(url, Object.assign({ method: 'GET' }, opcoes));
    },
    post: function (url, dados, opcoes) {
      return request(url, Object.assign({ method: 'POST', body: JSON.stringify(dados) }, opcoes));
    },
    put: function (url, dados, opcoes) {
      return request(url, Object.assign({ method: 'PUT', body: JSON.stringify(dados) }, opcoes));
    },
    del: function (url, opcoes) {
      return request(url, Object.assign({ method: 'DELETE' }, opcoes));
    }
  };
})(window);
