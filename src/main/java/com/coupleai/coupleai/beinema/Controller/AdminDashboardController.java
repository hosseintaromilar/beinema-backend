package com.coupleai.coupleai.beinema.Controller;

import com.coupleai.coupleai.beinema.Entity.ServiceFeedback;
import com.coupleai.coupleai.beinema.Enum.BlogPostStatus;
import com.coupleai.coupleai.beinema.Enum.ChatRoomStatus;
import com.coupleai.coupleai.beinema.Enum.MessageSenderType;
import com.coupleai.coupleai.beinema.Repository.BlogPostRepository;
import com.coupleai.coupleai.beinema.Repository.ChatRoomRepository;
import com.coupleai.coupleai.beinema.Repository.MessageRepository;
import com.coupleai.coupleai.beinema.Repository.ServiceFeedbackRepository;
import com.coupleai.coupleai.beinema.Repository.UserRepository;
import com.coupleai.coupleai.beinema.Service.BlogCommentService;
import com.coupleai.coupleai.beinema.Service.BlogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** The first page of the admin panel: how the service is doing and what waits for the admin. */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final ServiceFeedbackRepository feedbackRepository;
    private final BlogPostRepository blogPostRepository;
    private final BlogCommentService commentService;
    private final BlogWriter blogWriter;
    private final com.coupleai.coupleai.beinema.Service.SupportService supportService;

    @GetMapping
    public Map<String, Object> dashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        // the database keeps server-local times; "today" is the Tehran day
        LocalDateTime todayStart = LocalDate.now(TEHRAN).atStartOfDay(TEHRAN)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        Map<String, Object> users = new LinkedHashMap<>();
        users.put("total", userRepository.count());
        users.put("lastWeek", userRepository.countByCreatedAtAfter(weekAgo));

        Map<String, Object> rooms = new LinkedHashMap<>();
        rooms.put("total", chatRoomRepository.count());
        rooms.put("active", chatRoomRepository.countByStatus(ChatRoomStatus.ACTIVE));
        rooms.put("ended", chatRoomRepository.countByStatus(ChatRoomStatus.ENDED));
        rooms.put("lastWeek", chatRoomRepository.countByCreatedAtAfter(weekAgo));

        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("today", messageRepository.countBySenderTypeAndCreatedAtAfter(MessageSenderType.USER, todayStart));
        messages.put("lastWeek", messageRepository.countBySenderTypeAndCreatedAtAfter(MessageSenderType.USER, weekAgo));

        List<ServiceFeedback> answers = feedbackRepository.findAll();
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("count", answers.size());
        feedback.put("heard", average(answers, ServiceFeedback::getHeard));
        feedback.put("fairness", average(answers, ServiceFeedback::getFairness));
        feedback.put("progress", average(answers, ServiceFeedback::getProgress));
        feedback.put("ease", average(answers, ServiceFeedback::getEase));
        feedback.put("recommend", average(answers, ServiceFeedback::getRecommend));

        Map<String, Object> blog = new LinkedHashMap<>();
        blog.put("drafts", blogPostRepository.countByStatus(BlogPostStatus.DRAFT));
        blog.put("revising", blogPostRepository.countByStatus(BlogPostStatus.REVISING));
        blog.put("published", blogPostRepository.countByStatus(BlogPostStatus.PUBLISHED));
        blog.put("pendingComments", commentService.pendingCount());
        blog.put("writer", blogWriter.status());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", users);
        result.put("rooms", rooms);
        result.put("messages", messages);
        result.put("feedback", feedback);
        result.put("blog", blog);
        result.put("support", Map.of("open", supportService.openCount()));
        return result;
    }

    private static Double average(List<ServiceFeedback> answers, Function<ServiceFeedback, Integer> question) {
        return answers.stream().map(question).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().stream().boxed().findFirst().orElse(null);
    }
}
