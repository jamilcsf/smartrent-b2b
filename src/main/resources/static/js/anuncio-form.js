/**
 * Formulário de anúncio: cadastro, edição direta (pré-publicação) e edição de
 * anúncio publicado (rascunho).
 *
 * Três modos, escolhidos pelo estado do anúncio:
 *  - criar:    sem ?id. Tudo fica na memória (inclusive os arquivos) até
 *              "Finalizar cadastro"; só então o anúncio é criado e as mídias
 *              sobem uma a uma, com barra de progresso.
 *  - direto:   ?id de um anúncio em pré-publicação. Os campos são salvos no
 *              botão; as mídias valem na hora.
 *  - rascunho: ?id de um anúncio EM_EDICAO. Os campos viram rascunho (autosave
 *              e botão) e só afetam o anúncio vivo na confirmação.
 *
 * Os limites e formatos aqui são conveniência (resposta imediata): o servidor
 * valida tudo de novo e é quem decide.
 */
(function (global) {
  'use strict';

  var LIM_IMAGENS = 14, LIM_VIDEOS = 2, DURACAO_MAX = 120;
  var MAX_IMG = 10 * 1024 * 1024, MAX_VID = 100 * 1024 * 1024;
  var SUGESTOES = ['Wi-Fi', 'Ar-condicionado', 'Piscina', 'Churrasqueira', 'Cozinha equipada', 'Vista para o mar',
    'Pet friendly', 'Estacionamento', 'Máquina de lavar', 'TV', 'Varanda', 'Café da manhã'];
  var ROTULOS_TIPO = { APARTAMENTO: 'Apartamento', CASA: 'Casa', KITNET: 'Kitnet', POUSADA: 'Pousada',
    CHALE: 'Chalé', LOFT: 'Loft', OUTRO: 'Outro' };
  var REGEX_WHATSAPP = /^https:\/\/(wa\.me\/\d{8,15}(\?\S*)?|(api\.)?whatsapp\.com\/send\?\S*phone=\d{8,15}\S*)$/;

  var S = { modo: 'criar', id: null, anuncio: null, midias: [], capaKey: null, comodidades: [],
            sujo: false, ocupado: false, seq: 0, timerAutosave: null, viewer: null };

  function $(id) { return document.getElementById(id); }
  function escapar(t) { return UI.escapar(t); }

  // ------------------------------------------------------------- campos

  function texto(id) { return $(id).value.trim(); }
  function numero(id) {
    var v = $(id).value.trim();
    return v === '' ? null : Number(v);
  }

  function lerForm() {
    return {
      titulo: texto('f-titulo'),
      descricao: $('f-descricao').value.trim(),
      tipoImovel: $('f-tipo').value || null,
      metragemQuadrada: numero('f-area'),
      numeroQuartos: numero('f-quartos'),
      numeroBanheiros: numero('f-banheiros'),
      vagasGaragem: numero('f-vagas'),
      capacidadeHospedes: numero('f-capacidade'),
      comodidades: S.comodidades.slice(),
      cep: texto('f-cep'),
      logradouro: texto('f-logradouro'),
      numero: texto('f-numero'),
      complemento: texto('f-complemento') || null,
      bairro: texto('f-bairro'),
      cidade: texto('f-cidade'),
      estado: texto('f-uf').toUpperCase(),
      whatsappLink: texto('f-whatsapp') || null,
      valorDiaria: S.modo === 'rascunho' ? numero('f-preco') : null
    };
  }

  function preencherForm(d) {
    $('f-titulo').value = d.titulo || '';
    $('f-descricao').value = d.descricao || '';
    $('f-tipo').value = d.tipoImovel || '';
    $('f-area').value = d.metragemQuadrada != null ? d.metragemQuadrada : '';
    $('f-quartos').value = d.numeroQuartos != null ? d.numeroQuartos : '';
    $('f-banheiros').value = d.numeroBanheiros != null ? d.numeroBanheiros : '';
    $('f-vagas').value = d.vagasGaragem != null ? d.vagasGaragem : '';
    $('f-capacidade').value = d.capacidadeHospedes != null ? d.capacidadeHospedes : '';
    S.comodidades = (d.comodidades || []).slice();
    $('f-cep').value = d.cep || '';
    $('f-logradouro').value = d.logradouro || '';
    $('f-numero').value = d.numero || '';
    $('f-complemento').value = d.complemento || '';
    $('f-bairro').value = d.bairro || '';
    $('f-cidade').value = d.cidade || '';
    $('f-uf').value = d.estado || '';
    $('f-whatsapp').value = d.whatsappLink || '';
    $('f-preco').value = d.valorDiaria != null ? d.valorDiaria : '';
    renderComodidades();
  }

  // --------------------------------------------------------- validação

  /** Espelha as regras do servidor para responder na hora, antes de qualquer envio. */
  function validar(d) {
    var e = {};
    if (!d.titulo) { e.titulo = 'Informe o título do anúncio.'; }
    if (!d.tipoImovel) { e.tipoImovel = 'Informe o tipo do imóvel.'; }
    if (d.metragemQuadrada == null || !(d.metragemQuadrada > 0)) { e.metragemQuadrada = 'Informe a área em m² (maior que zero).'; }
    if (d.numeroQuartos == null || d.numeroQuartos < 0) { e.numeroQuartos = 'Informe o número de quartos.'; }
    if (d.numeroBanheiros == null || d.numeroBanheiros < 0) { e.numeroBanheiros = 'Informe o número de banheiros.'; }
    if (d.vagasGaragem != null && d.vagasGaragem < 0) { e.vagasGaragem = 'O número de vagas não pode ser negativo.'; }
    if (d.capacidadeHospedes == null || d.capacidadeHospedes < 1) { e.capacidadeHospedes = 'A capacidade deve ser de ao menos 1 hóspede.'; }
    if (!/^\d{5}-?\d{3}$/.test(d.cep)) { e.cep = 'CEP inválido. Use o formato 00000-000.'; }
    if (!d.logradouro) { e.logradouro = 'Informe o logradouro.'; }
    if (!d.numero) { e.numero = 'Informe o número.'; }
    if (!d.bairro) { e.bairro = 'Informe o bairro.'; }
    if (!d.cidade) { e.cidade = 'Informe a cidade.'; }
    if (!/^[A-Za-z]{2}$/.test(d.estado)) { e.estado = 'UF inválida. Use duas letras.'; }
    if (d.whatsappLink && !REGEX_WHATSAPP.test(d.whatsappLink)) {
      e.whatsappLink = 'Link do WhatsApp inválido. Use um link wa.me ou api.whatsapp.com.';
    }
    if (S.modo === 'rascunho' && d.valorDiaria != null && d.valorDiaria < 1) {
      e.valorDiaria = 'O valor da diária deve ser de ao menos R$ 1,00.';
    }
    return e;
  }

  var CAMPO_DO_ERRO = { titulo: 'f-titulo', descricao: 'f-descricao', tipoImovel: 'f-tipo', metragemQuadrada: 'f-area',
    numeroQuartos: 'f-quartos', numeroBanheiros: 'f-banheiros', vagasGaragem: 'f-vagas', capacidadeHospedes: 'f-capacidade',
    cep: 'f-cep', logradouro: 'f-logradouro', numero: 'f-numero', complemento: 'f-complemento', bairro: 'f-bairro',
    cidade: 'f-cidade', estado: 'f-uf', whatsappLink: 'f-whatsapp', valorDiaria: 'f-preco', comodidades: 'f-comodidade-nova' };

  function mostrarErros(erros) {
    document.querySelectorAll('.erro').forEach(function (p) { p.classList.remove('ativo'); p.innerText = ''; });
    document.querySelectorAll('.campo.invalido').forEach(function (c) { c.classList.remove('invalido'); });
    var primeiro = null;
    Object.keys(erros).forEach(function (chave) {
      var campo = chave.replace(/^dados\./, '').replace(/\[\d+\].*$/, '');
      var p = document.querySelector('[data-erro="' + campo + '"]');
      if (p) { p.innerText = erros[chave]; p.classList.add('ativo'); }
      var input = $(CAMPO_DO_ERRO[campo]);
      if (input) { input.classList.add('invalido'); if (!primeiro) { primeiro = input; } }
    });
    if (primeiro) { primeiro.scrollIntoView({ behavior: 'smooth', block: 'center' }); primeiro.focus(); }
  }

  function erroGeral(mensagem) {
    var c = $('erroGeral');
    c.innerText = mensagem || '';
    c.classList.toggle('hidden', !mensagem);
  }

  // -------------------------------------------------------- comodidades

  function renderComodidades() {
    var custom = S.comodidades.filter(function (c) { return SUGESTOES.indexOf(c) < 0; });
    $('sugestoesComodidades').innerHTML = SUGESTOES.map(function (c) {
      var on = S.comodidades.indexOf(c) >= 0;
      return '<button type="button" data-comodidade="' + escapar(c) + '" aria-pressed="' + on + '" class="px-3 py-1.5 rounded-full text-xs font-semibold border transition ' +
        (on ? 'bg-blue-600 border-blue-600 text-white' : 'bg-white border-slate-300 text-slate-600 hover:bg-slate-50') + '">' + escapar(c) + '</button>';
    }).join('') + custom.map(function (c) {
      return '<span class="px-3 py-1.5 rounded-full text-xs font-semibold bg-blue-600 text-white flex items-center gap-1.5">' + escapar(c) +
        '<button type="button" data-remover-comodidade="' + escapar(c) + '" aria-label="Remover ' + escapar(c) + '">✕</button></span>';
    }).join('');
  }

  function alternarComodidade(nome) {
    var i = S.comodidades.indexOf(nome);
    if (i >= 0) { S.comodidades.splice(i, 1); } else { S.comodidades.push(nome); }
    renderComodidades();
    marcarSujo();
  }

  // ---------------------------------------------------------------- CEP

  function formatarCep(valor) {
    var d = valor.replace(/\D/g, '').slice(0, 8);
    return d.length > 5 ? d.slice(0, 5) + '-' + d.slice(5) : d;
  }

  async function buscarCep() {
    var cep = $('f-cep').value.replace(/\D/g, '');
    var dica = $('dicaCep');
    if (cep.length !== 8) { return; }
    dica.innerText = 'Buscando endereço...';
    try {
      var r = await fetch('https://viacep.com.br/ws/' + cep + '/json/');
      var d = await r.json();
      if (d.erro) { dica.innerText = 'CEP não encontrado. Preencha o endereço manualmente.'; return; }
      if (d.logradouro) { $('f-logradouro').value = d.logradouro; }
      if (d.bairro) { $('f-bairro').value = d.bairro; }
      if (d.localidade) { $('f-cidade').value = d.localidade; }
      if (d.uf) { $('f-uf').value = d.uf; }
      dica.innerText = 'Endereço preenchido. Confira e informe o número.';
      marcarSujo();
    } catch (e) {
      dica.innerText = 'Não foi possível consultar o CEP agora. Preencha o endereço manualmente.';
    }
  }

  // -------------------------------------------------------------- mídias

  function efetivas() { return S.midias.filter(function (m) { return m.estado !== 'REMOVIDA'; }); }
  function qtdImagens() { return efetivas().filter(function (m) { return m.tipo !== 'VIDEO'; }).length; }
  function qtdVideos() { return efetivas().filter(function (m) { return m.tipo === 'VIDEO'; }).length; }

  function erroMidia(mensagem) {
    var p = $('erroMidia');
    p.innerText = mensagem || '';
    p.classList.toggle('hidden', !mensagem);
  }

  function deServidor(m) {
    return { key: 's' + m.id, id: m.id, local: false, tipo: m.tipo, estado: m.estado, url: m.url,
      miniaturaUrl: m.miniaturaUrl, capa: m.capa, largura: m.largura, altura: m.altura,
      duracaoSegundos: m.duracaoSegundos, progresso: 100, erro: null };
  }

  function carregarImagem(url) {
    return new Promise(function (ok, falha) {
      var img = new Image();
      img.onload = function () { ok(img); };
      img.onerror = function () { falha(new Error('Não foi possível ler a imagem.')); };
      img.src = url;
    });
  }

  function lerDuracao(url) {
    return new Promise(function (ok, falha) {
      var v = document.createElement('video');
      v.preload = 'metadata';
      v.onloadedmetadata = function () { ok(v.duration); };
      v.onerror = function () { falha(new Error('Não foi possível ler a duração do vídeo. Envie um MP4 válido.')); };
      v.src = url;
    });
  }

  /** Confere limites, tipo, tamanho, proporção 360 e duração. Lança Error com a mensagem para a tela. */
  async function validarArquivo(file, tipo) {
    var imagem = tipo !== 'VIDEO';
    if (imagem && qtdImagens() >= LIM_IMAGENS) {
      throw new Error('Limite de ' + LIM_IMAGENS + ' imagens atingido (fotos comuns e 360° somadas).');
    }
    if (!imagem && qtdVideos() >= LIM_VIDEOS) { throw new Error('Limite de ' + LIM_VIDEOS + ' vídeos atingido.'); }
    if (imagem) {
      if (['image/jpeg', 'image/png'].indexOf(file.type) < 0) { throw new Error('"' + file.name + '": use imagens JPEG ou PNG.'); }
      if (file.size > MAX_IMG) { throw new Error('"' + file.name + '": a imagem excede 10 MB.'); }
    } else {
      if (['video/mp4', 'video/quicktime'].indexOf(file.type) < 0) { throw new Error('"' + file.name + '": use vídeo em MP4.'); }
      if (file.size > MAX_VID) { throw new Error('"' + file.name + '": o vídeo excede 100 MB.'); }
    }
    var url = URL.createObjectURL(file);
    try {
      if (imagem) {
        var img = await carregarImagem(url);
        if (tipo === 'FOTO_360' && Math.abs(img.naturalWidth / img.naturalHeight - 2) > 0.04) {
          throw new Error('"' + file.name + '": foto 360° deve ter proporção 2:1 (equirretangular). Esta tem ' +
            img.naturalWidth + 'x' + img.naturalHeight + '.');
        }
        return { url: url, largura: img.naturalWidth, altura: img.naturalHeight };
      }
      var duracao = await lerDuracao(url);
      if (duracao > DURACAO_MAX) { throw new Error('"' + file.name + '": o vídeo deve ter no máximo 2 minutos.'); }
      return { url: url, duracaoSegundos: Math.ceil(duracao) };
    } catch (e) {
      URL.revokeObjectURL(url);
      throw e;
    }
  }

  async function adicionarArquivos(lista, tipo) {
    erroMidia('');
    var erros = [];
    for (var i = 0; i < lista.length; i++) {
      var file = lista[i];
      try {
        var info = await validarArquivo(file, tipo);
        var item = { key: 'l' + (++S.seq), id: null, local: true, file: file, tipo: tipo, estado: S.modo === 'rascunho' ? 'NOVA' : 'ATIVA',
          url: info.url, miniaturaUrl: null, capa: false, largura: info.largura || null, altura: info.altura || null,
          duracaoSegundos: info.duracaoSegundos || null, progresso: 0, erro: null };
        S.midias.push(item);
        marcarSujo();
        renderMidias();
        if (S.modo !== 'criar') { await enviarItem(item); renderMidias(); }
      } catch (e) {
        erros.push(e.message);
      }
    }
    if (erros.length) { erroMidia(erros.join(' ')); }
    renderMidias();
  }

  /** Upload com XMLHttpRequest: é o que dá progresso real de envio. */
  function enviarItem(item) {
    return new Promise(function (resolve) {
      item.erro = null;
      item.progresso = 0;
      var xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/gestor/imoveis/' + S.id + '/midias');
      var token = Auth.getToken();
      if (token) { xhr.setRequestHeader('Authorization', 'Bearer ' + token); }
      xhr.upload.onprogress = function (e) {
        if (e.lengthComputable) { item.progresso = Math.round(e.loaded / e.total * 100); atualizarProgresso(item); }
      };
      xhr.onload = function () {
        var corpo = null;
        try { corpo = JSON.parse(xhr.responseText); } catch (e) { /* sem corpo */ }
        if (xhr.status === 401) { Auth.limparSessao(); location.href = Api.urlDeLogin(Auth.rotaAtual()); return resolve(false); }
        if (xhr.status >= 200 && xhr.status < 300 && corpo) {
          item.id = corpo.id; item.key = 's' + corpo.id; item.local = false; item.estado = corpo.estado;
          item.url = corpo.url; item.miniaturaUrl = corpo.miniaturaUrl; item.capa = corpo.capa; item.progresso = 100;
          resolve(true);
        } else {
          item.erro = (corpo && corpo.erro) || ('Falha no envio (HTTP ' + xhr.status + ').');
          resolve(false);
        }
      };
      xhr.onerror = function () { item.erro = 'Falha de conexão durante o envio.'; resolve(false); };
      var fd = new FormData();
      fd.append('tipo', item.tipo);
      fd.append('arquivo', item.file);
      xhr.send(fd);
    });
  }

  function atualizarProgresso(item) {
    var barra = document.querySelector('[data-barra="' + item.key + '"]');
    if (barra) { barra.style.width = item.progresso + '%'; }
  }

  function capaEfetivaKey() {
    var imgs = efetivas().filter(function (m) { return m.tipo !== 'VIDEO'; });
    if (S.modo === 'criar') {
      var escolhida = imgs.filter(function (m) { return m.key === S.capaKey; })[0];
      return (escolhida || imgs[0] || {}).key;
    }
    var daApi = imgs.filter(function (m) { return m.capa; })[0];
    return (daApi || imgs[0] || {}).key;
  }

  function cartaoMidia(m, indice, total, capaKey) {
    var imagem = m.tipo !== 'VIDEO';
    var removida = m.estado === 'REMOVIDA';
    var miniatura = imagem
      ? '<img src="' + escapar(m.miniaturaUrl || m.url) + '" alt="" class="w-full h-full object-cover">'
      : '<div class="w-full h-full flex flex-col items-center justify-center bg-slate-800 text-white text-[10px]"><i data-lucide="play" class="w-6 h-6"></i>' +
        (m.duracaoSegundos ? m.duracaoSegundos + 's' : '') + '</div>';
    var tipoControle = imagem
      ? '<select data-acao="tipo" data-key="' + m.key + '" class="text-[11px] border border-slate-300 rounded-lg px-1.5 py-1 bg-white"' + (removida ? ' disabled' : '') + '>' +
        '<option value="FOTO"' + (m.tipo === 'FOTO' ? ' selected' : '') + '>Foto comum</option>' +
        '<option value="FOTO_360"' + (m.tipo === 'FOTO_360' ? ' selected' : '') + '>Foto 360°</option></select>'
      : '<span class="text-[11px] font-semibold text-slate-600">Vídeo</span>';
    var selo = (m.estado === 'NOVA' ? '<span class="text-[9px] font-bold bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded">NOVA</span>' : '') +
               (removida ? '<span class="text-[9px] font-bold bg-rose-100 text-rose-700 px-1.5 py-0.5 rounded">REMOVIDA (sai ao confirmar)</span>' : '');
    var emEnvio = m.local && S.modo !== 'criar' && !m.erro && m.progresso < 100;
    var barra = (m.local && S.modo !== 'criar') || (m.progresso > 0 && m.progresso < 100)
      ? '<div class="h-1.5 bg-slate-100 rounded-full overflow-hidden mt-1.5"><div data-barra="' + m.key + '" class="h-full bg-blue-600 transition-all" style="width:' + m.progresso + '%"></div></div>'
      : '';
    return '<div class="flex gap-3 p-3 rounded-xl border ' + (removida ? 'border-rose-200 bg-rose-50/40 opacity-70' : 'border-slate-200 bg-slate-50') + '">' +
      '<div class="w-24 h-20 rounded-lg overflow-hidden bg-slate-200 shrink-0">' + miniatura + '</div>' +
      '<div class="flex-1 min-w-0">' +
        '<div class="flex flex-wrap items-center gap-1.5">' + tipoControle + selo + '</div>' +
        (m.erro ? '<p class="text-[11px] font-semibold text-rose-600 mt-1">' + escapar(m.erro) + '</p>' : '') +
        (emEnvio ? '<p class="text-[10px] text-slate-500 mt-1">Enviando...</p>' : '') + barra +
        '<div class="flex flex-wrap items-center gap-1 mt-2">' +
          (imagem && !removida
            ? '<label class="flex items-center gap-1 text-[11px] text-slate-600 cursor-pointer mr-2"><input type="radio" name="capa" data-acao="capa" data-key="' + m.key + '" class="accent-blue-600"' + (capaKey === m.key ? ' checked' : '') + '> Capa</label>'
            : '') +
          (!removida ? '<button type="button" data-acao="subir" data-key="' + m.key + '" class="px-2 py-1 rounded-lg border border-slate-300 text-xs hover:bg-white"' + (indice === 0 ? ' disabled' : '') + ' aria-label="Mover para cima">↑</button>' +
                       '<button type="button" data-acao="descer" data-key="' + m.key + '" class="px-2 py-1 rounded-lg border border-slate-300 text-xs hover:bg-white"' + (indice === total - 1 ? ' disabled' : '') + ' aria-label="Mover para baixo">↓</button>' : '') +
          (m.erro && m.local && S.modo !== 'criar' ? '<button type="button" data-acao="repetir" data-key="' + m.key + '" class="px-2 py-1 rounded-lg border border-blue-300 text-blue-700 text-xs hover:bg-blue-50">Tentar de novo</button>' : '') +
          (!removida ? '<button type="button" data-acao="remover" data-key="' + m.key + '" class="px-2 py-1 rounded-lg border border-rose-300 text-rose-700 text-xs hover:bg-rose-50 ml-auto">Remover</button>' : '') +
        '</div>' +
      '</div></div>';
  }

  function renderMidias() {
    var visiveis = S.midias.filter(function (m) { return m.estado !== 'REMOVIDA'; });
    var removidas = S.midias.filter(function (m) { return m.estado === 'REMOVIDA'; });
    var capaKey = capaEfetivaKey();
    $('listaMidias').innerHTML = visiveis.map(function (m, i) { return cartaoMidia(m, i, visiveis.length, capaKey); }).join('') +
      removidas.map(function (m) { return cartaoMidia(m, 0, 1, capaKey); }).join('');
    $('vazioMidias').classList.toggle('hidden', S.midias.length > 0);
    var ni = qtdImagens(), nv = qtdVideos();
    var ci = $('contadorImagens'), cv = $('contadorVideos');
    ci.innerText = ni + '/' + LIM_IMAGENS + ' imagens';
    cv.innerText = nv + '/' + LIM_VIDEOS + ' vídeos';
    ci.className = 'px-2.5 py-1 rounded-full ' + (ni >= LIM_IMAGENS ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-700');
    cv.className = 'px-2.5 py-1 rounded-full ' + (nv >= LIM_VIDEOS ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-700');
    document.querySelectorAll('.btn-add').forEach(function (b) {
      var vid = b.dataset.add === 'VIDEO';
      b.disabled = vid ? nv >= LIM_VIDEOS : ni >= LIM_IMAGENS;
      b.classList.toggle('opacity-40', b.disabled);
    });
    if (global.lucide) { global.lucide.createIcons(); }
  }

  function porKey(key) { return S.midias.filter(function (m) { return m.key === key; })[0]; }

  async function recarregarDoServidor() {
    S.anuncio = await Api.get('/api/gestor/imoveis/' + S.id);
    var pendentes = S.midias.filter(function (m) { return m.local && m.erro; });
    S.midias = S.anuncio.midias.map(deServidor).concat(pendentes);
  }

  async function enviarOrdem() {
    var ids = efetivas().filter(function (m) { return m.id; }).map(function (m) { return m.id; });
    try { await Api.put('/api/gestor/imoveis/' + S.id + '/midias/ordem', { ids: ids }); }
    catch (e) { UI.toast(e.message, 'erro'); await recarregarDoServidor(); }
  }

  async function aoClicarMidia(e) {
    var alvo = e.target.closest('[data-acao]');
    if (!alvo) { return; }
    var acao = alvo.dataset.acao, m = porKey(alvo.dataset.key);
    if (!m) { return; }
    if (acao === 'tipo') { return; } // tratado no evento change
    if (acao === 'capa') {
      if (S.modo === 'criar') { S.capaKey = m.key; marcarSujo(); }
      else if (m.id) {
        try {
          await Api.put('/api/gestor/imoveis/' + S.id + '/midias/' + m.id + '/capa', {});
          S.midias.forEach(function (x) { x.capa = x.key === m.key; });
        } catch (er) { UI.toast(er.message, 'erro'); }
      }
      return renderMidias();
    }
    if (acao === 'subir' || acao === 'descer') {
      var vis = efetivas();
      var i = vis.indexOf(m), j = acao === 'subir' ? i - 1 : i + 1;
      if (j < 0 || j >= vis.length) { return; }
      var a = S.midias.indexOf(vis[i]), b = S.midias.indexOf(vis[j]);
      var tmp = S.midias[a]; S.midias[a] = S.midias[b]; S.midias[b] = tmp;
      renderMidias();
      marcarSujo();
      if (S.modo !== 'criar') { await enviarOrdem(); }
      return;
    }
    if (acao === 'repetir') { await enviarItem(m); return renderMidias(); }
    if (acao === 'remover') {
      var ok = await UI.confirmar({ titulo: 'Remover mídia?', mensagem: S.modo === 'rascunho' && m.estado === 'ATIVA'
        ? 'A mídia sairá do anúncio quando você confirmar a alteração. Se descartar a edição, ela volta.'
        : 'Esta mídia será removida do anúncio.', confirmarTexto: 'Remover', perigo: true });
      if (!ok) { return; }
      if (m.local && !m.id) {
        URL.revokeObjectURL(m.url);
        S.midias.splice(S.midias.indexOf(m), 1);
      } else {
        try { await Api.del('/api/gestor/imoveis/' + S.id + '/midias/' + m.id); await recarregarDoServidor(); }
        catch (er) { UI.toast(er.message, 'erro'); }
      }
      marcarSujo();
      renderMidias();
    }
  }

  async function aoMudarTipo(e) {
    var sel = e.target.closest('select[data-acao="tipo"]');
    if (!sel) { return; }
    var m = porKey(sel.dataset.key);
    var novo = sel.value;
    if (!m || m.tipo === novo) { return; }
    erroMidia('');
    if (novo === 'FOTO_360' && m.largura && m.altura && Math.abs(m.largura / m.altura - 2) > 0.04) {
      erroMidia('Esta imagem tem ' + m.largura + 'x' + m.altura + ' e não está na proporção 2:1 exigida para foto 360°.');
      sel.value = m.tipo;
      return;
    }
    if (m.id) {
      try {
        var r = await Api.put('/api/gestor/imoveis/' + S.id + '/midias/' + m.id + '/tipo', { tipo: novo });
        m.tipo = r.tipo;
      } catch (er) { UI.toast(er.message, 'erro'); sel.value = m.tipo; }
    } else {
      m.tipo = novo;
    }
    marcarSujo();
    renderMidias();
  }

  // ------------------------------------------------------------ preview

  /** O servidor remove tags HTML do texto; o preview faz o mesmo para mostrar o que o catálogo vai mostrar. */
  function semTags(t) { return (t || '').replace(/<[^>]*>?/g, '').replace(/[ 	]+/g, ' ').trim(); }

  function imovelDoPreview() {
    var d = lerForm();
    ['titulo', 'descricao', 'logradouro', 'bairro', 'cidade'].forEach(function (c) { d[c] = semTags(d[c]); });
    d.comodidades = d.comodidades.map(semTags);
    var capaKey = capaEfetivaKey();
    var precoPreview = S.modo === 'rascunho' ? d.valorDiaria
      : (S.anuncio && S.anuncio.dados ? S.anuncio.dados.valorDiaria : null);
    var midias = efetivas().map(function (m, i) {
      return { tipo: m.tipo, url: m.url, miniaturaUrl: m.miniaturaUrl, capa: m.key === capaKey, ordem: i,
        duracaoSegundos: m.duracaoSegundos };
    });
    var capa = midias.filter(function (m) { return m.capa; })[0];
    return {
      id: S.id || 0, titulo: d.titulo || 'Título do anúncio', descricao: d.descricao,
      tipoImovel: d.tipoImovel, tipoImovelRotulo: ROTULOS_TIPO[d.tipoImovel] || null,
      logradouro: d.logradouro, bairro: d.bairro, cidade: d.cidade,
      metragemQuadrada: d.metragemQuadrada, numeroQuartos: d.numeroQuartos, numeroBanheiros: d.numeroBanheiros,
      vagasGaragem: d.vagasGaragem, capacidadeHospedes: d.capacidadeHospedes,
      valorDiariaBase: precoPreview, comodidades: d.comodidades, ativo: true,
      capaUrl: capa ? (capa.miniaturaUrl || capa.url) : null, midias: midias, whatsappLink: d.whatsappLink
    };
  }

  function renderPreview() {
    var imovel = imovelDoPreview();
    if (S.viewer) { S.viewer.destruir(); S.viewer = null; }
    // O cartão do preview não leva a lugar nenhum: o anúncio ainda não existe no catálogo.
    $('previewCard').innerHTML = '<div class="pointer-events-none">' + ImovelCard.card(imovel) + '</div>';
    var raiz = $('previewDetalhe');
    raiz.innerHTML = ImovelDetalhe.montar(imovel, { preview: true, logado: true });
    S.viewer = ImovelDetalhe.ativar(raiz, imovel, {});
    if (global.lucide) { global.lucide.createIcons(); }
  }

  function mostrarAba(preview) {
    $('painelCadastro').classList.toggle('hidden', preview);
    $('painelPreview').classList.toggle('hidden', !preview);
    $('abaCadastro').setAttribute('aria-selected', String(!preview));
    $('abaPreview').setAttribute('aria-selected', String(preview));
    $('abaCadastro').className = 'px-4 py-2.5 text-sm -mb-px border-b-2 ' + (!preview ? 'font-bold border-blue-600 text-blue-700' : 'font-semibold border-transparent text-slate-500 hover:text-slate-800');
    $('abaPreview').className = 'px-4 py-2.5 text-sm -mb-px border-b-2 ' + (preview ? 'font-bold border-blue-600 text-blue-700' : 'font-semibold border-transparent text-slate-500 hover:text-slate-800');
    if (preview) { renderPreview(); }
  }

  // ------------------------------------------------------------- salvar

  function marcarSujo() {
    S.sujo = true;
    if (S.modo === 'rascunho') { agendarAutosave(); }
  }

  function atualizarBotaoFinalizar() {
    var btn = $('btnFinalizar');
    btn.disabled = S.ocupado || (S.modo === 'criar' && !$('f-aceite').checked);
  }

  function agendarAutosave() {
    clearTimeout(S.timerAutosave);
    $('statusRascunho').innerText = 'Alterações pendentes...';
    S.timerAutosave = setTimeout(function () { salvarRascunho(true); }, 2000);
  }

  async function salvarRascunho(silencioso) {
    clearTimeout(S.timerAutosave);
    try {
      var r = await Api.put('/api/gestor/imoveis/' + S.id + '/edicao/rascunho', lerForm());
      S.anuncio = r;
      S.sujo = false;
      $('statusRascunho').innerText = 'Rascunho salvo às ' + UI.horaMinuto() + ' (horário de Brasília).';
      if (!silencioso) { UI.toast('Rascunho salvo. O anúncio no ar não foi alterado.'); }
      return true;
    } catch (e) {
      $('statusRascunho').innerText = 'Não foi possível salvar o rascunho.';
      UI.toast(e.message, 'erro');
      return false;
    }
  }

  function mostrarErrosDoServidor(e) {
    if (e && e.body && e.body.campos && Object.keys(e.body.campos).length) { mostrarErros(e.body.campos); }
    erroGeral(e.message || 'Não foi possível concluir a operação.');
  }

  function progressoGeral(visivel, pct, texto) {
    $('progressoGeral').classList.toggle('hidden', !visivel);
    $('barraGeral').style.width = (pct || 0) + '%';
    $('textoProgressoGeral').innerText = texto || '';
  }

  async function finalizar(evento) {
    evento.preventDefault();
    erroGeral('');
    var d = lerForm();
    var erros = validar(d);
    mostrarErros(erros);
    if (Object.keys(erros).length) { erroGeral('Corrija os campos destacados para continuar.'); return; }

    if (S.modo === 'direto') { return salvarDireto(d); }

    if (!$('f-aceite').checked) { erroGeral('É necessário aceitar o termo de uso para finalizar.'); return; }
    S.ocupado = true;
    atualizarBotaoFinalizar();
    try {
      var criado = await Api.post('/api/gestor/imoveis', { dados: d, aceiteTermo: true });
      S.id = criado.id;
      S.anuncio = criado;
      S.modo = 'direto'; // se algum upload falhar, novas tentativas não criam um segundo anúncio
      history.replaceState(null, '', '/anuncio-form.html?id=' + S.id);
      var locais = S.midias.filter(function (m) { return m.local; });
      var falhas = 0;
      for (var i = 0; i < locais.length; i++) {
        progressoGeral(true, Math.round(i / locais.length * 100), 'Enviando mídia ' + (i + 1) + ' de ' + locais.length + '...');
        renderMidias();
        if (!(await enviarItem(locais[i]))) { falhas++; }
      }
      // Capa escolhida: a primeira imagem enviada já nasce capa; só troca se a pessoa escolheu outra.
      var escolhida = S.capaKey ? porKey(S.capaKey) : null;
      if (escolhida && escolhida.id && !escolhida.capa) {
        try { await Api.put('/api/gestor/imoveis/' + S.id + '/midias/' + escolhida.id + '/capa', {}); } catch (e) { /* segue */ }
      }
      progressoGeral(false);
      S.sujo = false;
      if (falhas) {
        configurarModo();
        renderMidias();
        erroGeral('O anúncio foi criado, mas ' + falhas + ' arquivo(s) não foram enviados. Corrija e use "Tentar de novo" ' +
                  'nos itens com erro; depois volte ao painel.');
      } else {
        location.href = '/dashboard.html?aba=pre&novo=' + S.id;
      }
    } catch (e) {
      mostrarErrosDoServidor(e);
      progressoGeral(false);
    } finally {
      S.ocupado = false;
      atualizarBotaoFinalizar();
    }
  }

  async function salvarDireto(d) {
    S.ocupado = true;
    atualizarBotaoFinalizar();
    try {
      S.anuncio = await Api.put('/api/gestor/imoveis/' + S.id, d);
      S.sujo = false;
      UI.toast('Alterações salvas.');
      location.href = '/dashboard.html?aba=pre';
    } catch (e) {
      mostrarErrosDoServidor(e);
    } finally {
      S.ocupado = false;
      atualizarBotaoFinalizar();
    }
  }

  async function confirmarAlteracao() {
    erroGeral('');
    var d = lerForm();
    var erros = validar(d);
    mostrarErros(erros);
    if (Object.keys(erros).length) { erroGeral('Corrija os campos destacados antes de confirmar a alteração.'); return; }
    if (!(await salvarRascunho(true))) { return; }
    var r = await AnuncioAcoes.confirmarAlteracao(S.anuncio);
    if (r) { S.sujo = false; location.href = '/dashboard.html?aba=anuncios&imovel=' + S.id; }
  }

  async function descartarEdicao() {
    var r = await AnuncioAcoes.descartarEdicao(S.anuncio);
    if (r) { S.sujo = false; location.href = '/dashboard.html?aba=anuncios&imovel=' + S.id; }
  }

  // ----------------------------------------------------------- inicialização

  function configurarModo() {
    var criar = S.modo === 'criar', direto = S.modo === 'direto', rascunho = S.modo === 'rascunho';
    $('tituloPagina').innerText = criar ? 'Criar anúncio' : (rascunho ? 'Editar anúncio' : 'Editar cadastro do imóvel');
    $('blocoAceite').classList.toggle('hidden', !criar);
    $('secaoPreco').classList.toggle('hidden', !rascunho);
    $('btnFinalizar').classList.toggle('hidden', rascunho);
    $('btnFinalizar').innerText = criar ? 'Finalizar cadastro' : 'Salvar alterações';
    $('btnSalvarRascunho').classList.toggle('hidden', !rascunho);
    $('faixaEdicao').classList.toggle('hidden', !rascunho);
    if (rascunho && S.anuncio) {
      $('faixaTitulo').innerText = 'Em edição desde ' + UI.dataHora(S.anuncio.edicaoIniciadaEm);
      $('faixaTexto').innerText = 'Este anúncio está fora do catálogo e não recebe novas reservas. Suas alterações ficam em rascunho ' +
        'e só valem quando você clicar em "Confirmar alteração"; depois disso ele volta sozinho ao catálogo em ' +
        S.anuncio.republicacaoHoras + 'h. Você pode descartar a edição a qualquer momento.';
    }
    var selos = $('selosEstado');
    selos.innerText = direto && S.anuncio ? 'Pré-publicação' : '';
    atualizarBotaoFinalizar();
  }

  function mostrarErroDePagina(mensagem) {
    $('carregando').classList.add('hidden');
    var e = $('erroPagina');
    e.innerHTML = mensagem;
    e.classList.remove('hidden');
  }

  async function iniciar() {
    if (!Auth.isAuthenticated()) { location.href = Api.urlDeLogin(Auth.rotaAtual()); return; }
    if (!Auth.isGestor()) {
      mostrarErroDePagina('Esta área é exclusiva para gestores de imóveis. <a class="underline" href="/imoveis.html">Voltar ao catálogo</a>.');
      return;
    }

    var id = new URLSearchParams(location.search).get('id');
    var dados = {};
    if (id) {
      try {
        S.anuncio = await Api.get('/api/gestor/imoveis/' + encodeURIComponent(id));
      } catch (e) {
        mostrarErroDePagina(escapar(e.status === 403 ? 'Você não tem permissão sobre este imóvel.' : (e.message || 'Anúncio não encontrado.')) +
          ' <a class="underline" href="/dashboard.html">Voltar ao painel</a>.');
        return;
      }
      S.id = S.anuncio.id;
      if (S.anuncio.status === 'EM_EDICAO') {
        S.modo = 'rascunho';
        dados = S.anuncio.rascunho || S.anuncio.dados;
      } else if (['PRE_PUBLICACAO_SEM_PRECO', 'PRE_PUBLICACAO_AGUARDANDO', 'PRONTO_PARA_PUBLICAR'].indexOf(S.anuncio.status) >= 0) {
        S.modo = 'direto';
        dados = S.anuncio.dados;
      } else {
        mostrarErroDePagina('Este anúncio já está publicado (ou com republicação agendada). Para alterá-lo, use <b>Editar anúncio</b> ' +
          'no painel: ele sairá do ar durante a edição. <a class="underline" href="/dashboard.html?aba=anuncios&imovel=' + S.id + '">Ir ao painel</a>.');
        return;
      }
      S.midias = S.anuncio.midias.map(deServidor);
    }

    preencherForm(dados);
    configurarModo();
    renderMidias();
    $('carregando').classList.add('hidden');
    $('conteudo').classList.remove('hidden');
    if (global.lucide) { global.lucide.createIcons(); }

    // Eventos
    $('abaCadastro').addEventListener('click', function () { mostrarAba(false); });
    $('abaPreview').addEventListener('click', function () { mostrarAba(true); });
    $('painelCadastro').addEventListener('submit', finalizar);
    $('f-aceite').addEventListener('change', atualizarBotaoFinalizar);
    $('btnLerTermo').addEventListener('click', UI.lerTermo);
    $('painelCadastro').addEventListener('input', function (e) {
      if (e.target.id === 'f-cep') { e.target.value = formatarCep(e.target.value); if (e.target.value.length === 9) { buscarCep(); } }
      if (e.target.id === 'f-uf') { e.target.value = e.target.value.toUpperCase().replace(/[^A-Z]/g, ''); }
      if (e.target.classList.contains('campo')) { e.target.classList.remove('invalido'); marcarSujo(); }
    });
    $('sugestoesComodidades').addEventListener('click', function (e) {
      var b = e.target.closest('[data-comodidade]');
      if (b) { return alternarComodidade(b.dataset.comodidade); }
      var r = e.target.closest('[data-remover-comodidade]');
      if (r) { alternarComodidade(r.dataset.removerComodidade); }
    });
    function addComodidade() {
      var v = $('f-comodidade-nova').value.trim();
      if (v && S.comodidades.indexOf(v) < 0) { S.comodidades.push(v); renderComodidades(); marcarSujo(); }
      $('f-comodidade-nova').value = '';
    }
    $('btnAddComodidade').addEventListener('click', addComodidade);
    $('f-comodidade-nova').addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); addComodidade(); } });

    var entradas = { FOTO: $('arquivoFoto'), FOTO_360: $('arquivoFoto360'), VIDEO: $('arquivoVideo') };
    document.querySelectorAll('.btn-add').forEach(function (b) {
      b.addEventListener('click', function () { entradas[b.dataset.add].click(); });
    });
    Object.keys(entradas).forEach(function (tipo) {
      entradas[tipo].addEventListener('change', function () {
        var arquivos = Array.prototype.slice.call(entradas[tipo].files);
        entradas[tipo].value = '';
        adicionarArquivos(arquivos, tipo);
      });
    });
    $('listaMidias').addEventListener('click', aoClicarMidia);
    $('listaMidias').addEventListener('change', aoMudarTipo);

    $('btnSalvarRascunho').addEventListener('click', function () { salvarRascunho(false); });
    $('btnConfirmarAlteracao').addEventListener('click', confirmarAlteracao);
    $('btnDescartarEdicao').addEventListener('click', descartarEdicao);

    global.addEventListener('beforeunload', function (e) {
      if (S.sujo && S.modo !== 'rascunho') { e.preventDefault(); e.returnValue = ''; }
    });
  }

  document.addEventListener('DOMContentLoaded', iniciar);
})(window);
