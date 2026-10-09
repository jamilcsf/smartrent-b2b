/**
 * reCAPTCHA e "Entrar com o Google" das telas de login e cadastro.
 *
 * Cada página só precisa ter os elementos #captcha, #erro-captcha,
 * #googleBotao e #googleFallback e chamar AuthExterno.iniciar(). As chaves
 * públicas vêm do servidor (/api/auth/config); o segredo do captcha nunca
 * chega ao navegador, e a validação que vale é sempre a do servidor.
 */
(function (global) {
  'use strict';

  var widgetId = null;
  var siteKey = null;
  var opcoes = {};

  function carregarScript(src, aoCarregar, aoFalhar) {
    var s = document.createElement('script');
    s.src = src;
    s.async = true;
    s.defer = true;
    if (aoCarregar) { s.onload = aoCarregar; }
    if (aoFalhar) { s.onerror = aoFalhar; }
    document.head.appendChild(s);
  }

  // Sem aviso, o captcha que expira, dá erro ou nem chega a carregar deixa
  // um espaço em branco e a pessoa não sabe o que fazer.
  function avisarCaptcha(mensagem) {
    var alvo = document.getElementById('erro-captcha');
    alvo.innerText = mensagem;
    alvo.classList.toggle('hidden', !mensagem);
  }

  // Chamado pelo próprio script do reCAPTCHA quando termina de carregar.
  global.aoCarregarCaptcha = function () {
    widgetId = global.grecaptcha.render('captcha', {
      sitekey: siteKey,
      hl: 'pt-BR',
      callback: function () { avisarCaptcha(''); },
      // O token vale cerca de 2 minutos: depois disso a caixa se desmarca sozinha.
      'expired-callback': function () {
        avisarCaptcha('A verificação expirou. Marque a caixa "Não sou um robô" novamente.');
      },
      // Queda de conexão: o próprio widget manda quem o usa avisar a pessoa.
      'error-callback': function () {
        avisarCaptcha('Erro de conexão na verificação de segurança. Confira a internet e tente de novo.');
      }
    });
  };

  async function aoReceberCredencial(resposta) {
    opcoes.limpar();
    try {
      var r = await global.Api.post('/api/auth/google',
        { credential: resposta.credential,
          perfil: opcoes.perfil ? opcoes.perfil() : undefined }, { ignorar401: true });
      opcoes.aoEntrar(r);
    } catch (e) {
      opcoes.aoErro(e.message || 'Não foi possível continuar com o Google.');
    }
  }

  function iniciarGoogle(clientId) {
    var fallback = document.getElementById('googleFallback');
    if (!clientId) {
      // Sem client ID o recurso não está configurado: o botão existe, mas avisa.
      fallback.addEventListener('click', function () {
        opcoes.aoErro('Acesso com o Google ainda não está configurado neste ambiente (GOOGLE_CLIENT_ID).');
      });
      return;
    }
    fallback.classList.add('hidden');
    carregarScript('https://accounts.google.com/gsi/client', function () {
      var area = document.getElementById('googleBotao');
      global.google.accounts.id.initialize({ client_id: clientId, callback: aoReceberCredencial });
      global.google.accounts.id.renderButton(area, {
        theme: 'outline', size: 'large', shape: 'pill',
        text: opcoes.textoGoogle || 'signin_with',
        locale: 'pt-BR', width: Math.min(400, Math.max(200, area.offsetWidth))
      });
    });
  }

  global.AuthExterno = {
    /**
     * opcoes: { textoGoogle, perfil() opcional, limpar(), aoEntrar(resposta), aoErro(mensagem) }
     */
    iniciar: async function (o) {
      opcoes = o;
      try {
        var cfg = await global.Api.get('/api/auth/config');
        if (cfg.recaptchaSiteKey) {
          siteKey = cfg.recaptchaSiteKey;
          carregarScript('https://www.google.com/recaptcha/api.js?onload=aoCarregarCaptcha&render=explicit&hl=pt-BR',
            null,
            function () {
              avisarCaptcha('Não foi possível carregar a verificação de segurança. '
                + 'Confira a internet (redes públicas costumam bloquear) e recarregue a página.');
            });
        }
        iniciarGoogle(cfg.googleClientId);
      } catch (e) {
        opcoes.aoErro('Não foi possível carregar a verificação de segurança. Recarregue a página.');
      }
    },

    /**
     * Devolve o token do captcha resolvido, ou '' (mostrando o aviso) se a
     * pessoa ainda não o resolveu.
     */
    exigirCaptcha: function () {
      var token = widgetId === null ? '' : global.grecaptcha.getResponse(widgetId);
      if (!token) {
        avisarCaptcha(widgetId === null
          ? 'A verificação de segurança não carregou. Recarregue a página.'
          : 'Confirme que você não é um robô.');
      }
      return token;
    },

    /** O token vale uma única verificação: após qualquer tentativa, resolver de novo. */
    resetarCaptcha: function () {
      if (widgetId !== null) { global.grecaptcha.reset(widgetId); }
    }
  };
})(window);
