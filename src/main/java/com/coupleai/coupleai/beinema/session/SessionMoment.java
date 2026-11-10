package com.coupleai.coupleai.beinema.session;

/**
 * A moment of the session clock the counselor speaks about: the first partner
 * opened a new session, or a partner entered a session that was already open.
 * directorNote is what the director reads (who entered, who is not in yet,
 * the number of the session and the summary of the last one).
 */
public record SessionMoment(Long roomId, Long userId, String directorNote) {
}
