package net.linlan.dsh.domain.todo.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.TodoItemPayload;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.todo.model.valobj.TodoItem;
import net.linlan.dsh.domain.todo.model.valobj.TodoWriteResultVO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 待办写入领域服务。
 * <p>
 * 执行流程：
 * 1. 校验完整待办列表；
 * 2. 写入 todo/write 会话事件；
 * 3. 返回统计后的结果。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class TodoWriteService {

    /**
     * 写入待办列表。
     * <p>
     * 执行流程：
     * 1. 校验重复项与 in_progress 数量；
     * 2. 将待办列表转换为会话事件；
     * 3. 返回统计结果。
     */
    public TodoWriteResultVO write(SessionLog session, List<TodoItem> todos, boolean allowParallelInProgress) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        if (todos == null) {
            throw new IllegalArgumentException("todos must not be null");
        }

        Set<String> seen = new HashSet<>();
        int active = 0;
        List<TodoItem> normalized = new ArrayList<>();
        for (TodoItem todo : todos) {
            if (todo == null) {
                throw new IllegalArgumentException("todo item must not be null");
            }
            String content = todo.content().trim();
            if (!seen.add(content)) {
                throw new IllegalArgumentException("duplicate todo content: " + content);
            }
            if (todo.status() == TodoItem.Status.IN_PROGRESS) {
                active++;
            }
            normalized.add(new TodoItem(content, todo.status()));
        }

        if (!allowParallelInProgress && active > 1) {
            throw new IllegalArgumentException("at most one task may be in_progress");
        }

        List<TodoItemPayload> payloads = normalized.stream()
                .map(item -> new TodoItemPayload(item.content(), item.status().wire()))
                .toList();
        session.append(SessionEventFactory.todoWrite(payloads));

        int pending = (int) normalized.stream().filter(item -> item.status() == TodoItem.Status.PENDING).count();
        int inProgress = (int) normalized.stream().filter(item -> item.status() == TodoItem.Status.IN_PROGRESS).count();
        int completed = (int) normalized.stream().filter(item -> item.status() == TodoItem.Status.COMPLETED).count();
        return new TodoWriteResultVO(normalized, pending, inProgress, completed);
    }

    /**
     * 折叠会话中的最新待办列表。
     * <p>
     * 执行流程：
     * 1. 遍历 session events；
     * 2. 读取最新 todo/write 事件；
     * 3. 返回当前待办快照。
     */
    public List<TodoItem> foldTodos(SessionLog session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        List<TodoItem> result = new ArrayList<>();
        for (SessionEvent event : session.events()) {
            if (event instanceof SessionEvent.TodoWrite tw) {
                result = tw.data().todos().stream()
                        .map(item -> new TodoItem(item.content(), TodoItem.Status.fromWire(item.status())))
                        .toList();
            }
        }
        return List.copyOf(result);
    }
}
