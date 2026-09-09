package com.app.repository;

import com.app.model.SupplyItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyItemRepository extends JpaRepository<SupplyItem, String> {
}
