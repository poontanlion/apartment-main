package com.app.repository;

import com.app.model.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildingRepository extends JpaRepository<Building, String> {
    List<Building> findAllByOrderByCodeAsc();
    Optional<Building> findByCodeIgnoreCase(String code);
}
