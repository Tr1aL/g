package org.tr1al.gainium.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tr1al.gainium.entity.Setting;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Integer> {
}
