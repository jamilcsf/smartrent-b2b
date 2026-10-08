"""
SmartRent B2B - dados de demonstracao da MODERACAO (denuncias e alertas), pelo caminho real da aplicacao.

Nao e migration. Com o servidor no ar (porta 8082 por padrao aqui; ajuste B) e os dados de demonstracao carregados
(scripts/dados-demonstracao.sql), o script: abre uma conversa do cliente com a gestora, troca mensagens (o filtro age de
verdade: telefone, e-mail, contato externo, link e pedido de pagamento por fora) e registra 3 denuncias.
O alerta de ENVIO_EM_MASSA nao se reproduz com 2 usuarios; para te-lo na demo:

  docker exec -i smartrent-pg psql -U postgres -d smartrent_demo -c "insert into alertas_internos
    (usuario_id,tipo,contagem,janela_inicio,janela_fim,status,criado_em) values
    (2,'ENVIO_EM_MASSA',6, now()-interval '12 minutes', now()-interval '2 minutes','ABERTO', now()-interval '2 minutes')"

Uso:  python scripts/dados-moderacao-demo.py     (so stdlib; senha das contas de demo: senhaSegura123)
"""
import json, urllib.request, subprocess

B = 'http://localhost:8082'  # ajuste para a porta do seu servidor

def req(metodo, caminho, corpo=None, token=None):
    d = json.dumps(corpo).encode() if corpo is not None else None
    r = urllib.request.Request(B + caminho, data=d, method=metodo, headers={'Content-Type': 'application/json'})
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(r) as resp:
            t = resp.read().decode()
            return json.loads(t) if t else None
    except urllib.error.HTTPError as e:
        return {'__erro': e.code, 'corpo': e.read().decode()[:300]}

def login(email):
    return req('POST', '/api/auth/login', {'email': email, 'senha': 'senhaSegura123', 'captchaToken': 'x'})['token']

tc = login('cliente@smartrent.dev')
tg = login('ana@smartrent.dev')
conv = req('POST', '/conversas/por-imovel/1'.replace('/conversas', '/api/smartchat/conversas'), None, tc)
print('conversa', conv.get('codigo') if conv else conv)
cod = conv['codigo']

# mensagens (o filtro age de verdade: telefone, e-mail, link, golpe)
msgs_cliente = ['Oi, tudo bem? Gostaria de saber se o apto aceita pets.',
                'Pode me passar o telefone 48 99999-1234 para falarmos melhor?']
msgs_gestor = ['Ola! Aceita sim, ate 10kg.',
               'Se preferir, me chama no zap, ou mande email para ana.golpe@gmail.com',
               'Faz o pagamento por pix direto para mim com desconto, fora da plataforma, que eu confirmo a reserva.',
               'Veja detalhes em http://site-externo.exemplo/reserva']
ids = {}
for t in msgs_cliente:
    r = req('POST', f'/api/smartchat/conversas/{cod}/mensagens', {'texto': t}, tc)
    print('cliente ->', r.get('mensagem', {}).get('codigo') if isinstance(r, dict) and 'mensagem' in r else r)
for t in msgs_gestor:
    r = req('POST', f'/api/smartchat/conversas/{cod}/mensagens', {'texto': t}, tg)
    ids[t[:12]] = r['mensagem']['codigo'] if isinstance(r, dict) and 'mensagem' in r else None
    print('gestor ->', ids[t[:12]] or r)

anexar = [v for v in ids.values() if v][1:3]
r = req('POST', f'/api/smartchat/conversas/{cod}/denuncias', {'motivo': 'TENTATIVA_DE_GOLPE', 'descricao': 'Pediu para pagar por pix fora da plataforma e passou contato externo.', 'mensagensIds': anexar}, tc)
print('denuncia 1', r)
r = req('POST', f'/api/smartchat/conversas/{cod}/denuncias', {'motivo': 'SPAM', 'descricao': 'Mandou link de site externo.', 'mensagensIds': [list(ids.values())[-1]]}, tc)
print('denuncia 2', r)
r = req('POST', f'/api/smartchat/conversas/{cod}/denuncias', {'motivo': 'ASSEDIO_OFENSAS', 'descricao': None, 'mensagensIds': []}, tg)
print('denuncia 3 (gestor contra cliente)', r)
