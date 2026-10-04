/**
 * Ações sobre o ciclo de vida do anúncio, com os modais exigidos em cada
 * passo. Compartilhadas pelo painel e pelo formulário para que o aviso e a
 * regra sejam sempre os mesmos. Todas devolvem o anúncio atualizado, ou null
 * se a pessoa desistiu; erros do servidor viram aviso na tela.
 *
 * Quem decide é o servidor: estas funções só pedem. Se ele negar (prazo,
 * estado, permissão), a mensagem dele é exibida.
 */
(function (global) {
  'use strict';

  var BASE = '/api/gestor/imoveis/';

  function falha(e) {
    UI.toast(e.message || 'Não foi possível concluir a ação.', 'erro');
    return null;
  }

  function horas(n) {
    return n + (n === 1 ? ' hora' : ' horas');
  }

  /** Modal de aviso antes de tirar o anúncio do ar para edição. */
  function iniciarEdicao(anuncio) {
    var h = anuncio.republicacaoHoras;
    var agendado = anuncio.status === 'REPUBLICACAO_AGENDADA';
    var corpo =
      '<p class="mb-3"><b>Atenção:</b> ao iniciar a edição, o anúncio <b>sai do ar agora</b> e deixa de aparecer ' +
      'no catálogo, em buscas e na API pública. Novas reservas ficam indisponíveis enquanto ele estiver fora do ar; ' +
      'reservas já existentes não são afetadas.</p>' +
      '<ul class="list-disc pl-5 space-y-1.5 mb-3">' +
        '<li>Depois de <b>Confirmar alteração</b>, o anúncio continua fora do ar por mais <b>' + horas(h) +
        '</b> e então volta sozinho ao catálogo.</li>' +
        '<li>O tempo gasto editando <b>não</b> conta nessas ' + horas(h) + '. Não há prazo máximo para editar.</li>' +
        '<li>Você pode <b>descartar a edição</b> a qualquer momento: o anúncio volta ao estado anterior, ' +
        'imediatamente e sem espera.</li>' +
      '</ul>' +
      (agendado
        ? '<p class="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800">Este anúncio tem uma ' +
          'republicação agendada. Ela será suspensa; o prazo de ' + horas(h) + ' recomeça somente quando você ' +
          'confirmar a nova alteração.</p>'
        : '') +
      '<p class="mt-3 text-xs text-slate-500">Se você não confirmar, nada muda e o anúncio continua no ar.</p>';
    return UI.confirmar({
      titulo: 'Editar anúncio: ele sairá do ar', corpoHtml: corpo,
      confirmarTexto: 'Sim, tirar do ar e editar', perigo: true
    }).then(function (ok) {
      if (!ok) { return null; }
      return Api.post(BASE + anuncio.id + '/edicao/iniciar', { confirmado: true }).catch(falha);
    });
  }

  /** Confirma a alteração: exige o aceite do termo e agenda a republicação. */
  function confirmarAlteracao(anuncio) {
    var h = anuncio.republicacaoHoras;
    return UI.aceitarTermo({
      titulo: 'Confirmar alteração do anúncio',
      confirmarTexto: 'Aceitar e confirmar alteração',
      aviso: 'Ao confirmar, as alterações são aplicadas e o anúncio <b>continua fora do ar por mais ' + horas(h) +
             '</b>, voltando automaticamente ao catálogo depois disso. A partir daqui não é mais possível ' +
             'descartar esta edição.'
    }).then(function (ok) {
      if (!ok) { return null; }
      return Api.post(BASE + anuncio.id + '/edicao/confirmar', { aceiteTermo: true }).catch(falha);
    });
  }

  /** Descarta o rascunho e devolve o anúncio ao ar, como estava, na hora. */
  function descartarEdicao(anuncio) {
    // Edição iniciada sobre uma republicação agendada: o descarte restaura o agendamento original.
    var restauraAgendamento = anuncio.edicaoEstadoOrigem === 'REPUBLICACAO_AGENDADA' && !!anuncio.republicarOriginalEm &&
      UI.instante(anuncio.republicarOriginalEm) > UI.relogioDoServidor(anuncio.agora)();
    var destino = restauraAgendamento
      ? '<p>O anúncio volta ao estado anterior: a <b>republicação agendada para ' + UI.dataHora(anuncio.republicarOriginalEm) +
        '</b> é restaurada, exatamente como estava, sem nenhuma suspensão adicional.</p>'
      : '<p>O anúncio volta <b>imediatamente</b> ao ar, exatamente como estava antes da edição, ' +
        'sem nenhuma suspensão adicional.</p>';
    return UI.confirmar({
      titulo: 'Descartar edição?',
      corpoHtml:
        '<p class="mb-2"><b>As alterações não salvas serão perdidas.</b> O rascunho será apagado.</p>' + destino,
      confirmarTexto: 'Descartar edição', perigo: true
    }).then(function (ok) {
      if (!ok) { return null; }
      return Api.post(BASE + anuncio.id + '/edicao/descartar', { confirmado: true }).catch(falha);
    });
  }

  /** Publicação: novo aceite do termo de uso. */
  function publicar(anuncio) {
    return UI.aceitarTermo({
      titulo: 'Publicar anúncio',
      confirmarTexto: 'Aceitar e publicar',
      aviso: 'Ao publicar, o anúncio entra no catálogo principal imediatamente.'
    }).then(function (ok) {
      if (!ok) { return null; }
      return Api.post(BASE + anuncio.id + '/publicar', { aceiteTermo: true }).catch(falha);
    });
  }

  global.AnuncioAcoes = {
    iniciarEdicao: iniciarEdicao,
    confirmarAlteracao: confirmarAlteracao,
    descartarEdicao: descartarEdicao,
    publicar: publicar
  };
})(window);
