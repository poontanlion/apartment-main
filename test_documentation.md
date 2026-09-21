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

**ทำไมถึงใช้วิธีนี้?** เพราะเป็นการทดสอบระดับ Controller (Unit Test) ที่เร็วมาก ไม่ต้องสตาร์ท Database ทำให้รันได้ภายในไม่กี่วินาที และสามารถตรวจสอบว่า API ตอบกลับ HTTP Status Code ที่ถูกต้องหรือไม่

---

## 2. ตารางสรุปความครอบคลุมของ Unit Tests ทั้งหมด

| Controller | Test Class | จำนวน Test | สำเร็จ | ไม่สำเร็จ |
|---|---|---|---|---|
| `RoomController` | `RoomControllerTest` | 8 | 4 (GET all, GET by ID, POST, PUT) | 4 (GET not found, PUT not found, DELETE not found, GET by ID not found) |
| `BookingController` | `BookingControllerTest` | 5 | 3 (GET all, POST, Cancel) | 2 (Room Occupied 409, Cancel not found 404) |
| `AuthController` | `AuthControllerTest` | 6 | 3 (Admin Login, Register, Update Profile) | 3 (Wrong Password 401, Duplicate Email 400, Missing Fields 400) |
| `BuildingController` | `BuildingControllerTest` | 8 | 4 (GET all, GET by ID, POST, PUT) | 4 (GET not found, PUT not found, DELETE not found, GET by ID not found) |
| `TenantController` | `TenantControllerTest` | 7 | 4 (GET all, GET filter building, POST, PUT) | 3 (PUT not found, DELETE not found, DELETE not found) |
| `UtilityBillController` | `UtilityBillControllerTest` | 7 | 4 (GET all, GET filter building, POST, Update Status) | 3 (PUT not found, Status not found) |
| `MaintenanceController` | `MaintenanceControllerTest` | 14 | 9 (Tasks/Supplies/Logs/Reminders CRUD) | 5 (Task/Reminder not found, Toggle not found) |
| `GeneralController` | `GeneralControllerTest` | 8 | 5 (GET logs, GET notifs, POST, Mark read) | 3 (Mark one not found, Delete not found) |
| **Context Load** | `ApplicationTests` | 2 | 2 (Context loads, Repositories wired) | - |

**รวมทั้งหมด: 65 Test Methods ครอบคลุม 8 Controllers + 1 Integration Test**

---

## 3. รายละเอียดแต่ละ Test Class

### 3.1 RoomControllerTest (`/api/rooms`)
ทดสอบการจัดการข้อมูลห้องพัก

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `getAllRooms_Success` | ✅ Success | `GET /api/rooms` | 200 OK | ดึงรายการห้องทั้งหมดได้สำเร็จ |
| `getRoomById_Success` | ✅ Success | `GET /api/rooms/{id}` | 200 OK | ดึงข้อมูลห้องตาม ID ที่มีอยู่จริง |
| `getRoomById_NotFound` | ❌ Failure | `GET /api/rooms/{id}` | 404 Not Found | ค้นหาห้องด้วย ID ที่ไม่มีอยู่ |
| `createRoom_Success` | ✅ Success | `POST /api/rooms` | 200 OK | สร้างห้องใหม่สำเร็จ |
| `updateRoom_Success` | ✅ Success | `PUT /api/rooms/{id}` | 200 OK | แก้ไขข้อมูลห้องที่มีอยู่จริง |
| `updateRoom_NotFound` | ❌ Failure | `PUT /api/rooms/{id}` | 404 Not Found | แก้ไขห้องที่ไม่มีอยู่ |
| `deleteRoom_Success` | ✅ Success | `DELETE /api/rooms/{id}` | 200 OK | ลบห้องที่มีอยู่จริง |
| `deleteRoom_NotFound` | ❌ Failure | `DELETE /api/rooms/{id}` | 404 Not Found | ลบห้องที่ไม่มีอยู่ |

### 3.2 BookingControllerTest (`/api/bookings`)
ทดสอบระบบจองห้องพัก ซึ่งมี Business Logic พิเศษ (เช่น ห้ามจองห้องที่ถูกเช่าอยู่)

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `getAllBookings_Success` | ✅ Success | `GET /api/bookings` | 200 OK | ดึงรายการจองทั้งหมด |
| `createBooking_Success` | ✅ Success | `POST /api/bookings` | 200 OK | สร้างการจองใหม่เมื่อห้องว่าง (Available) |
| `createBooking_RoomOccupied_Conflict` | ❌ Failure | `POST /api/bookings` | 409 Conflict | พยายามจองห้องที่มีคนเช่าอยู่แล้ว (Occupied) ระบบต้องป้องกัน |
| `cancelBooking_Success` | ✅ Success | `PUT /api/bookings/{id}/cancel` | 200 OK | ยกเลิกการจองสำเร็จ |
| `cancelBooking_NotFound` | ❌ Failure | `PUT /api/bookings/{id}/cancel` | 404 Not Found | ยกเลิกการจองที่ไม่มีอยู่ |

### 3.3 AuthControllerTest (`/api/auth`)
ทดสอบระบบยืนยันตัวตน (Authentication) ทั้ง Login, Register, และอัปเดตโปรไฟล์

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `login_AdminSuccess` | ✅ Success | `POST /api/auth/login` | 200 OK | เข้าสู่ระบบด้วย admin/admin สำเร็จ |
| `login_WrongPassword` | ❌ Failure | `POST /api/auth/login` | 401 Unauthorized | เข้าสู่ระบบด้วยรหัสผ่านผิด |
| `register_Success` | ✅ Success | `POST /api/auth/register` | 200 OK | สมัครสมาชิกใหม่ด้วย email ที่ไม่ซ้ำ |
| `register_DuplicateEmail` | ❌ Failure | `POST /api/auth/register` | 400 Bad Request | สมัครด้วย email ที่มีอยู่แล้วในระบบ |
| `register_MissingFields` | ❌ Failure | `POST /api/auth/register` | 400 Bad Request | สมัครโดยไม่กรอก email หรือ password |
| `updateProfile_Success` | ✅ Success | `PUT /api/auth/profile` | 200 OK | อัปเดตข้อมูลโปรไฟล์สำเร็จ |

### 3.4 BuildingControllerTest (`/api/buildings`)
ทดสอบการจัดการข้อมูลอาคาร (CRUD)

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `getAllBuildings_Success` | ✅ Success | `GET /api/buildings` | 200 OK | ดึงรายการอาคารทั้งหมด |
| `getBuildingById_Success` | ✅ Success | `GET /api/buildings/{id}` | 200 OK | ดึงข้อมูลอาคารตาม ID |
| `getBuildingById_NotFound` | ❌ Failure | `GET /api/buildings/{id}` | 404 Not Found | ค้นหาอาคารที่ไม่มีอยู่ |
| `createBuilding_Success` | ✅ Success | `POST /api/buildings` | 200 OK | สร้างอาคารใหม่สำเร็จ |
| `updateBuilding_Success` | ✅ Success | `PUT /api/buildings/{id}` | 200 OK | แก้ไขข้อมูลอาคาร |
| `updateBuilding_NotFound` | ❌ Failure | `PUT /api/buildings/{id}` | 404 Not Found | แก้ไขอาคารที่ไม่มีอยู่ |
| `deleteBuilding_Success` | ✅ Success | `DELETE /api/buildings/{id}` | 200 OK | ลบอาคารสำเร็จ |
| `deleteBuilding_NotFound` | ❌ Failure | `DELETE /api/buildings/{id}` | 404 Not Found | ลบอาคารที่ไม่มีอยู่ |

### 3.5 TenantControllerTest (`/api/tenants`)
ทดสอบการจัดการข้อมูลผู้เช่า รวมถึง Building Filter

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `getAllTenants_Success` | ✅ Success | `GET /api/tenants` | 200 OK | ดึงผู้เช่าทั้งหมด |
| `getAllTenants_FilterByBuilding` | ✅ Success | `GET /api/tenants?buildingId=bld-1` | 200 OK | ดึงผู้เช่าเฉพาะอาคาร bld-1 |
| `createTenant_Success` | ✅ Success | `POST /api/tenants` | 200 OK | สร้างผู้เช่าใหม่ |
| `updateTenant_Success` | ✅ Success | `PUT /api/tenants/{id}` | 200 OK | แก้ไขข้อมูลผู้เช่า |
| `updateTenant_NotFound` | ❌ Failure | `PUT /api/tenants/{id}` | 404 Not Found | แก้ไขผู้เช่าที่ไม่มีอยู่ |
| `deleteTenant_Success` | ✅ Success | `DELETE /api/tenants/{id}` | 200 OK | ลบผู้เช่าสำเร็จ |
| `deleteTenant_NotFound` | ❌ Failure | `DELETE /api/tenants/{id}` | 404 Not Found | ลบผู้เช่าที่ไม่มีอยู่ |

### 3.6 UtilityBillControllerTest (`/api/utility-bills`)
ทดสอบระบบบิลค่าน้ำค่าไฟ

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `getAllBills_Success` | ✅ Success | `GET /api/utility-bills` | 200 OK | ดึงบิลทั้งหมด |
| `getAllBills_FilterByBuilding` | ✅ Success | `GET /api/utility-bills?buildingId=bld-1` | 200 OK | ดึงบิลเฉพาะอาคาร |
| `createBill_Success` | ✅ Success | `POST /api/utility-bills` | 200 OK | สร้างบิลใหม่ (ค่าน้ำ/ไฟคำนวณอัตโนมัติ) |
| `updateBill_Success` | ✅ Success | `PUT /api/utility-bills/{id}` | 200 OK | แก้ไขบิล |
| `updateBill_NotFound` | ❌ Failure | `PUT /api/utility-bills/{id}` | 404 Not Found | แก้ไขบิลที่ไม่มี |
| `updateBillStatus_Success` | ✅ Success | `PUT /api/utility-bills/{id}/status` | 200 OK | อัปเดตสถานะเป็น Paid |
| `updateBillStatus_NotFound` | ❌ Failure | `PUT /api/utility-bills/{id}/status` | 404 Not Found | อัปเดตสถานะบิลที่ไม่มี |

### 3.7 MaintenanceControllerTest (`/api`)
ทดสอบระบบซ่อมบำรุง (ซับซ้อนที่สุด มีหลาย Sub-resource: Tasks, Supplies, Logs, Reminders)

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `getAllTasks_Success` | ✅ Success | `GET /api/maintenance-tasks` | 200 OK | ดึงงานซ่อมทั้งหมด |
| `getAllTasks_FilterByBuilding` | ✅ Success | `GET /api/maintenance-tasks?buildingId=bld-1` | 200 OK | ดึงงานซ่อมเฉพาะอาคาร |
| `createTask_Success` | ✅ Success | `POST /api/maintenance-tasks` | 200 OK | สร้างงานซ่อมใหม่ |
| `updateTask_Success` | ✅ Success | `PUT /api/maintenance-tasks/{id}` | 200 OK | แก้ไขงานซ่อม |
| `updateTask_NotFound` | ❌ Failure | `PUT /api/maintenance-tasks/{id}` | 404 Not Found | แก้ไขงานที่ไม่มี |
| `deleteTask_Success` | ✅ Success | `DELETE /api/maintenance-tasks/{id}` | 200 OK | ลบงานซ่อม |
| `deleteTask_NotFound` | ❌ Failure | `DELETE /api/maintenance-tasks/{id}` | 404 Not Found | ลบงานที่ไม่มี |
| `getAllSupplies_Success` | ✅ Success | `GET /api/supplies` | 200 OK | ดึงรายการวัสดุ |
| `createSupply_Success` | ✅ Success | `POST /api/supplies` | 200 OK | เพิ่มวัสดุใหม่ |
| `getAllLogs_Success` | ✅ Success | `GET /api/maintenance-logs` | 200 OK | ดึงประวัติซ่อม |
| `getAllReminders_Success` | ✅ Success | `GET /api/reminders` | 200 OK | ดึงแจ้งเตือนกำหนดซ่อม |
| `createReminder_Success` | ✅ Success | `POST /api/reminders` | 200 OK | สร้างแจ้งเตือนใหม่ |
| `toggleReminder_Success` | ✅ Success | `PUT /api/reminders/{id}/toggle` | 200 OK | เปิด/ปิดแจ้งเตือน |
| `toggleReminder_NotFound` | ❌ Failure | `PUT /api/reminders/{id}/toggle` | 404 Not Found | Toggle แจ้งเตือนที่ไม่มี |

### 3.8 GeneralControllerTest (`/api`)
ทดสอบระบบ Notifications และ Activity Logs

| Test Method | ประเภท | Endpoint | คาดหวัง | อธิบาย |
|---|---|---|---|---|
| `getActivityLogs_Success` | ✅ Success | `GET /api/activity-logs` | 200 OK | ดึงประวัติการใช้งานระบบ |
| `getNotifications_Success` | ✅ Success | `GET /api/notifications` | 200 OK | ดึงการแจ้งเตือนทั้งหมด |
| `createNotification_Success` | ✅ Success | `POST /api/notifications` | 200 OK | สร้างการแจ้งเตือนใหม่ |
| `markAllRead_Success` | ✅ Success | `PUT /api/notifications/mark-read` | 200 OK | ทำเครื่องหมายอ่านแล้วทั้งหมด |
| `markOneRead_Success` | ✅ Success | `PUT /api/notifications/{id}/read` | 200 OK | ทำเครื่องหมายอ่านแล้วรายตัว |
| `markOneRead_NotFound` | ❌ Failure | `PUT /api/notifications/{id}/read` | 404 Not Found | ทำเครื่องหมายแจ้งเตือนที่ไม่มี |
| `deleteNotification_Success` | ✅ Success | `DELETE /api/notifications/{id}` | 200 OK | ลบแจ้งเตือนสำเร็จ |
| `deleteNotification_NotFound` | ❌ Failure | `DELETE /api/notifications/{id}` | 404 Not Found | ลบแจ้งเตือนที่ไม่มี |

---

## 4. GitHub Actions CI/CD Workflows

โปรเจกต์มี 4 Workflows แยกตามหน้าที่:

### 4.1 Backend Build (`backend-build.yml`)
- **Trigger:** Push/PR ไปที่ `main` หรือ `develop` เมื่อมีการเปลี่ยนแปลงในโฟลเดอร์ `backend/`
- **ทำอะไร:** ตั้งค่า JDK 17 → รัน `./mvnw clean compile`
- **จุดประสงค์:** ตรวจสอบว่า Java code compile ผ่านหรือไม่ (Syntax Error / Missing Import)

### 4.2 Frontend Build (`frontend-build.yml`)
- **Trigger:** Push/PR ไปที่ `main` หรือ `develop` เมื่อมีการเปลี่ยนแปลงในโฟลเดอร์ `frontend/`
- **ทำอะไร:** ตั้งค่า Node.js 20 → `npm ci` → `npm run build` (ซึ่งรัน `tsc && vite build`)
- **จุดประสงค์:** ตรวจสอบ TypeScript type errors และ Production build

### 4.3 Backend Unit Tests (`backend-unit-tests.yml`)
- **Trigger:** Push/PR ไปที่ `main` หรือ `develop` เมื่อมีการเปลี่ยนแปลงในโฟลเดอร์ `backend/`
- **ทำอะไร:** ตั้งค่า JDK 17 → รัน `./mvnw test -Dtest=*ControllerTest`
- **จุดประสงค์:** รันเฉพาะ Unit Test ระดับ Controller (เร็ว ไม่ต้องโหลด Full Context)

### 4.4 Backend All Tests (`backend-integration-tests.yml`)
- **Trigger:** Push/PR ไปที่ `main` หรือ `develop` เมื่อมีการเปลี่ยนแปลงในโฟลเดอร์ `backend/`
- **ทำอะไร:** ตั้งค่า JDK 17 → รัน `./mvnw test`
- **จุดประสงค์:** รันทุก Test รวมถึง `ApplicationTests` ที่ทดสอบ Spring Context Load และ Repository Wiring

---

## 5. สรุปความครอบคลุม (Coverage Summary)

- **8/8 Controllers** มี Unit Test ครบถ้วน ✅
- ทุก Test Method มี **JavaDoc Comment** อธิบายรายละเอียด ✅
- แบ่งเคสเป็น **Success / Failure** ชัดเจน ✅
- ครอบคลุม HTTP Status Codes: `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `409 Conflict` ✅
- ครอบคลุม Building Filter (`?buildingId=`) ใน Controllers ที่รองรับ ✅
