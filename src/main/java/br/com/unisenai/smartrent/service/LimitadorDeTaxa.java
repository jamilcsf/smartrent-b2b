package br.com.unisenai.smartrent.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit por chave (ex.: usuario) em janela deslizante, em memoria. Suficiente
 * para uma instancia; com varias instancias, trocar por um limitador distribuido
 * (Redis etc.) com a mesma interface.
 */
@Component
public class LimitadorDeTaxa {

    private final Map<String, Deque<Instant>> janelas = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimitadorDeTaxa(Clock clock) {
        this.clock = clock;
    }

    /** Registra uma tentativa e diz se ela cabe em {@code limite} por {@code janela}. */
    public boolean permitir(String chave, int limite, Duration janela) {
        Instant agora = clock.instant();
        Deque<Instant> fila = janelas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            Instant corte = agora.minus(janela);
            while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) {
                fila.pollFirst();
            }
            if (fila.size() >= limite) {
                return false;
            }
            fila.addLast(agora);
            return true;
        }
    }

    /** So consulta: ja ha {@code limite} ou mais ocorrencias na janela? (Nao registra nada.) */
    public boolean atingiu(String chave, int limite, Duration janela) {
        Deque<Instant> fila = janelas.get(chave);
        if (fila == null) {
            return false;
        }
        synchronized (fila) {
            Instant corte = clock.instant().minus(janela);
            while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) {
                fila.pollFirst();
            }
            return fila.size() >= limite;
        }
    }

    /** Registra uma ocorrencia (por exemplo, uma senha errada) sem decidir nada. */
    public void registrar(String chave) {
        Deque<Instant> fila = janelas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            fila.addLast(clock.instant());
        }
    }

    /** Esquece as ocorrencias da chave (depois de um sucesso). */
    public void limpar(String chave) {
        janelas.remove(chave);
    }
}
