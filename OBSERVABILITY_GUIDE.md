# คู่มือระบบ Observability & Business Monitoring (Grafana LGTM Stack)
## โครงการระบบบริหารจัดการอพาร์ตเมนต์ (Victory Apartment Management System)

ระบบ Observability ของโครงการ **Victory Apartment Management System** รวม 3 เสาหลัก (**Metrics, Logs, Traces**) เข้ากับ **Single Pane of Glass บน Grafana** อย่างครบวงจร เพื่อตอบสนองทั้งการตรวจสอบเชิงวิศวกรรมระบบ (Engineering Monitoring / SRE RED Method) และการติดตามมูลค่าทางธุรกิจจริง (Business Value Metrics)

---

## 1. สถาปัตยกรรมระบบ (Architecture Overview)

```
                    +------------------------------------------+
                    |           React Frontend UI              |
                    +--------------------+---------------------+
                                         | HTTP Requests
                                         v
                    +------------------------------------------+
                    |      Apartment Backend (Spring Boot 3)   |
                    |  - Micrometer Prometheus Registry        |
                    |  - OpenTelemetry Tracing Bridge (OTLP)   |
                    |  - Multi-layer Method Spans (@NewSpan)   |
                    |  - Direct Log Streaming to Loki (Loki4j) |
                    +--------+----------------+----------------+
                             |                |
             Metrics (Pull)  |                | Traces (Push OTLP)
                    +--------v-------+        |
                    |   Prometheus   |        v
                    |  Port: 9090    |  +-----------+         +------------------+
                    +--------+-------+  |   Tempo   |         | Docker Container |
                             |          | Port:3200 |         |       Logs       |
                             |          +-----+-----+         +--------+---------+
                             |                |                        |
                             |                |                        v
                             |                |               +------------------+
                             |                |               |     Promtail     |
                             |                |               +--------+---------+
                             |                |                        |
                             |                |    +-------------------+
                             |                |    | Push Logs (Loki4j + Promtail)
                             |                |    v
                             |                | +---------------------+
                             |                | |    Grafana Loki     |
                             |                | |     Port: 3100      |
                             |                | +----------+----------+
                             |                |            |
                             +----------------+------------+
                                              |
                                              v
                              +-------------------------------+
                              |       Grafana Dashboard       |
                              |   Port: 3000 (admin/admin)    |
                              | - Metrics (RED + Business)    |
                              | - Traces (Waterfall Spans)    |
                              | - Logs (Trace-to-Log Link)    |
                              +-------------------------------+
```

---

## 2. การจำแนกประเภท Metrics (Metric Catalog)

### 2.1 Business Value Metrics (คุณค่าในเชิงธุรกิจ)
คำนวณและประมวลผลข้อมูลสดจากฐานข้อมูล PostgreSQL โดยอัตโนมัติทุก 15 วินาที เพื่อเป็น KPI สรุปสถานะการดำเนินงานของอพาร์ตเมนต์:

| Metric Name | Type | รายละเอียด / คุณค่าเชิงธุรกิจ (Business Value) |
|---|---|---|
| `apartment_rooms_occupancy_rate` | Gauge | **อัตราการเข้าพัก (% Occupancy Rate):** สัดส่วนห้องพักที่มีผู้เช่าจริงเทียบกับห้องทั้งหมด เป็น KPI สูงสุดในการประเมินประสิทธิภาพธุรกิจ |
| `apartment_rooms_occupied` | Gauge | **จำนวนห้องที่มีผู้เช่า:** ห้องพักที่มีสถานะเป็น Occupied |
| `apartment_rooms_available` | Gauge | **จำนวนห้องว่างพร้อมปล่อยเช่า:** ข้อมูลสำหรับฝ่ายการตลาดเพื่อใช้วางแผนโปรโมชัน |
| `apartment_rooms_maintenance` | Gauge | **จำนวนห้องที่ปิดซ่อมบำรุง:** ห้องที่อยู่ระหว่างการปรับปรุงและยังไม่พร้อมปล่อยเช่า |
| `apartment_rooms_total` | Gauge | **จำนวนห้องพักทั้งหมดในอาคาร** |
| `apartment_bills_total_invoiced` | Gauge | **ยอดออกบิลเรียกเก็บรวม (Total Invoiced Revenue):** มูลค่าบิลทั้งหมดในรอบ |
| `apartment_bills_paid_amount` | Gauge | **ยอดเงินสดที่เก็บได้จริง (Cashflow Inflow):** สะท้อนสภาพคล่องทางการเงินจริงของอพาร์ตเมนต์ |
| `apartment_bills_outstanding_amount` | Gauge | **ยอดหนี้ค้างชำระสะสม (Outstanding Debt):** เงินค้างจ่ายเพื่อการติดตามหนี้และบริหารความเสี่ยง |
| `apartment_bills_unpaid_count` | Gauge | **จำนวนบิลที่ยังไม่ชำระ / ค้างชำระ:** ปริมาณรายการที่ยังไม่เคลียร์ยอด |
| `apartment_maintenance_tasks_active` | Gauge | **งานซ่อมบำรุงที่เปิดอยู่ (Active Work Orders):** ภาระงานปัจจุบันของทีมช่าง |
| `apartment_maintenance_tasks_high_priority` | Gauge | **งานซ่อมด่วนฉุกเฉิน (High Priority):** งานซ่อมที่กระทบความเป็นอยู่ของลูกบ้านโดยตรง |
| `apartment_maintenance_predictions_total` | Gauge | **จำนวนอุปกรณ์ที่ระบบ AI พยากรณ์พบความเสี่ยงล่วงหน้า:** Predictive Maintenance |
| `apartment_bookings_pending` | Gauge | **คำขอจองห้องพักใหม่ที่รอการอนุมัติ:** สะท้อน Demand ลูกค้าใหม่ที่ต้องการเข้าพัก |

### 2.2 Monitoring Value Metrics (เชิงวิศวกรรมระบบและ SRE)
รวบรวมผ่าน Spring Boot Actuator และ Micrometer ตามหลักการ **RED Method (Rate, Errors, Duration)** เพื่อรักษาเสถียรภาพของระบบ:

| Metric Name | Type | ประโยชน์ทางวิศวกรรมระบบ (Engineering Value) |
|---|---|---|
| `http_server_requests_seconds_count` | Counter | **Throughput (Rate):** ปริมาณคำขอต่อวินาที (RPS) แยกตาม URI และสถานะ |
| `http_server_requests_seconds_count{status=~"5.."}` | Counter | **Error Rate (Errors):** สัดส่วนข้อผิดพลาด HTTP 5xx Server Error |
| `http_server_requests_seconds_max` / histogram | Gauge | **Duration (Latency):** วัดความเร็ว p95 และ Response Time สูงสุดของ API |
| `jvm_memory_used_bytes{area="heap"}` | Gauge | **JVM Heap Memory:** ตรวจสอบการใช้งานหน่วยความจำ ป้องกัน OutOfMemoryError |
| `hikaricp_connections_active` / `pending` | Gauge | **Database Connection Pool:** ตรวจสอบสุขภาพและการรองรับโหลดของ PostgreSQL |
| `system_cpu_usage` | Gauge | **CPU Utilization:** ภาระการประมวลผลของโฮสต์และคอนเทนเนอร์ |

---

## 3. กฎการแจ้งเตือน (Alerting Rules)

กำหนดไว้ในไฟล์ [`observability/prometheus/alert-rules.yaml`](observability/prometheus/alert-rules.yaml) แบ่งออกเป็น 2 กลุ่ม:

### 3.1 Monitoring Alerts (เชิงวิศวกรรมระบบ)
| Alert Name | เงื่อนไขการแจ้งเตือน | Severity | ผู้รับผิดชอบ |
|---|---|---|---|
| `HighHttpErrorRate` | HTTP 5xx Error Rate > 5% ต่อเนื่อง 2 นาที | 🔴 Critical | Backend Team |
| `HighApiLatency` | API Response Time สูงสุดเกิน 2 วินาที ต่อเนื่อง 3 นาที | ⚠️ Warning | Backend Team |
| `JvmHeapMemoryHigh` | JVM Heap ใช้งานเกิน 85% ต่อเนื่อง 5 นาที | ⚠️ Warning | Platform Team |
| `DatabaseConnectionPoolExhausted` | HikariCP Pending Connections > 5 ต่อเนื่อง 1 นาที | 🔴 Critical | Backend Team |

### 3.2 Business Alerts (เชิงธุรกิจ)
| Alert Name | เงื่อนไขการแจ้งเตือน | Severity | ผู้รับผิดชอบ |
|---|---|---|---|
| `LowRoomOccupancy` | อัตราเข้าพักห้อง < 60% ต่อเนื่อง 10 นาที | ⚠️ Warning | Management |
| `HighOutstandingDebt` | ยอดหนี้ค้างชำระสะสม > 100,000 บาท ต่อเนื่อง 5 นาที | ⚠️ Warning | Finance Team |
| `ManyHighPriorityMaintenanceTasks` | งานซ่อมด่วนฉุกเฉิน > 10 งาน ต่อเนื่อง 5 นาที | 🔴 Critical | Maintenance Team |
| `ManyPendingBookings` | คำขอจองห้องพักค้างรออนุมัติ > 20 รายการ ต่อเนื่อง 10 นาที | ⚠️ Warning | Management |

---

## 4. พอร์ตและ URL สำหรับเข้าใช้งานระบบ

| Service | URL | สิทธิ์ / บัญชี | วัตถุประสงค์ |
|---|---|---|---|
| **Grafana** | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` | **แดชบอร์ดหลัก รวม Metrics, Logs, Traces** |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | - | ตรวจสอบ Raw Metrics Time Series และ Alert Rules |
| **Tempo** | [http://localhost:3200](http://localhost:3200) | - | ตรวจสอบ Distributed Traces |
| **Loki** | [http://localhost:3100](http://localhost:3100) | - | Log Aggregation Engine |
| **Backend Actuator** | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) | - | Prometheus Metrics Scrape Endpoint |

---

## 5. คำสั่งเปิดใช้งาน (How to Run)

```powershell
# รันระบบทั้งหมดพร้อมชุด Observability
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d

# หรือรันเฉพาะชุด Observability (เมื่อรัน Backend แยกแบบ Local อยู่แล้ว)
docker compose -f docker-compose.observability.yml up -d
```
