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
    carregar();
  });
})(window);
