/**
 * Peças de interface compartilhadas pelo painel do gestor e pelo formulário
 * de anúncio: modais de confirmação, aceite do termo de uso, avisos e
 * formatação de datas e contagens regressivas.
 */
(function (global) {
  'use strict';

  function escapar(texto) {
    var d = document.createElement('div');
    d.textContent = texto == null ? '' : String(texto);
    return d.innerHTML;
  }

  /** Aviso rápido no canto da tela. */
  function toast(mensagem, tipo) {
    var cores = tipo === 'erro' ? 'bg-rose-600' : (tipo === 'aviso' ? 'bg-amber-600' : 'bg-emerald-600');
    var caixa = document.getElementById('toasts');
    if (!caixa) {
      caixa = document.createElement('div');
      caixa.id = 'toasts';
      caixa.className = 'fixed bottom-4 right-4 z-[100] space-y-2 max-w-sm';
      caixa.setAttribute('aria-live', 'polite');
      document.body.appendChild(caixa);
    }
    var t = document.createElement('div');
    t.className = 'text-white text-xs font-semibold px-4 py-3 rounded-xl shadow-lg ' + cores;
    t.innerText = mensagem;
    caixa.appendChild(t);
    setTimeout(function () { t.remove(); }, tipo === 'erro' ? 8000 : 4500);
  }

  /**
   * Modal genérico. `corpo` é HTML já escapado pelo chamador.
   * `botoes`: [{texto, classe, aoClicar(fechar, modal)}]. Devolve {fechar, el}.
   */
  function modal(opcoes) {
    var fundo = document.createElement('div');
    fundo.className = 'fixed inset-0 z-[90] bg-slate-900/60 flex items-center justify-center p-4';
    fundo.setAttribute('role', 'dialog');
    fundo.setAttribute('aria-modal', 'true');
    fundo.innerHTML =
      '<div class="bg-white rounded-2xl shadow-2xl w-full ' + (opcoes.largura || 'max-w-md') + ' max-h-[90vh] flex flex-col">' +
        '<div class="px-6 pt-5 pb-3 border-b border-slate-100"><h3 class="text-base font-bold text-slate-900">' +
          escapar(opcoes.titulo) + '</h3></div>' +
        '<div class="px-6 py-4 overflow-y-auto text-sm text-slate-600 leading-relaxed" data-corpo>' + (opcoes.corpo || '') + '</div>' +
        '<div class="px-6 py-4 border-t border-slate-100 flex flex-wrap justify-end gap-2" data-botoes></div>' +
      '</div>';
    document.body.appendChild(fundo);

    function fechar() { fundo.remove(); document.removeEventListener('keydown', aoTecla); }
    function aoTecla(e) { if (e.key === 'Escape') { fechar(); } }
    document.addEventListener('keydown', aoTecla);

    var area = fundo.querySelector('[data-botoes]');
    (opcoes.botoes || []).forEach(function (b) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = b.classe || 'px-4 py-2 rounded-xl text-xs font-bold border border-slate-300 text-slate-700 hover:bg-slate-50';
      btn.innerText = b.texto;
      btn.addEventListener('click', function () { b.aoClicar(fechar, fundo, btn); });
      area.appendChild(btn);
    });
    return { fechar: fechar, el: fundo };
  }

  var BTN_PRIMARIO = 'px-4 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white disabled:opacity-40 disabled:cursor-not-allowed';
  var BTN_PERIGO = 'px-4 py-2 rounded-xl text-xs font-bold bg-rose-600 hover:bg-rose-700 text-white disabled:opacity-40 disabled:cursor-not-allowed';
  var BTN_NEUTRO = 'px-4 py-2 rounded-xl text-xs font-bold border border-slate-300 text-slate-700 hover:bg-slate-50';

  /** Pergunta sim/não. Resolve true ao confirmar, false ao cancelar ou fechar. */
  function confirmar(opcoes) {
    return new Promise(function (resolve) {
      var feito = false;
      function fim(valor, fechar) { if (!feito) { feito = true; resolve(valor); } fechar(); }
      var m = modal({
        titulo: opcoes.titulo,
        corpo: opcoes.corpoHtml || ('<p>' + escapar(opcoes.mensagem) + '</p>'),
        botoes: [
          { texto: opcoes.cancelarTexto || 'Cancelar', classe: BTN_NEUTRO, aoClicar: function (fechar) { fim(false, fechar); } },
          { texto: opcoes.confirmarTexto || 'Confirmar', classe: opcoes.perigo ? BTN_PERIGO : BTN_PRIMARIO,
            aoClicar: function (fechar) { fim(true, fechar); } }
        ]
      });
      // Fechar com Esc ou clicando fora conta como cancelar.
      m.el.addEventListener('click', function (e) { if (e.target === m.el) { fim(false, m.fechar); } });
    });
  }

  /**
   * Lê o termo de uso vigente e exige o aceite explícito (checkbox) antes de
   * liberar o botão. Resolve true somente se a pessoa aceitar.
   */
  function aceitarTermo(opcoes) {
    opcoes = opcoes || {};
    return new Promise(function (resolve) {
      var resolvido = false;
      function fim(v, fechar) { if (!resolvido) { resolvido = true; resolve(v); } fechar(); }
      var m = modal({
        titulo: opcoes.titulo || 'Termo de uso',
        largura: 'max-w-xl',
        corpo: (opcoes.aviso ? '<div class="mb-3 p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800">' + opcoes.aviso + '</div>' : '') +
               '<div id="termoTexto" class="whitespace-pre-line text-xs bg-slate-50 border border-slate-200 rounded-xl p-3 max-h-64 overflow-y-auto">Carregando termo...</div>' +
               '<label class="flex items-start gap-2 mt-4 text-xs text-slate-700 cursor-pointer">' +
                 '<input type="checkbox" id="termoAceite" class="mt-0.5 accent-blue-600">' +
                 '<span>Li e aceito o termo de uso e responsabilidade do anunciante.</span></label>',
        botoes: [
          { texto: 'Cancelar', classe: BTN_NEUTRO, aoClicar: function (fechar) { fim(false, fechar); } },
          { texto: opcoes.confirmarTexto || 'Aceitar e continuar', classe: BTN_PRIMARIO,
            aoClicar: function (fechar, el) {
              if (el.querySelector('#termoAceite').checked) { fim(true, fechar); }
            } }
        ]
      });
      var btn = m.el.querySelector('[data-botoes] button:last-child');
      var caixa = m.el.querySelector('#termoAceite');
      btn.disabled = true;
      caixa.addEventListener('change', function () { btn.disabled = !caixa.checked; });
      global.Api.get('/api/termos/atual', { ignorar401: true }).then(function (t) {
        m.el.querySelector('#termoTexto').innerText = t.titulo + ' (versão ' + t.versao + ')\n\n' + t.texto;
      }).catch(function () {
        m.el.querySelector('#termoTexto').innerText = 'Não foi possível carregar o termo agora. Tente novamente.';
        caixa.disabled = true;
      });
      m.el.addEventListener('click', function (e) { if (e.target === m.el) { fim(false, m.fechar); } });
    });
  }

  /** Só leitura do termo (link "ler o termo" do formulário). */
  function lerTermo() {
    var m = modal({
      titulo: 'Termo de uso', largura: 'max-w-xl',
      corpo: '<div id="termoTexto" class="whitespace-pre-line text-xs">Carregando termo...</div>',
      botoes: [{ texto: 'Fechar', classe: BTN_PRIMARIO, aoClicar: function (fechar) { fechar(); } }]
    });
    global.Api.get('/api/termos/atual', { ignorar401: true }).then(function (t) {
      m.el.querySelector('#termoTexto').innerText = t.titulo + ' (versão ' + t.versao + ')\n\n' + t.texto;
    }).catch(function () {
      m.el.querySelector('#termoTexto').innerText = 'Não foi possível carregar o termo agora.';
    });
  }

  // ----------------------------------------------------------------- tempo

  function doisDigitos(n) { return n < 10 ? '0' + n : String(n); }

  /** "03/10/2026 14:05". Datas da API vêm sem fuso; são exibidas como chegam. */
  function dataHora(iso) {
    if (!iso) { return '—'; }
    var d = new Date(iso);
    if (isNaN(d)) { return '—'; }
    return doisDigitos(d.getDate()) + '/' + doisDigitos(d.getMonth() + 1) + '/' + d.getFullYear() +
           ' ' + doisDigitos(d.getHours()) + ':' + doisDigitos(d.getMinutes());
  }

  /** 3725000 ms -> "1h 02min 05s" (ou "02min 05s" abaixo de 1h). */
  function contagem(ms) {
    if (ms <= 0) { return '0s'; }
    var s = Math.floor(ms / 1000);
    var h = Math.floor(s / 3600);
    var m = Math.floor((s % 3600) / 60);
    var seg = s % 60;
    return (h > 0 ? h + 'h ' : '') + (h > 0 ? doisDigitos(m) : m) + 'min ' + doisDigitos(seg) + 's';
  }

  /** "2 dias" / "5 horas" / "40 min" a partir de milissegundos. */
  function duracaoLonga(ms) {
    var min = Math.floor(ms / 60000);
    if (min < 60) { return min + ' min'; }
    var h = Math.floor(min / 60);
    if (h < 48) { return h + (h === 1 ? ' hora' : ' horas'); }
    return Math.floor(h / 24) + ' dias';
  }

  /**
   * Relógio alinhado ao do servidor. As datas da API não têm fuso, então a
   * diferença entre "agora do servidor" e o relógio do navegador é medida
   * uma vez e aplicada a todas as contagens.
   */
  function relogioDoServidor(agoraIso) {
    var deslocamento = new Date(agoraIso).getTime() - Date.now();
    return function () { return Date.now() + deslocamento; };
  }

  global.UI = {
    escapar: escapar, toast: toast, modal: modal, confirmar: confirmar, aceitarTermo: aceitarTermo,
    lerTermo: lerTermo, dataHora: dataHora, contagem: contagem, duracaoLonga: duracaoLonga,
    relogioDoServidor: relogioDoServidor,
    BTN_PRIMARIO: BTN_PRIMARIO, BTN_PERIGO: BTN_PERIGO, BTN_NEUTRO: BTN_NEUTRO
  };
})(window);
