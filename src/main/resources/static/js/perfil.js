/**
 * Pagina "Meu perfil". Tudo age sobre o usuario autenticado (nenhuma chamada leva id de
 * usuario). Validacoes aqui so ajudam a experiencia: quem decide e o servidor.
 * Todo texto vindo do servidor entra por textContent, nunca por innerHTML.
 */
(function (global) {
  'use strict';

  var PAPEL = { CLIENTE: 'Cliente', ANFITRIAO: 'Gestor de imóveis', ADMIN: 'Administrador' };
  var estado = { perfil: null };

  function $(id) { return document.getElementById(id); }

  function iniciais(nome) {
    return (nome || '?').trim().split(/\s+/).slice(0, 2)
      .map(function (p) { return p.charAt(0).toUpperCase(); }).join('');
  }

  /** Mensagem de sucesso/erro sob um formulario (aria-live via role="status"). */
  function mensagem(id, texto, erro) {
    var el = $(id);
    if (!el) { return; }
    el.textContent = texto || '';
    el.className = 'text-[11px] font-semibold ' + (erro ? 'text-rose-600' : 'text-emerald-700') + (texto ? '' : ' hidden');
  }

  function ocupado(botao, sim) { if (botao) { botao.disabled = !!sim; } }

  function pintarFoto(alvo, perfil, classeImg) {
    alvo.textContent = '';
    if (perfil.fotoUrl) {
      var img = document.createElement('img');
      img.src = perfil.fotoUrl;
      img.alt = '';
      img.className = classeImg || 'w-full h-full object-cover';
      img.addEventListener('error', function () { alvo.textContent = iniciais(perfil.nome); });
      alvo.appendChild(img);
    } else {
      alvo.textContent = iniciais(perfil.nome);
    }
  }

  /** Atualiza a tela e o cabecalho (sessao local) com o que o servidor devolveu. */
  function aplicar(perfil) {
    estado.perfil = perfil;
    pintarFoto($('fotoAtual'), perfil);
    $('btnRemoverFoto').classList.toggle('hidden', !perfil.fotoUrl);
    if (document.activeElement !== $('nomeExibicao')) { $('nomeExibicao').value = perfil.nome || ''; }
    $('emailAtual').textContent = perfil.email;
    var pend = $('emailPendente');
    pend.classList.toggle('hidden', !perfil.emailPendente);
    pend.textContent = perfil.emailPendente
      ? 'Aguardando a confirmação de ' + perfil.emailPendente.email + ' (link válido até ' + UI.dataHora(perfil.emailPendente.expiraEm) + '). Até lá, você entra com o e-mail atual.'
      : '';
    $('papel').textContent = PAPEL[perfil.papel] || perfil.papel;
    $('cadastro').textContent = UI.dataHora(perfil.dataCadastro);
    var aviso = $('avisoRestricao');
    var restrito = perfil.restricoes && perfil.restricoes.length;
    aviso.classList.toggle('hidden', !restrito);
    aviso.textContent = restrito
      ? 'Há uma solicitação de exclusão em análise. Algumas ações estão temporariamente indisponíveis.' : '';
    if (global.PerfilExclusao) { global.PerfilExclusao.atualizar(perfil); }
    $('notaSenhaGoogle').classList.toggle('hidden', perfil.senhaDefinida);
    if (global.PerfilReauth) { global.PerfilReauth.atualizar(perfil); }
  }

  /** Mantem cabecalho e demais paginas em dia com o perfil novo (nome, foto, restricoes). */
  function sincronizarSessao(token) {
    var atual = global.Auth.getUser() || {};
    var p = estado.perfil;
    global.Auth.definirSessao(token || global.Auth.getToken(), Object.assign({}, atual, {
      nome: p.nome, email: p.email, fotoUrl: p.fotoUrl, restricoes: p.restricoes || []
    }));
  }

  async function carregar() {
    try {
      var perfil = await Api.get('/api/perfil');
      $('carregando').classList.add('hidden');
      $('conteudo').classList.remove('hidden');
      aplicar(perfil);
      sincronizarSessao();
    } catch (e) {
      $('carregando').classList.add('hidden');
      var erro = $('erroPagina');
      erro.textContent = 'Não foi possível carregar seu perfil. ' + (e.message || '');
      erro.classList.remove('hidden');
    }
  }

  // ------------------------------------------------------------ nome de exibicao

  function iniciarNome() {
    $('formNome').addEventListener('submit', async function (e) {
      e.preventDefault();
      var botao = e.target.querySelector('button[type="submit"]');
      var nome = $('nomeExibicao').value.trim();
      if (nome.length < 2 || nome.length > 60) {
        mensagem('msgNome', 'O nome de exibição deve ter entre 2 e 60 caracteres.', true);
        return;
      }
      ocupado(botao, true);
      mensagem('msgNome', '');
      try {
        var perfil = await Api.patch('/api/perfil', { nome: nome });
        aplicar(perfil);
        sincronizarSessao(); // cabecalho atualizado na hora
        mensagem('msgNome', 'Nome atualizado.');
      } catch (err) {
        mensagem('msgNome', err.message || 'Não foi possível salvar o nome.', true);
      } finally {
        ocupado(botao, false);
      }
    });
  }

  // ------------------------------------------------------------ foto de perfil

  var FOTO_MAX_BYTES = 2 * 1024 * 1024;
  var MSG_FOTO = 'Use uma imagem PNG ou JPG de até 2 MB.';
  var fotoEscolhida = null;

  function limparPrevia() {
    fotoEscolhida = null;
    $('previaFoto').classList.add('hidden');
    $('inputFoto').value = '';
  }

  /** Pre-visualizacao circular com o mesmo recorte quadrado central que o servidor aplica. */
  function escolherFoto(arquivo) {
    mensagem('msgFoto', '');
    if (!arquivo) { return; }
    if (arquivo.type !== 'image/png' && arquivo.type !== 'image/jpeg') {
      limparPrevia();
      mensagem('msgFoto', MSG_FOTO, true);
      return;
    }
    if (arquivo.size > FOTO_MAX_BYTES) {
      limparPrevia();
      mensagem('msgFoto', MSG_FOTO, true);
      return;
    }
    var url = URL.createObjectURL(arquivo);
    var img = new Image();
    img.onload = function () {
      URL.revokeObjectURL(url);
      if (img.naturalWidth < 128 || img.naturalHeight < 128) {
        limparPrevia();
        mensagem('msgFoto', 'A imagem deve ter pelo menos 128×128 pixels.', true);
        return;
      }
      var canvas = $('canvasPrevia');
      var ctx = canvas.getContext('2d');
      var lado = Math.min(img.naturalWidth, img.naturalHeight);
      ctx.fillStyle = '#fff';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, (img.naturalWidth - lado) / 2, (img.naturalHeight - lado) / 2, lado, lado,
        0, 0, canvas.width, canvas.height);
      fotoEscolhida = arquivo;
      $('previaFoto').classList.remove('hidden');
    };
    img.onerror = function () {
      URL.revokeObjectURL(url);
      limparPrevia();
      mensagem('msgFoto', MSG_FOTO, true);
    };
    img.src = url;
  }

  function iniciarFoto() {
    $('inputFoto').addEventListener('change', function (e) { escolherFoto(e.target.files[0]); });
    var zona = $('zonaFoto');
    ['dragenter', 'dragover'].forEach(function (nome) {
      zona.addEventListener(nome, function (e) { e.preventDefault(); zona.classList.add('border-blue-500', 'bg-blue-50'); });
    });
    ['dragleave', 'drop'].forEach(function (nome) {
      zona.addEventListener(nome, function (e) { e.preventDefault(); zona.classList.remove('border-blue-500', 'bg-blue-50'); });
    });
    zona.addEventListener('drop', function (e) {
      escolherFoto(e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]);
    });
    $('btnCancelarFoto').addEventListener('click', function () { limparPrevia(); mensagem('msgFoto', ''); });

    $('btnSalvarFoto').addEventListener('click', async function (e) {
      if (!fotoEscolhida) { return; }
      ocupado(e.target, true);
      try {
        var dados = new FormData();
        dados.append('arquivo', fotoEscolhida);
        var perfil = await Api.postForm('/api/perfil/foto', dados);
        limparPrevia();
        aplicar(perfil);
        sincronizarSessao();
        mensagem('msgFoto', 'Foto atualizada.');
      } catch (err) {
        mensagem('msgFoto', err.message || MSG_FOTO, true);
      } finally {
        ocupado(e.target, false);
      }
    });

    $('btnRemoverFoto').addEventListener('click', async function (e) {
      var sim = await UI.confirmar({ titulo: 'Remover foto', mensagem: 'Remover sua foto de perfil? Voltarão a aparecer suas iniciais.',
        confirmarTexto: 'Remover' });
      if (!sim) { return; }
      ocupado(e.target, true);
      try {
        aplicar(await Api.del('/api/perfil/foto'));
        sincronizarSessao();
        mensagem('msgFoto', 'Foto removida.');
      } catch (err) {
        mensagem('msgFoto', err.message || 'Não foi possível remover a foto.', true);
      } finally {
        ocupado(e.target, false);
      }
    });
  }

  // ------------------------------------------------------ reautenticacao (senha atual)

  /**
   * Cada formulario sensivel (senha, e-mail, exclusao) tem um espaco [data-reauth]. Conta com senha:
   * campo "Senha atual". Conta criada pelo Google (sem senha): botao do Google, que entrega uma
   * credencial recente, verificada no servidor. A confirmacao em si e sempre do servidor.
   */
  var Reauth = {
    credencial: null,
    clienteGoogle: undefined,
    contador: 0,

    atualizar: function (perfil) {
      var self = this;
      document.querySelectorAll('[data-reauth]').forEach(function (area) {
        var modo = perfil.senhaDefinida ? 'senha' : 'google';
        if (area.dataset.pronto === modo) { return; }
        area.dataset.pronto = modo;
        area.textContent = '';
        if (perfil.senhaDefinida) {
          self.contador++;
          var id = 'senhaAtual' + self.contador;
          var rotulo = document.createElement('label');
          rotulo.className = 'block text-xs font-medium text-slate-500 mb-1';
          rotulo.setAttribute('for', id);
          rotulo.textContent = 'Senha atual';
          var campo = document.createElement('input');
          campo.type = 'password';
          campo.id = id;
          campo.autocomplete = 'current-password';
          campo.dataset.senhaAtual = '1';
          campo.className = 'w-full px-4 py-2.5 border rounded-xl border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500';
          area.appendChild(rotulo);
          area.appendChild(campo);
        } else {
          var info = document.createElement('p');
          info.className = 'text-[11px] text-slate-600 mb-2';
          info.textContent = 'Confirme sua identidade com o Google para continuar:';
          var botao = document.createElement('div');
          botao.dataset.googleBotao = '1';
          var status = document.createElement('p');
          status.dataset.googleStatus = '1';
          status.className = 'text-[11px] font-semibold text-emerald-700 mt-1';
          status.textContent = self.credencial ? 'Identidade confirmada com o Google.' : '';
          area.appendChild(info);
          area.appendChild(botao);
          area.appendChild(status);
        }
      });
      if (!perfil.senhaDefinida) { this.iniciarGoogle(); }
    },

    iniciarGoogle: async function () {
      var self = this;
      if (self.clienteGoogle !== undefined) { return self.desenharGoogle(); }
      try {
        var cfg = await Api.get('/api/auth/config');
        self.clienteGoogle = cfg.googleClientId || null;
      } catch (e) {
        self.clienteGoogle = null;
      }
      if (!self.clienteGoogle) {
        document.querySelectorAll('[data-google-status]').forEach(function (el) {
          el.className = 'text-[11px] font-semibold text-rose-600 mt-1';
          el.textContent = 'O acesso com o Google não está configurado neste ambiente.';
        });
        return;
      }
      var s = document.createElement('script');
      s.src = 'https://accounts.google.com/gsi/client';
      s.async = true;
      s.onload = function () {
        global.google.accounts.id.initialize({
          client_id: self.clienteGoogle,
          callback: function (resposta) {
            self.credencial = resposta.credential;
            document.querySelectorAll('[data-google-status]').forEach(function (el) {
              el.textContent = 'Identidade confirmada com o Google.';
            });
          }
        });
        self.desenharGoogle();
      };
      document.head.appendChild(s);
    },

    desenharGoogle: function () {
      if (!global.google || !global.google.accounts) { return; }
      document.querySelectorAll('[data-google-botao]').forEach(function (el) {
        el.textContent = '';
        global.google.accounts.id.renderButton(el, { theme: 'outline', size: 'large', shape: 'pill', text: 'continue_with', locale: 'pt-BR' });
      });
    },

    /** Credencial a enviar: { senhaAtual } ou { credencialGoogle }. */
    ler: function (container) {
      var campo = container.querySelector('[data-senha-atual]');
      if (campo) { return { senhaAtual: campo.value }; }
      return { credencialGoogle: this.credencial };
    },

    informada: function (dados) {
      return !!(dados.senhaAtual || dados.credencialGoogle);
    },

    limpar: function (container) {
      var campo = container.querySelector('[data-senha-atual]');
      if (campo) { campo.value = ''; }
    }
  };
  global.PerfilReauth = Reauth;

  // ------------------------------------------------------------------- senha

  /** Indicador de forca so de apoio: quem decide (tamanho, senhas comuns...) e o servidor. */
  function forca(senha) {
    if (!senha) { return { pontos: 0, texto: 'Mínimo de 10 caracteres.', cor: 'bg-slate-300' }; }
    var classes = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^A-Za-z0-9]/].filter(function (r) { return r.test(senha); }).length;
    var pontos = Math.min(senha.length, 20) / 20 * 55 + classes * 11;
    if (senha.length < 10) { return { pontos: Math.min(pontos, 30), texto: 'Curta demais: use ao menos 10 caracteres.', cor: 'bg-rose-500' }; }
    if (pontos < 55) { return { pontos: pontos, texto: 'Fraca: misture letras, números e símbolos.', cor: 'bg-rose-500' }; }
    if (pontos < 75) { return { pontos: pontos, texto: 'Razoável.', cor: 'bg-amber-500' }; }
    if (pontos < 90) { return { pontos: pontos, texto: 'Boa.', cor: 'bg-lime-500' }; }
    return { pontos: pontos, texto: 'Forte.', cor: 'bg-emerald-500' };
  }

  function iniciarSenha() {
    var nova = $('novaSenha');
    nova.addEventListener('input', function () {
      var f = forca(nova.value);
      var barra = $('forcaBarra');
      barra.style.width = Math.max(4, Math.min(100, f.pontos)) + '%';
      barra.className = 'h-full transition-all ' + f.cor;
      $('forcaTexto').textContent = f.texto;
    });
    $('mostrarSenhas').addEventListener('change', function (e) {
      document.querySelectorAll('#formSenha input[type="password"], #formSenha input[data-mostrar]').forEach(function (campo) {
        campo.type = e.target.checked ? 'text' : 'password';
        campo.dataset.mostrar = '1';
      });
    });
    $('formSenha').addEventListener('submit', async function (e) {
      e.preventDefault();
      var form = e.currentTarget;
      var botao = form.querySelector('button[type="submit"]');
      var cred = Reauth.ler(form.querySelector('[data-reauth]'));
      if (!Reauth.informada(cred)) {
        mensagem('msgSenha', estado.perfil.senhaDefinida ? 'Informe a senha atual.' : 'Confirme sua identidade com o Google.', true);
        return;
      }
      if (nova.value.length < 10) {
        mensagem('msgSenha', 'A nova senha deve ter ao menos 10 caracteres.', true);
        return;
      }
      if (nova.value !== $('confirmaSenha').value) {
        mensagem('msgSenha', 'A confirmação não confere com a nova senha.', true);
        return;
      }
      ocupado(botao, true);
      mensagem('msgSenha', '');
      try {
        var resp = await Api.post('/api/perfil/senha', {
          senhaAtual: cred.senhaAtual, credencialGoogle: cred.credencialGoogle,
          novaSenha: nova.value, confirmacaoSenha: $('confirmaSenha').value
        });
        // As outras sessoes deixaram de valer; esta continua com o token novo.
        Auth.definirSessao(resp.token, Object.assign({}, Auth.getUser() || {}, resp.usuario));
        nova.value = '';
        $('confirmaSenha').value = '';
        nova.dispatchEvent(new Event('input'));
        Reauth.limpar(form.querySelector('[data-reauth]'));
        Reauth.credencial = null;
        mensagem('msgSenha', 'Senha alterada. As outras sessões foram encerradas e enviamos um aviso ao seu e-mail.');
        aplicar(await Api.get('/api/perfil'));
      } catch (err) {
        mensagem('msgSenha', err.message || 'Não foi possível alterar a senha.', true);
      } finally {
        ocupado(botao, false);
      }
    });
  }

  // ------------------------------------------------------------------ e-mail

  function iniciarEmail() {
    var form = $('formEmail');
    function abrir(sim) {
      form.classList.toggle('hidden', !sim);
      $('btnAlterarEmail').classList.toggle('hidden', sim);
      if (sim) { $('novoEmail').focus(); }
    }
    $('btnAlterarEmail').addEventListener('click', function () { mensagem('msgEmail', ''); abrir(true); });
    $('btnFecharEmail').addEventListener('click', function () { abrir(false); });

    form.addEventListener('submit', async function (e) {
      e.preventDefault();
      var botao = form.querySelector('button[type="submit"]');
      var area = form.querySelector('[data-reauth]');
      var cred = Reauth.ler(area);
      var novo = $('novoEmail').value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(novo)) {
        mensagem('msgEmail', 'Informe um e-mail válido.', true);
        return;
      }
      if (!Reauth.informada(cred)) {
        mensagem('msgEmail', estado.perfil.senhaDefinida ? 'Informe a senha atual.' : 'Confirme sua identidade com o Google.', true);
        return;
      }
      ocupado(botao, true);
      mensagem('msgEmail', '');
      try {
        var r = await Api.post('/api/perfil/email', {
          novoEmail: novo, senhaAtual: cred.senhaAtual, credencialGoogle: cred.credencialGoogle
        });
        $('novoEmail').value = '';
        Reauth.limpar(area);
        Reauth.credencial = null;
        abrir(false);
        mensagem('msgEmail', r.mensagem);
        aplicar(await Api.get('/api/perfil')); // mostra o pedido pendente, se houver
      } catch (err) {
        mensagem('msgEmail', err.message || 'Não foi possível pedir a troca de e-mail.', true);
      } finally {
        ocupado(botao, false);
      }
    });
  }

  global.Perfil = {
    $: $, iniciais: iniciais, mensagem: mensagem, ocupado: ocupado, pintarFoto: pintarFoto,
    aplicar: aplicar, sincronizarSessao: sincronizarSessao, perfil: function () { return estado.perfil; }
  };

  document.addEventListener('DOMContentLoaded', function () {
    // Visitante vai ao login e volta para ca depois (a API tambem responde 401).
    if (!Auth.isAuthenticated()) {
      location.href = Api.urlDeLogin(Auth.rotaAtual());
      return;
    }
    iniciarNome();
    iniciarFoto();
    iniciarSenha();
    iniciarEmail();
    carregar();
  });
})(window);
