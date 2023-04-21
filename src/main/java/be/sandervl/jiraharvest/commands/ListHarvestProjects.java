package be.sandervl.jiraharvest.commands;

import be.sandervl.jiraharvest.services.HarvestService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ShellComponent
public class ListHarvestProjects {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private final HarvestService harvestService;

    public ListHarvestProjects(HarvestService harvestService) {
        this.harvestService = harvestService;
    }

    @ShellMethod(value = "List Harvest projects", key = "lshp")
    public String listHarvestProjects() {
        return StreamSupport.stream(harvestService.getProjectAssignments().spliterator(), false)
                .flatMap(projectAssignment -> projectAssignment.taskAssignments().stream()
                        .map(taskAssignment -> formatHarvestClient(projectAssignment, taskAssignment))).collect(Collectors.joining("\n"));
    }

    @ShellMethod(value = "List Harvest time entries", key = "lsht")
    public String listHarvestTimeEntries() {
        return StreamSupport.stream(harvestService.getTimeEntries().spliterator(), false)
                .map(ListHarvestProjects::formatTimeEntry)
                .collect(Collectors.joining("\n"));
    }

    private static String formatHarvestClient(HarvestService.ProjectAssignment projectAssignment, HarvestService.TaskAssignment taskAssignment) {
        return
                String.format("""
                        Client: %s
                        Project: %s
                        Task: %s
                        """, projectAssignment.client().name(), projectAssignment.project().name(), taskAssignment.task().name());
    }

    public static String formatTimeEntry(HarvestService.TimeEntry entry) {
        return
                String.format("""
                                Project: %s
                                Task: %s
                                Notes: %s
                                Task Assignment: %s
                                Hours: %s
                                Created At: %s
                                
                                """,
                        entry.project().name(),
                        entry.task().name(),
                        Optional.ofNullable(entry.notes()).orElse(""),
                        Optional.ofNullable(entry.taskAssignment()).map(ta -> ta.task().name()).orElse("N/A"),
                        entry.hours(),
                        entry.createdAt().format(DateTimeFormatter.ISO_DATE)
                );
    }
}
