# คู่มือระบบ Observability & Business Monitoring (Grafana LGTM Stack)

ระบบ Observability ของโปรเจกต์ **Apartment Management System** ได้รับการออกแบบตามแนวทาง **Observability 101 (Grafana Observability Stack)** เพื่อรวมข้อมูลทั้ง 3 เสาหลัก (**Metrics, Logs, Traces**) เข้ากับ **Single Pane of Glass บน Grafana** พร้อมตอบสนองความต้องการทั้งในเชิงวิศวกรรมระบบ (Monitoring Value) และคุณค่าทางธุรกิจ (Business Value)

---

## 1. สถาปัตยกรรมระบบ (Architecture)

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
                    |  - Structured Logging with Trace & Span  |
                    +--------+----------------+----------------+
                             |                |
             Metrics (Pull)  |                | Traces (Push OTLP)
                    +--------v-------+        |
                    |   Prometheus   |        |
                    |  Port: 9090    |        v
                    +--------+-------+  +-----------+         +------------------+
                             |          |   Tempo   |         | Docker Container |
                             |          | Port:3200 |         |       Logs       |
                             |          +-----+-----+         +--------+---------+
                             |                |                        |
                             |                |                        v
                             |                |               +------------------+
                             |                |               |     Promtail     |
                             |                |               +--------+---------+
                             |                |                        | Push Logs
                             |                |                        v
                             |                |               +------------------+
                             |                |               |    Grafana Loki  |
                             |                |               |    Port: 3100    |
                             |                |               +--------+---------+
                             |                |                        |
                             +----------------+------------------------+
                                              |
                                              v
                              +-------------------------------+
                              |       Grafana Dashboard       |
                              |   Port: 3000 (admin/admin)    |
                              +-------------------------------+
```

---

## 2. การจำแนกประเภท Metrics

### 2.1 Business Value Metrics (คุณค่าในเชิงธุรกิจ)
คำนวณและส่งออกจาก `BusinessMetricsCollector.java` สู่ Prometheus อัตโนมัติ:

| Metric Name | Type | รายละเอียด / ความหมายเชิงธุรกิจ |
|---|---|---|
| `apartment_rooms_occupancy_rate` | Gauge | **อัตราการเข้าพัก (% Occupancy):** สัดส่วนห้องที่มีผู้เช่าเทียบกับห้องทั้งหมด เป็น KPI สูงสุดของธุรกิจอพาร์ตเมนต์ |
| `apartment_rooms_occupied` | Gauge | **จำนวนห้องที่มีผู้เช่า:** ห้องสถานะ Occupied |
| `apartment_rooms_available` | Gauge | **จำนวนห้องว่างพร้อมปล่อยเช่า:** ห้องสถานะ Available สำหรับฝ่ายการตลาด |
| `apartment_rooms_maintenance` | Gauge | **จำนวนห้องที่ปิดซ่อมแซม:** ไม่พร้อมให้บริการ |
| `apartment_rooms_total` | Gauge | **จำนวนห้องทั้งหมดในอาคาร** |
| `apartment_bills_total_invoiced` | Gauge | **ยอดออกบิลเรียกเก็บรวม (Total Invoiced Revenue):** มูลค่าบิลทั้งหมดในรอบเดือน |
| `apartment_bills_paid_amount` | Gauge | **ยอดเงินสดที่เก็บได้จริง (Cashflow Inflow):** ประเมินสภาพคล่องการชำระเงินของลูกบ้าน |
| `apartment_bills_outstanding_amount` | Gauge | **ยอดหนี้ค้างชำระสะสม (Outstanding Debt):** เงินที่ยังไม่จ่ายเพื่อติดตามทวงหนี้ |
| `apartment_bills_unpaid_count` | Gauge | **จำนวนบิลที่ยังไม่ชำระ / ค้างชำระ:** ปริมาณลูกค้าที่มีสถานะค้างจ่าย |
| `apartment_maintenance_tasks_active` | Gauge | **งานซ่อมบำรุงที่เปิดอยู่ (Active Work Orders):** ปริมาณงานที่ช่างต้องเข้าดำเนินการ |
| `apartment_maintenance_tasks_high_priority` | Gauge | **งานซ่อมด่วนฉุกเฉิน (High Priority):** งานซ่อมเร่งด่วนที่กระทบชีวิตความเป็นอยู่ |
| `apartment_maintenance_predictions_total` | Gauge | **จำนวนอุปกรณ์ที่ AI/Predictor พยากรณ์พบความเสี่ยง:** Predictive Maintenance |
| `apartment_bookings_pending` | Gauge | **คำขอจองห้องพักใหม่ที่รอ Admin อนุมัติ:** สะท้อน Demand ลูกค้าใหม่ |

### 2.2 Monitoring Value Metrics (เชิงวิศวกรรมระบบและ SRE)
รวบรวมผ่าน Spring Boot Actuator & JVM Instruments ตามหลัก RED Method (Rate, Errors, Duration):

| Metric Name | Type | รายละเอียด / ประโยชน์ทางวิศวกรรม |
|---|---|---|
| `http_server_requests_seconds_count` | Counter | **API Throughput (RPS):** ปริมาณคำขอต่อวินาที แยกตาม URI และ HTTP status |
| `http_server_requests_seconds_max` | Gauge | **API Latency (Response Time):** วัดความเร็วในการประมวลผล เพื่อจับ Endpoint ที่ช้า |
| `http_server_requests_seconds_count{status=~"5.."}` | Counter | **Error Rate:** จำนวนข้อผิดพลาดระดับ Server Error (500 Internal Error) |
| `jvm_memory_used_bytes{area="heap"}` | Gauge | **JVM Heap Memory Usage:** การใช้งานหน่วยความจำของ Java ป้องกัน OutOfMemory |
| `hikaricp_connections_active` / `pending` | Gauge | **Database Connection Pool:** สุขภาพการเชื่อมต่อ PostgreSQL |
| `system_cpu_usage` | Gauge | **CPU Utilization:** สัดส่วนการใช้ CPU ของ Container |

---

## 3. ขั้นตอนการเปิดใช้งาน (How to Run)

### วิธีที่ 1: รันผ่าน Docker Compose ครบทั้งระบบ (แนะนำ)

สั่งรันทั้งแอปพลิเคชันและชุด Observability พร้อมกัน:
```powershell
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

### วิธีที่ 2: รัน Observability แยกต่างหากเมื่อรัน Backend แบบ Local

หากคุณรัน Backend ผ่าน IDE หรือคำสั่ง `mvnw spring-boot:run` อยู่แล้ว:
```powershell
docker compose -f docker-compose.observability.yml up -d
```
> ระบบ Prometheus ได้รับการตั้งค่าให้ scrape ข้อมูลจากทั้ง `backend:8080` (Docker) และ `host.docker.internal:8080` (Local Host) อัตโนมัติ

---

## 4. พอร์ตและ URL สำหรับเข้าใช้งาน

| Service | URL | ข้อมูลล็อกอิน | วัตถุประสงค์ |
|---|---|---|---|
| **Grafana** | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` | **แดชบอร์ดหลัก รวม Metrics, Logs, Traces** |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | - | ตรวจสอบ Raw Time Series Metrics และ Target Scrapes |
| **Tempo** | [http://localhost:3200](http://localhost:3200) | - | ตรวจสอบ Distributed Traces |
| **Loki** | [http://localhost:3100](http://localhost:3100) | - | Log Aggregator Engine |
| **Backend Actuator** | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) | - | Prometheus Metrics Exporter Endpoint |

---

## 5. การตรวจสอบแดชบอร์ดใน Grafana (Dashboard Walkthrough)

เมื่อเปิดเข้า [http://localhost:3000](http://localhost:3000):
1. ล็อกอินด้วย `admin` / `admin`
2. ไปที่เมนู **Dashboards** $\rightarrow$ โฟลเดอร์ **Apartment System**
3. เลือก **`Victory Apartment - Full Observability & Business Value`**

คุณจะพบกับ:
1. **แถวบน (Business Value):**
   - **Room Occupancy Rate (%)**: Gauge เกจวัดระดับความจุห้อง (สีเขียว >80%, เหลือง 60-80%, แดง <60%)
   - **Room Inventory Status**: แสดงจำนวนห้องว่าง, ห้องที่มีคนพัก, และห้องปิดซ่อม
   - **Financial Stats**: ยอดเรียกเก็บรวม vs เงินสดที่ได้รับ และยอดหนี้ค้างชำระ
   - **Operations**: งานซ่อมค้าง, งานซ่อมด่วน, และคำขอจองห้อง
2. **แถวกลาง (System Monitoring):**
   - **HTTP Throughput**: กราฟแสดง Requests/Sec แยกตามรหัสสถานะ HTTP 200, 400, 500
   - **API Latency**: Response Time สูงสุดของแต่ละ API
   - **JVM Heap Memory**: กราฟการจองและใช้งาน Heap Memory
3. **แถวล่าง (Logs & Distributed Tracing Correlation):**
   - กล่องข้อความ Log ที่ส่งมาจากแอปพลิเคชันผ่าน Loki
   - แต่ละบรรทัด Log จะมี `traceId` (เช่น `663c7b2a9d...`) ซึ่งสามารถ**คลิกที่ปุ่ม TraceID เพื่อเปิดดู Distributed Trace ใน Tempo** ได้ทันทีโดยไม่ต้องค้นหาเอง!


---

## 4. Alerting Rules

Alerting Rules ถูกกำหนดไว้ใน [`observability/prometheus/alert-rules.yaml`](observability/prometheus/alert-rules.yaml) แบ่งเป็น 2 กลุ่ม:

### Monitoring Alerts (เชิงวิศวกรรม)

| Alert Name | เงื่อนไข | Severity | ผู้รับผิดชอบ |
|---|---|---|---|
| `HighHttpErrorRate` | HTTP 5xx Error Rate > 5% ต่อเนื่อง 2 นาที | 🔴 Critical | Backend Team |
| `HighApiLatency` | API Response Time สูงสุดเกิน 2 วินาที ต่อเนื่อง 3 นาที | ⚠️ Warning | Backend Team |
| `JvmHeapMemoryHigh` | JVM Heap ใช้งานเกิน 85% ต่อเนื่อง 5 นาที | ⚠️ Warning | Platform Team |
| `DatabaseConnectionPoolExhausted` | HikariCP Pending Connections > 5 ต่อเนื่อง 1 นาที | 🔴 Critical | Backend Team |

### Business Alerts (เชิงธุรกิจ)

| Alert Name | เงื่อนไข | Severity | ผู้รับผิดชอบ |
|---|---|---|---|
| `LowRoomOccupancy` | Occupancy Rate < 60% ต่อเนื่อง 10 นาที | ⚠️ Warning | Management |
| `HighOutstandingDebt` | ยอดหนี้ค้างชำระ > 100,000 บาท ต่อเนื่อง 5 นาที | ⚠️ Warning | Finance Team |
| `ManyHighPriorityMaintenanceTasks` | งานซ่อมด่วน High Priority > 10 งาน ต่อเนื่อง 5 นาที | 🔴 Critical | Maintenance |
| `ManyPendingBookings` | คำขอจองรอ Approve > 20 รายการ ต่อเนื่อง 10 นาที | ⚠️ Warning | Management |
