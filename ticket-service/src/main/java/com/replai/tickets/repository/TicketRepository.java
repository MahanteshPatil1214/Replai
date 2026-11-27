package com.replai.tickets.repository;

import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findAllByOrderByCreatedAtDesc();

    /**
     * Ticket list for the UI queues. All filters are optional; results are
     * ordered by priority (CRITICAL first) and then oldest first, as the spec
     * requires for team queues.
     */
    @Query("""
            select t from Ticket t
            where (:status is null or t.status = :status)
              and (:teamId is null or t.teamId = :teamId)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assigneeId = :assigneeId)
            order by case t.priority
                         when 'CRITICAL' then 0
                         when 'HIGH' then 1
                         when 'MEDIUM' then 2
                         when 'LOW' then 3
                         else 4
                     end,
                     t.createdAt asc
            """)
    List<Ticket> search(@Param("status") TicketStatus status,
                        @Param("teamId") Long teamId,
                        @Param("priority") String priority,
                        @Param("assigneeId") Long assigneeId);

    /** Open ticket counts per employee, used to pick the least-loaded agent. */
    @Query("""
            select t.assigneeId, count(t) from Ticket t
            where t.assigneeId is not null and t.status in :statuses
            group by t.assigneeId
            """)
    List<Object[]> countOpenTicketsByAssignee(@Param("statuses") Collection<TicketStatus> statuses);

    /** Unresolved tickets whose SLA deadline has passed and are not flagged yet. */
    @Query("""
            select t from Ticket t
            where t.slaDueAt is not null and t.slaDueAt < :now
              and t.status <> :resolved
              and (t.slaBreached = false or t.slaBreached is null)
            """)
    List<Ticket> findSlaBreaches(@Param("now") Instant now,
                                 @Param("resolved") TicketStatus resolvedStatus);

    /** Tickets still sitting in one status after the cutoff (stuck-ticket job). */
    @Query("""
            select t from Ticket t
            where t.status = :status and t.createdAt < :cutoff
            """)
    List<Ticket> findOlderThan(@Param("status") TicketStatus status, @Param("cutoff") Instant cutoff);
}
