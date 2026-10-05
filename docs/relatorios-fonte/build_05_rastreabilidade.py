# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

styles = get_styles()
reset_counters()
story = []

story += capa(styles, "SmartRent B2B — Matriz de Rastreabilidade",
              "Requisito funcional, caso de uso, componente de código e caso de teste")

story.append(Paragraph("1 OBJETIVO", styles["H1"]))
story.append(Paragraph(
    "Esta matriz relaciona cada requisito funcional ao caso de uso, ao componente de código e "
    "ao caso de teste correspondentes, evidenciando a cobertura da implementação e servindo de "
    "roteiro de consulta durante a arguição da banca. Requisitos classificados como Visão "
    "Futura não possuem componente nem caso de teste, por estarem fora do escopo atual.",
    styles["Body"]))

rows = [
    ("RF01", "Autenticar-se", "4.3", "Usuario, AuthService, JwtService, SecurityConfig", "CT13–CT31"),
    ("RF02", "Cadastrar imóvel", "4.3", "Imovel, Endereco, ImovelRepository", "CT10"),
    # RF04 passa a ter endpoint proprio; antes so havia o repositorio.
    ("RF03", "Editar ou inativar imóvel", "4.3", "Imovel, ImovelRepository", "—"),
    ("RF04", "Listar imóveis por situação", "4.3", "ImovelController, ImovelRepository", "—"),
    ("RF05", "Cadastrar reserva", "4.1", "Reserva, ReservaRepository", "CT11"),
    ("RF06", "Verificar conflito de datas", "4.1", "ReservaRepository", "CT01–CT06"),
    ("RF07", "Cancelar ou concluir reserva", "4.3", "Reserva, ReservaRepository", "—"),
    ("RF08", "Gerar sugestão de preço via IA", "4.2", "SugestaoPreco, SugestaoPrecoService", "CT07"),
    ("RF09", "Aplicar cálculo de contingência", "4.2", "SugestaoPreco, SugestaoPrecoService", "CT08, CT09"),
    # RF06 permanece com CT01-CT06: apenas CT02 esta implementado ate aqui.
    ("RF10", "Exibir painel executivo", "4.3", "SugestaoPrecoRepository", "—"),
    ("RF11", "Consultar histórico de sugestões", "4.3", "SugestaoPrecoRepository", "—"),
    ("RF12", "Exibir reservas conflitantes no erro", "4.1", "ReservaRepository", "CT12"),
    ("RF13", "Sincronização iCal (Visão Futura)", "—", "—", "—"),
    ("RF14", "Gateway de pagamento real (Visão Futura)", "—", "—", "—"),
    ("RF15", "Cadastrar anúncio com mídias e termo", "4.3", "AnuncioService, MidiaService, MidiaProcessador, AnuncioDados", "CT50–CT53, CT70–CT98"),
    ("RF16", "Ciclo de vida e janela de 24h", "4.3", "StatusAnuncio, MaquinaDeEstados, AnuncioService, PromocaoAnuncioJob", "CT54–CT66, CT160–CT165"),
    ("RF17", "Autorização por papel e propriedade; sem WhatsApp", "4.3", "SecurityConfig, ImovelAcesso, ImovelController", "CT100–CT107, CT800–CT802"),
    ("RF18", "Editar anúncio publicado", "4.3", "AnuncioEdicaoService, AnuncioRascunho, MidiaService", "CT110–CT132"),
    ("RF19", "Lembrete de edição esquecida", "4.3", "LembreteEdicaoJob, LembreteEdicaoProcessador, NotificacaoService", "CT140–CT150, CT167"),
    ("RF20", "Snapshot imutável da reserva", "4.1", "Reserva, ReservaService", "CT170–CT184"),
    ("RF21", "Sugestão de preço por IA em lote", "4.3", "PricingSuggestionService, GroqPricingSuggestionProvider, PrecificacaoLoteService", "CT190–CT199"),
    ("RF22", "Auditoria de aceites, preços e ações", "4.3", "AuditoriaService, AceiteTermo, HistoricoPreco, AuditoriaAnuncio", "CT55–CT62, CT127"),
    ("RF23", "Dashboard de estatísticas do gestor", "4.3", "EstatisticasService, EstatisticasController", "CT210–CT214"),
    ("RF24", "Calendário de reservas por imóvel", "4.3", "CalendarioService, CalendarioController, calendario.js", "CT335, CT360–CT361"),
    ("RF25", "Bloqueio manual de datas", "4.3", "BloqueioService, BloqueioData, ReservaService", "CT330–CT337, CT350"),
    ("RF26", "Campos comerciais e snapshot dos termos", "4.1", "Imovel, Reserva, ReservaService, AnuncioDados", "CT200–CT205"),
    ("RF27", "Reserva do cliente e pagamento", "4.1", "ReservaClienteService, GatewayPagamento, Pagamento", "CT310–CT315"),
    ("RF28", "Cancelamento e reembolso", "4.1", "CancelamentoService, RefundPolicyService, ReembolsoService", "CT300–CT308, CT320–CT324"),
    ("RF29", "SmartChat: canal único e criação na confirmação", "4.3", "SmartChatService, ChatEventos, ChatReservaListener, ChatReconciliador", "CT500–CT515, CT525–CT529"),
    ("RF30", "Filtro de conteúdo, denúncia e bloqueio (protótipo)", "4.3", "MessageFilterService, ContentModerationService, SmartChatService", "CT400–CT436, CT520–CT524, CT530–CT533"),
    ("RF31", "Vídeos: 1:30, envio retomável e processamento", "4.3", "VideoUploadService, VideoWorker, VideoProcessingService", "CT600–CT650, CT700–CT707"),
    ("RF32", "Fuso horário de Brasília", "4.3", "ClockConfig, PlataformaTempo, Agora, ConfigController", "CT820–CT823"),
    ("RF33", "Perfil pelo cabeçalho; só o próprio usuário", "4.3", "header-auth.js, perfil.html, PerfilController, JwtAuthenticationFilter", "CT437–CT440, CT902–CT906"),
    ("RF34", "Nome de exibição", "4.3", "NomeExibicaoValidador, PerfilService, MessageFilterService", "CT441–CT446"),
    ("RF35", "Foto de perfil", "4.3", "FotoPerfilProcessador, FotoPerfilService, MidiaStorage", "CT447–CT456"),
    ("RF36", "Alteração de senha", "4.3", "SenhaService, PoliticaDeSenha, ReautenticacaoService", "CT457–CT460, CT466–CT470"),
    ("RF37", "Alteração de e-mail com verificação", "4.3", "TrocaEmailService, TokenSeguro, EmailSender", "CT471–CT479"),
    ("RF38", "Solicitação de exclusão de dados", "4.3", "ExclusaoDadosService, SinaisDeRisco, DataDeletionReviewService", "CT480–CT488"),
    ("RF39", "Restrições temporárias e trava de aprovação", "4.3", "AccountRestrictionService", "CT489–CT499, CT900–CT901"),
    ("RNF12", "Cifra em repouso do SmartChat (AES-256-GCM)", "4.3", "CifraCampo, TextoCifradoConverter, CifraCampoConfig, MigracaoCifraChat", "CT540–CT556"),
]
tdata = [[Paragraph("RF", styles["CellHeader"]), Paragraph("Descrição", styles["CellHeader"]),
          Paragraph("Caso de uso", styles["CellHeaderCenter"]), Paragraph("Componente", styles["CellHeader"]),
          Paragraph("Caso(s) de teste", styles["CellHeaderCenter"])]]
for rf, desc, uc, comp, ct in rows:
    tdata.append([Paragraph(rf, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                  Paragraph(uc, styles["CellCenter"]), Paragraph(comp, styles["Cell"]),
                  Paragraph(ct, styles["CellCenter"])])

story.append(Paragraph("2 MATRIZ DE RASTREABILIDADE", styles["H1"]))
story.extend(quadro(styles, "Requisito funcional, caso de uso, componente e caso de teste", quadro_table(
    tdata, [12 * mm, 46 * mm, 18 * mm, 56 * mm, 22 * mm], font_size=8.4,
    header_align_center=[2, 4])))

story.append(Paragraph(
    "A coluna de caso de uso remete às seções do Relatório Técnico (Documento 1) nas quais "
    "cada caso é descrito; a coluna de casos de teste remete aos identificadores do Plano de "
    "Testes (Documento 4). Requisitos sem teste associado ainda não possuem caso definido e "
    "devem ser complementados à medida que os componentes correspondentes forem "
    "implementados.", styles["Body"]))

story.append(Paragraph(
    "A referência de um requisito a um intervalo de casos não implica que todos estejam "
    "executados: o Plano de Testes indica, caso a caso, quais já possuem implementação. O "
    "RF01 é hoje o requisito de maior cobertura, com dezenove casos automatizados; o RF06 "
    "referencia seis casos, dos quais apenas o CT02 está implementado.", styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "05_MATRIZ_DE_RASTREABILIDADE.pdf"), "Matriz de Rastreabilidade")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 05_MATRIZ_DE_RASTREABILIDADE.pdf")
