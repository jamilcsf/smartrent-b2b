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
    carregar();
  });
})(window);
