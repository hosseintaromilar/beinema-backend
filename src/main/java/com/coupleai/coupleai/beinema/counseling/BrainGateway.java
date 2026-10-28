package com.coupleai.coupleai.beinema.counseling;

/**
 * The counselor brain in Metis (workflow beinema-counselor-v3). Two lanes of one
 * workflow: the director decides (JSON), the voice speaks (Persian text). The
 * prompts and the models are edited in the Metis console, not in code.
 */
public interface BrainGateway {

    /** A reply and how long it took. text is "" when Metis answered nothing. */
    record Reply(String text, long millis) {
    }

    Reply director(String packet);

    /** The brain of this room's companion; every lane of a turn goes through it. */
    default BrainGateway forRoom(com.coupleai.coupleai.beinema.Entity.ChatRoom room) {
        return this;
    }

    Reply voice(String packet);

    /** The session manager lane: the plan of the session (JSON). Empty when there is no such lane. */
    default Reply planner(String packet) {
        return new Reply("", 0);
    }

    /** The teacher lane: a lesson fitted to the couple (JSON). Empty when there is no such lane. */
    default Reply teacher(String packet) {
        return new Reply("", 0);
    }
}
