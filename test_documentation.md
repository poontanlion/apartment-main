# Test Documentation (เอกสารอธิบายการทำงานของ Unit Tests)

เอกสารนี้อธิบายการทำงานของ Unit Tests ทั้งหมดในโปรเจกต์ เพื่อครอบคลุมเส้นทางของ API (API Endpoints) แต่ละเส้น โดยแบ่งเป็นกรณีสำเร็จ (Success) และไม่สำเร็จ (Failure) ตามที่ระบุใน Requirement

---

## 1. การทำงานของ Test Framework

Unit Test ของ Spring Boot ในโปรเจกต์นี้ใช้เทคโนโลยีหลักดังนี้:

| เทคโนโลยี | หน้าที่ |
|---|---|
| `@WebMvcTest` | จำลอง Spring MVC Layer เฉพาะ Controller ที่ระบุ โดยไม่ต้องโหลดทั้ง Application Context |
| `MockMvc` | ส่ง HTTP Request จำลอง (GET, POST, PUT, DELETE) เข้ามาที่ Controller |
| `@MockBean` | สร้างวัตถุจำลอง (Mock) ของ Repository และ Service โดยไม่เชื่อมต่อ Database จริง |
| `Mockito` | กำหนดพฤติกรรมของ Mock (เช่น `when(...).thenReturn(...)`) |
| `ObjectMapper` | แปลง Java Object เป็น JSON เพื่อส่งใน Request Body |
| JUnit 5 | Framework สำหรับรัน Test |

---

## 2. ตารางสรุปความครอบคลุมของ Unit Tests ทั้งหมด

| Controller | Test Class | จำนวน Test |
|---|---|---|
| `RoomController` | `RoomControllerTest` | 8 |
| `BookingController` | `BookingControllerTest` | 5 |
| `AuthController` | `AuthControllerTest` | 6 |
| `BuildingController` | `BuildingControllerTest` | 8 |
| `TenantController` | `TenantControllerTest` | 7 |
| `UtilityBillController` | `UtilityBillControllerTest` | 7 |
| `MaintenanceController` | `MaintenanceControllerTest` | 14 |
| `GeneralController` | `GeneralControllerTest` | 8 |
| `LeaseController` | `LeaseControllerTest` | 6 |

**รวมทั้งหมด: 69 Test Methods ครอบคลุม 9 Controllers**

---

## 3. รายละเอียดแต่ละ Test Class (บางส่วน)

- **AuthControllerTest**: ครอบคลุม Login (สำเร็จ/รหัสผิด), Register (สำเร็จ/ซ้ำ/ข้อมูลไม่ครบ), อัปเดต Profile
- **BuildingControllerTest**: ครอบคลุม CRUD ครบถ้วน (200 OK, 404 Not Found)
- **TenantControllerTest**: ครอบคลุม CRUD และการใช้ Filter `?buildingId=` 
- **UtilityBillControllerTest**: ครอบคลุม CRUD, อัปเดต Status และ Filter `?buildingId=`
- **MaintenanceControllerTest**: ครอบคลุม Tasks, Supplies, Logs, Reminders รวม 14 เคส
- **LeaseControllerTest**: ทดสอบการจองสัญญา, เช็ค Conflict และยกเลิกสัญญา (Terminate)

---

## 4. GitHub Actions CI/CD Workflows

- `backend-build.yml`: คอมไพล์ Backend (เช็ค Syntax)
- `frontend-build.yml`: คอมไพล์ Frontend ด้วย Vite + TS
- `backend-unit-tests.yml`: รันเฉพาะ ControllerTest แบบเร็ว
- `backend-integration-tests.yml`: รัน Test ทั้งระบบเพื่อความสมบูรณ์
