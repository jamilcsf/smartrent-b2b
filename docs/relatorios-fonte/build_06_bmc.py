# -*- coding: utf-8 -*-
import os
BASE = os.path.dirname(os.path.abspath(__file__))
import sys
sys.path.insert(0, BASE)
from pdf_common import *

A = os.path.join(BASE, "assets")
styles = get_styles()
reset_counters()
story = []

story += capa(styles, "SmartRent B2B — Business Model Canvas",
              "Modelo de negócio da plataforma, segundo o framework de Osterwalder e Pigneur")

story.append(Paragraph("1 VISÃO GERAL DO MODELO DE NEGÓCIO", styles["H1"]))
story.append(Paragraph(
    "O Business Model Canvas organiza, em nove blocos, como o SmartRent B2B cria, entrega e "
    "captura valor. O modelo aqui descrito reflete o escopo do MVP de 30 dias: os itens "
    "marcados como Visão Futura (V2) representam frentes de receita ou de parceria já "
    "identificadas, mas ainda fora do que será construído no ciclo atual.", styles["Body"]))
story.append(figura(styles, f"{A}/business_model_canvas.png",
                     "Business Model Canvas do SmartRent B2B", 172, 1632 / 2635))

story.append(Paragraph("2 DETALHAMENTO DOS NOVE BLOCOS", styles["H1"]))

story.append(Paragraph("2.1 Segmentos de clientes", styles["H2"]))
story.append(Paragraph(
    "O modelo atende a dois perfis de cliente, correspondentes às personas descritas no "
    "Relatório Técnico (Documento 1). O primeiro é o pequeno anfitrião, proprietário de um a "
    "cinco imóveis de temporada, que hoje opera por planilha e aplicativo de mensagens. O "
    "segundo é o administrador de carteira, que gerencia imóveis de terceiros e presta contas "
    "de ocupação e receita a diversos proprietários. Os dois segmentos compartilham a mesma "
    "dor central, o risco de sobreposição de reservas e a dificuldade de precificar a diária "
    "de forma consistente, mas o segundo tem uma necessidade adicional de visão consolidada "
    "entre imóveis. O hóspede final é um beneficiário indireto do modelo, mas não é cliente "
    "pagante no escopo atual.", styles["Body"]))

story.append(Paragraph("2.2 Proposta de valor", styles["H2"]))
story.append(Paragraph(
    "A proposta de valor central é eliminar o risco de overbooking por meio de uma "
    "verificação automática de conflito de datas, combinada a uma sugestão de preço que leva "
    "em conta sazonalidade e microgeografia, calculada por IA generativa e nunca indisponível "
    "graças ao cálculo de contingência determinístico. Para o administrador de carteira, soma-se "
    "a isso um painel executivo que substitui as planilhas paralelas hoje usadas para "
    "acompanhar ocupação e repasse. O diferencial competitivo em relação a ferramentas "
    "corporativas de revenue management é o custo e a simplicidade de operação, adequados à "
    "escala de um pequeno negócio.", styles["Body"]))

story.append(Paragraph("2.3 Canais", styles["H2"]))
story.append(Paragraph(
    "No MVP, o canal principal é a própria plataforma web, com cadastro direto do anfitrião. "
    "Dado o caráter regional e de nicho do público-alvo, a indicação entre anfitriões da mesma "
    "região tende a ser um canal relevante de aquisição, reforçado por parcerias locais, como "
    "imobiliárias e associações de turismo da Grande Florianópolis, ainda não formalizadas "
    "neste ciclo. Redes sociais e marketing digital regionalizado são cogitados como reforço a "
    "esses canais, mas ficam registrados como Visão Futura, por exigirem orçamento de mídia "
    "ainda não previsto no MVP.", styles["Body"]))

story.append(Paragraph("2.4 Relacionamento com clientes", styles["H2"]))
story.append(Paragraph(
    "O relacionamento é majoritariamente de autoatendimento, compatível com uma operação "
    "enxuta de 30 dias de desenvolvimento. Para o perfil de anfitriã com menor familiaridade "
    "tecnológica, como a persona Marina Andrade, prevê-se um onboarding assistido no primeiro "
    "cadastro, para reduzir a barreira de entrada, complementado por suporte por canal "
    "digital. Conteúdo educativo sobre precificação de temporada, explicando de forma simples "
    "como a sazonalidade afeta o valor sugerido, foi identificado como reforço natural do "
    "relacionamento, mas fica como Visão Futura.", styles["Body"]))

story.append(Paragraph("2.5 Fontes de receita", styles["H2"]))
story.append(Paragraph(
    "A receita prevista é uma assinatura mensal por imóvel gerenciado, com um plano "
    "diferenciado para administradores de carteira que operam um número maior de imóveis. "
    "Uma comissão sobre o valor das reservas processadas pela plataforma, e uma eventual taxa "
    "de onboarding assistido para clientes de menor afinidade técnica, foram identificadas "
    "como fontes de receita complementares, mas estão registradas como Visão Futura, por "
    "dependerem da integração de pagamento real e de uma operação de suporte mais estruturada, "
    "fora do escopo do MVP.", styles["Body"]))

story.append(Paragraph("2.6 Recursos principais", styles["H2"]))
story.append(Paragraph(
    "O recurso mais crítico é o motor de precificação, que depende do acesso à Groq API para "
    "a IA generativa e da regra de negócio de contingência que garante disponibilidade mesmo "
    "quando essa IA falha. A plataforma em si, construída em Spring Boot com persistência em "
    "PostgreSQL, a equipe de desenvolvimento responsável por evoluí-la e a documentação viva "
    "do projeto (as decisões arquiteturais registradas em ADRs e o histórico em "
    "STATUS_PROJETO.md) completam os recursos essenciais deste estágio do negócio, este "
    "último especialmente relevante para a continuidade do projeto entre semestres.",
    styles["Body"]))

story.append(Paragraph("2.7 Atividades-chave", styles["H2"]))
story.append(Paragraph(
    "Quatro atividades sustentam a proposta de valor: o desenvolvimento contínuo da "
    "plataforma, a curadoria do motor de precificação (o ajuste do prompt enviado à IA e da "
    "regra de contingência, à medida que se observam os resultados em uso real), a aquisição "
    "e o onboarding de novos anfitriões na região atendida, e o suporte contínuo aos clientes "
    "já cadastrados.", styles["Body"]))

story.append(Paragraph("2.8 Parcerias-chave", styles["H2"]))
story.append(Paragraph(
    "A Groq fornece a infraestrutura de IA generativa que sustenta a precificação preditiva; "
    "Supabase, Render e Vercel fornecem, respectivamente, o banco de dados gerenciado e a "
    "hospedagem de backend e frontend; o GitHub Actions sustenta a esteira de integração "
    "contínua. Uma parceria com o SEBRAE ou com associações de turismo locais foi identificada "
    "como caminho natural de distribuição, dado o alinhamento do projeto com o Objetivo de "
    "Desenvolvimento Sustentável 8, assim como uma futura integração com OTAs (Airbnb, "
    "Booking) para sincronização de calendário via iCal; nenhuma das duas foi formalizada "
    "neste ciclo.", styles["Body"]))

story.append(Paragraph("2.9 Estrutura de custos", styles["H2"]))
story.append(Paragraph(
    "Os custos concentram-se em infraestrutura em nuvem (com os níveis gratuitos de Supabase, "
    "Render e Vercel cobrindo o MVP), no consumo variável da API de IA generativa, proporcional "
    "ao número de sugestões de preço geradas, na equipe de desenvolvimento e suporte e nas "
    "ferramentas de integração contínua. Não há, neste estágio, custo de aquisição pago; a "
    "aquisição prevista é orgânica, por indicação, com eventual investimento em mídia paga "
    "ainda em avaliação para ciclos futuros.", styles["Body"]))

doc = new_doc(os.path.join(BASE, "out", "06_BUSINESS_MODEL_CANVAS.pdf"), "Business Model Canvas")
hf = make_header_footer_simples(start_page=2)
doc.build(story, onFirstPage=hf, onLaterPages=hf)
print("OK: 06_BUSINESS_MODEL_CANVAS.pdf")
