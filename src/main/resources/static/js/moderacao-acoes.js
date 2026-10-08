/**
 * Ações de moderação reutilizadas pelo painel de admin e pela área de moderação: suspender e reativar conta (sempre com
 * motivo, que fica na trilha) e enviar mensagem direta ao usuário em nome da administração. O servidor é quem valida
 * e autoriza (só ADMIN); aqui é só interface. Cada função devolve uma Promise que resolve true se algo foi feito.
 */
(function (global) {
  'use strict';

  var esc = global.UI.escapar;
  var CAMPO = 'w-full border border-slate-300 rounded-lg p-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500';
  var niveisCache = null;

  function alvo(u) { return esc(u.nome) + ' <span class="text-slate-400">(' + esc(u.email) + ')</span>'; }

  async function niveis() {
    if (!niveisCache) { niveisCache = await Api.get('/api/admin/moderacao/niveis'); }
    return niveisCache;
  }

  /** Modal com validação: o botão de confirmar só fecha quando `validar` não devolve erro. Resolve com o que `coletar` devolve, ou null. */
  function formulario(opcoes) {
    return new Promise(function (resolve) {
      var pronto = false;
      function fim(valor, fechar) { if (!pronto) { pronto = true; resolve(valor); } fechar(); }
      var m = UI.modal({
        titulo: opcoes.titulo, largura: opcoes.largura || 'max-w-lg',
        corpo: opcoes.corpo + '<p data-erro class="hidden mt-3 text-xs font-semibold text-rose-700" role="alert"></p>',
        botoes: [
          { texto: 'Cancelar', classe: 'px-4 py-2 rounded-xl text-xs font-bold border border-slate-300 text-slate-700 hover:bg-slate-50',
            aoClicar: function (fechar) { fim(null, fechar); } },
          { texto: opcoes.confirmarTexto || 'Confirmar',
            classe: 'px-4 py-2 rounded-xl text-xs font-bold text-white ' + (opcoes.perigo ? 'bg-rose-600 hover:bg-rose-700' : 'bg-blue-600 hover:bg-blue-700'),
            aoClicar: function (fechar, el) {
              var erro = el.querySelector('[data-erro]');
              var dados = opcoes.coletar(el);
              var msg = opcoes.validar ? opcoes.validar(dados) : null;
              if (msg) { erro.textContent = msg; erro.classList.remove('hidden'); return; }
              erro.classList.add('hidden');
              fim(dados, fechar);
            } }
        ]
      });
      m.el.addEventListener('click', function (e) { if (e.target === m.el) { fim(null, m.fechar); } });
      var primeiro = m.el.querySelector('textarea, input, select');
      if (primeiro) { primeiro.focus(); }
    });
  }

  async function executar(fn, sucesso) {
    try {
      await fn();
      UI.toast(sucesso);
      return true;
    } catch (e) {
      UI.toast(e && e.message ? e.message : 'Não foi possível concluir.', 'erro');
      return false;
    }
  }

  async function pedirMotivo(opcoes) {
    var dados = await formulario({
      titulo: opcoes.titulo, confirmarTexto: opcoes.confirmarTexto, perigo: opcoes.perigo,
      corpo: '<p class="mb-3">' + opcoes.descricao + '</p>' +
        '<label class="block text-xs font-semibold text-slate-700">Motivo (fica registrado e é informado ao usuário)' +
        '<textarea data-motivo rows="3" maxlength="400" class="mt-1 ' + CAMPO + '"></textarea></label>',
      coletar: function (el) { return el.querySelector('[data-motivo]').value.trim(); },
      validar: function (v) { return v.length < 5 ? 'Escreva o motivo (mínimo de 5 caracteres).' : null; }
    });
    return dados;
  }

  async function suspender(u) {
    var motivo = await pedirMotivo({
      titulo: 'Suspender conta', confirmarTexto: 'Suspender', perigo: true,
      descricao: 'Suspender a conta de ' + alvo(u) + '? A pessoa perde o acesso agora, inclusive nas sessões abertas' +
        (u.papel === 'ANFITRIAO' ? ', e os anúncios dela saem do catálogo' : '') + '. Ela recebe um aviso com o motivo.'
    });
    if (motivo === null) { return false; }
    return executar(function () { return Api.post('/api/admin/moderacao/usuarios/' + u.id + '/suspensao', { motivo: motivo }); }, 'Conta suspensa.');
  }

  async function reativar(u) {
    var motivo = await pedirMotivo({
      titulo: 'Reativar conta', confirmarTexto: 'Reativar',
      descricao: 'Reativar a conta de ' + alvo(u) + '? A pessoa volta a poder entrar e é avisada.'
    });
    if (motivo === null) { return false; }
    return executar(function () { return Api.post('/api/admin/moderacao/usuarios/' + u.id + '/reativacao', { motivo: motivo }); }, 'Conta reativada.');
  }

  /** `padrao`: {nivel, assunto, texto} para pré-preencher. */
  async function mensagem(u, padrao) {
    padrao = padrao || {};
    var lista = await niveis();
    var dados = await formulario({
      titulo: 'Mensagem para o usuário', confirmarTexto: 'Enviar', largura: 'max-w-xl',
      corpo: '<p class="mb-3">Para ' + alvo(u) + ' (' + esc(u.papel) + '). A mensagem aparece em "Avisos" na conta da pessoa.</p>' +
        '<label class="block text-xs font-semibold text-slate-700">Enviar como' +
        '<select data-nivel class="mt-1 ' + CAMPO + '">' + lista.map(function (n) {
          return '<option value="' + esc(n.codigo) + '"' + (n.codigo === (padrao.nivel || 'ADMINISTRACAO') ? ' selected' : '') + '>' + esc(n.rotulo) + '</option>';
        }).join('') + '</select></label>' +
        '<label class="block text-xs font-semibold text-slate-700 mt-3">Assunto' +
        '<input data-assunto maxlength="150" value="' + esc(padrao.assunto || '') + '" class="mt-1 ' + CAMPO + '"></label>' +
        '<label class="block text-xs font-semibold text-slate-700 mt-3">Mensagem' +
        '<textarea data-texto rows="6" maxlength="2000" class="mt-1 ' + CAMPO + '">' + esc(padrao.texto || '') + '</textarea></label>' +
        '<label class="flex items-center gap-2 text-xs text-slate-700 mt-3"><input data-email type="checkbox" checked class="rounded border-slate-300"> Enviar também uma cópia por e-mail</label>',
      coletar: function (el) {
        return { nivel: el.querySelector('[data-nivel]').value, assunto: el.querySelector('[data-assunto]').value.trim(),
                 texto: el.querySelector('[data-texto]').value.trim(), copiarPorEmail: el.querySelector('[data-email]').checked };
      },
      validar: function (d) {
        if (d.assunto.length < 3) { return 'Informe o assunto (mínimo de 3 caracteres).'; }
        if (d.texto.length < 5) { return 'Escreva a mensagem (mínimo de 5 caracteres).'; }
        return null;
      }
    });
    if (!dados) { return false; }
    return executar(function () { return Api.post('/api/admin/moderacao/usuarios/' + u.id + '/mensagens', dados); }, 'Mensagem enviada.');
  }

  /** Busca um usuário por nome/e-mail e resolve com ele (ou null). */
  function escolherUsuario(titulo) {
    return new Promise(function (resolve) {
      var pronto = false, temporizador;
      function fim(v, fechar) { if (!pronto) { pronto = true; resolve(v); } fechar(); }
      var m = UI.modal({
        titulo: titulo || 'Escolher usuário', largura: 'max-w-lg',
        corpo: '<label class="block text-xs font-semibold text-slate-700">Buscar por nome ou e-mail' +
          '<input data-busca type="search" maxlength="80" class="mt-1 ' + CAMPO + '"></label>' +
          '<ul data-resultado class="mt-3 divide-y divide-slate-100 text-sm max-h-64 overflow-y-auto"></ul>',
        botoes: [{ texto: 'Cancelar', classe: 'px-4 py-2 rounded-xl text-xs font-bold border border-slate-300 text-slate-700 hover:bg-slate-50',
                   aoClicar: function (fechar) { fim(null, fechar); } }]
      });
      var lista = m.el.querySelector('[data-resultado]'), campo = m.el.querySelector('[data-busca]'), achados = [];
      campo.focus();
      campo.addEventListener('input', function () {
        clearTimeout(temporizador);
        temporizador = setTimeout(async function () {
          if (campo.value.trim().length < 2) { lista.innerHTML = ''; return; }
          try {
            var r = await Api.get('/api/admin/usuarios?tamanho=8&busca=' + encodeURIComponent(campo.value.trim()));
            achados = r.itens;
            lista.innerHTML = achados.length ? achados.map(function (u, i) {
              return '<li><button type="button" data-i="' + i + '" class="w-full text-left py-2 px-1 hover:bg-slate-50"><b>' + esc(u.nome) + '</b> ' +
                '<span class="text-xs text-slate-500">' + esc(u.email) + ' • ' + esc(u.papel) + (u.ativo ? '' : ' • suspensa') + '</span></button></li>';
            }).join('') : '<li class="py-2 text-xs text-slate-400">Ninguém encontrado.</li>';
          } catch (e) { lista.innerHTML = '<li class="py-2 text-xs text-rose-700">Falha na busca.</li>'; }
        }, 300);
      });
      lista.addEventListener('click', function (e) {
        var b = e.target.closest('[data-i]');
        if (b) { fim(achados[Number(b.getAttribute('data-i'))], m.fechar); }
      });
      m.el.addEventListener('click', function (e) { if (e.target === m.el) { fim(null, m.fechar); } });
    });
  }

  global.ModAcoes = { suspender: suspender, reativar: reativar, mensagem: mensagem, escolherUsuario: escolherUsuario, formulario: formulario };
})(window);
