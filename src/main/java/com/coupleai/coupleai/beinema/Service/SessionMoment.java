package com.coupleai.coupleai.beinema.Service;

/**
 * A moment of the session clock the counselor speaks about: the first partner
 * opened a new session, or a partner entered a session that was already open.
 *
 * directorNote is what the v3 director reads (who entered, who is not in yet,
 * the number of the session and the summary of the last one); v2Cue is the old
 * workflow's cue, or null when the old workflow says nothing at this moment.
 */
public record SessionMoment(Long roomId, Long userId, String directorNote, String v2Cue) {
}
