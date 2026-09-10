package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.Membership;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.MembershipStatus;
import net.jojoaddison.repository.MembershipRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.PatientScope;
import net.jojoaddison.service.MembershipService;
import net.jojoaddison.service.event.PatientEventPublisher;
import net.jojoaddison.service.event.PatientEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;

/**
 * That choosing a plan tells hc-admin, and that saying so can never cost the patient their subscription.
 *
 * <p>Until 2026-09-08 a patient chose a tier, a {@code PENDING} membership was written and nothing was published, so
 * the back office learned of it only by looking. Backlog item 18.</p>
 *
 * <p><strong>This class pins a cross-repo contract, so it names the strings rather than the constants.</strong>
 * hc-admin's {@code SiblingEventParser} dispatches on the literal {@code "PlanChosen"} and reads the payload keys by
 * name; neither is a compile error there if this repository renames one, it is an event their {@code switch} silently
 * ignores. The assertions below are deliberately written as literals for that reason — if one has to be edited, the
 * other repository has to be edited in the same breath.</p>
 *
 * <p>The end-to-end proof that the event really reaches the topic is {@link MembershipPlanEventIT}; this class is the
 * one that can arrange a failing write, which no integration test against a live Mongo can.</p>
 */
class MembershipPlanEventTest {

    private static final String PATIENT_ID = "patient-ama";
    private static final String PATIENT_EMAIL = "Ama@Example.Test";

    private MembershipRepository memberships;
    private PatientScope patientScope;
    private ProfileRepository profiles;
    private PatientEventPublisher events;
    private MembershipResource resource;

    @BeforeEach
    void setUp() {
        memberships = mock(MembershipRepository.class);
        patientScope = mock(PatientScope.class);
        profiles = mock(ProfileRepository.class);
        events = mock(PatientEventPublisher.class);
        // The MongoTemplate is item 19's, not this test's: MembershipService reaches for it only in
        // activateIfPending, which is the inbound consumer's conditional update and no part of any HTTP path.
        resource =
            new MembershipResource(
                new MembershipService(memberships, profiles, events, mock(MongoTemplate.class)),
                memberships,
                patientScope
            );

        when(patientScope.requirePatientIdForWrite(any())).thenReturn(PATIENT_ID);
        when(memberships.save(any(Membership.class))).thenAnswer(call -> ((Membership) call.getArgument(0)).id("membership-1"));
        when(profiles.findByPatientId(PATIENT_ID)).thenReturn(List.of(new Profile().patientId(PATIENT_ID).email(PATIENT_EMAIL)));
    }

    /** What both clients' {@code choosePlan} posts: the plan's code into {@code plan}, its display name into {@code name}. */
    private static Membership chosenPlan() {
        return new Membership()
            .patientId(PATIENT_ID)
            .plan("PAWPAW")
            .name("PAWPAW Plan")
            .description("For a growing family")
            .status(MembershipStatus.PENDING);
    }

    @Test
    @SuppressWarnings("unchecked")
    void choosingAPlanPublishesPlanChosen() throws Exception {
        resource.createMembership(chosenPlan());

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> patientId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(type.capture(), email.capture(), any(), patientId.capture(), data.capture());

        // The type string hc-admin switches on. Renaming it is a two-repository change.
        assertThat(type.getValue()).isEqualTo("PlanChosen");
        assertThat(type.getValue()).isEqualTo(PatientEventType.PLAN_CHOSEN);

        // Keyed on the patient, like every other event here — the publisher lowercases it.
        assertThat(email.getValue()).isEqualTo(PATIENT_EMAIL);
        assertThat(patientId.getValue()).isEqualTo(PATIENT_ID);

        // The payload shape, in full. containsOnlyKeys rather than containsEntry: a key quietly added here is a key
        // hc-admin has not agreed to, and one quietly dropped is one they are still reading.
        assertThat(data.getValue()).containsOnlyKeys("membershipId", "planCode", "planName", "status");
        assertThat(data.getValue())
            .containsEntry("membershipId", "membership-1")
            // planCode is Membership.plan and planName is Membership.name. The document has no `code` field, whatever
            // item 18's field list says — this is what choosePlan actually writes.
            .containsEntry("planCode", "PAWPAW")
            .containsEntry("planName", "PAWPAW Plan")
            .containsEntry("status", "PENDING");
        // Free text a client supplied. The event says which plan was chosen, never what was said about it.
        assertThat(data.getValue()).doesNotContainKey("description");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMembershipWithNoPlanOmitsTheKeysRatherThanPuttingNullsOnTheWire() throws Exception {
        // The administrative CRUD path: an administrator creating a membership with no tier named. Membership.plan
        // carries no @NotNull, so this used to publish planCode: null and planName: null beside a real membership id.
        // hc-admin's DirectoryProjectionService cites that line of ours by name — it was a defect on their side until
        // their item 48 review, because our own containsOnlyKeys test above pins the key SET and says nothing about
        // the values, and they generalised from it. Nothing on their side needs to change for this; it stops us
        // asking a consumer to tell "no plan" from "we forgot the plan" with nothing on the wire to tell it by.
        resource.createMembership(new Membership().patientId(PATIENT_ID).status(MembershipStatus.PENDING));

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq("PlanChosen"), any(), any(), any(), data.capture());

        assertThat(data.getValue()).containsOnlyKeys("membershipId", "status");
        assertThat(data.getValue()).containsEntry("membershipId", "membership-1").containsEntry("status", "PENDING");
    }

    @Test
    void aRefusedWritePublishesNothing() throws Exception {
        // The realistic failure: an account with no profile behind it cannot own a record, so PatientScope refuses
        // before anything is saved. There is no membership, so there is nothing to announce.
        when(patientScope.requirePatientIdForWrite(any())).thenThrow(new AccessDeniedException("no profile"));

        assertThatThrownBy(() -> resource.createMembership(chosenPlan())).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(events);
    }

    @Test
    void aFailedSavePublishesNothing() throws Exception {
        // And the other one: the write reached Mongo and did not land. Announcing a subscription that does not exist
        // would leave hc-admin holding a pending membership nobody can approve, because it is not there.
        when(memberships.save(any(Membership.class))).thenThrow(new DataAccessResourceFailureException("mongo is gone"));

        assertThatThrownBy(() -> resource.createMembership(chosenPlan())).isInstanceOf(DataAccessResourceFailureException.class);

        verifyNoInteractions(events);
    }

    @Test
    void aMembershipWithAnIdIsRefusedAndPublishesNothing() throws Exception {
        assertThatThrownBy(() -> resource.createMembership(chosenPlan().id("already-exists")))
            .isInstanceOf(net.jojoaddison.web.rest.errors.BadRequestAlertException.class);

        verifyNoInteractions(events);
    }

    @Test
    void aBrokerOutageDoesNotCostThePatientTheirSubscription() throws Exception {
        // Against the REAL publisher and a broken StreamBridge, not a mocked publisher. That distinction is the whole
        // point after review: the guarantee is PatientEventPublisher's — it swallows send failures itself — and this
        // resource must not add a catch of its own to reproduce it, because the same catch would also swallow the
        // assertNothingClinical throw that is meant to be loud (see aClinicalKeyInThePayloadIsNotSwallowed). Mocking
        // the publisher would have tested this class's catch instead of the real division of responsibility.
        StreamBridge brokenBroker = mock(StreamBridge.class);
        when(brokenBroker.send(anyString(), any())).thenThrow(new IllegalStateException("broker down"));
        MembershipResource withRealPublisher = new MembershipResource(
            new MembershipService(memberships, profiles, new PatientEventPublisher(brokenBroker), mock(MongoTemplate.class)),
            memberships,
            patientScope
        );

        assertThatCode(() -> withRealPublisher.createMembership(chosenPlan())).doesNotThrowAnyException();

        verify(brokenBroker).send(eq(PatientEventPublisher.BINDING), any());
    }

    @Test
    void anUnreadableProfileDoesNotCostThePatientTheirSubscription() throws Exception {
        // The lookup this class introduced is a Mongo query, and it runs after the membership is saved. Without the
        // catch around it, a database hiccup while resolving an email would turn a successful subscription into a 500.
        when(profiles.findByPatientId(PATIENT_ID)).thenThrow(new DataAccessResourceFailureException("mongo is gone"));

        assertThatCode(() -> resource.createMembership(chosenPlan())).doesNotThrowAnyException();
    }

    @Test
    void aMembershipWhoseOwnerHasNoProfilePassesANullEmailRatherThanInventingOne() throws Exception {
        // This class's share of the rule, and only this share. Refusing to send an unkeyed frame belongs to
        // PatientEventPublisher — it was written here first and CareDelegationService promptly proved that was the
        // wrong place, so the guard moved to the envelope and PatientEventPublisherTest owns the refusal. What is
        // still this method's job is to pass a null rather than substitute the caller's address, because an event
        // filed under the wrong person is worse than one not filed at all.
        when(profiles.findByPatientId(PATIENT_ID)).thenReturn(List.of());
        when(profiles.findById(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> resource.createMembership(chosenPlan())).doesNotThrowAnyException();

        verify(events).publish(eq("PlanChosen"), eq(null), any(), eq(PATIENT_ID), any());
    }

    @Test
    void aMembershipForAnUnnamedPatientPassesANullEmail() throws Exception {
        // The path that reaches this in practice, and the reason it is not hypothetical: a caller with no acting-as
        // header and no patientId in the body gets it back unchanged — PatientScope.requirePatientIdForWrite returns
        // what it was given, including null. This repo's own MembershipResourceIT does exactly that, so before the
        // publisher's guard every CI run put an event about nobody on the topic.
        when(patientScope.requirePatientIdForWrite(any())).thenReturn(null);

        assertThatCode(() -> resource.createMembership(chosenPlan().patientId(null))).doesNotThrowAnyException();

        verify(events).publish(eq("PlanChosen"), eq(null), any(), eq(null), any());
        verifyNoInteractions(profiles);
    }

    @Test
    void aClinicalKeyInThePayloadIsNotSwallowed() throws Exception {
        // The publisher throws from assertNothingClinical deliberately — "quietly dropping it would let the caller
        // believe the field is being published" — and that throw happens OUTSIDE its own try. announceChosenPlan must
        // not put a blanket catch around it: a payload extension that picked a denylisted key would then lose every
        // event in production while this method reported success, which is the failure item 18 exists to end.
        doThrow(new IllegalArgumentException("clinical key")).when(events).publish(anyString(), any(), any(), any(), any());

        assertThatThrownBy(() -> resource.createMembership(chosenPlan())).isInstanceOf(IllegalArgumentException.class);
    }
}
