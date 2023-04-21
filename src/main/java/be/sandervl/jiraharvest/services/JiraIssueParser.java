package be.sandervl.jiraharvest.services;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;

@Service
public class JiraIssueParser {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public record StatusChange(String from, String to, LocalDateTime time) {
    }

    public static boolean isCurrentAssigneeOrWasAssigneeInChangelog(JiraService.JiraUser currentUser, JiraService.BasicIssue issue) {
        boolean issueIsAssignedToUser = issue.fields().assignee() != null && issue.fields().assignee().accountId().equals(currentUser.accountId());
        boolean userIsAssigneeInChangelog = issue.changelog().histories().stream().anyMatch(history -> history.items().stream().anyMatch(item -> item.fieldId() != null && item.fromString() != null && item.fieldId().equalsIgnoreCase("assignee") && item.from().equals(currentUser.accountId())));
        return issueIsAssignedToUser || userIsAssigneeInChangelog;
    }

    public Optional<LocalDate> getWorkedOnTimeForIssue(JiraService.BasicIssue issue) {
        var statusChange = issue.changelog().histories().stream()
                .flatMap(history -> history.items().stream()
                        .filter(item -> item.fieldId() != null
                                && item.fromString() != null
                                && item.toStringJava() != null
                                && item.fieldId().equalsIgnoreCase("status")
                        )
                        .map(item -> new StatusChange(item.fromString(), item.toStringJava(), LocalDateTime.parse(history.created(), formatter)))
                ).toList();

        return statusChange.stream()
                .filter(sc -> sc.time != null && sc.from().equalsIgnoreCase("To Do"))
                .min(Comparator.comparing(StatusChange::time))
                .map(StatusChange::time)
                .map(LocalDateTime::toLocalDate);
    }

    public Optional<Duration> getWorkedTimeForIssue(JiraService.BasicIssue issue) {
        var statusChange = issue.changelog().histories().stream()
                .flatMap(history -> history.items().stream()
                        .filter(item -> item.fieldId() != null
                                && item.fromString() != null
                                && item.toStringJava() != null
                                && item.fieldId().equalsIgnoreCase("status")
                        )
                        .map(item -> new StatusChange(item.fromString(), item.toStringJava(), LocalDateTime.parse(history.created(), formatter)))
                ).toList();

        Optional<LocalDateTime> startingTime = statusChange.stream()
                .filter(sc -> sc.time != null && sc.from().equalsIgnoreCase("To Do"))
                .min(Comparator.comparing(StatusChange::time))
                .map(StatusChange::time);
        Optional<LocalDateTime> endingTime = statusChange.stream()
                .filter(sc -> sc.time != null && sc.from().equalsIgnoreCase("In Progress"))
                .max(Comparator.comparing(StatusChange::time))
                .map(StatusChange::time);
        return startingTime.flatMap(st -> endingTime.map(et -> Duration.between(st, et)));
    }

}
