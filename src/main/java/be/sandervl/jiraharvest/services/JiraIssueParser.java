package be.sandervl.jiraharvest.services;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class JiraIssueParser {
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  public static boolean isCurrentAssigneeOrWasAssigneeInChangelog(
      JiraService.JiraUser currentUser, JiraService.BasicIssue issue) {
    boolean issueIsAssignedToUser =
        issue.fields().assignee() != null
            && issue.fields().assignee().accountId().equals(currentUser.accountId());
    boolean userIsAssigneeInChangelog =
        issue.changelog().histories().stream()
            .anyMatch(
                history ->
                    history.items().stream()
                        .anyMatch(
                            item ->
                                item.fieldId() != null
                                    && item.fromString() != null
                                    && item.fieldId().equalsIgnoreCase("assignee")
                                    && item.from().equals(currentUser.accountId())));
    return issueIsAssignedToUser || userIsAssigneeInChangelog;
  }

  public Optional<LocalDate> getWorkedOnTimeForIssue(JiraService.BasicIssue issue) {
    List<StatusChange> statusChange = getStatusChanges(issue);

    return statusChange.stream()
        .filter(sc -> sc.time != null && sc.to().equalsIgnoreCase("In Progress"))
        .min(Comparator.comparing(StatusChange::time))
        .map(StatusChange::time)
        .map(LocalDateTime::toLocalDate);
  }

  private List<StatusChange> getStatusChanges(JiraService.BasicIssue issue) {
    return issue.changelog().histories().stream()
        .flatMap(
            history ->
                history.items().stream()
                    .filter(
                        item ->
                            item.fieldId() != null
                                && item.fromString() != null
                                && item.toStringJava() != null
                                && item.fieldId().equalsIgnoreCase("status"))
                    .map(
                        item ->
                            new StatusChange(
                                item.fromString(),
                                item.toStringJava(),
                                LocalDateTime.parse(history.created(), formatter))))
        .toList();
  }

  public Optional<Duration> getWorkedTimeForIssue(JiraService.BasicIssue issue) {
    List<StatusChange> statusChange = getStatusChanges(issue);

    Optional<LocalDateTime> startingTime =
        statusChange.stream()
            .filter(sc -> sc.time != null && sc.from().equalsIgnoreCase("To Do"))
            .min(Comparator.comparing(StatusChange::time))
            .map(StatusChange::time);
    Optional<LocalDateTime> endingTime =
        statusChange.stream()
            .filter(sc -> sc.time != null && sc.from().equalsIgnoreCase("In Progress"))
            .max(Comparator.comparing(StatusChange::time))
            .map(StatusChange::time);

    return startingTime.flatMap(
        start ->
            endingTime.map(
                end -> {
                  if (start.toLocalDate().equals(end.toLocalDate())) {
                    return Duration.between(start, end);
                  }

                  LocalTime startWorkingTime = LocalTime.of(9, 0);
                  LocalTime endWorkingTime = LocalTime.of(17, 0);

                  return Stream.iterate(start, date -> date.plusDays(1))
                      .limit(ChronoUnit.DAYS.between(start, end.plusDays(1)) + 1)
                      .filter(
                          date ->
                              date.getDayOfWeek() != DayOfWeek.SATURDAY
                                  && date.getDayOfWeek() != DayOfWeek.SUNDAY)
                      .map(
                          date -> {
                            LocalDateTime startOfWorkingTime = date.with(startWorkingTime);
                            LocalDateTime endOfWorkingTime = date.with(endWorkingTime);
                            LocalDateTime startOfOverlap =
                                start.isBefore(startOfWorkingTime) ? startOfWorkingTime : start;
                            LocalDateTime endOfOverlap =
                                end.isAfter(endOfWorkingTime) ? endOfWorkingTime : end;
                            return Duration.between(startOfOverlap, endOfOverlap);
                          })
                      .reduce(Duration.ZERO, Duration::plus);
                }));
  }

  public record StatusChange(String from, String to, LocalDateTime time) {}
}
