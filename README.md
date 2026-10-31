# Apartment Management System - คู่มือการติดตั้ง ตรวจสอบโค้ด และทดสอบระบบ

โปรเจกต์นี้เป็นระบบบริหารจัดการหอพัก/อพาร์ตเมนต์ พัฒนาขึ้นโดยแบ่งออกเป็น 2 ส่วนหลักคือ:
- **Backend:** Java Spring Boot 3 (RESTful API, Spring Data JPA, PostgreSQL 16 + Flyway Migrations)
- **Frontend:** React + TypeScript + Vite + Tailwind CSS

คู่มือฉบับนี้จัดทำขึ้นเพื่อให้ผู้ประเมินสามารถตรวจสอบทั้งซอร์สโค้ด (Source Code), การรันระบบ (Local/Docker/K8s), ชุดการทดสอบ (Automated Tests & Coverage), รวมถึงไปป์ไลน์ CI/CD (Pipeline as Code) ได้อย่างครบถ้วนโดยไม่ต้องตั้งค่าเพิ่มเติม

---

## สารบัญ
1. [โครงสร้างโค้ดและส่วนประกอบสำคัญ (Codebase Structure)](#1-โครงสร้างโค้ดและส่วนประกอบสำคัญ-codebase-structure)
2. [การตรวจสอบ Pipeline as Code (CI/CD Workflows)](#2-การตรวจสอบ-pipeline-as-code-cicd-workflows)
3. [การรันและตรวจสอบ Tests (Unit Tests & Coverage)](#3-การรันและตรวจสอบ-tests-unit-tests--coverage)
4. [วิธีการเปิดใช้งานระบบ (Local / Docker / Minikube)](#4-วิธีการเปิดใช้งานระบบ)
   - 4.1 [รันแบบ Local (Development)](#41-การรันแบบ-local-สำหรับ-development)
   - 4.2 [รันผ่าน Docker Compose](#42-การรันผ่าน-docker-compose-สะดวกที่สุด)
   - 4.3 [รันบน Kubernetes ด้วย Minikube](#43-การรันบน-kubernetes-ผ่าน-minikube)
5. [การทดสอบระบบ End-to-End (Automated E2E ด้วย Playwright & Core Workflow)](#5-การทดสอบระบบ-end-to-end-automated-e2e-ด้วย-playwright--core-workflow)
   - 5.1 [Automated E2E Testing ด้วย Playwright (12 Scenarios)](#51-automated-e2e-testing-ด้วย-playwright-12-scenarios)
   - 5.2 [ขั้นตอนการทดสอบฟังก์ชันทางธุรกิจด้วยตนเอง (Manual E2E Walkthrough)](#52-ขั้นตอนการทดสอบฟังก์ชันทางธุรกิจด้วยตนเอง-manual-e2e-walkthrough)

---

## 1. โครงสร้างโค้ดและส่วนประกอบสำคัญ (Codebase Structure)

อาจารย์สามารถเข้าตรวจสอบโค้ดตามโฟลเดอร์หลักของโปรเจกต์ได้ดังนี้:

- **Backend (`/backend`)**
  - Controller Layer: `backend/src/main/java/com/victory/apartment/controller/` (จัดการ REST Endpoints)
  - Business Logic & Service: `backend/src/main/java/com/victory/apartment/service/`
  - Database & Entity: `backend/src/main/java/com/victory/apartment/model/` และ `repository/`
  - Test Suites: `backend/src/test/java/com/victory/apartment/controller/` (รวบรวม Unit Tests ทั้ง 69 รายการ)
- **Frontend (`/frontend`)**
  - หน้า UI และ Routing: `frontend/src/pages/`
  - คอมโพเนนต์การทำงาน: `frontend/src/components/`
  - การเชื่อมต่อ API Service: `frontend/src/services/`
  - Automated E2E Tests: `frontend/e2e/` (ชุดทดสอบ Playwright จำลองพฤติกรรมผู้ใช้จริง 12 Scenarios)
  - E2E Configuration: `frontend/playwright.config.ts` (การตั้งค่าเบราว์เซอร์, Multi-devices และ Auto WebServer)
- **Kubernetes Manifests (`/k8s`)**
  - `configmap.yaml`: คอนฟิกตัวแปรระบบ (Profile, Ports)
  - `deployment.yaml`: คำสั่ง Deployments และ Services (ทั้ง ClusterIP และ NodePort)

---

## 2. การตรวจสอบ Pipeline as Code (CI/CD Workflows)

โปรเจกต์มีการนำแนวคิด Pipeline as Code มาประยุกต์ใช้เต็มรูปแบบ โดยกำหนดการทำงานทั้งหมดผ่าน GitHub Actions ไว้ในโฟลเดอร์ `.github/workflows/`:

| ชื่อไฟล์ Workflow | หน้าที่การทำงาน | การทริกเกอร์ |
|---|---|---|
| `frontend-ci.yml` | ตรวจสอบ Lint, Type Check (TypeScript), และคอมไพล์ Frontend ด้วย Vite | Push / PR ในโฟลเดอร์ `frontend/` |
| `backend-ci.yml` | ตรวจสอบการ Build ซอร์สโค้ด Spring Boot ด้วย Maven | Push / PR ในโฟลเดอร์ `backend/` |
| `backend-unit-tests.yml` | รันชุด Unit Tests ทั้งหมดอัตโนมัติ | Push / PR |
| `backend-all-tests.yml` | รันชุดการทดสอบ พร้อม Publish JUnit Test Report สรุปผลผ่าน GitHub Step Summary | Push / PR / Manual Dispatch |
| `docker-build.yml` | ทำการ Build Docker Image (Backend & Frontend) และ Push ขึ้น Docker Hub พร้อมติด Tag ตาม Git SHA | Push เข้ากิ่ง `main`, `develop` |
| `deploy-k8s.yml` | นำ Image ล่าสุดไป Rollout Deploy บน Kubernetes Cluster อัตโนมัติหลัง Docker Build สำเร็จ | ทำงานต่อเนื่องหลัง `docker-build.yml` ผ่าน |

> **หมายเหตุสำหรับการตรวจ:** อาจารย์สามารถเปิดดูประวัติและหลักฐานการทำงานของแต่ละ Pipeline ได้ทันทีผ่านแท็บ **Actions** บน GitHub Repository: `https://github.com/poontanlion/apartment-main/actions`

---

## 3. การรันและตรวจสอบ Tests (Unit Tests & Coverage)

ระบบมีการเขียน Test ครอบคลุมการทำงานทั้ง 9 Controller รวมทั้งสิ้น **69 Test Methods** เพื่อป้องกันข้อผิดพลาดและ Regression

### คำสั่งรันการทดสอบทั้งหมดผ่าน Local:
> **ต้องเปิด Docker Desktop ไว้ก่อน** เพราะเทสที่ใช้ฐานข้อมูลจะสร้าง PostgreSQL ชั่วคราวผ่าน Testcontainers

1. เข้าไปที่โฟลเดอร์ `backend`:
   ```bash
   cd backend
   ```
2. สั่งรันคำสั่ง Maven Test โดยกำหนดเวอร์ชัน Docker API เป็น `1.44`:
   - **Windows (cmd):**
     ```cmd
     set JAVA_TOOL_OPTIONS=-Dapi.version=1.44
     mvnw.cmd test
     ```
   - **Windows (PowerShell):**
     ```powershell
     $env:JAVA_TOOL_OPTIONS="-Dapi.version=1.44"
     .\mvnw.cmd test
     ```
   - **macOS / Linux:**
     ```bash
     JAVA_TOOL_OPTIONS="-Dapi.version=1.44" ./mvnw test
     ```

### การตรวจสอบรายงานผลการทดสอบ (Test Results & Reports):
เมื่อรันคำสั่งเสร็จสิ้น รายงานผลการทดสอบอย่างละเอียดจะถูกบันทึกไว้ที่:
- `backend/target/surefire-reports/` (มีทั้งไฟล์ `.txt` สรุปผล และ `.xml` สำหรับ CI Parser)

### ขอบเขตการทดสอบ (Test Coverage Summary):
- `RoomControllerTest` (8 tests): ตรวจสอบการดึงข้อมูลห้องพัก, สถานะห้องว่าง 24 ห้อง
- `BookingControllerTest` (5 tests): ตรวจสอบการจองห้องพัก
- `AuthControllerTest` (6 tests): การ Login, Register และการยืนยันสิทธิ์
- `BuildingControllerTest` (8 tests): การจัดการข้อมูลอาคารและ CRUD Operations
- `TenantControllerTest` (7 tests): การจัดการข้อมูลผู้เช่าและตัวกรองตามอาคาร
- `UtilityBillControllerTest` (7 tests): การคำนวณและจัดการบิลค่าน้ำ ค่าไฟ ค่าเช่า
- `MaintenanceControllerTest` (14 tests): การติดตามงานซ่อม, การเบิกอุปกรณ์, บันทึกประวัติ และการแจ้งเตือนรอบบำรุงรักษา
- `GeneralControllerTest` (8 tests): ข้อมูลบันทึกกิจกรรม (Activity Logs) และการแจ้งเตือน (Notifications)
- `ActuatorHealthTest` (3 tests): Health check & Probes ของระบบด้วย Spring Boot Actuator (Database status, Liveness, Readiness)
- `LeaseControllerTest` (6 tests): การออกสัญญาเช่า และ**การตรวจจับการเช่าห้องซ้ำซ้อน (Conflict Prevention)**

*(ดูรายละเอียดเชิงลึกของแต่ละ Test Case เพิ่มเติมได้ที่ไฟล์ `test_documentation.md`)*

---

## 4. วิธีการเปิดใช้งานระบบ

### 4.1 การรันแบบ Local (สำหรับ Development)

#### สิ่งที่ต้องติดตั้ง
- Node.js (v18+)
- Java JDK (17+)
- Docker Desktop (ใช้รัน PostgreSQL)

#### ขั้นตอนการรัน
1. **Database (PostgreSQL):** เปิด Terminal ในโฟลเดอร์ `apartment-main`
   ```bash
   cp .env.example .env        # แก้รหัสผ่านใน .env ได้ตามต้องการ
   docker compose up -d db
   ```
   PostgreSQL จะเปิดที่ `localhost:5432` (database `apartmentdb`, user `apartment`)
   ตาราง (schema) ถูกสร้างอัตโนมัติด้วย Flyway จากไฟล์ใน `backend/src/main/resources/db/migration/` ตอน Backend start
2. **Backend:**
   ```bash
   cd backend
   mvnw.cmd spring-boot:run   # (หรือ ./mvnw spring-boot:run บน macOS/Linux)
   ```
   Backend จะเปิดทำงานที่ `http://localhost:8080`
   (ถ้าเปลี่ยน user/password ใน `.env` ให้ตั้ง `DB_USERNAME` / `DB_PASSWORD` ให้ตรงกันก่อนรัน)
3. **Frontend:** เปิด Terminal อีกหน้าต่าง
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   Frontend จะเปิดทำงานที่ `http://localhost:3000`

---

### 4.2 การรันผ่าน Docker Compose (สะดวกที่สุด)

#### สิ่งที่ต้องติดตั้ง
- Docker Desktop (เปิดโปรแกรมเตรียมไว้)

#### ขั้นตอนการรัน
1. เปิด Terminal ในโฟลเดอร์ `apartment-main` แล้วรัน:
   ```bash
   cp .env.example .env        # ครั้งแรกครั้งเดียว
   docker compose up --build -d
   ```
   ระบบจะเปิด 3 service: `db` (PostgreSQL), `backend` (รอจน db พร้อมก่อน start) และ `frontend`
2. เข้าใช้งาน:
   - **Frontend:** `http://localhost` (พอร์ต 80)
   - **Backend API:** `http://localhost:8080`
   - **PostgreSQL:** `localhost:5432`
3. คำสั่งปิดระบบ:
   ```bash
   docker compose down        # ข้อมูลยังอยู่ใน volume pgdata
   docker compose down -v     # ⚠️ ลบข้อมูลในฐานข้อมูลทิ้งทั้งหมด
   ```

---

### 4.3 การรันบน Kubernetes (ผ่าน Minikube)

#### สิ่งที่ต้องติดตั้ง
- Docker Desktop
- Minikube
- kubectl

#### ขั้นตอนการรัน
1. **เปิด Kubernetes Cluster:**
   ```bash
   minikube start
   minikube status
   ```
2. **สร้าง Secret ของฐานข้อมูล** (ครั้งแรกครั้งเดียว ดูตัวอย่าง key ได้ที่ `k8s/secret.yaml.example`):
   ```bash
   DB_USER='apartment'
   DB_PASS='MyDbPass123'
   kubectl create secret generic apartment-db-secret \
     --from-literal=DB_USERNAME="$DB_USER" \
     --from-literal=DB_PASSWORD="$DB_PASS" \
     --from-literal=POSTGRES_DB='apartmentdb' \
     --from-literal=POSTGRES_USER="$DB_USER" \
     --from-literal=POSTGRES_PASSWORD="$DB_PASS" \
     --from-literal=ADMIN_EMAIL='admin@victoryapartment.com' \
     --from-literal=ADMIN_PASSWORD='MyAdminPass456'
   ```

   | Key | ใส่อะไร | กฎ |
   |---|---|---|
   | `DB_USERNAME` / `POSTGRES_USER` | ชื่อ user ของ database | สองค่านี้ต้องตรงกัน (ตัวอย่างใช้ตัวแปร `DB_USER` ให้แล้ว) |
   | `DB_PASSWORD` / `POSTGRES_PASSWORD` | รหัสผ่าน database (แอปใช้ ไม่ต้องจำ) | สองค่านี้ต้องตรงกัน (ตัวอย่างใช้ตัวแปร `DB_PASS` ให้แล้ว) |
   | `POSTGRES_DB` | ชื่อ database | ใช้ `apartmentdb` |
   | `ADMIN_EMAIL` | อีเมลสำหรับล็อกอินหน้าเว็บเป็นแอดมิน | ไม่จำเป็นต้องเป็นอีเมลจริง |
   | `ADMIN_PASSWORD` | รหัสผ่านสำหรับล็อกอินหน้าเว็บเป็นแอดมิน | ห้ามว่าง ห้ามเป็น `admin` และควรยาว 6 ตัวขึ้นไป |

   ค่าในตัวอย่างเปลี่ยนเป็นของตัวเองได้ และแต่ละคนไม่จำเป็นต้องใช้ค่าเดียวกัน เพราะ Minikube ของแต่ละเครื่องแยกกัน ถ้ารหัสผ่านมีอักขระพิเศษ เช่น `$ ! & #` ให้ครอบด้วย single quote `'...'`

   (บน CI ขั้นนี้ทำอัตโนมัติใน `deploy-k8s.yml` โดยต้องตั้ง GitHub Secrets `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` ไว้ก่อน)
3. **Deploy ไปยังคลัสเตอร์:**
   ```bash
   kubectl apply -f k8s/postgres.yaml
   kubectl rollout status statefulset/postgres
   kubectl apply -f k8s/configmap.yaml
   kubectl apply -f k8s/deployment.yaml
   ```
   ข้อมูลของ PostgreSQL ถูกเก็บใน PersistentVolume จึงไม่หายเมื่อ Pod restart
4. **ตรวจสอบสถานะ Pods (รอให้ขึ้น Running ครบ):**
   ```bash
   kubectl get pods
   ```
5. **เปิดเข้าใช้งานหน้าเว็บ:**
   - วิธีที่ 1 (สะดวกที่สุด): รันคำสั่งเปิดเบราว์เซอร์อัตโนมัติ
     ```bash
     minikube service apartment-frontend-service
     ```
   - วิธีที่ 2 (Port Forward):
     ```bash
     kubectl port-forward service/apartment-frontend-service 30080:80
     ```
     แล้วเปิดเบราว์เซอร์ไปที่ `http://localhost:30080`
6. **การเปลี่ยน Docker Image หรือย้อนเวอร์ชัน:**
   หากต้องการเปลี่ยนไปใช้ Image อื่น (เช่น ย้อนกลับไปใช้ Tag หรือ Git SHA เก่าบน Docker Hub):
   - **วิธีที่ 1: เปลี่ยนผ่านคำสั่ง `kubectl set image`**
     ```bash
     kubectl set image deployment/apartment-backend-deployment backend=pixiz/apartment-backend:sha-<commit-hash>

     kubectl set image deployment/apartment-frontend-deployment frontend=pixiz/apartment-frontend:sha-<commit-hash>
     ```
     *(ตัวอย่าง: `kubectl set image deployment/apartment-backend-deployment backend=pixiz/apartment-backend:sha-5b24fc1a`)*

   - **วิธีที่ 2: แก้ไขในไฟล์ `k8s/deployment.yaml` โดยตรง**
     1. เปิดไฟล์ `k8s/deployment.yaml` แล้วแก้ไขระบุ Image ที่ต้องการในส่วนของ Containers:
        ```yaml
        containers:
        - name: backend
          image: pixiz/apartment-backend:<tag-ที่ต้องการ>   # เช่น sha-05622c4 หรือ latest
          imagePullPolicy: Always
        ```
     2. สั่ง Apply การเปลี่ยนแปลงไปยังคลัสเตอร์:
        ```bash
        kubectl apply -f k8s/deployment.yaml
        ```
7. **คำสั่ง Cleanup เมื่อตรวจเสร็จ:**
   ```bash
   kubectl delete -f k8s/deployment.yaml
   kubectl delete -f k8s/configmap.yaml
   kubectl delete -f k8s/postgres.yaml
   kubectl delete pvc pgdata-postgres-0   # ⚠️ ลบข้อมูลฐานข้อมูลทิ้ง (ข้ามได้ถ้าอยากเก็บไว้)
   minikube stop
   ```

---

## 5. การทดสอบระบบ End-to-End (Automated E2E ด้วย Playwright & Core Workflow)

ระบบมีชุดการทดสอบ End-to-End (E2E) ทั้งในรูปแบบ **Automated Test Scripts** ด้วย Playwright และลำดับขั้นตอนการทดสอบด้วยตนเองผ่านหน้าเว็บ (Manual Walkthrough)

---

### 5.1 Automated E2E Testing ด้วย Playwright (12 Scenarios)

โปรเจกต์ได้ติดตั้งชุดทดสอบ End-to-End อัตโนมัติด้วย **Playwright** เพื่อจำลองพฤติกรรมจริงของลูกบ้าน (Resident Portal / Tenant Journey) ผ่านเบราว์เซอร์ โดยทดสอบทั้งบน **Desktop Chrome** และ **Mobile Viewport (Pixel 7)**

> **จุดเด่นด้านความสะดวก (Zero Setup Server):**  
> คอนฟิกของ Playwright (`playwright.config.ts`) ได้ตั้งค่า `webServer` ไว้ล่วงหน้า เมื่อสั่งรันคำสั่งเทสต์ ระบบจะทำการเปิดทั้ง **Spring Boot Backend (:8080)** และ **React Frontend (:3000)** ให้อัตโนมัติ และจะปิดระบบลงให้อัตโนมัติหลังรันเสร็จสิ้น

#### ขั้นตอนการรัน Automated E2E Tests:
1. เข้าไปที่โฟลเดอร์ `frontend`:
   ```bash
   cd frontend
   ```
2. ติดตั้ง Dependencies และ Browser Engine (ทำเฉพาะครั้งแรก):
   ```bash
   npm install
   npx playwright install
   ```
3. สั่งรันชุดการทดสอบ E2E:
   - **รันแบบ Headless (รันเบื้องหลังแบบรวดเร็ว):**
     ```bash
     npm run test:e2e
     ```
   - **รันแบบ Interactive UI Mode (แนะนำสำหรับการประเมินและ Demo):**
     ```bash
     npm run test:e2e:ui
     ```
     *(เปิดหน้าต่าง Playwright Test Runner ให้เห็นขั้นตอนการจำลองคลิก, กรอกฟอร์ม และตรวจสอบผลลัพธ์ทีละ Step ได้อย่างชัดเจน)*

#### สรุป 12 Test Scenarios ใน Resident Portal (`frontend/e2e/resident-portal.e2e.ts`):

| รหัสทดสอบ | หมวดหมู่ | รายละเอียดการทดสอบ |
|---|---|---|
| **E2E-RP-01** | Access & Auth | ลูกบ้านใหม่สามารถลงทะเบียน (Register) ผ่านหน้า UI และเข้าสู่ระบบได้สำเร็จ |
| **E2E-RP-02** | Access & Auth | ลูกบ้านสามารถ Login ด้วย Credentials ที่ถูกต้อง และคงสถานะการล็อกอินหลังรีโหลดหน้าเว็บ |
| **E2E-RP-03** | Access & Auth | ระบบปฏิเสธการล็อกอินเมื่อกรอกรหัสผ่านผิด พร้อมแสดง Error Message ที่ถูกต้อง |
| **E2E-RP-04** | Access & Auth | ผู้ใช้ที่ยังไม่ได้ล็อกอิน หากเข้าหน้าแจ้งซ่อม (`/my-maintenance`) จะถูกแจ้งเตือนให้ Sign In |
| **E2E-RP-05** | Reporting a Repair | ลูกบ้านที่ยังไม่มีห้องพักที่ได้รับการอนุมัติ (Approved) จะไม่สามารถส่งแบบฟอร์มแจ้งซ่อมได้ |
| **E2E-RP-06** | Reporting a Repair | ลูกบ้านที่มีห้องอนุมัติแล้ว ส่งคำขอแจ้งซ่อมสำเร็จ ข้อมูลถูกบันทึกลง Database จริง และแสดงในประวัติ *(ทดสอบบน Mobile Viewport)* |
| **E2E-RP-07** | Reporting a Repair | Form Validation: ป้องกันการส่งคำขอหากไม่ได้กรอกรายละเอียดปัญหา |
| **E2E-RP-08** | Reporting a Repair | ลูกบ้านสามารถส่งคำขอแจ้งซ่อมได้หลายรายการ และระบบแสดงผลเรียงลำดับจากรายการใหม่ล่าสุด |
| **E2E-RP-09** | Repair Lifecycle | ติดตามสถานะงาน: เมื่อแอดมินมอบหมายงานให้ช่าง ลูกบ้านจะเห็นสถานะอัปเดตเป็น "In Progress" พร้อมชื่อช่างทันที |
| **E2E-RP-10** | Repair Lifecycle | ติดตามสถานะงาน: เมื่อช่างซ่อมแซมเสร็จสิ้น ระบบแสดงสถานะ "Completed" พร้อมเครื่องหมาย Done |
| **E2E-RP-11** | Security & Isolation | Data Isolation: ลูกบ้านแต่ละคนจะมองเห็นเฉพาะประวัติงานซ่อมของห้องตนเองเท่านั้น ไม่เห็นของผู้อื่น |
| **E2E-RP-12** | Security & Isolation | Route Protection: ลูกบ้านทั่วไปไม่สามารถเข้าถึงหน้า Admin Dashboard ได้ (ถูก Redirect ไปยัง `/profile`) ขณะที่ Admin ล็อกอินเข้าใช้งานได้ตามปกติ |

---

### 5.2 ขั้นตอนการทดสอบฟังก์ชันทางธุรกิจด้วยตนเอง (Manual E2E Walkthrough)

สำหรับอาจารย์ที่ต้องการคลิกทดสอบฟังก์ชันทางธุรกิจหลัก (Core Business Features) บนหน้าเบราว์เซอร์ด้วยตนเอง:

1. **ระบบแสดงสถานะห้องพัก (24 Rooms Dashboard):**
   - เข้าหน้า Dashboard ระบบจะแสดงผังห้องพัก 24 ห้อง (ชั้นละ 12 ห้อง จำนวน 2 ชั้น) พร้อมบอกสถานะห้องว่าง/ไม่ว่างแบบเรียลไทม์
2. **ระบบจัดการผู้เช่าและสัญญา (Tenant & Lease Management):**
   - ไปที่เมนูจัดการสัญญาเช่า ลองทำการทำสัญญาห้องที่ว่างอยู่
   - **ทดสอบ Conflict Prevention:** ลองเลือกห้องที่มีคนทำสัญญาอยู่แล้ว ระบบจะต้องปฏิเสธและป้องกันไม่ให้มีการเช่าซ้ำซ้อน
3. **ระบบพิมพ์ใบเสร็จและสัญญา (Receipt & Contract Generation):**
   - ในหน้ารายละเอียดการเช่าและบิลค่าน้ำค่าไฟ จะมีปุ่มสำหรับ Generate ใบเสร็จและสัญญาเช่าเป็นเอกสารให้สามารถสั่งพิมพ์หรือดาวน์โหลดได้
4. **ระบบงานซ่อมบำรุงและเบิกอุปกรณ์ (Maintenance & Supplies):**
   - ทดสอบสร้างคำขอแจ้งซ่อม (เช่น หลอดไฟเสีย, ล้างแอร์)
   - ตรวจสอบรายการเบิกจ่ายอุปกรณ์ และการตั้งค่าแจ้งเตือนรอบการบำรุงรักษา
