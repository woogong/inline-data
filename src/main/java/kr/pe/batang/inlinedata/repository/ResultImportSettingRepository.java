package kr.pe.batang.inlinedata.repository;

import kr.pe.batang.inlinedata.entity.ResultImportSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultImportSettingRepository extends JpaRepository<ResultImportSetting, Long> {

    Optional<ResultImportSetting> findTopByOrderByIdAsc();
}
