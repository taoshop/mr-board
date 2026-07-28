package com.mrboard.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrboard.system.entity.Mrs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MrsMapper extends BaseMapper<Mrs> {

    @Select("SELECT p.name as projectName, COUNT(*) as count FROM mrs m JOIN projects p ON m.project_id = p.id GROUP BY m.project_id, p.name ORDER BY count DESC")
    List<Map<String, Object>> selectProjectDistribution();

    @Select("SELECT author_name as authorName, COUNT(*) as count FROM mrs GROUP BY author_name ORDER BY count DESC LIMIT 50")
    List<Map<String, Object>> selectAuthorDistribution();

    @Select("SELECT board_status as boardStatus, COUNT(*) as count FROM mrs GROUP BY board_status")
    List<Map<String, Object>> selectStatusDistribution();

    @Select("SELECT DISTINCT DATE(created_at) FROM mrs UNION SELECT DISTINCT DATE(merged_at) FROM mrs WHERE merged_at IS NOT NULL UNION SELECT DISTINCT DATE(closed_at) FROM mrs WHERE closed_at IS NOT NULL ORDER BY 1")
    List<java.time.LocalDate> selectDistinctDatesWithActivity();
}
