# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

styles = get_styles()
reset_counters()
story = []

story += capa(styles, "SmartRent B2B — Matriz de Riscos",
              "Identificação, probabilidade, impacto e mitigação dos principais riscos do MVP")

story.append(Paragraph("1 METODOLOGIA", styles["H1"]))
story.append(Paragraph(
    "Cada risco é classificado por probabilidade (baixa, média ou alta) e por impacto (baixo, "
    "médio ou alto) sobre o prazo, a qualidade ou o escopo do MVP de 30 dias. A severidade "
    "combina as duas dimensões e ordena a atenção da equipe: quanto maior a ênfase tipográfica "
    "no quadro a seguir, maior a severidade.", styles["Body"]))

riscos = [
    ("R01", "Indisponibilidade ou instabilidade da Groq API durante o desenvolvimento ou em "
            "produção.", "Média", "Médio", "Média",
     "O cálculo de contingência já é requisito obrigatório (RF09), com a origem de cada "
     "sugestão registrada; o sistema nunca fica sem um valor sugerido."),
    ("R02", "Falha na verificação de conflito de datas permitir reservas sobrepostas, o risco "
            "mais crítico do domínio de negócio.", "Baixa", "Alto", "Média",
     "Verificação na camada de aplicação, reforçada por controle de concorrência otimista; "
     "cobertura de testes dedicada a todos os casos de sobreposição."),
    ("R03", "Instabilidade ou limite do plano gratuito do Supabase afetar a disponibilidade "
            "durante a apresentação à banca.", "Média", "Médio", "Média",
     "Ambiente validado com antecedência; manutenção de uma cópia local do esquema e dos "
     "dados de demonstração."),
    ("R04", "Aumento do escopo pressionar o cronograma de 30 dias.", "Alta", "Médio", "Alta",
     "O protocolo do projeto já classifica qualquer pedido fora da matriz MoSCoW acordada "
     "como Visão Futura, preservando o cronograma."),
    ("R05", "Bloqueios inesperados no ambiente de desenvolvimento local, como já ocorreu com a "
            "ausência do Maven e do wrapper, e com a reescrita indevida de pacotes por uma "
            "extensão de IDE.", "Alta", "Baixo", "Média",
     "Uso do Maven Wrapper versionado no repositório; documentação viva (STATUS_PROJETO.md e "
     "ADRs) registra cada incidente e sua resolução."),
    ("R06", "Falta de rastreabilidade entre requisitos, código e testes dificultar a arguição "
            "da banca.", "Média", "Médio", "Média",
     "Matriz de rastreabilidade de requisitos mantida atualizada a cada entrega."),
    ("R07", "Cobertura insuficiente de testes automatizados na reta final do prazo.", "Média",
     "Alto", "Alta", "O plano de testes prioriza desde já os casos críticos, conflito de "
     "datas e contingência de IA, antes dos casos de borda secundários."),
    ("R08", "Falhas expostas durante a demonstração ao vivo para a banca.", "Média", "Alto",
     "Alta", "Ensaio da apresentação com o ambiente já implantado, com antecedência mínima de "
     "dois dias, e gravação prévia de um vídeo de segurança da demonstração."),
    ("R09", "Mídias dos anúncios guardadas em disco local não sobreviverem a uma troca de "
            "instância nem escalarem para mais de um servidor.", "Média", "Médio", "Média",
     "O armazenamento está atrás da interface MidiaStorage; migrar para armazenamento de "
     "objetos (S3) exige apenas uma nova implementação."),
    ("R10", "Lembretes de edição esquecida não chegarem por e-mail, hoje apenas registrados "
            "em log, e o gestor deixar anúncios fora do ar sem perceber.", "Média", "Médio",
     "Média", "O aviso in-app, o selo de destaque no painel e a contagem de tempo em edição já "
     "funcionam; o canal de e-mail é um ponto de extensão (NotificadorEmail) a ser ligado a um SMTP."),
    ("R11", "O texto do termo de uso, hoje um modelo de trabalho, não ter validade jurídica "
            "quando o sistema for usado de verdade.", "Média", "Alto", "Alta",
     "O aceite é versionado e auditado (usuário, imóvel, versão, data, hora e IP); o texto "
     "deve ser revisado por assessoria jurídica antes da produção."),
    ("R12", "Ir à produção com o gateway de pagamento ainda simulado e cobrar ou estornar de "
            "forma incorreta.", "Média", "Alto", "Alta",
     "O gateway está atrás da interface GatewayPagamento, com chave de idempotência única no banco "
     "para cobrança e estorno; o provedor real é uma nova implementação da interface."),
    ("R13", "Textos e regras de cancelamento e reembolso entrarem em produção sem a revisão do "
            "setor jurídico (o direito de arrependimento está em espera).", "Média", "Alto", "Alta",
     "Textos provisórios centralizados em um único arquivo marcado como pendente; parâmetro "
     "CANCEL_REGRET_DAYS desligado e sem efeito; parâmetros da política gravados no snapshot da reserva."),
    ("R14", "O filtro de conteúdo do SmartChat ocultar texto legítimo ou deixar passar contatos "
            "disfarçados.", "Média", "Médio", "Média",
     "Regras e listas configuráveis, dados legítimos da reserva protegidos, mais de cem casos de "
     "teste e texto original guardado para revisão futura."),
    ("R15", "O processamento de vídeo depender do FFmpeg, ausente em ambientes de desenvolvimento, "
            "e consumir CPU do servidor.", "Média", "Médio", "Média",
     "Interface VideoProcessingService com fallback básico, tempo máximo e poucas threads por "
     "execução, fila com retry; em produção, executar o worker em contêiner com limites."),
    ("R16", "Limite de envios e agrupamento de notificações do chat em memória não valerem com "
            "mais de uma instância do servidor.", "Média", "Baixo", "Baixa",
     "Documentado; trocar por limitador distribuído mantendo a mesma interface."),
    ("R17", "A remoção das colunas de WhatsApp apagar dados que alguém ainda precise.", "Baixa", "Médio", "Baixa",
     "Os valores são arquivados em tabelas próprias antes do descarte, em migration separada."),
    ("R18", "O pedido de exclusão de dados ser usado como rota de fuga em golpe, ou feito por quem "
            "invadiu a conta.", "Média", "Alto", "Alta",
     "Nada é excluído automaticamente; análise da equipe com período mínimo, e-mail ao titular com "
     "link para cancelar, sinais de risco, restrições temporárias e trava de aprovação por reservas, "
     "reembolsos e denúncias."),
    ("R19", "A execução da exclusão acontecer sem definição do setor jurídico (o que apagar, o que reter).",
     "Média", "Alto", "Alta",
     "A execução é uma interface sem implementação: concluir recusa e nada é tocado; textos e prazos "
     "provisórios em arquivo único, listados como pendentes de revisão."),
    ("R20", "Avisos de segurança e links de confirmação não chegarem ao usuário em produção "
            "(e-mail ainda só em log).", "Alta", "Médio", "Alta",
     "Interface EmailSender pronta para SMTP; o corpo (com links) só vai ao log em desenvolvimento "
     "(EMAIL_LOG_CORPO); SMTP real registrado como pendência."),
    ("R21", "Imagem maliciosa ou gigante enviada como foto de perfil (decompression bomb, SVG com script, "
            "metadados com localização).", "Média", "Médio", "Média",
     "Tipo real pelos bytes (só PNG e JPEG), limites lidos do cabeçalho antes de decodificar, "
     "recodificação em JPEG sem metadados e nome aleatório; original nunca guardado."),
    ("R22", "Sessões antigas continuarem válidas depois da troca de senha ou de e-mail.", "Média", "Alto", "Alta",
     "Versão de sessão no token (sv): trocar senha ou e-mail invalida os demais tokens."),
    ("R23", "As restrições temporárias trancarem o usuário legítimo para fora da própria conta.", "Baixa", "Médio", "Baixa",
     "Restrições parciais (login, senha, chat, reembolsos e bloqueio de datas nunca são restritos), "
     "cancelamento a qualquer momento, link “não fui eu”, aviso fixo e chave para desligar."),
    ("R24", "Perda ou vazamento da chave de cifra do SmartChat (SMARTCHAT_CRYPTO_KEY).", "Baixa", "Alto", "Média",
     "Perder a chave torna as mensagens irrecuperáveis: guardá-la em cofre, com backup separado do banco. "
     "Vazar a chave junto com o banco anula a proteção. Formato versionado (v1) prepara a rotação, ainda sem rotina."),
]
rdata = [[Paragraph("ID", styles["CellHeader"]), Paragraph("Risco", styles["CellHeader"]),
          Paragraph("Prob.", styles["CellHeaderCenter"]), Paragraph("Impacto", styles["CellHeaderCenter"]),
          Paragraph("Sever.", styles["CellHeaderCenter"]), Paragraph("Mitigação", styles["CellHeader"])]]
peso = {"Baixa": 0, "Baixo": 0, "Média": 1, "Médio": 1, "Alta": 2, "Alto": 2}
for rid, desc, prob, imp, sev, mit in riscos:
    sev_style = styles["CellCenter"] if peso.get(sev, 0) < 2 else styles["CellBold"]
    sev_style = ParagraphStyle("sevc", parent=sev_style, alignment=TA_CENTER)
    rdata.append([Paragraph(rid, styles["CellBold"]), Paragraph(desc, styles["Cell"]),
                  Paragraph(prob, styles["CellCenter"]), Paragraph(imp, styles["CellCenter"]),
                  Paragraph(sev, sev_style), Paragraph(mit, styles["Cell"])])

story.append(Paragraph("2 REGISTRO DE RISCOS", styles["H1"]))
story.extend(quadro(styles, "Registro de riscos do projeto", quadro_table(
    rdata, [10 * mm, 42 * mm, 13 * mm, 18 * mm, 13 * mm, 50 * mm], font_size=8.4,
    header_align_center=[2, 3, 4])))

story.append(Paragraph(
    "Os riscos R04, R07, R08 e R11 concentram a maior severidade combinada. Recomenda-se revisão "
    "semanal deste documento pela equipe, com o registro explícito de qualquer risco novo "
    "identificado ao longo do desenvolvimento.", styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "03_MATRIZ_DE_RISCOS.pdf"), "Matriz de Riscos")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 03_MATRIZ_DE_RISCOS.pdf")
