package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.RelayTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RelayTeamMemberRepository extends JpaRepository<RelayTeamMember, Long> {

    List<RelayTeamMember> findByHeatEntryIdOrderByOrderNumberAsc(Long heatEntryId);

    @Query("SELECT m FROM RelayTeamMember m LEFT JOIN FETCH m.athlete " +
           "WHERE m.heatEntry.id IN :ids ORDER BY m.heatEntry.id ASC, m.orderNumber ASC")
    List<RelayTeamMember> findByHeatEntryIdIn(@Param("ids") List<Long> heatEntryIds);

    void deleteByHeatEntryId(Long heatEntryId);
}
