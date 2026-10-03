/**
 * Fluxo de reserva do cliente: previa (valores, regras do imóvel e política de
 * cancelamento) -> criação pendente -> pagamento. Todo valor, regra e prazo vem
 * do servidor; aqui só há apresentação. A política exibida é provisória
 * (pendente de revisão jurídica) e fica visível ANTES do pagamento.
 */
(function (global) {
  'use strict';

  function esc(t) { return UI.escapar(t); }
  function moeda(v) { return 'R$ ' + Number(v || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  function br(s) { var p = String(s).split('-'); return p[2] + '/' + p[1] + '/' + p[0]; }

  function verPolitica() {
    var m = UI.modal({
      titulo: 'Política de cancelamento', largura: 'max-w-xl',
      corpo: '<div id="polTexto" class="whitespace-pre-line text-xs">Carregando...</div>',
      botoes: [{ texto: 'Fechar', classe: UI.BTN_PRIMARIO, aoClicar: function (fechar) { fechar(); } }]
    });
    Api.get('/api/politica-cancelamento', { ignorar401: true }).then(function (p) {
      m.el.querySelector('#polTexto').innerText = p.texto;
    }).catch(function () { m.el.querySelector('#polTexto').innerText = 'Não foi possível carregar a política agora.'; });
  }

  /** Resumo curto da política (para o anúncio e para a etapa de reserva). */
  function resumoDaPolitica(destino) {
    Api.get('/api/politica-cancelamento', { ignorar401: true }).then(function (p) {
      destino.innerHTML = esc(p.resumo) + ' <button type="button" data-politica class="font-semibold text-blue-700 underline underline-offset-2">Ver política completa</button>' +
        ' <span class="text-slate-400">(texto provisório, v. ' + esc(p.versao) + ')</span>';
      var b = destino.querySelector('[data-politica]');
      if (b) { b.addEventListener('click', verPolitica); }
    }).catch(function () { destino.innerText = ''; });
  }

  function linha(rotulo, valor, forte) {
    return '<div class="flex justify-between gap-3 ' + (forte ? 'font-bold text-slate-900 text-sm pt-2 mt-1 border-t border-slate-200' : 'text-xs') + '"><span>' + rotulo + '</span><span>' + valor + '</span></div>';
  }

  async function iniciar(imovel, dados) {
    if (!dados.checkin || !dados.checkout) { UI.toast('Escolha as datas de check-in e check-out.', 'erro'); return; }
    var previa;
    try {
      previa = await Api.get('/api/cliente/reservas/previa?imovelId=' + imovel.id + '&dataCheckin=' + dados.checkin +
        '&dataCheckout=' + dados.checkout + '&numeroHospedes=' + (dados.hospedes || 1));
    } catch (e) { UI.toast(e.message, 'erro'); return; }

    var pol = previa.politica;
    var semReembolso = pol.semDireitoAReembolso;
    var corpo =
      '<div class="space-y-1.5 p-3 rounded-xl bg-slate-50 border border-slate-200">' +
        '<p class="font-bold text-slate-900 text-sm">' + esc(previa.imovelTitulo) + '</p>' +
        '<p class="text-xs text-slate-500">' + br(previa.dataCheckin) + ' a ' + br(previa.dataCheckout) + ' · ' + previa.numeroDiarias +
          (previa.numeroDiarias === 1 ? ' diária' : ' diárias') + ' · ' + previa.numeroHospedes + (previa.numeroHospedes === 1 ? ' hóspede' : ' hóspedes') + '</p>' +
        linha(moeda(previa.precoDiaria) + ' x ' + previa.numeroDiarias, moeda(previa.subtotalDiarias)) +
        (Number(previa.taxaLimpeza) > 0 ? linha('Taxa de limpeza (uma vez por reserva)', moeda(previa.taxaLimpeza)) : '') +
        linha('Total', moeda(previa.total), true) +
      '</div>' +
      '<div class="mt-3 text-[11px] text-slate-600 leading-relaxed" id="rv-politica"></div>' +
      (semReembolso
        ? '<div class="mt-3 p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-800"><b>Atenção:</b> como faltam menos de ' + pol.antecedenciaHoras +
          ' horas para o check-in, esta reserva <b>não terá direito a reembolso</b> se for cancelada.' +
          '<label class="flex items-start gap-2 mt-2 cursor-pointer"><input type="checkbox" id="rv-ciente" class="mt-0.5 accent-rose-600"><span>Estou ciente de que esta reserva não terá direito a reembolso.</span></label></div>'
        : '<p class="mt-3 text-[11px] text-emerald-700">Cancelamento com reembolso integral até ' + UI.dataHora(pol.reembolsoIntegralAte) + '.</p>') +
      '<p id="rv-erro" class="hidden mt-3 text-xs font-semibold text-rose-700"></p>';

    var m = UI.modal({
      titulo: 'Confirmar reserva', corpo: corpo, largura: 'max-w-lg',
      botoes: [
        { texto: 'Voltar', classe: UI.BTN_NEUTRO, aoClicar: function (fechar) { fechar(); } },
        { texto: 'Reservar e ir para o pagamento', classe: UI.BTN_PRIMARIO, aoClicar: async function (fechar, el, btn) {
            var erro = el.querySelector('#rv-erro');
            if (semReembolso && !el.querySelector('#rv-ciente').checked) {
              erro.innerText = 'Confirme que está ciente de que não há direito a reembolso.'; erro.classList.remove('hidden'); return;
            }
            btn.disabled = true;
            try {
              var r = await Api.post('/api/cliente/reservas', { imovelId: imovel.id, dataCheckin: dados.checkin,
                dataCheckout: dados.checkout, numeroHospedes: dados.hospedes || 1, cienteSemReembolso: semReembolso });
              fechar();
              pagar(r);
            } catch (e) { erro.innerText = e.message; erro.classList.remove('hidden'); btn.disabled = false; }
          } }
      ]
    });
    resumoDaPolitica(m.el.querySelector('#rv-politica'));
  }

  /** Pagamento simulado (sandbox): não se digitam dados de cartão; a recusa pode ser simulada. */
  function pagar(reserva) {
    var m = UI.modal({
      titulo: 'Pagamento', largura: 'max-w-md',
      corpo: '<p class="text-xs mb-3">Reserva <b>#' + reserva.id + '</b> criada, aguardando pagamento até <b>' + UI.dataHora(reserva.expiraEm) + '</b>. Depois disso as datas são liberadas.</p>' +
        '<div class="p-3 rounded-xl bg-slate-50 border border-slate-200">' + linha('Total a pagar', moeda(reserva.total), true) + '</div>' +
        '<div class="mt-3 p-3 rounded-xl bg-amber-50 border border-amber-200 text-[11px] text-amber-900"><b>Ambiente de teste:</b> o pagamento é simulado e nenhum dado de cartão é solicitado.' +
          '<label class="block mt-2"><span class="font-semibold">Resultado simulado</span>' +
          '<select id="pg-resultado" class="mt-1 w-full border border-amber-300 rounded-lg p-1.5 bg-white"><option value="ok">Pagamento aprovado</option><option value="recusado">Pagamento recusado</option></select></label></div>' +
        '<p id="pg-erro" class="hidden mt-3 text-xs font-semibold text-rose-700"></p>',
      botoes: [
        { texto: 'Pagar depois', classe: UI.BTN_NEUTRO, aoClicar: function (fechar) { fechar(); location.href = '/reserva.html?id=' + reserva.id; } },
        { texto: 'Pagar ' + moeda(reserva.total), classe: UI.BTN_PRIMARIO, aoClicar: async function (fechar, el, btn) {
            var erro = el.querySelector('#pg-erro');
            erro.classList.add('hidden');
            btn.disabled = true;
            try {
              await Api.post('/api/cliente/reservas/' + reserva.id + '/pagar', { token: el.querySelector('#pg-resultado').value });
              UI.toast('Pagamento aprovado! Reserva confirmada.');
              fechar();
              location.href = '/reserva.html?id=' + reserva.id;
            } catch (e) { erro.innerText = e.message; erro.classList.remove('hidden'); btn.disabled = false; }
          } }
      ]
    });
    m.el.addEventListener('click', function (e) { if (e.target === m.el) { /* fechar so por botao: evita perder o pagamento */ } });
  }

  global.Reservar = { iniciar: iniciar, pagar: pagar, verPolitica: verPolitica, resumoDaPolitica: resumoDaPolitica };
})(window);
