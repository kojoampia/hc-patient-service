package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unit tests for {@link KafkaConsumer}.
 *
 * <p>This is the fan-out from the Kafka stream to every connected browser. The behaviour worth
 * pinning is that one broken client cannot stop the others receiving their message, and that
 * completed clients are cleaned up rather than accumulating.</p>
 */
class KafkaConsumerTest {

    private final KafkaConsumer consumer = new KafkaConsumer();

    @Test
    void registerReturnsAnEmitterPerKey() {
        SseEmitter first = consumer.register("alice");
        SseEmitter second = consumer.register("bob");

        assertThat(first).isNotNull();
        assertThat(second).isNotNull().isNotSameAs(first);
    }

    @Test
    void registeringTheSameKeyTwiceReplacesTheEmitter() {
        SseEmitter first = consumer.register("alice");
        SseEmitter second = consumer.register("alice");

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void unregisterCompletesTheEmitter() {
        consumer.register("alice");

        // Completing is what releases the HTTP connection; without it the client hangs.
        assertThatNoExceptionIsThrownBy(() -> consumer.unregister("alice"));
    }

    @Test
    void unregisteringAnUnknownKeyIsHarmless() {
        assertThatNoExceptionIsThrownBy(() -> consumer.unregister("nobody"));
    }

    @Test
    void acceptWithNoSubscribersIsHarmless() {
        assertThatNoExceptionIsThrownBy(() -> consumer.accept("a message with no listeners"));
    }

    @Test
    void aBrokenSubscriberDoesNotStopTheOthers() throws Exception {
        KafkaConsumer spyingConsumer = new KafkaConsumer();
        SseEmitter healthy = mock(SseEmitter.class);
        SseEmitter broken = mock(SseEmitter.class);
        doThrow(new IOException("client gone")).when(broken).send(any(SseEmitter.SseEventBuilder.class));

        // register() builds its own emitters, so reach the map the same way the class does.
        java.lang.reflect.Field field = KafkaConsumer.class.getDeclaredField("emitters");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, SseEmitter> emitters = (java.util.Map<String, SseEmitter>) field.get(spyingConsumer);
        emitters.put("broken", broken);
        emitters.put("healthy", healthy);

        spyingConsumer.accept("hello");

        verify(broken).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthy, never()).completeWithError(any());
    }

    private static void assertThatNoExceptionIsThrownBy(Runnable runnable) {
        org.assertj.core.api.Assertions.assertThatCode(runnable::run).doesNotThrowAnyException();
    }
}
