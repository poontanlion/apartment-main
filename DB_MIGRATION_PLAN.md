# แผน Migrate H2 → PostgreSQL ทั้งโปรเจกต์ (แบ่งงาน 2 คน)

> Branch หลักของงาน: `Migration-database` · Spring Boot 3.2.5 · Java 17 · Hibernate 6.4 · Flyway 9.22 (Boot จัดการ version ให้)

---

## 0. ภาพรวม

### ทำไมต้องย้าย
- ตอนนี้ใช้ H2 แบบไฟล์ (`jdbc:h2:file:./data/apartmentdb`) และ `ddl-auto=update` ให้ Hibernate แก้ schema เองทุกครั้งที่ start ซึ่งควบคุมไม่ได้และย้อนกลับไม่ได้
- บน k8s ชี้ไป `/tmp/apartmentdb` ใน container ข้อมูลเลย**หายทุกครั้งที่ pod restart หรือ deploy ใหม่**
- H2 console เปิดอยู่ (`web-allow-others=true`) เป็นช่องโหว่ด้านความปลอดภัย

### เป้าหมายสุดท้าย
1. ทุก environment (local, CI, k8s) ใช้ PostgreSQL 16
2. Flyway เป็นคนคุม schema ส่วน Hibernate แค่ `validate`
3. เทสที่ใช้ DB รันกับ Postgres จริงผ่าน Testcontainers
4. ไม่มีร่องรอย H2 เหลือในโค้ด, config, Docker, k8s และ CI
5. ข้อมูลบน k8s ไม่หายเมื่อ pod restart และไม่มี password อยู่ใน git

### สิ่งที่สำรวจเจอในโค้ด (ใช้ประกอบการตัดสินใจ)
| เรื่อง | สิ่งที่เจอ | ผลต่อการ migrate |
|---|---|---|
| Entity | 13 ตัวใน `backend/src/main/java/com/victory/apartment/model/` | ต้องเขียน DDL 13 ตาราง |
| Primary key | ทุกตัวเป็น `String id` ที่แอปสร้างเอง ไม่มี `@GeneratedValue` | ไม่ต้องทำ sequence/identity |
| Query | ส่วนใหญ่เป็น derived query, มี JPQL 2 ตัวที่ใช้ `PESSIMISTIC_WRITE` (`RoomRepository.findByIdForUpdate`, `BookingRepository.findByIdForUpdate`) | Postgres ใช้ `SELECT ... FOR UPDATE` ได้ ไม่ต้องแก้ แต่ควรมีเทส |
| Native SQL | ไม่มีเลย | ความเสี่ยงเรื่อง dialect ต่ำ |
| `IgnoreCase` | `findByGuestEmailIgnoreCase...`, `findByCodeIgnoreCase`, `findFirstByEmailIgnoreCase` ฯลฯ | Hibernate แปลงเป็น `upper()` ให้ ใช้ได้เลย |
| Ordering | `OrderByRoomNumberAsc`, `OrderByCodeAsc`, `OrderByDateDesc` (เป็น String) | ลำดับ string อาจต่างจาก H2 ตาม collation ต้องเทส |
| Type | `String`, `Integer`, `Double`, `Boolean`, `LocalDate`, `LocalDateTime` | map ตรงๆ ได้ (ดูข้อ B2) |
| คอลัมน์ที่อาจยาวเกิน 255 | `Room.coverImage`, `Room.gallery` (JSON), `Room.amenities` (JSON), `Room.description`, `Booking.specialRequests`, `Lease.notes`, `MaintenanceLog.suppliesSummary`, `UtilityBill.slipImage` | ต้องเปลี่ยนเป็น `text` (ดูข้อ B1) |
| ชื่อตาราง | `users`, `rooms`, `bookings`, … | ไม่มีชื่อไหนเป็น reserved word ใน Postgres |
| **DataSeeder** | commit 27abfd57 ใส่ `@Profile("!prod")` แต่ **commit 94b4355d ทำให้มันหายไป** ตอนนี้ seeder รันทุก profile และสร้าง admin ด้วย | ต้องแยก seed admin กับ seed ข้อมูล demo (ดูข้อ B5) |
| เทส | `@SpringBootTest` 3 ไฟล์ (`ApplicationTests`, `ActuatorHealthTest`, `BookingLeaseIntegrationTest`) ใช้ DB จริง, ที่เหลือเป็น `@WebMvcTest` + `@MockBean` ไม่แตะ DB | ต้องแก้แค่ 3 ไฟล์ |
| `BookingLeaseIntegrationTest` | hardcode `jdbc:h2:mem:...` และ `ddl-auto=create-drop` | ต้องลบ property พวกนี้ออก |
| `ActuatorHealthTest` | assert `$.components.db.status == UP` | ใช้เป็นเทสยืนยันว่าต่อ Postgres ได้ |
| CI | `.github/workflows/backend-all-tests.yml` รัน `./mvnw test` บน `ubuntu-latest` | runner มี Docker อยู่แล้ว Testcontainers ใช้ได้ |
| Deploy | `.github/workflows/deploy-k8s.yml` apply `k8s/configmap.yaml` + `k8s/deployment.yaml` | ต้องเพิ่มขั้น Postgres + Secret |
| Dockerfile | สร้าง `/app/data` ไว้ให้ H2 | ลบทิ้งได้ |
| `.gitignore` | มี `*.mv.db`, `backend/data/` อยู่แล้ว | ไม่ต้องเพิ่ม |

---

## 1. แบ่งงาน (สรุป)

| | **คน A: Infrastructure & DevOps** | **คน B: Schema, Data & Testing** |
|---|---|---|
| ความรับผิดชอบ | dependency, config, Docker, k8s, CI/CD, เอกสาร | DDL/Flyway, แก้ entity, DataSeeder, ย้ายข้อมูล, เทส |
| ไฟล์ที่เป็นเจ้าของ | `backend/pom.xml`, `application*.properties`, `docker-compose.yml`, `.env.example`, `backend/Dockerfile`, `k8s/*`, `.github/workflows/*`, `README.md` | `backend/src/main/resources/db/migration/*`, `model/*.java`, `config/DataSeeder.java`, `backend/src/test/**`, `test_documentation.md` |
| Branch | `db/infra` | `db/schema` |
| งานปิดท้าย | A8: merge ทั้งสอง branch, ตรวจ k8s/CI/Cleanup, เปิด PR เข้า `main` | B7: ตรวจ Local/Test ตาม checklist, review PR ของ A |

**ไม่มีงานที่ต้องทำด้วยกัน** ทุกข้อมีเจ้าของคนเดียว การส่งต่องานทำผ่าน push/PR และแจ้งในแชทเท่านั้น

**กติกา:** ห้ามแก้ไฟล์ของอีกฝั่ง ถ้าจำเป็นให้บอกเจ้าของไฟล์ (เช่น B อยากได้ dependency เพิ่มให้บอก A) จะได้แทบไม่มี merge conflict

---

## 2. ค่าที่ใช้ร่วมกัน (กำหนดไว้แล้ว ไม่ต้องประชุม)

ทั้งสองคนใช้ค่าตามตารางนี้ได้เลย ถ้าใครอยากเปลี่ยนค่าไหนให้แจ้งอีกคนในแชท

| หัวข้อ | ค่าที่ใช้ |
|---|---|
| Postgres image | `postgres:16-alpine` (ใช้ตัวเดียวกันทั้ง compose, k8s, Testcontainers) |
| DB name / user | `apartmentdb` / `apartment` |
| Env vars ฝั่งแอป | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Env vars ฝั่ง container postgres | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Hostname ของ DB | compose: `db` · k8s: `postgres` (ชื่อ Service) |
| ชื่อไฟล์ migration | `V{n}__{snake_case}.sql` เริ่มที่ `V1__init_schema.sql` และ**ห้ามแก้ไฟล์ที่ merge แล้ว** ให้เพิ่มเป็น V ใหม่แทน |
| คอลัมน์ text | ตามรายการในข้อ B1 |
| ข้อมูลเก่าใน H2 | บน k8s อยู่ใน `/tmp` ซึ่งหายทุก deploy อยู่แล้ว **สรุปคือไม่มีข้อมูล production ให้ย้าย** ส่วนข้อมูล local ใน `backend/data/` ย้ายก็ได้ไม่ย้ายก็ได้ (ข้อ B6) |
| เรื่อง DataSeeder ใน prod | B เป็นคนตัดสิน (ข้อ B5) แล้วแจ้งชื่อ env ของ admin ให้ A ไปใส่ใน secret |

---

## 3. คน A: Infrastructure & DevOps (ละเอียด)

### A1. `backend/pom.xml` (วันที่ 1 เช้า แล้ว merge เข้า `Migration-database` ทันที)
```xml
<!-- ลบ -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- เพิ่ม: driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- เพิ่ม: migration (Boot 3.2 ใช้ Flyway 9 ไม่ต้องใส่ flyway-database-postgresql) -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<!-- เพิ่ม: test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```
- ไม่ต้องระบุ version เพราะ `spring-boot-starter-parent` 3.2.5 จัดการให้
- หลัง merge ข้อนี้ เทสที่ใช้ DB จะพังจนกว่า B จะทำ B4 เสร็จ ให้แจ้งทีมไว้ (หรือ merge A1 พร้อม B4 ก็ได้)

### A2. `backend/src/main/resources/application.properties`
แทนที่ block DB และ H2 ทั้งหมดด้วย:
```properties
# PostgreSQL (ค่า default ใช้สำหรับ local dev เท่านั้น บน prod ต้อง override ด้วย env)
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/apartmentdb}
spring.datasource.username=${DB_USERNAME:apartment}
spring.datasource.password=${DB_PASSWORD:apartment}
spring.datasource.hikari.maximum-pool-size=10

# JPA: schema ถูกคุมโดย Flyway ส่วน Hibernate แค่ตรวจ
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.show-sql=false

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```
- ลบ `spring.jpa.database-platform=org.hibernate.dialect.H2Dialect` (Hibernate 6 detect dialect เองได้)
- ลบ `spring.h2.console.*` ทั้ง 3 บรรทัด
- ส่วน actuator และ admin เก็บไว้เหมือนเดิม
- `open-in-view=false`: ก่อนเปิดใช้ให้เช็กกับ B ว่าไม่มี lazy loading นอก transaction (entity ไม่มี relation เลย น่าจะปลอดภัย)

### A3. `docker-compose.yml` และ `.env.example`
```yaml
services:
  db:
    image: postgres:16-alpine
    container_name: apartment-db
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-apartmentdb}
      POSTGRES_USER: ${POSTGRES_USER:-apartment}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-apartment}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10
    restart: unless-stopped

  backend:
    build: { context: ./backend, dockerfile: Dockerfile }
    container_name: apartment-backend
    environment:
      DB_URL: jdbc:postgresql://db:5432/${POSTGRES_DB:-apartmentdb}
      DB_USERNAME: ${POSTGRES_USER:-apartment}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-apartment}
    depends_on:
      db:
        condition: service_healthy
    ports: ["8080:8080"]
    restart: unless-stopped

  frontend: # เหมือนเดิม

volumes:
  pgdata:
```
- สร้าง `.env.example` (commit ได้) ที่มี `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` แต่ไม่ใส่ค่าจริง ส่วน `.env` ถูก ignore อยู่แล้ว
- เช็กว่า `docker compose config --quiet` ผ่าน (CI `docker-build.yml` รันคำสั่งนี้)

### A4. `backend/Dockerfile`
- ลบ `&& mkdir -p /app/data` (ส่วน `chown -R appuser:appgroup /app` เก็บไว้)

### A5. Kubernetes (`k8s/`)
1. **ไฟล์ใหม่ `k8s/postgres.yaml`**
   - `Service` ชื่อ `postgres` (ClusterIP, port 5432)
   - `StatefulSet` ชื่อ `postgres`, `replicas: 1`, image `postgres:16-alpine`
     - env `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` ดึงจาก secret `apartment-db-secret`
     - env `PGDATA=/var/lib/postgresql/data/pgdata` (กัน error เรื่อง lost+found บน PV)
     - `volumeClaimTemplates` ขนาด 1–5Gi (ต้องเช็ก StorageClass ของ cluster ว่ามี default ไหม)
     - readiness/liveness ใช้ `exec: pg_isready`
     - resources: requests 256Mi/250m, limits 512Mi/500m
   - (ทางเลือก) ถ้าใช้ managed Postgres (RDS / Cloud SQL / Neon) ให้ข้ามไฟล์นี้ แล้วแค่ชี้ `DB_URL` ไปหา
2. **`k8s/configmap.yaml`**
   - ลบ `SPRING_H2_CONSOLE_ENABLED` และ `SPRING_DATASOURCE_URL`
   - เพิ่ม `DB_URL: "jdbc:postgresql://postgres:5432/apartmentdb"`
3. **Secret (ห้าม commit ค่าจริง)**
   - เพิ่ม `k8s/secret.example.yaml` เป็นตัวอย่างไว้ให้ดู
   - ของจริงให้ CI สร้างจาก GitHub Secrets (ดู A6)
4. **`k8s/deployment.yaml`** (เฉพาะ backend)
   ```yaml
   envFrom:
   - configMapRef: { name: apartment-backend-config }
   - secretRef:    { name: apartment-db-secret }   # มี DB_USERNAME, DB_PASSWORD
   ```
   - ถ้าต่อ DB ไม่ได้ Flyway จะ fail แล้ว pod restart เอง ถ้าอยากให้เรียบร้อยขึ้นให้เพิ่ม `initContainer` ที่ใช้ `postgres:16-alpine` รัน `until pg_isready -h postgres; do sleep 2; done`
   - `readinessProbe.initialDelaySeconds` อาจต้องเพิ่ม เพราะรอบแรก Flyway ใช้เวลาสร้าง schema

### A6. CI/CD (`.github/workflows/`)
1. **`deploy-k8s.yml`**: เพิ่มขั้นตอนก่อน "Apply ConfigMap"
   ```yaml
   - name: Create/Update DB Secret
     env:
       DB_USERNAME: ${{ secrets.DB_USERNAME }}
       DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
     run: |
       kubectl create secret generic apartment-db-secret \
         --from-literal=DB_USERNAME="$DB_USERNAME" \
         --from-literal=DB_PASSWORD="$DB_PASSWORD" \
         --from-literal=POSTGRES_USER="$DB_USERNAME" \
         --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
         --from-literal=POSTGRES_DB=apartmentdb \
         --dry-run=client -o yaml | kubectl apply -f -
   - name: Apply PostgreSQL
     run: |
       kubectl apply -f k8s/postgres.yaml
       kubectl rollout status statefulset/postgres --timeout=180s
   ```
   - ไปเพิ่ม `DB_USERNAME`, `DB_PASSWORD` ใน GitHub repo → Settings → Secrets
2. **`backend-all-tests.yml`**: ไม่ต้องแก้ (Testcontainers ใช้ Docker ที่มีใน `ubuntu-latest`) แต่ควรเช็กว่าเวลารันไม่เกิน timeout
3. **`backend-unit-tests.yml`**: เปิดดูว่ามันรันเทสชุดไหน ถ้ารวม `@SpringBootTest` ด้วยก็จะต้องมี Docker เหมือนกัน
4. **`docker-build.yml`**: ไม่ต้องแก้ แค่เช็กว่า `docker compose config` ผ่าน

### A7. เอกสาร
- `README.md`: แก้วิธีรัน local
  - รันทั้งระบบ: `docker compose up -d`
  - รันเฉพาะ DB แล้วรัน backend จาก IDE: `docker compose up -d db` ตามด้วย `cd backend && ./mvnw spring-boot:run`
  - ต่อ DB ด้วย psql/DBeaver: `localhost:5432` user `apartment`
  - ลบส่วนที่พูดถึง H2 console
- เขียนวิธี backup/restore ด้วย `pg_dump` / `pg_restore` บน k8s (`kubectl exec statefulset/postgres -- pg_dump ...`)

---

### A8. รวมงานและตรวจฝั่ง infra (วันที่ 5)
1. review PR `db/schema` ของ B (คนเดียว ไม่ต้องนั่งดูด้วยกัน) แล้วคอมเมนต์ใน PR
2. merge `db/infra` และ `db/schema` เข้า `Migration-database` ถ้ามี conflict ให้แก้เอง เพราะตามกติกาไฟล์ไม่ทับกันอยู่แล้ว
3. deploy ขึ้น cluster แล้วไล่ checklist หมวด **Kubernetes**, **CI** และ **Cleanup** ในข้อ 7
4. เปิด PR `Migration-database` → `main` แล้วแปะผล checklist ของตัวเองกับของ B ลงใน description

---

## 4. คน B: Schema, Data & Testing (ละเอียด)

### B1. แก้ entity ให้ตรงกับ schema ใหม่ (วันที่ 1)
คอลัมน์ที่อาจยาวเกิน 255 ตัวอักษร (URL รูป, JSON, ข้อความอิสระ) ให้ใส่ `@Column(columnDefinition = "text")`:

| Entity | Field | เหตุผล |
|---|---|---|
| `Room` | `description`, `amenities`, `coverImage`, `gallery` | JSON และ URL รูป |
| `Booking` | `specialRequests` | ข้อความอิสระ |
| `Lease` | `notes` | ข้อความอิสระ |
| `MaintenanceLog` | `suppliesSummary` | ข้อความสรุป |
| `UtilityBill` | `slipImage` | อาจเป็น URL ยาวหรือ base64 (ไปเช็กหน้า upload สลิปใน frontend) |

- ส่วนที่มี `@Column(length = 1000/2000)` อยู่แล้ว (`Building.description/coverImage/address`, `MaintenanceTask.description/suppliesUsed`, `ActivityLog.details`, `AppNotification.message`) คงเป็น `varchar(n)` ตามเดิม
- **ยังไม่**เปลี่ยน `Double` เป็น `BigDecimal` ในรอบนี้ เพราะต้องแก้ service/frontend ด้วย ให้เปิดเป็น ticket แยก
- **ยังไม่**เปลี่ยนฟิลด์วันที่ที่เป็น String (`Booking.checkIn/checkOut`, `MaintenanceTask.reportedDate/dueDate`, `UtilityBill.paymentDate`, `ScheduledReminder.nextDueDate`, `MaintenanceLog.date`) เป็น `date` เพราะกระทบ API ให้เปิด ticket แยกเช่นกัน

### B2. สร้าง `V1__init_schema.sql` (วันที่ 1–2)
**ขั้นที่ 1: ให้ Hibernate dump DDL ออกมา** (ทำบน Postgres local ที่ A เตรียมไว้ หรือ `docker run -d -p 5432:5432 -e POSTGRES_USER=apartment -e POSTGRES_PASSWORD=apartment -e POSTGRES_DB=apartmentdb postgres:16-alpine`)
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
 --spring.flyway.enabled=false \
 --spring.jpa.hibernate.ddl-auto=none \
 --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
 --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/schema.sql"
```
ได้ไฟล์ `target/schema.sql` มาแล้วค่อยเอามาเกลา

**ขั้นที่ 2: เกลาแล้วบันทึกเป็น** `backend/src/main/resources/db/migration/V1__init_schema.sql`

ตาราง mapping type (Hibernate 6.4 + PostgreSQL):
| Java | PostgreSQL |
|---|---|
| `String` (ไม่ระบุ length) | `varchar(255)` |
| `String` + `length = n` | `varchar(n)` |
| `String` + `columnDefinition = "text"` | `text` |
| `Integer` | `integer` |
| `Double` | `float(53)` / `double precision` |
| `Boolean` | `boolean` |
| `LocalDate` | `date` |
| `LocalDateTime` | `timestamp(6)` |

ตัวอย่าง:
```sql
CREATE TABLE rooms (
    id                   varchar(255) PRIMARY KEY,
    room_number          varchar(255) NOT NULL,
    floor                integer      NOT NULL,
    room_name            varchar(255),
    room_type            varchar(255),
    description          text,
    capacity             integer,
    price                double precision,
    status               varchar(255),
    amenities            text,
    size_sqm             double precision,
    bed_type             varchar(255),
    cover_image          text,
    gallery              text,
    current_tenant_id    varchar(255),
    current_tenant_name  varchar(255),
    building_id          varchar(255),
    building_name        varchar(255),
    prev_water_meter     double precision,
    curr_water_meter     double precision,
    prev_electric_meter  double precision,
    curr_electric_meter  double precision,
    created_at           timestamp(6),
    CONSTRAINT uk_rooms_room_number UNIQUE (room_number)
);
```
Checklist ระหว่างเกลา:
- [ ] ครบ 13 ตาราง: `users`, `tenants`, `buildings`, `rooms`, `leases`, `bookings`, `utility_bills`, `maintenance_tasks`, `maintenance_logs`, `supply_items`, `scheduled_reminders`, `app_notifications`, `activity_logs`
- [ ] ชื่อ unique constraint อ่านรู้เรื่อง (`uk_<table>_<column>`) แทนชื่อสุ่มของ Hibernate ครบทั้ง 5 ตัว: `users.email`, `rooms.room_number`, `bookings.booking_no`, `maintenance_tasks.task_no`, `utility_bills.invoice_no`
- [ ] `NOT NULL` ตรงกับ `@Column(nullable = false)` ทุกตัว
- [ ] เพิ่ม index ให้คอลัมน์ที่ repository ใช้ค้นหาหรือเรียงบ่อย:
  - `rooms(building_id)`, `rooms(status)`
  - `bookings(room_id, status)`, `bookings(created_at)`
  - `leases(room_id, status)`
  - `maintenance_logs(room_id)`, `maintenance_logs(task_no)`
  - `maintenance_tasks(room_number)`
  - `activity_logs(created_at)`
  - ส่วนที่ค้นแบบ IgnoreCase (`bookings.guest_email`, `tenants.email`, `buildings.code`) ทำเป็น index `upper(...)` ทีหลังได้ถ้าข้อมูลเยอะ
- [ ] **ไม่ใส่ foreign key** ในรอบนี้ เพราะ entity เก็บ ID เป็น String ลอยๆ และ DataSeeder/โค้ดอาจลบหรือสร้างข้อมูลไม่เรียงลำดับ ให้เปิด ticket แยก

**ขั้นที่ 3: ทดสอบ** ลบ DB ทิ้ง (`docker compose down -v`) แล้วรันแอปด้วย config ของ A ต้องเห็น `Successfully applied 1 migration` และ Hibernate `validate` ต้องผ่าน

### B3. ตรวจ query กับ Postgres (วันที่ 2–3)
| Query | สิ่งที่ต้องเช็ก |
|---|---|
| `RoomRepository.findByIdForUpdate`, `BookingRepository.findByIdForUpdate` | lock ทำงานจริง ลองเขียนเทสที่ approve booking เดียวกันพร้อมกัน 2 thread (ดู `BookingLeaseService`) |
| `findAllByOrderByRoomNumberAsc`, `findByBuildingIdOrderByRoomNumberAsc` | ลำดับเลขห้องอย่าง `"101"`, `"1001"`, `"A-101"` ตรงกับที่ frontend คาดไว้หรือเปล่า ถ้าไม่ตรงให้สร้าง DB ด้วย `--lc-collate=C` หรือใส่ `COLLATE "C"` ที่คอลัมน์ |
| `findBy...IgnoreCase...` | ค้นด้วย email ตัวพิมพ์ปนกันแล้วต้องเจอ |
| `findTop50ByOrderByCreatedAtDesc` | `LIMIT` ทำงานถูก |
| `findByRoomIdAndStatusIn` | `IN` กับ list ว่าง ถ้า service ส่ง list ว่างมาได้ |
| `LocalDateTime` | Postgres เก็บละเอียดถึง microsecond ส่วน `LocalDateTime.now()` มี nanosecond ทำให้เทสที่ `assertEquals` เวลาก่อนกับหลังบันทึกอาจพัง |

### B4. ย้ายเทสมาใช้ Postgres (Testcontainers) (วันที่ 3)
1. สร้าง `backend/src/test/java/com/victory/apartment/TestcontainersConfiguration.java`
   ```java
   @TestConfiguration(proxyBeanMethods = false)
   public class TestcontainersConfiguration {
       @Bean
       @ServiceConnection
       PostgreSQLContainer<?> postgres() {
           return new PostgreSQLContainer<>("postgres:16-alpine");
       }
   }
   ```
2. ใส่ `@Import(TestcontainersConfiguration.class)` ใน
   - `ApplicationTests.java`: แก้ comment บรรทัด 81 ที่เขียนว่า "H2 in-memory database" ด้วย
   - `actuator/ActuatorHealthTest.java`
   - `controller/BookingLeaseIntegrationTest.java`: **ลบ** `properties = { "spring.datasource.url=jdbc:h2:mem:...", "spring.jpa.hibernate.ddl-auto=create-drop" }` ออกจาก `@SpringBootTest`
3. เทส `@WebMvcTest` อีก 10 ไฟล์ไม่ต้องแก้ เพราะ mock repository หมด (แต่ให้รันยืนยันอีกที)
4. ข้อมูลค้างระหว่างเทส: ตอนนี้ไม่มี `create-drop` แล้ว ให้ใส่ `@Transactional` ที่ class เทส (rollback เอง) หรือลบข้อมูลใน `@AfterEach` ส่วน `BookingLeaseIntegrationTest` ใช้ UUID suffix อยู่แล้วเลยไม่ชนกัน
5. DataSeeder จะรันตอนเทสด้วย ถ้าทำให้เทสช้าหรือกวนผล ให้ใส่ `@ActiveProfiles("test")` แล้วให้ seeder ข้าม profile `test` (ประสานกับ B5)
6. (ทางเลือก) เพิ่มเทส `FlywayMigrationTest` ที่เช็กว่า `flyway.info().applied()` มี V1 และ Hibernate validate ผ่าน
7. อัปเดต `test_documentation.md` ว่าเทสต้องมี Docker

### B5. DataSeeder (วันที่ 4) ⚠️ B เป็นคนตัดสินใจ
ตอนนี้ `DataSeeder` (`config/DataSeeder.java`) **ไม่มี `@Profile("!prod")` แล้ว** (หายไปตอน commit 94b4355d) ถ้าขึ้น Postgres บน prod ตามนี้ ระบบจะ seed ห้อง ผู้เช่า บิลปลอม ฯลฯ ลง production

ทางที่เสนอ:
- แยก `seedUsers()` (สร้าง admin จาก `ADMIN_EMAIL`/`ADMIN_PASSWORD`) ไปไว้ใน class ใหม่ `AdminBootstrap` ที่รันทุก profile และสร้างเฉพาะตอนที่ยังไม่มี admin
- ใส่ `@Profile("!prod & !test")` กลับให้ `DataSeeder` (ส่วน building/room/tenant/demo)
- เช็กว่ารันซ้ำรอบสองแล้วไม่ insert ซ้ำ (มี `count() > 0` / `findById().isEmpty()` อยู่แล้ว แต่ให้เช็กทุก method)
- บน prod ต้องตั้ง `ADMIN_PASSWORD` ผ่าน secret ห้ามใช้ default `admin` (แจ้ง A ให้เพิ่มใน secret)

### B6. ย้ายข้อมูลเก่า (ทางเลือก, วันที่ 4)
- k8s: **ไม่มีอะไรให้ย้าย** เพราะ H2 อยู่ใน `/tmp` ของ container
- local (`backend/data/apartmentdb.mv.db`): ถ้าทีมอยากเก็บข้อมูลที่กรอกไว้
  1. checkout commit ก่อน migrate แล้วรัน H2 shell: `SCRIPT NODATA TO 'schema.sql'` / `CALL CSVWRITE('rooms.csv', 'SELECT * FROM ROOMS')` ทีละตาราง
  2. รัน V1 บน Postgres แล้ว `\copy rooms FROM 'rooms.csv' CSV HEADER` ทีละตาราง
  3. เก็บ script ไว้ใน `backend/scripts/h2-to-postgres/` **ไม่ใส่ใน Flyway** เพราะเป็นงานทำครั้งเดียว
- ถ้าไม่ต้องการข้อมูลเก่าก็ข้ามไป ใช้ DataSeeder สร้าง demo data ใหม่แทน

### B7. ตรวจฝั่ง app และ review (วันที่ 5)
1. review PR `db/infra` ของ A (คนเดียว) แล้วคอมเมนต์ใน PR
2. pull `Migration-database` หลัง A merge แล้วไล่ checklist หมวด **Local** และ **Test** ในข้อ 7 ด้วยตัวเอง (รวมทั้งทดสอบ flow หลักผ่าน UI)
3. ส่งผล checklist ให้ A ไปแปะใน PR เข้า `main` ถ้าเจอบั๊กฝั่ง schema/test ให้แก้เองใน branch `db/schema` แล้ว push ตาม

---

## 5. Timeline

| วัน | คน A | คน B | ส่งต่องาน (ผ่าน push/แชท) |
|---|---|---|---|
| 1 | A1 pom + A3 compose (เฉพาะ `db`) → push `db/infra` ภายในเช้า | B1 แก้ entity + เริ่ม B2 บน `docker run postgres` ของตัวเอง | A push pom แล้ว B merge `db/infra` เข้า `db/schema` เอง |
| 2 | A2 properties + A3 ส่วน backend + A4 Dockerfile → push | B2 เขียน V1 ให้เสร็จ ทดสอบกับ `ddl-auto=validate` | B ดึง A2 มาใช้ทดสอบ validate |
| 3 | A5 k8s manifests (postgres, secret, configmap, deployment) | B3 ตรวจ query + B4 Testcontainers | |
| 4 | A6 CI/CD + A7 README, ลอง deploy บน cluster ทดสอบ | B5 DataSeeder + B6 (ถ้าทำ) → เปิด PR `db/schema` | B แจ้งชื่อ env ของ admin ให้ A ใส่ secret และ A เปิด PR `db/infra` |
| 5 | A8 review PR ของ B → merge → ตรวจ k8s/CI/Cleanup → เปิด PR เข้า `main` | B7 review PR ของ A → ตรวจ Local/Test → ส่งผลให้ A | B ส่งผล checklist ให้ A |

---

## 6. ความเสี่ยงและวิธีรับมือ

| ความเสี่ยง | วิธีรับมือ |
|---|---|
| Hibernate `validate` fail เพราะ type ไม่ตรง | dump DDL จาก Hibernate แทนการเขียนเองตั้งแต่ศูนย์ (B2 ขั้นที่ 1) |
| ข้อมูลยาวเกิน `varchar(255)` | ใช้ `text` ตาม B1 และลองสร้าง room ที่มี gallery 3 รูปผ่าน UI |
| DataSeeder seed ข้อมูล demo ลง prod | B5 |
| k8s ไม่มี default StorageClass ทำให้ PVC ค้าง Pending | A เช็ก `kubectl get storageclass` ตั้งแต่วันที่ 1 |
| pod backend start ก่อน Postgres พร้อม | `initContainer` รอ `pg_isready` (A5) |
| เทสบน CI ช้าลงเพราะต้องดึง image | ใช้ `postgres:16-alpine` และให้ทุกเทส reuse context เดียวกัน (ใช้ `TestcontainersConfiguration` ตัวเดียว) |
| ลำดับเรียงเลขห้องเปลี่ยน | เทสใน B3 ถ้าเปลี่ยนจริงให้ใช้ collation `C` |
| ทีมอื่นแก้ entity ระหว่างที่กำลังทำ | หลังจากนี้แก้ entity ต้องมาพร้อม `V{n}__*.sql` ใหม่เสมอ (เขียนไว้ใน README) |

---

## 7. Definition of Done (วันที่ 5 แยกกันตรวจคนละหมวด)

**Local** (B ตรวจ)
- [ ] `docker compose down -v && docker compose up -d --build` แล้ว backend log มี `Successfully applied 1 migration to schema "public"` และไม่มี error จาก Hibernate validate
- [ ] `curl localhost:8080/actuator/health` ได้ `"status":"UP"` และ `components.db.details.database = "PostgreSQL"`
- [ ] login admin ได้ด้วย `ADMIN_EMAIL`/`ADMIN_PASSWORD` ที่ตั้งไว้
- [ ] flow หลักผ่าน UI ได้หมด: สร้าง building → สร้าง room (มี gallery/amenities) → booking → approve → lease → ออก utility bill + แนบสลิป → maintenance task → complete → archive → ดู maintenance log
- [ ] `docker compose restart backend db` แล้วข้อมูลทั้งหมดยังอยู่
- [ ] start รอบสอง: Flyway ขึ้น `Schema is up to date` และ DataSeeder ไม่ insert ซ้ำ

**Test** (B ตรวจ)
- [ ] `cd backend && ./mvnw clean test` ผ่านทั้งหมด (ต้องเปิด Docker ไว้)

**CI** (A ตรวจ)
- [ ] GitHub Actions `Backend All Tests`, `Backend CI`, `Docker Build and Publish` เป็นสีเขียว

**Kubernetes** (A ตรวจ)
- [ ] `kubectl get pods` ทั้ง `postgres-0` และ backend เป็น Running/Ready
- [ ] `kubectl delete pod -l app=apartment-backend` แล้วข้อมูลยังอยู่ ลบ `postgres-0` แล้วข้อมูลก็ยังอยู่ (มาจาก PVC)
- [ ] ไม่ seed ข้อมูล demo บน prod มีแค่ admin

**Cleanup** (A ตรวจ)
- [ ] `grep -rni "h2" backend/src backend/pom.xml backend/Dockerfile k8s docker-compose.yml README.md` ไม่เจออะไร (ยกเว้น `.gitignore`)
- [ ] ไม่มี password จริงใน git (`git grep -i password` เจอแค่ placeholder หรือการอ่านจาก env)

---

## 8. งานต่อยอด (เปิด ticket แยก ไม่ทำในรอบนี้)
- เปลี่ยนจำนวนเงิน `Double` เป็น `BigDecimal` / `numeric(12,2)`
- เปลี่ยนฟิลด์วันที่ที่เป็น String เป็น `LocalDate` / `date`
- เปลี่ยน `amenities`, `gallery`, `suppliesUsed` เป็น `jsonb`
- เพิ่ม foreign key ระหว่างตาราง (rooms ↔ buildings, leases ↔ tenants/rooms, …)
- ตั้ง automated backup (CronJob รัน `pg_dump`) ไปเก็บที่ object storage
