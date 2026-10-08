/**
 * Caixa de avisos da administração, para qualquer usuário logado. Só lê os próprios avisos (o servidor garante).
 * O texto é sempre inserido como texto (textContent), nunca como HTML. Abrir um aviso o marca como lido.
 */
(function (global) {
  'use strict';

  function el(tag, classe, texto) {
    var e = document.createElement(tag);
    if (classe) { e.className = classe; }
    if (texto != null) { e.textContent = texto; }
    return e;
  }

  function quando(iso) {
    var d = new Date(iso);
    return isNaN(d) ? '' : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  function cartao(c) {
    var li = el('li', 'bg-white border rounded-2xl shadow-sm p-4 ' + (c.lido ? 'border-slate-200' : 'border-blue-300 ring-1 ring-blue-100'));
    var topo = el('div', 'flex items-start justify-between gap-3');
    var esq = el('div');
    esq.appendChild(el('p', 'text-[11px] font-semibold text-blue-700', c.remetente));
    esq.appendChild(el('h3', 'text-sm font-bold text-slate-900 mt-0.5', c.assunto));
    topo.appendChild(esq);
    var dir = el('div', 'text-right shrink-0');
    dir.appendChild(el('p', 'text-[10px] text-slate-400', quando(c.criadoEm)));
    var selo = el('span', 'inline-block mt-1 px-2 py-0.5 rounded-full bg-blue-600 text-white text-[10px] font-bold', 'Novo');
    selo.hidden = c.lido;
    dir.appendChild(selo);
    topo.appendChild(dir);
    li.appendChild(topo);
    li.appendChild(el('p', 'text-sm text-slate-700 mt-3 whitespace-pre-line leading-relaxed', c.texto));

    if (!c.lido) {
      var botao = el('button', 'mt-3 text-xs font-bold text-blue-700 hover:underline', 'Marcar como lido');
      botao.type = 'button';
      botao.addEventListener('click', async function () {
        try {
          await Api.post('/api/comunicados/' + c.id + '/lido', {});
          li.className = 'bg-white border border-slate-200 rounded-2xl shadow-sm p-4';
          selo.hidden = true;
          botao.remove();
          if (global.Navegacao) { global.Navegacao.atualizarAvisos(); }
        } catch (e) {
          var erro = document.getElementById('erroPagina');
          erro.textContent = 'Não foi possível marcar como lido: ' + e.message;
          erro.classList.remove('hidden');
        }
      });
      li.appendChild(botao);
    }
    return li;
  }

  async function carregar() {
    var erro = document.getElementById('erroPagina');
    if (!Auth.isAuthenticated()) {
      global.location.href = Api.urlDeLogin('/comunicados.html');
      return;
    }
    try {
      var lista = await Api.get('/api/comunicados');
      var ul = document.getElementById('lista');
      ul.textContent = '';
      lista.forEach(function (c) { ul.appendChild(cartao(c)); });
      document.getElementById('vazio').classList.toggle('hidden', lista.length > 0);
    } catch (e) {
      erro.textContent = 'Não foi possível carregar os avisos: ' + e.message;
      erro.classList.remove('hidden');
    } finally {
      document.getElementById('carregando').classList.add('hidden');
    }
  }

  document.addEventListener('DOMContentLoaded', carregar);
})(window);
