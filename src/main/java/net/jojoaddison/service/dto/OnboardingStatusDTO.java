package net.jojoaddison.service.dto;

import net.jojoaddison.domain.enumeration.OnboardingStatus;

/**
 * What the portal's guard asks for, and the only call it makes before deciding where to send someone.
 *
 * @param status where the patient is. <strong>Null means COMPLETE</strong>, not "not started" — every profile written
 *               before onboarding existed reads null, and dragging those patients through a wizard they never needed
 *               would empty the quality stack's dashboard on the day this ships.
 * @param step the highest step answered, so a returning patient resumes rather than restarts.
 * @param profileId the patient's profile, or null when they have none yet — which is what "not started" actually is.
 * @param onboarded convenience for the guard, so the redirect rule lives in one place rather than in every client.
 */
public record OnboardingStatusDTO(OnboardingStatus status, Integer step, String profileId, boolean onboarded) {}
