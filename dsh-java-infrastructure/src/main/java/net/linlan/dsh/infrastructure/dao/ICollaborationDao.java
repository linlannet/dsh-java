package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.CollaborationPO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ICollaborationDao {
    @Insert(value={"INSERT INTO collaboration_room (id, workspace_id, title, objective, orchestration_mode, run_state, created_at)\nVALUES (#{id}, #{workspaceId}, #{title}, #{objective}, #{orchestrationMode}, #{runState}, #{createdAt})\n"})
    public void insertRoom(CollaborationPO.RoomPO var1);

    @Select(value={"SELECT id, workspace_id, title, objective, orchestration_mode, run_state, created_at\nFROM collaboration_room WHERE id = #{id}\n"})
    public Optional<CollaborationPO.RoomPO> findRoom(@Param(value="id") String var1);

    @Update(value={"UPDATE collaboration_room SET objective = #{objective} WHERE id = #{id} AND objective IS NULL"})
    public int fillObjective(@Param(value="id") String var1, @Param(value="objective") String var2);

    @Update(value={"UPDATE collaboration_room SET run_state = #{runState} WHERE id = #{id}"})
    public int updateRoomState(@Param(value="id") String var1, @Param(value="runState") String var2);

    @Insert(value={"INSERT INTO room_participant (id, room_id, digital_human_id, room_role, presence, active_task_label, joined_at)\nVALUES (#{id}, #{roomId}, #{digitalHumanId}, #{roomRole}, #{presence}, #{activeTaskLabel}, #{joinedAt})\n"})
    public void insertParticipant(CollaborationPO.ParticipantPO var1);

    @Select(value={"SELECT id, room_id, digital_human_id, room_role, presence, active_task_label, joined_at\nFROM room_participant WHERE room_id = #{roomId} AND digital_human_id = #{digitalHumanId}\n"})
    public Optional<CollaborationPO.ParticipantPO> findParticipant(@Param(value="roomId") String var1, @Param(value="digitalHumanId") String var2);

    @Select(value={"SELECT id, room_id, digital_human_id, room_role, presence, active_task_label, joined_at\nFROM room_participant WHERE room_id = #{roomId} ORDER BY joined_at ASC\n"})
    public List<CollaborationPO.ParticipantPO> listParticipants(@Param(value="roomId") String var1);

    @Update(value={"UPDATE room_participant SET presence = #{presence}, active_task_label = #{activeTaskLabel}\nWHERE room_id = #{roomId} AND digital_human_id = #{digitalHumanId}\n"})
    public int updatePresence(@Param(value="roomId") String var1, @Param(value="digitalHumanId") String var2, @Param(value="presence") String var3, @Param(value="activeTaskLabel") String var4);

    @Update(value={"DELETE FROM room_participant WHERE room_id = #{roomId} AND digital_human_id = #{digitalHumanId}"})
    public int removeParticipant(@Param(value="roomId") String var1, @Param(value="digitalHumanId") String var2);

    @Insert(value={"INSERT INTO collaboration_task (id, room_id, parent_task_id, title, instruction, assigned_to, requested_by, state, depends_on, input_artifacts, output_artifacts, remote_session_id, trace_id, error_summary, created_at, updated_at)\nVALUES (#{id}, #{roomId}, #{parentTaskId}, #{title}, #{instruction}, #{assignedTo}, #{requestedBy}, #{state}, #{dependsOn}, #{inputArtifacts}, #{outputArtifacts}, #{remoteSessionId}, #{traceId}, #{errorSummary}, #{createdAt}, #{updatedAt})\n"})
    public void insertTask(CollaborationPO.TaskPO var1);

    @Update(value={"UPDATE collaboration_task SET state = #{state}, remote_session_id = #{remoteSessionId},\n    error_summary = #{errorSummary}, updated_at = #{updatedAt}\nWHERE id = #{id}\n"})
    public int updateTaskState(@Param(value="id") String var1, @Param(value="state") String var2, @Param(value="remoteSessionId") String var3, @Param(value="errorSummary") String var4, @Param(value="updatedAt") Instant var5);

    @Update(value={"UPDATE collaboration_task SET output_artifacts = #{outputArtifacts}, updated_at = #{updatedAt} WHERE id = #{id}"})
    public int updateTaskArtifacts(@Param(value="id") String var1, @Param(value="outputArtifacts") String var2, @Param(value="updatedAt") Instant var3);

    @Update(value={"UPDATE collaboration_task SET instruction = #{instruction}, updated_at = #{updatedAt} WHERE id = #{id}"})
    public int updateTaskInstruction(@Param(value="id") String var1, @Param(value="instruction") String var2, @Param(value="updatedAt") Instant var3);

    @Update(value={"UPDATE collaboration_task SET assigned_to = #{assignedTo}, state = #{state}, remote_session_id = NULL,\n    error_summary = NULL, updated_at = #{updatedAt}\nWHERE id = #{id}\n"})
    public int updateTaskAssignment(@Param(value="id") String var1, @Param(value="assignedTo") String var2, @Param(value="state") String var3, @Param(value="updatedAt") Instant var4);

    @Select(value={"SELECT id, room_id, parent_task_id, title, instruction, assigned_to, requested_by, state, depends_on, input_artifacts, output_artifacts, remote_session_id, trace_id, error_summary, created_at, updated_at\nFROM collaboration_task WHERE id = #{id}\n"})
    public Optional<CollaborationPO.TaskPO> findTask(@Param(value="id") String var1);

    @Select(value={"SELECT id, room_id, parent_task_id, title, instruction, assigned_to, requested_by, state, depends_on, input_artifacts, output_artifacts, remote_session_id, trace_id, error_summary, created_at, updated_at\nFROM collaboration_task WHERE room_id = #{roomId} ORDER BY created_at ASC\n"})
    public List<CollaborationPO.TaskPO> listTasks(@Param(value="roomId") String var1);

    @Insert(value={"INSERT INTO collaboration_artifact (id, room_id, task_id, producer_id, kind, title, version, content, trace_id, created_at)\nVALUES (#{id}, #{roomId}, #{taskId}, #{producerId}, #{kind}, #{title}, #{version}, #{content}, #{traceId}, #{createdAt})\n"})
    public void insertArtifact(CollaborationPO.ArtifactPO var1);

    @Select(value={"SELECT id, room_id, task_id, producer_id, kind, title, version, content, trace_id, created_at\nFROM collaboration_artifact WHERE room_id = #{roomId} ORDER BY created_at ASC\n"})
    public List<CollaborationPO.ArtifactPO> listArtifacts(@Param(value="roomId") String var1);

    @Insert(value={"INSERT INTO room_event (id, room_id, seq, task_id, participant_id, digital_human_id, event_type, occurred_at, visibility, payload, trace_id)\nVALUES (#{id}, #{roomId}, #{seq}, #{taskId}, #{participantId}, #{digitalHumanId}, #{eventType}, #{occurredAt}, #{visibility}, #{payload}, #{traceId})\n"})
    public void insertEvent(CollaborationPO.RoomEventPO var1);

    @Select(value={"SELECT COALESCE(MAX(seq), 0) FROM room_event WHERE room_id = #{roomId}"})
    public long maxEventSeq(@Param(value="roomId") String var1);

    @Select(value={"SELECT id, room_id, seq, task_id, participant_id, digital_human_id, event_type, occurred_at, visibility, payload, trace_id\nFROM room_event WHERE room_id = #{roomId} AND seq > #{afterSeq} ORDER BY seq ASC LIMIT #{limit}\n"})
    public List<CollaborationPO.RoomEventPO> listEventsAfter(@Param(value="roomId") String var1, @Param(value="afterSeq") long var2, @Param(value="limit") int var4);
}

