package com.coupleai.coupleai.beinema.Enum;

/** How a counseling session ended. */
public enum SessionEndReason {

    /** Everyone in the session took the break when the 45 minutes were over. */
    BOTH_AGREED,

    /** The 15 extra minutes ran out. */
    TIME_LIMIT,

    /** Nobody wrote anything in it; it does not count as a session. */
    EMPTY,
    /** The owner ended the whole conversation for good during it. */
    ENDED_FOR_GOOD
}
