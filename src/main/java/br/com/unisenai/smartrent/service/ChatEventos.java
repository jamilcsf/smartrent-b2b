package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tempo quase real do SmartChat por SSE (nativo do Spring, sem dependencia
 * nova). O navegador nao envia cabecalho Authorization em EventSource, entao a
 * conexao usa um ticket de uso unico, curto e preso ao usuario, emitido por um
 * endpoint autenticado. O evento nao leva texto: so avisa "ha novidade" e o
 * navegador busca as mensagens pela API autenticada (texto ja filtrado). Se o SSE
 * cair, o front volta ao polling.
 */
@Component
public class ChatEventos {

    private static final Logger log = LoggerFactory.getLogger(ChatEventos.class);
    private static final long TEMPO_LIMITE_MS = 30 * 60 * 1000L;
    private static final long VALIDADE_TICKET_SEG = 60;
    /** Teto de conexoes SSE abertas e de tickets pendentes por usuario: a mais antiga cai (evita esgotar memoria/threads). */
    static final int MAX_POR_USUARIO = 5;

    private record Ticket(Long usuarioId, Instant expira, long ordem) {
    }

    private final Map<Long, Set<SseEmitter>> porUsuario = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong sequencia = new java.util.concurrent.atomic.AtomicLong();
    private final Clock clock;

    public ChatEventos(Clock clock) {
        this.clock = clock;
    }

    public String emitirTicket(Long usuarioId) {
        limparTickets();
        List<Map.Entry<String, Ticket>> pendentes = tickets.entrySet().stream()
                .filter(e -> e.getValue().usuarioId().equals(usuarioId))
                .sorted(Comparator.comparingLong(e -> e.getValue().ordem()))
                .toList();
        for (int i = 0; i <= pendentes.size() - MAX_POR_USUARIO; i++) { // abre espaco para o novo
            tickets.remove(pendentes.get(i).getKey());
        }
        String t = UUID.randomUUID().toString();
        tickets.put(t, new Ticket(usuarioId, clock.instant().plusSeconds(VALIDADE_TICKET_SEG), sequencia.incrementAndGet()));
        return t;
    }

    /** Consome o ticket (uso unico) e abre o fluxo do usuario dono dele. */
    public SseEmitter abrir(String ticket) {
        Ticket t = ticket == null ? null : tickets.remove(ticket);
        if (t == null || t.expira().isBefore(clock.instant())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Ticket inválido ou expirado.");
        }
        SseEmitter emitter = new SseEmitter(TEMPO_LIMITE_MS);
        Set<SseEmitter> lista = porUsuario.computeIfAbsent(t.usuarioId(), k -> new CopyOnWriteArraySet<>());
        while (lista.size() >= MAX_POR_USUARIO) { // a conexao mais antiga cai (o navegador reconecta com novo ticket)
            SseEmitter antigo = lista.iterator().next();
            lista.remove(antigo);
            try {
                antigo.complete();
            } catch (RuntimeException e) {
                // ja encerrada
            }
        }
        lista.add(emitter);
        Runnable remover = () -> lista.remove(emitter);
        emitter.onCompletion(remover);
        emitter.onTimeout(remover);
        emitter.onError(e -> remover.run());
        try {
            emitter.send(SseEmitter.event().name("pronto").data("{}"));
        } catch (IOException e) {
            remover.run();
        }
        return emitter;
    }

    int conexoesAbertas(Long usuarioId) {
        Set<SseEmitter> s = porUsuario.get(usuarioId);
        return s == null ? 0 : s.size();
    }

    int ticketsPendentes(Long usuarioId) {
        return (int) tickets.values().stream().filter(t -> t.usuarioId().equals(usuarioId)).count();
    }

    public boolean conectado(Long usuarioId) {
        Set<SseEmitter> s = porUsuario.get(usuarioId);
        return s != null && !s.isEmpty();
    }

    /** Publica depois do commit da transacao em curso (senao o navegador buscaria algo ainda nao gravado). */
    public void publicar(Long usuarioId, String tipo, Map<String, Object> dados) {
        Runnable envio = () -> enviar(usuarioId, tipo, dados);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }

    private void enviar(Long usuarioId, String tipo, Map<String, Object> dados) {
        Set<SseEmitter> s = porUsuario.get(usuarioId);
        if (s == null) {
            return;
        }
        String json = dados.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + (e.getValue() instanceof Number ? e.getValue() : "\"" + e.getValue() + "\""))
                .reduce((a, b) -> a + "," + b).map(x -> "{" + x + "}").orElse("{}");
        for (SseEmitter em : s) {
            try {
                em.send(SseEmitter.event().name(tipo).data(json));
            } catch (IOException | IllegalStateException e) {
                s.remove(em);
            }
        }
    }

    /** Mantem as conexoes vivas atras de proxies e descobre as que cairam. */
    @Scheduled(fixedRate = 25_000)
    void batimento() {
        porUsuario.forEach((id, s) -> {
            for (SseEmitter em : s) {
                try {
                    em.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    s.remove(em);
                }
            }
        });
    }

    private void limparTickets() {
        Instant agora = clock.instant();
        tickets.values().removeIf(t -> t.expira().isBefore(agora));
    }
}
