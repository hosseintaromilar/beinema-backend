# Beinema backend: where things are

One Spring Boot application, split by what the code does (`com.coupleai.coupleai.beinema`):

- `agents`: the companions. `AgentPacks` reads `resources/agents/<slug>.json` (Metis workflow, former workflows, kind, abilities, starting texts); agent listing, access and admin.
- `counseling`: the counselor brain. Director and voice lanes, memory, questions, validation (`DecisionValidator`), turn running (`TurnOrchestrator`, `TurnRunner`, `SessionPulse`), lesson and kit material.
- `governance`, `safety`: pauses, consent and safety cards.
- `chat`: rooms, messages, realtime delivery, leaving a conversation.
- `files`: attachments and reading them.
- `session`: the session clock, its mode, memory and summary.
- `billing` (with `billing.payment`): wallet, top-ups, what a session costs.
- `auth` (with `auth.sms`), `user`, `admin`, `invitation`, `support`, `insight`, `blog`, `mail`, `inbox`, `mirror`, `teach`, `market`, `guide`, `analytics`, `ai`, `metis` (the Metis client).
- Shared layers: `Entity`, `Repository`, `DTO`, `Enum`, `Exception`, `Security`, `Config`, `Util`.

## A new companion

1. Copy its Metis workflow from an existing companion.
2. Add `resources/agents/<slug>.json` with that workflow id, its `type` (`AgentType`), its `abilities` (`OWN_METHOD`, `KIT`, `MARKET`, `TEACH`) and its starting texts.
3. Restart: `CompanionAgentSeeder` adds it once; after that the admin panel owns its name, photo and texts.

Nothing in the engine changes. A companion that moves to a new workflow lists the old id in `formerWorkflowIds`.
