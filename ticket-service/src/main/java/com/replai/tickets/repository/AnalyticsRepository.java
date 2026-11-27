package com.replai.tickets.repository;

import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Read-only queries behind /api/analytics. Every number on the dashboard comes
 * from the tickets table with GROUP BY, so no extra service is needed.
 */
public interface AnalyticsRepository extends Repository<Ticket, Long> {

    @Query("select count(t) from Ticket t where t.status <> :resolved")
    long countUnresolved(@Param("resolved") TicketStatus resolvedStatus);

    @Query("select count(t) from Ticket t where t.status = :resolved")
    long countResolved(@Param("resolved") TicketStatus resolvedStatus);

    /**
     * One row per resolved ticket: (teamId, assigneeId, createdAt, resolvedAt,
     * slaDueAt, slaBreached). Durations and SLA results are averaged from these
     * rows. The optional cutoff restricts the range for the leaderboard.
     */
    @Query("""
            select t.teamId, t.assigneeId, t.createdAt, t.resolvedAt, t.slaDueAt, t.slaBreached
            from Ticket t
            where t.status = :resolved and (:cutoff is null or t.resolvedAt >= :cutoff)
            """)
    List<Object[]> resolvedRows(@Param("resolved") TicketStatus resolvedStatus,
                                @Param("cutoff") Instant cutoff);

    @Query("""
            select t.teamId, count(t) from Ticket t
            where t.teamId is not null and t.status <> :resolved
            group by t.teamId
            """)
    List<Object[]> openCountByTeam(@Param("resolved") TicketStatus resolvedStatus);

    @Query("""
            select t.assigneeId, count(t) from Ticket t
            where t.assigneeId is not null and t.status = :resolved
              and (:cutoff is null or t.resolvedAt >= :cutoff)
            group by t.assigneeId
            """)
    List<Object[]> resolvedCountByEmployee(@Param("resolved") TicketStatus resolvedStatus,
                                           @Param("cutoff") Instant cutoff);

    @Query("""
            select t.priority, count(t) from Ticket t
            where t.status <> :resolved and t.priority is not null
            group by t.priority
            """)
    List<Object[]> openCountByPriority(@Param("resolved") TicketStatus resolvedStatus);

    @Query("""
            select t.draftAction, count(t) from Ticket t
            where t.draftAction is not null
            group by t.draftAction
            """)
    List<Object[]> countByDraftAction();

    @Query("select t from Ticket t where t.knowledgeGap = true order by t.createdAt desc")
    List<Ticket> findKnowledgeGaps();
}
