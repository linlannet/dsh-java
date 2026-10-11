package net.linlan.dsh.infrastructure.digitalhuman;

import net.linlan.dsh.infrastructure.dao.ICollaborationDao;
import net.linlan.dsh.infrastructure.dao.IDigitalHumanDao;
import net.linlan.dsh.infrastructure.dao.IDigitalHumanEndpointDao;
import net.linlan.dsh.infrastructure.dao.po.CollaborationPO;
import net.linlan.dsh.infrastructure.dao.po.DigitalHumanEndpointPO;
import net.linlan.dsh.infrastructure.dao.po.DigitalHumanPO;
import net.linlan.dsh.infrastructure.digitalhuman.PlannerService;
import net.linlan.dsh.infrastructure.digitalhuman.RemoteAgentGateway;
import net.linlan.dsh.infrastructure.digitalhuman.RemoteEndpointCredentialRegistry;
import net.linlan.dsh.api.port.ICollaborationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CollaborationService implements ICollaborationService {
    private static final Logger log = LoggerFactory.getLogger(CollaborationService.class);
    private static final int EVENT_PAGE_SIZE = 500;
    private static final Pattern LOCAL_FILE_PATTERN = Pattern.compile("(?:/[^\\s<>\\\"'\uff0c\u3002\uff1b\uff09)]+|[A-Za-z]:\\\\[^\\s<>\\\"']+)\\.(?:docx|xlsx|xls|csv|md|markdown|html?|drawio)", 2);
    private final ICollaborationDao collaborationDao;
    private final IDigitalHumanDao humanDao;
    private final IDigitalHumanEndpointDao endpointDao;
    private final PlannerService plannerService;
    private final RemoteEndpointCredentialRegistry remoteCredentials;
    private final RemoteAgentGateway gateway;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> roomEmitters = new ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>();
    private final Map<String, SandboxScope> taskSandboxScopes = new ConcurrentHashMap<String, SandboxScope>();
    private final Map<String, FutureTask<Void>> activeRuns = new ConcurrentHashMap<String, FutureTask<Void>>();
    private final Map<String, Integer> clarifyBudget = new ConcurrentHashMap<String, Integer>();
    private final Map<String, Integer> recoveryBudget = new ConcurrentHashMap<String, Integer>();
    private static final Pattern ASK_PATTERN = Pattern.compile("\\[\\[ASK:@([^\\]]+)\\]\\s*([^\\]]*)]");
    private static final long SSE_HEARTBEAT_INTERVAL_MS = 15000L;

    public CollaborationService(ICollaborationDao collaborationDao, IDigitalHumanDao humanDao, IDigitalHumanEndpointDao endpointDao, PlannerService plannerService, RemoteEndpointCredentialRegistry remoteCredentials) {
        this.collaborationDao = collaborationDao;
        this.humanDao = humanDao;
        this.endpointDao = endpointDao;
        this.plannerService = plannerService;
        this.remoteCredentials = remoteCredentials;
        this.gateway = new RemoteAgentGateway();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "collab-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "collab-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Map<String, Object> createRoom(String workspaceId, String title, String objective, String orchestrationMode) {
        String id = "room_" + this.shortId();
        this.collaborationDao.insertRoom(new CollaborationPO.RoomPO(id, workspaceId == null || workspaceId.isBlank() ? "default" : workspaceId, this.requireText(title, "\u623f\u95f4\u6807\u9898\u4e0d\u80fd\u4e3a\u7a7a"), objective, orchestrationMode == null || orchestrationMode.isBlank() ? "MANUAL" : orchestrationMode, "idle", Instant.now()));
        return this.roomDetail(id);
    }

    public Map<String, Object> roomDetail(String roomId) {
        CollaborationPO.RoomPO room = this.ensureRoom(roomId);
        LinkedHashMap<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", room.id());
        view.put("workspaceId", room.workspaceId());
        view.put("title", room.title());
        view.put("objective", room.objective());
        view.put("orchestrationMode", room.orchestrationMode());
        view.put("runState", room.runState());
        view.put("createdAt", room.createdAt() == null ? null : room.createdAt().toString());
        ArrayList participants = new ArrayList();
        for (CollaborationPO.ParticipantPO participantPO : this.collaborationDao.listParticipants(roomId)) {
            LinkedHashMap<String, String> p = new LinkedHashMap<String, String>();
            p.put("participantId", participantPO.id());
            p.put("digitalHumanId", participantPO.digitalHumanId());
            p.put("roomRole", participantPO.roomRole());
            p.put("presence", participantPO.presence());
            p.put("activeTaskLabel", participantPO.activeTaskLabel());
            p.put("joinedAt", participantPO.joinedAt() == null ? null : participantPO.joinedAt().toString());
            this.humanDao.findById(participantPO.digitalHumanId()).ifPresent(human -> {
                p.put("displayName", human.displayName());
                p.put("avatarRef", human.avatarRef());
                p.put("themeColor", human.themeColor());
            });
            participants.add(p);
        }
        view.put("participants", participants);
        ArrayList<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
        for (CollaborationPO.TaskPO task : this.collaborationDao.listTasks(roomId)) {
            tasks.add(this.taskView(task));
        }
        view.put("tasks", tasks);
        ArrayList<Map<String, Object>> arrayList = new ArrayList<Map<String, Object>>();
        for (CollaborationPO.ArtifactPO artifact : this.collaborationDao.listArtifacts(roomId)) {
            arrayList.add(this.artifactView(artifact));
        }
        view.put("artifacts", arrayList);
        return view;
    }

    public Map<String, Object> joinRoom(String roomId, String digitalHumanId, String roomRole) {
        this.ensureRoom(roomId);
        DigitalHumanPO human = this.humanDao.findById(digitalHumanId).orElseThrow(() -> new IllegalArgumentException("\u6570\u5b57\u4eba\u4e0d\u5b58\u5728\uff1a" + digitalHumanId));
        if (this.collaborationDao.findParticipant(roomId, digitalHumanId).isEmpty()) {
            this.collaborationDao.insertParticipant(new CollaborationPO.ParticipantPO("pt_" + this.shortId(), roomId, digitalHumanId, roomRole == null || roomRole.isBlank() ? "member" : roomRole, "idle", null, Instant.now()));
            this.appendEvent(roomId, null, null, digitalHumanId, "PARTICIPANT_JOINED", Map.of("displayName", human.displayName()));
        }
        return this.roomDetail(roomId);
    }

    public Map<String, Object> leaveRoom(String roomId, String digitalHumanId) {
        this.ensureRoom(roomId);
        this.collaborationDao.removeParticipant(roomId, digitalHumanId);
        this.humanDao.findById(digitalHumanId).ifPresent(human -> this.appendEvent(roomId, null, null, digitalHumanId, "PARTICIPANT_LEFT", Map.of("displayName", human.displayName())));
        return this.roomDetail(roomId);
    }

    public Map<String, Object> postMessage(String roomId, String content, List<String> mentions, String token, Map<String, String> digitalHumanTokens, String channelCode, String approvalMode, String cwd, List<String> sandboxRoots) {
        boolean explicit;
        CollaborationPO.RoomPO room = this.ensureRoom(roomId);
        String objective = this.requireText(content, "\u6d88\u606f\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
        if (room.objective() == null || room.objective().isBlank()) {
            this.collaborationDao.fillObjective(roomId, objective.length() > 60 ? objective.substring(0, 60) : objective);
        }
        List<CollaborationPO.ParticipantPO> participants = this.collaborationDao.listParticipants(roomId);
        this.appendEvent(roomId, null, null, null, "MESSAGE_CREATED", Map.of("role", "user", "content", objective));
        this.collaborationDao.updateRoomState(roomId, "running");
        SandboxScope scope = new SandboxScope(cwd, sandboxRoots == null ? List.of() : sandboxRoots);
        boolean bl = explicit = mentions != null && !mentions.isEmpty();
        if (explicit || participants.size() <= 1) {
            List<CollaborationPO.ParticipantPO> targets = this.resolveTargets(participants, mentions);
            ArrayList<Map<String, Object>> dispatched = new ArrayList<Map<String, Object>>();
            for (CollaborationPO.ParticipantPO target : targets) {
                CollaborationPO.TaskPO task = this.createTask(roomId, objective, target.digitalHumanId(), "user", null);
                this.taskSandboxScopes.put(task.id(), scope);
                dispatched.add(this.taskView(task));
            }
            this.schedule(roomId, token, digitalHumanTokens, channelCode, approvalMode);
            return Map.of("accepted", true, "mode", "MANUAL", "tasks", dispatched);
        }
        return this.autoPlan(roomId, objective, participants, token, digitalHumanTokens, channelCode, approvalMode, scope);
    }

    private List<CollaborationPO.ParticipantPO> resolveTargets(List<CollaborationPO.ParticipantPO> participants, List<String> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            if (participants.isEmpty()) {
                throw new IllegalArgumentException("\u623f\u95f4\u8fd8\u6ca1\u6709\u6570\u5b57\u4eba\uff0c\u8bf7\u5148\u52a0\u5165");
            }
            return List.of(participants.get(0));
        }
        ArrayList<CollaborationPO.ParticipantPO> targets = new ArrayList<CollaborationPO.ParticipantPO>();
        for (String humanId : mentions) {
            participants.stream().filter(p -> p.digitalHumanId().equals(humanId)).findFirst().ifPresent(targets::add);
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("@ \u7684\u6570\u5b57\u4eba\u4e0d\u5728\u5f53\u524d\u623f\u95f4");
        }
        return targets;
    }

    private Map<String, Object> autoPlan(String roomId, String objective, List<CollaborationPO.ParticipantPO> participants, String token, Map<String, String> digitalHumanTokens, String channelCode, String approvalMode, SandboxScope scope) {
        String plannerKind;
        ArrayList<PlannerService.CapabilityCard> cards = new ArrayList<PlannerService.CapabilityCard>();
        for (CollaborationPO.ParticipantPO participant : participants) {
            DigitalHumanPO human = this.humanDao.findById(participant.digitalHumanId()).orElse(null);
            cards.add(new PlannerService.CapabilityCard(participant.digitalHumanId(), human == null ? participant.digitalHumanId() : human.displayName(), human == null ? "\u6267\u884c\u4efb\u52a1" : human.purpose(), human == null || human.roleTags() == null || human.roleTags().isBlank() ? List.of() : List.of(human.roleTags().split(","))));
        }
        List<PlannerService.PlanStep> llmSteps = this.plannerService.plan(objective, cards);
        ArrayList<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
        if (llmSteps != null && !llmSteps.isEmpty()) {
            plannerKind = "llm";
            ArrayList<String> taskIds = new ArrayList<String>();
            for (int i = 0; i < llmSteps.size(); ++i) {
                PlannerService.PlanStep step = llmSteps.get(i);
                String dependsOn = step.dependsOn().isEmpty() ? null : (String)step.dependsOn().stream().map(taskIds::get).reduce((a, b) -> a + "," + b).orElse(null);
                CollaborationPO.TaskPO task = this.createTask(roomId, step.title(), step.instruction(), step.assignTo(), "orchestrator", dependsOn);
                this.taskSandboxScopes.put(task.id(), scope);
                taskIds.add(task.id());
                steps.add(this.stepView(i + 1, task.id(), step.title(), step.assignTo(), cards, step.dependsOn()));
            }
        } else {
            plannerKind = "rule";
            int seq = 1;
            for (PlannerService.CapabilityCard card : cards) {
                String title = "\u6b65\u9aa4 " + seq + "\uff1a" + this.abbreviate(card.purpose(), 24);
                String instruction = "\u4f5c\u4e3a\u300c" + card.displayName() + "\u300d\uff08\u804c\u8d23\uff1a" + card.purpose() + "\uff09\uff0c\u53c2\u4e0e\u5b8c\u6210\u623f\u95f4\u76ee\u6807\uff1a\u300c" + objective + "\u300d\u3002\u8bf7\u4e0e\u5176\u4ed6\u6570\u5b57\u4eba\u5e76\u884c\u5b8c\u6210\u4f60\u804c\u8d23\u8303\u56f4\u5185\u7684\u90e8\u5206\uff0c\u5e76\u660e\u786e\u4ea4\u4ed8\u7269\u3002";
                CollaborationPO.TaskPO task = this.createTask(roomId, title, instruction, card.digitalHumanId(), "orchestrator", null);
                this.taskSandboxScopes.put(task.id(), scope);
                steps.add(this.stepView(seq, task.id(), card.purpose(), card.digitalHumanId(), cards, List.of()));
                ++seq;
            }
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("objective", objective);
        payload.put("planner", plannerKind);
        payload.put("steps", steps);
        this.appendEvent(roomId, null, null, null, "PLAN_CREATED", payload);
        this.schedule(roomId, token, digitalHumanTokens, channelCode, approvalMode);
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("accepted", true);
        result.put("mode", "AUTO_PLAN");
        result.put("planner", plannerKind);
        result.put("plan", steps);
        return result;
    }

    private Map<String, Object> stepView(int seq, String taskId, String title, String assignTo, List<PlannerService.CapabilityCard> cards, List<Integer> dependsOn) {
        String assigneeName = cards.stream().filter(c -> c.digitalHumanId().equals(assignTo)).map(PlannerService.CapabilityCard::displayName).findFirst().orElse(assignTo);
        LinkedHashMap<String, Object> step = new LinkedHashMap<String, Object>();
        step.put("seq", seq);
        step.put("taskId", taskId);
        step.put("title", this.abbreviate(title, 30));
        step.put("assignTo", assignTo);
        step.put("assigneeName", assigneeName);
        step.put("dependsOn", dependsOn);
        return step;
    }

    private synchronized void schedule(String roomId, String token, Map<String, String> digitalHumanTokens, String channelCode, String approvalMode) {
        this.reapOrphanTasks(roomId);
        this.cascadeBlockedTasks(roomId);
        List<CollaborationPO.TaskPO> tasks = this.collaborationDao.listTasks(roomId);
        LinkedHashMap<String, String> stateById = new LinkedHashMap<String, String>();
        LinkedHashMap<String, Integer> runningByHuman = new LinkedHashMap<String, Integer>();
        for (CollaborationPO.TaskPO taskPO : tasks) {
            stateById.put(taskPO.id(), taskPO.state());
            if (!"RUNNING".equals(taskPO.state()) || taskPO.assignedTo() == null) continue;
            runningByHuman.merge(taskPO.assignedTo(), 1, Integer::sum);
        }
        boolean dispatchedAny = false;
        for (CollaborationPO.TaskPO task : tasks) {
            if (!"READY".equals(task.state()) || !this.depsSatisfied(task.dependsOn(), stateById)) continue;
            int limit = this.humanDao.findById(task.assignedTo()).map(DigitalHumanPO::concurrencyLimit).orElse(1);
            int running = runningByHuman.getOrDefault(task.assignedTo(), 0);
            if (running >= limit) continue;
            this.collaborationDao.updateTaskState(task.id(), "ASSIGNED", null, null, Instant.now());
            runningByHuman.merge(task.assignedTo(), 1, Integer::sum);
            dispatchedAny = true;
            String taskId = task.id();
            String humanId = task.assignedTo();
            String instruction = this.buildInstructionWithDeps(task, stateById);
            this.executor.submit(() -> this.runTask(roomId, taskId, humanId, instruction, token, digitalHumanTokens, channelCode, approvalMode));
        }
        boolean bl = tasks.stream().allMatch(t -> List.of("COMPLETED", "FAILED", "CANCELED", "TIMEOUT").contains(t.state()));
        if (!tasks.isEmpty() && bl && !dispatchedAny) {
            this.collaborationDao.updateRoomState(roomId, "done");
            for (CollaborationPO.TaskPO task : tasks) {
                this.clarifyBudget.remove(task.id());
                this.recoveryBudget.remove(task.id());
            }
        }
    }

    private boolean depsSatisfied(String dependsOn, Map<String, String> stateById) {
        if (dependsOn == null || dependsOn.isBlank()) {
            return true;
        }
        for (String dep : dependsOn.split(",")) {
            if ("COMPLETED".equals(stateById.get(dep.trim()))) continue;
            return false;
        }
        return true;
    }

    private void reapOrphanTasks(String roomId) {
        for (CollaborationPO.TaskPO task : this.collaborationDao.listTasks(roomId)) {
            Instant updatedAt;
            if (!List.of("RUNNING", "ASSIGNED").contains(task.state()) || this.activeRuns.containsKey(task.id()) || (updatedAt = task.updatedAt()) == null || Duration.between(updatedAt, Instant.now()).toMinutes() < 1L) continue;
            this.collaborationDao.updateTaskState(task.id(), "FAILED", null, "\u6267\u884c\u4e2d\u65ad\uff1a\u672a\u627e\u5230\u6d3b\u8dc3\u8fd0\u884c\uff08\u53ef\u80fd\u670d\u52a1\u91cd\u542f\uff09", Instant.now());
            this.collaborationDao.updatePresence(roomId, task.assignedTo(), "error", null);
            this.appendEvent(roomId, task.id(), null, task.assignedTo(), "ERROR", Map.of("code", "ORPHAN_TASK", "message", "\u6267\u884c\u4e2d\u65ad\uff1a\u672a\u627e\u5230\u6d3b\u8dc3\u8fd0\u884c", "recoverable", true));
            this.appendEvent(roomId, task.id(), null, task.assignedTo(), "TASK_STATE_CHANGED", Map.of("state", "FAILED"));
        }
    }

    private String buildInstructionWithDeps(CollaborationPO.TaskPO task, Map<String, String> stateById) {
        if (task.dependsOn() == null || task.dependsOn().isBlank()) {
            return task.instruction();
        }
        StringBuilder upstream = new StringBuilder();
        for (String dep : task.dependsOn().split(",")) {
            this.collaborationDao.findTask(dep.trim()).ifPresent(depTask -> this.collaborationDao.listArtifacts(task.roomId()).stream().filter(a -> depTask.id().equals(a.taskId())).forEach(a -> upstream.append("\n\n--- \u4e0a\u6e38\u4ea4\u4ed8\u7269\uff1a").append(a.title()).append(" ---\n").append(this.abbreviate(a.content(), 2000))));
        }
        return upstream.length() == 0 ? task.instruction() : task.instruction() + "\n" + String.valueOf(upstream);
    }

    private CollaborationPO.TaskPO createTask(String roomId, String titleOrObjective, String assignedTo, String requestedBy, String dependsOn) {
        return this.createTask(roomId, (String)(titleOrObjective.length() > 40 ? titleOrObjective.substring(0, 40) + "\u2026" : titleOrObjective), titleOrObjective, assignedTo, requestedBy, dependsOn);
    }

    private CollaborationPO.TaskPO createTask(String roomId, String title, String instruction, String assignedTo, String requestedBy, String dependsOn) {
        CollaborationPO.TaskPO task = new CollaborationPO.TaskPO("task_" + this.shortId(), roomId, null, title, instruction, assignedTo, requestedBy, "READY", dependsOn, null, null, null, "tr_" + this.shortId(), null, Instant.now(), Instant.now());
        this.collaborationDao.insertTask(task);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("title", title);
        payload.put("assignedTo", assignedTo);
        if (dependsOn != null) {
            payload.put("dependsOn", List.of(dependsOn.split(",")));
        }
        this.appendEvent(roomId, task.id(), null, assignedTo, "TASK_CREATED", payload);
        return task;
    }

    public Map<String, Object> cancelTask(String roomId, String taskId) {
        CollaborationPO.TaskPO task = this.collaborationDao.findTask(taskId).orElseThrow(() -> new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5b58\u5728\uff1a" + taskId));
        if (!roomId.equals(task.roomId())) {
            throw new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5c5e\u4e8e\u5f53\u524d\u623f\u95f4");
        }
        if (List.of("COMPLETED", "FAILED", "CANCELED", "TIMEOUT").contains(task.state())) {
            return this.taskView(task);
        }
        FutureTask<Void> future = this.activeRuns.get(taskId);
        if (future != null) {
            future.cancel(true);
        }
        this.gateway.cancel(taskId);
        this.collaborationDao.updateTaskState(taskId, "CANCELED", task.remoteSessionId(), null, Instant.now());
        this.collaborationDao.updatePresence(roomId, task.assignedTo(), "idle", null);
        this.clarifyBudget.remove(taskId);
        this.recoveryBudget.remove(taskId);
        this.appendEvent(roomId, taskId, null, task.assignedTo(), "TASK_STATE_CHANGED", Map.of("state", "CANCELED"));
        this.cascadeBlockedTasks(roomId);
        this.settleRoomIfTerminal(roomId);
        return this.taskView(this.collaborationDao.findTask(taskId).orElse(task));
    }

    private void cascadeBlockedTasks(String roomId) {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<CollaborationPO.TaskPO> tasks = this.collaborationDao.listTasks(roomId);
            LinkedHashMap<String, String> stateById = new LinkedHashMap<String, String>();
            for (CollaborationPO.TaskPO t : tasks) {
                stateById.put(t.id(), t.state());
            }
            block2: for (CollaborationPO.TaskPO t : tasks) {
                if (!"READY".equals(t.state()) && !"ASSIGNED".equals(t.state()) || t.dependsOn() == null || t.dependsOn().isBlank()) continue;
                for (String dep : t.dependsOn().split(",")) {
                    String depState = (String)stateById.get(dep.trim());
                    if (depState == null || !List.of("FAILED", "CANCELED", "TIMEOUT").contains(depState)) continue;
                    this.collaborationDao.updateTaskState(t.id(), "FAILED", null, "\u4e0a\u6e38\u4efb\u52a1" + ("CANCELED".equals(depState) ? "\u5df2\u53d6\u6d88" : "\u5931\u8d25") + "\uff0c\u7ea7\u8054\u7ec8\u6b62", Instant.now());
                    this.collaborationDao.updatePresence(roomId, t.assignedTo(), "idle", null);
                    this.appendEvent(roomId, t.id(), null, t.assignedTo(), "ERROR", Map.of("code", "UPSTREAM_FAILED", "message", "\u4e0a\u6e38\u4efb\u52a1\u672a\u6210\u529f\uff0c\u672c\u4efb\u52a1\u81ea\u52a8\u7ec8\u6b62", "recoverable", true));
                    this.appendEvent(roomId, t.id(), null, t.assignedTo(), "TASK_STATE_CHANGED", Map.of("state", "FAILED"));
                    stateById.put(t.id(), "FAILED");
                    changed = true;
                    continue block2;
                }
            }
        }
    }

    private void settleRoomIfTerminal(String roomId) {
        boolean allTerminal;
        List<CollaborationPO.TaskPO> tasks = this.collaborationDao.listTasks(roomId);
        boolean bl = allTerminal = !tasks.isEmpty() && tasks.stream().allMatch(t -> List.of("COMPLETED", "FAILED", "CANCELED", "TIMEOUT").contains(t.state()));
        if (allTerminal) {
            this.collaborationDao.updateRoomState(roomId, "done");
        }
    }

    public Map<String, Object> resumeTask(String roomId, String taskId, String token, Map<String, String> digitalHumanTokens, String channelCode, String approvalMode) {
        CollaborationPO.TaskPO task = this.collaborationDao.findTask(taskId).orElseThrow(() -> new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5b58\u5728\uff1a" + taskId));
        if (!"WAITING_APPROVAL".equals(task.state())) {
            throw new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5728\u7b49\u5f85\u5ba1\u6279\u72b6\u6001\uff08\u5f53\u524d\uff1a" + task.state() + "\uff09");
        }
        this.collaborationDao.updateTaskState(taskId, "READY", null, null, Instant.now());
        this.appendEvent(roomId, taskId, null, task.assignedTo(), "APPROVAL_RESOLVED", Map.of("taskId", taskId, "verdict", "ALLOW"));
        this.schedule(roomId, token, digitalHumanTokens, channelCode, approvalMode);
        return this.taskView(this.collaborationDao.findTask(taskId).orElse(task));
    }

    public Map<String, Object> retryTask(String roomId, String taskId, String token, Map<String, String> digitalHumanTokens, String channelCode, String approvalMode) {
        CollaborationPO.TaskPO task = this.collaborationDao.findTask(taskId).orElseThrow(() -> new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5b58\u5728\uff1a" + taskId));
        if (!roomId.equals(task.roomId())) {
            throw new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5c5e\u4e8e\u5f53\u524d\u623f\u95f4");
        }
        this.collaborationDao.updateTaskState(taskId, "READY", null, null, Instant.now());
        this.collaborationDao.updatePresence(roomId, task.assignedTo(), "idle", null);
        this.recoveryBudget.remove(taskId);
        this.appendEvent(roomId, taskId, null, task.assignedTo(), "TASK_RETRIED", Map.of("taskId", taskId, "assignedTo", task.assignedTo()));
        this.appendEvent(roomId, taskId, null, task.assignedTo(), "TASK_STATE_CHANGED", Map.of("state", "READY"));
        this.collaborationDao.updateRoomState(roomId, "running");
        this.schedule(roomId, token, digitalHumanTokens, channelCode, approvalMode);
        return this.taskView(this.collaborationDao.findTask(taskId).orElse(task));
    }

    public Map<String, Object> reassignTask(String roomId, String taskId, String targetDigitalHumanId, String token, Map<String, String> digitalHumanTokens, String channelCode, String approvalMode) {
        CollaborationPO.TaskPO task = this.collaborationDao.findTask(taskId).orElseThrow(() -> new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5b58\u5728\uff1a" + taskId));
        if (!roomId.equals(task.roomId())) {
            throw new IllegalArgumentException("\u4efb\u52a1\u4e0d\u5c5e\u4e8e\u5f53\u524d\u623f\u95f4");
        }
        if (this.collaborationDao.findParticipant(roomId, targetDigitalHumanId).isEmpty()) {
            throw new IllegalArgumentException("\u76ee\u6807\u6570\u5b57\u4eba\u4e0d\u5728\u5f53\u524d\u623f\u95f4");
        }
        this.collaborationDao.updateTaskAssignment(taskId, targetDigitalHumanId, "READY", Instant.now());
        if (task.assignedTo() != null && !task.assignedTo().equals(targetDigitalHumanId)) {
            this.collaborationDao.updatePresence(roomId, task.assignedTo(), "idle", null);
        }
        this.collaborationDao.updatePresence(roomId, targetDigitalHumanId, "idle", null);
        this.appendEvent(roomId, taskId, null, targetDigitalHumanId, "TASK_REASSIGNED", Map.of("taskId", taskId, "from", task.assignedTo(), "to", targetDigitalHumanId));
        this.appendEvent(roomId, taskId, null, targetDigitalHumanId, "TASK_STATE_CHANGED", Map.of("state", "READY", "assignedTo", targetDigitalHumanId));
        this.collaborationDao.updateRoomState(roomId, "running");
        this.schedule(roomId, token, digitalHumanTokens, channelCode, approvalMode);
        return this.taskView(this.collaborationDao.findTask(taskId).orElse(task));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void runTask(String roomId, String taskId, String digitalHumanId, String instruction, String token, Map<String, String> digitalHumanTokens, String channelCode, String approvalMode) {
        String currentState;
        String remoteToken;
        String remoteInstruction;
        DigitalHumanPO human = this.humanDao.findById(digitalHumanId).orElse(null);
        DigitalHumanEndpointPO endpoint = this.endpointDao.findByHumanId(digitalHumanId).orElse(null);
        String displayName = human == null ? digitalHumanId : human.displayName();
        this.collaborationDao.updateTaskState(taskId, "RUNNING", null, null, Instant.now());
        this.collaborationDao.updatePresence(roomId, digitalHumanId, "working", this.taskTitle(taskId));
        this.appendEvent(roomId, taskId, null, digitalHumanId, "TASK_STATE_CHANGED", Map.of("state", "RUNNING"));
        this.gateway.clearCancelled(taskId);
        StringBuilder finalText = new StringBuilder();
        GatewayEventSink sink = new GatewayEventSink(roomId, taskId, digitalHumanId, finalText);
        SandboxScope scope = this.taskSandboxScopes.getOrDefault(taskId, SandboxScope.EMPTY);
        try {
            String targetId;
            String effectiveApprovalMode = this.effectiveApprovalMode(human, approvalMode);
            if (endpoint == null || "local-dsh".equals(endpoint.endpointType()) || endpoint.baseUrl() == null) {
                String roleInstruction = "[\u89d2\u8272\u8bbe\u5b9a] \u4f60\u662f\u300c" + displayName + "\u300d\uff0c\u804c\u8d23\uff1a" + (human == null ? "\u901a\u7528\u667a\u80fd\u4f53" : human.purpose()) + "\u3002\u8bf7\u4ee5\u8be5\u8eab\u4efd\u5b8c\u6210\u4efb\u52a1\u3002\n[\u534f\u4f5c\u89c4\u5219] \u4f60\u5728\u591a\u6570\u5b57\u4eba\u534f\u4f5c\u623f\u95f4\u4e2d\u5de5\u4f5c\u3002\u4e0d\u8981\u8c03\u7528 ask_user_question \u5411\u7528\u6237\u63d0\u95ee\u3002\u9047\u5230\u4fe1\u606f\u4e0d\u8db3\u65f6\uff0c\u9ed8\u8ba4\u57fa\u4e8e\u5df2\u6709\u4e0a\u4e0b\u6587\u548c\u4e0a\u6e38\u4ea4\u4ed8\u7269\u81ea\u4e3b\u505a\u51fa\u6700\u5408\u7406\u7684\u5047\u8bbe\u5e76\u7ee7\u7eed\u63a8\u8fdb\uff0c\u5728\u7ed3\u679c\u4e2d\u6ce8\u660e\u5047\u8bbe\u5373\u53ef\u3002\u4ec5\u5f53\u7f3a\u5931\u4fe1\u606f\u662f\u963b\u585e\u6027\u7684\uff08\u62ff\u4e0d\u5230\u5c31\u65e0\u6cd5\u4ea7\u51fa\u6709\u6548\u4ea4\u4ed8\u7269\uff09\u4e14\u623f\u95f4\u5185\u5176\u4ed6\u6210\u5458\u53ef\u80fd\u77e5\u9053\u7b54\u6848\u65f6\uff0c\u624d\u53ef\u5728\u56de\u590d\u7684\u6700\u540e\u5355\u72ec\u4e00\u884c\u8f93\u51fa\uff1a[[ASK:@\u6210\u5458\u540d] \u4f60\u7684\u95ee\u9898]\uff08\u6574\u4e2a\u4efb\u52a1\u6700\u591a 1 \u6b21\uff0c\u95ee\u9898\u8981\u5177\u4f53\u3001\u5bf9\u65b9\u53ef\u4e00\u53e5\u8bdd\u56de\u7b54\uff09\uff0c\u63d0\u95ee\u540e\u672c\u4efb\u52a1\u4f1a\u7b49\u5f85\u5bf9\u65b9\u7b54\u590d\u5e76\u81ea\u52a8\u7eed\u8dd1\u3002\u9664\u6b64\u4e4b\u5916\u8bf7\u6b63\u5e38\u5b8c\u6210\u4efb\u52a1\u3002\n\n" + instruction;
                this.runWithTimeout(taskId, () -> this.gateway.runLocal(taskId, roleInstruction, channelCode, effectiveApprovalMode, sink, scope.cwd(), scope.sandboxRoots()));
            } else if ("a2a".equals(endpoint.endpointType())) {
                remoteToken = this.tokenForHuman(token, digitalHumanTokens, digitalHumanId);
                remoteInstruction = this.remoteInstruction(human, displayName, instruction);
                this.runWithTimeout(taskId, () -> this.gateway.runA2a(taskId, endpoint.baseUrl(), remoteToken, remoteInstruction, sink));
            } else {
                remoteToken = this.tokenForHuman(token, digitalHumanTokens, digitalHumanId);
                remoteInstruction = this.remoteInstruction(human, displayName, instruction);
                this.remoteCredentials.remember(digitalHumanId, remoteToken);
                this.runWithTimeout(taskId, () -> this.gateway.runRemote(taskId, endpoint.baseUrl(), remoteToken, remoteInstruction, channelCode, effectiveApprovalMode, sink));
            }
            if (sink.approvalPending) {
                this.collaborationDao.updateTaskState(taskId, "WAITING_APPROVAL", null, null, Instant.now());
                this.collaborationDao.updatePresence(roomId, digitalHumanId, "waiting_approval", this.taskTitle(taskId));
                this.appendEvent(roomId, taskId, null, digitalHumanId, "APPROVAL_REQUIRED", Map.of("digitalHumanId", digitalHumanId, "displayName", displayName, "taskId", taskId, "summary", sink.approvalSummary, "riskLevel", "write"));
                return;
            }
            currentState = this.collaborationDao.findTask(taskId).map(CollaborationPO.TaskPO::state).orElse("");
            if ("CANCELED".equals(currentState)) {
                this.collaborationDao.updatePresence(roomId, digitalHumanId, "idle", null);
                return;
            }
            String answer = finalText.toString();
            AskRequest ask = this.parseAskRequest(answer);
            if (ask != null && this.clarifyBudget.merge(taskId, 1, Integer::sum) <= 2 && (targetId = this.resolveMemberByName(roomId, ask.targetName())) != null && !targetId.equals(digitalHumanId)) {
                String reply;
                DigitalHumanPO target = this.humanDao.findById(targetId).orElse(null);
                String targetName = target == null ? targetId : target.displayName();
                String question = this.abbreviate(ask.question(), 500);
                try {
                    reply = this.askPeer(roomId, taskId, digitalHumanId, displayName, targetId, targetName, question, token, digitalHumanTokens, channelCode);
                }
                catch (Exception qaError) {
                    log.warn("Collab QA turn {} -> {} failed: {}", new Object[]{taskId, targetId, qaError.getMessage()});
                    this.appendEvent(roomId, taskId, null, targetId, "ERROR", Map.of("code", "QA_FAILED", "message", "\u7b54\u590d\u5931\u8d25\uff1a" + (qaError.getMessage() == null ? "\u672a\u77e5\u9519\u8bef" : qaError.getMessage()), "recoverable", true));
                    reply = null;
                }
                String followUp = reply == null || reply.isBlank() ? "\n\n[\u7cfb\u7edf\u63d0\u793a] \u6210\u5458\u300c" + targetName + "\u300d\u672a\u80fd\u7b54\u590d\u4f60\u7684\u95ee\u9898\uff0c\u8bf7\u57fa\u4e8e\u5408\u7406\u5047\u8bbe\u7ee7\u7eed\u5b8c\u6210\u4efb\u52a1\u3002" : "\n\n[\u6210\u5458\u300c" + targetName + "\u300d\u7684\u7b54\u590d]\n" + this.abbreviate(reply, 1500);
                this.collaborationDao.updateTaskInstruction(taskId, instruction + followUp, Instant.now());
                this.collaborationDao.updateTaskState(taskId, "READY", null, null, Instant.now());
                this.collaborationDao.updatePresence(roomId, digitalHumanId, "idle", null);
                this.appendEvent(roomId, taskId, null, digitalHumanId, "TASK_STATE_CHANGED", Map.of("state", "READY", "reason", "\u5df2\u83b7\u5f97\u6210\u5458\u7b54\u590d\uff0c\u81ea\u52a8\u7eed\u8dd1"));
                return;
            }
            answer = this.stripAskMarkers(answer);
            this.collaborationDao.updateTaskState(taskId, "COMPLETED", null, null, Instant.now());
            this.collaborationDao.updatePresence(roomId, digitalHumanId, "done", null);
            this.appendEvent(roomId, taskId, null, digitalHumanId, "MESSAGE_CREATED", Map.of("role", "assistant", "digitalHumanId", digitalHumanId, "displayName", displayName, "content", answer));
            this.appendEvent(roomId, taskId, null, digitalHumanId, "TASK_STATE_CHANGED", Map.of("state", "COMPLETED"));
            if (!answer.isBlank()) {
                this.archiveArtifact(roomId, taskId, digitalHumanId, new ArtifactDraft("markdown", this.taskTitle(taskId) + " \u00b7 \u4ea4\u4ed8", answer, null));
            }
        }
        catch (CancellationException e) {
            log.info("Collab task {} canceled", (Object)taskId);
            this.collaborationDao.updatePresence(roomId, digitalHumanId, "idle", null);
        }
        catch (Exception e) {
            log.warn("Collab task {} failed: {}", (Object)taskId, (Object)e.getMessage());
            currentState = this.collaborationDao.findTask(taskId).map(CollaborationPO.TaskPO::state).orElse("");
            if ("CANCELED".equals(currentState)) {
                this.collaborationDao.updatePresence(roomId, digitalHumanId, "idle", null);
            } else if (!this.attemptAutoRecovery(roomId, taskId, digitalHumanId, human, e, token, digitalHumanTokens)) {
                this.collaborationDao.updateTaskState(taskId, "FAILED", null, e.getMessage(), Instant.now());
                this.collaborationDao.updatePresence(roomId, digitalHumanId, "error", null);
                this.appendEvent(roomId, taskId, null, digitalHumanId, "ERROR", Map.of("code", "TASK_FAILED", "message", e.getMessage() == null ? "\u6267\u884c\u5931\u8d25" : e.getMessage(), "recoverable", true));
                this.appendEvent(roomId, taskId, null, digitalHumanId, "TASK_STATE_CHANGED", Map.of("state", "FAILED"));
            }
        }
        finally {
            this.activeRuns.remove(taskId);
            this.schedule(roomId, token, digitalHumanTokens, channelCode, approvalMode);
        }
    }

    private String effectiveApprovalMode(DigitalHumanPO human, String requestedMode) {
        String policy;
        if (requestedMode != null && !requestedMode.isBlank()) {
            return requestedMode;
        }
        String string = policy = human == null || human.approvalPolicy() == null ? "WRITE_REQUIRES_APPROVAL" : human.approvalPolicy();
        if ("ALWAYS_CONFIRM".equals(policy)) {
            return "REQUEST_APPROVAL";
        }
        if ("AUTO_ALLOW".equals(policy)) {
            return "FULL_OPEN";
        }
        return "AUTO_APPROVE";
    }

    private String tokenForHuman(String fallbackToken, Map<String, String> digitalHumanTokens, String digitalHumanId) {
        String token;
        if (digitalHumanTokens != null && (token = digitalHumanTokens.get(digitalHumanId)) != null && !token.isBlank()) {
            return token;
        }
        return fallbackToken;
    }

    private String remoteInstruction(DigitalHumanPO human, String displayName, String instruction) {
        return "[\u89d2\u8272\u8bbe\u5b9a] \u4f60\u662f\u300c" + displayName + "\u300d\uff0c\u804c\u8d23\uff1a" + (human == null ? "\u901a\u7528\u667a\u80fd\u4f53" : human.purpose()) + "\u3002\u8bf7\u4ee5\u8be5\u8eab\u4efd\u5b8c\u6210\u4efb\u52a1\u3002\n[\u534f\u4f5c\u89c4\u5219] \u4f60\u5728\u591a\u6570\u5b57\u4eba\u534f\u4f5c\u623f\u95f4\u4e2d\u5de5\u4f5c\u3002\u9047\u5230\u4fe1\u606f\u4e0d\u8db3\u65f6\uff0c\u9ed8\u8ba4\u57fa\u4e8e\u4e0a\u4e0b\u6587\u505a\u51fa\u5047\u8bbe\u5e76\u7ee7\u7eed\u63a8\u8fdb\uff0c\u5728\u7ed3\u679c\u4e2d\u6ce8\u660e\u5047\u8bbe\u4e0e\u4ea4\u4ed8\u7269\u3002\u4ec5\u5f53\u7f3a\u5931\u4fe1\u606f\u662f\u963b\u585e\u6027\u7684\u4e14\u623f\u95f4\u5185\u5176\u4ed6\u6210\u5458\u53ef\u80fd\u77e5\u9053\u7b54\u6848\u65f6\uff0c\u624d\u53ef\u5728\u56de\u590d\u7684\u6700\u540e\u5355\u72ec\u4e00\u884c\u8f93\u51fa\uff1a[[ASK:@\u6210\u5458\u540d] \u4f60\u7684\u95ee\u9898]\uff08\u6574\u4e2a\u4efb\u52a1\u6700\u591a 1 \u6b21\uff0c\u95ee\u9898\u8981\u5177\u4f53\u3001\u5bf9\u65b9\u53ef\u4e00\u53e5\u8bdd\u56de\u7b54\uff09\uff0c\u63d0\u95ee\u540e\u672c\u4efb\u52a1\u4f1a\u7b49\u5f85\u5bf9\u65b9\u7b54\u590d\u5e76\u81ea\u52a8\u7eed\u8dd1\u3002\u9664\u6b64\u4e4b\u5916\u8bf7\u6b63\u5e38\u5b8c\u6210\u4efb\u52a1\u3002\n\n" + instruction;
    }

    private AskRequest parseAskRequest(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = ASK_PATTERN.matcher(text);
        AskRequest found = null;
        while (matcher.find()) {
            found = new AskRequest(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return found != null && !found.question().isBlank() ? found : null;
    }

    private String stripAskMarkers(String text) {
        return text == null ? "" : ASK_PATTERN.matcher(text).replaceAll("").trim();
    }

    private String resolveMemberByName(String roomId, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (CollaborationPO.ParticipantPO participant : this.collaborationDao.listParticipants(roomId)) {
            DigitalHumanPO human = this.humanDao.findById(participant.digitalHumanId()).orElse(null);
            if (human == null || !name.equalsIgnoreCase(human.displayName()) && !name.equalsIgnoreCase(human.id())) continue;
            return human.id();
        }
        return null;
    }

    private String askPeer(String roomId, String taskId, String askerId, String askerName, String targetId, String targetName, String question, String token, Map<String, String> digitalHumanTokens, String channelCode) throws Exception {
        this.appendEvent(roomId, taskId, null, askerId, "MESSAGE_CREATED", Map.of("role", "assistant", "digitalHumanId", askerId, "displayName", askerName, "content", "[\u5411 " + targetName + " \u63d0\u95ee] " + question));
        DigitalHumanEndpointPO endpoint = this.endpointDao.findByHumanId(targetId).orElse(null);
        final StringBuilder reply = new StringBuilder();
        RemoteAgentGateway.EventSink qaSink = new RemoteAgentGateway.EventSink(){

            @Override
            public void onChunk(String text) {
                reply.append(text);
            }

            @Override
            public void onReasoning(String text) {
            }

            @Override
            public void onToolCall(String toolName, String summary) {
            }

            @Override
            public void onToolResult(String toolName, String summary) {
            }
        };
        String qaInstruction = "[\u534f\u4f5c\u534f\u52a9\u8bf7\u6c42] \u6210\u5458\u300c" + askerName + "\u300d\u5728\u6267\u884c\u4efb\u52a1\u65f6\u5411\u4f60\u63d0\u95ee\uff1a\n" + question + "\n\u8bf7\u76f4\u63a5\u4f5c\u7b54\uff1a\u7ed3\u8bba\u5148\u884c\u3001\u7b80\u8981\uff08500 \u5b57\u5185\uff09\uff0c\u57fa\u4e8e\u4f60\u7684\u804c\u8d23\u4e0e\u623f\u95f4\u5df2\u6709\u4e0a\u4e0b\u6587\u3002\u4e0d\u8981\u53cd\u95ee\u3001\u4e0d\u8981\u5411\u7528\u6237\u63d0\u95ee\u3001\u4e0d\u8981\u6267\u884c\u957f\u4efb\u52a1\u3001\u4e0d\u8981\u8f93\u51fa [[ASK:...]] \u6807\u8bb0\u3002";
        String qaKey = taskId + ":qa";
        this.gateway.clearCancelled(qaKey);
        String remoteToken = this.tokenForHuman(token, digitalHumanTokens, targetId);
        this.remoteCredentials.remember(targetId, remoteToken);
        if (endpoint == null || "local-dsh".equals(endpoint.endpointType()) || endpoint.baseUrl() == null) {
            this.gateway.runLocal(qaKey, qaInstruction, channelCode, "FULL_OPEN", qaSink, null, null);
        } else if ("a2a".equals(endpoint.endpointType())) {
            this.gateway.runA2a(qaKey, endpoint.baseUrl(), remoteToken, qaInstruction, qaSink);
        } else {
            this.gateway.runRemote(qaKey, endpoint.baseUrl(), remoteToken, qaInstruction, channelCode, "FULL_OPEN", qaSink);
        }
        String answer = reply.toString();
        this.appendEvent(roomId, taskId, null, targetId, "MESSAGE_CREATED", Map.of("role", "assistant", "digitalHumanId", targetId, "displayName", targetName, "content", "[\u7b54\u590d " + askerName + "] " + this.abbreviate(answer.isBlank() ? "\uff08\u65e0\u6709\u6548\u7b54\u590d\uff09" : answer, 1500)));
        return answer;
    }

    private boolean attemptAutoRecovery(String roomId, String taskId, String failedHumanId, DigitalHumanPO failedHuman, Exception cause, String token, Map<String, String> digitalHumanTokens) {
        if (this.recoveryBudget.merge(taskId, 1, Integer::sum) > 1) {
            return false;
        }
        boolean endpointSurface = cause instanceof RemoteAgentGateway.HttpSurfaceException;
        CollaborationPO.TaskPO task = this.collaborationDao.findTask(taskId).orElse(null);
        if (task == null) {
            return false;
        }
        String reason = cause.getMessage() == null ? "\u6267\u884c\u5931\u8d25" : cause.getMessage();
        String recoveryNote = "\n\n[\u81ea\u52a8\u6062\u590d] \u4e0a\u4e00\u6b21\u6267\u884c\u5931\u8d25\uff1a" + this.abbreviate(reason, 300) + "\u3002\u8bf7\u8c03\u6574\u65b9\u6cd5\u6216\u8def\u5f84\u89c4\u907f\u8be5\u95ee\u9898\u540e\u91cd\u8bd5\u3002";
        String targetId = this.pickReassignTarget(roomId, failedHumanId, failedHuman);
        if (targetId != null) {
            DigitalHumanPO target = this.humanDao.findById(targetId).orElse(null);
            String targetName = target == null ? targetId : target.displayName();
            this.collaborationDao.updateTaskAssignment(taskId, targetId, "READY", Instant.now());
            this.collaborationDao.updateTaskInstruction(taskId, task.instruction() + recoveryNote, Instant.now());
            this.collaborationDao.updatePresence(roomId, failedHumanId, "idle", null);
            this.appendEvent(roomId, taskId, null, targetId, "TASK_REASSIGNED", Map.of("taskId", taskId, "from", failedHumanId, "to", targetId, "auto", true, "reason", this.abbreviate(reason, 200)));
            this.appendEvent(roomId, taskId, null, targetId, "TASK_STATE_CHANGED", Map.of("state", "READY", "reason", "\u81ea\u52a8\u6539\u6d3e\u7ed9 " + targetName + " \u91cd\u8bd5"));
            log.info("Collab task {} auto-reassigned {} -> {} after failure: {}", new Object[]{taskId, failedHumanId, targetId, reason});
            return true;
        }
        if (!endpointSurface) {
            this.collaborationDao.updateTaskState(taskId, "READY", null, null, Instant.now());
            this.collaborationDao.updateTaskInstruction(taskId, task.instruction() + recoveryNote, Instant.now());
            this.collaborationDao.updatePresence(roomId, failedHumanId, "idle", null);
            this.appendEvent(roomId, taskId, null, failedHumanId, "TASK_RETRIED", Map.of("taskId", taskId, "assignedTo", failedHumanId, "auto", true, "reason", this.abbreviate(reason, 200)));
            this.appendEvent(roomId, taskId, null, failedHumanId, "TASK_STATE_CHANGED", Map.of("state", "READY", "reason", "\u81ea\u52a8\u91cd\u8bd5"));
            log.info("Collab task {} auto-retried on {} after failure: {}", new Object[]{taskId, failedHumanId, reason});
            return true;
        }
        this.recoveryBudget.remove(taskId);
        return false;
    }

    private String pickReassignTarget(String roomId, String excludeHumanId, DigitalHumanPO failedHuman) {
        LinkedHashMap<String, Long> runningByHuman = new LinkedHashMap<String, Long>();
        for (CollaborationPO.TaskPO t : this.collaborationDao.listTasks(roomId)) {
            if (!"RUNNING".equals(t.state()) || t.assignedTo() == null) continue;
            runningByHuman.merge(t.assignedTo(), 1L, Long::sum);
        }
        String best = null;
        int bestScore = -1;
        for (CollaborationPO.ParticipantPO participant : this.collaborationDao.listParticipants(roomId)) {
            boolean sameRole;
            int score;
            int limit;
            DigitalHumanPO human;
            String id = participant.digitalHumanId();
            if (id.equals(excludeHumanId) || (human = (DigitalHumanPO)this.humanDao.findById(id).orElse(null)) == null) continue;
            int n = limit = human.concurrencyLimit() <= 0 ? 1 : human.concurrencyLimit();
            if (runningByHuman.getOrDefault(id, 0L) >= (long)limit || (score = ((sameRole = failedHuman != null && failedHuman.roleTags() != null && failedHuman.roleTags().equalsIgnoreCase(human.roleTags())) ? 2 : 0) + ("working".equals(participant.presence()) ? 0 : 1)) <= bestScore) continue;
            bestScore = score;
            best = id;
        }
        return best;
    }

    private void runWithTimeout(String taskId, ThrowingRunnable runnable) throws Exception {
        FutureTask<Void> future = new FutureTask<Void>(() -> {
            runnable.run();
            return null;
        });
        Thread thread = new Thread(future, "collab-task-" + taskId);
        thread.setDaemon(true);
        this.activeRuns.put(taskId, future);
        thread.start();
        try {
            future.get(6L, TimeUnit.MINUTES);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            thread.interrupt();
            this.gateway.cancel(taskId);
            throw new IllegalStateException("\u6267\u884c\u8d85\u65f6\uff08\u53ef\u80fd\u56e0\u7b49\u5f85\u7528\u6237\u8f93\u5165\u800c\u4e2d\u65ad\uff09\uff0c\u5df2\u81ea\u52a8\u505c\u6b62\u3002\u53ef\u91cd\u8bd5\u6216\u8c03\u6574\u4efb\u52a1\u63cf\u8ff0\u3002");
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                Exception exception = (Exception)cause;
                throw exception;
            }
            throw new IllegalStateException(String.valueOf(cause));
        }
        finally {
            this.activeRuns.remove(taskId);
        }
    }

    private String archiveArtifact(String roomId, String taskId, String digitalHumanId, ArtifactDraft draft) {
        CollaborationPO.ArtifactPO artifact = new CollaborationPO.ArtifactPO("art_" + this.shortId(), roomId, taskId, digitalHumanId, draft.kind(), draft.title(), 1, draft.content(), taskId, Instant.now());
        this.collaborationDao.insertArtifact(artifact);
        this.appendTaskArtifact(taskId, artifact.id());
        LinkedHashMap<String, Object> eventPayload = new LinkedHashMap<String, Object>();
        eventPayload.put("artifactId", artifact.id());
        eventPayload.put("title", artifact.title());
        eventPayload.put("kind", artifact.kind());
        this.readJsonObject(artifact.content()).ifPresent(node -> {
            this.putText(eventPayload, "filePath", node.path("filePath"));
            this.putText(eventPayload, "mimeType", node.path("mimeType"));
        });
        this.appendEvent(roomId, taskId, null, digitalHumanId, "ARTIFACT_CREATED", eventPayload);
        return artifact.id();
    }

    private void appendTaskArtifact(String taskId, String artifactId) {
        HashSet<String> ids = new HashSet<String>();
        this.collaborationDao.findTask(taskId).ifPresent(task -> {
            if (task.outputArtifacts() == null || task.outputArtifacts().isBlank()) {
                return;
            }
            for (String id : task.outputArtifacts().split(",")) {
                if (id.isBlank()) continue;
                ids.add(id.trim());
            }
        });
        ids.add(artifactId);
        this.collaborationDao.updateTaskArtifacts(taskId, String.join((CharSequence)",", ids), Instant.now());
    }

    private List<ArtifactDraft> extractToolArtifacts(String toolName, String summary, String rawResult) {
        ArrayList<ArtifactDraft> drafts = new ArrayList<ArtifactDraft>();
        this.readJsonObject(rawResult).ifPresent(node -> {
            String filePath;
            String title = this.textOr(node.path("title"), this.toolArtifactTitle(toolName, summary));
            JsonNode echartsOption = node.path("echartsOption");
            if (!echartsOption.isMissingNode() && !echartsOption.isNull()) {
                LinkedHashMap<String, Object> content = new LinkedHashMap<String, Object>();
                content.put("kind", "echarts");
                content.put("title", title);
                content.put("summary", this.textOr(node.path("summary"), summary));
                content.put("echartsOption", this.objectMapper.convertValue((Object)echartsOption, Object.class));
                drafts.add(new ArtifactDraft("echarts", title, this.toJson(content), "echarts:" + title));
            }
            if (!(filePath = this.textOr(node.path("filePath"), this.textOr(node.path("path"), ""))).isBlank()) {
                String kind = this.kindFromFilePath(filePath, toolName);
                LinkedHashMap<String, Object> content = new LinkedHashMap<String, Object>();
                content.put("kind", kind);
                content.put("title", title);
                content.put("filePath", filePath);
                content.put("mimeType", this.mimeTypeOf(filePath, kind));
                this.putText(content, "summary", node.path("summary"));
                this.putText(content, "previewMarkdown", node.path("markdown"));
                drafts.add(new ArtifactDraft(kind, title, this.toJson(content), kind + ":" + filePath));
            }
        });
        if (drafts.isEmpty()) {
            for (String filePath : this.extractLocalFilePaths(rawResult + "\n" + summary)) {
                String kind = this.kindFromFilePath(filePath, toolName);
                LinkedHashMap<String, String> content = new LinkedHashMap<String, String>();
                content.put("kind", kind);
                content.put("title", this.toolArtifactTitle(toolName, summary));
                content.put("filePath", filePath);
                content.put("mimeType", this.mimeTypeOf(filePath, kind));
                drafts.add(new ArtifactDraft(kind, this.toolArtifactTitle(toolName, summary), this.toJson(content), kind + ":" + filePath));
            }
        }
        return drafts;
    }

    private Optional<JsonNode> readJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = this.objectMapper.readTree(value.trim());
            if (node.isTextual()) {
                node = this.objectMapper.readTree(node.asText());
            }
            return node.isObject() ? Optional.of(node) : Optional.empty();
        }
        catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<String> extractLocalFilePaths(String text) {
        ArrayList<String> paths = new ArrayList<String>();
        Matcher matcher = LOCAL_FILE_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String path = matcher.group().replaceAll("[.,;:!?\uff0c\u3002\uff1b\uff1a\uff01\uff1f\uff09)\u300d\u3011'\"]+$", "");
            if (paths.contains(path)) continue;
            paths.add(path);
        }
        return paths;
    }

    private String toolArtifactTitle(String toolName, String summary) {
        String base;
        String string = base = summary == null || summary.isBlank() ? toolName : summary;
        if (base == null || base.isBlank()) {
            return "\u5de5\u5177\u4ea7\u7269";
        }
        return base.length() > 48 ? base.substring(0, 48) + "\u2026" : base;
    }

    private String kindFromFilePath(String filePath, String toolName) {
        String tool;
        String lower = filePath == null ? "" : filePath.toLowerCase();
        String string = tool = toolName == null ? "" : toolName.toLowerCase();
        if (lower.endsWith(".docx") || tool.contains("doc")) {
            return "word";
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv") || tool.contains("sheet")) {
            return "excel";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "markdown";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "html";
        }
        if (lower.endsWith(".drawio")) {
            return "drawio";
        }
        return "file";
    }

    private String mimeTypeOf(String filePath, String kind) {
        String lower;
        String string = lower = filePath == null ? "" : filePath.toLowerCase();
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if ("markdown".equals(kind)) {
            return "text/markdown";
        }
        if ("html".equals(kind)) {
            return "text/html";
        }
        if ("drawio".equals(kind) || lower.endsWith(".drawio")) {
            return "application/x-drawio";
        }
        return "application/octet-stream";
    }

    private String textOr(JsonNode node, String fallback) {
        return node != null && node.isTextual() ? node.asText() : fallback;
    }

    private void putText(Map<String, Object> target, String key, JsonNode node) {
        if (node != null && node.isTextual() && !node.asText().isBlank()) {
            target.put(key, node.asText());
        }
    }

    public SseEmitter subscribe(String roomId, long afterSeq) {
        this.ensureRoom(roomId);
        SseEmitter emitter = new SseEmitter(Long.valueOf(0L));
        this.roomEmitters.computeIfAbsent(roomId, key -> new CopyOnWriteArrayList()).add(emitter);
        AtomicBoolean heartbeatStopped = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = this.startHeartbeat(emitter, heartbeatStopped);
        Runnable cleanup = () -> {
            heartbeatStopped.set(true);
            heartbeat.cancel(false);
            this.roomEmitters.getOrDefault(roomId, new CopyOnWriteArrayList()).remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        this.executor.submit(() -> {
            try {
                List<CollaborationPO.RoomEventPO> batch;
                long cursor = afterSeq;
                do {
                    batch = this.collaborationDao.listEventsAfter(roomId, cursor, 500);
                    for (CollaborationPO.RoomEventPO event : batch) {
                        emitter.send(SseEmitter.event().name("room-event").data((Object)this.toJson(this.eventView(event))));
                        cursor = event.seq();
                    }
                } while (batch.size() >= 500);
            }
            catch (Exception e) {
                cleanup.run();
                emitter.complete();
            }
        });
        return emitter;
    }

    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, AtomicBoolean stopped) {
        return this.heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (stopped.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            }
            catch (Exception exception) {
                stopped.set(true);
            }
        }, 15000L, 15000L, TimeUnit.MILLISECONDS);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void appendEvent(String roomId, String taskId, String participantId, String digitalHumanId, String type, Map<String, Object> payload) {
        try {
            String string = ("seq_" + roomId).intern();
            synchronized (string) {
                long seq = this.collaborationDao.maxEventSeq(roomId) + 1L;
                CollaborationPO.RoomEventPO event = new CollaborationPO.RoomEventPO("evt_" + this.shortId(), roomId, seq, taskId, participantId, digitalHumanId, type, Instant.now(), "room", this.toJson(payload), taskId);
                this.collaborationDao.insertEvent(event);
                this.push(roomId, event);
            }
        }
        catch (Exception e) {
            log.warn("Append room event failed ({} {}): {}", new Object[]{roomId, type, e.getMessage()});
        }
    }

    private void push(String roomId, CollaborationPO.RoomEventPO event) {
        List<SseEmitter> emitters = this.roomEmitters.get(roomId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        String json = this.toJson(this.eventView(event));
        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            try {
                emitter.send(SseEmitter.event().name("room-event").data((Object)json));
            }
            catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public List<Map<String, Object>> listEvents(String roomId, long afterSeq) {
        ArrayList<Map<String, Object>> views = new ArrayList<Map<String, Object>>();
        for (CollaborationPO.RoomEventPO event : this.collaborationDao.listEventsAfter(roomId, afterSeq, 500)) {
            views.add(this.eventView(event));
        }
        return views;
    }

    private Map<String, Object> taskView(CollaborationPO.TaskPO task) {
        LinkedHashMap<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("taskId", task.id());
        view.put("roomId", task.roomId());
        view.put("title", task.title());
        view.put("instruction", task.instruction());
        view.put("assignedTo", task.assignedTo());
        view.put("state", task.state());
        view.put("dependsOn", task.dependsOn() == null ? List.of() : List.of(task.dependsOn().split(",")));
        view.put("traceId", task.traceId());
        view.put("errorSummary", task.errorSummary());
        view.put("createdAt", task.createdAt() == null ? null : task.createdAt().toString());
        this.humanDao.findById(task.assignedTo()).ifPresent(human -> {
            view.put("assigneeName", human.displayName());
            view.put("assigneeAvatar", human.avatarRef());
            view.put("assigneeColor", human.themeColor());
        });
        return view;
    }

    private Map<String, Object> artifactView(CollaborationPO.ArtifactPO artifact) {
        LinkedHashMap<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("artifactId", artifact.id());
        view.put("taskId", artifact.taskId());
        view.put("producerId", artifact.producerId());
        view.put("kind", artifact.kind());
        view.put("title", artifact.title());
        view.put("version", artifact.version());
        view.put("content", artifact.content());
        this.readJsonObject(artifact.content()).ifPresent(node -> {
            this.putText(view, "filePath", node.path("filePath"));
            this.putText(view, "mimeType", node.path("mimeType"));
            this.putText(view, "previewMarkdown", node.path("previewMarkdown"));
            this.putText(view, "summary", node.path("summary"));
            JsonNode echartsOption = node.path("echartsOption");
            if (!echartsOption.isMissingNode() && !echartsOption.isNull()) {
                view.put("echartsOption", this.objectMapper.convertValue((Object)echartsOption, Object.class));
            }
        });
        view.put("createdAt", artifact.createdAt() == null ? null : artifact.createdAt().toString());
        this.humanDao.findById(artifact.producerId()).ifPresent(human -> {
            view.put("producerName", human.displayName());
            view.put("producerColor", human.themeColor());
        });
        return view;
    }

    private Map<String, Object> eventView(CollaborationPO.RoomEventPO event) {
        LinkedHashMap<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", event.id());
        view.put("roomId", event.roomId());
        view.put("seq", event.seq());
        view.put("taskId", event.taskId());
        view.put("participantId", event.participantId());
        view.put("digitalHumanId", event.digitalHumanId());
        view.put("type", event.eventType());
        view.put("occurredAt", event.occurredAt() == null ? null : event.occurredAt().toString());
        try {
            view.put("payload", this.objectMapper.readValue(event.payload() == null ? "{}" : event.payload(), Map.class));
        }
        catch (Exception e) {
            view.put("payload", Map.of());
        }
        view.put("traceId", event.traceId());
        if (event.digitalHumanId() != null) {
            this.humanDao.findById(event.digitalHumanId()).ifPresent(human -> {
                view.put("displayName", human.displayName());
                view.put("avatarRef", human.avatarRef());
                view.put("themeColor", human.themeColor());
            });
        }
        return view;
    }

    private String taskTitle(String taskId) {
        return this.collaborationDao.findTask(taskId).map(CollaborationPO.TaskPO::title).orElse(null);
    }

    private CollaborationPO.RoomPO ensureRoom(String roomId) {
        return this.collaborationDao.findRoom(roomId).orElseThrow(() -> new IllegalArgumentException("\u623f\u95f4\u4e0d\u5b58\u5728\uff1a" + roomId));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() > max ? singleLine.substring(0, max) + "\u2026" : singleLine;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String toJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        }
        catch (Exception e) {
            return "{}";
        }
    }

    private record SandboxScope(String cwd, List<String> sandboxRoots) {
        static final SandboxScope EMPTY = new SandboxScope(null, List.of());
    }

    private class GatewayEventSink
    implements RemoteAgentGateway.EventSink {
        private final String roomId;
        private final String taskId;
        private final String digitalHumanId;
        private final StringBuilder finalText;
        private final Set<String> artifactKeys = ConcurrentHashMap.newKeySet();
        private final Set<String> toolCallKeys = ConcurrentHashMap.newKeySet();
        volatile boolean approvalPending = false;
        volatile String approvalSummary = "";

        GatewayEventSink(String roomId, String taskId, String digitalHumanId, StringBuilder finalText) {
            this.roomId = roomId;
            this.taskId = taskId;
            this.digitalHumanId = digitalHumanId;
            this.finalText = finalText;
        }

        @Override
        public void onChunk(String text) {
            this.finalText.append(text);
            CollaborationService.this.appendEvent(this.roomId, this.taskId, null, this.digitalHumanId, "MESSAGE_CHUNK", Map.of("text", text));
        }

        @Override
        public void onReasoning(String text) {
        }

        @Override
        public void onToolCall(String toolName, String summary) {
            this.onToolCall(toolName, summary, "");
        }

        @Override
        public void onToolCall(String toolName, String summary, String callId) {
            boolean duplicated;
            boolean bl = duplicated = callId != null && !callId.isBlank() && !this.toolCallKeys.add(callId);
            if (duplicated) {
                return;
            }
            LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("toolName", toolName);
            payload.put("summary", summary);
            if (callId != null && !callId.isBlank()) {
                payload.put("callId", callId);
            }
            CollaborationService.this.appendEvent(this.roomId, this.taskId, null, this.digitalHumanId, "TOOL_CALL", payload);
        }

        @Override
        public void onToolResult(String toolName, String summary) {
            this.onToolResult(toolName, summary, "");
        }

        @Override
        public void onToolResult(String toolName, String summary, String rawResult) {
            this.onToolResult(toolName, summary, rawResult, "");
        }

        @Override
        public void onToolResult(String toolName, String summary, String rawResult, String callId) {
            this.approvalPending = false;
            LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("toolName", toolName);
            payload.put("summary", summary);
            if (callId != null && !callId.isBlank()) {
                payload.put("callId", callId);
            }
            CollaborationService.this.appendEvent(this.roomId, this.taskId, null, this.digitalHumanId, "TOOL_RESULT", payload);
            for (ArtifactDraft draft : CollaborationService.this.extractToolArtifacts(toolName, summary, rawResult)) {
                if (draft.identity() != null && !this.artifactKeys.add(draft.identity())) continue;
                try {
                    CollaborationService.this.archiveArtifact(this.roomId, this.taskId, this.digitalHumanId, draft);
                }
                catch (Exception exception) {
                    log.debug("Skip tool artifact archive for {}: {}", (Object)toolName, (Object)exception.getMessage());
                }
            }
        }

        @Override
        public void onApprovalRequired(String toolName, String summary) {
            this.approvalPending = true;
            this.approvalSummary = summary == null || summary.isBlank() ? toolName : summary;
        }

        @Override
        public boolean approvalAwaiting() {
            return this.approvalPending;
        }
    }

    @FunctionalInterface
    private static interface ThrowingRunnable {
        public void run() throws Exception;
    }

    private record AskRequest(String targetName, String question) {
    }

    private record ArtifactDraft(String kind, String title, String content, String identity) {
    }
}

