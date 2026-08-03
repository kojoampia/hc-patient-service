package net.jojoaddison.aop.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link LoggingAspect}.
 *
 * <p>The aspect wraps every repository, service and resource call, so a bug in it surfaces as a
 * failure in unrelated code. What matters is that it is transparent: the advised method's return
 * value and its exceptions must pass through unchanged, on both the dev and the production paths.</p>
 */
class LoggingAspectTest {

    private MockEnvironment devEnvironment;
    private MockEnvironment prodEnvironment;

    @BeforeEach
    void setUp() {
        devEnvironment = new MockEnvironment();
        devEnvironment.setActiveProfiles("dev");
        prodEnvironment = new MockEnvironment();
        prodEnvironment.setActiveProfiles("prod");
    }

    private static ProceedingJoinPoint joinPoint(Object returnValue) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringTypeName()).thenReturn("net.jojoaddison.service.ProfileService");
        when(signature.getName()).thenReturn("findAll");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { "an-argument" });
        when(joinPoint.proceed()).thenReturn(returnValue);
        return joinPoint;
    }

    @Test
    void returnsTheAdvisedMethodsValueOnTheDevPath() throws Throwable {
        LoggingAspect aspect = new LoggingAspect(devEnvironment);

        assertThat(aspect.logAround(joinPoint("the result"))).isEqualTo("the result");
    }

    @Test
    void returnsTheAdvisedMethodsValueOutsideDev() throws Throwable {
        LoggingAspect aspect = new LoggingAspect(prodEnvironment);

        assertThat(aspect.logAround(joinPoint("the result"))).isEqualTo("the result");
    }

    @Test
    void rethrowsIllegalArgumentExceptionRatherThanSwallowingIt() throws Throwable {
        LoggingAspect aspect = new LoggingAspect(devEnvironment);
        ProceedingJoinPoint joinPoint = joinPoint(null);
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("bad id"));

        assertThatThrownBy(() -> aspect.logAround(joinPoint)).isInstanceOf(IllegalArgumentException.class).hasMessage("bad id");
    }

    @Test
    void logsThrownExceptionsOnTheDevPath() throws Throwable {
        LoggingAspect aspect = new LoggingAspect(devEnvironment);
        Throwable cause = new IllegalStateException("underlying");

        // The advice only logs; the assertion is that it does not itself blow up on either shape
        // of throwable — one with a cause and one without.
        aspect.logAfterThrowing(joinPoint(null), new RuntimeException("boom", cause));
        aspect.logAfterThrowing(joinPoint(null), new RuntimeException("boom"));
    }

    @Test
    void logsThrownExceptionsOutsideDev() throws Throwable {
        LoggingAspect aspect = new LoggingAspect(prodEnvironment);

        aspect.logAfterThrowing(joinPoint(null), new RuntimeException("boom", new IllegalStateException("underlying")));
        aspect.logAfterThrowing(joinPoint(null), new RuntimeException("boom"));
    }

    @Test
    void pointcutsAreDeclarationsOnly() {
        LoggingAspect aspect = new LoggingAspect(devEnvironment);

        // Both are empty by design — the advice lives in the @Around/@AfterThrowing methods.
        aspect.springBeanPointcut();
        aspect.applicationPackagePointcut();
    }
}
