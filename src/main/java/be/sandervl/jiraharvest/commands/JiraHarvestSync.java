package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.HarvestService;
import be.sandervl.jiraharvest.services.JiraIssueParser;
import be.sandervl.jiraharvest.services.JiraService;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jline.terminal.Terminal;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class JiraHarvestSync {

  protected final Logger LOG = Logger.getLogger(getClass().getName());

  private final JiraService jiraService;
  private final HarvestService harvestService;
  private final JiraIssueParser issueParser;
  private final ComponentFlow.Builder componentFlowBuilder;
  private final Terminal terminal;
  private Set<HarvestService.ProjectAssignment> projectAssignments;

  public JiraHarvestSync(
      JiraService jiraService,
      HarvestService harvestService,
      JiraIssueParser issueParser,
      ComponentFlow.Builder componentFlowBuilder,
      Terminal terminal) {
    this.jiraService = jiraService;
    this.harvestService = harvestService;
    this.issueParser = issueParser;
    this.componentFlowBuilder = componentFlowBuilder;
    this.terminal = terminal;
    projectAssignments =
        StreamSupport.stream(harvestService.getProjectAssignments().spliterator(), false)
            .collect(Collectors.toSet());
  }

  @ShellMethod(value = "Start Jira Harvest sync", key = "start")
  public void start() {
    var existingTimeEntryNotes =
        StreamSupport.stream(harvestService.getTimeEntries().spliterator(), false)
            .map(HarvestService.TimeEntry::notes)
            .filter(note -> note != null && !note.equals(""))
            .collect(Collectors.toSet());
    var jiraIssues = StreamSupport.stream(jiraService.getIssues().spliterator(), false);

    jiraIssues
        .filter(issue -> !existingTimeEntryNotes.contains(issue.key()))
        .forEach(this::process);
  }

  private void process(JiraService.BasicIssue issue) {
    try {
      String statement =
          String.format("Processing Jira issue '%s - %s'\n", issue.key(), issue.fields().summary());
      terminal.writer().print(statement);

      var taskAndProjectEntry =
          getTaskAndProjectFromIssue(issue).orElseGet(this::projectAndTaskCorrectionFlow);

      var projectAssignment = taskAndProjectEntry._1();
      var taskAssignment = taskAndProjectEntry._2();
      var spentDate =
          issueParser.getWorkedOnTimeForIssue(issue).orElseGet(this::spentDateCorrectionFlow);
      Double hours =
          Math.max(
              1,
              issueParser
                  .getWorkedTimeForIssue(issue)
                  .map(Duration::toHours)
                  .map(Double::valueOf)
                  .orElseGet(this::spentHoursCorrectionFlow));
      String notes = issue.key();

      confirmationFlow(
          issue,
          projectAssignment.project().id(),
          taskAssignment.task().id(),
          spentDate,
          hours,
          notes);

    } catch (RuntimeException e) {
      terminal
          .writer()
          .print(
              String.format(
                  """
                    Could not auto create entry for issue %s - %s
                    Failure reason: %s
                                        """,
                  issue.key(), issue.fields().summary(), e.getMessage()));
    }
  }

  private Optional<Tuple2<HarvestService.ProjectAssignment, HarvestService.TaskAssignment>>
      getTaskAndProjectFromIssue(JiraService.BasicIssue issue) {
    var projectAssignmentsForClient =
        projectAssignments.stream()
            .filter(assignment -> issue.fields().summary().contains(assignment.client().name()))
            .collect(Collectors.toSet());
    if (projectAssignmentsForClient.size() < 1) {
      return Optional.empty();
    }
    var issueIsBillable = issue.fields().labels().contains("HARVEST-Billable");

    return projectAssignmentsForClient.stream()
        .filter(
            assignment ->
                assignment.taskAssignments().stream()
                    .anyMatch(taskAssignment -> taskAssignment.billable() == issueIsBillable))
        .flatMap(
            ass ->
                ass.taskAssignments().stream()
                    .sorted(matchingTaskComparator())
                    .map(e -> Tuple.of(ass, e)))
        .findFirst();
  }

  private void confirmationFlow(
      JiraService.BasicIssue issue,
      Long projectId,
      Long taskId,
      LocalDate spentDate,
      Double hours,
      String notes) {
    String statement =
        String.format(
            """
                            This Jira issue '%s - %s' will be converted to a Harvest time entry.
                            Client: %s
                            Project: %s
                            Task: %s
                            Spent Date: %s
                            Hours: %s
                            Notes: %s
                            """,
            issue.key(),
            issue.fields().summary(),
            this.projectAssignments.stream()
                .filter(pa -> pa.project().id().equals(projectId))
                .findFirst()
                .map(pa -> pa.client().name())
                .orElseThrow(),
            this.projectAssignments.stream()
                .filter(pa -> pa.project().id().equals(projectId))
                .findFirst()
                .map(pa -> pa.project().name())
                .orElseThrow(),
            this.projectAssignments.stream()
                .filter(e -> e.project().id().equals(projectId))
                .flatMap(e -> e.taskAssignments().stream())
                .filter(e -> e.task().id().equals(taskId))
                .findFirst()
                .map(ta -> ta.task().name())
                .orElseThrow(),
            spentDate,
            hours,
            notes);
    terminal.writer().print(statement);

    ComponentFlow flow =
        componentFlowBuilder
            .clone()
            .reset()
            .withSingleItemSelector("isCorrect")
            .selectItems(
                Map.of(
                    "Yes, send to Harvest",
                    "true",
                    "No, I will correct",
                    "false",
                    "No, skip this issue",
                    "skip"))
            .name("Is this correct?")
            .and()
            .build();
    var results = flow.run();

    String isCorrect = results.getContext().get("isCorrect");

    if (isCorrect != null && isCorrect.equals("true")) {
      var created = harvestService.create(projectId, taskId, spentDate, hours, notes);
      terminal
          .writer()
          .print(
              String.format(
                  """
                    Created entry in Harvest
                    %s
                    """,
                  ListHarvestProjects.formatTimeEntry(created)));
    }
    if (isCorrect != null && isCorrect.equals("false")) {
      var projectAndTask = projectAndTaskCorrectionFlow();
      spentDate = spentDateCorrectionFlow();
      hours = spentHoursCorrectionFlow();
      confirmationFlow(
          issue,
          projectAndTask._1().project().id(),
          projectAndTask._2().task().id(),
          spentDate,
          hours,
          notes);
    }
  }

  private Tuple2<HarvestService.ProjectAssignment, HarvestService.TaskAssignment>
      projectAndTaskCorrectionFlow() {
    ComponentFlow correctionFlow =
        componentFlowBuilder
            .clone()
            .reset()
            .withSingleItemSelector("clientId")
            .selectItems(
                this.projectAssignments.stream()
                    .collect(
                        Collectors.toMap(
                            e -> e.client().name(), e -> e.client().id() + "", (a, b) -> a)))
            .name("What Client is it?\n")
            .and()
            .build();
    var correctionResults = correctionFlow.run();
    Long clientId = Long.valueOf(correctionResults.getContext().get("clientId").toString());

    correctionFlow =
        componentFlowBuilder
            .clone()
            .reset()
            .withSingleItemSelector("projectId")
            .selectItems(
                this.projectAssignments.stream()
                    .filter(e -> e.client().id().equals(clientId))
                    .collect(Collectors.toMap(e -> e.project().name(), e -> e.project().id() + "")))
            .name("What Project is it?\n")
            .and()
            .build();
    correctionResults = correctionFlow.run();
    Long projectId = Long.valueOf(correctionResults.getContext().get("projectId").toString());

    correctionFlow =
        componentFlowBuilder
            .clone()
            .reset()
            .withSingleItemSelector("taskId")
            .selectItems(
                this.projectAssignments.stream()
                    .filter(e -> e.project().id().equals(projectId))
                    .flatMap(e -> e.taskAssignments().stream())
                    .collect(Collectors.toMap(e -> e.task().name(), e -> e.task().id() + "")))
            .name("What Task is it?\n")
            .and()
            .build();
    correctionResults = correctionFlow.run();
    Long taskId = Long.valueOf(correctionResults.getContext().get("taskId").toString());

    return Tuple.of(
        this.projectAssignments.stream()
            .filter(e -> e.project().id().equals(projectId))
            .findFirst()
            .orElseThrow(),
        this.projectAssignments.stream()
            .filter(e -> e.project().id().equals(projectId))
            .flatMap(e -> e.taskAssignments().stream())
            .filter(e -> e.task().id().equals(taskId))
            .findFirst()
            .orElseThrow());
  }

  private LocalDate spentDateCorrectionFlow() {
    try {
      ComponentFlow correctionFlow =
          componentFlowBuilder
              .clone()
              .reset()
              .withStringInput("spentDate")
              .name("What day should this be logged?\n[dd/mm/yyyy]")
              .and()
              .build();
      var correctionResults = correctionFlow.run();
      return LocalDate.parse(
          correctionResults.getContext().get("spentDate").toString(),
          DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    } catch (DateTimeParseException pe) {
      return spentDateCorrectionFlow();
    }
  }

  private Double spentHoursCorrectionFlow() {
    ComponentFlow correctionFlow =
        componentFlowBuilder
            .clone()
            .reset()
            .withStringInput("spentHours")
            .name("How many hours did you work on this issue?\n")
            .and()
            .build();
    var correctionResults = correctionFlow.run();
    return Double.valueOf(correctionResults.getContext().get("spentHours").toString());
  }

  private static Comparator<HarvestService.TaskAssignment> matchingTaskComparator() {
    return (a, b) -> {
      if (isRelevantForDevs(a) && isRelevantForDevs(b)) {
        return a.task().name().compareTo(b.task().name());
      }
      if (isRelevantForDevs(a)) {
        return -1;
      }
      if (isRelevantForDevs(b)) {
        return 1;
      }
      return a.task().name().compareTo(b.task().name());
    };
  }

  private static boolean isRelevantForDevs(HarvestService.TaskAssignment taskAssignment) {
    return taskAssignment.task().name().toLowerCase().contains("billable");
  }
}
