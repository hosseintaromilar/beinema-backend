package com.coupleai.coupleai.beinema.counseling;

import com.coupleai.coupleai.beinema.Entity.ChatRoom;
import com.coupleai.coupleai.beinema.Entity.ChatRoomParticipant;
import com.coupleai.coupleai.beinema.Entity.ClarifyingQuestion;
import com.coupleai.coupleai.beinema.Entity.ClientIntent;
import com.coupleai.coupleai.beinema.Entity.CounselingState;
import com.coupleai.coupleai.beinema.Entity.DirectorDecisionRecord;
import com.coupleai.coupleai.beinema.Entity.Message;
import com.coupleai.coupleai.beinema.Entity.PersonMilestone;
import com.coupleai.coupleai.beinema.Repository.PersonMilestoneRepository;
import com.coupleai.coupleai.beinema.Entity.User;
import com.coupleai.coupleai.beinema.Enum.DecisionKind;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Enum.MessageStatus;
import com.coupleai.coupleai.beinema.Enum.ParticipantRole;
import com.coupleai.coupleai.beinema.Enum.QuestionStatus;
import com.coupleai.coupleai.beinema.Repository.ChatRoomAgentRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomParticipantRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.ClarifyingQuestionRepository;
import com.coupleai.coupleai.beinema.Repository.ClientIntentRepository;
import com.coupleai.coupleai.beinema.Repository.CounselingStateRepository;
import com.coupleai.coupleai.beinema.Repository.DirectorDecisionRecordRepository;
import com.coupleai.coupleai.beinema.Repository.MessageAttachmentRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Service.ChatRealtimeService;
import com.coupleai.coupleai.beinema.Service.ChatRoomEventHub;
import com.coupleai.coupleai.beinema.Service.SessionLifecycleService;
import com.coupleai.coupleai.beinema.governance.ParticipationPauseService;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A counseling room held in memory, with the real v3 services wired to it and
 * a scripted brain instead of Metis. Nothing is called over the network.
 */
class FakeRoom {

    static final long ROOM = 5L;
    static final long AGENT = 9L;

    long ids = 100;
    final ChatRoom room = new ChatRoom();
    final User afsaneh = user(1L, "افسانه");
    final User hossein = user(3L, "حسین");
    final List<Message> messages = new ArrayList<>();
    final Map<Long, ClientIntent> intents = new LinkedHashMap<>();
    final Map<Long, ClarifyingQuestion> questions = new LinkedHashMap<>();
    final Map<Long, DirectorDecisionRecord> decisions = new LinkedHashMap<>();
    CounselingState state;

    final ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    final ChatRoomParticipantRepository participants = mock(ChatRoomParticipantRepository.class);
    final MessageRepository messageRepository = mock(MessageRepository.class);
    final MessageAttachmentRepository attachments = mock(MessageAttachmentRepository.class);
    final ClientIntentRepository intentRepository = mock(ClientIntentRepository.class);
    final ClarifyingQuestionRepository questionRepository = mock(ClarifyingQuestionRepository.class);
    final DirectorDecisionRecordRepository decisionRepository = mock(DirectorDecisionRecordRepository.class);
    final CounselingStateRepository stateRepository = mock(CounselingStateRepository.class);
    final ChatRoomAgentRepository agents = mock(ChatRoomAgentRepository.class);
    final ChatRealtimeService realtime = mock(ChatRealtimeService.class);
    final ChatRoomEventHub eventHub = mock(ChatRoomEventHub.class);
    final SessionLifecycleService sessions = mock(SessionLifecycleService.class);
    final ParticipationPauseService pauses = mock(ParticipationPauseService.class);
    final PersonMilestoneRepository milestoneRepository = mock(PersonMilestoneRepository.class);
    final List<PersonMilestone> milestones = new ArrayList<>();

    final ScriptedBrain brain = new ScriptedBrain();
    final CounselingConfig config = new CounselingConfig();
    final TurnInbox inbox = new TurnInbox();
    final CounselingStateStore stateStore = new CounselingStateStore(stateRepository);
    final IntentLedger ledger = new IntentLedger(intentRepository);
    final QuestionService questionService;
    final TurnMaterialLoader loader;
    final TurnPersistence persistence;
    final TurnOrchestrator orchestrator;

    FakeRoom() {
        room.setId(ROOM);
        room.setSessionNumber(1);
        room.setSessionDurationMinutes(45);
        room.setSessionStartedAt(Instant.now().minusSeconds(10 * 60));
        config.setMode("orchestrated");
        config.setWorkflowId("wf");

        ChatRoomParticipant owner = link(afsaneh, ParticipantRole.OWNER);
        ChatRoomParticipant member = link(hossein, ParticipantRole.MEMBER);
        when(rooms.findById(ROOM)).thenReturn(Optional.of(room));
        when(participants.findAllByChatRoom(room)).thenReturn(List.of(owner, member));
        when(participants.existsByChatRoomAndUser(any(), any())).thenReturn(true);
        when(agents.findActiveAgentIds(ROOM)).thenReturn(List.of(AGENT));
        when(agents.findActiveAgentNames(ROOM)).thenReturn(List.of("نادر"));
        when(attachments.findAllByChatRoomIdAndMessageIdIsNotNull(ROOM)).thenReturn(List.of());

        when(messageRepository.findAllByChatRoomOrderBySequenceNumberAsc(any())).thenAnswer(call -> new ArrayList<>(messages));
        when(messageRepository.findMaxSequenceNumberByChatRoom(any())).thenAnswer(call -> Optional.of(messages.size()));
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(call -> store(call.getArgument(0)));
        when(messageRepository.findById(anyLong())).thenAnswer(call -> messages.stream()
                .filter(message -> message.getId().equals(call.getArgument(0))).findFirst());

        when(intentRepository.save(any(ClientIntent.class))).thenAnswer(call -> keep(intents, call.getArgument(0)));
        when(intentRepository.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(intents.get((Long) call.getArgument(0))));
        when(intentRepository.findAllByChatRoomIdOrderByIdAsc(ROOM)).thenAnswer(call -> new ArrayList<>(intents.values()));

        when(questionRepository.save(any(ClarifyingQuestion.class))).thenAnswer(call -> keep(questions, call.getArgument(0)));
        when(questionRepository.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(questions.get((Long) call.getArgument(0))));
        when(questionRepository.findByPublicId(anyString())).thenAnswer(call -> byPublicId(call.getArgument(0)));
        when(questionRepository.findByPublicIdForUpdate(anyString())).thenAnswer(call -> byPublicId(call.getArgument(0)));
        when(questionRepository.findAllByPublicIdIn(anyCollection())).thenAnswer(call -> questions.values().stream()
                .filter(question -> ((Collection<?>) call.getArgument(0)).contains(question.getPublicId())).toList());
        when(questionRepository.findAllByChatRoomIdAndStatus(eq(ROOM), any())).thenAnswer(call -> questions.values().stream()
                .filter(question -> question.getStatus() == call.getArgument(1)).toList());
        when(questionRepository.countByChatRoomIdAndSessionNumberAndStatusIn(eq(ROOM), any(), anyCollection())).thenAnswer(call ->
                questions.values().stream().filter(question -> ((Collection<?>) call.getArgument(2)).contains(question.getStatus())
                        && Objects.equals(question.getSessionNumber(), call.getArgument(1))).count());

        when(decisionRepository.save(any(DirectorDecisionRecord.class))).thenAnswer(call -> keep(decisions, call.getArgument(0)));
        when(decisionRepository.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(decisions.get((Long) call.getArgument(0))));
        when(decisionRepository.findByChatRoomIdAndTriggerMessageId(eq(ROOM), any())).thenAnswer(call -> decisions.values().stream()
                .filter(record -> Objects.equals(record.getTriggerMessageId(), call.getArgument(1))).findFirst());
        when(decisionRepository.findByChatRoomIdAndKindOrderByIdDesc(eq(ROOM), any(DecisionKind.class), any(Pageable.class)))
                .thenAnswer(call -> {
                    List<DirectorDecisionRecord> list = new ArrayList<>(decisions.values().stream()
                            .filter(record -> record.getKind() == call.getArgument(1)).toList());
                    java.util.Collections.reverse(list);
                    return list.subList(0, Math.min(6, list.size()));
                });

        when(stateRepository.findByChatRoomId(ROOM)).thenAnswer(call -> Optional.ofNullable(state));
        when(stateRepository.save(any(CounselingState.class))).thenAnswer(call -> {
            CounselingState saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(++ids);
                saved.setVersion(0L);
            } else {
                saved.setVersion(saved.getVersion() + 1);
            }
            state = saved;
            return saved;
        });

        when(realtime.saveAgentAndPublish(eq(ROOM), any(), anyString())).thenAnswer(call -> agentSays(call.getArgument(2), null));
        when(realtime.saveAgentAndPublish(eq(ROOM), any(), anyString(), any())).thenAnswer(call -> agentSays(call.getArgument(2), call.getArgument(3)));
        when(realtime.saveAgentAndPublish(eq(ROOM), any(), anyString(), isNull())).thenAnswer(call -> agentSays(call.getArgument(2), null));

        questionService = new QuestionService(questionRepository, rooms, participants, messageRepository, ledger, sessions, pauses);
        when(milestoneRepository.save(any(PersonMilestone.class))).thenAnswer(call -> {
            milestones.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(milestoneRepository.findAllByChatRoomIdOrderByIdAsc(ROOM)).thenAnswer(call -> new ArrayList<>(milestones));
        loader = new TurnMaterialLoader(rooms, participants, messageRepository, attachments, intentRepository, questionRepository,
                decisionRepository, stateStore, new KnowledgeRetriever(new KnowledgeBase()), inbox, config, milestoneRepository, agents);
        persistence = new TurnPersistence(decisionRepository, stateRepository, stateStore, intentRepository, ledger, questionService,
                milestoneRepository);
        orchestrator = new TurnOrchestrator(loader, brain, config, persistence, questionService, realtime, eventHub, pauses,
                rooms, agents, decisionRepository, inbox);
        orchestrator.pacing = false;
    }

    Message say(User user, String text) {
        Message message = Message.builder().chatRoom(room).senderType(MessageSenderType.USER).senderId(user.getId())
                .content(text).status(MessageStatus.SENT).sequenceNumber(messages.size() + 1).build();
        return store(message);
    }

    Message agentSays(String text, Long replyTo) {
        Message message = Message.builder().chatRoom(room).senderType(MessageSenderType.AGENT).senderId(AGENT)
                .content(text).status(MessageStatus.SENT).sequenceNumber(messages.size() + 1).replyToMessageId(replyTo).build();
        return store(message);
    }

    List<Message> counselorMessages() {
        return messages.stream().filter(message -> message.getSenderType() == MessageSenderType.AGENT).toList();
    }

    ClarifyingQuestion openQuestion() {
        return questions.values().stream().filter(question -> question.getStatus() == QuestionStatus.OPEN).findFirst().orElse(null);
    }

    CounselingSnapshot memory() {
        return CounselingStateStore.snapshot(state);
    }

    private Message store(Message message) {
        if (message.getId() == null) {
            message.setId(++ids);
            message.setCreatedAt(LocalDateTime.now());
            messages.add(message);
        }
        return message;
    }

    private <T extends com.coupleai.coupleai.beinema.Entity.BaseEntity> T keep(Map<Long, T> table, T entity) {
        if (entity.getId() == null) {
            entity.setId(++ids);
            entity.setCreatedAt(LocalDateTime.now());
        }
        table.put(entity.getId(), entity);
        return entity;
    }

    private Optional<ClarifyingQuestion> byPublicId(String publicId) {
        return questions.values().stream().filter(question -> question.getPublicId().equals(publicId)).findFirst();
    }

    private static User user(long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    private static ChatRoomParticipant link(User user, ParticipantRole role) {
        ChatRoomParticipant participant = new ChatRoomParticipant();
        participant.setUser(user);
        participant.setRole(role);
        participant.setSessionConsented(true);
        return participant;
    }

    /** Director and voice replies in order; each can depend on the packet it receives. */
    static class ScriptedBrain implements BrainGateway {
        final List<Function<String, String>> directorReplies = new ArrayList<>();
        final List<Function<String, String>> voiceReplies = new ArrayList<>();
        final List<String> directorPackets = new ArrayList<>();
        final List<String> voicePackets = new ArrayList<>();
        final List<String> plannerPackets = new ArrayList<>();
        String plannerReply = "";
        final List<String> teacherPackets = new ArrayList<>();
        String teacherReply = "";

        @Override
        public Reply teacher(String packet) {
            teacherPackets.add(packet);
            return new Reply(teacherReply, 6);
        }

        @Override
        public Reply planner(String packet) {
            plannerPackets.add(packet);
            return new Reply(plannerReply, 4);
        }

        @Override
        public Reply director(String packet) {
            directorPackets.add(packet);
            if (directorReplies.isEmpty()) {
                throw new IllegalStateException("no scripted director reply");
            }
            return new Reply(directorReplies.remove(0).apply(packet), 5);
        }

        @Override
        public Reply voice(String packet) {
            voicePackets.add(packet);
            if (voiceReplies.isEmpty()) {
                throw new IllegalStateException("no scripted voice reply");
            }
            return new Reply(voiceReplies.remove(0).apply(packet), 3);
        }
    }
}
