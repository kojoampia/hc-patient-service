package net.jojoaddison.service;

/**
 * A care-delegation transition that was asked for and is not allowed from where the delegation is.
 *
 * <p>This lives in {@code service} rather than being a {@code BadRequestAlertException} thrown directly, because the
 * ArchUnit layer rules do not let {@code service} depend on {@code web} — and the rule is right here. The state machine
 * is a domain fact: "a declined nomination cannot be accepted" is true whether the caller arrived over HTTP, through a
 * Kafka consumer, or from a migration. {@code web/rest/errors/ExceptionTranslator} turns it into the same RFC-7807
 * response any other bad request produces, so callers cannot tell the difference.</p>
 */
public class DelegationStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String entityName;
    private final String errorKey;

    public DelegationStateException(String message, String entityName, String errorKey) {
        super(message);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
