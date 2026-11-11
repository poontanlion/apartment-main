# คู่มือระบบ Observability & Business Monitoring (Grafana LGTM Stack)
### อ้างอิงตามแนวทาง Class Week 13 (SpringBootObservability & Observability 101)

ระบบ Observability ของโปรเจกต์ **Apartment Management System** ได้รับการพัฒนาและออกแบบตามแนวทาง **Class Week 13: Observability 101 (Grafana Observability Stack)** โดยรวมครบทั้ง 3 เสาหลัก (**Metrics, Logs, Traces**) เข้ากับ **Single Pane of Glass บน Grafana** ตอบสนองทั้งในเชิงวิศวกรรมระบบ (Monitoring Value / RED Method) และคุณค่าทางธุรกิจจริง (Business Value Metrics)

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
                    |  - @NewSpan Multi-layer Method Spans     |
                    |  - Loki4j Logback Appender (Direct Push) |
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

## 2. การตั้งค่า Backend ตาม Class Week 13 (The Setup)

### 2.1 Dependencies (`backend/pom.xml`)
อ้างอิงตามสไลด์หน้า 3:
```xml
<!-- Spring Boot Actuator & Prometheus Metrics -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Distributed Tracing with Micrometer & OpenTelemetry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Direct Log Shipping to Grafana Loki -->
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>1.5.1</version>
</dependency>

<!-- AspectJ / AOP สำหรับ @NewSpan -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 2.2 Logback Appender (`backend/src/main/resources/logback-spring.xml`)
อ้างอิงตามสไลด์หน้า 4: ส่ง log โดยตรงเข้า Loki พร้อม label `service_name` และ pattern `[%X{traceId:-}%X{spanId:-}]`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/base.xml"/>
    <springProperty scope="context" name="application_name" source="spring.application.name" defaultValue="apartment-backend"/>
    <springProperty scope="context" name="loki_url" source="logging.loki.url" defaultValue="http://loki:3100/loki/api/v1/push"/>

    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>${loki_url}</url>
        </http>
        <labels>
            <app>${application_name}</app>
            <service_name>${application_name}</service_name>
            <host>${HOSTNAME}</host>
            <level>%level</level>
        </labels>
        <format>
            <message>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}%X{spanId:-}] %-5level %logger{36} - %msg%n</pattern>
            </message>
            <sortByTime>true</sortByTime>
        </format>
        <batchSize>100</batchSize>
        <batchTimeoutMs>100</batchTimeoutMs>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="LOKI"/>
    </root>
</configuration>
```

### 2.3 การตั้งค่า Config (`backend/src/main/resources/application.properties`)
อ้างอิงตามสไลด์หน้า 5:
```properties
spring.application.name=apartment-backend

# Metrics (Prometheus)
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.prometheus.enabled=true
management.endpoint.metrics.enabled=true
management.prometheus.metrics.export.enabled=true

# Tracing (OpenTelemetry to Grafana Tempo)
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=w3c
management.otlp.tracing.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}

# Common Tags & Structured Logging Correlation
management.observations.key-values.application=apartment-backend
logging.loki.url=${LOKI_URL:http://localhost:3100/loki/api/v1/push}
logging.pattern.correlation=[${spring.application.name:apartment-backend},%X{traceId:-},%X{spanId:-}]
logging.pattern.level=%5p [${spring.application.name:apartment-backend},%X{traceId:-},%X{spanId:-}]
```

### 2.4 Multi-layer Nested Tracing ด้วย `@NewSpan`
อ้างอิงตามสไลด์หน้า 6: เพิ่ม `@NewSpan` บน Controller และ Service เพื่อให้ได้ Trace Waterfall ซ้อนเป็นระดับชั้นใน Grafana Tempo (Slide 8-9):
- **`RoomController`**: `@NewSpan("getRoomsControllerSpan")`, `@NewSpan("getRoomByIdControllerSpan")`
- **`RoomPriceService`**: `@NewSpan("calculateRoomPriceServiceSpan")`
- **`ReportController`**: `@NewSpan("getReportSummaryControllerSpan")`
- **`ReportService`**: `@NewSpan("summarizeReportServiceSpan")`

---

## 3. การจำแนกประเภท Metrics

### 3.1 Business Value Metrics (คุณค่าในเชิงธุรกิจ)
คำนวณและดึงข้อมูลสดจากฐานข้อมูล PostgreSQL ผ่าน `BusinessMetricsCollector.java` ไปยัง Prometheus ทุก 15 วินาที:

| Metric Name | Type | ความหมายเชิงธุรกิจ / Business KPI |
|---|---|---|
| `apartment_rooms_occupancy_rate` | Gauge | **อัตราการเข้าพัก (% Occupancy Rate):** สัดส่วนห้องพักที่มีผู้เช่าจริงเทียบกับห้องทั้งหมด เป็น KPI สูงสุดของธุรกิจอพาร์ตเมนต์ |
| `apartment_rooms_occupied` | Gauge | **จำนวนห้องที่มีผู้เช่า:** ห้องสถานะ Occupied |
| `apartment_rooms_available` | Gauge | **จำนวนห้องว่างพร้อมปล่อยเช่า:** สำหรับฝ่ายการตลาดวางแผนโปรโมชัน |
| `apartment_rooms_maintenance` | Gauge | **จำนวนห้องที่ปิดปรับปรุง/ซ่อมแซม** |
| `apartment_rooms_total` | Gauge | **จำนวนห้องพักทั้งหมดในระบบ** |
| `apartment_bills_total_invoiced` | Gauge | **ยอดออกบิลเรียกเก็บรวม (Total Invoiced Revenue):** มูลค่าบิลทั้งหมดในรอบ |
| `apartment_bills_paid_amount` | Gauge | **ยอดเงินสดที่เก็บได้จริง (Cashflow Inflow):** สะท้อนสภาพคล่องทางการเงิน |
| `apartment_bills_outstanding_amount` | Gauge | **ยอดหนี้ค้างชำระสะสม (Outstanding Debt):** เงินที่ยังค้างจ่ายเพื่อติดตามหนี้ |
| `apartment_bills_unpaid_count` | Gauge | **จำนวนบิลที่ยังไม่ชำระ / ค้างชำระ:** ปริมาณลูกค้าที่มีค้างจ่าย |
| `apartment_maintenance_tasks_active` | Gauge | **งานซ่อมบำรุงที่เปิดอยู่ (Active Work Orders):** ปริมาณภาระงานของทีมช่าง |
| `apartment_maintenance_tasks_high_priority` | Gauge | **งานซ่อมด่วนฉุกเฉิน (High Priority):** งานซ่อมเร่งด่วนที่กระทบลูกบ้าน |
| `apartment_maintenance_predictions_total` | Gauge | **จำนวนคู่ห้อง-อุปกรณ์ที่ระบบ AI ตรวจพบความเสี่ยง:** Predictive Maintenance |
| `apartment_bookings_pending` | Gauge | **คำขอจองห้องพักใหม่ที่รอการอนุมัติ:** สะท้อน Demand ลูกค้าใหม่ |

### 3.2 Monitoring Value Metrics (เชิงวิศวกรรมระบบและ SRE)
รวบรวมผ่าน Spring Boot Actuator ตามหลัก **RED Method (Rate, Errors, Duration)**:

| Metric Name | Type | ประโยชน์ทางวิศวกรรมระบบ |
|---|---|---|
| `http_server_requests_seconds_count` | Counter | **Throughput (Rate):** ปริมาณคำขอ HTTP ต่อวินาที แยกตาม URI และสถานะ |
| `http_server_requests_seconds_count{status=~"5.."}` | Counter | **Error Rate (Errors):** สัดส่วนข้อผิดพลาด HTTP 5xx Server Error |
| `http_server_requests_seconds_max` / histogram | Gauge | **Duration (Latency):** วัดความเร็ว p95 และ Response Time สูงสุด |
| `jvm_memory_used_bytes{area="heap"}` | Gauge | **JVM Heap Memory:** ตรวจสอบการใช้ Heap ป้องกัน OutOfMemory |
| `hikaricp_connections_active` / `pending` | Gauge | **DB Connection Pool:** ตรวจสอบสุขภาพการเชื่อมต่อ PostgreSQL |
| `system_cpu_usage` | Gauge | **CPU Usage:** ภาระการประมวลผลของระบบ |

---

## 4. ผลลัพธ์และการตรวจสอบใน Grafana (The Result)

### 4.1 ตรวจสอบ Distributed Tracing ใน Tempo (สไลด์หน้า 7-9)
1. เข้า **Grafana** ([http://localhost:3000](http://localhost:3000)) ล็อกอิน `admin` / `admin`
2. ไปที่เมนู **Explore** $\rightarrow$ เลือก Datasource **Tempo**
3. เลือก Query Type: **Search** $\rightarrow$ Service Name: `apartment-backend` หรือคลิก Run query
4. เมื่อคลิกเลือก Trace ใดๆ จะเห็น **Waterfall Spans Tree** ซ้อนกันอย่างสมบูรณ์:

```text
Trace ID: 6fe6904853cb68a63da5b460c5eb0bf6
├── [Root Span]   http get /api/reports/summary (HTTP Server Span)
│   └── [Child 1]  get-report-summary-controller-span (Controller Layer via @NewSpan)
│       └── [Child 2]  summarize-report-service-span (Service Layer via @NewSpan)
```

### 4.2 ตรวจสอบ Trace to Log Correlation (สไลด์หน้า 10)
- **จาก Trace $\rightarrow$ Log:** ในหน้า Tempo Trace ให้คลิกที่ Span หรือปุ่ม **Logs for this span / Trace to logs** ระบบจะเปิด Loki ขึ้นมาและกรองเอาเฉพาะ Log lines ที่มี `traceId` นั้นทันที!
- **จาก Log $\rightarrow$ Trace:** ในหน้า Loki เมื่อค้นหา `{service_name=~".*backend.*"}` จะพบบรรทัด Log พร้อมปุ่มสีฟ้า **TraceID** คลิกเพื่อเปิดหน้า Tempo Trace ได้ทันทีในคลิกเดียว!

### 4.3 ตรวจสอบ Dashboards (สไลด์หน้า 11)
เข้าเมนู **Dashboards** $\rightarrow$ โฟลเดอร์ **Apartment System**:

1. **`Victory Apartment - Full Observability & Business Value` (apartment-obs-01):**
   - **แถว 1 (Business KPIs):** Room Occupancy Rate (%), Room Inventory, Cashflow vs Debt, Operation Tasks
   - **แถว 2 (System Monitoring):** HTTP Throughput, API Latency, JVM Memory
   - **แถว 3 (Live Logs):** Live Log Panel จาก Loki พร้อม Correlation Links

2. **`Victory Apartment - Alerting & Trends` (apartment-obs-02):**
   - **Active Alerts Status:** สถานะแจ้งเตือน 6 จุด (System & Business)
   - **Business Trends Over Time:** กราฟแนวโน้มอัตราการเข้าพักเทียบเป้าหมาย (50%, 80%), กราฟกระแสเงินสดเทียบยอดหนี้
   - **SRE RED Method:** Rate (RPS), Errors (5xx error rate threshold 5%), Duration (Top 5 slowest endpoints p95)

---

## 5. Alerting Rules

ไฟล์กฎการแจ้งเตือนอยู่ที่ [`observability/prometheus/alert-rules.yaml`](observability/prometheus/alert-rules.yaml):

### 5.1 Monitoring Alerts
| Alert Name | เงื่อนไข | Severity | ผู้รับผิดชอบ |
|---|---|---|---|
| `HighHttpErrorRate` | HTTP 5xx Error Rate > 5% ต่อเนื่อง 2 นาที | 🔴 Critical | Backend Team |
| `HighApiLatency` | API Response Time สูงสุดเกิน 2 วินาที ต่อเนื่อง 3 นาที | ⚠️ Warning | Backend Team |
| `JvmHeapMemoryHigh` | JVM Heap ใช้งานเกิน 85% ต่อเนื่อง 5 นาที | ⚠️ Warning | Platform Team |
| `DatabaseConnectionPoolExhausted` | HikariCP Pending Connections > 5 ต่อเนื่อง 1 นาที | 🔴 Critical | Backend Team |

### 5.2 Business Alerts
| Alert Name | เงื่อนไข | Severity | ผู้รับผิดชอบ |
|---|---|---|---|
| `LowRoomOccupancy` | อัตราเข้าพัก < 60% ต่อเนื่อง 10 นาที | ⚠️ Warning | Management |
| `HighOutstandingDebt` | ยอดหนี้ค้างชำระ > 100,000 บาท ต่อเนื่อง 5 นาที | ⚠️ Warning | Finance Team |
| `ManyHighPriorityMaintenanceTasks` | งานซ่อมด่วนฉุกเฉิน > 10 งาน ต่อเนื่อง 5 นาที | 🔴 Critical | Maintenance Team |
| `ManyPendingBookings` | คำขอจองห้องค้างรออนุมัติ > 20 รายการ ต่อเนื่อง 10 นาที | ⚠️ Warning | Management |

---

## 6. พอร์ตและ URL สำหรับเข้าใช้งาน

| Service | URL | สิทธิ์ / บัญชี | วัตถุประสงค์ |
|---|---|---|---|
| **Grafana** | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` | **แดชบอร์ดหลัก (Metrics, Logs, Traces)** |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | - | ตรวจสอบ Raw Time Series และ Alerts |
| **Tempo** | [http://localhost:3200](http://localhost:3200) | - | ตรวจสอบ Distributed Traces |
| **Loki** | [http://localhost:3100](http://localhost:3100) | - | Log Aggregation Engine |
| **Backend Actuator** | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) | - | Prometheus Scrape Endpoint |

---

## 7. คำสั่งเปิดใช้งาน (How to Run)

```powershell
# รันระบบทั้งหมดพร้อมชุด Observability
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d

# หรือรันเฉพาะชุด Observability (เมื่อรัน Backend แบบ Local อยู่แล้ว)
docker compose -f docker-compose.observability.yml up -d
```
