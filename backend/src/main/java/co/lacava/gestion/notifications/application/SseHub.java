package co.lacava.gestion.notifications.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registro en memoria de emisores SSE (D9, D41): varios por usuario (una pestaña cada uno), sin timeout, y se quitan
 * al completarse, vencer o fallar.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Abre un emisor para el usuario y envía un comentario inicial para que el cliente vea la conexión abierta. */
    public SseEmitter register(long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.compute(userId, (id, list) -> {
            List<SseEmitter> mine = list == null ? new CopyOnWriteArrayList<>() : list;
            mine.add(emitter);
            return mine;
        });
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            cleanup.run();
        }
        return emitter;
    }

    /** Envía el evento a todos los conectados. */
    public void broadcast(String name, Object data) {
        emitters.forEach((userId, list) -> list.forEach(emitter -> send(userId, emitter, name, data)));
    }

    /** Envía el evento solo a las conexiones del usuario (D42). */
    public void sendTo(long userId, String name, Object data) {
        List<SseEmitter> mine = emitters.get(userId);
        if (mine != null) {
            mine.forEach(emitter -> send(userId, emitter, name, data));
        }
    }

    public int connections() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    /** Usuarios con al menos un emisor (visible para pruebas). */
    int trackedUsers() {
        return emitters.size();
    }

    private void send(long userId, SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            remove(userId, emitter);
            log.debug("Emisor SSE descartado: {}", e.toString());
        }
    }

    /** Quita el emisor y, si el usuario se queda sin ninguno, su entrada (atómico frente a {@link #register}). */
    private void remove(long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
